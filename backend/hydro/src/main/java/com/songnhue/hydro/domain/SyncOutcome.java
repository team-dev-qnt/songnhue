package com.songnhue.hydro.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Kết cục <b>một lượt polling</b> — một dòng của {@code sync_logs}, nuôi màn hình <i>Nhật ký đồng
 * bộ</i> (M3.16).
 *
 * <p>⚠ Bốn bộ đếm để riêng chứ không gộp. {@link #writtenCount} bằng 0 là kết cục <b>bình thường</b>
 * của phần lớn lượt chạy: poll 2 phút/lần trên một nguồn cập nhật 10 phút/lần thì 4/5 lượt chỉ nhận
 * lại dữ liệu trùng. Gộp {@code written} với {@code skipped} là dạy người vận hành bỏ qua số 0 —
 * đúng lúc số 0 ấy có ngày sẽ là thật.
 *
 * <p>⛔ Hai bất biến ép ở hàm dựng, trùng khít hai ràng buộc CHECK của CSDL. Ép ở cả hai nơi là cố ý:
 * CSDL chặn mọi đường ghi kể cả đường chưa ai nghĩ tới, còn hàm dựng cho lỗi ở đúng dòng mã sai thay
 * vì ở giữa một câu SQL.
 */
public record SyncOutcome(
        Long apiSourceId,
        Instant startedAt,
        Instant finishedAt,
        Instant frameStart,
        SyncStatus status,
        SyncFailureKind failureKind,
        String failureDetail,
        int receivedCount,
        int writtenCount,
        int skippedCount,
        int unmappedCount,
        Long rawLogId) {

    private static final int DAI_TOI_DA_LY_DO = 1000;

    public SyncOutcome {
        Objects.requireNonNull(apiSourceId, "apiSourceId");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(status, "status");

        if (status == SyncStatus.FAILED && failureKind == null) {
            // §10.68-B: một dòng FAILED không nói được vì sao thì cho cùng một vân tay với mọi
            // nguyên nhân, và người trực không biết phải làm gì khác đi.
            throw new IllegalArgumentException("Lượt FAILED bắt buộc kèm failureKind — xem SyncFailureKind");
        }
        if (status == SyncStatus.SUCCESS && failureKind != null) {
            throw new IllegalArgumentException("Lượt SUCCESS không được mang failureKind — hai khẳng định trái nhau");
        }
        if (receivedCount < 0 || writtenCount < 0 || skippedCount < 0 || unmappedCount < 0) {
            throw new IllegalArgumentException("Bộ đếm không được âm");
        }
        if (failureDetail != null && failureDetail.length() > DAI_TOI_DA_LY_DO) {
            failureDetail = failureDetail.substring(0, DAI_TOI_DA_LY_DO);
        }
    }

    /** Lượt cố ý không gọi vì toàn bộ điểm đo đã đủ dữ liệu của khung hiện tại. */
    public static SyncOutcome boQuaVoiDuDuLieu(Long apiSourceId, Instant at, Instant frameStart) {
        return new SyncOutcome(
                apiSourceId, at, at, frameStart, SyncStatus.SKIPPED_UP_TO_DATE, null, null, 0, 0, 0, 0, null);
    }
}
