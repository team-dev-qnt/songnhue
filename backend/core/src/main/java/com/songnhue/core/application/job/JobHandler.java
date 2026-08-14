package com.songnhue.core.application.job;

/**
 * Cách cắm một loại việc nền vào hàng đợi — pattern P5.
 *
 * <p>Module nghiệp vụ chỉ cần khai một bean cài giao diện này; {@link JobWorker} tự tìm thấy qua
 * Spring, không phải đăng ký ở đâu cả. Đó là điều kiện để Phase 1+ "chỉ khai báo cấu hình".
 *
 * <p><b>Hợp đồng:</b>
 *
 * <ul>
 *   <li>Ném ngoại lệ = thất bại. Không nuốt lỗi rồi return bình thường — worker sẽ ghi SUCCEEDED cho
 *       một việc chưa làm xong, và không ai biết.
 *   <li>Phải <b>chạy lại được</b>. Job có thể được thử tới {@code max_attempts} lần, và job đang chạy
 *       dở lúc node chết sẽ được trả về hàng đợi. Handler nào không idempotent thì lần thử thứ hai
 *       tạo ra dữ liệu trùng.
 *   <li>Việc dài phải gọi {@link JobContext#progress(int)}.
 * </ul>
 */
public interface JobHandler {

    /** Khớp với cột {@code jobs.job_type}. Hai handler cùng mã là lỗi cấu hình, chặn lúc khởi động. */
    String jobType();

    /** @throws Exception bất kỳ lỗi nào — worker lo phần thử lại và ghi nhận */
    void handle(JobContext context) throws Exception;

    /** Số lần thử tối đa. Việc gọi ra ngoài mạng nên để cao hơn việc thuần tính toán. */
    default short maxAttempts() {
        return 3;
    }
}
