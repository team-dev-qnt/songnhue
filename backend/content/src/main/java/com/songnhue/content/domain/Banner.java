package com.songnhue.content.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Ảnh carousel trang chủ — CN-01.5.
 *
 * <p>Hiển thị hay không là <b>giá trị dẫn xuất</b> từ ba thứ: {@code active}, {@code startAt},
 * {@code endAt}. Không có cột "đang hiển thị" cho người dùng bật tay — có thì nó sẽ lệch với lịch,
 * và không ai biết cái nào đúng.
 */
@Entity
@Table(name = "banners")
@Audited(module = "cms", entityType = "Banner")
public class Banner extends BaseEntity {

    /** Khớp {@code attachments.owner_type} — ảnh banner thuộc về chính bản ghi banner. */
    public static final String OWNER_TYPE = "BANNER";

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "image_attachment_public_id", nullable = false)
    private UUID imageAttachmentPublicId;

    @Column(name = "link_url", length = 1000)
    private String linkUrl;

    @Column(name = "open_new_tab", nullable = false)
    private boolean openNewTab;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    protected Banner() {}

    public Banner(String title, UUID imageAttachmentPublicId) {
        this.title = title;
        this.imageAttachmentPublicId = imageAttachmentPublicId;
    }

    /**
     * Có nằm trong khung hiển thị tại thời điểm này không.
     *
     * <p>Biên được lấy <b>bao gồm điểm bắt đầu</b> và <b>loại trừ điểm kết thúc</b>: đặt banner từ
     * 01/09 đến 03/09 thì nó sống trọn ngày 02 và tắt đúng lúc sang ngày 03 nếu giờ đặt là 00:00 —
     * cùng quy ước với mọi khoảng thời gian khác trong hệ.
     */
    public boolean isVisibleAt(Instant now) {
        if (!active || getDeletedAt() != null) {
            return false;
        }
        return (startAt == null || !startAt.isAfter(now)) && (endAt == null || endAt.isAfter(now));
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getImageAttachmentPublicId() {
        return imageAttachmentPublicId;
    }

    public void setImageAttachmentPublicId(UUID imageAttachmentPublicId) {
        this.imageAttachmentPublicId = imageAttachmentPublicId;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public boolean isOpenNewTab() {
        return openNewTab;
    }

    public void setOpenNewTab(boolean openNewTab) {
        this.openNewTab = openNewTab;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    /** Đặt cả hai đầu cùng lúc — nửa khoảng đặt riêng lẻ là cách chắc chắn để có {@code end < start}. */
    public void schedule(Instant startAt, Instant endAt) {
        this.startAt = startAt;
        this.endAt = endAt;
    }
}
