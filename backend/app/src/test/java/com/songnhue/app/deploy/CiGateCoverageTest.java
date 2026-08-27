package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Cổng kiểm CI phải phủ MỌI job của {@code ci.yml} — không job nào đứng ngoài.</b>
 *
 * <h2>Chuyện đã xảy ra — 27/8</h2>
 *
 * Một PR không đụng {@code frontend/} thì <b>không bao giờ merge được vào {@code dev}</b>. Đo trên hai
 * PR mở cùng lúc — cùng nhánh đích, khác đúng một biến:
 *
 * <pre>
 *   PR #48 (có đụng frontend/) → matrix CHẠY   → báo hai tên đã bung → CLEAN
 *   PR #47 (chỉ sửa tài liệu)  → matrix BỎ QUA → báo tên GỐC         → BLOCKED
 * </pre>
 *
 * Bảo vệ nhánh đòi hai context <b>đã bung</b> ({@code "… (admin-app, …)"} / {@code "… (public-web, …)"}),
 * nhưng một job matrix bị bỏ qua chỉ báo tên <b>chưa bung</b>. Hai context kia không bao giờ tới, nên PR
 * treo mãi ở <i>"Waiting for status to be reported"</i>. Không có tên nào đúng cho cả hai trường hợp —
 * phải có một job luôn chạy.
 *
 * <h2>Rủi ro của chính bản vá</h2>
 *
 * Gom về một context nghĩa là <b>context ấy phải biết hết</b>. Thêm một job mới mà quên khai vào
 * {@code needs} thì cổng xanh trong khi job ấy đỏ — đúng hình dạng CLAUDE.md luật 28: <i>một cơ chế canh
 * gác hẹp hơn nơi nó phải chặn, và cái xanh của nó đọc như một lời bảo đảm</i>.
 *
 * <p>Bài này đọc danh sách job <b>có thật</b> trong {@code ci.yml} rồi đối chiếu với {@code needs} của
 * cổng, nên chỗ quên ấy thành một bài kiểm ĐỎ thay vì một lời bảo đảm sai.
 */
class CiGateCoverageTest {

    private static final String TEN_CONG = "cong-kiem";

    /** Khoá job cấp một trong {@code ci.yml}: đúng hai dấu cách thụt đầu, kết thúc bằng dấu hai chấm. */
    private static final Pattern KHOA_JOB = Pattern.compile("(?m)^  ([a-z][a-z0-9-]*):$");

    @Test
    @DisplayName("⭐⭐ Cổng kiểm phủ MỌI job của ci.yml")
    void congPhuMoiJob() {
        List<String> job = danhSachJob();
        List<String> needs = needsCuaCong();

        // Chặn xanh-trên-tập-rỗng ở cả hai vế: regex lỗi thời thì phải ĐỎ, không phải im lặng đạt.
        assertThat(job)
                .as("Không đọc ra job nào từ ci.yml — cấu trúc tệp đã đổi, SỬA bài kiểm chứ đừng xoá")
                .hasSizeGreaterThanOrEqualTo(5);
        assertThat(needs).as("Không đọc ra `needs` của job `%s`", TEN_CONG).hasSizeGreaterThanOrEqualTo(5);

        List<String> thieu = new ArrayList<>(job);
        thieu.remove(TEN_CONG);
        thieu.removeAll(needs);

        assertThat(thieu)
                .as(
                        """
                        %d job của `ci.yml` KHÔNG nằm trong `needs` của `%s`: %s

                        Bảo vệ nhánh chỉ đòi một context là `Cổng kiểm CI`. Job nào đứng ngoài `needs` \
                        thì hỏng bao nhiêu cũng không chặn được PR — và cái xanh của cổng sẽ đọc như \
                        một lời bảo đảm (CLAUDE.md luật 28). Thêm job thì thêm cả vào `needs`.""",
                        thieu.size(), TEN_CONG, thieu)
                .isEmpty();

        // Chiều ngược lại: `needs` trỏ tới một job không còn tồn tại thì workflow không chạy nổi.
        List<String> moCoi = new ArrayList<>(needs);
        moCoi.removeAll(job);
        assertThat(moCoi).as("`needs` trỏ tới job không có thật: %s", moCoi).isEmpty();
    }

