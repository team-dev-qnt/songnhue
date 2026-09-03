package com.songnhue.hydro.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO của báo cáo thuỷ văn — WS-34.
 *
 * <h2>⚠⚠ {@code @JsonInclude(ALWAYS)} trên MỌI record ở đây — ⛔ không phải trang trí</h2>
 *
 * <p>{@code application.yml} đặt {@code default-property-inclusion: non_null} cho toàn ứng dụng.
 * Với một DTO nghiệp vụ thông thường thì đó là lựa chọn đúng; với một <b>báo cáo</b> thì nó là một
 * bẫy: ô chưa đo được sẽ <b>biến mất khỏi JSON</b>, và giao diện ⛔ không phân biệt được
 * <i>"trường này rỗng"</i> với <i>"phiên bản backend cũ chưa có trường này"</i>. Cột nào đó lặng lẽ
 * không hiện, ⛔ không có lỗi nào.
 *
 * <p>⇒ Báo cáo luôn phát <b>đủ khung cột</b>, ô rỗng là {@code null} <b>tường minh</b> kèm một
 * trường lý do bên cạnh. Đó là quy tắc 16 đi hết đường ra tới dây.
 *
 * <h2>⚠ Mọi {@code BigDecimal} mang {@code @JsonFormat(shape = STRING)}</h2>
 *
 * <p>Bài học T28.27 (cổng công khai) và V2 (đường admin): {@code 2.30} tuần tự hoá thành số JSON
 * rồi qua JavaScript trở về {@code 2.3} — thang đo của phép đo bị mất, và mất im lặng. Chuỗi giữ
 * nguyên số chữ số thập phân mà nghiệp vụ đã chọn.
 */
public final class HydroReportDtos {

    private HydroReportDtos() {}

