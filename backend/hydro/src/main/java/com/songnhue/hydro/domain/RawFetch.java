package com.songnhue.hydro.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Kết quả <b>một lượt gọi HTTP</b> tới nguồn — thứ được ghi nguyên văn vào {@code hydro_raw_logs}
 * <b>trước khi</b> parse.
 *
 * <p>⭐ Thứ tự ấy là bất biến của cả MOD-03, không phải một tối ưu: nguồn
 * {@code songnhue.bhh40.net} <b>không có API lịch sử</b> (đo 01/09/2026 — gọi kèm
 * {@code &date=…&from=…&to=…} trả về byte y hệt), nên nếu parse hỏng <i>trước</i> khi kịp ghi thì
 * response ấy mất vĩnh viễn cùng với mọi cơ hội tìm ra vì sao nó hỏng.
 *
 * @param apiSourceId nguồn đã gọi
 * @param fetchedAt mốc <b>ta gọi</b> — cũng là khoá phân mảnh của {@code hydro_raw_logs}. Truyền
 *     tường minh chứ không để {@code DEFAULT now()} quyết định: đây là thứ quyết định bản ghi rơi
 *     vào partition nào, và một giá trị do CSDL tự đặt là một giá trị không bài kiểm nào điều khiển
 *     được — nhánh partition {@code DEFAULT} khi ấy không có cách nào đi qua (luật 7)
 * @param frameStart khung 10' lượt gọi này nhắm tới; {@code null} khi hỏng trước lúc xác định được
 * @param httpStatus {@code null} khi chưa nhận được phản hồi nào (timeout, lỗi mạng)
 * @param durationMs thời gian lượt gọi, mili-giây
 * @param body ⭐ <b>nguyên văn</b>, chưa cắt, chưa chuẩn hoá — kể cả trang HTML rỗng ở đuôi
 * @param failureKind {@code null} = thành công. Xem {@link SyncFailureKind} về việc vì sao bốn giá
 *     trị phải phân biệt được
 * @param failureDetail mô tả ngắn cho người trực đọc; ⛔ tuyệt đối không chứa mã số nguồn
 */
public record RawFetch(
        Long apiSourceId,
        Instant fetchedAt,
        Instant frameStart,
        Integer httpStatus,
        Integer durationMs,
        String body,
        SyncFailureKind failureKind,
        String failureDetail) {

    /** Giới hạn của cột {@code failure_detail}; cắt ở đây thay vì để CSDL từ chối cả lượt ghi. */
    private static final int DAI_TOI_DA_LY_DO = 1000;

    public RawFetch {
        Objects.requireNonNull(apiSourceId, "apiSourceId");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        if (failureDetail != null && failureDetail.length() > DAI_TOI_DA_LY_DO) {
            failureDetail = failureDetail.substring(0, DAI_TOI_DA_LY_DO);
        }
    }

    public boolean thanhCong() {
        return failureKind == null;
    }

    /** Số byte của thân phản hồi; 0 khi không nhận được gì — ⛔ khác {@code null}. */
    public int soByte() {
        return body == null ? 0 : body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
