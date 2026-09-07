package com.songnhue.app.scope;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.persistence.ScopeGuard;

/**
 * Đóng vai một service nghiệp vụ ở tầng application — T10.3.
 *
 * <p>Bộ lọc phạm vi được {@code ScopeFilterAspect} bật quanh {@code @Transactional}, nên bài kiểm
 * <b>bắt buộc</b> phải đi qua một bean có ranh giới transaction thật. Gọi thẳng {@code EntityManager}
 * từ lớp test là bỏ qua đúng cái cơ chế đang cần chứng minh — và bài kiểm sẽ xanh trong khi hệ thống
 * hỏng.
 */
public class ScopedRecordProbe {

    @PersistenceContext
    private EntityManager entityManager;

    private final ScopeGuard scopeGuard;

    public ScopedRecordProbe(ScopeGuard scopeGuard) {
        this.scopeGuard = scopeGuard;
    }

    /** Ghi bản ghi mới — chạy trong transaction nên cũng chịu bộ lọc như mọi thao tác khác. */
    @Transactional
    public ScopedRecord create(String title, Long orgUnitId) {
        ScopedRecord record = new ScopedRecord(title, orgUnitId);
        entityManager.persist(record);
        entityManager.flush();
        return record;
    }

    /** Danh sách — nơi bộ lọc phạm vi thể hiện rõ nhất: bản ghi ngoài đơn vị đơn giản là không có. */
    @Transactional(readOnly = true)
    public List<String> listTitles() {
        return entityManager
                .createQuery("SELECT r.title FROM ScopedRecord r ORDER BY r.title", String.class)
                .getResultList();
    }

    /** Tra theo {@code public_id} — đường mà {@link ScopeGuard} phân biệt 403 với 404. */
    @Transactional(readOnly = true)
    public ScopedRecord get(UUID publicId) {
        return scopeGuard.require(findByPublicId(publicId), ScopedRecord.class, publicId);
    }

    private Optional<ScopedRecord> findByPublicId(UUID publicId) {
        try {
            return Optional.of(entityManager
                    .createQuery("SELECT r FROM ScopedRecord r WHERE r.publicId = :publicId", ScopedRecord.class)
                    .setParameter("publicId", publicId)
                    .getSingleResult());
        } catch (NoResultException ignored) {
            return Optional.empty();
        }
    }
}
