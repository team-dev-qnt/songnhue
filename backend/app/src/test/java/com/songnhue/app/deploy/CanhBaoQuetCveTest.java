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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>Chuông báo lượt quét bảo mật đỏ — chạy THẬT script với một {@code gh} giả.</b>
 *
 * <h2>Cái sai bài này sinh ra để chặn</h2>
 *
 * Lượt quét theo lịch 2/9/2026 07:06 UTC đỏ với 4 mã CVSS ≥ 7. Tới 3/9 vẫn không ai biết — lần thứ
 * <b>ba trong năm ngày</b>, và nhịp đỏ 6 lượt {@code schedule} gần nhất trên {@code dev} là 4/6.
 *
 * <p>Đo {@code security-scan.yml} xem nó phát ra gì khi đỏ, kết quả là <b>không gì cả</b>:
 * {@code if: failure()} = 0 · {@code gh issue create} = 0 · webhook/smtp = 0 ·
 * {@code permissions: contents: read} nên muốn mở issue cũng <b>403</b>. Dòng ghi
 * {@code $GITHUB_STEP_SUMMARY} duy nhất nằm trong nhánh <i>thiếu {@code NVD_API_KEY}</i> — nhánh
 * không bao giờ chạy khi khoá có mặt.
 *
 * <h2>Vì sao chạy script thay vì đọc chữ trong YAML</h2>
 *
 * Một bài khẳng định <i>"workflow có chứa chuỗi {@code gh issue create}"</i> sẽ xanh với cả một
 * chuông đúng lẫn một chuông gọi lệnh ấy ở nhánh không bao giờ tới. Nó không phân biệt được hai
 * trạng thái, nên nó không khẳng định gì (CLAUDE.md luật 9). Ở đây script được <b>gọi</b>, và thứ
 * được khẳng định là <b>lệnh {@code gh} nào thật sự chạy</b>.
 *
 * <h2>⛔ Giới hạn của chính bài kiểm này (luật 28)</h2>
 *
 * Nó chứng minh <b>dây đã nối</b>. Nó KHÔNG chứng minh <b>GitHub đã giao</b> — nửa sau chỉ đo được
 * bằng một lượt chạy thật. Và nó không phủ trường hợp <b>lượt quét không chạy</b>: {@code if:
 * failure()} chỉ phát khi có lượt để mà đỏ (luật 31 — thứ nguy hiểm là sự vắng mặt).
 */
class CanhBaoQuetCveTest {

    private static final String TEN_JOB_CHUONG = "bao-dong";

    /** Khoá job cấp một trong {@code security-scan.yml}: đúng hai dấu cách thụt đầu. */
    private static final Pattern KHOA_JOB = Pattern.compile("(?m)^  ([a-z][a-z0-9-]*):$");

    // ── Nhóm 1: hành vi thật của script ────────────────────────────────────────────────────────

    @Test
    @DisplayName("A · đỏ + chưa có issue mốc → MỞ issue")
    void doVaChuaCoThiMo(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "[]", "do");

