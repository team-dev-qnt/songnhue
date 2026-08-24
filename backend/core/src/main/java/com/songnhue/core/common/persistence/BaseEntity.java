package com.songnhue.core.common.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Cột chuẩn cho mọi entity nghiệp vụ (conventions.md §1.2, §2.5).
 *
 * <p>Ba điểm quan trọng:
 *
 * <ul>
 *   <li><b>{@code publicId}</b> — API chỉ được nhận và trả UUID này, KHÔNG bao giờ lộ {@code id}
 *       chạy số. Lộ id tuần tự là mời người ta dò {@code /constructions/1}, {@code /2}, {@code /3}
 *       (IDOR — §4.2).
 *   <li><b>{@code version}</b> — khoá lạc quan. Hai người sửa cùng lúc thì người sau nhận 409 chứ
 *       không ghi đè lặng lẽ mất thay đổi của người trước (§4.3).
 *   <li><b>{@code deletedAt}</b> — xoá mềm. Bản ghi đã có lịch sử thao tác không được xoá thật,
 *       nếu không nhật ký kiểm toán trỏ vào khoảng không.
 * </ul>
 *
 * <p>Entity thuộc phạm vi đơn vị phải kế thừa {@link ScopedEntity} thay vì lớp này.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Định danh dùng ở API. DB tự sinh mặc định, nhưng gán sẵn ở đây để dùng được ngay sau khi tạo object. */
    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    /** Khác null nghĩa là đã xoá mềm. Truy vấn phải lọc {@code deleted_at IS NULL}. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public void setPublicId(UUID publicId) {
        this.publicId = publicId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Đánh dấu xoá mềm. Không có hàm xoá cứng ở tầng này — cố ý. */
    public void markDeleted(Instant when) {
        this.deletedAt = when;
    }

    public void restore() {
        this.deletedAt = null;
    }
}
