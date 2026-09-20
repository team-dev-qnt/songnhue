package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.exception.ValidationException;

/**
 * <b>Độ dài khung cập nhật của nguồn — HAI núm phải mang CÙNG một trần (T75.4, luật 14).</b>
 *
 * <p>Công ty gõ độ dài khung ở hai chỗ khác nhau cho cùng một đại lượng:
 *
 * <ul>
 *   <li><b>núm chung</b> — khoá {@code hydro.polling.source-frame-minutes}, màn hình <i>Cấu hình hệ
 *       thống › Thuỷ văn</i>. Trần của nó là cột {@code settings.validation}.
 *   <li><b>núm riêng</b> — cột {@code api_sources.frame_minutes}, màn hình <i>Nguồn dữ liệu</i>, dùng
 *       khi một nguồn lệch nhịp với phần còn lại. Trần của nó là {@code ck_api_sources_frame}.
 * </ul>
 *
 * <p>Seed {@code V202608131009} để núm chung ở {@code max=60} trong khi núm riêng nhận tới
 * {@code 1440} — người vận hành đặt được 240 phút cho MỘT nguồn nhưng ⛔ đặt được cho toàn hệ, và
 * ⛔ màn hình nào nói vì sao. {@code V202609201089} nới núm chung lên đúng trần của núm riêng.
 *
 * <p><b>Vì sao bài này ⛔ đọc hằng số trong mã Java.</b> Cả hai trần sống trong CSDL — một ở cột
 * {@code validation}, một trong định nghĩa {@code CHECK}. Chép chúng vào một hằng số Java là dựng
 * <i>nơi thứ ba</i> để quên đồng bộ (§11.12). Bài đo thẳng hai nguồn ấy rồi so với nhau, nên nới
 * một bên mà quên bên kia là CI đỏ ngay, ⛔ phải một khoảng lệch nằm im tới ngày có người gõ số.
 *
 * <p>⚠ Bài khôi phục giá trị khoá sau mỗi phép thử ({@code @AfterEach}) — dọn ở cuối phương thức là
 * rò rỉ trạng thái sang mọi lớp chạy sau khi một khẳng định đỏ (T48.8).
 */
class KhungNguonHaiNumTest extends IntegrationTestBase {

    private static final String KHOA = "hydro.polling.source-frame-minutes";

    /** Trần cũ của seed. Giá trị thử phải VƯỢT nó, nếu không bài xanh ở cả hai trạng thái (luật 9). */
    private static final int TRAN_CU = 60;

    @Autowired
    private SettingService settings;

    @Autowired
    private JdbcTemplate jdbc;

    private String giaTriCu;

    @AfterEach
    void traLaiGiaTri() {
        if (giaTriCu != null) {
            settings.update(KHOA, giaTriCu);
            giaTriCu = null;
        }
    }

    @Test
    @DisplayName("Trần núm chung (settings.validation) = trần núm riêng (ck_api_sources_frame)")
    void haiNumCungTran() {
        assertThat(tranNumChung())
                .as("Độ dài khung gõ được ở HAI màn hình. Lệch trần ⇒ một giá trị hợp lệ ở màn hình "
                        + "Nguồn dữ liệu lại bị Cấu hình hệ thống từ chối (hoặc ngược lại).")
                .isEqualTo(tranNumRieng());
    }

    @Test
    @DisplayName("⛔ Trần mới phải VƯỢT trần cũ 60 — nếu ⛔ thì hai bài dưới xanh vì lý do sai")
    void tranMoiThucSuRongHon() {
        assertThat(tranNumChung())
                .as("Tiền đề của `nhanDuocSoPhutVuotTranCu`: 240 phải nằm trong trần mới")
                .isGreaterThan(TRAN_CU);
    }

    @Test
    @DisplayName("Công ty nhập 240 phút (4 giờ) — trước V202609201089 thì bị từ chối")
    void nhanDuocSoPhutVuotTranCu() {
        giaTriCu = settings.getString(KHOA).orElse("10");

        settings.update(KHOA, "240");

        assertThat(settings.getInt(KHOA, -1))
                .as("Giá trị đã GIẢI, đọc lại từ đường đọc thật — ⛔ phải thứ ta vừa gửi đi (luật 3)")
                .isEqualTo(240);
    }

    @Test
    @DisplayName("Vượt trần chung vẫn bị chặn — nới ⛔ phải gỡ bỏ ràng buộc")
    void vuotTranMoiVanBiTuChoi() {
        int quaTran = tranNumChung() + 1;

        assertThatThrownBy(() -> settings.update(KHOA, String.valueOf(quaTran)))
                .as("Bỏ hẳn trần là để núm chung nhận một số mà núm riêng ⛔ nhận nổi")
                .isInstanceOf(ValidationException.class);
    }

    /** Đọc {@code max=} của cột {@code settings.validation} — nơi màn hình Cấu hình lấy trần. */
    private int tranNumChung() {
        String luat = jdbc.queryForObject("SELECT validation FROM settings WHERE setting_key = ?", String.class, KHOA);
        assertThat(luat).as("Khoá `%s` phải còn trong danh mục", KHOA).isNotNull();
        return bocSo(luat, "max=(\\d+)", "cột validation");
    }

    /**
     * Đọc trần trên của {@code ck_api_sources_frame} thẳng từ pg_catalog.
     *
     * <p>⚠ Migration viết {@code CHECK (frame_minutes BETWEEN 1 AND 1440)}, nhưng
     * {@code pg_get_constraintdef} trả về dạng ĐÃ CHUẨN HOÁ của Postgres:
     * {@code (((frame_minutes IS NULL) OR ((frame_minutes >= 1) AND (frame_minutes <= 1440))))}.
     * Bản đầu của bài này bắt theo chữ {@code BETWEEN} — tức bắt theo thứ có trong **tệp nguồn**
     * chứ ⛔ phải thứ CSDL **đang giữ**, và nó đỏ ngay lượt chạy đầu. Giữ lại nguyên do ở đây vì đó
     * đúng là lý lẽ của cả bài: đo giá trị ĐÃ GIẢI, ⛔ đo thứ ta tưởng mình đã viết (luật 3).
     */
    private int tranNumRieng() {
        String dinhNghia = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_api_sources_frame'",
                String.class);
        assertThat(dinhNghia)
                .as("`ck_api_sources_frame` phải còn tồn tại — nó là trần của ô 'Khung nguồn riêng'")
                .isNotNull();
        return bocSo(dinhNghia, "frame_minutes\\s*<=\\s*(\\d+)", "ck_api_sources_frame");
    }

    private static int bocSo(String nguon, String mau, String ten) {
        Matcher m = Pattern.compile(mau).matcher(nguon);
        assertThat(m.find()).as("⛔ đọc được trần từ %s: %s", ten, nguon).isTrue();
        return Integer.parseInt(m.group(1));
    }
}
