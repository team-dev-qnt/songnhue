package com.songnhue.hydro.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Khoá tự nhiên của một dòng {@code hydro_readings} <b>trong phạm vi một loại chỉ số</b> — dùng để
 * biết lượt ghi vừa rồi đã tạo ra <i>những dòng nào</i>, không chỉ <i>bao nhiêu dòng</i>.
 *
 * <h2>⭐ Vì sao cần biết dòng nào, không chỉ đếm — T32.3</h2>
 *
 * <p>Poller chạy 2 phút/lần trên nguồn cập nhật 10 phút/lần ⇒ <b>4/5 lượt gọi trả về đúng dữ liệu
 * cũ</b>, và {@code ON CONFLICT DO NOTHING} bỏ qua chúng. Nếu thông báo "có bản ghi nghi ngờ" phát
 * theo <i>những gì nhận được</i> thay vì <i>những gì vừa ghi mới</i> thì một bản ghi đáng ngờ duy
 * nhất sẽ đánh thức người trực <b>năm lần mỗi khung 10 phút</b> cho tới khi có người xử lý — và một
 * chuông kêu sai nhịp là một chuông sẽ bị tắt.
 *
 * <p>⛔ Không kèm {@code measurementTypeId}: nơi dùng đang xử lý đúng một loại chỉ số trong một lượt
 * ingest. Thêm một trường mà mọi lời gọi truyền cùng một giá trị là nửa cặp đọc–ghi ngay từ lúc sinh
 * ra (luật 15).
 */
public record KhoaSoDo(Long stationId, Instant measuredAt) {

    public KhoaSoDo {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(measuredAt, "measuredAt");
    }
}
