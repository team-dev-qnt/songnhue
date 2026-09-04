package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>Gắn tag SHA cho image không đổi — chạy THẬT script với một {@code docker} giả.</b>
 *
 * <h2>Cái sai bài này sinh ra để chặn — T11.78</h2>
 *
 * Lượt CI {@code 33881305079} trên {@code dev} (4/9/2026, đỉnh {@code f165f06}) đỏ ở job
 * {@code Gắn tag SHA cho image không đổi}, kéo theo {@code Cổng kiểm CI} — cổng bắt buộc
 * <b>duy nhất</b> của {@code dev}. Thông báo in ra:
 *
 * <pre>app chưa có tag `dev` — … Xảy ra ở commit ĐẦU TIÊN của một gói GHCR mới.</pre>
 *
 * <p>Khẳng định ấy <b>sai</b>, và log của chính lượt chạy ấy chứng minh: job đóng gói xanh, đẩy
 * {@code app:dev@sha256:075d43d9…} lúc 14:07:42; bước gắn tag mới bắt đầu lúc 14:09:58 — tag đã
 * tồn tại trước đó <b>2 phút 16 giây</b>. Nguyên nhân thật nằm ở dòng ngay <i>trước</i> dòng lỗi
 * (CLAUDE.md luật 23): {@code error pinging v2 registry … Client.Timeout exceeded}. Chạy lại
 * không đổi một dòng mã thì xanh, và bước ấy chạy <b>10 giây</b> thay vì 3 phút 20 hết giờ.
 *
 * <h2>⛔ Hình dạng này đã bị bắt một lần rồi, ở đúng chỗ này</h2>
 *
 * Chú thích trên chính job ấy trong {@code ci.yml} viết rằng vòng quét ngược 50 commit trước kia
 * đẻ ra §10.43 vì <b>"nó nuốt stderr nên 403 (chưa xác thực) và 404 (chưa dựng) cho ra cùng một
 * thông báo"</b>. Bản thay thế tái lập đúng khuyết tật ấy — {@code >/dev/null 2>&1} ở lượt dò
 * đầu, và một nhánh lỗi khẳng định đúng MỘT nguyên nhân cho điều kiện có ít nhất BA.
 *
 * <h2>Vì sao chạy script thay vì đọc chữ trong YAML</h2>
 *
 * Một bài khẳng định <i>"script có chứa chuỗi Client.Timeout"</i> sẽ xanh với cả bản vá đúng lẫn
 * một bản chép câu ấy vào chú thích. Ở đây script được <b>gọi</b>, {@code docker} bị thay bằng
 * bản giả trả về đúng stderr thật của từng tình huống, và thứ được khẳng định là <b>script kết
 * luận gì</b> — luật 9, phải phân biệt được hai trạng thái.
 *
 * <h2>⛔ Giới hạn của chính bài kiểm này (luật 28)</h2>
 *
 * Nó chứng minh script <b>phân loại đúng</b> khi {@code docker} nói những câu đã biết. Nó KHÔNG
 * chứng minh danh sách câu ấy phủ hết mọi cách Docker diễn đạt — nếu Docker đổi câu chữ cho
 * trường hợp "vắng thật", script rơi xuống nhánh <i>chưa biết</i>: thử lại rồi đỏ kèm nguyên văn.
 * Đó là hướng hỏng an toàn, và bài {@code vangLaKetLuanDutKhoat} khoá hướng ấy lại.
 */
class GanTagShaTest {

    /** Nguyên văn stderr của lượt hỏng 4/9 — không phải một câu tự nghĩ ra. */
    private static final String LOI_MANG_THAT = "failed to configure transport: error pinging v2 registry: "
            + "Get \"https://ghcr.io/v2/\": net/http: request canceled while waiting for "
            + "connection (Client.Timeout exceeded while awaiting headers)";

    private static final String LOI_VANG_THAT = "manifest unknown: manifest unknown";

    /** 403 của gói GHCR riêng tư — §10.43. KHÔNG được đọc thành "không tồn tại". */
    private static final String LOI_CAM_THAT = "unauthorized: unauthenticated: User cannot be authenticated";

    private static final String GHI_TAG = "buildx imagetools create";

    private static final String CAU_GOI_MOI = "commit ĐẦU TIÊN";

    private static final String CAU_KHONG_HOI_DUOC = "KHÔNG HỎI ĐƯỢC";

    // ── Nhóm 1: hai nhánh xanh ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A · `:sha` đã có → không gắn thêm, không gọi lệnh GHI")
    void daDongGoiThiThoi(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "sha=co");