    /**
     * ⭐⭐ BC-13 — Nhật ký đồng bộ &amp; chất lượng dữ liệu (T34.3).
     *
     * <p>Trả về <b>hai bảng</b> chứ ⛔ không một: chúng có <i>hạt</i> khác nhau và trộn lại là mất
     * nghĩa. Bảng trên là (điểm đo × chỉ số × ngày) — nó trả lời <i>"số liệu nào không về"</i>; bảng
     * dưới là (nguồn × ngày) — nó trả lời <i>"vì sao"</i>.
     *
     * @param khungPhut kích thước khung của nguồn đang hiệu lực, phát ra dây để giao diện ⛔ không
     *     phải đoán mẫu số của tỷ lệ đầy đủ
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BaoCaoDongBoView(
            LocalDate tuNgay,
            LocalDate denNgay,
            int khungPhut,
            List<ChatLuongNgayView> chatLuong,
            List<DongBoNgayView> dongBo) {}

    /**
     * Một hàng chất lượng dữ liệu.
     *
     * @param soKhungBoSot ⭐ <b>Cột chịu lực của NFR-03.</b> {@code null} ⇔ chưa đo được, và khi ấy
     *     {@link #lyDoTrong} nói vì sao. ⛔ Đừng thay {@code null} bằng {@code 0} ở tầng hiển thị:
     *     0 là một khẳng định (<i>"hôm ấy poller chạy hoàn hảo"</i>), rỗng là một câu khác hẳn.
     * @param tinhLuc lượt tính lại gần nhất của kỳ — trả lời <i>"con số này cũ tới mức nào"</i> khi
     *     người dùng vừa duyệt một bản ghi nghi ngờ và bảng tổng hợp chưa kịp chạy
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChatLuongNgayView(
            LocalDate ngay,
            String stationCode,
            String stationName,
            boolean stationActive,
            String measurementTypeCode,
            String measurementTypeName,
            int soHopLe,
            int soNghiNgo,
            int soDaXoa,
            Integer soKhungMongDoi,
            Integer soKhungBoSot,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal tyLeDayDu,
            String lyDoTrong,
            Instant tinhLuc) {}

    /**
     * ⭐⭐ BC-05 — tổng hợp kỳ (T34.5).
     *
     * @param soNgayTrongKy mẫu số để người đọc biết {@code soNgayCoDuLieu} là nhiều hay ít —
     *     "12 ngày có dữ liệu" nói hai điều khác hẳn nhau tuỳ kỳ dài 14 ngày hay 90 ngày
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BaoCaoTongHopView(LocalDate tuNgay, LocalDate denNgay, int soNgayTrongKy, List<TongHopKyView> hang) {}

    /**
     * Một hàng tổng hợp kỳ.
     *
     * <h2>⛔⛔ Quy tắc 16 ép ở HÀM DỰNG</h2>
     *
     * <p><i>"Số 0 là một câu khẳng định"</i>. Một ô ghi {@code 0.000} cho mực nước trung bình nói
     * rằng nước ở cao trình 0 — một câu <b>sai và đáng tin</b>, vì nó đúng định dạng, vẽ được biểu
     * đồ, và nằm gọn giữa các con số thật. Ô đúng phải <b>rỗng kèm lý do</b>.
     *
     * <p>Hàm dựng ép: <b>hoặc</b> có đủ bộ giá trị <b>hoặc</b> có {@link #lyDoTrong}, ⛔ không bao
     * giờ cả hai và ⛔ không bao giờ không có gì. Nó ném thay vì sửa lặng lẽ — một lời dặn trong
     * javadoc là thứ lượt refactor sau sẽ ⛔ không đọc.
     *
     * @param giaTriTb ⭐ Trung bình <b>theo trọng số</b> ({@code SUM(sum)/SUM(count)}), ⛔ không
     *     phải trung bình của các trung bình ngày. Xem {@code hydro_agg_daily.sum_value}.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TongHopKyView(
            String stationCode,
            String stationName,
            String riverName,
            String positionRole,
            String measurementTypeCode,
            String measurementTypeName,
            String unit,
            long soBanGhi,
            int soNgayCoDuLieu,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal giaTriMin,
            Instant mocMin,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal giaTriMax,
            Instant mocMax,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal giaTriTb,
            String lyDoTrong) {

        public TongHopKyView {
            boolean coSo = giaTriMin != null;
            if (coSo != (giaTriMax != null) || coSo != (giaTriTb != null)) {
                throw new IllegalArgumentException(
                        "Bộ min/max/TB của một kỳ phải cùng có hoặc cùng rỗng — nửa vời là một ô nói dối");
            }
            if (coSo == (lyDoTrong != null)) {
                throw new IllegalArgumentException(
                        coSo
                                ? "Ô có số liệu ⛔ không được kèm lý do rỗng — hai câu trái nhau trên cùng một ô"
                                : "Ô rỗng BẮT BUỘC có lý do (quy tắc 16) — rỗng không lý do trông y hệt đang tải");
            }
            if (coSo && soBanGhi <= 0) {
                throw new IllegalArgumentException("Có min/max/TB thì phải có ít nhất một bản ghi sinh ra chúng");
            }
        }
    }

    /**
     * ⭐ BC-12 — chi tiết theo yêu cầu (T34.6).
     *
     * <p>⛔ {@code quality} và {@code source} là <b>hai cột chịu lực</b>, ⛔ không phải siêu dữ liệu
     * phụ trợ: chúng là thứ được đánh đổi lấy quyền ⛔ không lọc chất lượng. Rút gọn DTO này thì
     * ngoại lệ của quy tắc 14 mất chỗ dựa và biến thành đúng cái lỗi nó được miễn.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChiTietSoDoView(
            Instant mocDo,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal giaTri,
            String quality,
            String qualityReason,
            String source,
            String note,
            String reviewNote) {}

    /** Một hàng nhật ký đồng bộ, gộp theo (nguồn × ngày). */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DongBoNgayView(
            LocalDate ngay,
            String sourceCode,
            String sourceName,
            int soLuot,
            int soThanhCong,
            int soMotPhan,
            int soHong,
            int soBoQua,
            long soNhan,
            long soGhiMoi,
            long soTrung,
            long soMaLa,
            Instant hongGanNhat) {}
}
