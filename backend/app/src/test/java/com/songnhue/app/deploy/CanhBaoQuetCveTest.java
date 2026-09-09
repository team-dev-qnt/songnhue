package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>Chuông báo lượt quét bảo mật — chạy THẬT script với một {@code gh} giả.</b>
 *
 * <h2>Cái sai bài này sinh ra để chặn</h2>
 *
 * Lượt quét theo lịch 2/9/2026 07:06 UTC đỏ với 4 mã CVSS ≥ 7. Tới 3/9 vẫn không ai biết — lần thứ
 * <b>ba trong năm ngày</b>, và nhịp đỏ 6 lượt {@code schedule} gần nhất trên {@code dev} là 4/6. Đo
 * {@code security-scan.yml} xem nó phát ra gì khi đỏ, kết quả là <b>không gì cả</b> (T11.58).
 *
 * <p>Rồi tới 5/9 chuông ấy để lại <b>9 bình luận giống hệt nhau</b> (732 byte, cùng vân tay sau khi
 * bỏ URL) trong khi tập CVE đi 12 → 13 → 12 → 15 → 11 mã và 7 → 8 → 6 mã ≥ 7 — nó không đọc báo cáo
 * một dòng nào (T11.84). Một chuông không phân biệt được <i>đỏ như cũ</i> với <i>đỏ và tệ hơn</i> thì
 * không nói gì (CLAUDE.md luật 9).
 *
 * <h2>Năm trạng thái được kiểm ở đây</h2>
 *
 * <ol>
 *   <li><b>xanh-có-bằng-chứng</b> — {@code xanh} + tệp vân tay {@code ge7=0} ⇒ đóng issue (chỉ {@code dev}, T11.81).
 *   <li><b>xanh-không-có-báo-cáo</b> — {@code xanh} mà không có tệp ⇒ KHÔNG đóng, exit 1 (thiếu
 *       {@code NVD_API_KEY} ⇒ mọi bước OWASP skipped ⇒ job success ⇒ chuông cũ đóng nhầm).
 *   <li><b>đỏ-không-có-báo-cáo</b> — lỗi công cụ, nói rõ {@code owasp=}/{@code npm=}; không mốc.
 *   <li><b>đỏ-như-cũ</b> — vân tay khớp mốc trong body issue ⇒ IM LẶNG.
 *   <li><b>đỏ-và-đổi</b> — bình luận nhãn ({@code MỐC ĐẦU}/{@code LEO THANG}/{@code GIẢM}/{@code ĐỔI}) + diff,
 *       ghi mốc mới vào body + tiêu đề. Nhánh phụ chỉ nói khi có mã ≥ 7 mới, và KHÔNG dời mốc.
 * </ol>
 *
 * <h2>Vì sao chạy script thay vì đọc chữ trong YAML</h2>
 *
 * Một bài khẳng định <i>"workflow có chứa chuỗi {@code gh issue create}"</i> sẽ xanh với cả một
 * chuông đúng lẫn một chuông gọi lệnh ấy ở nhánh không bao giờ tới. Ở đây script được <b>gọi</b>, và
 * thứ được khẳng định là <b>lệnh {@code gh} nào thật sự chạy</b>, với thân nào.
 *
 * <h2>⛔ Giới hạn của chính bài kiểm này (luật 28)</h2>
 *
 * Nó chứng minh <b>dây đã nối</b>. Nó KHÔNG chứng minh <b>GitHub đã giao</b> — nửa sau chỉ đo được
 * bằng một lượt chạy thật.
 *
 * <p>⭐ <b>Đã trả 08/09 (T11.67):</b> trường hợp <b>lượt quét không chạy</b> — luật 31, thứ nguy hiểm
 * là sự vắng mặt — nay có bộ canh RIÊNG: {@link CanhCongQuetTest} +
 * {@code .github/workflows/canh-cong-quet.yml}. Hai chuông phủ hai tập <b>rời nhau</b>: chuông ở đây
 * lo lượt quét <b>ĐỎ</b> (kèm bằng chứng), chuông kia lo lượt quét <b>KHÔNG TỒN TẠI / quá hạn / kết
 * cục lạ</b> và <b>im lặng</b> trước {@code failure}. Chồng lấn là dựng lại đúng lỗi T11.84 đã vá.
 */
class CanhBaoQuetCveTest {

    private static final String SCRIPT = ".github/scripts/bao-dong-quet-cve.sh";
    private static final String WORKFLOW = ".github/workflows/security-scan.yml";
    private static final String TEN_JOB_CHUONG = "bao-dong";
    private static final String TEN_JOB_QUET = "owasp";
    private static final String URL = "https://vi.du/runs/1";

    /** Khoá job cấp một trong {@code security-scan.yml}: đúng hai dấu cách thụt đầu. */
    private static final Pattern KHOA_JOB = Pattern.compile("(?m)^  ([a-z][a-z0-9-]*):$");

    private static final String KHONG_ISSUE = "[]";
    private static final String ISSUE_42 = "[{\"number\":42}]";
    /** Body issue không có mốc — trạng thái trước T11.84, hoặc sau khi ai đó sửa tay body. */
    private static final String VIEW_TRONG = "{\"body\":\"Lượt quét phụ thuộc theo lịch **ĐỎ**.\"}";

    private static final List<String> MA7_MOC = List.of("CVE-2026-47890", "CVE-2026-47891");
    private static final List<String> DUOI7_MOC = List.of("CVE-2020-29582");
    private static final String NHANH_PHU = "fix/mot-nhanh-va";

    // ── Nhóm 1: hành vi thật của script — không có báo cáo (trạng thái 2, 3) và hạ tầng ────────

    @Test
    @DisplayName("A · đỏ + chưa có issue mốc + KHÔNG có báo cáo → MỞ issue (trạng thái 3)")
    void doVaChuaCoThiMo(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, KHONG_ISSUE, "do");

