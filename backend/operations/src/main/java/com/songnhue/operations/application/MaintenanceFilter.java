package com.songnhue.operations.application;

import java.time.LocalDate;
import java.util.UUID;

import com.songnhue.operations.domain.IncidentSeverity;
import com.songnhue.operations.domain.MaintenanceType;

/**
 * Bộ lọc timeline lịch sử sửa chữa — CN-02.2.
 *
 * <p>Mọi trường {@code null} = không lọc theo tiêu chí đó.
 *
 * <p>⚠ <b>Không có ô lọc "đơn vị của bản ghi"</b>, cùng lý do với {@code ConstructionFilter}: phạm
 * vi dữ liệu do tầng 3 áp tự động cho mọi truy vấn. Một tham số như vậy chỉ tạo ảo giác rằng người
 * gọi điều khiển được phạm vi — và nếu nó thật sự có tác dụng thì đó là một lỗ hổng. Ô lọc
 * {@link #performerOrgUnitPublicId} là chuyện khác hẳn: <i>ai làm</i> công việc, không phải
 * <i>ai được đọc</i> bản ghi.
 */
public record MaintenanceFilter(
        UUID constructionPublicId,
        MaintenanceType workType,
        IncidentSeverity severity,
        String status,
        UUID performerOrgUnitPublicId,
        LocalDate from,
        LocalDate to,
        String keyword) {

    /** Chuỗi tìm kiếm dạng {@code %tu khoa%}; {@code null} khi người dùng chưa gõ gì. */
    public String keywordLike() {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    }
}
