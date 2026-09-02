package com.songnhue.hydro.application;

import java.time.Instant;
import java.util.List;

import com.songnhue.hydro.domain.SyncFailureKind;

/**
 * Kết quả một lượt <b>Gọi thử</b> nguồn — thứ quản trị viên đọc để biết nguồn có dùng được không.
 *
 * <h2>⛔⛔ KHÔNG mang thân phản hồi, và đó là điều kiện tồn tại của kiểu này</h2>
 *
 * <p>Thân phản hồi của {@code bhh40} chứa <b>chính mã số</b> ({@code <form action="…?key=…%3b">}, đo
 * 01/09/2026). Trả nó ra API là trả credential cho bất kỳ ai mở DevTools — vi phạm thẳng
 * {@code conventions.md} §4.7. Adapter đã che mã số trước khi thân đi đâu, nhưng ⛔ <b>không dựa vào
 * lớp che ấy làm lớp bảo vệ duy nhất</b>: bộ che là mã, mã thì có ngày sửa sai. Bản ghi này không có
 * chỗ nào để đặt thân vào — đó là bảo đảm ở tầng <i>cấu trúc</i>, thứ không sửa nhầm được (luật 12).
 *
 * <p>⇒ Người vận hành cần đối chiếu nguyên văn thì tra {@code hydro_raw_logs} — nơi có phân quyền,
 * có phân mảnh, có hạn lưu và có bộ che.
 *
 * @param thanhCong lượt gọi tới nơi và thân dùng được
 * @param httpStatus {@code null} khi chưa nhận được phản hồi nào
 * @param durationMs thời gian lượt gọi — số này là thứ duy nhất phân biệt "nguồn treo" với "không có
 *     đường mạng" khi cả hai cùng hỏng
 * @param loi {@code null} khi thành công
 * @param lyDo câu ngắn cho người đọc; ⛔ đã qua bộ che mã số
 * @param soByteThan số byte thân <b>đã lưu</b> (sau khi che), 0 khi không nhận được gì
 * @param soBanGhi số dòng bóc được
 * @param soDongRac dòng không khớp định dạng — tăng đột ngột nghĩa là nguồn đổi định dạng
 * @param soDongTrung dòng trùng {@code (mã, mốc)} trong cùng một response
 * @param maChuaKhai ⭐ mã nguồn trả về mà {@code stations.api_code} chưa có — ⛔ <b>không tự tạo điểm
 *     đo</b> (quy tắc parse 5): bản suy đoán trước đó từ biểu tổng hợp đã sai 1/4 mã
 * @param soDiemDoDangHoatDong để người đọc tự đối chiếu với {@link #soBanGhi}
 * @param thieuDuLieu quy tắc parse 9 — dưới 50% số điểm đo đang hoạt động
 * @param mocDoGanNhat mốc đo chung của mẻ; {@code null} khi không bóc được dòng nào
 * @param rawLogId dòng {@code hydro_raw_logs} vừa ghi — ⛔ {@code null} nghĩa là <b>không</b> ghi
 *     được, xem lý do ở {@code TelemetryProbeService}
 */
public record KetQuaGoiThu(
        boolean thanhCong,
        Integer httpStatus,
        int durationMs,
        SyncFailureKind loi,
        String lyDo,
        int soByteThan,
        int soBanGhi,
        int soDongRac,
        int soDongTrung,
        List<String> maChuaKhai,
        int soDiemDoDangHoatDong,
        boolean thieuDuLieu,
        Instant mocDoGanNhat,
        Long rawLogId) {

    public KetQuaGoiThu {
        maChuaKhai = maChuaKhai == null ? List.of() : List.copyOf(maChuaKhai);
    }
}
