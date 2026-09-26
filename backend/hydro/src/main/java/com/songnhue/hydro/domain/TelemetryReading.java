package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Một số đo <b>vừa bóc khỏi dây</b> — chưa biết thuộc điểm đo nào.
 *
 * <p>⭐ Đây là điểm hẹn duy nhất giữa WS-30 (adapter) và WS-31 (poller). Khác biệt với
 * {@link ReadingRow} nằm ở đúng một chỗ và đó là toàn bộ lý do có hai kiểu: bản ghi này còn mang
 * {@code apiCode} — <b>một chuỗi vô nghĩa với con người</b> — trong khi {@code ReadingRow} đã tra ra
 * {@code stationId}. Gộp hai kiểu làm một là mời việc tra cứu xảy ra ở hai chỗ.
 *
 * <h2>⚠ Giữ giá trị THÔ, quy đổi bằng một hàm</h2>
 *
 * <p>Nguồn trả số nguyên <b>cm</b> ({@code 493} = 4,93 m). Hai người tiêu thụ cần hai thứ khác nhau:
 *
 * <ul>
 *   <li>{@code hydro_readings} cần <b>giá trị đã quy đổi</b> (m, scale 3) — {@link #giaTri()};
 *   <li>{@code hydro_unmapped_readings} cần <b>giá trị nguyên trạng kèm đơn vị của nguồn</b>
 *       ({@link #giaTriTho()} + {@link #donViTho()}): mã ấy chưa khai nên ta <i>chưa biết</i> loại
 *       chỉ số của nó, mà đơn vị chuẩn hoá là thuộc tính của loại chỉ số. Quy đổi sang mét một số đo
 *       chưa biết là lượng mưa hay mực nước là bịa ra một đơn vị.
 * </ul>
 *
 * <p>⇒ Bản ghi giữ <b>một</b> sự thật (số thô + đơn vị nguồn) và quy đổi là một <b>hàm dẫn xuất</b>.
 * Nếu giữ cả hai làm hai trường thì có hai chỗ để lệch nhau, và chỗ lệch ấy là sai số liệu —
 * {@code architecture-review.md} §10.32 đã trả giá đúng loại này với toạ độ.
 */
public record TelemetryReading(String apiCode, Instant measuredAt, BigDecimal giaTriTho, String donViTho) {

    /** Đơn vị của nguồn <b>mực nước</b> — {@code getmucnuoc.aspx}, số nguyên cm. */
    public static final String DON_VI_CM = "cm";

    /**
     * Đơn vị của nguồn <b>lượng mưa</b> — {@code getluongmua.aspx} (WS-87; G3-a mở lại 26/09/2026,
     * Công ty cấp endpoint thật). Nguồn trả số <b>thập phân</b> — đo được: {@code value=0.0}.
     */
    public static final String DON_VI_MM = "mm";

    /**
     * Số chữ số thập phân của giá trị chuẩn hoá — quy ước B6, và là scale của
     * {@code hydro_readings.reading_value NUMERIC(12,3)}.
     */
    public static final int SO_LE_CHUAN = 3;

    /**
     * Scale chuẩn hoá của lượng mưa — <b>1</b>, khớp {@code measurement_types.value_scale} của
     * {@code LUONG_MUA} (seed {@code V202608311049}).
     *
     * <p>⚠ Hằng số Java này <b>soi gương</b> một cột CSDL ⇒ đúng hình dạng luật 14.
     * {@code DonViChuanHoaTest} đọc cả hai phía và đỏ khi chúng lệch — ⛔ sửa một bên rồi thôi.
     */
    public static final int SO_LE_MM = 1;

    private static final BigDecimal MOT_TRAM = new BigDecimal("100");

    /**
     * Đơn vị này đã có người biết quy đổi chưa?
     *
     * <h2>⛔⛔ Vì sao tách khỏi hàm dựng — T87.3</h2>
     *
     * <p>{@code Bhh40Parser.bocMotDong} <b>BẮT</b> {@link IllegalArgumentException} của hàm dựng rồi
     * đếm dòng ấy vào {@code soDongRac}. Nên một adapter khai sai đơn vị ⛔ hỏng lớn tiếng: <b>cả mẻ
     * biến thành rác</b>, {@code me.soDo()} rỗng, và {@code TelemetryIngestService} kết luận
     * <i>"HTTP 200 nhưng ⛔ bóc được số đo nào — nhiều khả năng nguồn đổi định dạng"</i>. Hệ
     * <b>đổ lỗi cho nguồn</b> trong khi lỗi nằm ở một hằng số của ta; người trực đi tìm nhầm chỗ
     * suốt thời gian dữ liệu mất VĨNH VIỄN (quy tắc 18).
     *
     * <p>⇒ Adapter gọi hàm này <b>lúc dựng bean</b>: khai sai thì ứng dụng ⛔ khởi động nổi, thay vì
     * chạy êm và đánh rơi số liệu. Cùng họ luật 12 — đặt bảo đảm ở chỗ ⛔ đi vòng được.
     */
    public static boolean donViDuocBiet(String donVi) {
        return DON_VI_CM.equals(donVi) || DON_VI_MM.equals(donVi);
    }

    public TelemetryReading {
        Objects.requireNonNull(apiCode, "apiCode");
        Objects.requireNonNull(measuredAt, "measuredAt");
        Objects.requireNonNull(giaTriTho, "giaTriTho");
        if (!donViDuocBiet(donViTho)) {
            // ⚠ Vẫn là một DANH SÁCH TRẮNG, ⛔ phải một phép nới. Nguồn thứ ba thì thêm một nhánh ở
            //   `donViDuocBiet` và một nhánh ở `giaTri()`, chứ ⛔ không nhận mọi chuỗi: một đơn vị
            //   không ai biết quy đổi thế nào mà vẫn đi tiếp là một số sai đi thẳng vào bảng chính.
            //   Từ chối lớn tiếng ở hàm dựng (quy tắc 16).
            throw new IllegalArgumentException("Chưa biết quy đổi đơn vị '" + donViTho
                    + "' — nguồn bhh40 trả '" + DON_VI_CM + "' (mực nước) và '" + DON_VI_MM
                    + "' (lượng mưa). Thêm nguồn mới thì thêm nhánh ở TelemetryReading, "
                    + "⛔ đừng nới điều kiện này.");
        }
    }

    /**
     * Giá trị đã quy đổi về đơn vị chuẩn hoá — <b>quy tắc parse 7</b>.
     *
     * <p>⛔ {@code BigDecimal}, ⛔ cấm {@code double}: {@code 493 / 100.0} cho ra
     * {@code 4.930000000000000159872115546022541821002960205078125} và mọi ngưỡng cảnh báo sau đó so
     * sánh với một con số không phải con số ta nghĩ. {@code RoundingMode.HALF_UP} khớp thói quen làm
     * tròn của người Việt và khớp quy ước B6.
     *
     * <p>⚠ Hôm nay {@code cm → m} luôn chia hết nên {@code HALF_UP} chưa bao giờ phải làm gì. Vẫn
     * khai tường minh: {@code divide} không có {@code RoundingMode} sẽ <b>ném</b>
     * {@code ArithmeticException} ngay khi nguồn gửi một số thập phân (regex của quy tắc 4 <i>cho
     * phép</i> {@code -?\d+([.,]\d+)?}), và nó sẽ ném ở giữa một lượt ingest.
     *
     * <h2>⛔⛔ {@code mm → mm} là phép ĐỒNG NHẤT, ⛔ phải "⛔ làm gì" — WS-87</h2>
     *
     * <p>Lượng mưa đo bằng {@code mm} và {@code measurement_types.LUONG_MUA.unit} cũng là
     * {@code mm} ⇒ hệ số quy đổi bằng <b>1</b>. Nhưng {@link #SO_LE_MM} vẫn phải áp: trả thẳng
     * {@code giaTriTho} là để scale của <i>nguồn</i> rò vào bảng chính, và ngày nguồn đổi cách in
     * số ({@code 0.0} → {@code 0.00}) thì hai bản ghi cùng một lượng mưa mang hai scale khác nhau.
     *
     * <p>⛔⛔ Và đây là chỗ một phép chia 100 nhầm sẽ <b>⛔ hỏng lớn tiếng</b>: {@code 12,5 mm} chia
     * 100 ra {@code 0,125} — một con số <b>hợp lệ về hình thức</b> cho cả mực nước lẫn lượng mưa.
     * ⇒ Nhánh theo đơn vị, ⛔ theo "hôm nay chỉ có một nguồn".
     */
    public BigDecimal giaTri() {
        if (DON_VI_MM.equals(donViTho)) {
            return giaTriTho.setScale(SO_LE_MM, RoundingMode.HALF_UP);
        }
        return giaTriTho.divide(MOT_TRAM, SO_LE_CHUAN, RoundingMode.HALF_UP);
    }

    /**
     * Giá trị <b>báo lỗi của thiết bị</b> ({@code sentinel}), tính bằng {@link #DON_VI_CM}.
     *
     * <p>⛔⛔ Đo được 09/09/2026 (T43.10): đây là danh sách <b>nguyên đơn vị nguồn</b>, ⛔ không phải
     * đơn vị chuẩn hoá — và đó là toàn bộ lý do phép kiểm này sống ở đây chứ ⛔ không ở
     * {@code PhanLoaiChatLuong}.
     */
    private static final BigDecimal[] GIA_TRI_BAO_CM = {new BigDecimal("-999"), new BigDecimal("-9999")};

    /**
     * Có phải một mã <b>"thiết bị ⛔ không đọc được"</b> ⛔ chứ không phải một mực nước?
     *
     * <h2>⛔⛔ Vì sao phép kiểm này ⛔ KHÔNG đặt ở bộ phân loại chất lượng — T43.10</h2>
     *
     * <p>Vỏ bọc {@code hydro.quality.suspect-rule} khai khoảng vật lý {@code [-10; 30]} m và một
     * chú thích migration ({@code V202609041061:233}) khẳng định: <i>"Sentinel âm của thiết bị đo
     * (-999 / -9999) rơi dưới -10"</i>. <b>Đo lại thì vế ấy đúng một nửa:</b>
     *
     * <table border="1">
     *   <caption>Sentinel sau khi quy đổi</caption>
     *   <tr><th>Nguồn trả</th><th>Quy đổi</th><th>Trong {@code [-10; 30]}?</th><th>Kết luận cũ</th></tr>
     *   <tr><td>{@code -9999}</td><td>−99,99 m</td><td>⛔ không</td><td>NGHI_NGO ✓</td></tr>
     *   <tr><td>{@code -999}</td><td><b>−9,99 m</b></td><td><b>CÓ</b></td><td><b>HOP_LE ✗</b></td></tr>
     * </table>
     *
     * <p>⇒ {@code -999} đi thẳng vào bảng chính như một <b>mực nước thật</b>, rồi vào báo cáo, rồi
     * vào phép so ngưỡng cảnh báo. Và bài kiểm duy nhất canh mục ấy chỉ thử {@code -9999} — nó xanh
     * trong đúng tình huống nó sinh ra để bắt (luật 7 · luật 9).
     *
     * <p><b>Sau khi quy đổi thì thông tin đã mất.</b> {@code -9,99 m} là một số hợp lệ về mặt hình
     * thức; thứ duy nhất phân biệt nó với một số đo thật là <i>nó bằng đúng sentinel trong đơn vị
     * của nguồn</i>. Nên phép kiểm phải đứng ở đây — chỗ cuối cùng còn giữ {@link #giaTriTho()} và
     * {@link #donViTho()} — ⛔ không phải ở {@code PhanLoaiChatLuong}, nơi chỉ còn con số đã quy đổi.
     * Đây đúng là luật 12: đặt một bảo đảm ở chỗ <i>dữ liệu đi qua</i>, và ở đây là chỗ <b>duy
     * nhất</b> nó còn đi qua được.
     *
     * <h2>⛔ Vì sao GẮN CỜ chứ ⛔ không VỨT</h2>
     *
     * <p>Quy tắc 18: ⛔ không có API lịch sử, <b>mất dữ liệu là vĩnh viễn</b>. Bản ghi vẫn vào bảng
     * chính dưới {@code NGHI_NGO} (chốt F2) ⇒ quy tắc 14 tự động giữ nó ra khỏi <b>mọi</b> báo cáo,
     * dashboard và phép so ngưỡng, còn người trực vẫn thấy nó ở màn hình <i>Dữ liệu nghi ngờ</i> và
     * biết cảm biến đang hỏng. Vứt ở tầng parse thì con số biến mất và <b>trạm hỏng trông y hệt
     * trạm im lặng</b>.
     *
     * <p>⚠ Dùng {@code compareTo}, ⛔ không {@code equals}: {@code BigDecimal} so bằng
     * {@code equals} thì {@code -999} ⛔ khác {@code -999.0} — nguồn đổi cách in số thập phân là
     * phép kiểm này lặng lẽ hết tác dụng.
     */
    public boolean laGiaTriBao() {
        if (!DON_VI_CM.equals(donViTho)) {
            // ⛔⛔ WS-87 — nguồn lượng mưa (mm) đi qua đây và PHẢI trả `false`, ⛔ phải vì "chắc là
            //    giống nhau" mà vì ta **chưa đo được** sentinel của trạm mưa: mọi giá trị quan sát
            //    ngày 26/09 đều là `0.0` (⛔ mưa). Áp danh sách của mực nước sang là đoán — và
            //    `-999 mm` nếu có thật thì lại là một lượng mưa **⛔ thể có**, tức đoán sai theo
            //    chiều im lặng. Ngày có mẫu mưa thật thì thêm một nhánh Ở ĐÂY, kèm số đo.
            return false;
        }
        for (BigDecimal bao : GIA_TRI_BAO_CM) {
            if (giaTriTho.compareTo(bao) == 0) {
                return true;
            }
        }
        return false;
    }

    /** Khoá chống trùng trong phạm vi một response — quy tắc parse 8, vế phía adapter. */
    public String khoaTrung() {
        return apiCode + '@' + measuredAt;
    }
}