        assertThat(kq.maThoat()).as("Đầu ra:\n%s", kq.dauRa()).isZero();
        assertThat(kq.dauRa()).contains("đã đóng gói");
        assertThat(kq.lenhDocker())
                .as("Image đã có tag SHA rồi thì ghi thêm là thừa — và mỗi lượt ghi cần quyền `packages: write`")
                .noneMatch(d -> d.contains(GHI_TAG));
    }

    @Test
    @DisplayName("B · `:sha` vắng + `:dev` có → gắn tag lên đúng digest cũ")
    void vangShaCoDevThiGan(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "sha=vang", "dev=co");

        assertThat(kq.maThoat()).as("Đầu ra:\n%s", kq.dauRa()).isZero();
        assertThat(kq.lenhDocker()).anyMatch(d -> d.contains(GHI_TAG));
        // Khẳng định VỀ SỐ LƯỢNG (luật 29): đúng một lượt ghi cho mỗi image được truyền vào.
        assertThat(kq.lenhDocker().stream().filter(d -> d.contains(GHI_TAG)).count())
                .isEqualTo(2);
    }

    // ── Nhóm 2: nhánh sinh ra bản vá ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("⭐⭐ C · `:dev` lỗi MẠNG → đỏ, và KHÔNG được đổ cho \"gói mới\"")
    void loiMangKhongDuocDocThanhVang(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "sha=vang", "dev=mang");

        assertThat(kq.maThoat())
                .as("Không hỏi được registry thì phải ĐỎ, không được đi tiếp:\n%s", kq.dauRa())
                .isNotZero();
        assertThat(kq.dauRa())
                .as(
                        """
                        Đây là toàn bộ lý do bản vá tồn tại. Bản cũ in "commit ĐẦU TIÊN của một gói \
                        GHCR mới" trong khi job đóng gói vừa đẩy `app:dev` xong 2 phút 16 giây trước \
                        — một khẳng định SAI, và lời khuyên kèm theo dẫn người đọc đi sai hướng \
                        (luật 33: lời khuyên chữa lỗi in ra từ một bộ canh cũng là mã).

                        Đầu ra thật:
                        %s""",
                        kq.dauRa())
                .doesNotContain(CAU_GOI_MOI);
        assertThat(kq.dauRa()).contains(CAU_KHONG_HOI_DUOC);
        assertThat(kq.dauRa())
                .as("Nguyên văn stderr phải ĐI RA log — nuốt nó chính là thứ đã tạo ra §10.43")
                .contains("Client.Timeout");
        assertThat(kq.lenhDocker())
                .as("Chưa biết `:dev` là bản nào thì tuyệt đối không được ghi tag")
                .noneMatch(d -> d.contains(GHI_TAG));
    }

    @Test
    @DisplayName("D · `:dev` registry TRẢ LỜI là vắng → đỏ, và ĐƯỢC nói \"gói mới\"")
    void vangThatThiDuocKetLuanVang(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "sha=vang", "dev=vang");

        assertThat(kq.maThoat()).isNotZero();
        assertThat(kq.dauRa())
                .as("Registry đã trả lời và nói không có — đây mới thật sự là gói GHCR mới")
                .contains(CAU_GOI_MOI);
        assertThat(kq.lenhDocker()).noneMatch(d -> d.contains(GHI_TAG));
    }

    @Test
    @DisplayName("⛔ G · 403 chưa xác thực KHÔNG được đọc thành \"không tồn tại\" (§10.43)")
    void camKhongPhaiVang(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "sha=vang", "dev=cam");

        assertThat(kq.maThoat()).isNotZero();
        assertThat(kq.dauRa())
                .as(
                        """
                        §10.43 nguyên bản: gói GHCR mặc định RIÊNG TƯ kể cả khi kho mã công khai, nên \
                        lượt tra ẩn danh trả 403. Bộ quét cũ gộp 403 với 404 thành cùng một câu \
                        "không tìm thấy image", và ba bước chẩn đoán nó gợi ý đều là ngõ cụt.

                        Đầu ra:
                        %s""",
                        kq.dauRa())
                .doesNotContain(CAU_GOI_MOI);
        assertThat(kq.dauRa()).contains(CAU_KHONG_HOI_DUOC);
    }

    // ── Nhóm 3: thử lại, và thiếu công cụ ──────────────────────────────────────────────────────

    @Test
    @DisplayName("⭐ E · lỗi mạng thì THỬ LẠI đủ số lần rồi mới bỏ cuộc")
    void loiMangThiThuLai(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "sha=vang", "dev=mang");

        // 1 lượt hỏi `:sha` (vắng — dứt khoát, không thử lại) + 4 lượt hỏi `:dev`.
        long soLanHoiDev = kq.lenhDocker().stream()
                .filter(d -> d.startsWith("manifest inspect") && d.endsWith(":dev"))
                .count();
        assertThat(soLanHoiDev)
                .as(
                        """
                        Hỏng vì mạng mà bỏ cuộc ngay lượt đầu thì một cú chớp 2 giây của ghcr.io vẫn \
                        hạ đỏ cổng bắt buộc — đúng chuyện đã xảy ra 4/9. Đầu ra:
                        %s""",
                        kq.dauRa())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("⭐ E2 · \"vắng\" là câu trả lời DỨT KHOÁT — không thử lại")
    void vangLaKetLuanDutKhoat(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "sha=vang", "dev=vang");

        long soLanHoiDev = kq.lenhDocker().stream()
                .filter(d -> d.startsWith("manifest inspect") && d.endsWith(":dev"))
                .count();
        assertThat(soLanHoiDev)
                .as("Thử lại một câu trả lời dứt khoát chỉ làm chậm lượt chạy mà không đổi kết luận")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔ F · không có `docker` trên PATH → ĐỎ, không im lặng đi tiếp")
    void thieuDockerThiDo(@TempDir Path thuMuc) throws Exception {
        Path binRong = Files.createDirectories(thuMuc.resolve("bin-rong"));
        KetQua kq = chayVoiPath(thuMuc, binRong.toString());

        assertThat(kq.maThoat())
                .as(
                        """
                        Thiếu công cụ mà thoát 0 là đúng bẫy `verify-no-keys.sh` đã mắc: nhánh thiếu \
                        `pg_restore` `exit 0`, và suốt bốn ngày mọi lượt triển khai in "BỎ QUA việc \
                        kiểm khoá" mà không ai đọc (T11.41).

                        Đầu ra:
                        %s""",
                        kq.dauRa())
                .isNotZero();
    }

    // ── Nhóm 4: tự kiểm chứng ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⭐ TỰ KIỂM: hai tình huống C và D phải cho ra HAI thông báo khác nhau")
    void haiTinhHuongPhaiPhanBietDuoc(@TempDir Path a, @TempDir Path b) throws Exception {
        KetQua mang = chay(a, "sha=vang", "dev=mang");
        KetQua vang = chay(b, "sha=vang", "dev=vang");

        // Cả hai cùng ĐỎ — nếu chỉ khẳng định "đỏ" thì bài này không phân biệt được gì (luật 9).
        assertThat(mang.maThoat()).isNotZero();
        assertThat(vang.maThoat()).isNotZero();

        // Thứ PHẢI khác nhau là kết luận, và nó phải khác theo đúng hai chiều.
        assertThat(mang.dauRa().contains(CAU_GOI_MOI))
                .as("Lỗi mạng KHÔNG được kết luận là gói mới")
                .isFalse();
        assertThat(vang.dauRa().contains(CAU_GOI_MOI))
                .as("Vắng thật thì PHẢI kết luận là gói mới — nếu không, bản vá đã giết luôn nhánh đúng")
                .isTrue();
    }

    // ── Nhóm 5: dây nối trong workflow ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("⭐ `ci.yml` gọi script, và job có bước `checkout` để script tồn tại")
    void workflowGoiScriptVaCoCheckout() {
        String ci = doc(timTuGocKho(".github/workflows/ci.yml"));
        String job = trichJob(ci, "gan-tag-sha");

        assertThat(job)
                .as("Thân lệnh phải nằm ở script tách rời thì nhánh quyết định mới kiểm được bằng `docker` giả")
                .contains(".github/scripts/gan-tag-sha.sh");
        assertThat(job)
                .as(
                        """
                        Trước 4/9 job này KHÔNG checkout, vì thân lệnh nằm nội tuyến trong `run:`. \
                        Chuyển sang gọi script mà quên bước này thì mọi lượt chạy hỏng với \
                        "No such file or directory" — và đó là một lỗi CHỈ lộ ra trên runner.""")
                .contains("actions/checkout");
        assertThat(job)
                .as("Không được còn một lượt `docker manifest inspect` nội tuyến nào lách qua bộ phân loại")
                .doesNotContain("docker manifest inspect");
    }

    @Test
    @DisplayName("⭐ TỰ KIỂM: phép trích job thật sự cắt đúng một job")
    void trichJobCatDungMotJob() {
        String ci = doc(timTuGocKho(".github/workflows/ci.yml"));
        String job = trichJob(ci, "gan-tag-sha");

        // Đối chứng phải-tìm-thấy: khối cắt ra không rỗng và có đúng tên job (luật 7).
        assertThat(job).isNotBlank();
        assertThat(job).contains("Gắn tag SHA cho image không đổi");
        // Và nó KHÔNG được nuốt sang job kế bên — nếu nuốt thì khẳng định "không còn
        // `docker manifest inspect`" ở bài trên sẽ đo nhầm phạm vi (luật 28).
        assertThat(job).as("Khối cắt ra phải dừng trước job kế tiếp").doesNotContain("dependency-review");
        assertThat(job.length())
                .as("Cắt ra cả tệp thì mọi khẳng định phạm vi bên trên đều vô nghĩa")
                .isLessThan(ci.length() / 2);
    }

    // ── Bộ đồ nghề ─────────────────────────────────────────────────────────────────────────────

    private record KetQua(int maThoat, String dauRa, List<String> lenhDocker) {}

    /**
     * Cắt một job cấp một ra khỏi {@code ci.yml}: từ dòng {@code  <tên>:} tới dòng khoá cấp một kế
     * tiếp. Đọc theo cấu trúc thụt đầu dòng chứ không khớp một chuỗi cố định (luật 2).
     */
    private static String trichJob(String yaml, String tenJob) {
        String moKhoa = "\n  " + tenJob + ":\n";
        int i = yaml.indexOf(moKhoa);
        if (i < 0) {
            return fail("Không tìm thấy job `%s` trong ci.yml".formatted(tenJob));
        }
        int j = i + moKhoa.length();
        String[] dong = yaml.substring(j).split("\n", -1);
        StringBuilder ra = new StringBuilder(moKhoa);
        for (String d : dong) {
            boolean khoaCapMot = d.length() > 2
                    && d.charAt(0) == ' '
                    && d.charAt(1) == ' '
                    && d.charAt(2) != ' '
                    && d.charAt(2) != '#'
                    && d.strip().endsWith(":");
            if (khoaCapMot) {
                break;
            }
            ra.append(d).append('\n');
        }
        return ra.toString();
    }

    private static KetQua chay(Path thuMuc, String... kichBan) throws Exception {
        Path bin = dungDockerGia(thuMuc, kichBan);
        return chayVoiPath(thuMuc, bin + File.pathSeparator + System.getenv("PATH"));
    }

    /**
     * Dựng một {@code docker} giả: ghi nguyên văn argv ra tệp, và với {@code manifest inspect} thì
     * trả về đúng stderr THẬT của tình huống được yêu cầu. Nhờ vậy nhánh phân loại kiểm được bằng
     * dữ liệu giả, thay vì phải chờ ghcr.io thật sự chớp tắt một lần nữa.
     */
    private static Path dungDockerGia(Path thuMuc, String... kichBan) throws IOException {
        Path bin = Files.createDirectories(thuMuc.resolve("bin"));
        Path nhatKy = thuMuc.resolve("argv.txt");
        Files.writeString(nhatKy, "", StandardCharsets.UTF_8);

        String nhanhSha = "co";
        String nhanhDev = "co";
        for (String k : kichBan) {
            String[] doi = k.split("=", 2);
            if ("sha".equals(doi[0])) {
                nhanhSha = doi[1];
            } else if ("dev".equals(doi[0])) {
                nhanhDev = doi[1];
            } else {
                fail("Kịch bản lạ: " + k);
            }
        }

        Path docker = bin.resolve("docker");
        Files.writeString(
                docker,
                """
                #!/usr/bin/env bash
                printf '%%s\\n' "$*" >> '%s'
                if [ "$1" = manifest ] && [ "$2" = inspect ]; then
                  case "$3" in
                    *:dev) tinh_huong='%s' ;;
                    *)     tinh_huong='%s' ;;
                  esac
                  case "$tinh_huong" in
                    co)   exit 0 ;;
                    vang) echo '%s' >&2; exit 1 ;;
                    mang) echo '%s' >&2; exit 1 ;;
                    cam)  echo '%s' >&2; exit 1 ;;
                  esac
                fi
                exit 0
                """
                        .formatted(nhatKy, nhanhDev, nhanhSha, LOI_VANG_THAT, LOI_MANG_THAT, LOI_CAM_THAT),
                StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(docker, PosixFilePermissions.fromString("rwxr-xr-x"));
        return bin;
    }

    private static KetQua chayVoiPath(Path thuMuc, String path) throws Exception {
        Path script = timTuGocKho(".github/scripts/gan-tag-sha.sh");

        // ⚠ `/bin/bash` TUYỆT ĐỐI, không dựa vào PATH: bài F cố tình dựng PATH rỗng để giấu
        //   `docker`, và nếu bash cũng phải tra qua PATH thì bài ấy hỏng vì lý do khác hẳn thứ nó
        //   đo. Cũng không dùng shebang — shell mặc định của máy dev là zsh (CLAUDE.md luật 20).
        ProcessBuilder pb = new ProcessBuilder(
                "/bin/bash", script.toString(), "ghcr.io/vi-du/songnhue", "abc1234", "app", "admin-app");
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().put("PATH", path);
        // Chờ 0 giây giữa các lượt thử: bài E đo SỐ LẦN hỏi, không đo thời gian.
        pb.environment().put("GAN_TAG_CHO_GIAY", "0");

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

    private static String doc(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fail("Không đọc được " + p, e);
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
