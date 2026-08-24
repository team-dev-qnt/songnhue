package com.songnhue.operations.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.AuditEntryView;
import com.songnhue.core.spi.AuditQueryPort;
import com.songnhue.operations.domain.Construction;

/**
 * Nhật ký thay đổi hồ sơ công trình — CN-02.7 / T17.8.
 *
 * <h2>⛔ Không có bảng lịch sử thứ hai</h2>
 *
 * Đây là <b>API đọc {@code audit_logs}</b>, lọc theo đúng một bản ghi. Bộ ghi nhật ký của Core đã
 * bắt mọi thao tác tạo/sửa/xoá ở tầng Hibernate với đủ giá trị cũ/mới, và chuỗi băm chứng minh không
 * ai sửa được nó về sau. Một bảng {@code construction_history} viết tay chỉ tạo ra nguồn sự thật thứ
 * hai — và nó sẽ là nguồn thiếu dòng, vì phụ thuộc vào việc có người nhớ gọi.
 *
 * <p>⭐ Nhờ thông số kỹ thuật là <b>bảng phụ</b> của cùng entity ({@code @SecondaryTable}) chứ không
 * phải entity riêng, nhật ký này bao gồm cả những lần sửa công suất máy bơm hay khẩu độ khoang cống
 * — thứ đáng theo dõi nhất trên một hồ sơ công trình. Tách entity thì chúng sẽ mang loại đối tượng
 * khác và rơi ra ngoài đúng màn hình sinh ra để xem chúng.
 */
@Service
public class ConstructionChangeLogService {

    /** Phải khớp {@code @Audited} trên {@code Construction} — sai một ký tự là nhật ký rỗng, không lỗi. */
    static final String MODULE = "ops";

    static final String ENTITY_TYPE = "Công trình";

    private static final int MAC_DINH = 50;

    private final ConstructionService constructions;
    private final AuditQueryPort audit;

    public ConstructionChangeLogService(ConstructionService constructions, AuditQueryPort audit) {
        this.constructions = constructions;
        this.audit = audit;
    }

    /**
     * Lịch sử của một hồ sơ, mới nhất trước.
     *
     * <p>{@code audit_logs} phân mảnh theo tháng, nên phải có mốc dưới — không thì PostgreSQL quét cả
     * 60 partition của 5 năm lưu trữ. Mốc lấy từ chính bản ghi: nó không thể có nhật ký trước ngày
     * nó ra đời.
     *
     * <p>⚠⚠ <b>Nhưng KHÔNG được lấy đúng {@code createdAt} làm mốc dưới</b> — bài kiểm qua HTTP bắt
     * đúng lỗi này ở lượt chạy đầu: nhật ký trả về <b>rỗng</b> cho một hồ sơ vừa tạo xong.
     *
     * <p>Nguyên nhân: {@code audit_logs.occurred_at} mặc định là {@code now()} của PostgreSQL, mà
     * {@code now()} trả <b>thời điểm bắt đầu giao dịch</b>, còn {@code createdAt} do Spring gán lúc
     * flush — tức là <i>sau</i> đó vài mili giây. Lấy {@code createdAt} làm cận dưới thì dòng nhật ký
     * của chính lượt tạo nằm ngay <i>dưới</i> mốc và bị loại. Triệu chứng: tab "Nhật ký thay đổi"
     * trống trơn, không lỗi nào, và người ta sẽ đi tìm nguyên nhân ở bộ ghi nhật ký chứ không ở đây.
     *
     * <p>Cách chữa: lùi mốc về <b>đầu tháng</b> chứa {@code createdAt}. Vì partition chia theo tháng
     * nên việc này <i>không</i> làm quét thêm partition nào — vẫn đúng chừng ấy bảng — mà xoá hẳn cả
     * lớp lỗi do lệch đồng hồ giữa tiến trình ứng dụng và CSDL.
     */
    @Transactional(readOnly = true)
    public List<AuditEntryView> historyOf(UUID publicId, Integer limit) {
        Construction ct = constructions.get(publicId);
        Instant tu = dauThangCua(ct.getCreatedAt());
        // Cận trên cũng nới một phút: cùng lý do ngược lại — đồng hồ CSDL có thể chạy trước JVM.
        Instant den = Instant.now().plus(1, ChronoUnit.MINUTES);
        return audit.historyOf(MODULE, ENTITY_TYPE, ct.getId(), tu, den, limit == null ? MAC_DINH : limit);
    }

    private static Instant dauThangCua(Instant moc) {
        if (moc == null) {
            return Instant.EPOCH;
        }
        return moc.atZone(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant();
    }
}
