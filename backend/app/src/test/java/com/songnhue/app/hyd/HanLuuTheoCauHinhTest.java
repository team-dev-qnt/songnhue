package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.spi.JobContext;
import com.songnhue.hydro.application.HydroJobTypes;
import com.songnhue.hydro.application.HydroRetentionHandler;

/**
 * <b>Hai khoá hạn lưu {@code hydro.*} thật sự điều khiển lượt dọn</b> — trả một phần nợ T48.11.
 *
 * <h2>Vì sao *"có hàm đọc"* ⛔ đủ</h2>
 *
 * `HydroSettingsReadTest` (T27.5) canh **cấu trúc**: mỗi khoá đã seed phải có một hàm đọc. Nó ⛔ trả
 * lời được câu <i>"giá trị người vận hành gõ vào có đổi điều gì ⛔"</i> — và T47.12 đã đo được rằng
 * hai câu ấy khác nhau: khoá `hydro.polling.max-retry` có hàm đọc, có ô nhập, có bài kiểm đi qua
 * nhánh của nó, mà <b>0 lượt nào chạm bảng {@code settings}</b>; tệ hơn, giá trị dự phòng trong Java
 * <b>trùng khít</b> giá trị seed ⇒ xoá hàng seed, đổi tên khoá hay {@code SettingService} trả rỗng
 * thì hệ vẫn chạy y hệt, ⛔ một dòng log nào đổi (luật 3 + luật 9).
 *
 * <h2>Hai khoá này hỏng ra sao nếu ô nhập là trang trí</h2>
 *
 * <ul>
 *   <li><b>{@code hydro.raw-retention-days}</b> — quyết định lúc nào <b>nguyên văn response</b> và
 *       nhật ký đồng bộ bị xoá. Đặt quá ngắn mà ô nhập ⛔ có tác dụng ⇒ người vận hành tin rằng họ
 *       đã nới hạn lưu, rồi ngày cần đối chiếu <i>"số này parse từ đâu ra"</i> thì dữ liệu ⛔ còn.
 *   <li><b>{@code hydro.retention-years}</b> — hạn lưu <b>số đo</b> (chốt D5, 5 năm). Nguồn ⛔ có
 *       API lịch sử nên <b>quy tắc 18</b> áp ở đây ở dạng nặng nhất: một lượt dọn theo con số sai là
 *       mất số liệu <b>vĩnh viễn</b>, ⛔ có đường lấy lại.
 * </ul>
 *
 * <h2>⚠ Chọn số thế nào để bài ⛔ xanh vì lý do sai</h2>
 *
 * Mặc định trong Java là 90 ngày / 5 năm và seed cũng vậy. Nên <b>cả hai vế</b> của bài dùng con số
 * <b>khác</b> mặc định (400 ngày / 10 năm ⇒ giữ; 30 ngày / 1 năm ⇒ xoá): nếu một vế trùng mặc định
 * thì nó xanh cả khi khoá `settings` ⛔ được đọc lần nào (T48.7 · T48.9).
 *
 * <p>⛔⛔ Ghi bằng {@link SettingService#update} chứ ⛔ {@code UPDATE settings} thẳng — service dọn
 * đệm Caffeine và phát event sau commit; ghi thẳng vào bảng thì đệm toàn tiến trình giữ giá trị cũ
 * và cho một xanh giả (§10.67). Và {@code finally} khôi phục là <b>bắt buộc</b>: đệm ấy rò sang mọi
 * lớp chạy sau, mà surefire xếp lớp theo hệ tệp (macOS ngược Linux) ⇒ hậu quả là một lượt CI đỏ ở
 * một bài vô can, ⛔ tái lập được ở máy (§11.19).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HanLuuTheoCauHinhTest extends IntegrationTestBase {

    private static final String KHOA_RAW = "hydro.raw-retention-days";
    private static final String KHOA_SO_DO = "hydro.retention-years";

    /** Mốc dữ liệu thử: đủ cũ để một hạn lưu NGẮN xoá, đủ mới để một hạn lưu DÀI giữ. */
    private static final int TUOI_NGAY_SYNC_LOG = 100;

    private static final int TUOI_NAM_SO_DO = 3;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SettingService settings;

    @Autowired
    private HydroRetentionHandler handler;

    private static JobContext boiCanh() {
        return new JobContext(UUID.randomUUID(), HydroJobTypes.RETENTION, "{}", null, phanTram -> {}, ketQua -> {});
    }

    private long nguonBatKy() {
        return jdbc.queryForObject("SELECT id FROM api_sources ORDER BY id LIMIT 1", Long.class);
    }

    /** Một dòng nhật ký đồng bộ CŨ; trả về id để đếm đúng dòng của bài này. */
    private long themSyncLogCu(long nguon) {
        return jdbc.queryForObject(
                """
                INSERT INTO sync_logs (api_source_id, started_at, status, received_count)
                VALUES (?, now() - make_interval(days => ?), 'SUCCESS', 0)
                RETURNING id
                """,
                Long.class,
                nguon,
                TUOI_NGAY_SYNC_LOG);
    }

    /** Một số đo chưa khai mã, CŨ. */
    private long themSoDoCu(long nguon) {
        return jdbc.queryForObject(
                """
                INSERT INTO hydro_unmapped_readings (api_code, api_source_id, measured_at, raw_value, raw_unit)
                VALUES (?, ?, now() - make_interval(years => ?), 123.400, 'cm')
                RETURNING id
                """,
                Long.class,
                "T4811-" + System.nanoTime() % 100000,
                nguon,
                TUOI_NAM_SO_DO);
    }

    private boolean conSyncLog(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM sync_logs WHERE id = ?", Integer.class, id) == 1;
    }

    private boolean conSoDo(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM hydro_unmapped_readings WHERE id = ?", Integer.class, id) == 1;
    }

    @Test
    @DisplayName("⭐⭐ Hạn lưu DÀI (400 ngày / 10 năm) ⇒ lượt dọn giữ nguyên — và cả hai số đều KHÁC mặc định")
    void hanLuuDaiThiGiuNguyen() {
        String rawCu = settings.getString(KHOA_RAW).orElse(null);
        String soDoCu = settings.getString(KHOA_SO_DO).orElse(null);
        long nguon = nguonBatKy();
        long idSync = themSyncLogCu(nguon);
        long idSoDo = themSoDoCu(nguon);
        try {
            settings.update(KHOA_RAW, "400");
            settings.update(KHOA_SO_DO, "10");

            handler.handle(boiCanh());

            assertThat(conSyncLog(idSync))
                    .as(
                            "⛔ Nhật ký đồng bộ %d ngày tuổi bị xoá dù hạn lưu đang đặt 400 ngày ⇒ ô nhập "
                                    + "trên màn hình Cấu hình hệ thống ⛔ điều khiển gì",
                            TUOI_NGAY_SYNC_LOG)
                    .isTrue();
            assertThat(conSoDo(idSoDo))
                    .as(
                            "⛔ Số đo %d năm tuổi bị xoá dù hạn lưu đang đặt 10 năm. Nguồn ⛔ có API lịch "
                                    + "sử ⇒ quy tắc 18: mất là mất VĨNH VIỄN",
                            TUOI_NAM_SO_DO)
                    .isTrue();
        } finally {
            settings.update(KHOA_RAW, rawCu);
            settings.update(KHOA_SO_DO, soDoCu);
            jdbc.update("DELETE FROM sync_logs WHERE id = ?", idSync);
            jdbc.update("DELETE FROM hydro_unmapped_readings WHERE id = ?", idSoDo);
        }
    }

    @Test
    @DisplayName("⭐⭐ VẾ PHÂN BIỆT — hạn lưu NGẮN (30 ngày / 1 năm) ⇒ đúng hai dòng ấy bị dọn")
    void hanLuuNganThiDon() {
        String rawCu = settings.getString(KHOA_RAW).orElse(null);
        String soDoCu = settings.getString(KHOA_SO_DO).orElse(null);
        long nguon = nguonBatKy();
        long idSync = themSyncLogCu(nguon);
        long idSoDo = themSoDoCu(nguon);
        try {
            // ⚠ Tiền đề: hai dòng ĐANG CÓ. Thiếu vế này thì một bài chạy trên dữ liệu đã bị lớp khác
            //   xoá vẫn "xanh" ở khẳng định *đã bị dọn* — đúng hình dạng §11.19.
            assertThat(conSyncLog(idSync) && conSoDo(idSoDo))
                    .as("⛔ Dữ liệu thử ⛔ vào được CSDL ⇒ mọi khẳng định dưới đây nói về tập RỖNG")
                    .isTrue();

            settings.update(KHOA_RAW, "30");
            settings.update(KHOA_SO_DO, "1");

            handler.handle(boiCanh());

            assertThat(conSyncLog(idSync))
                    .as(
                            "⛔ Hạn lưu 30 ngày mà nhật ký %d ngày tuổi vẫn còn ⇒ giá trị ĐÃ GIẢI ⛔ tới "
                                    + "được lượt dọn",
                            TUOI_NGAY_SYNC_LOG)
                    .isFalse();
            assertThat(conSoDo(idSoDo))
                    .as("⛔ Hạn lưu 1 năm mà số đo %d năm tuổi vẫn còn", TUOI_NAM_SO_DO)
                    .isFalse();
        } finally {
            settings.update(KHOA_RAW, rawCu);
            settings.update(KHOA_SO_DO, soDoCu);
            jdbc.update("DELETE FROM sync_logs WHERE id = ?", idSync);
            jdbc.update("DELETE FROM hydro_unmapped_readings WHERE id = ?", idSoDo);
        }
    }
}
