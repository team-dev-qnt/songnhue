package com.songnhue.core.spi;

import java.util.Collection;

/**
 * ⭐⭐ Bắt tính lại <b>trạng thái vận hành dẫn xuất</b> của công trình — WS-33 / T33.9.
 *
 * <h2>Vì sao cổng này phải tồn tại — một nửa vòng chạy hoàn hảo vẫn cho ra số không</h2>
 *
 * <p>{@code constructions.operational_status} là một cột <b>vật chất hoá</b>: nó được
 * {@code ConstructionStatusService.tinh()} tính từ sáu mắt xích rồi <b>ghi xuống CSDL</b>. Mọi màn
 * hình, mọi marker trên bản đồ điều hành và cổng công khai đều đọc cột ấy, ⛔ không ai gọi lại hàm
 * tính.
 *
 * <p>WS-33 vừa làm <b>mắt xích 3</b> sống lại — nhưng máy cảnh báo ghi vào {@code alert_events}, và
 * ⛔ <b>không có gì</b> bảo cột dẫn xuất kia tính lại. Đo được ngay lượt chạy đầu của
 * {@code AlertEngineHttpTest}: cảnh báo mở đúng, dòng {@code alert_events} đúng, và
 * {@code operational_status} <b>vẫn là {@code BINH_THUONG}</b> cho tới lượt chạy tiếp theo của
 * {@code StatusReconcileJob} — tức tới hôm sau.
 *
 * <p>⚠ Đây đúng hình dạng luật 27 mà dự án đã trả giá nhiều lần: <i>màn hình báo lưu thành công,
 * cổng không đổi gì</i>. Nếu chỉ nhìn bảng {@code alert_events} thì mọi thứ trông hoàn hảo.
 *
 * <h2>⛔ Vì sao là một cổng, ⛔ không phải một lời gọi thẳng</h2>
 *
 * <p>{@code ConstructionStatusService} nằm ở {@code operations}, và {@code hydro} ⛔ <b>không phụ
 * thuộc</b> {@code operations} — đo được ở {@code hydro/pom.xml}: dependency duy nhất là
 * {@code songnhue-core}. Cùng khuôn với {@link ConstructionLookupPort} và {@link HydroAlertPort}:
 * hợp đồng ở {@code core.spi}, cài đặt ở module sở hữu, Spring nối hai đầu trong {@code app}.
 *
 * <h2>⚠ Vòng gọi là CÓ, và nó hữu hạn</h2>
 *
 * <p>{@code hydro} → cổng này → {@code ConstructionStatusService.recompute} → mắt xích 3 →
 * {@link HydroAlertPort#hasActiveAlert} → về lại {@code hydro}. Vế cuối là một câu <b>đọc</b> thuần
 * trên {@code alert_events}; nó ⛔ không ghi gì, nên ⛔ không có đệ quy. Nói ra ở đây vì một vòng
 * giữa hai module là thứ người đọc sau sẽ hoảng nếu không được giải thích.
 */
public interface ConstructionStatusPort {

    /**
     * Tính lại và <b>ghi</b> trạng thái dẫn xuất cho từng công trình trong danh sách.
     *
     * <p>⛔ Cài đặt ⛔ <b>không</b> lọc phạm vi đơn vị: đây là một phép tính của <i>hệ thống</i>,
     * thường chạy trong job poller nơi ⛔ không có ai đăng nhập cả. Lọc theo người gọi ở đây là để
     * kết quả phụ thuộc <i>ai bấm F5 sau cùng</i> — luật 13, §10.35 lỗi 2.
     *
     * <p>⚠ Danh sách rỗng là chuyện bình thường và phải là một lượt gọi <b>không làm gì</b>: điểm đo
     * {@code MN_SONG} (4/19 trạm) ⛔ không thuộc công trình nào theo thiết kế.
     *
     * @param constructionIds khoá <b>nội bộ</b> công trình — nơi gọi đã có sẵn từ
     *     {@code station_constructions}, nên ⛔ không bắt nó đi vòng qua {@code publicId}
     */
    void recomputeFor(Collection<Long> constructionIds);
}
