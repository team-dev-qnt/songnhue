package com.songnhue.core.common.audit;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.core.annotation.AnnotationUtils;

import com.songnhue.core.common.persistence.BaseEntity;
import com.songnhue.core.common.persistence.ScopedEntity;
import com.songnhue.core.common.persistence.WorkflowAware;
import com.songnhue.core.domain.audit.AuditAction;
import com.songnhue.core.domain.audit.AuditEntry;

/**
 * Ghi nhật ký kiểm toán <b>tự động</b> ở tầng Hibernate — T6.12.
 *
 * <p><b>Vì sao bắt ở đây chứ không gọi tay trong từng service.</b> Gọi tay là dựa vào việc lập trình
 * viên nhớ — cùng loại rủi ro mà quy tắc 5 của dự án đã loại bỏ với bộ lọc phạm vi. Chỗ hay quên
 * nhất không phải hàm CRUD chính mà là những đường ít ai để ý: sửa nhanh một trường trên màn hình
 * quản trị, nhánh xử lý ngoại lệ, thao tác hàng loạt. Bắt ở tầng Hibernate thì <b>không có đường
 * nào</b> đổi dữ liệu mà không để lại dấu vết.
 *
 * <p>Chỉ entity mang {@link Audited} mới được ghi. Entity hạ tầng (hàng đợi job, phiên đăng nhập)
 * thay đổi liên tục do máy; ghi vào đây chỉ làm loãng thứ người ta cần tìm.
 *
 * <p><b>Xoá mềm được ghi là {@code UPDATE}, không phải {@code DELETE}.</b> Về mặt kỹ thuật đó đúng
 * là một lệnh UPDATE đặt {@code deleted_at}. Ghi thành DELETE sẽ khiến người tra cứu tin rằng dòng
 * đã biến mất khỏi bảng, trong khi nó vẫn còn và vẫn khôi phục được.
 *
 * <p>⚠ <b>Không phải bean Spring, và cố ý nhận collaborator qua {@link Supplier}.</b> Lớp này được
 * cắm vào Hibernate bằng {@code Integrator} <i>trong lúc dựng SessionFactory</i> — sớm hơn lúc các
 * bean Spring sẵn sàng, nên không thể tiêm thẳng. Vì sao phải làm vòng như vậy: xem
 * {@code AuditHibernateConfig}.
 *
 * <p>⚠ <b>Giới hạn đã biết:</b> câu lệnh cập nhật hàng loạt ({@code @Modifying} với JPQL/native)
 * <b>không</b> đi qua bộ lắng nghe này — Hibernate không nạp entity nào nên không có sự kiện nào để
 * bắt. Thao tác nghiệp vụ cần dấu vết phải đi qua entity, hoặc tự gọi
 * {@code AuditService.record(...)}.
 */
