package com.songnhue.core.application.audit;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.audit.AuditCollector;
import com.songnhue.core.domain.audit.AuditAction;
import com.songnhue.core.domain.audit.AuditEntry;
import com.songnhue.core.domain.audit.AuditLog;
import com.songnhue.core.infra.audit.AuditLogRepository;
import com.songnhue.core.infra.audit.AuditMaintenanceRepository;

/**
 * Tra cứu nhật ký kiểm toán và kiểm tra tính toàn vẹn (M5.8, CN-05.4).
 *
 * <p>Phần <b>ghi</b> chủ yếu là tự động qua {@code AuditEventListener}. Lớp này chỉ mở thêm đường
 * ghi tay cho những thao tác <i>không</i> tương ứng với một lần sửa entity — đăng nhập, kết xuất,
 * sao lưu, đổi phân quyền. Không có đường đó thì những việc quan trọng nhất lại là những việc không
 * để lại dấu vết.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final AuditMaintenanceRepository maintenance;
    private final AuditCollector collector;

    public AuditService(
            AuditLogRepository repository, AuditMaintenanceRepository maintenance, AuditCollector collector) {
        this.repository = repository;
        this.maintenance = maintenance;
        this.collector = collector;
    }

    /**
     * Ghi tay một sự kiện không gắn với thay đổi entity nào.
     *
     * <p>VD: {@code EXPORT} danh sách nhân viên — không dòng nào trong DB đổi, nhưng dữ liệu nhạy
     * cảm vừa rời khỏi hệ thống và đó đúng là thứ nhật ký kiểm toán sinh ra để ghi.
     */
    public void record(
            String module, String entityType, Long entityId, AuditAction action, String detail, Long orgUnitId) {
        collector.collect(new AuditEntry(module, entityType, entityId, null, action, null, detail, orgUnitId));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(
            Instant from,
            Instant to,
            String module,
            String entityType,
            Long entityId,
            Long actorUserId,
            Pageable pageable) {
        return repository.search(from, to, module, entityType, entityId, actorUserId, pageable);
    }

    /**
     * Kiểm tra chuỗi hash.
     *
     * <p>Kết quả rỗng = nguyên vẹn. Có bản ghi = <b>đã có người sửa hoặc xoá trực tiếp trong DB</b>,
     * vì không đường nào trong ứng dụng làm được việc đó. Đây là sự kiện phải báo lên ngay, không
     * phải một cảnh báo để đó.
     */
    @Transactional(readOnly = true)
    public ChainVerification verifyChain(Long fromSeq, Long toSeq) {
        AuditMaintenanceRepository.SeqRange range = maintenance.seqRange();
        List<ChainBreak> breaks = maintenance.verifyChain(fromSeq, toSeq);

        if (breaks.isEmpty()) {
            log.info("Kiểm tra chuỗi nhật ký: nguyên vẹn ({} bản ghi)", range.total());
        } else {
            log.error(
                    "⚠ Chuỗi nhật ký kiểm toán KHÔNG toàn vẹn — {} điểm gãy, điểm đầu tiên ở seq {}",
                    breaks.size(),
                    breaks.get(0).seq());
        }
        return new ChainVerification(breaks.isEmpty(), range.minSeq(), range.maxSeq(), range.total(), breaks);
    }

    /**
     * @param intact true = chuỗi nguyên vẹn
     * @param breaks các điểm gãy, rỗng khi nguyên vẹn
     */
    public record ChainVerification(
            boolean intact, long minSeq, long maxSeq, long totalRecords, List<ChainBreak> breaks) {}
}
