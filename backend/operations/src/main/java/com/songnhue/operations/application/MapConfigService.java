package com.songnhue.operations.application;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.songnhue.core.spi.SettingPort;

/**
 * Cấu hình bản đồ nền — CN-02.4 / T23.9.
 *
 * <p>Đọc {@code settings} nhóm {@code OPERATION} (migration {@code V…1027}) và trả cho giao diện.
 * Không có giá trị nào ghi cứng ở FE: đổi nguồn tile là đổi một dòng trong màn hình cấu hình, không
 * phải dựng lại ảnh admin-app (quy tắc 12).
 *
 * <p>⚠ Mọi khoá đều có giá trị dự phòng ngay tại đây, và đó không phải sự thừa thãi so với hàng
 * {@code default_value} trong CSDL: một lượt {@code UPDATE} tay đặt giá trị rỗng, hoặc một môi
 * trường quên chạy migration, sẽ làm bản đồ không dựng được. Bản đồ trắng thì cả dashboard điều hành
 * mất nửa nội dung, trong khi cái giá của việc rơi về OSM mặc định là bằng không.
 */
@Service
public class MapConfigService {

    private static final Logger log = LoggerFactory.getLogger(MapConfigService.class);

    public static final String KEY_TILE_URL = "ops.map.tile-url";
    public static final String KEY_ATTRIBUTION = "ops.map.tile-attribution";
    public static final String KEY_CENTER_LAT = "ops.map.center-lat";
    public static final String KEY_CENTER_LNG = "ops.map.center-lng";
    public static final String KEY_DEFAULT_ZOOM = "ops.map.default-zoom";
    public static final String KEY_MAX_ZOOM = "ops.map.max-zoom";

    /** Giữ khớp với giá trị seed ở {@code V…1027} — {@code NginxSecurityHeadersTest} canh cả hai. */
    public static final String TILE_URL_MAC_DINH = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

    private static final String ATTRIBUTION_MAC_DINH = "© OpenStreetMap contributors";
    private static final BigDecimal LAT_MAC_DINH = new BigDecimal("20.9800");
    private static final BigDecimal LNG_MAC_DINH = new BigDecimal("105.7800");

    private final SettingPort settings;

    public MapConfigService(SettingPort settings) {
        this.settings = settings;
    }

    /**
     * @param centerLat tâm khung nhìn khi <b>chưa</b> công trình nào có toạ độ — có dữ liệu thật thì
     *     giao diện tự khớp khung theo các điểm, tâm này không được dùng tới
     */
    public record MapConfig(
            String tileUrl,
            String attribution,
            BigDecimal centerLat,
            BigDecimal centerLng,
            int defaultZoom,
            int maxZoom) {}

    public MapConfig current() {
        return new MapConfig(
                chuoi(KEY_TILE_URL, TILE_URL_MAC_DINH),
                chuoi(KEY_ATTRIBUTION, ATTRIBUTION_MAC_DINH),
                so(KEY_CENTER_LAT, LAT_MAC_DINH),
                so(KEY_CENTER_LNG, LNG_MAC_DINH),
                settings.getInt(KEY_DEFAULT_ZOOM, 11),
                settings.getInt(KEY_MAX_ZOOM, 18));
    }

    private String chuoi(String key, String macDinh) {
        return settings.getString(key).filter(v -> !v.isBlank()).orElse(macDinh);
    }

    /**
     * Số thập phân từ {@code settings}.
     *
     * <p>Giá trị hỏng thì <b>ghi log rồi rơi về mặc định</b>, không ném lỗi: một ký tự thừa trong ô
     * toạ độ tâm không đáng để cả màn hình điều hành trả 500. Nhưng cũng không được im lặng — người
     * vừa sửa cần biết vì sao giá trị họ nhập không có tác dụng.
     */
    private BigDecimal so(String key, BigDecimal macDinh) {
        String tho = settings.getString(key).orElse(null);
        if (tho == null || tho.isBlank()) {
            return macDinh;
        }
        try {
            return new BigDecimal(tho.trim());
        } catch (NumberFormatException e) {
            log.warn("Tham số {} không phải số thập phân hợp lệ ('{}') — dùng mặc định {}", key, tho, macDinh);
            return macDinh;
        }
    }
}
