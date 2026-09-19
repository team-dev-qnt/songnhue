package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>{@code ci.yml} — mỗi việc nặng chạy ĐÚNG MỘT lần (đơn giản hoá 18/09/2026).</b>
 *
 * <ul>
 *   <li>PR vào {@code dev}: lint + test ({@code backend}, {@code frontend}). ⛔ Không dựng image.
 *   <li>push vào {@code dev}: ⛔ không test lại — chỉ dựng/đẩy image + gắn tag SHA.
 * </ul>
 *
 * Đo trước khi đổi (lượt push {@code dev} 35346347718): 9' = test 6' + image 2–3' + gắn tag; và mỗi PR
 * dựng thử image thêm 2–3'. Bỏ test ở push dựa vào {@code strict=true} của bảo vệ nhánh {@code dev} (PR
 * phải up-to-date) cộng squash ⇒ cây sau merge trùng khít cây đã test ở PR.
 *
 * <h2>Bộ canh này giữ cái gì</h2>
 *
 * Hai cách hỏng, cả hai đều <b>im lặng</b>:
 *
 * <ol>
 *   <li>Job image giữ {@code needs: backend|frontend} — job test nay luôn {@code skipped} ở push, và một job
 *       phụ thuộc job skipped cũng bị skip ⇒ <b>không image nào được dựng</b>, {@code Cổng kiểm CI} vẫn xanh
 *       (nó coi {@code skipped} là đạt), rồi CD đi tra {@code app:<sha>} và 404.
 *   <li>Ai đó bỏ điều kiện sự kiện ⇒ quay về dựng image hai lần / test hai lần — đúng chi phí vừa gỡ.
 * </ol>
 */
class CiImageChiTrenDevTest {

    private static final Path CI = timTuGocKho(".github/workflows/ci.yml");

    @Test
    @DisplayName("⭐⭐ Job image CHỈ chạy ở push và ⛔ phụ thuộc job test")
    void imageChiChayOPushVaKhongPhuThuocTest() {
        String ci = doc(CI);
        for (String job : new String[] {"image", "image-frontend"}) {
            String khoi = khoiJob(ci, job);
            assertThat(dongKhoa(khoi, "if"))
                    .as("Job `%s` không giới hạn ở push — PR sẽ dựng image lại (2–3'/PR)", job)
                    .contains("github.event_name == 'push'");
            assertThat(dongKhoa(khoi, "needs"))
                    .as(
                            """
                            Job `%s` còn phụ thuộc job test. Ở push `dev` job test luôn `skipped` nên job \
                            này skip theo ⇒ ⛔ image nào được dựng mà `Cổng kiểm CI` vẫn xanh, và CD 404.""",
                            job)
                    .doesNotContainPattern("\\b(backend|frontend)\\b");
        }
    }

    @Test
    @DisplayName("⭐⭐ Job test CHỈ chạy ở PR")
    void testChiChayOPr() {
        String ci = doc(CI);
        for (String job : new String[] {"backend", "frontend"}) {
            assertThat(dongKhoa(khoiJob(ci, job), "if"))
                    .as("Job `%s` không giới hạn ở PR — push `dev` sẽ test lại lần hai (~6')", job)
                    .contains("github.event_name == 'pull_request'");
        }
    }

    @Test
    @DisplayName("⭐ Job gắn tag vẫn chạy khi job image SKIPPED — lượt merge chỉ sửa tài liệu vẫn có đủ tag")
    void ganTagChayCaKhiImageSkipped() {
        // `if: >-` nhiều dòng ⇒ soi cả khối job, ⛔ chỉ dòng `if:`.
        String dieuKien = khoiJob(doc(CI), "gan-tag-sha");
        assertThat(dieuKien)
                .as("`gan-tag-sha` phải `!cancelled()` và chỉ chặn khi image `failure`, ⛔ khi `skipped`")
                .contains("!cancelled()")
                .contains("needs.image.result != 'failure'")
                .contains("needs.image-frontend.result != 'failure'");
    }

    @Test
    @DisplayName("⭐ TỰ KIỂM: phép trích phân biệt được bản cũ (needs backend, không điều kiện sự kiện)")
    void tuKiemPhepTrich() {
        // Luật 1/29: dựng lại đúng hình dạng TRƯỚC 18/09 và chạy chính các phép trích ở trên.
        String cu =
                """
                jobs:
                  image:
                    name: Đóng gói image
                    needs: [changes, backend]
                    if: needs.changes.outputs.backend == 'true'
                    runs-on: ubuntu-24.04
                  image-frontend:
                    name: x
                """;
        String khoi = khoiJob(cu, "image");
        assertThat(dongKhoa(khoi, "needs")).containsPattern("\\bbackend\\b");
        assertThat(dongKhoa(khoi, "if")).doesNotContain("github.event_name == 'push'");
        assertThat(khoi).as("Phép trích tràn sang job kế tiếp").doesNotContain("image-frontend");
    }

    // -------------------------------------------------------------------------

    /** Khối của một job cấp một: từ {@code "  <khoa>:"} tới job cấp một kế tiếp. */
    private static String khoiJob(String yml, String khoa) {
        Matcher dau =
                Pattern.compile("(?m)^  " + Pattern.quote(khoa) + ":\\s*$").matcher(yml);
        if (!dau.find()) {
            return fail("Không thấy job `%s` trong ci.yml — đổi tên job thì SỬA bài kiểm, đừng xoá", khoa);
        }
        Matcher sau = Pattern.compile("(?m)^  [a-z][a-z0-9-]*:\\s*$").matcher(yml);
        int cuoi = sau.find(dau.end()) ? sau.start() : yml.length();
        return yml.substring(dau.start(), cuoi);
    }

    /** Dòng {@code <khoa>: …} ở mức thuộc tính job (4 dấu cách thụt đầu). */
    private static String dongKhoa(String khoiJob, String khoa) {
        Matcher m =
                Pattern.compile("(?m)^    " + Pattern.quote(khoa) + ":(.*)$").matcher(khoiJob);
        return m.find() ? m.group(1) : fail("Job thiếu dòng `%s:`:%n%s", khoa, khoiJob);
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
