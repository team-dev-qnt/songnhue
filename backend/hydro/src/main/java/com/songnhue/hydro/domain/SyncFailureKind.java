package com.songnhue.hydro.domain;

/**
 * Vì sao một lượt lấy dữ liệu hỏng — <b>bốn nguyên nhân phải phân biệt được</b>.
 *
 * <h2>Vì sao không gộp thành một cột "lỗi" duy nhất</h2>
 *
 * <p>§10.68-B là bài học nguyên bản: bước SSH của CD từng cho <b>cùng một vân tay</b> cho ba nguyên
 * nhân cần ba cách xử lý ngược nhau, nên người trực nhìn log mà không biết phải làm gì. Ở đây cũng
 * đúng như vậy — bốn giá trị dưới đây dẫn tới bốn hành động khác nhau:
 *
 * <ul>
 *   <li>{@link #THIEU_MA_SO} → <b>người</b> phải vào cấu hình mã số. Chưa hề có lượt gọi nào.
 *   <li>{@link #NOT_WORKING} → <b>người</b> phải vào sửa mã số. Thử lại không bao giờ hết.
 *   <li>{@link #TIMEOUT} → thử lại có ích; nhiều lần liên tiếp mới là sự cố.
 *   <li>{@link #HTTP_ERROR} → nguồn còn sống nhưng từ chối; liên hệ đơn vị cấp dữ liệu.
 *   <li>{@link #EMPTY_BODY} → ⚠ <b>nguy hiểm nhất</b>, xem ghi chú của chính nó.
 * </ul>
 *
 * <h2>⚠ Hai ràng buộc CSDL soi enum này, và chúng KHÁC NHAU ĐÚNG MỘT GIÁ TRỊ</h2>
 *
 * <p>{@code ck_sync_logs_failure_kind} nhận <b>cả năm</b>: một dòng {@code sync_logs} mô tả một
 * <i>lượt polling</i>, mà lượt polling có thể hỏng <b>trước khi</b> mở kết nối.
 *
 * <p>{@code ck_hydro_raw_logs_failure_kind} nhận <b>bốn</b>, ⛔ không có {@link #THIEU_MA_SO}: một
 * dòng {@code hydro_raw_logs} là một <i>lượt gọi HTTP đã xảy ra</i>. Thiếu mã số thì không có lượt
 * gọi nào, nên cũng không có dòng raw nào để mang lý do ấy. Cho phép giá trị đó ở bảng raw là dựng
 * sẵn một trạng thái không ai ghi được — luật 15 ở tầng ràng buộc.
 *
 * <p>Ba nơi khai cùng một bộ từ vựng là đúng hình dạng luật 14 (<i>chỗ nào con người phải nhớ hai
 * nơi thì chỗ đó cần một phép kiểm nhớ hộ</i>), nên {@code HydroEnumSchemaTest} đối chiếu enum với
 * <b>cả hai</b> ràng buộc — và khẳng định luôn khoảng chênh một giá trị ấy, để nó là một quyết định
 * chứ không phải một chỗ quên.
 *
 * <p>⛔ Cố ý <b>không</b> có giá trị {@code OK}: cả hai bảng dùng quy ước <b>NULL = thành công</b>.
 * Thêm một phần tử vào một trong hai bộ từ vựng là cách rẻ nhất để chúng lệch nhau.
 */
public enum SyncFailureKind {

    /**
     * Nguồn <b>chưa được cấu hình mã số</b> ({@code api_sources.credential} rỗng) — lượt polling
     * dừng lại trước khi mở kết nối.
     *
     * <p>⭐ Đây là vế "fail-fast chuyển chỗ" mà {@code HydroApiProperties} đã hứa: thiếu mã số thì
     * <b>lượt polling</b> từ chối chạy và nói rõ lý do, ⛔ chứ không kéo cả ứng dụng đã nghiệm thu
     * Phase 1 xuống bằng một {@code @NotBlank} lúc khởi động (T28.13).
     *
     * <p>⚠ Giá trị này lệch với danh sách bốn giá trị ghi ở T29.5, và lệch <b>có chủ đích</b>: bốn
     * giá trị kia mô tả một lượt gọi đã xảy ra rồi hỏng, còn đây là lượt gọi <i>chưa từng xảy ra</i>.
     * Gộp nó vào {@link #NOT_WORKING} thì người trực đọc log thấy "sai mã số" trong khi thật ra
     * <b>chưa có mã số nào</b> — hai việc phải làm khác hẳn nhau.
     */
    THIEU_MA_SO,

