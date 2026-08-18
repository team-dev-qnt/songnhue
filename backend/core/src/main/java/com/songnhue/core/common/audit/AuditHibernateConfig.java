package com.songnhue.core.common.audit;

import java.util.List;
import java.util.Map;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Cắm {@link AuditEventListener} vào Hibernate — <b>đúng lúc dựng SessionFactory</b>.
 *
 * <p><b>Vì sao không đăng ký sau khi ứng dụng đã lên.</b> Cách trực giác hơn là tiêm
 * {@code EntityManagerFactory} vào một bean rồi trong {@code @PostConstruct} gọi
 * {@code EventListenerRegistry.appendListeners(...)}. Cách đó <b>không hoạt động</b> ở Hibernate 6:
 * SessionFactory tính sẵn các nhóm listener vào {@code FastSessionServices} ngay khi được dựng, nên
 * listener thêm vào sau đó không bao giờ được gọi.
 *
 * <p>Điều khiến nó nguy hiểm là <b>không có lỗi nào báo ra</b>: đăng ký "thành công", log in ra dòng
 * xác nhận, ứng dụng chạy bình thường — chỉ là nhật ký kiểm toán trống rỗng. Phát hiện được ở WS-6
 * nhờ chạy thử thật (đăng nhập sai làm {@code users.failed_login_count} tăng nhưng
 * {@code audit_logs} không có dòng nào).
 *
 * <p><b>Cái giá phải trả:</b> {@code Integrator} chạy sớm hơn các bean Spring, nên listener không
 * nhận được collaborator qua constructor mà nhận {@code Supplier} — {@link ObjectProvider} chỉ được
 * gọi lúc có sự kiện đầu tiên, khi context đã sẵn sàng.
 */
@Configuration
public class AuditHibernateConfig implements HibernatePropertiesCustomizer {

    private static final Logger log = LoggerFactory.getLogger(AuditHibernateConfig.class);

    /** Khoá cấu hình của Hibernate JPA bootstrap để cắm danh sách {@code Integrator}. */
    private static final String INTEGRATOR_PROVIDER = "hibernate.integrator_provider";

    private final ObjectProvider<AuditCollector> collector;
    private final ObjectProvider<AuditValueSerializer> serializer;

    public AuditHibernateConfig(
            ObjectProvider<AuditCollector> collector, ObjectProvider<AuditValueSerializer> serializer) {
        this.collector = collector;
        this.serializer = serializer;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        AuditEventListener listener = new AuditEventListener(collector::getObject, serializer::getObject);
        hibernateProperties.put(INTEGRATOR_PROVIDER, (IntegratorProvider) () -> List.of(new AuditIntegrator(listener)));
    }

    /** Đăng ký listener vào registry ngay trong lúc Hibernate khởi tạo. */
    private record AuditIntegrator(AuditEventListener listener) implements Integrator {

        @Override
        public void integrate(Metadata metadata, BootstrapContext bootstrapContext, SessionFactoryImplementor factory) {
            EventListenerRegistry registry = factory.getServiceRegistry().requireService(EventListenerRegistry.class);
            // Chỉ ba sự kiện này. KHÔNG thêm POST_COMMIT_* — Hibernate sẽ gọi listener hai lần cho
            // cùng một thao tác, và nhật ký có hai dòng giống hệt nhau cho một lần sửa.
            registry.appendListeners(EventType.POST_INSERT, listener);
            registry.appendListeners(EventType.POST_UPDATE, listener);
            registry.appendListeners(EventType.POST_DELETE, listener);
            log.info("Đã cắm bộ ghi nhật ký kiểm toán vào Hibernate — entity mang @Audited được ghi tự động");
        }

        @Override
        public void disintegrate(
                SessionFactoryImplementor factory, org.hibernate.service.spi.SessionFactoryServiceRegistry registry) {
            // Không cần dọn: registry chết cùng SessionFactory.
        }
    }
}
