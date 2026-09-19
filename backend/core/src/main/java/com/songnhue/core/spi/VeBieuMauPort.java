package com.songnhue.core.spi;

import java.time.Instant;

/**
 * Vé biểu mẫu công khai — kiểm "tốc độ con người" (ASVS 11.1.2), T73.9. Hiện thực: {@code VeBieuMauService} (core).
 *
 * <p>Cổng cho {@code content}: nơi phát vé ({@code GET /api/v1/public/bieu-mau/ve}) và nơi kiểm vé
 * ({@code InboundSubmissionGate}) ⛔ phải biết vé ký bằng gì.
 */
public interface VeBieuMauPort {

    /** Vé mang mốc phát lúc này, do máy chủ ký. */
    String phat();

    /**
     * Tuổi tối thiểu HIỆN HÀNH của vé (giây, đã kẹp trần). Cổng trả kèm vé để giao diện tự CHỜ đủ tuổi trước khi gửi —
     * một người điền nhanh thấy "Đang gửi…" thêm một nhịp, thay vì nhận một câu lỗi về thứ họ ⛔ làm sai.
     */
    long tuoiToiThieuGiay();

    /**
     * @throws com.songnhue.core.common.exception.BusinessRuleException {@code CMS-2025} — thiếu vé, vé giả/hỏng, gửi
     *     quá nhanh, hoặc vé quá hạn
     */
    void kiem(String ve, Instant now);
}
