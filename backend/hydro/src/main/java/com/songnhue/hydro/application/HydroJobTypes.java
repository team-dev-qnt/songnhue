package com.songnhue.hydro.application;

/**
 * Mã loại việc nền của MOD-03 — khớp cột {@code jobs.job_type}.
 *
 * <p>Khai riêng chứ không dùng {@code core.application.job.JobTypes}: đó là lớp của Core và module
 * nghiệp vụ không được import ({@code conventions.md} §1.1). Hàng đợi chỉ cần chuỗi khớp nhau giữa
 * nơi đặt việc và nơi xử lý, nên mỗi module tự quản danh mục của mình là đúng — miễn là <b>không
 * trùng chuỗi</b>. Trùng thì {@code JobWorker} chặn ngay lúc khởi động, không phải lúc chạy. Cùng
 * khuôn {@code CmsJobTypes} của MOD-01.
 *
 * <p>⚠ Tiền tố {@code HYDRO_} không phải để cho đẹp: {@code docs/runbook/poller-chet.md} tra hàng
 * đợi bằng {@code job_type LIKE 'HYDRO%'}. Đổi tiền tố là làm runbook nói sai mà không ai biết.
 */
public final class HydroJobTypes {

    /**
     * Giữ runway partition cho {@code hydro_raw_logs} và {@code hydro_readings} (T29.6).
     *
     * <p>Hết runway không làm hỏng việc ghi — bản ghi rơi vào partition {@code DEFAULT}. Nhưng
     * {@code DEFAULT} có bản ghi <i>chính là</i> tín hiệu job này đã chết, nên handler ghi cảnh báo.
     */
    public static final String PARTITION = "HYDRO_PARTITION";

    /** Dọn dữ liệu quá hạn lưu: raw · sync log · số đo · mã chưa khai (T29.7). */
    public static final String RETENTION = "HYDRO_RETENTION";

    private HydroJobTypes() {}
}
