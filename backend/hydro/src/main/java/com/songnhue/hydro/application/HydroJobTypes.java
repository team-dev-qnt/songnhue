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

    /**
     * ⭐ Một lượt lấy dữ liệu từ nguồn — T31.1.
     *
     * <p>{@code @Scheduled} <b>chỉ đặt việc vào hàng đợi</b>; handler mới mở HTTP. Chạy thẳng trong
     * phương thức hẹn giờ thì hỏng là <i>im lặng</i>: không trạng thái, không thử lại, không hiện ở
     * màn hình nào — và với một nguồn không có API lịch sử, mỗi phút im lặng là số đo mất vĩnh viễn.
     *
     * <p>⚠ Khoá chống trùng là {@code HYDRO_POLL:<mã nguồn>}, ⛔ <b>không</b> kèm mốc khung. Bất biến
     * cần giữ là <i>"mỗi nguồn tối đa một lượt polling đang chạy"</i> — nếu lượt trước còn kẹt thì
     * lượt sau không được chồng lên. Kèm mốc khung vào khoá là cho phép năm lượt của cùng một khung
     * xếp hàng cùng lúc, đúng thứ khoá này sinh ra để chặn.
     */
    public static final String POLL = "HYDRO_POLL";

    /**
     * Rà điểm đo <b>mất tín hiệu</b> và tình trạng <b>chưa từng ingest được lần nào</b> — T31.8/T31.9.
     *
     * <p>⛔ Việc này ⛔ <b>không</b> ghi một cột trạng thái nào: trạng thái tín hiệu là giá trị dẫn
     * xuất ({@code StationDisplayStatus.suyRa}). Lý do nằm ở javadoc lớp ấy và nó là lý do duy nhất
     * khiến hệ nói đúng lúc poller chết — không ai ghi thì cũng không có trạng thái cũ để tin nhầm.
     */
    public static final String SIGNAL_LOSS = "HYDRO_SIGNAL_LOSS";

    /**
     * ⭐ Tính lại {@code hydro_agg_daily} cho những kỳ đang mang cờ bẩn — T34.1.
     *
     * <p>⚠ Loại việc <b>duy nhất</b> của MOD-03 chạy theo phút (5') chứ không theo ngày. Lý do và
     * hai khoá chống trùng khác nhau nằm ở {@link HydroAggScheduler}; ⛔ đừng gộp nó vào
     * {@link HydroMaintenanceScheduler} — ở đó nó sẽ thừa hưởng nhịp hằng đêm, và BC-13 (phép đo
     * duy nhất của NFR-03) sẽ chỉ nói được sự thật của ngày hôm qua.
     */
    public static final String AGG_REBUILD = "HYDRO_AGG_REBUILD";

    /**
     * ⭐ Kết xuất một báo cáo thuỷ văn ra CSV — T34.7.
     *
     * <p>⚠ Khoá chống trùng kèm <b>toàn bộ tham số</b> của yêu cầu, ⛔ không chỉ mã báo cáo: hai
     * người cùng xin BC-05 cho hai khoảng ngày khác nhau là hai việc khác nhau, và gộp chúng lại thì
     * người thứ hai nhận về bản kết xuất của người thứ nhất — đúng số liệu, sai kỳ, ⛔ không có gì
     * báo sai.
     */
    public static final String REPORT_EXPORT = "HYDRO_REPORT_EXPORT";

    private HydroJobTypes() {}
}
