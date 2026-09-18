package com.songnhue.core.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Mực nước <b>tại một thời điểm</b> — Bảng 3 của Báo cáo nhanh (module {@code operations}).
 *
 * <p>Cài đặt: {@code com.songnhue.hydro.application.HydroSnapshotAdapter}. SPI mỏng, chỉ khai đúng
 * phương thức đang có người gọi (coding-guide §1).
 *
 * <h2>⛔ Giá trị TỨC THỜI, ⛔ cộng dồn, ⛔ trung bình</h2>
 *
 * <p>Tiêu đề cột của mẫu là *"Mực nước hồi 16h ngày …"* ⇒ đúng một số đo tại mốc ấy. Nguồn đo 10
 * phút/lần nên mốc hiếm khi trùng ⇒ lấy <b>bản ghi gần nhất TRƯỚC ĐÓ</b> và trả kèm mốc thật để văn
 * bản ghi chú được *"gần nhất lúc HH:mm"*. Chỉ {@code quality = 'HOP_LE'} (quy tắc 14).
 */
public interface HydroSnapshotPort {

    /**
     * Lý do cột lượng mưa trống — MỘT chỗ khai cho cổng công khai, báo cáo thuỷ văn và Báo cáo nhanh.
     *
     * <p>Dời lên đây từ {@code PublicHydroService} (18/09/2026) vì Bảng 4 của Báo cáo nhanh ở module
     * {@code operations} phải nói đúng câu ấy mà ⛔ import được {@code hydro}.
     */
    String LY_DO_LUONG_MUA = "Chưa có nguồn lượng mưa: loại chỉ số đã khai nhưng chưa gắn cho điểm đo nào (mục G3-a)";

    /**
     * Một số đo mực nước.
     *
     * @param giaTriM mét — backend đã chia 100 lúc nạp; {@code null} = ⛔ có số đo hợp lệ nào trong
     *     cửa sổ nhìn lại (⛔ phải 0)
     * @param mocDo mốc nguồn đo của giá trị; {@code null} khi {@code giaTriM} null
     * @param dungMoc {@code true} khi {@code mocDo} trùng đúng thời điểm hỏi
     */
    record MucNuoc(String apiCode, BigDecimal giaTriM, Instant mocDo, boolean dungMoc) {}

    /**
     * Mực nước của từng mã API tại (hoặc ngay trước) {@code thoiDiem}.
     *
     * @return đúng một phần tử cho MỖI mã hỏi, cùng thứ tự — mã ⛔ có số đo vẫn có mặt với
     *     {@code giaTriM = null}, để nơi gọi ⛔ phải đoán "vắng" nghĩa là gì
     */
    List<MucNuoc> mucNuocTaiThoiDiem(List<String> apiCodes, Instant thoiDiem);
}
