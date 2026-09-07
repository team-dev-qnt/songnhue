package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Bản ghi <b>hợp lệ</b> liền trước của <b>chính điểm đo ấy</b> — mốc so sánh duy nhất của phép kiểm
 * delta/giờ (T32.1).
 *
 * <h2>⭐⭐ Hai chữ "hợp lệ" và "chính điểm đo ấy" đều chịu lực</h2>
 *
 * <ol>
 *   <li><b>Hợp lệ</b> — nguồn đọc là {@code hydro_latest.valid_measured_at/valid_value}, ⛔ không
 *       phải {@code last_seen_at}. So với một bản ghi đang bị nghi ngờ là dựng một chuỗi trong đó
 *       mỗi bước sai kéo theo bước sau: một cảm biến trôi dần sẽ luôn "chênh ít so với lần trước"
 *       và ⛔ không bao giờ bị bắt.
 *   <li><b>Chính điểm đo ấy</b> — xem {@link PhanLoaiChatLuong}. Kiểu này ⛔ <b>không có chỗ nào</b>
 *       mang id điểm đo, và sự vắng mặt ấy là cố ý: nó làm cho phép so chéo hai điểm đo trở thành
 *       thứ <i>không viết ra được</i> ở tầng này, chứ không phải thứ bị dặn là đừng viết (luật 12).
 * </ol>
 *
 * @param mocDo mốc <b>nguồn đo</b> của bản ghi hợp lệ gần nhất
 * @param giaTri giá trị đã quy đổi về đơn vị chuẩn hoá
 */
public record SoDoTruoc(Instant mocDo, BigDecimal giaTri) {

    public SoDoTruoc {
        Objects.requireNonNull(mocDo, "mocDo");
        Objects.requireNonNull(giaTri, "giaTri");
    }
}
