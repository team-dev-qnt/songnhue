package com.songnhue.content.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Thẻ gắn cho bài viết — CN-01.1 (trường "Tags").
 *
 * <p>Cố ý <b>không</b> kế thừa {@code BaseEntity}: thẻ không có vòng đời duyệt, không xoá mềm, không
 * cần khoá lạc quan. Nó gần với một từ khoá hơn là một bản ghi nghiệp vụ. Cho nó đủ bộ cột chuẩn chỉ
 * làm người đọc tưởng ở đây có một quy trình nào đó.
 *
 * <p>Cũng không gắn {@code @Audited}: nhật ký kiểm toán ghi thay đổi của <i>bài viết</i>, mà danh
 * sách thẻ của bài nằm trong đó rồi. Ghi thêm một dòng "vừa tạo thẻ 'thuỷ lợi'" chỉ làm loãng thứ
 * người ta thực sự tra.
 */
@Entity
@Table(name = "tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;

    protected Tag() {}

    public Tag(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