    /**
     * Nguồn trả chuỗi {@code not.working}.
     *
     * <p>⚠ Hai nguyên nhân khác nhau cho ra <b>cùng một chuỗi</b>: mã số sai, và mã số <b>thiếu dấu
     * {@code ;} ở cuối</b>. Dấu chấm phẩy ấy là một phần của giá trị mã số, không phải dấu phân cách
     * — cả bốn tầng của hệ đều giữ nguyên nó và có bài kiểm hồi quy. ⛔ Đừng thêm {@code trim()} ở
     * bất kỳ đâu trên đường đi của mã số.
     */
    NOT_WORKING,

    /** Hết thời gian chờ ({@code hydro.polling.timeout-seconds}). Mạng hoặc nguồn quá tải. */
    TIMEOUT,

    /** Mã trạng thái HTTP khác 200 — nguồn còn sống nhưng từ chối phục vụ. */
    HTTP_ERROR,

    /**
     * HTTP 200 nhưng <b>không parse được dòng số đo nào</b>.
     *
     * <p>⚠ Đây là kiểu hỏng nguy hiểm nhất vì hệ thống <i>trông như</i> đang chạy: lượt gọi thành
     * công, thời gian phản hồi bình thường, không ngoại lệ nào. Nguyên nhân thường gặp là nguồn đổi
     * định dạng — và vì nguyên văn response đã được ghi vào {@code hydro_raw_logs} <b>trước khi</b>
     * parse, đây là lúc bảng ấy trả lại toàn bộ chi phí lưu trữ của nó.
     */
    EMPTY_BODY;

    /**
     * Lý do này có được phép nằm trên một dòng {@code hydro_raw_logs} không — và, cùng một câu hỏi
     * viết cách khác, <b>lượt gọi HTTP đã thật sự xảy ra chưa</b>.
     *
     * <p>⭐ Một vị ngữ, ba nơi dùng, để ba nơi ấy không thể lệch nhau (luật 14):
     *
     * <ol>
     *   <li>{@link TelemetryFetch} từ chối dựng một bản ghi mang lý do không được phép — chặn một
     *       lượt {@code INSERT} chắc chắn vỡ ràng buộc ở giữa lượt ingest;
     *   <li>{@code ck_hydro_raw_logs_failure_kind} (4 giá trị) so với {@code ck_sync_logs_failure_kind}
     *       (5 giá trị) — {@code HydroEnumSchemaTest} đối chiếu chênh lệch ấy với hàm này;
     *   <li>⭐ {@code HydroPollJobHandler} quyết <b>ném hay không ném</b> {@code UpstreamException}.
     * </ol>
     *
     * <p>Điểm 3 là chỗ hàm này chịu lực nhất, nên viết rõ luật: <b>ném khi lượt gọi ĐÃ xảy ra và
     * hỏng; ⛔ không ném khi chưa hề có lượt gọi nào.</b> {@link #THIEU_MA_SO} là một <i>trạng thái
     * cấu hình</i> — người vận hành đã thấy nó trên màn hình <i>Nguồn dữ liệu</i>, đã nhận thông báo,
     * và {@code sync_logs} đã ghi. Biến nó thành job đỏ nghĩa là <b>720 job FAILED mỗi ngày</b> cho
     * một nguồn chưa ai dán mã số vào, và một màn hình việc nền đỏ rực vì một lý do ai cũng biết là
     * một màn hình sẽ không còn ai đọc (§10.42).
     */
    public boolean duocGhiVaoRawLog() {
        return this != THIEU_MA_SO;
    }
}
