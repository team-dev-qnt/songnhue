package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * ⭐⭐ Bộ đánh giá ngưỡng cảnh báo — CN-03.6 / T33.5, <b>hàm thuần, không trạng thái</b>.
 *
 * <p>Cùng khuôn {@link PhanLoaiChatLuong} của WS-32 và cố ý giống tới từng chi tiết ở ba chỗ, vì cả
 * ba đều là bài học đã trả giá:
 *
 * <ol>
 *   <li><b>Không có id điểm đo trong chữ ký.</b> Bối cảnh lịch sử duy nhất là {@link SoDoTruoc}, kiểu
 *       ⛔ không mang id trạm — nên so chéo hai điểm đo là thứ <i>không viết ra được</i> ở tầng này
 *       (T32.2). Ngưỡng của Cống Liên Mạc ⛔ không bao giờ được đọc số của Vân Đình.
 *   <li><b>So nhân chéo, ⛔ không chia</b> ở {@link AlertConditionType#RATE_OF_CHANGE} — xem javadoc
 *       hàm. Chia rồi so là đưa một quyết định nghiệp vụ vào một {@code RoundingMode}.
 *   <li><b>"Chưa so được" là một kết cục riêng</b>, ⛔ không phải "không vi phạm" — xem
 *       {@link KetLuanNguong}.
 * </ol>
 *
 * <h2>⚠⚠ Biên là KHÔNG vi phạm — và đây là một quyết định nghiệp vụ, không phải một chi tiết</h2>
 *
 * <p>{@code GT} so bằng {@code >}, {@code LT} bằng {@code <}, {@code OUT_OF_RANGE} vi phạm khi ra
 * <b>hẳn</b> ngoài khoảng đóng. Một luật duy nhất, không ngoại lệ: <b>giá trị đúng bằng ngưỡng thì
 * không phát cảnh báo</b>. Chọn vậy để tên hằng nói đúng thứ mã làm — một {@code GT} cư xử như
 * {@code >=} là đúng cái bẫy luật 14, và nó sẽ sống sót mọi lượt rà vì không ai đọc lại tên enum.
 *
 * <p>⬜ <b>Phải hỏi Công ty, gắn vào G9-a (đang mở).</b> Thực hành phòng chống lụt bão nói <i>"mực
 * nước <b>đạt</b> báo động I"</i> — tức là {@code >=}. Chênh lệch giữa hai cách hiểu đúng bằng một
 * bước đo (1 cm), nên nó ⛔ không bao giờ lộ ra khi thử, và sẽ chỉ được phát hiện vào đúng lúc không
 * nên phát hiện. {@code DanhGiaNguongTest} ghim hành vi hiện tại bằng một bài kiểm ở đúng biên: đổi
 * ý thì bài ấy đỏ, ⛔ không đổi lặng lẽ được.
 */
public final class DanhGiaNguong {

    /** Số chữ số thập phân khi in tốc độ đổi vào câu giải thích — ⛔ không dùng để quyết định. */
    private static final int CHU_SO_IN = 3;

    private static final BigDecimal GIAY_MOI_GIO = BigDecimal.valueOf(3600);

    private DanhGiaNguong() {}

    /**
     * Đánh giá một số đo trước một luật ngưỡng.
     *
     * <p>⛔ Nơi gọi <b>phải</b> đã lọc {@code quality = HOP_LE} trước khi tới đây (quy tắc 14, T33.5).
     * Hàm này ⛔ không kiểm lại — nó không nhận {@code ReadingQuality} nào, và đó là chủ ý: một tham
     * số chất lượng ở đây sẽ mời người sau truyền {@code NGHI_NGO} vào rồi trông chờ hàm tự lo.
     *
     * @param giaTri giá trị đã quy đổi về đơn vị chuẩn hoá của loại chỉ số
     * @param mocDo mốc nguồn đo của chính bản ghi này
     * @param dieuKien luật cần đánh giá
     * @param truoc số đo <b>hợp lệ</b> liền trước của <b>cùng điểm đo × cùng loại chỉ số</b>;
     *     {@code null} khi chưa có
     */
    public static KetLuanNguong danhGia(BigDecimal giaTri, Instant mocDo, DieuKienNguong dieuKien, SoDoTruoc truoc) {

        if (giaTri == null || mocDo == null || dieuKien == null) {
            throw new IllegalArgumentException("giaTri, mocDo và dieuKien là bắt buộc");
        }

        return switch (dieuKien.loai()) {
            case GT -> so(giaTri.compareTo(dieuKien.nguong()) > 0, "cao hơn", giaTri, dieuKien.nguong());
            case LT -> so(giaTri.compareTo(dieuKien.nguong()) < 0, "thấp hơn", giaTri, dieuKien.nguong());
            case OUT_OF_RANGE -> kiemNgoaiKhoang(giaTri, dieuKien);
            case RATE_OF_CHANGE -> kiemTocDoDoi(giaTri, mocDo, dieuKien, truoc);
        };
    }

    private static KetLuanNguong so(boolean vuot, String chieu, BigDecimal giaTri, BigDecimal nguong) {
        if (!vuot) {
            return KetLuanNguong.khongViPham();
        }
        return KetLuanNguong.viPham(
                "Giá trị %s %s ngưỡng %s".formatted(giaTri.toPlainString(), chieu, nguong.toPlainString()));
    }

    private static KetLuanNguong kiemNgoaiKhoang(BigDecimal giaTri, DieuKienNguong dieuKien) {
        boolean duoiSan = giaTri.compareTo(dieuKien.nguong()) < 0;
        boolean tranTran = giaTri.compareTo(dieuKien.nguongCao()) > 0;
        if (!duoiSan && !tranTran) {
            return KetLuanNguong.khongViPham();
        }
        return KetLuanNguong.viPham("Giá trị %s ngoài khoảng [%s … %s]"
                .formatted(
                        giaTri.toPlainString(),
                        dieuKien.nguong().toPlainString(),
                        dieuKien.nguongCao().toPlainString()));
    }

    /**
     * ⭐ So <b>nhân chéo</b>: {@code |Δgiá trị| × 3600 > giới hạn × Δgiây}.
     *
     * <p>Cho ra đúng cùng kết luận với phép chia mà ⛔ <b>không phải chọn một quy tắc làm tròn</b>.
     * Chia rồi so ở scale 3 làm mọi giá trị vượt dưới 0,0005 đơn vị/giờ rơi về đúng biên, và kết
     * luận khi ấy phụ thuộc {@code RoundingMode} — một quyết định nghiệp vụ giấu trong một tham số
     * kỹ thuật (quy tắc 2). Phép chia dưới đây <b>chỉ</b> để in ra câu giải thích.
     *
     * <p>⚠ Hai nhánh ⛔ <b>không</b> trả "không vi phạm":
     *
     * <ul>
     *   <li>{@code truoc == null} — chưa có mốc nào để so. Trả {@code KHONG_KET_LUAN_DUOC}: nói
     *       <i>"trạm ổn"</i> lúc chưa canh gì cả là câu khẳng định sai nguy hiểm nhất của cả module.
     *   <li>{@code Δgiây <= 0} — bản ghi này cũ hơn (hoặc cùng mốc với) bản hợp lệ gần nhất, xảy ra
     *       khi nhập tay bù dữ liệu quá khứ. ⛔ Không có "tốc độ đổi" nào để tính, và lấy trị tuyệt
     *       đối của khoảng thời gian là bịa ra một chiều thời gian ngược.
     * </ul>
     */
    private static KetLuanNguong kiemTocDoDoi(
            BigDecimal giaTri, Instant mocDo, DieuKienNguong dieuKien, SoDoTruoc truoc) {

        if (truoc == null) {
            return KetLuanNguong.khongKetLuanDuoc(
                    "Chưa có số đo hợp lệ nào trước đó để tính tốc độ đổi — luật này chưa canh được gì");
        }

        long giay = Duration.between(truoc.mocDo(), mocDo).toSeconds();
        if (giay <= 0) {
            return KetLuanNguong.khongKetLuanDuoc(
                    "Mốc đo không muộn hơn bản hợp lệ gần nhất (%s) — không có khoảng thời gian để tính tốc độ"
                            .formatted(truoc.mocDo()));
        }

        BigDecimal gioiHan = dieuKien.nguong();
        BigDecimal chenh = giaTri.subtract(truoc.giaTri()).abs();
        boolean vuot = chenh.multiply(GIAY_MOI_GIO).compareTo(gioiHan.multiply(BigDecimal.valueOf(giay))) > 0;
        if (!vuot) {
            return KetLuanNguong.khongViPham();
        }

        BigDecimal tocDo =
                chenh.multiply(GIAY_MOI_GIO).divide(BigDecimal.valueOf(giay), CHU_SO_IN, RoundingMode.HALF_UP);
        return KetLuanNguong.viPham("Đổi %s trong %d phút (≈%s/giờ, ngưỡng %s/giờ)"
                .formatted(chenh.toPlainString(), giay / 60, tocDo.toPlainString(), gioiHan.toPlainString()));
    }
}
