package com.songnhue.core.application.job;

/**
 * Mã loại việc nền — khớp cột {@code jobs.job_type}.
 *
 * <p>Gom thành hằng số thay vì rải chuỗi: gõ nhầm tên loại thì job vào hàng đợi bình thường rồi nằm
 * đó mãi vì không handler nào nhận, và triệu chứng duy nhất là "việc không bao giờ xong".
 */
public final class JobTypes {

    /** Dọn token trong denylist và phiên quá hạn lưu trữ (trước ở {@code TokenMaintenanceJob}). */
    public static final String TOKEN_CLEANUP = "TOKEN_CLEANUP";

    /** Tạo trước partition {@code audit_logs} cho các tháng tới. */
    public static final String AUDIT_PARTITION = "AUDIT_PARTITION";

    /** Dọn chính bảng hàng đợi — job đã xong không phải nơi lưu lịch sử. */
    public static final String JOB_PURGE = "JOB_PURGE";

    /** Gửi các thông báo đang chờ ở kênh email (T6.6). */
    public static final String NOTIFICATION_DISPATCH = "NOTIFICATION_DISPATCH";

    private JobTypes() {}
}
