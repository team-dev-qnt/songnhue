package com.songnhue.core.domain.job;

/** Vòng đời một job nền. Khớp đúng ràng buộc {@code ck_jobs_status} trong DB. */
public enum JobStatus {
    /** Đang chờ tới lượt. Worker chỉ nhặt job ở trạng thái này và có {@code available_at <= now()}. */
    PENDING,
    RUNNING,
    SUCCEEDED,
    /** Đã hết số lần thử lại. Cần người xem lại {@code last_error} rồi cho chạy lại bằng tay. */
    FAILED,
    CANCELLED
}