    @Test
    @DisplayName("⭐ Cổng phải `if: always()` — nếu không thì nó cũng bị bỏ qua như job nó thay thế")
    void congPhaiLuonChay() {
        String than = thanJob(TEN_CONG);
        assertThat(than)
                .as(
                        """
                        `%s` thiếu `if: always()`.

                        Thiếu nó thì cổng bị bỏ qua khi một job trong `needs` bị bỏ qua — tức là đúng \
                        trường hợp nó sinh ra để xử lý, và ta quay lại chỗ cũ: context bắt buộc không \
                        bao giờ được báo cáo.""",
                        TEN_CONG)
                .contains("if: always()");
    }

    @Test
    @DisplayName("⭐ Cổng phải ĐỎ khi có job `failure` — và phải BỎ QUA `skipped`")
    void congPhanBietHongVoiBoQua() {
        String than = thanJob(TEN_CONG);

        assertThat(than)
                .as("Cổng không xét `failure` thì nó không chặn được gì")
                .contains("failure");
        assertThat(than)
                .as("Cổng không xét `cancelled` thì huỷ lượt chạy sẽ đi lọt")
                .contains("cancelled");
        assertThat(than).as("Cổng phải thoát khác 0 khi có job hỏng").contains("exit 1");

        // ⚠ `skipped` KHÔNG được nằm trong điều kiện chặn: bộ lọc đường dẫn bỏ qua một vùng không
        //   thay đổi là đúng việc của nó. Bài này khẳng định điều đó là CỐ Ý, không phải bỏ sót.
        assertThat(than)
                .as("Nếu cổng chặn cả `skipped` thì mọi PR chỉ sửa tài liệu lại đỏ — đúng chỗ cũ, đổi dấu")
                .doesNotContain("\"skipped\"");
    }

    @Test
    @DisplayName("⛔ Bảo vệ nhánh phải trỏ vào cổng này — bài kiểm nói ra giới hạn của chính nó")
    void phaiCoConTroSangBaoVeNhanh() {
        // Bài này KHÔNG gọi được API GitHub, nên nó không chứng minh được danh sách context thật đã
        // đổi. Thứ nó ép được là: tài liệu bảo vệ nhánh phải nhắc tên cổng, để người áp lệnh biết
        // phải áp gì. Không có dòng này thì một cổng đúng vẫn có thể chưa được ai bật.
        assertThat(doc(timTuGocKho("docs/branch-protection.md")))
                .as("`branch-protection.md` chưa nhắc `Cổng kiểm CI` — con trỏ trỏ vào chỗ trống")
                .contains("Cổng kiểm CI");
    }

    // -------------------------------------------------------------------------

    private static List<String> danhSachJob() {
        String w = doc(timTuGocKho(".github/workflows/ci.yml"));
        int dau = w.indexOf("\njobs:\n");
        if (dau < 0) {
            return fail("`ci.yml` không có khối `jobs:`");
        }
        List<String> ket = new ArrayList<>();
        Matcher m = KHOA_JOB.matcher(w.substring(dau));
        while (m.find()) {
            ket.add(m.group(1));
        }
        return ket;
    }

    private static List<String> needsCuaCong() {
        String than = thanJob(TEN_CONG);
        Matcher m = Pattern.compile("needs:\\s*\\[([^\\]]*)]").matcher(than);
        if (!m.find()) {
            return fail("Job `%s` không khai `needs: [...]`", TEN_CONG);
        }
        return Arrays.stream(m.group(1).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Thân một job, cắt từ khoá của nó tới khoá job kế tiếp. */
    private static String thanJob(String khoa) {
        String w = doc(timTuGocKho(".github/workflows/ci.yml"));
        int dau = w.indexOf("\n  " + khoa + ":\n");
        if (dau < 0) {
            return fail("Không thấy job `%s` trong ci.yml — đổi tên job thì SỬA bài kiểm, đừng xoá", khoa);
        }
        Matcher ke = KHOA_JOB.matcher(w);
        int cuoi = w.length();
        while (ke.find()) {
            if (ke.start() > dau + 1) {
                cuoi = ke.start();
                break;
            }
        }
        return w.substring(dau, cuoi);
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
