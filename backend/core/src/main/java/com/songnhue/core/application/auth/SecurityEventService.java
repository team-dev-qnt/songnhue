package com.songnhue.core.application.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.core.common.web.RequestContext;
import com.songnhue.core.domain.security.SecurityEvent;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.security.SecurityEventRepository;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Ghi nhật ký sự kiện bảo mật (conventions.md §4.5) — nguồn cho cảnh báo M5.16 và cho điều tra sự cố.
 *
 * <p><b>Mỗi sự kiện ghi trong transaction riêng.</b> Phần lớn sự kiện đáng ghi nhất lại xảy ra đúng
 * lúc nghiệp vụ <i>thất bại</i>: đăng nhập sai, bị từ chối quyền, phát hiện token bị đánh cắp. Những
 * luồng đó kết thúc bằng exception → transaction bên ngoài rollback. Nếu sự kiện nằm chung
 * transaction ấy thì nó biến mất cùng, và bảng {@code security_events} sẽ chỉ ghi lại được những
 * chuyện bình thường — đúng thứ không ai cần.
 *
 * <p><b>Ghi hỏng thì không được làm hỏng request.</b> Không ghi được nhật ký là chuyện phải báo động
 * (log ERROR để cảnh báo hạ tầng bắt được), nhưng chặn người dùng đăng nhập vì bảng nhật ký đầy đĩa
 * thì còn tệ hơn. Đây là ngoại lệ có cân nhắc của quy tắc "không nuốt exception".
 */
@Service
public class SecurityEventService {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventService.class);

    private final SecurityEventRepository repository;
    private final TransactionTemplate requiresNew;
    private final MeterRegistry meterRegistry;

    public SecurityEventService(
            SecurityEventRepository repository,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * @param username giữ dạng text để nhật ký còn đọc được cả khi tài khoản không tồn tại (đăng nhập
     *     sai tên) hoặc về sau bị xoá mềm
     * @param detail JSON tự do — ⛔ TUYỆT ĐỐI không đưa mật khẩu, token, secret TOTP vào đây
     */
    public void record(SecurityEventType type, String username, Long userId, ClientInfo client, String detail) {
        countForAlerting(type);
        try {
            requiresNew.executeWithoutResult(status -> {
                SecurityEvent event = new SecurityEvent(type, username, userId);
                event.setIpAddress(client == null ? null : client.ipAddress());
                event.setUserAgent(client == null ? null : client.userAgent());
                event.setDetail(detail);
                event.setTraceId(RequestContext.traceId());
                repository.save(event);
            });
        } catch (RuntimeException e) {
            log.error("Không ghi được sự kiện bảo mật {} — kiểm tra kết nối CSDL ngay", type, e);
        }
    }

    public void record(SecurityEventType type, String username, Long userId, ClientInfo client) {
        record(type, username, userId, client, null);
    }

    /**
     * Đẩy sự kiện thành chuỗi số cho Prometheus/Grafana (T7.10, trả nợ #18).
     *
     * <p><b>Đếm TRƯỚC khi ghi CSDL, và cố ý như vậy.</b> Kiểu sự cố đáng sợ nhất ở đây là CSDL không
     * ghi được: lúc đó bảng {@code security_events} câm lặng, mà đó lại đúng lúc cần thấy chuyện gì
     * đang xảy ra. Bộ đếm nằm trong bộ nhớ tiến trình nên vẫn tăng bình thường và Grafana vẫn thấy
     * dòng sự kiện — cùng với dòng ERROR ở dưới.
     *
     * <p>Nhãn chỉ có {@code type} và {@code severity}, <b>không có username hay IP</b>: mỗi tổ hợp
     * nhãn là một chuỗi thời gian riêng trong Prometheus, gắn nhãn theo người dùng là sinh ra vô hạn
     * chuỗi và làm sập chính hệ giám sát. Chi tiết từng sự kiện tra ở bảng CSDL.
     */
    private void countForAlerting(SecurityEventType type) {
        try {
            meterRegistry
                    .counter(
                            "songnhue.security.events",
                            "type",
                            type.name(),
                            "severity",
                            type.severity().name())
                    .increment();
        } catch (RuntimeException e) {
            log.debug("Không tăng được bộ đếm sự kiện bảo mật: {}", e.getMessage());
        }
    }
}
