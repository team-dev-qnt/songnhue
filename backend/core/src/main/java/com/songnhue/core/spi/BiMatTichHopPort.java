package com.songnhue.core.spi;

import java.util.Optional;

/**
 * Đọc bí mật tích hợp lúc DÙNG — T61.44.
 *
 * <p>Thứ tự nguồn: giá trị đặt trên giao diện (bảng {@code integration_secrets}, AES-256-GCM) → giá trị mồi ở
 * {@code .env} → rỗng. ⛔ Nơi gọi ⛔ log, ⛔ trả ra API, ⛔ đưa vào payload job (quy tắc 13).
 */
public interface BiMatTichHopPort {

    /** Rỗng khi chưa đặt ở đâu, hoặc bản mã ⛔ giải được (đã ghi sự kiện bảo mật). */
    Optional<String> giaTri(LoaiBiMat loai);
}
