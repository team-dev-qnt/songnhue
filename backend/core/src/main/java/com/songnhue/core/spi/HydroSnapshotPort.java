package com.songnhue.core.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
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
     *
     * <h2>⚠ Câu này ĐỔI BẢN CHẤT ngày 26/09/2026 (WS-87)</h2>
     *
     * <p>Bản cũ nói <i>"chưa có nguồn lượng mưa"</i>. Sau khi Công ty cấp {@code getluongmua.aspx}
     * thì vế ấy <b>sai</b>, và sai theo chiều đắt: nó gửi người vận hành đi <b>chờ một endpoint đã
     * có</b>, trong khi thứ thật sự còn thiếu là <b>bảng ánh xạ 15 mã ↔ trạm</b> — việc của Công ty,
     * thuộc G8. Hai câu nghe gần giống nhau mà dẫn tới hai việc khác hẳn (T52.8 · T59.0).
     */
    String LY_DO_LUONG_MUA = "Nguồn lượng mưa đã nối (26/09/2026) nhưng 15 mã trạm mưa chưa được khai "
            + "thành điểm đo — số đo đang giữ nguyên văn ở hydro_unmapped_readings, chờ Công ty cấp "
            + "bảng ánh xạ mã ↔ trạm (mục G8)";

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

    /**
     * Một liên kết điểm đo ↔ công trình ở vế THƯỢNG LƯU / HẠ LƯU — Bảng 3 suy ra điểm đo của từng cống
     * từ đây thay vì một danh sách mã ghi trong mã nguồn (18/09/2026).
     *
     * @param vaiTro {@code THUONG_LUU} | {@code HA_LUU}
     * @param chinh {@code station_constructions.is_primary}
     */
    record DiemDoVe(Long constructionId, String vaiTro, String apiCode, boolean chinh) {}

    /**
     * Mọi điểm đo (có mã API, chưa xoá) gắn vào các công trình đã cho ở vai trò THƯỢNG LƯU / HẠ LƯU.
     *
     * <p>⛔ lọc phạm vi đơn vị — Báo cáo nhanh là văn bản cấp Công ty (cùng lý do
     * {@code ConstructionLookupPort.timTheoIds}). Một vế có nhiều điểm đo thì trả ĐỦ — chọn cái nào là
     * việc của nơi gọi, và nơi gọi phải nói ra khi ⛔ chọn được.
     */
    List<DiemDoVe> diemDoMucNuocCuaCongTrinh(Collection<Long> constructionIds);
}
