package com.songnhue.hydro.domain;

import java.time.Instant;
import java.util.UUID;

import com.songnhue.core.common.exception.ValidationException;

/**
 * Bộ lọc của màn hình <i>Nhật ký đồng bộ</i> — T31.13.
 *
 * <h2>⭐ {@link #chiHong()} là bộ lọc thật sự được dùng hằng ngày</h2>
 *
 * <p>720 dòng mỗi ngày cho một nguồn, và <b>4/5 trong số đó là {@code SKIPPED_UP_TO_DATE}</b> —
 * trạng thái bình thường và mong muốn. Người trực mở màn hình này để tìm cái hỏng, nên phải có đúng
 * một cú bấm đưa họ tới đó. Cờ này ánh xạ thẳng vào chỉ mục riêng
 * {@code ix_sync_logs_hong (started_at DESC) WHERE status IN ('FAILED','PARTIAL')} đã dựng sẵn từ
 * {@code V202609041059}.
 *
 * <p>⚠ {@link #chiHong()} và {@link #trangThai()} <b>giao nhau</b>, không phải cái này thắng cái kia:
 * đặt cả hai với {@code trangThai = SUCCESS} cho ra <b>0 dòng</b>. Đó là câu trả lời đúng cho một
 * câu hỏi vô nghĩa, ⛔ và tốt hơn hẳn một luật "ai thắng ai" mà nửa năm sau không ai nhớ.
 *
 * <h2>⛔ Cố ý KHÔNG có tham số sắp xếp</h2>
 *
 * <p>Nhật ký chỉ có đúng một thứ tự đọc được: mới nhất trước. Mở một tham số {@code sort} ở đây là
 * dựng lại nguyên hình dạng A1 — một mặc định của giao diện nằm ngoài whitelist của
 * {@code PageUtils.parseSort} làm màn hình trả <b>422 ngay lượt tải đầu tiên</b>, và triệu chứng
 * ("bảng rỗng") trùng khít với trạng thái đúng nên không ai báo. Không có tham số thì không có lớp
 * lỗi ấy.
 *
 * @param nguonPublicId lọc theo nguồn; {@code null} = mọi nguồn
 * @param trangThai một trong bốn kết cục; {@code null} = mọi kết cục
 * @param loi một trong <b>năm</b> lý do hỏng ({@link SyncFailureKind}); {@code null} = mọi lý do
 * @param tu {@code started_at >= tu}, tính theo UTC
 * @param den {@code started_at &lt; den} — <b>nửa khoảng mở</b>: một khoảng đóng hai đầu làm bản ghi
 *     đúng mốc cuối bị đếm hai lần khi người dùng lật sang khoảng kế tiếp
 */
public record BoLocNhatKy(
        UUID nguonPublicId, SyncStatus trangThai, SyncFailureKind loi, Instant tu, Instant den, boolean chiHong) {

    public BoLocNhatKy {
        if (tu != null && den != null && !tu.isBefore(den)) {
            throw (ValidationException) new ValidationException().withDetail("tu", "TRUOC_MOC_KET_THUC", tu.toString());
        }
    }

    /** Bộ lọc rỗng — dùng khi màn hình vừa mở và người dùng chưa chọn gì. */
    public static BoLocNhatKy khongLoc() {
        return new BoLocNhatKy(null, null, null, null, null, false);
    }
}
