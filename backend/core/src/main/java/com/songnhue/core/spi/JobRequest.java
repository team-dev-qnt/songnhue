package com.songnhue.core.spi;

/**
 * Yêu cầu đặt một việc vào hàng đợi.
 *
 * @param payload JSON tham số. ⛔ <b>Không đặt dữ liệu nhạy cảm vào đây</b> — payload nằm nguyên văn
 *     trong bảng {@code jobs} và lọt vào bản sao lưu
 * @param dedupKey khoá chống trùng; đã có việc <i>đang hoạt động</i> cùng khoá thì trả lại chính nó
 *     thay vì đặt thêm. {@code null} = không chống trùng
 * @param maxAttempts số lần thử tối đa, hoặc <b>{@code null} = theo {@link JobHandler#maxAttempts()}
 *     của chính loại việc này</b> (T68.30). Khai một số ở đây là một lượt <b>ghi đè có chủ đích</b>
 *     lên lời khai của handler, nên nó phải mang lý do — dùng {@link #theoHandler} khi ⛔ có lý do
 *     riêng, thay vì chép lại con số handler đã khai (luật 14)
 */
public record JobRequest(String jobType, String payload, String dedupKey, Short maxAttempts) {

    public JobRequest {
        if (maxAttempts != null && maxAttempts <= 0) {
            throw new IllegalArgumentException(
                    ("Số lần thử ⛔ được %d — một việc đặt vào hàng đợi mà ⛔ bao giờ chạy là một "
                                    + "trạng thái ⛔ ai đọc ra được: `jobs.status` đứng ở PENDING, "
                                    + "`attempts < max_attempts` sai ngay lượt đầu. Muốn *theo handler* "
                                    + "thì dùng JobRequest.theoHandler(...), ⛔ phải số 0.")
                            .formatted(maxAttempts));
        }
    }

    /**
     * Số lần thử lấy từ {@link JobHandler#maxAttempts()} của chính loại việc này.
     *
     * <p>⭐ Đây là dạng nên dùng <b>mặc định</b>. Gõ lại con số handler đã khai thì hai nơi phải nhớ
     * nhau bằng tay, và đo 23/09/2026 cho thấy điều đó ⛔ giữ nổi: {@code PORTAL_REVALIDATE} đã lệch
     * 5 ↔ 10 mà ⛔ gì báo.
     */
    public static JobRequest theoHandler(String jobType, String payload, String dedupKey) {
        return new JobRequest(jobType, payload, dedupKey, null);
    }

    /** Theo handler, không chống trùng. */
    public static JobRequest of(String jobType, String payload) {
        return theoHandler(jobType, payload, null);
    }
}
