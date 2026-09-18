package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.hydro.application.ApiSourceHealthService;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.infra.ApiSourceRepository;

/**
 * <b>{@code hydro.source.alert-after-failures} thật sự quyết định lúc chuông kêu</b> — trả một phần
 * nợ T48.11.
 *
 * <h2>Khoá này canh cái gì</h2>
 *
 * Nó là ngưỡng của <b>cái chuông duy nhất</b> báo rằng đường thuỷ văn đã chết. Dự án đã trả giá đủ
 * hai lần ở đúng chỗ này: nguồn hỏng <b>3323 lượt liên tiếp</b> mà ⛔ ai được báo (T50.4), rồi bản
 * vá đầu khiến chuông kêu <b>đúng một lần</b> rồi im suốt 9 ngày vì nó so {@code == ngưỡng} (T50.10).
 * Nay là thang leo {@code N · N×4 · N×16}, và con số N ấy do người vận hành đặt trên màn hình.
 *
 * <p>⇒ Nếu ô nhập ấy là trang trí thì mọi thứ trông vẫn đúng: vẫn có chuông, vẫn có thang leo, chỉ
 * là nó kêu theo một con số <b>⛔ ai chọn</b>. Và <b>quy tắc 18</b> xếp giám sát poller <b>ngang
 * backup CSDL</b> — mỗi ngày chậm phát hiện là một ngày mất số liệu vĩnh viễn.
 *
 * <h2>⚠ Hai vế đều dùng số KHÁC mặc định</h2>
 *
 * Mặc định trong Java và seed đều là 3. Vế *"kêu"* đặt <b>2</b>, vế *"⛔ kêu"* đặt <b>9</b>: nếu một
 * vế trùng 3 thì nó xanh cả khi khoá {@code settings} ⛔ được đọc lần nào (luật 3 · T48.7).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NguongChuongNguonTheoCauHinhTest extends IntegrationTestBase {

    private static final String KHOA = "hydro.source.alert-after-failures";
    private static final String SU_KIEN = "HYDRO_SOURCE_DOWN";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SettingService settings;

    @Autowired
    private ApiSourceHealthService health;

    @Autowired
    private ApiSourceRepository sources;

    private ApiSource nguonBatKy() {
        return sources.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("⛔ có nguồn dữ liệu nào trong CSDL thử"));
    }

    private int soChuong() {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE event_type = ?", Integer.class, SU_KIEN);
    }

    /** Đưa bộ đếm hỏng của nguồn về 0 — hai vế phải xuất phát từ cùng một trạng thái. */
    private void datLaiBoDem(ApiSource nguon) {
        jdbc.update("UPDATE api_sources SET consecutive_failures = 0 WHERE id = ?", nguon.getId());
    }

    private void hong(ApiSource nguon, int soLuot) {
        for (int i = 0; i < soLuot; i++) {
            health.ghiNhanThatBai(nguon, Instant.now(), SyncFailureKind.HTTP_ERROR, "T48.11 — lượt thử " + i);
        }
    }

    @Test
    @DisplayName("⭐⭐ Ngưỡng 2 ⇒ chuông kêu ở lượt hỏng thứ 2; ngưỡng 9 ⇒ hai lượt ấy IM — cùng một dữ liệu")
    void nguongTrenManHinhQuyetDinhLucChuongKeu() {
        String cu = settings.getString(KHOA).orElse(null);
        ApiSource nguon = nguonBatKy();
        try {
            // ---- Vế 1: ngưỡng 2 (⛔ trùng mặc định 3) ⇒ lượt hỏng thứ hai phải phát chuông.
            settings.update(KHOA, "2");
            datLaiBoDem(nguon);
            int truoc = soChuong();

            hong(nguon, 2);

            assertThat(soChuong() - truoc)
                    .as("⛔ Ngưỡng đặt 2 mà hai lượt hỏng liên tiếp ⛔ sinh chuông nào ⇒ ô nhập trên "
                            + "màn hình Cấu hình hệ thống ⛔ điều khiển gì. Đây là cái chuông DUY NHẤT "
                            + "báo đường thuỷ văn đã chết (T50.4 · T50.10).")
                    .isEqualTo(1);

            // ---- Vế 2 — VẾ PHÂN BIỆT: cùng đúng hai lượt hỏng ấy, ngưỡng 9 thì phải IM.
            settings.update(KHOA, "9");
            datLaiBoDem(nguon);
            int truocB = soChuong();

            hong(nguon, 2);

            assertThat(soChuong() - truocB)
                    .as("⛔ Ngưỡng đặt 9 mà hai lượt hỏng đã phát chuông ⇒ hoặc giá trị ĐÃ GIẢI ⛔ tới "
                            + "được service, hoặc ngưỡng đang bị ghi cứng. Thiếu vế này thì bài trên "
                            + "xanh cả khi hệ chạy bằng một hằng số (luật 9).")
                    .isZero();
        } finally {
            settings.update(KHOA, cu);
            datLaiBoDem(nguon);
            jdbc.update("DELETE FROM notifications WHERE event_type = ? AND body LIKE '%T48.11%'", SU_KIEN);
        }
    }
}
