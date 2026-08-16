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

    /** Kết xuất nhật ký quá hạn lưu trữ rồi xoá (G7, T6.13). Chạy hằng tháng. */
    public static final String AUDIT_ARCHIVE = "AUDIT_ARCHIVE";

    /** Quét virus tệp vừa tải lên; xong mới cho tệp chuyển sang tải xuống được (T6.4). */
    public static final String VIRUS_SCAN = "VIRUS_SCAN";

    /** Gửi các thông báo đang chờ ở kênh email (T6.6). */
    public static final String NOTIFICATION_DISPATCH = "NOTIFICATION_DISPATCH";

    /** Sao lưu CSDL bằng {@code pg_dump} — hằng đêm 02:00 hoặc theo yêu cầu (T7.1, M5.10). */
    public static final String DB_BACKUP = "DB_BACKUP";

    /**
     * Khôi phục CSDL từ một bản sao lưu (T7.5, M5.11).
     *
     * <p>⚠ Loại việc <b>duy nhất</b> ghi đè toàn bộ dữ liệu. {@code max_attempts = 1}: thử lại một
     * thao tác khôi phục hỏng dở là chồng thêm một lần ghi đè lên đúng chỗ đang dở dang.
     */
    public static final String DB_RESTORE = "DB_RESTORE";

    private JobTypes() {}
}
