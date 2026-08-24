package com.songnhue.core.spi;

import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Thứ một {@link JobHandler} nhận được khi chạy.
 *
 * <p>Cố ý <b>không</b> đưa cả entity {@code Job} vào đây: handler mà sửa được trạng thái job thì
 * việc "job này đã xong hay chưa" có hai nơi quyết định, và nơi thua cuộc là nơi im lặng. Handler
 * chỉ đọc dữ liệu đầu vào và báo tiến độ; kết luận thành/bại do {@link JobWorker} rút ra từ việc
 * handler có ném ngoại lệ hay không.
 */
public record JobContext(UUID jobPublicId, String jobType, String payload, Long requestedBy, IntConsumer progressSink) {

    /**
     * Báo tiến độ 0–100 để người dùng nhìn thấy trên UI.
     *
     * <p>Việc dài mà không báo tiến độ thì người dùng chỉ thấy "đang chạy" hàng phút và sẽ bấm lại —
     * tạo thêm job trùng.
     */
    public void progress(int percent) {
        progressSink.accept(percent);
    }
}