        assertThat(kq.maThoat())
                .as("Script phải chạy trót lọt:\n%s", kq.dauRa())
                .isZero();
        assertThat(kq.lenhGh())
                .as("Không có issue mốc nào thì phải MỞ một cái")
                .anyMatch(l -> l.startsWith("issue create"));
        assertThat(kq.lenhGh()).noneMatch(l -> l.startsWith("issue comment"));
    }

    @Test
    @DisplayName("B · đỏ + ĐÃ có issue mốc → bình luận, KHÔNG mở thêm")
    void doVaDaCoThiBinhLuan(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "[{\"number\":42}]", "do");

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh()).anyMatch(l -> l.startsWith("issue comment 42"));
        assertThat(kq.lenhGh())
                .as(
                        """
                        Mở issue thứ hai khi đã có một cái đang mở là cách nhanh nhất để người ta \
                        thôi đọc: sau một tuần đỏ liên tiếp sẽ có bảy issue nói cùng một chuyện. \
                        Một issue mốc, mỗi lượt đỏ một dòng bình luận.""")
                .noneMatch(l -> l.startsWith("issue create"));
    }

    @Test
    @DisplayName("C · xanh + đang có issue mốc → ĐÓNG nó")
    void xanhThiDong(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "[{\"number\":42}]", "xanh");

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh()).anyMatch(l -> l.startsWith("issue close 42"));
        // Bình luận phải đứng TRƯỚC lượt đóng, nếu không dòng giải thích rơi vào một issue đã đóng.
        int viTriBinhLuan = viTriDau(kq.lenhGh(), "issue comment 42");
        int viTriDong = viTriDau(kq.lenhGh(), "issue close 42");
        assertThat(viTriBinhLuan)
                .as("Phải bình luận trước khi đóng")
                .isGreaterThanOrEqualTo(0)
                .isLessThan(viTriDong);
    }

    @Test
    @DisplayName("D · xanh + không có issue nào → không làm gì cả")
    void xanhVaSachThiImLang(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "[]", "xanh");

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh())
                .as("Lượt quét xanh và không có issue nào thì chuông không được tạo ra tiếng động")
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
        KetQua kq = chay(thuMuc, "[]", "khong-phai-do-cung-khong-phai-xanh");

        assertThat(kq.maThoat()).isNotZero();
        assertThat(kq.lenhGh()).isEmpty();
    }

    @Test
    @DisplayName("⭐ Nhãn tiêu đề định nghĩa ĐÚNG MỘT LẦN — nhánh mở và nhánh đóng tìm cùng chuỗi")
    void nhanChiDinhNghiaMotLan(@TempDir Path thuMuc) throws Exception {
        String script = doc(timTuGocKho(".github/scripts/bao-dong-quet-cve.sh"));

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
        KetQua kq = chay(thuMuc, "[]", "do");
        String nhan = trichNhan(script);
        assertThat(kq.lenhGh()).anyMatch(l -> l.startsWith("issue create") && l.contains(nhan));
        assertThat(kq.lenhGh()).anyMatch(l -> l.contains("in:title " + nhan));
    }

    @Test
    @DisplayName("⛔⛔ T11.81 · XANH trên NHÁNH PHỤ → KHÔNG được đóng issue mốc")
    void xanhTrenNhanhPhuThiKhongDong(@TempDir Path thuMuc) throws Exception {
        Path bin = dungGhGia(thuMuc, "[{\"number\":42}]");
        KetQua kq = chayVoiPath(
                thuMuc, bin + java.io.File.pathSeparator + System.getenv("PATH"), "xanh", "fix/mot-nhanh-va");

        assertThat(kq.maThoat()).as("Đầu ra:\n%s", kq.dauRa()).isZero();
        assertThat(kq.lenhGh())
                .as(
                        """
                        Đo 05/09: script không đọc một biến nhánh nào, nên nhánh XANH đóng issue mốc \
                        bất kể lượt quét chạy ở đâu — và lượt nhánh phụ CÓ với tới chuông thật \
                        (bình luận #5 của issue #84 đến từ `fix/t11-76-jackson-bom` qua \
                        `workflow_dispatch`).

                        Hệ quả rơi đúng vào lúc nguy hiểm nhất: làm T11.69 thì người ta bấm \
                        `workflow_dispatch` trên nhánh vá để xem đã sạch chưa; lượt ấy xanh ⇒ đóng \
                        issue #84 trong khi `dev` VẪN ĐỎ. Chuông tự tắt mình, và lượt theo lịch hôm \
                        sau mở một issue MỚI — số issue thôi khớp số lượt đỏ, phá đúng vòng khứ hồi \
                        mà T11.58 dựng ra để tự nghiệm thu.""")
                .noneMatch(l -> l.startsWith("issue close"));
    }

    @Test
    @DisplayName("⭐ Đối chứng: cùng đầu vào ấy nhưng trên `dev` thì PHẢI đóng (luật 9)")
    void xanhTrenDevThiVanDong(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "[{\"number\":42}]", "xanh");

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh())
                .as(
                        """
                        Không có bài này thì bài trên xanh cả khi ai đó vô hiệu hoá hẳn nhánh đóng — \
                        một khẳng định không phân biệt được hai trạng thái thì không khẳng định gì.""")
                .anyMatch(l -> l.startsWith("issue close 42"));
    }

    @Test
    @DisplayName("⭐ ĐỎ trên nhánh phụ VẪN phải bình luận — mở rộng tay, đóng chặt tay")
    void doTrenNhanhPhuVanBinhLuan(@TempDir Path thuMuc) throws Exception {
        Path bin = dungGhGia(thuMuc, "[{\"number\":42}]");
        KetQua kq =
                chayVoiPath(thuMuc, bin + java.io.File.pathSeparator + System.getenv("PATH"), "do", "fix/mot-nhanh-va");

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh())
                .as("Một nhánh phụ phát hiện THÊM mã thì vẫn đáng nói — bất đối xứng này là cố ý")
                .anyMatch(l -> l.startsWith("issue comment 42"));
    }

    @Test
    @DisplayName("⭐ Nhánh mốc định nghĩa ĐÚNG MỘT LẦN, và workflow có truyền `NHANH` vào")
    void nhanhMocMotLanVaWorkflowTruyenVao() {
        String script = doc(timTuGocKho(".github/scripts/bao-dong-quet-cve.sh"));
        long soLanGan =
                script.lines().filter(d -> d.strip().startsWith("NHANH_MOC=")).count();
        assertThat(soLanGan)
                .as("Hai hằng rời nhau là dựng lại luật 14 — nhánh kiểm một chuỗi, thông báo in chuỗi khác")
                .isEqualTo(1);

        String yml = doc(timTuGocKho(".github/workflows/security-scan.yml"));
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
        // ⚠ Ngưỡng ở vế này là **1**, không phải 2 — cố ý.
        //
        //   Đo được lúc kiểm chứng ngược: đặt ngưỡng 2 thì lượt phá "bỏ `npm` khỏi `needs`" làm bài
        //   đỏ vì vấp CHÍNH VẾ NÀY, và thông báo in ra là *"không đọc ra needs"* — trong khi `needs`
        //   đọc được bình thường, chỉ là thiếu một job. Bài vẫn đỏ, nhưng nó **gọi sai tên nguyên
        //   nhân**, và người đọc sau sẽ đi truy regex thay vì truy job bị bỏ quên (§10.68-B: bước
        //   SSH đỏ 6/6 mà `2>/dev/null` vứt mất lý do).
        //
        //   Vế này chỉ có một việc: chứng minh regex còn đọc được. Việc phát hiện thiếu job là của
        //   phép so hai chiều bên dưới, và nó phải là thứ lên tiếng.
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

        // ⚠ Khẳng định trên KHỐI `permissions:`, không trên toàn thân job.
        //
        //   Đo được lúc viết bài này: chuỗi `issues: write` xuất hiện **2 lần** trong workflow —
        //   một lần là quyền thật, một lần nằm trong dòng chú thích giải thích vì sao cần nó. Một
        //   bài `than.contains("issues: write")` sẽ xanh cả khi ai đó xoá dòng quyền và giữ lại
        //   dòng chú thích. Đó đúng là CLAUDE.md luật 2: canh cấu trúc, đừng canh văn bản — và
        //   `includes('.sn-align-center')` từng xanh sau khi thuộc tính đã bị xoá hẳn.
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
                        **403 trong im lặng** — cùng hình dạng §10.57 (cổng secret bỏ qua trong im \
                        lặng). Đây là bài đắt nhất của cả lớp: mọi bài khác vẫn xanh trong khi chuông \
                        câm.

                        Khối đọc được:
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
        String workflow = doc(timTuGocKho(".github/workflows/security-scan.yml"));

        assertThat(workflow)
                .as(
                        """
                        `paths:` không bao chính `security-scan.yml` thì sửa ngưỡng `--audit-level` \
                        hay sửa nhánh báo động chỉ được kiểm ở lượt theo lịch HÔM SAU (§10.69). \
                        `SuppressionPolicyTest` đã bắt đúng lỗi ấy cho tệp suppression.""")
                .contains("'.github/workflows/security-scan.yml'");

        assertThat(workflow).contains("'.github/scripts/bao-dong-quet-cve.sh'");
    }

    // ── Nhóm 3: tự kiểm chứng — bài này có bắt được vi phạm không? ──────────────────────────────

    @Test
    @DisplayName("⭐ TỰ KIỂM: bỏ một job quét khỏi `needs` thì phép so hai chiều phải bắt")
    void tuKiemChung() {
        // Dựng một `security-scan.yml` GIẢ thiếu `npm` trong `needs`, rồi chạy đúng phép so của
        // bài `chuongPhuMoiJobQuet` lên nó. Không dùng lại regex của bài kia một cách mù quáng —
        // đây là chỗ chứng minh regex ấy thật sự phân biệt được hai trạng thái (luật 29).
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
        // Đây là bài tự kiểm đắt nhất của cả lớp, và nó neo vào một phép đo thật: chuỗi
        // `issues: write` có mặt **2 lần** trong `security-scan.yml` — một quyền thật, một chú
        // thích. Một bộ dò khớp văn bản sẽ không phân biệt được hai bản dưới đây.
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

    private static String trichNhan(String script) {
        Matcher m = Pattern.compile("(?m)^NHAN='([^']+)'").matcher(script);
        return m.find() ? m.group(1) : fail("Không đọc được hằng NHAN từ script");
    }

    private static KetQua chay(Path thuMuc, String jsonDanhSach, String trangThai) throws Exception {
        Path bin = dungGhGia(thuMuc, jsonDanhSach);
        return chayVoiPath(thuMuc, bin + java.io.File.pathSeparator + System.getenv("PATH"), trangThai);
    }

    /**
     * Dựng một {@code gh} giả: ghi nguyên văn argv ra tệp, và với {@code issue list} thì in ra JSON
     * đóng hộp. Nhờ vậy nhánh quyết định của script kiểm được bằng dữ liệu giả thay vì phải có một
     * lượt quét đỏ thật.
     */
    private static Path dungGhGia(Path thuMuc, String jsonDanhSach) throws IOException {
        Path bin = Files.createDirectories(thuMuc.resolve("bin"));
        Path nhatKy = thuMuc.resolve("argv.txt");
        Path json = thuMuc.resolve("list.json");
        Files.writeString(json, jsonDanhSach, StandardCharsets.UTF_8);
        Files.writeString(nhatKy, "", StandardCharsets.UTF_8);

        Path gh = bin.resolve("gh");
        Files.writeString(
                gh,
                """
                #!/usr/bin/env bash
                printf '%%s\\n' "$*" >> '%s'
                if [ "$1" = issue ] && [ "$2" = list ]; then cat '%s'; fi
                """
                        .formatted(nhatKy, json),
                StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(gh, PosixFilePermissions.fromString("rwxr-xr-x"));
        return bin;
    }

    private static KetQua chayVoiPath(Path thuMuc, String path, String trangThai) throws Exception {
        return chayVoiPath(thuMuc, path, trangThai, "dev");
    }

    private static KetQua chayVoiPath(Path thuMuc, String path, String trangThai, String nhanh) throws Exception {
        Path script = timTuGocKho(".github/scripts/bao-dong-quet-cve.sh");

        // ⚠ `/bin/bash` TUYỆT ĐỐI, không dựa vào PATH: bài E cố tình dựng một PATH rỗng để giấu
        //   `gh`, và nếu bash cũng phải tra qua PATH thì bài ấy hỏng vì lý do khác hẳn thứ nó đo.
        //   Cũng không dùng shebang — shell mặc định của máy dev là zsh (CLAUDE.md luật 20).
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", script.toString(), trangThai, "https://vi.du/runs/1");
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().put("PATH", path);
        // ⚠ Môi trường bị xoá sạch nên `NHANH` PHẢI được đặt tường minh — thiếu nó thì mọi bài
        //   "xanh ⇒ đóng" đỏ vì lý do khác hẳn thứ chúng đo (luật 9).
        pb.environment().put("NHANH", nhanh);

        Process p = pb.start();
        String dauRa = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("Script không kết thúc trong 30 giây");
        }

        Path nhatKy = thuMuc.resolve("argv.txt");
        List<String> lenh = Files.exists(nhatKy)
                ? Files.readAllLines(nhatKy, StandardCharsets.UTF_8).stream()
                        .filter(d -> !d.isBlank())
                        .toList()
                : List.of();
        return new KetQua(p.exitValue(), dauRa, lenh);
    }

    private static List<String> danhSachJob() {
        return docKhoaJob(doc(timTuGocKho(".github/workflows/security-scan.yml")));
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
        return docNeeds(doc(timTuGocKho(".github/workflows/security-scan.yml")));
    }

    private static List<String> docNeeds(String noiDung) {
        Matcher m = Pattern.compile("(?m)^\\s{4}needs:\\s*\\[([^\\]]*)\\]").matcher(noiDung);
        if (!m.find()) {
            return List.of();
        }
        return java.util.Arrays.stream(m.group(1).split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Thân của một job: từ dòng khoá job tới dòng khoá job cấp một kế tiếp. */
    private static String thanJob(String tenJob) {
        String noiDung = doc(timTuGocKho(".github/workflows/security-scan.yml"));
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
                    // Dòng chú thích thuần hoặc dòng trống: bỏ qua, khối chưa kết thúc.
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
