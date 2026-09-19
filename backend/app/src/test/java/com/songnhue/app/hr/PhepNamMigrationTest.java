package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;

/**
 * <b>Chốt chặn của migration Điều 114 — T68.10 — phải thật sự chặn.</b>
 *
 * <p>{@code V202609201091} gỡ ba khoá 3 bậc cũ và giữ CƠ SỞ người vận hành đang dùng. Nếu hai khoá bậc trên đã bị
 * sửa khỏi dạng (cơ sở + 1, cơ sở + 2) thì migration DỪNG: ánh xạ một chính sách tuỳ biến là quyết định nhân sự,
 * ⛔ để migration đoán. Trên CSDL kiểm thử ba khoá luôn mang seed ⇒ nhánh RAISE <b>về nguyên tắc</b> ⛔ lượt chạy
 * nào đi qua (luật 7) — lớp này chạy lại đúng khối {@code DO} ấy, đọc từ chính tệp migration, trên hai trạng thái.
 */
class PhepNamMigrationTest extends IntegrationTestBase {

    private static final String TEP =
            "backend/hr/src/main/resources/db/migration/hr/V202609201093__hr_phep_nam_dieu_114.sql";

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * ⛔⛔ Dọn ĐÚNG ba khoá đồ gá, ⛔ dùng {@code LIKE 'hr.leave.annual-days.%-years'}.
     *
     * <p>Bản đầu dọn bằng mẫu ấy — và nó khớp luôn khoá THẬT {@code hr.leave.annual-days.seniority-step-years} (seed
     * của chính migration này). Ở máy, thứ tự lớp đặt lớp này SAU {@code NghiPhepHttpTest} nên ⛔ ai thấy; trên runner
     * thứ tự ngược lại và <b>ba bài phép năm đỏ</b> với một triệu chứng chẳng liên quan (*"hệ cho 12, luật 13"*).
     * Đúng hình dạng T48.8 · §11.19: rò trạng thái giữa các lớp, chỉ lộ ở MỘT thứ tự — và {@code make ci-order} (một
     * thứ tự KHÁC, ⛔ phải thứ tự của runner) cũng xanh. Một mẫu dọn cũng là một phép so: nó phải phân biệt được hàng
     * ĐỒ GÁ với hàng THẬT.
     */
    @AfterEach
    void don() {
        jdbc.update("DELETE FROM settings WHERE setting_key IN (" + BA_KHOA_CU + ")");
    }

    private static final String BA_KHOA_CU = "'hr.leave.annual-days.under-5-years', "
            + "'hr.leave.annual-days.5-to-10-years', 'hr.leave.annual-days.over-10-years'";

    @Test
    @DisplayName("⛔⛔ Hai bậc trên bị sửa khỏi dạng cơ sở/+1/+2 ⇒ khối DO NÉM, gọi đích danh ba giá trị")
    void lechDangThiDung() {
        themKhoaCu("12", "15", "14");
        assertThatThrownBy(() -> jdbc.execute(khoiKiem()))
                .as("⛔ một chính sách tuỳ biến bị ánh xạ lặng lẽ sang mô hình mới là đổi số phép của người lao động")
                .hasMessageContaining("T68.10")
                .hasMessageContaining("15");
    }

    @Test
    @DisplayName("⭐ Đối chứng: đúng dạng seed (12/13/14) hoặc cơ sở tuỳ biến (14/15/16) ⇒ ⛔ ném")
    void dungDangThiQua() {
        themKhoaCu("12", "13", "14");
        jdbc.execute(khoiKiem());
        don();
        themKhoaCu("14", "15", "16");
        jdbc.execute(khoiKiem());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM settings WHERE setting_key IN (" + BA_KHOA_CU + ")", Integer.class))
                .as("⛔ chống tập rỗng: ba khoá cũ phải CÓ mặt lúc khối DO chạy, nếu không nó xanh vì ⛔ đọc được gì")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("⛔⛔ Lượt DỌN của chính lớp này ⛔ được chạm khoá THẬT `seniority-step-years` (rò trạng thái giữa lớp)")
    void donKhongChamKhoaThat() {
        themKhoaCu("12", "13", "14");
        don();
        assertThat(jdbc.queryForList(
                        "SELECT setting_value FROM settings WHERE setting_key = ?",
                        String.class,
                        "hr.leave.annual-days.seniority-step-years"))
                .as("⛔⛔ Mẫu dọn `LIKE 'hr.leave.annual-days.%%-years'` khớp LUÔN khoá này ⇒ ba bài phép năm của "
                        + "`NghiPhepHttpTest` đỏ với *\"hệ cho 12, luật 13\"* — nhưng CHỈ ở thứ tự lớp đặt lớp này "
                        + "chạy trước (runner), còn ở máy thì xanh (T48.8 · §11.19)")
                .containsExactly("5");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM settings WHERE setting_key IN (" + BA_KHOA_CU + ")", Integer.class))
                .as("⛔ và ba khoá ĐỒ GÁ thì phải bị dọn sạch")
                .isZero();
    }

    private void themKhoaCu(String duoi5, String tu5, String tren10) {
        jdbc.update(
                "INSERT INTO settings (setting_key, setting_value, value_type, group_code, label, editable, sort_order) "
                        + "VALUES ('hr.leave.annual-days.under-5-years', ?, 'INTEGER', 'HR', 'thử', TRUE, 1), "
                        + "('hr.leave.annual-days.5-to-10-years', ?, 'INTEGER', 'HR', 'thử', TRUE, 2), "
                        + "('hr.leave.annual-days.over-10-years', ?, 'INTEGER', 'HR', 'thử', TRUE, 3)",
                duoi5,
                tu5,
                tren10);
    }

    /** Khối {@code DO $$ … END $$;} đọc từ CHÍNH tệp migration — ⛔ chép lại (luật 29). */
    private static String khoiKiem() {
        String sql = doc(TEP);
        int dau = sql.indexOf("DO $$");
        int cuoi = sql.indexOf("END $$;", dau);
        assertThat(dau)
                .as("⛔ tệp migration ⛔ còn khối DO — bộ canh này mất đối tượng")
                .isNotNegative();
        assertThat(cuoi).isGreaterThan(dau);
        return sql.substring(dau, cuoi + "END $$;".length());
    }

    private static String doc(String duongDan) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDan);
            if (Files.exists(ungVien)) {
                try {
                    return Files.readString(ungVien, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            hienTai = hienTai.getParent();
        }
        throw new IllegalStateException("⛔ thấy " + duongDan);
    }
}
