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

    private CmsJobTypes() {}
}
