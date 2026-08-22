package com.songnhue.content.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.content.domain.Banner;

/** Truy vấn banner — CN-01.5. */
public interface BannerRepository extends JpaRepository<Banner, Long> {

    Optional<Banner> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /**
     * Toàn bộ banner chưa xoá, kể cả đang tắt và đã hết hạn — dùng cho màn hình quản trị.
     *
     * <p>Việc lọc theo lịch cố ý <b>không</b> nằm ở đây: {@code Banner.isVisibleAt(now)} là một chỗ
     * duy nhất, kiểm được bằng test đơn vị, và dùng chung cho cả cổng công khai. Viết thêm một mệnh
     * đề WHERE tương đương ở tầng SQL là dựng bản sao thứ hai của cùng một luật, rồi hai bản đó lệch
     * nhau lúc ai đó sửa một bên.
     */
    List<Banner> findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc();

    long countByImageAttachmentPublicId(UUID imageAttachmentPublicId);
}
