package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * ⭐⭐ Bộ phân loại {@code HOP_LE} / {@code NGHI_NGO} — T32.1 · T32.2, <b>hàm thuần, không trạng
 * thái</b>.
 *
 * <h2>⛔⛔ CẤM validate liên điểm đo kiểu "TL phải cao hơn HL" — T32.2</h2>
 *
 * <p>Đây là <b>cấm lệnh nghiệp vụ</b>, ⛔ không phải một chỗ chưa làm. Số liệu thật của Công ty có
 * cặp bị đảo <b>một cách hợp lệ</b> — đo được:
 *
 * <table border="1">
 *   <caption>Hai cặp TL &lt; HL hợp lệ trong seed</caption>
 *   <tr><th>Công trình</th><th>Thượng lưu</th><th>Hạ lưu</th><th>Chênh</th></tr>
 *   <tr><td>Vân Đình</td><td>1,82 m</td><td>2,18 m</td><td><b>−0,36 m</b></td></tr>
 *   <tr><td>Cống tiêu tự chảy Yên Nghĩa</td><td>2,03 m</td><td>3,51 m</td><td><b>−1,48 m</b></td></tr>
 * </table>
 *
 * <p>Đó là trạng thái vận hành đúng của một cống tiêu tự chảy khi sông ngoài đang cao — nước không
 * tiêu ra được, và <i>đó chính là lúc người trực cần con số nhất</i>. Một luật "TL &gt; HL" sẽ đánh
 * dấu nghi ngờ đúng những bản ghi quan trọng nhất, rồi loại chúng khỏi báo cáo theo quy tắc 14.
 *
 * <p>⭐ Cấm lệnh này được ép bằng <b>hình dạng chữ ký hàm</b>, ⛔ không bằng lời dặn (luật 12): tham
 * số duy nhất mang bối cảnh lịch sử là {@link SoDoTruoc}, mà kiểu ấy <b>không có id điểm đo</b>. Nên
 * ở tầng này, so chéo hai điểm đo là thứ <i>không viết ra được</i> — không phải thứ bị nhắc là đừng
 * viết. {@code PhanLoaiChatLuongTest} khẳng định số lượng tham số để một lần ai đó thêm
 * {@code stationId} "cho tiện" thì bài kiểm đỏ trước khi luật cấm bị vi phạm.
 *
 * <h2>Thứ tự kiểm — khoảng vật lý trước, tốc độ sau</h2>
 *
 * <p>Hai lý do đòi hai cách xử lý ngược nhau ({@link LyDoNghiNgo}) nên bản ghi chỉ mang <b>một</b>
 * lý do, và phải là lý do <i>nặng hơn</i>. Một giá trị vừa ngoài khoảng vật lý vừa nhảy nhanh thì
 * điều đáng nói là cái thứ nhất: nó không thể đúng ở bất kỳ tình huống nào, còn cái thứ hai thì có
 * thể.
 */
public final class PhanLoaiChatLuong {

    /** Số chữ số thập phân khi in tốc độ đổi vào câu giải thích — ⛔ không dùng để quyết định. */
    private static final int CHU_SO_IN = 3;

    private static final BigDecimal GIAY_MOI_GIO = BigDecimal.valueOf(3600);

    private PhanLoaiChatLuong() {}

    /**
     * Phân loại một số đo.
     *
     * <p>⚠ Bốn tham số, và ⛔ <b>không có tham số thứ năm</b> — xem cấm lệnh T32.2 ở javadoc lớp.
     *
     * @param giaTri giá trị <b>đã quy đổi</b> về đơn vị chuẩn hoá của loại chỉ số
     * @param mocDo mốc nguồn đo của chính bản ghi này
     * @param quyTac quy tắc của loại chỉ số ấy; {@link QuyTacNghiNgo#KHONG_KIEM} khi chưa cấu hình
     * @param truoc bản ghi hợp lệ liền trước của <b>cùng điểm đo</b>; {@code null} khi chưa có bản
     *     hợp lệ nào — trạm mới khai hoặc mọi bản ghi cũ đều đang bị nghi ngờ
     */
    public static ChanDoanChatLuong danhGia(BigDecimal giaTri, Instant mocDo, QuyTacNghiNgo quyTac, SoDoTruoc truoc) {

        if (giaTri == null || mocDo == null || quyTac == null) {
            throw new IllegalArgumentException("giaTri, mocDo và quyTac là bắt buộc");
        }

        ChanDoanChatLuong ngoaiKhoang = kiemKhoangVatLy(giaTri, quyTac);
        if (ngoaiKhoang != null) {
            return ngoaiKhoang;
        }
        return kiemTocDoDoi(giaTri, mocDo, quyTac, truoc);
    }

