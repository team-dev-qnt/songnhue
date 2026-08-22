package com.songnhue.core.spi;

/**
 * Yêu cầu đặt một việc vào hàng đợi.
 *
 * @param payload JSON tham số. ⛔ <b>Không đặt dữ liệu nhạy cảm vào đây</b> — payload nằm nguyên văn
 *     trong bảng {@code jobs} và lọt vào bản sao lưu
 * @param dedupKey khoá chống trùng; đã có việc <i>đang hoạt động</i> cùng khoá thì trả lại chính nó
 *     thay vì đặt thêm. {@code null} = không chống trùng
 * @param maxAttempts số lần thử tối đa trước khi coi là hỏng
 */
public record JobRequest(String jobType, String payload, String dedupKey, short maxAttempts) {

    /** Mặc định: thử tối đa 3 lần, không chống trùng. */
    public static JobRequest of(String jobType, String payload) {
        return new JobRequest(jobType, payload, null, (short) 3);
    }
}
