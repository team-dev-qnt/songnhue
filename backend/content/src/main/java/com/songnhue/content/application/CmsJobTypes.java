package com.songnhue.content.application;

/**
 * Mã loại việc nền của MOD-01 — khớp cột {@code jobs.job_type}.
 *
 * <p>Khai riêng chứ không dùng {@code core.application.job.JobTypes}: đó là lớp của Core và module
 * nghiệp vụ không được import ({@code conventions.md} §1.1). Hàng đợi chỉ cần chuỗi khớp nhau giữa
 * nơi đặt việc và nơi xử lý, nên mỗi module tự quản danh mục của mình là đúng — miễn là <b>không
 * trùng chuỗi</b>. Trùng thì {@code JobWorker} chặn ngay lúc khởi động, không phải lúc chạy.
 */
public final class CmsJobTypes {

    /** Yêu cầu cổng công khai dựng lại một trang (T16.5). */
    public static final String PORTAL_REVALIDATE = "CMS_PORTAL_REVALIDATE";

    /**
     * Thư xác nhận gửi cho <b>người dân</b> vừa điền biểu mẫu liên hệ (T36.3).
     *
     * <p>⚠ Đi qua hàng đợi chứ ⛔ không gửi thẳng trong lượt POST: SMTP chậm hàng chục giây là
     * chuyện thường, và người dân ⛔ không phải chờ máy chủ thư để biết biểu mẫu đã gửi được. Việc
     * đặt vào hàng đợi nằm <b>trong cùng giao dịch</b> với lượt lưu, nên bản ghi hỏng thì thư cũng
     * ⛔ không đi.
     */
    public static final String CONTACT_ACK_MAIL = "CMS_CONTACT_ACK_MAIL";

    /** Quét liên hệ quá hạn xử lý rồi nhắc người phụ trách (T36.4, SRS UC1.3). */
    public static final String CONTACT_SLA_REMIND = "CMS_CONTACT_SLA_REMIND";

    private CmsJobTypes() {}
}
