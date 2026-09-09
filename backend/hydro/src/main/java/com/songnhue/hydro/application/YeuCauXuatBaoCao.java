package com.songnhue.hydro.application;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload của việc nền kết xuất báo cáo — T34.7.
 *
 * <h2>⛔⛔ Payload nằm NGUYÊN VĂN trong bảng {@code jobs}, và bảng ấy nằm trong mọi bản sao lưu</h2>
 *
 * <p>{@code conventions.md} §4.7: ⛔ <b>không bao giờ</b> đặt credential vào payload. Ở đây điều đó
 * dễ giữ vì mọi trường đều là <i>câu hỏi</i>, ⛔ không phải dữ liệu — nhưng bản kết xuất thì đi qua
 * cùng một đường, nên luật phải được nhắc ở đúng chỗ nó dễ bị phá: ngày nào có ai thêm một trường
 * "gửi kèm email cho ai" thì địa chỉ ấy cũng vào bản sao lưu, vĩnh viễn.
 *
 * <p>⚠ Cũng vì vậy mà ở đây dùng {@code stationPublicId} chứ ⛔ không phải khoá nội bộ: payload là
 * thứ người vận hành đọc được trên màn hình theo dõi việc nền, và một số bigint ở đó ⛔ không nói
 * cho ai điều gì.
 *
 * @param loai mã báo cáo — {@code BC13} · {@code BC05} · {@code BC11} · {@code BC12}
 * @param maLoaiChiSo chỉ BC-12 cần; hai báo cáo kia phát mọi loại chỉ số
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record YeuCauXuatBaoCao(
        String loai, LocalDate tuNgay, LocalDate denNgay, UUID stationPublicId, String maLoaiChiSo) {

    /** Mã báo cáo hợp lệ — ⛔ đối chiếu ở tầng API, ⛔ không để handler gặp một chuỗi lạ lúc 3 giờ sáng. */
    public static final String BC13 = "BC13";

    public static final String BC05 = "BC05";

    /**
     * ⭐ BC-11 — biểu tổng hợp theo tuyến sông, <b>một ngày</b>.
     *
     * <p>Thêm 09/09/2026. Tới hôm ấy BC-11 là báo cáo DUY NHẤT ⛔ không có đường xuất nào:
     * {@code RiverBoardPage.tsx} 97 dòng, 0 nút Xuất — trong khi nó là biểu mà Công ty đang dùng
     * hằng ngày trên giấy và là biểu đặc tả đánh dấu <i>"ưu tiên cao nhất"</i>
     * ({@code report-templates-proposal.md} §2.2).
     *
     * <p>⚠ {@code tuNgay} và {@code denNgay} phải TRÙNG nhau: biểu này là ảnh chụp một ngày, ⛔
     * không phải một kỳ. Giữ cả hai trường thay vì thêm một trường {@code ngay} riêng để payload
     * ⛔ không có hai cách diễn đạt cùng một khoảng — {@code kiemYeuCau} ép sự trùng ấy.
     */
    public static final String BC11 = "BC11";

    public static final String BC12 = "BC12";
}
