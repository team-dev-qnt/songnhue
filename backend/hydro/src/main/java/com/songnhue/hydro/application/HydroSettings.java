package com.songnhue.hydro.application;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.songnhue.core.spi.SettingPort;

/**
 * Đọc tham số vận hành của MOD-03 từ bảng {@code settings} — nửa còn thiếu của một cặp đọc–ghi.
 *
 * <h2>Vì sao lớp này là một bản vá, không phải một tiện ích</h2>
 *
 * <p>Tám khoá nhóm {@code HYDRO} đã được seed từ <b>13/08/2026</b>
 * ({@code V202608131009__core_seed_settings.sql}) và tới 31/08/2026 <b>không một dòng mã nào đọc
 * chúng</b>. Đó là vi phạm luật 15 đang treo suốt 18 ngày: <i>công tắc / cột / tham số chưa ai đọc
 * là một lỗi, không phải việc để dành</i> — người vận hành thấy tám ô nhập trên màn hình Cấu hình
 * hệ thống, sửa chúng, và không có gì đổi.
 *
 * <p>Lớp này khai hàm đọc cho sáu khoá — nhưng ⚠ <b>tính tới 01/09/2026 chỉ BỐN khoá thật sự có
 * người gọi lúc chạy</b>: {@link #cronPolling}, {@link #khungNguon}, {@link #timeoutGoiNguon} và
 * {@link #soLanThuLai}, cả bốn qua {@code ApiSourceService.thamSoHieuLuc()}.
 *
 * <p>⬜ Hai hàm còn lại chưa ai gọi (nợ <b>T28.36</b>): {@link #soKhungMatTinHieu} chờ job phát hiện
 * mất tín hiệu (WS-31) — người tiêu thụ duy nhất của nó là {@code StationDisplayStatus.suyRa()}, mà
 * hàm ấy <i>cũng</i> chưa ai gọi (T28.20); {@link #soNamGiuDuLieu} chờ job dọn dữ liệu cũ (WS-29).
 * ⛔ Tới WS-31 mà vẫn không ai gọi thì <b>gỡ hàm</b>, đừng để lại một cơ chế chưa ai đi qua (luật 15).
 *
 * <p>📌 Câu "lớp này đóng sáu khoá" nằm ở đây từ 31/08 và <b>không đúng</b> — một hàm đọc tồn tại
 * không phải là một khoá đã được đọc. Đó đúng là hình dạng nợ mà chính lớp này sinh ra để trả, tái
 * diễn ở tầng javadoc: nửa cặp đọc–ghi trông y hệt một cặp hoàn chỉnh.
 *
 * <p>⬜ Hai khoá <i>không</i> có hàm đọc — {@code hydro.threshold.default-set} và
 * {@code hydro.quality.suspect-rule} — là JSON rỗng chờ Công ty (G9-a) và chỉ có nghĩa khi bộ
 * validate (WS-32) và alert engine (WS-33) ra đời; nối vế đọc cho chúng ở đúng hai hạng mục ấy,
 * hoặc <b>gỡ khỏi seed</b> nếu Phase 2 không dùng.
 *
 * <h2>⚠ Đọc mỗi lần gọi, không cache trong trường</h2>
 *
 * <p>{@link SettingPort} đã có bộ đệm Caffeine phía sau và bộ đệm ấy được dọn bằng
 * {@code SettingChangedEvent} phát ở {@code AFTER_COMMIT} (§10.13). Cache lần thứ hai ở đây là một
 * bản sao thứ hai của cùng một sự thật, và nó sẽ không nghe được sự kiện dọn — người vận hành đổi
 * chu kỳ polling rồi phải khởi động lại app mới có tác dụng, đúng thứ quy tắc 12 cấm.
 *
 * <h2>⛔ Giá trị mặc định ở đây là LƯỚI AN TOÀN, không phải nơi chốt giá trị</h2>
 *
 * <p>Nơi chốt là migration seed. Mặc định chỉ dùng đến khi CSDL chưa có khoá — và nếu nó khác giá
 * trị seed thì hai nơi nói hai điều khác nhau về cùng một tham số, kiểu lệch mà §10.29-a gọi tên:
 * <i>canh giá trị ĐÃ GIẢI, đừng canh giá trị MẶC ĐỊNH</i>. Con số dưới đây <b>trùng khít</b> seed.
 */
@Component
public class HydroSettings {

    /** Khớp `V202608131009` — 2 phút/lần vào phút lẻ, giây 45 (chốt G3). */
    public static final String MAC_DINH_CRON = "45 1/2 * * * *";

    static final String KHOA_CRON = "hydro.polling.cron";
    static final String KHOA_KHUNG_NGUON = "hydro.polling.source-frame-minutes";
    static final String KHOA_TIMEOUT = "hydro.polling.timeout-seconds";
    static final String KHOA_MAX_RETRY = "hydro.polling.max-retry";
    static final String KHOA_MAT_TIN_HIEU = "hydro.station.signal-loss-frames";
    static final String KHOA_RETENTION = "hydro.retention-years";

    private final SettingPort settings;

    public HydroSettings(SettingPort settings) {
        this.settings = settings;
    }

    /**
     * Biểu thức cron của lượt polling — chốt G3.
     *
     * <p>⚠ <b>Giây 45, không phải giây 0.</b> Nguồn làm việc theo khung 10 phút và chỉ đẩy dữ liệu
     * mới lên API trong cửa sổ {@code x1:30 → x8:30}; gọi vào giây 0 của phút lẻ đầu tiên là gọi
     * <i>trước</i> mốc {@code 01:30}, tức là chắc chắn lấy về dữ liệu của khung cũ.
     */
    public String cronPolling() {
        return settings.getString(KHOA_CRON).filter(s -> !s.isBlank()).orElse(MAC_DINH_CRON);
    }

    /**
     * Độ dài khung cập nhật của nguồn, phút — cơ sở của phép rate-limit.
     *
     * <p>Một "khung" là đơn vị mà nguồn làm việc theo: {@code frame = floor(now / khung)}. Poller
     * bỏ lượt gọi khi <b>toàn bộ</b> điểm đo đang hoạt động đã có bản ghi thuộc khung hiện tại.
     */
    public Duration khungNguon() {
        return Duration.ofMinutes(settings.getInt(KHOA_KHUNG_NGUON, 10));
    }

    /** Thời gian chờ tối đa một lượt gọi nguồn. Ràng buộc seed: {@code min=5;max=300}. */
    public Duration timeoutGoiNguon() {
        return Duration.ofSeconds(settings.getInt(KHOA_TIMEOUT, 30));
    }

    /** Số lần thử lại khi gọi nguồn hỏng. Ràng buộc seed: {@code min=0;max=10}. */
    public int soLanThuLai() {
        return settings.getInt(KHOA_MAX_RETRY, 3);
    }

    /**
     * Số khung liên tiếp không có bản ghi thì coi điểm đo là <b>mất tín hiệu</b> — chốt G3.
     *
     * <p>Mặc định 3 khung ≈ 30 phút. Điểm đo mất tín hiệu bị <b>loại khỏi đánh giá ngưỡng</b>
     * ({@code HYD-2004}) và hiện marker xám trên GIS: giá trị cũ của một trạm đã chết không được
     * dùng để kết luận mực nước hiện tại.
     */
    public int soKhungMatTinHieu() {
        return settings.getInt(KHOA_MAT_TIN_HIEU, 3);
    }

    /** Số năm giữ dữ liệu chi tiết {@code hydro_readings} — chốt D5. */
    public int soNamGiuDuLieu() {
        return settings.getInt(KHOA_RETENTION, 5);
    }
}
