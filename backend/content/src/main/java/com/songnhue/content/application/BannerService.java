package com.songnhue.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Banner;
import com.songnhue.content.infra.BannerRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.AttachmentUploadCommand;

/**
 * Banner/Carousel trang chủ — CN-01.5, T15.1.
 *
 * <p>⛔ <b>Không nhận SVG.</b> Banner là ảnh do người làm nội dung tải lên và hiển thị cho khách
 * vãng lai; SVG chỉ vào hệ thống qua màn hình cấu hình nhận diện (điểm nghiệp vụ 7).
 */
@Service
public class BannerService {

    private static final List<String> DINH_DANG_ANH = List.of("image/jpeg", "image/png", "image/webp");

    private final BannerRepository banners;
    private final AttachmentPort attachments;

    public BannerService(BannerRepository banners, AttachmentPort attachments) {
        this.banners = banners;
        this.attachments = attachments;
    }

    /** Toàn bộ banner cho màn hình quản trị, kể cả đang tắt và đã hết hạn. */
    @Transactional(readOnly = true)
    public List<Banner> listAll() {
        return banners.findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc();
    }

    /**
     * Banner đang thật sự hiển thị tại thời điểm {@code now} — dùng cho cổng công khai.
     *
     * <p>Lọc bằng {@link Banner#isVisibleAt(Instant)} chứ không bằng mệnh đề WHERE tương đương: luật
     * "đang hiển thị" chỉ nên có một bản, và bản đó phải kiểm được bằng test đơn vị.
     */
    @Transactional(readOnly = true)
    public List<Banner> listVisible(Instant now) {
        return listAll().stream().filter(b -> b.isVisibleAt(now)).toList();
    }

    @Transactional(readOnly = true)
    public Banner get(UUID publicId) {
        return banners.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /**
     * Tạo banner từ ảnh vừa tải lên.
     *
     * <p>Ảnh đi cùng lượt tạo chứ không tách thành hai bước: một dòng banner không có ảnh thì không
     * hiển thị được, mà cột ảnh lại {@code NOT NULL} — tách ra chỉ để lại những bản ghi dở dang.
     */
    @Transactional
    public Banner create(String title, String originalName, byte[] image) {
        AttachmentRef ref = attachments.upload(
                new AttachmentUploadCommand(Banner.OWNER_TYPE, null, "BANNER", originalName, image, DINH_DANG_ANH));
        return banners.save(new Banner(title, ref.publicId()));
    }

    @Transactional
    public Banner update(
            UUID publicId,
            String title,
            String description,
            String linkUrl,
            boolean openNewTab,
            boolean active,
            Instant startAt,
            Instant endAt) {

        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new BusinessRuleException(ErrorCode.CMS_2014);
        }
        Banner banner = get(publicId);
        banner.setTitle(title);
        banner.setDescription(description);
        banner.setLinkUrl(linkUrl);
        banner.setOpenNewTab(openNewTab);
        banner.setActive(active);
        banner.schedule(startAt, endAt);
        return banner;
    }

    /** Đổi ảnh của một banner. Ảnh cũ giữ lại — xem ghi chú ở {@code SiteConfigService}. */
    @Transactional
    public Banner replaceImage(UUID publicId, String originalName, byte[] image) {
        Banner banner = get(publicId);
        AttachmentRef ref = attachments.upload(new AttachmentUploadCommand(
                Banner.OWNER_TYPE, banner.getId(), "BANNER", originalName, image, DINH_DANG_ANH));
        banner.setImageAttachmentPublicId(ref.publicId());
        return banner;
    }

    /** Thứ tự kéo thả — cùng cách làm với menu, nhận trọn danh sách. */
    @Transactional
    public void reorder(List<UUID> publicIdsInOrder) {
        int order = 0;
        for (UUID publicId : publicIdsInOrder) {
            get(publicId).setSortOrder(order);
            order += 10;
        }
    }

    @Transactional
    public void delete(UUID publicId) {
        get(publicId).markDeleted(Instant.now());
    }

    /** Đường dẫn xem ảnh cho màn hình quản trị — presigned, hạn ngắn. */
    @Transactional(readOnly = true)
    public String imageUrl(UUID publicId) {
        return attachments.downloadUrl(get(publicId).getImageAttachmentPublicId());
    }
}