    /** @return {@code null} khi giá trị nằm trong khoảng — hoặc khi chưa ai khai khoảng nào */
    private static ChanDoanChatLuong kiemKhoangVatLy(BigDecimal giaTri, QuyTacNghiNgo quyTac) {
        BigDecimal min = quyTac.min();
        BigDecimal max = quyTac.max();
        boolean duoiSan = min != null && giaTri.compareTo(min) < 0;
        boolean tranTran = max != null && giaTri.compareTo(max) > 0;
        if (!duoiSan && !tranTran) {
            return null;
        }
        return ChanDoanChatLuong.nghiNgo(
                LyDoNghiNgo.NGOAI_KHOANG_VAT_LY,
                "Giá trị %s ngoài khoảng vật lý %s — nhiều khả năng cảm biến hỏng hoặc nguồn đổi đơn vị"
                        .formatted(giaTri.toPlainString(), moTaKhoang(min, max)));
    }

    /**
     * ⭐ So sánh <b>nhân chéo</b>, ⛔ không chia.
     *
     * <p>{@code |Δgiá trị| × 3600 > giới hạn × Δgiây} cho ra đúng cùng kết luận với phép chia mà
     * <b>không phải chọn một quy tắc làm tròn</b>. Đây là chỗ dễ mắc: chia rồi so ở scale 3 làm mọi
     * giá trị vượt dưới 0,0005 đơn vị/giờ rơi về đúng biên và kết luận phụ thuộc {@code RoundingMode}
     * — một quyết định nghiệp vụ nằm trong một tham số kỹ thuật (quy tắc 2).
     *
     * @param truoc {@code null} ⇒ ⛔ <b>không kết luận gì</b>: "chưa có mốc để so" là một câu trả
     *     lời khác hẳn "đã so và thấy ổn"
     */
    private static ChanDoanChatLuong kiemTocDoDoi(
            BigDecimal giaTri, Instant mocDo, QuyTacNghiNgo quyTac, SoDoTruoc truoc) {

        BigDecimal gioiHan = quyTac.deltaToiDaMoiGio();
        if (gioiHan == null || truoc == null) {
            return ChanDoanChatLuong.hopLe();
        }

        long giay = Duration.between(truoc.mocDo(), mocDo).toSeconds();
        if (giay <= 0) {
            // ⚠ Bản ghi này CŨ HƠN (hoặc cùng mốc với) bản hợp lệ gần nhất — xảy ra khi nhập tay bù
            //   dữ liệu quá khứ. ⛔ Không có "tốc độ đổi" nào để tính: chia cho 0 thì nổ, còn lấy trị
            //   tuyệt đối của khoảng thời gian là bịa ra một chiều thời gian ngược. Bỏ qua phép kiểm
            //   này, ⛔ không bịa một kết luận.
            return ChanDoanChatLuong.hopLe();
        }

        BigDecimal chenh = giaTri.subtract(truoc.giaTri()).abs();
        boolean vuot = chenh.multiply(GIAY_MOI_GIO).compareTo(gioiHan.multiply(BigDecimal.valueOf(giay))) > 0;
        if (!vuot) {
            return ChanDoanChatLuong.hopLe();
        }

        BigDecimal tocDo =
                chenh.multiply(GIAY_MOI_GIO).divide(BigDecimal.valueOf(giay), CHU_SO_IN, RoundingMode.HALF_UP);
        return ChanDoanChatLuong.nghiNgo(
                LyDoNghiNgo.NHAY_QUA_NHANH,
                "Chênh %s so với bản hợp lệ trước sau %d phút (≈%s/giờ, giới hạn %s/giờ) — kiểm xem có mở cống không"
                        .formatted(chenh.toPlainString(), giay / 60, tocDo.toPlainString(), gioiHan.toPlainString()));
    }

    private static String moTaKhoang(BigDecimal min, BigDecimal max) {
        if (min != null && max != null) {
            return "[" + min.toPlainString() + " … " + max.toPlainString() + "]";
        }
        return min != null ? "≥ " + min.toPlainString() : "≤ " + max.toPlainString();
    }
}