        assertThat(kq.maThoat())
                .as("Script phải chạy trót lọt:\n%s", kq.dauRa())
                .isZero();
        assertThat(kq.lenhGh())
                .as("Không có issue mốc nào thì phải MỞ một cái — kể cả khi chỉ biết 'đỏ' mà không có báo cáo")
                .anyMatch(l -> l.startsWith("issue create"));
        assertThat(kq.lenhGh()).noneMatch(l -> l.startsWith("issue comment"));
    }

    @Test
    @DisplayName("B · đỏ + ĐÃ có issue mốc + KHÔNG có báo cáo → bình luận, KHÔNG mở thêm")
    void doVaDaCoThiBinhLuan(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, ISSUE_42, "do");

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh()).anyMatch(l -> l.startsWith("issue comment 42"));
        assertThat(kq.lenhGh())
                .as(
                        """
                        Mở issue thứ hai khi đã có một cái đang mở là cách nhanh nhất để người ta \
                        thôi đọc: sau một tuần đỏ liên tiếp sẽ có bảy issue nói cùng một chuyện.""")
                .noneMatch(l -> l.startsWith("issue create"));
    }

    @Test
    @DisplayName("C · xanh CÓ bằng chứng + đang có issue mốc → ĐÓNG nó (bình luận trước, đóng sau)")
    void xanhThiDong(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(thuMuc, ISSUE_42, VIEW_TRONG, "xanh", "dev", bangChungXanh(thuMuc), Map.of());

        assertThat(kq.maThoat()).as("Đầu ra:\n%s", kq.dauRa()).isZero();
        assertThat(kq.lenhGh()).anyMatch(l -> l.startsWith("issue close 42"));
        int viTriBinhLuan = viTriDau(kq.lenhGh(), "issue comment 42");
        int viTriDong = viTriDau(kq.lenhGh(), "issue close 42");
        assertThat(viTriBinhLuan)
                .as("Phải bình luận trước khi đóng — đóng trước thì dòng giải thích rơi vào một issue đã đóng")
                .isGreaterThanOrEqualTo(0)
                .isLessThan(viTriDong);
    }

    @Test
    @DisplayName("D · xanh CÓ bằng chứng + không có issue nào → không làm gì cả")
    void xanhVaSachThiImLang(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(thuMuc, KHONG_ISSUE, VIEW_TRONG, "xanh", "dev", bangChungXanh(thuMuc), Map.of());

        assertThat(kq.maThoat()).as("Đầu ra:\n%s", kq.dauRa()).isZero();
        assertThat(kq.lenhGh())
                .as("Lượt quét xanh có bằng chứng và không có issue nào thì chuông không được tạo ra tiếng động")
                .noneMatch(l ->
                        l.startsWith("issue create") || l.startsWith("issue comment") || l.startsWith("issue close"));
    }

    @Test
    @DisplayName("⛔ E · không có `gh` trên PATH → ĐỎ, không im lặng đi tiếp")
    void thieuGhThiDo(@TempDir Path thuMuc) throws Exception {
        // PATH chỉ có đúng thư mục tạm RỖNG — không `gh`, và cũng không phải `/usr/bin` (nơi runner
        // của GitHub thật sự có `gh`, nên nếu để nguyên PATH thì bài này sẽ gọi `gh` THẬT).
        Path binRong = Files.createDirectories(thuMuc.resolve("bin-rong"));
        KetQua kq = chayVoiPath(thuMuc, binRong.toString(), "do");

        assertThat(kq.maThoat())
                .as(
                        """
                        Thiếu `gh` mà script vẫn thoát 0 thì cái chuông hỏng trong im lặng — đúng bẫy \
                        `verify-no-keys.sh` đã mắc: thiếu công cụ thì `exit 0`, và suốt bốn ngày mọi \
                        lượt triển khai in "BỎ QUA việc kiểm khoá" mà không ai đọc.

                        Đầu ra:
                        %s""",
                        kq.dauRa())
                .isNotZero();
    }

    @Test
    @DisplayName("F · tham số trạng thái lạ → ĐỎ, không đoán bừa")
    void thamSoLaThiDo(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, KHONG_ISSUE, "khong-phai-do-cung-khong-phai-xanh");

        assertThat(kq.maThoat()).isNotZero();
        assertThat(kq.lenhGh()).isEmpty();
    }

    @Test
    @DisplayName("⭐ Nhãn tiêu đề định nghĩa ĐÚNG MỘT LẦN — nhánh mở, nhánh đóng và nhánh đổi tiêu đề tìm cùng chuỗi")
    void nhanChiDinhNghiaMotLan(@TempDir Path thuMuc) throws Exception {
        String script = doc(timTuGocKho(SCRIPT));

        long soLanGan =
                script.lines().filter(d -> d.strip().startsWith("NHAN=")).count();
        assertThat(soLanGan)
                .as(
                        """
                        Nhãn nhận diện issue phải được gán đúng một lần. Hai hằng số rời nhau là dựng \
                        lại CLAUDE.md luật 14 — nhánh MỞ tìm một chuỗi, nhánh ĐÓNG tìm chuỗi khác, và \
                        issue không bao giờ được đóng.""")
                .isEqualTo(1);

        // Và cùng một chuỗi ấy phải THẬT SỰ dùng cho cả lượt tìm lẫn lượt tạo tiêu đề.
        assertThat(script).contains("--search \"in:title $NHAN\"");
        assertThat(script).contains("TIEU_DE=\"$NHAN");

        // Vòng khứ hồi đo được: issue mà nhánh "đỏ" tạo ra phải mang đúng cái nhãn mà nhánh tìm dùng.
        KetQua kq = chay(thuMuc, KHONG_ISSUE, "do");
        String nhan = trichNhan(script);
        assertThat(kq.lenhGh()).anyMatch(l -> l.startsWith("issue create") && l.contains(nhan));
        assertThat(kq.lenhGh()).anyMatch(l -> l.contains("in:title " + nhan));
    }

    @Test
    @DisplayName("⛔⛔ T11.81 · XANH có bằng chứng trên NHÁNH PHỤ → KHÔNG được đóng issue mốc")
    void xanhTrenNhanhPhuThiKhongDong(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(thuMuc, ISSUE_42, VIEW_TRONG, "xanh", NHANH_PHU, bangChungXanh(thuMuc), Map.of());

        assertThat(kq.maThoat()).as("Đầu ra:\n%s", kq.dauRa()).isZero();
        assertThat(kq.lenhGh())
                .as(
                        """
                        Đo 05/09: script không đọc một biến nhánh nào, nên nhánh XANH đóng issue mốc \
                        bất kể lượt quét chạy ở đâu — và lượt nhánh phụ CÓ với tới chuông thật \
                        (bình luận #5 của issue #84 đến từ `fix/t11-76-jackson-bom` qua \
                        `workflow_dispatch`). Làm T11.69 thì người ta bấm `workflow_dispatch` trên \
                        nhánh vá để xem đã sạch chưa; lượt ấy xanh ⇒ đóng #84 trong khi `dev` VẪN ĐỎ.""")
                .noneMatch(l -> l.startsWith("issue close"));
    }

    @Test
    @DisplayName("⭐ Đối chứng: cùng bằng chứng ấy nhưng trên `dev` thì PHẢI đóng (luật 9)")
    void xanhTrenDevThiVanDong(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(thuMuc, ISSUE_42, VIEW_TRONG, "xanh", "dev", bangChungXanh(thuMuc), Map.of());

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh())
                .as("Không có bài này thì bài trên xanh cả khi ai đó vô hiệu hoá hẳn nhánh đóng")
                .anyMatch(l -> l.startsWith("issue close 42"));
    }

    @Test
    @DisplayName("⭐ ĐỎ không có báo cáo trên nhánh phụ VẪN phải bình luận — mở rộng tay, đóng chặt tay")
    void doTrenNhanhPhuVanBinhLuan(@TempDir Path thuMuc) throws Exception {
        Path bin = dungGhGia(thuMuc, ISSUE_42, VIEW_TRONG);
        KetQua kq = chayVoiPath(thuMuc, bin + java.io.File.pathSeparator + System.getenv("PATH"), "do", NHANH_PHU);

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh())
                .as("Một lỗi công cụ trên nhánh phụ vẫn đáng nói — bất đối xứng này là cố ý")
                .anyMatch(l -> l.startsWith("issue comment 42"));
    }

    @Test
    @DisplayName("⭐ Nhánh mốc định nghĩa ĐÚNG MỘT LẦN, và workflow có truyền `NHANH` vào")
    void nhanhMocMotLanVaWorkflowTruyenVao() {
        String script = doc(timTuGocKho(SCRIPT));
        long soLanGan =
                script.lines().filter(d -> d.strip().startsWith("NHANH_MOC=")).count();
        assertThat(soLanGan)
                .as("Hai hằng rời nhau là dựng lại luật 14 — nhánh kiểm một chuỗi, thông báo in chuỗi khác")
                .isEqualTo(1);

        String yml = doc(timTuGocKho(WORKFLOW));
        assertThat(yml)
                .as(
                        """
                        Script đọc `NHANH` mà workflow không truyền thì biến rỗng, và rỗng != "dev" \
                        nên chuông THÔI ĐÓNG issue ở mọi lượt — hỏng theo hướng ngược lại, im lặng \
                        y hệt. Đây là nửa còn lại của cặp đọc–ghi (quy tắc 27).""")
                .contains("NHANH: ${{ github.ref_name }}");
    }

    // ── Nhóm 2: dây nối trong workflow ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("⭐⭐ `needs` của chuông phủ MỌI job quét — cả hai chiều")
    void chuongPhuMoiJobQuet() {
        List<String> jobQuet = new ArrayList<>(danhSachJob());
        jobQuet.remove(TEN_JOB_CHUONG);
        List<String> needs = needsCuaChuong();

        // Chặn xanh-trên-tập-rỗng ở CẢ HAI vế: regex lỗi thời phải ĐỎ, không phải im lặng đạt.
        assertThat(jobQuet)
                .as("Không đọc ra job quét nào từ security-scan.yml — cấu trúc đã đổi, SỬA bài kiểm chứ đừng xoá")
                .hasSizeGreaterThanOrEqualTo(2);
        // ⚠ Ngưỡng ở vế này là **1**, không phải 2 — cố ý: vế này chỉ chứng minh regex còn đọc được;
        //   việc phát hiện thiếu job là của phép so hai chiều bên dưới, và nó phải là thứ lên tiếng
        //   (§10.68-B: bước SSH đỏ 6/6 mà `2>/dev/null` vứt mất lý do).
        assertThat(needs)
                .as(
                        "Không đọc ra `needs` nào của job `%s` — regex đã lỗi thời, SỬA bài kiểm chứ đừng xoá",
                        TEN_JOB_CHUONG)
                .hasSizeGreaterThanOrEqualTo(1);

        List<String> thieu = new ArrayList<>(jobQuet);
        thieu.removeAll(needs);
        assertThat(thieu)
                .as(
                        """
                        %d job quét KHÔNG nằm trong `needs` của `%s`: %s

                        Job đứng ngoài `needs` thì đỏ bao nhiêu chuông cũng báo xanh — và cái xanh ấy \
                        đọc như một lời bảo đảm (CLAUDE.md luật 28).""",
                        thieu.size(), TEN_JOB_CHUONG, thieu)
                .isEmpty();

        List<String> moCoi = new ArrayList<>(needs);
        moCoi.removeAll(jobQuet);
        assertThat(moCoi).as("`needs` trỏ tới job không có thật: %s", moCoi).isEmpty();
    }

    @Test
    @DisplayName("⭐ Chuông phải `if: always()` và có quyền `issues: write`")
    void chuongLuonChayVaCoQuyenGhi() {
        String than = thanJob(TEN_JOB_CHUONG);

        assertThat(than)
                .as(
                        """
                        Thiếu `if: always()` thì chuông bị bỏ qua đúng lúc một job quét hỏng — tức là \
                        đúng trường hợp nó sinh ra để xử lý. Và `skipped` được GitHub tính là ĐẠT \
                        (luật 24).""")
                .contains("if: always()");

        // ⚠ Khẳng định trên KHỐI `permissions:`, không trên toàn thân job: chuỗi `issues: write` xuất
        //   hiện 2 lần trong workflow — một quyền thật, một dòng chú thích. Luật 2: canh cấu trúc.
        String khoiQuyen = khoiQuyenCua(than);
        assertThat(khoiQuyen)
                .as(
                        """
                        Job `%s` không có khối `permissions:` nào — quyền mặc định của workflow là \
                        `contents: read`, tức `gh issue create` sẽ trả 403.""",
                        TEN_JOB_CHUONG)
                .isNotEmpty();
        assertThat(khoiQuyen)
                .as(
                        """
                        Khối `permissions:` của `%s` thiếu `issues: write` thì `gh issue create` trả \
                        **403 trong im lặng** — cùng hình dạng §10.57. Khối đọc được:
                        %s""",
                        TEN_JOB_CHUONG, khoiQuyen)
                .contains("issues: write");

        assertThat(than)
                .as("Chuông phải GỌI script, không tự viết lại logic trong `run:`")
                .contains("bao-dong-quet-cve.sh");
    }

    @Test
    @DisplayName("⭐ `paths:` bao cả workflow lẫn script chuông — sửa cổng phải làm cổng chạy lại")
    void pathsBaoCaHaiTepMoi() {
        String workflow = doc(timTuGocKho(WORKFLOW));

        assertThat(workflow)
                .as(
                        """
                        `paths:` không bao chính `security-scan.yml` thì sửa ngưỡng `--audit-level` \
                        hay sửa nhánh báo động chỉ được kiểm ở lượt theo lịch HÔM SAU (§10.69). \
                        `SuppressionPolicyTest` đã bắt đúng lỗi ấy cho tệp suppression.""")
                .contains("'.github/workflows/security-scan.yml'");

        assertThat(workflow).contains("'.github/scripts/bao-dong-quet-cve.sh'");
    }

    @Test
    @DisplayName("⭐ G16 · MỌI script `.github/scripts/*.sh` mà workflow gọi đều nằm trong `paths:`")
    void moiScriptDuocGoiDeuTrongPaths() {
        String workflow = doc(timTuGocKho(WORKFLOW));
        List<String> dongMa =
                workflow.lines().filter(d -> !d.strip().startsWith("#")).toList();

        TreeSet<String> duocGoi = new TreeSet<>();
        Matcher m = Pattern.compile("\\.github/scripts/[a-z0-9-]+\\.sh").matcher(String.join("\n", dongMa));
        while (m.find()) {
            duocGoi.add(m.group());
        }
        TreeSet<String> trongPaths = new TreeSet<>();
        Matcher p = Pattern.compile("(?m)^\\s+- '(\\.github/scripts/[a-z0-9-]+\\.sh)'$")
                .matcher(workflow);
        while (p.find()) {
            trongPaths.add(p.group(1));
        }

        assertThat(duocGoi)
                .as("Bộ dò không đọc ra script nào — cấu trúc đã đổi, SỬA bài kiểm chứ đừng xoá")
                .hasSizeGreaterThanOrEqualTo(3);
        List<String> thieu = new ArrayList<>(duocGoi);
        thieu.removeAll(trongPaths);
        assertThat(thieu)
                .as(
                        """
                        Script được workflow gọi nhưng KHÔNG nằm trong `paths:`: %s — sửa nó thì cổng \
                        không chạy lại, thay đổi chỉ được kiểm ở lượt theo lịch hôm sau (luật 24, §10.69).""",
                        thieu)
                .isEmpty();
    }

    @Test
    @DisplayName("⭐ G17 · cặp đọc–ghi: `bao-dong` tải ĐÚNG artifact mà `owasp` đẩy lên, và truyền hai tệp vào script")
    void chuongTaiDungArtifact() {
        String thanQuet = thanJob(TEN_JOB_QUET);
        String thanChuong = thanJob(TEN_JOB_CHUONG);

        String tenDay = tenArtifact(buocChua(thanQuet, "upload-artifact"));
        String tenTai = tenArtifact(buocChua(thanChuong, "download-artifact"));
        assertThat(tenDay).as("Không đọc ra `name:` của bước upload").isNotBlank();
        assertThat(tenTai)
                .as(
                        """
                        Chuông tải artifact `%s` trong khi job quét đẩy lên `%s` — hai đầu của một cặp \
                        đọc–ghi phải nhớ cùng một tên (quy tắc 27), và download-artifact vắng tên thì \
                        chuông đọc mọi lượt thành "không có báo cáo".""",
                        tenTai, tenDay)
                .isEqualTo(tenDay);

        String buocTai = buocChua(thanChuong, "download-artifact");
        assertThat(buocTai)
                .as("Artifact vắng (job quét bị huỷ) không được làm chuông đỏ câm — script phải nhận 'không có tệp'")
                .contains("continue-on-error: true")
                .contains("if: always()");

        assertThat(thanChuong)
                .as("Script nhận tệp vân tay và tệp phạm vi qua tham số 3–4 — thiếu là chuông quay về một bit")
                .contains("\"${van_tay:-}\" \"${phu_quet:-}\"")
                .contains("-name van-tay-cve.txt")
                .contains("-name phu-quet-cve.txt");
    }

    // ── Nhóm 3: tự kiểm chứng — bài này có bắt được vi phạm không? ──────────────────────────────

    @Test
    @DisplayName("⭐ TỰ KIỂM: bỏ một job quét khỏi `needs` thì phép so hai chiều phải bắt")
    void tuKiemChung() {
        String yamlGia =
                """
                jobs:
                  owasp:
                    name: OWASP Dependency-Check
                  npm:
                    name: npm audit
                  bao-dong:
                    name: Báo động lượt quét đỏ
                    needs: [owasp]
                    if: always()
                """;

        List<String> job = docKhoaJob(yamlGia);
        job.remove(TEN_JOB_CHUONG);
        List<String> needs = docNeeds(yamlGia);

        assertThat(job)
                .as("Bộ dò job phải đọc được cả hai job quét từ chuỗi giả")
                .containsExactlyInAnyOrder("owasp", "npm");
        assertThat(needs).containsExactly("owasp");

        List<String> thieu = new ArrayList<>(job);
        thieu.removeAll(needs);
        assertThat(thieu)
                .as("Bộ dò KHÔNG bắt được job quét bị bỏ khỏi `needs` — phép so ở bài trên vô nghĩa")
                .containsExactly("npm");
    }

    @Test
    @DisplayName("⭐⭐ TỰ KIỂM: một dòng CHÚ THÍCH nhắc `issues: write` KHÔNG được tính là có quyền")
    void tuKiemChungQuyenGhi() {
        String coQuyenThat =
                """
                    permissions:
                      contents: read
                      issues: write
                """;
        String chiCoChuThich =
                """
                    # ⚠ Thiếu dòng `issues: write` thì `gh issue create` trả 403 trong im lặng.
                    permissions:
                      contents: read
                """;

        assertThat(khoiQuyenCua(coQuyenThat))
                .as("Bản CÓ quyền thật phải đọc ra `issues: write`")
                .contains("issues: write");

        assertThat(khoiQuyenCua(chiCoChuThich))
                .as(
                        """
                        Bộ dò tính một dòng CHÚ THÍCH là có quyền ⇒ bài `chuongLuonChayVaCoQuyenGhi` \
                        sẽ xanh sau khi ai đó xoá dòng quyền thật và để lại lời giải thích. Đúng \
                        CLAUDE.md luật 2 — canh cấu trúc, đừng canh văn bản.""")
                .doesNotContain("issues: write");

        // Và vế "không có khối permissions nào" cũng phải phân biệt được với "có nhưng thiếu".
        assertThat(khoiQuyenCua("    name: x\n    if: always()\n")).isEmpty();
    }

    // ── Nhóm 4: chuông có trạng thái (T11.84) ───────────────────────────────────────────────────

    @Test
    @DisplayName("G1 · dev · đỏ · vân tay KHỚP mốc → IM LẶNG: không comment, không edit, không create")
    void doNhuCuThiImLang(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(
                thuMuc,
                ISSUE_42,
                viewCoMoc(MA7_MOC, DUOI7_MOC, "ok", "success", 0),
                "do",
                "dev",
                bangChung(thuMuc, MA7_MOC, DUOI7_MOC, 0),
                Map.of());

        assertThat(kq.maThoat()).as("Đầu ra:\n%s", kq.dauRa()).isZero();
        assertThat(kq.lenhGh())
                .as(
                        """
                        9 bình luận 732 byte giống hệt nhau (3/9→5/9) là thứ đã che mất lượt 7 → 8 mã. \
                        Đỏ như cũ thì im lặng; issue mốc đang mở ĐÃ là trạng thái.""")
                .noneMatch(l ->
                        l.startsWith("issue comment") || l.startsWith("issue edit") || l.startsWith("issue create"));
        assertThat(kq.dauRa()).contains("đỏ-như-cũ");
    }

    @Test
    @DisplayName("⭐ G2 · dev · thêm một mã ≥ 7 → bình luận `LEO THANG` + edit tiêu đề (mang số) và body (mốc mới)")
    void leoThang(@TempDir Path thuMuc) throws Exception {
        List<String> ma7Moi = List.of("CVE-2026-47890", "CVE-2026-47891", "CVE-2026-59283");
        KetQua kq = chayDayDu(
                thuMuc,
                ISSUE_42,
                viewCoMoc(MA7_MOC, DUOI7_MOC, "ok", "success", 0),
                "do",
                "dev",
                bangChung(thuMuc, ma7Moi, DUOI7_MOC, 0),
                Map.of());

        assertThat(kq.maThoat()).as("Đầu ra:\n%s", kq.dauRa()).isZero();
        String than = thanCua(kq, "issue comment 42");
        assertThat(than).contains("LEO THANG").contains("CVE-2026-59283");

        String edit = lenhDauTien(kq, "issue edit 42");
        String nhan = trichNhan(doc(timTuGocKho(SCRIPT)));
        assertThat(edit)
                .as("Tiêu đề phải mang nhãn và SỐ mã ≥ 7 mới — danh sách issue phải đọc ra được mức độ")
                .contains("--title")
                .contains(nhan)
                .contains("3 mã CVSS ≥ 7");
        assertThat(edit)
                .as("Body mới phải mang mốc mới, nếu không lượt sau lại báo LEO THANG lần nữa")
                .contains("van-tay-cve ge7=3")
                .contains("ma7=CVE-2026-47890,CVE-2026-47891,CVE-2026-59283");
        assertThat(kq.lenhGh()).noneMatch(l -> l.startsWith("issue create"));
    }

    @Test
    @DisplayName("G3 · dev · bớt một mã ≥ 7 → `GIẢM`, không `LEO THANG`, vẫn dời mốc")
    void giam(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(
                thuMuc,
                ISSUE_42,
                viewCoMoc(MA7_MOC, DUOI7_MOC, "ok", "success", 0),
                "do",
                "dev",
                bangChung(thuMuc, List.of("CVE-2026-47890"), DUOI7_MOC, 0),
                Map.of());

        assertThat(kq.maThoat()).isZero();
        String than = thanCua(kq, "issue comment 42");
        assertThat(than).contains("GIẢM").doesNotContain("LEO THANG").contains("CVE-2026-47891");
        assertThat(lenhDauTien(kq, "issue edit 42")).contains("ma7=CVE-2026-47890 ");
    }

    @Test
    @DisplayName("G4 · dev · thêm một mã DƯỚI 7 → `ĐỔI`, không `LEO THANG`")
    void doiKhongLeoThang(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(
                thuMuc,
                ISSUE_42,
                viewCoMoc(MA7_MOC, DUOI7_MOC, "ok", "success", 0),
                "do",
                "dev",
                bangChung(thuMuc, MA7_MOC, List.of("CVE-2020-29582", "CVE-2026-59314"), 0),
                Map.of());

        assertThat(kq.maThoat()).isZero();
        String than = thanCua(kq, "issue comment 42");
        assertThat(than).contains("ĐỔI").doesNotContain("LEO THANG").contains("CVE-2026-59314");
    }

    @Test
    @DisplayName("G5 · dev · body chưa có mốc → `MỐC ĐẦU` + edit ghi mốc vào body")
    void mocDau(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(
                thuMuc, ISSUE_42, VIEW_TRONG, "do", "dev", bangChung(thuMuc, MA7_MOC, DUOI7_MOC, 0), Map.of());

        assertThat(kq.maThoat()).isZero();
        assertThat(thanCua(kq, "issue comment 42")).contains("MỐC ĐẦU");
        assertThat(lenhDauTien(kq, "issue edit 42")).contains("van-tay-cve ge7=2 tong=3 suppress=0 npm=success phu=ok");
    }

    @Test
    @DisplayName("G6 · dev · chưa có issue → `create` với tiêu đề mang số và body mang mốc")
    void chuaCoIssueThiTaoCoMoc(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(
                thuMuc, KHONG_ISSUE, VIEW_TRONG, "do", "dev", bangChung(thuMuc, MA7_MOC, DUOI7_MOC, 0), Map.of());

        assertThat(kq.maThoat()).isZero();
        String create = lenhDauTien(kq, "issue create");
        assertThat(create).contains("2 mã CVSS ≥ 7").contains("van-tay-cve ge7=2");
        assertThat(kq.lenhGh()).noneMatch(l -> l.startsWith("issue comment") || l.startsWith("issue edit"));
    }

    @Test
    @DisplayName("⛔ G7 · dev · đỏ mà KHÔNG có tệp vân tay, owasp=cancelled → nói 'KHÔNG có báo cáo', không dời mốc")
    void doKhongCoBaoCao(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(
                thuMuc,
                ISSUE_42,
                viewCoMoc(MA7_MOC, DUOI7_MOC, "ok", "success", 0),
                "do",
                "dev",
                List.of(),
                Map.of("KQ_OWASP", "cancelled"));

        assertThat(kq.maThoat()).isZero();
        String than = thanCua(kq, "issue comment 42");
        assertThat(than)
                .as("Lỗi công cụ và có-CVE là hai lớp lỗi khác hẳn — bình luận phải nói rõ và nêu kết quả job")
                .contains("KHÔNG có báo cáo")
                .contains("cancelled")
                .doesNotContain("van-tay-cve ");
        assertThat(kq.lenhGh()).noneMatch(l -> l.startsWith("issue edit"));
    }

    @Test
    @DisplayName("G8 · nhánh phụ · vân tay khớp mốc `dev` → im lặng")
    void nhanhPhuNhuCuThiImLang(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(
                thuMuc,
                ISSUE_42,
                viewCoMoc(MA7_MOC, DUOI7_MOC, "ok", "success", 0),
                "do",
                NHANH_PHU,
                bangChung(thuMuc, MA7_MOC, DUOI7_MOC, 0),
                Map.of());

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh())
                .noneMatch(l ->
                        l.startsWith("issue comment") || l.startsWith("issue edit") || l.startsWith("issue create"));
    }

    @Test
    @DisplayName("⭐ G9 · nhánh phụ · có mã ≥ 7 MỚI so với mốc dev → bình luận nêu nhánh, KHÔNG mốc, KHÔNG edit")
    void nhanhPhuCoMaMoiThiNoiKhongDoiMoc(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(
                thuMuc,
                ISSUE_42,
                viewCoMoc(MA7_MOC, DUOI7_MOC, "ok", "success", 0),
                "do",
                NHANH_PHU,
                bangChung(thuMuc, List.of("CVE-2026-47890", "CVE-2026-47891", "CVE-2026-59283"), DUOI7_MOC, 0),
                Map.of());

        assertThat(kq.maThoat()).isZero();
        String than = thanCua(kq, "issue comment 42");
        assertThat(than).contains(NHANH_PHU).contains("CVE-2026-59283").doesNotContain("van-tay-cve ");
        assertThat(kq.lenhGh())
                .as(
                        """
                        Nhánh phụ dời mốc thì một `workflow_dispatch` trên nhánh vá (6 → 4 mã) làm lượt theo \
                        lịch hôm sau trên `dev` báo "LEO THANG 4 → 6" giả. Mở thì rộng tay, mốc thì chỉ `dev`.""")
                .noneMatch(l -> l.startsWith("issue edit") || l.startsWith("issue create"));
    }

    @Test
    @DisplayName("G10 · nhánh phụ · ÍT mã hơn mốc dev → im lặng (nhánh vá đang dọn, không phải tin của dev)")
    void nhanhPhuItMaHonThiImLang(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(
                thuMuc,
                ISSUE_42,
                viewCoMoc(MA7_MOC, DUOI7_MOC, "ok", "success", 0),
                "do",
                NHANH_PHU,
                bangChung(thuMuc, List.of("CVE-2026-47890"), List.of(), 0),
                Map.of());

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh())
                .noneMatch(l ->
                        l.startsWith("issue comment") || l.startsWith("issue edit") || l.startsWith("issue create"));
    }

    @Test
    @DisplayName("⭐ G11 · BẤT BIẾN chiều 1: cùng tập CVE, URL khác → thân giống hệt sau khi bỏ URL")
    void cungTapThiCungThan(@TempDir Path thuMuc) throws Exception {
        Path bin = dungGhGia(thuMuc, ISSUE_42, VIEW_TRONG);
        String path = bin + java.io.File.pathSeparator + System.getenv("PATH");
        List<String> bangChung = bangChung(thuMuc, MA7_MOC, DUOI7_MOC, 0);

        KetQua lan1 = chayScript(
                timTuGocKho(SCRIPT), thuMuc, path, "do", "https://vi.du/runs/111", "dev", bangChung, Map.of());
        KetQua lan2 = chayScript(
                timTuGocKho(SCRIPT), thuMuc, path, "do", "https://vi.du/runs/222", "dev", bangChung, Map.of());

        String than1 = boUrl(thanCua(lan1, "issue comment 42"));
        String than2 = boUrl(thanCua(lan2, "issue comment 42"));
        assertThat(than1)
                .as("Thân KHÔNG được chứa ngày giờ hay số lượt — chỉ URL được khác")
                .isEqualTo(than2);
        assertThat(thanCua(lan1, "issue comment 42")).isNotEqualTo(thanCua(lan2, "issue comment 42"));
    }

    @Test
    @DisplayName("⭐ G12 · BẤT BIẾN chiều 2: khác tập CVE → thân KHÁC sau khi bỏ URL")
    void khacTapThiKhacThan(@TempDir Path thuMuc) throws Exception {
        KetQua bay = chayDayDu(
                thuMuc, ISSUE_42, VIEW_TRONG, "do", "dev", bangChung(thuMuc, MA7_MOC, DUOI7_MOC, 0), Map.of());
        KetQua tam = chayDayDu(
                thuMuc,
                ISSUE_42,
                VIEW_TRONG,
                "do",
                "dev",
                bangChung(thuMuc, List.of("CVE-2026-47890", "CVE-2026-47891", "CVE-2026-59283"), DUOI7_MOC, 0),
                Map.of());

        assertThat(boUrl(thanCua(bay, "issue comment 42")))
                .as("Hai tập CVE khác nhau mà cùng một thân là đúng cái một-bit của 3/9→5/9")
                .isNotEqualTo(boUrl(thanCua(tam, "issue comment 42")));
    }

    @Test
    @DisplayName("⭐⭐ G13 · TỰ KIỂM CỦA TỰ KIỂM: thay `van_tay()` bằng hằng số thì ca LEO THANG phải MÙ")
    void tuKiemCuaTuKiem(@TempDir Path thuMuc) throws Exception {
        String nguon = doc(timTuGocKho(SCRIPT));
        long soDongVanTay =
                nguon.lines().filter(d -> d.startsWith("van_tay() {")).count();
        assertThat(soDongVanTay)
                .as("`van_tay()` phải là đúng MỘT dòng — đó là điểm đột biến của bài này")
                .isEqualTo(1);

        String dotBien = nguon.lines()
                        .map(d -> d.startsWith("van_tay() {") ? "van_tay() { printf 'HANG-SO'; }" : d)
                        .collect(Collectors.joining("\n"))
                + "\n";
        long truoc = dem(nguon, "HANG-SO");
        long sau = dem(dotBien, "HANG-SO");
        assertThat(truoc).as("grep -c HANG-SO ở bản gốc").isZero();
        assertThat(sau).as("grep -c HANG-SO ở bản đột biến").isEqualTo(1);
        Path scriptDotBien = Files.writeString(thuMuc.resolve("bao-dong-dot-bien.sh"), dotBien, StandardCharsets.UTF_8);

        // Đúng ca G2 (thêm một mã ≥ 7 so với mốc) nhưng chạy trên bản đột biến.
        Path bin = dungGhGia(thuMuc, ISSUE_42, viewCoMoc(MA7_MOC, DUOI7_MOC, "ok", "success", 0));
        KetQua kq = chayScript(
                scriptDotBien,
                thuMuc,
                bin + java.io.File.pathSeparator + System.getenv("PATH"),
                "do",
                URL,
                "dev",
                bangChung(thuMuc, List.of("CVE-2026-47890", "CVE-2026-47891", "CVE-2026-59283"), DUOI7_MOC, 0),
                Map.of());

        assertThat(kq.maThoat()).as("Đầu ra:\n%s", kq.dauRa()).isZero();
        assertThat(kq.lenhGh())
                .as(
                        """
                        Bản đột biến (vân tay là hằng số) mà VẪN bình luận LEO THANG thì bài G2 không hề \
                        phụ thuộc vào hàm vân tay — nó chỉ đang bắt khác nhau ở URL hay ở chữ. Bài G2 chỉ \
                        có ý nghĩa khi bài này chứng minh được nó MÙ ở đây.""")
                .noneMatch(l -> l.startsWith("issue comment"));
    }

    @Test
    @DisplayName(
            "G14 · phạm vi quét THIẾU một jar, tập CVE không đổi → vẫn là 'đổi', thân nêu jar, mốc ghi `phu=thieu-1`")
    void phamViThieuLaMotThayDoi(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(
                thuMuc,
                ISSUE_42,
                viewCoMoc(MA7_MOC, DUOI7_MOC, "ok", "success", 0),
                "do",
                "dev",
                bangChung(thuMuc, MA7_MOC, DUOI7_MOC, 1),
                Map.of());

        assertThat(kq.maThoat()).isZero();
        assertThat(thanCua(kq, "issue comment 42")).contains("THIẾU").contains("x-bi-bo-sot-1.jar");
        assertThat(lenhDauTien(kq, "issue edit 42")).contains("phu=thieu-1");
    }

    @Test
    @DisplayName("G15 · `npm audit` đổi màu, tập CVE không đổi → là một thay đổi, thân nhắc npm")
    void npmDoiLaMotThayDoi(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(
                thuMuc,
                ISSUE_42,
                viewCoMoc(MA7_MOC, DUOI7_MOC, "ok", "success", 0),
                "do",
                "dev",
                bangChung(thuMuc, MA7_MOC, DUOI7_MOC, 0),
                Map.of("KQ_NPM", "failure"));

        assertThat(kq.maThoat()).isZero();
        assertThat(thanCua(kq, "issue comment 42")).contains("npm").contains("failure");
        assertThat(lenhDauTien(kq, "issue edit 42")).contains("npm=failure");
    }

    @Test
    @DisplayName("G18 · body trả về dạng JSON escape `\\u003c!-- … --\\u003e` vẫn đọc ra mốc → im lặng")
    void mocEscapeVanDocDuoc(@TempDir Path thuMuc) throws Exception {
        String view = viewCoMoc(MA7_MOC, DUOI7_MOC, "ok", "success", 0)
                .replace("<!--", "\\u003c!--")
                .replace("-->", "--\\u003e");
        KetQua kq = chayDayDu(thuMuc, ISSUE_42, view, "do", "dev", bangChung(thuMuc, MA7_MOC, DUOI7_MOC, 0), Map.of());

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh())
                .as("Mẫu đọc mốc không được phụ thuộc vào việc gh có escape HTML trong JSON hay không")
                .noneMatch(l -> l.startsWith("issue comment") || l.startsWith("issue edit"));
    }

    @Test
    @DisplayName("⛔⛔ G19 · xanh mà KHÔNG có tệp vân tay + đang có issue → KHÔNG đóng, bình luận, exit ≠ 0")
    void xanhKhongBangChungThiKhongDong(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(thuMuc, ISSUE_42, VIEW_TRONG, "xanh", "dev", List.of(), Map.of("KQ_OWASP", "success"));

        assertThat(kq.maThoat())
                .as(
                        """
                        Thiếu `NVD_API_KEY` ⇒ mọi bước OWASP `skipped` ⇒ job `success` ⇒ chuông cũ đọc là \
                        xanh và ĐÓNG issue #84 trong khi chẳng quét gì — cùng họ T11.81. "Xanh" phải mang \
                        bằng chứng, và một lượt xanh không bằng chứng phải ĐỎ ở chính job chuông.""")
                .isNotZero();
        assertThat(kq.lenhGh()).noneMatch(l -> l.startsWith("issue close"));
        assertThat(thanCua(kq, "issue comment 42")).contains("KHÔNG có bằng chứng");
    }

    @Test
    @DisplayName("G20 · xanh mà KHÔNG có tệp vân tay + chưa có issue → mở issue, exit ≠ 0")
    void xanhKhongBangChungChuaCoIssueThiMo(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chayDayDu(thuMuc, KHONG_ISSUE, VIEW_TRONG, "xanh", "dev", List.of(), Map.of());

        assertThat(kq.maThoat()).isNotZero();
        assertThat(lenhDauTien(kq, "issue create")).contains("KHÔNG có bằng chứng");
    }

    // ── Hạ tầng ────────────────────────────────────────────────────────────────────────────────

    private record KetQua(int maThoat, String dauRa, List<String> lenhGh) {}

    private static int viTriDau(List<String> danhSach, String tienTo) {
        for (int i = 0; i < danhSach.size(); i++) {
            if (danhSach.get(i).startsWith(tienTo)) {
                return i;
            }
        }
        return -1;
    }

    private static String lenhDauTien(KetQua kq, String tienTo) {
        int i = viTriDau(kq.lenhGh(), tienTo);
        if (i < 0) {
            return fail("Không có lệnh gh nào bắt đầu bằng `%s`. Các lệnh: %s\nĐầu ra:\n%s"
                    .formatted(
                            tienTo,
                            kq.lenhGh().stream()
                                    .map(l -> l.lines().findFirst().orElse(""))
                                    .toList(),
                            kq.dauRa()));
        }
        return kq.lenhGh().get(i);
    }

    /** Phần sau {@code --body } của lệnh {@code gh} đầu tiên bắt đầu bằng tiền tố cho trước. */
    private static String thanCua(KetQua kq, String tienTo) {
        String lenh = lenhDauTien(kq, tienTo);
        int viTri = lenh.indexOf("--body ");
        return viTri < 0 ? fail("Lệnh không có --body: " + lenh) : lenh.substring(viTri + "--body ".length());
    }

    private static String boUrl(String than) {
        return than.replaceAll("https?://\\S+", "URL");
    }

    private static long dem(String vanBan, String chuoi) {
        return vanBan.lines().filter(d -> d.contains(chuoi)).count();
    }

    private static String trichNhan(String script) {
        Matcher m = Pattern.compile("(?m)^NHAN='([^']+)'").matcher(script);
        return m.find() ? m.group(1) : fail("Không đọc được hằng NHAN từ script");
    }

    /** Tệp vân tay giả theo đúng định dạng {@code van-tay-cve.sh} sinh ra. */
    private static Path tepVanTay(Path thuMuc, List<String> ge7, List<String> duoi7, int suppress) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("ge7=").append(ge7.size()).append('\n');
        sb.append("tong=").append(ge7.size() + duoi7.size()).append('\n');
        sb.append("suppress=").append(suppress).append('\n');
        for (String ma : new TreeSet<>(ge7)) {
            sb.append(ma).append("\t9.8\t>=7\tspring-core-6.2.19.jar,spring-web-6.2.19.jar\n");
        }
        for (String ma : new TreeSet<>(duoi7)) {
            sb.append(ma).append("\t5.3\t<7\tkotlin-stdlib-1.9.25.jar\n");
        }
        return Files.writeString(thuMuc.resolve("van-tay-cve.txt"), sb.toString(), StandardCharsets.UTF_8);
    }

    /** Tệp phạm vi giả theo đúng định dạng {@code phu-quet-cve.sh} sinh ra. */
    private static Path tepPhuQuet(Path thuMuc, int thieu) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("runtime=120 phu=")
                .append(115 - thieu)
                .append(" ngoai=5 thieu=")
                .append(thieu)
                .append('\n');
        sb.append("NGOAI: songnhue-core-0.1.0-SNAPSHOT.jar\n");
        for (int i = 1; i <= thieu; i++) {
            sb.append("THIEU: x-bi-bo-sot-").append(i).append(".jar\n");
        }
        return Files.writeString(thuMuc.resolve("phu-quet-cve.txt"), sb.toString(), StandardCharsets.UTF_8);
    }

    private static List<String> bangChung(Path thuMuc, List<String> ge7, List<String> duoi7, int thieu)
            throws IOException {
        return List.of(
                tepVanTay(thuMuc, ge7, duoi7, 0).toString(),
                tepPhuQuet(thuMuc, thieu).toString());
    }

    private static List<String> bangChungXanh(Path thuMuc) throws IOException {
        return bangChung(thuMuc, List.of(), List.of(), 0);
    }

    /** JSON của {@code gh issue view --json body} với một mốc đúng định dạng script ghi ra. */
    private static String viewCoMoc(List<String> ma7, List<String> duoi7, String phu, String npm, int suppress) {
        TreeSet<String> tatCa = new TreeSet<>(ma7);
        tatCa.addAll(duoi7);
        String moc = "<!-- van-tay-cve ge7=%d tong=%d suppress=%d npm=%s phu=%s ma7=%s ma=%s -->"
                .formatted(
                        ma7.size(),
                        tatCa.size(),
                        suppress,
                        npm,
                        phu,
                        String.join(",", new TreeSet<>(ma7)),
                        String.join(",", tatCa));
        return "{\"body\":\"Lượt quét trên dev ĐỎ.\\n\\n| CVE | ... |\\n\\n" + moc + "\\n\"}";
    }

    private static KetQua chay(Path thuMuc, String jsonDanhSach, String trangThai) throws Exception {
        Path bin = dungGhGia(thuMuc, jsonDanhSach, VIEW_TRONG);
        return chayVoiPath(thuMuc, bin + java.io.File.pathSeparator + System.getenv("PATH"), trangThai);
    }

    private static KetQua chayDayDu(
            Path thuMuc,
            String jsonDanhSach,
            String jsonView,
            String trangThai,
            String nhanh,
            List<String> thamSo,
            Map<String, String> env)
            throws Exception {
        Path bin = dungGhGia(thuMuc, jsonDanhSach, jsonView);
        return chayScript(
                timTuGocKho(SCRIPT),
                thuMuc,
                bin + java.io.File.pathSeparator + System.getenv("PATH"),
                trangThai,
                URL,
                nhanh,
                thamSo,
                env);
    }

    /**
     * Dựng một {@code gh} giả: ghi nguyên văn argv ra tệp (phân cách bằng RS — thân bình luận nhiều
     * dòng nên không tách được bằng xuống dòng), với {@code issue list} thì in danh sách đóng hộp, với
     * {@code issue view} thì in body đóng hộp. Nhờ vậy mọi nhánh quyết định kiểm được bằng dữ liệu giả.
     */
    private static Path dungGhGia(Path thuMuc, String jsonDanhSach, String jsonView) throws IOException {
        Path bin = Files.createDirectories(thuMuc.resolve("bin"));
        Path nhatKy = thuMuc.resolve("argv.txt");
        Path list = thuMuc.resolve("list.json");
        Path view = thuMuc.resolve("view.json");
        Files.writeString(list, jsonDanhSach, StandardCharsets.UTF_8);
        Files.writeString(view, jsonView, StandardCharsets.UTF_8);
        Files.writeString(nhatKy, "", StandardCharsets.UTF_8);

        Path gh = bin.resolve("gh");
        Files.writeString(
                gh,
                """
                #!/usr/bin/env bash
                printf '%%s\\036' "$*" >> '%s'
                if [ "$1" = issue ] && [ "$2" = list ]; then cat '%s'; fi
                if [ "$1" = issue ] && [ "$2" = view ]; then cat '%s'; fi
                """
                        .formatted(nhatKy, list, view),
                StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(gh, PosixFilePermissions.fromString("rwxr-xr-x"));
        return bin;
    }

    private static KetQua chayVoiPath(Path thuMuc, String path, String trangThai) throws Exception {
        return chayVoiPath(thuMuc, path, trangThai, "dev");
    }

    private static KetQua chayVoiPath(Path thuMuc, String path, String trangThai, String nhanh) throws Exception {
        return chayScript(timTuGocKho(SCRIPT), thuMuc, path, trangThai, URL, nhanh, List.of(), Map.of());
    }

    private static KetQua chayScript(
            Path script,
            Path thuMuc,
            String path,
            String trangThai,
            String url,
            String nhanh,
            List<String> thamSo,
            Map<String, String> env)
            throws Exception {
        List<String> lenh = new ArrayList<>(Arrays.asList("/bin/bash", script.toString(), trangThai, url));
        lenh.addAll(thamSo);

        // ⚠ `/bin/bash` TUYỆT ĐỐI, không dựa vào PATH: bài E cố tình dựng một PATH rỗng để giấu
        //   `gh`, và nếu bash cũng phải tra qua PATH thì bài ấy hỏng vì lý do khác hẳn thứ nó đo.
        //   Cũng không dùng shebang — shell mặc định của máy dev là zsh (CLAUDE.md luật 20).
        ProcessBuilder pb = new ProcessBuilder(lenh);
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().put("PATH", path);
        // ⚠ Môi trường bị xoá sạch nên `NHANH` PHẢI được đặt tường minh — thiếu nó thì mọi bài
        //   "xanh ⇒ đóng" đỏ vì lý do khác hẳn thứ chúng đo (luật 9).
        pb.environment().put("NHANH", nhanh);
        pb.environment().put("KQ_OWASP", "do".equals(trangThai) ? "failure" : "success");
        pb.environment().put("KQ_NPM", "success");
        pb.environment().putAll(env);

        // Mỗi lượt chạy đọc nhật ký của RIÊNG nó: bài G11 chạy hai lượt trên cùng thư mục, và nhật ký
        // cộng dồn làm lượt 2 đọc nhầm bình luận của lượt 1 (bắt được ở lượt viết đầu).
        Path nhatKy = thuMuc.resolve("argv.txt");
        Files.writeString(nhatKy, "", StandardCharsets.UTF_8);

        Process p = pb.start();
        String dauRa = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("Script không kết thúc trong 30 giây");
        }

        List<String> lenhGh = Files.exists(nhatKy)
                ? Arrays.stream(Files.readString(nhatKy, StandardCharsets.UTF_8).split("\u001e"))
                        .filter(d -> !d.isBlank())
                        .toList()
                : List.of();
        return new KetQua(p.exitValue(), dauRa, lenhGh);
    }

    private static List<String> danhSachJob() {
        return docKhoaJob(doc(timTuGocKho(WORKFLOW)));
    }

    private static List<String> docKhoaJob(String noiDung) {
        // Chỉ lấy phần sau `jobs:` để `on:`/`permissions:` cấp một không lọt vào.
        int viTri = noiDung.indexOf("\njobs:");
        String phanJob = viTri >= 0 ? noiDung.substring(viTri) : noiDung;
        List<String> ket = new ArrayList<>();
        Matcher m = KHOA_JOB.matcher(phanJob);
        while (m.find()) {
            ket.add(m.group(1));
        }
        return ket;
    }

    private static List<String> needsCuaChuong() {
        return docNeeds(doc(timTuGocKho(WORKFLOW)));
    }

    private static List<String> docNeeds(String noiDung) {
        Matcher m = Pattern.compile("(?m)^\\s{4}needs:\\s*\\[([^\\]]*)\\]").matcher(noiDung);
        if (!m.find()) {
            return List.of();
        }
        return Arrays.stream(m.group(1).split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Thân của một job: từ dòng khoá job tới dòng khoá job cấp một kế tiếp. */
    private static String thanJob(String tenJob) {
        String noiDung = doc(timTuGocKho(WORKFLOW));
        Matcher m = Pattern.compile("(?m)^  " + Pattern.quote(tenJob) + ":$").matcher(noiDung);
        if (!m.find()) {
            return fail("Không tìm thấy job `%s` trong security-scan.yml".formatted(tenJob));
        }
        int batDau = m.end();
        Matcher ke = KHOA_JOB.matcher(noiDung);
        int ketThuc = noiDung.length();
        if (ke.find(batDau)) {
            ketThuc = ke.start();
        }
        return noiDung.substring(batDau, ketThuc);
    }

    /**
     * Khối một bước (bắt đầu bằng {@code - } ở thụt 6) trong thân job có chứa chuỗi cho trước — đã <b>bỏ
     * mọi dòng chú thích</b>, vì chú thích của bước kế tiếp nằm trong cùng khối (luật 2; đo được 6/9 ở
     * {@code VanTayCveTest}).
     */
    private static String buocChua(String thanJob, String chuoi) {
        for (String buoc : thanJob.split("(?m)^      - ")) {
            String khongChuThich =
                    buoc.lines().filter(d -> !d.strip().startsWith("#")).collect(Collectors.joining("\n"));
            if (khongChuThich.contains(chuoi)) {
                return khongChuThich;
            }
        }
        return fail("Không có bước nào trong job chứa `%s`".formatted(chuoi));
    }

    /** Giá trị {@code name:} trong khối {@code with:} của một bước upload/download-artifact. */
    private static String tenArtifact(String buoc) {
        Matcher m = Pattern.compile("(?m)^\\s{10}name:\\s*(\\S+)\\s*$").matcher(buoc);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Khối {@code permissions:} của một thân job, đã <b>bỏ mọi dòng chú thích</b>.
     *
     * <p>Trả về chuỗi rỗng khi job không khai {@code permissions:} — hai trạng thái ấy khác nhau và
     * bài kiểm phân biệt được cả hai (luật 9).
     */
    private static String khoiQuyenCua(String thanJob) {
        List<String> ket = new ArrayList<>();
        boolean trongKhoi = false;
        for (String dong : thanJob.split("\n", -1)) {
            String khongChuThich = dong.replaceFirst("#.*$", "");
            if (khongChuThich.strip().equals("permissions:")) {
                trongKhoi = true;
                continue;
            }
            if (trongKhoi) {
                if (khongChuThich.isBlank()) {
                    if (dong.isBlank()) {
                        break;
                    }
                    continue;
                }
                int thut = khongChuThich.length() - khongChuThich.stripLeading().length();
                if (thut <= 4) {
                    break;
                }
                ket.add(khongChuThich.strip());
            }
        }
        return String.join("\n", ket);
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + duongDan, e);
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
    }
}