public class AuditEventListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private final Supplier<AuditCollector> collectorSupplier;
    private final Supplier<AuditValueSerializer> serializerSupplier;

    public AuditEventListener(
            Supplier<AuditCollector> collectorSupplier, Supplier<AuditValueSerializer> serializerSupplier) {
        this.collectorSupplier = collectorSupplier;
        this.serializerSupplier = serializerSupplier;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        Audited audited = auditedOf(event.getEntity());
        if (audited == null) {
            return;
        }
        collect(
                audited,
                event.getEntity(),
                AuditAction.CREATE,
                null,
                serializerSupplier
                        .get()
                        .toJson(event.getPersister().getPropertyNames(), event.getState(), excluded(audited)));
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Audited audited = auditedOf(event.getEntity());
        if (audited == null) {
            return;
        }
        Set<String> excluded = excluded(audited);
        String[] names = event.getPersister().getPropertyNames();

        // Chỉ ghi những trường THỰC SỰ đổi. Ghi cả bản ghi thì mỗi lần sửa một ô sinh ra hai bản
        // JSON gần giống hệt nhau, và người tra cứu phải tự dò xem khác nhau chỗ nào.
        int[] dirty = event.getDirtyProperties();
        if (dirty == null || dirty.length == 0) {
            return;
        }
        String[] changedNames = new String[dirty.length];
        Object[] oldValues = new Object[dirty.length];
        Object[] newValues = new Object[dirty.length];
        for (int i = 0; i < dirty.length; i++) {
            changedNames[i] = names[dirty[i]];
            // oldState rỗng khi entity vào session ở trạng thái detached (merge) — lúc đó chỉ ghi
            // được giá trị mới, và nói rõ như vậy còn hơn bịa ra một giá trị cũ.
            oldValues[i] = event.getOldState() == null ? null : event.getOldState()[dirty[i]];
            newValues[i] = event.getState()[dirty[i]];
        }

        collect(
                audited,
                event.getEntity(),
                AuditAction.UPDATE,
                event.getOldState() == null ? null : serializerSupplier.get().toJson(changedNames, oldValues, excluded),
                serializerSupplier.get().toJson(changedNames, newValues, excluded));
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        Audited audited = auditedOf(event.getEntity());
        if (audited == null) {
            return;
        }
        collect(
                audited,
                event.getEntity(),
                AuditAction.DELETE,
                serializerSupplier
                        .get()
                        .toJson(event.getPersister().getPropertyNames(), event.getDeletedState(), excluded(audited)),
                null);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        // false = chạy trong lúc flush, tức là VẪN trong giao dịch nghiệp vụ. Đó là điều kiện để
        // nhật ký và dữ liệu cùng rollback khi giao dịch hỏng (xem AuditCollector).
        return false;
    }

    // -------------------------------------------------------------------------

    private void collect(Audited audited, Object entity, AuditAction action, String oldValue, String newValue) {
        Long entityId = null;
        UUID publicId = null;
        Long orgUnitId = null;
        if (entity instanceof BaseEntity base) {
            entityId = base.getId();
            publicId = base.getPublicId();
        } else if (entity instanceof WorkflowAware quyTrinh) {
            // ⭐⭐ Nhánh này bịt một lỗ đo được ngày 02/09/2026: một entity KHÔNG kế thừa
            //    `BaseEntity` vẫn ghi được nhật ký, nhưng ghi với `entity_id = NULL` — tức nhật ký
            //    có dòng mà ⛔ không truy ngược được về bản ghi nào. Câu hỏi "ai loại bỏ số đo #123
            //    và vì sao" khi ấy KHÔNG có chỗ nào trả lời, dù mọi cơ chế trông như đã chạy.
            //
            //    ⚠ Chính javadoc của `WorkflowAware.entityId()` đã nói mục đích: *"Id dùng cho NHẬT
            //    KÝ và thông báo"* — và tới hôm nay không ai đọc nó cho nhật ký. Một nửa cặp đọc–ghi
            //    ở dạng khó thấy nhất: vế khai thì có, vế dùng thì không (luật 15/27).
            //
            //    ⛔ `else if`, ⛔ không phải nhánh song song: entity kế thừa `BaseEntity` giữ NGUYÊN
            //    hành vi cũ (chúng có cả `public_id`), nên thay đổi này thuần bổ sung.
            //    Trường hợp đầu tiên đi qua đây: `HydroReading` — bảng phân mảnh, khoá ghép
            //    `(id, measured_at)`, ⛔ không có `public_id` nên ⛔ không kế thừa `BaseEntity` được.
            entityId = quyTrinh.entityId();
            orgUnitId = quyTrinh.orgUnitId();
        }
        if (entity instanceof ScopedEntity scoped) {
            orgUnitId = scoped.getOrgUnitId();
        }

        String entityType = audited.entityType().isBlank() ? entity.getClass().getSimpleName() : audited.entityType();

        collectorSupplier
                .get()
                .collect(new AuditEntry(
                        audited.module(), entityType, entityId, publicId, action, oldValue, newValue, orgUnitId));
    }

    private static Audited auditedOf(Object entity) {
        return entity == null ? null : AnnotationUtils.findAnnotation(entity.getClass(), Audited.class);
    }

    private static Set<String> excluded(Audited audited) {
        return Set.copyOf(Arrays.asList(audited.excludeFields()));
    }
}
