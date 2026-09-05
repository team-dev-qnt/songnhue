package com.songnhue.core.common.util;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.songnhue.core.common.exception.ValidationException;

/**
 * Chuẩn hoá phân trang và <b>đối chiếu trường sắp xếp với whitelist</b> (conventions.md §4.4).
 *
 * <p>Whitelist không phải để cho đẹp. Tên trường sort đi thẳng vào câu ORDER BY của JPA; nhận bừa
 * tên trường từ client là mở đường cho hai chuyện: lộ cấu trúc bảng qua thông báo lỗi, và sắp xếp
 * theo cột chưa đánh index khiến truy vấn quét toàn bảng — một request đủ làm chậm cả hệ thống.
 *
 * <p>Vì vậy MỌI endpoint danh sách bắt buộc gọi qua đây với tập trường cho phép của riêng nó.
 */
public final class PageUtils {

    /** Chặn trên kích thước trang (§1.3): không cho client kéo cả bảng bằng {@code size=100000}. */
    public static final int MAX_SIZE = 100;

    public static final int DEFAULT_SIZE = 20;

    private PageUtils() {}

    /**
     * @param page số trang, đếm từ 1 theo quy ước API (§1.3); nhỏ hơn 1 thì về 1
     * @param size số phần tử, tự kẹp vào khoảng {@code [1, MAX_SIZE]}
     * @param sortExpression dạng {@code "createdAt,desc"}; null hoặc rỗng thì không sắp xếp
     * @param allowedSortFields tập trường được phép sắp xếp của endpoint đó
     * @throws ValidationException khi trường sort nằm ngoài whitelist
     */
    public static Pageable toPageable(
            Integer page, Integer size, String sortExpression, Collection<String> allowedSortFields) {

        int pageIndex = (page == null || page < 1) ? 0 : page - 1;
        int pageSize = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(pageIndex, pageSize, parseSort(sortExpression, allowedSortFields));
    }

    /** Tách chuỗi sort và kiểm tra whitelist. Hỗ trợ nhiều tiêu chí: {@code "a,asc;b,desc"}. */
    public static Sort parseSort(String sortExpression, Collection<String> allowedSortFields) {
        if (sortExpression == null || sortExpression.isBlank()) {
            return Sort.unsorted();
        }
        Set<String> allowed = Set.copyOf(allowedSortFields);
        List<Sort.Order> orders = new java.util.ArrayList<>();

        for (String part : sortExpression.split(";")) {
            if (part.isBlank()) {
                continue;
            }
            String[] tokens = part.split(",");
            String field = tokens[0].trim();

            if (!allowed.contains(field)) {
                // Cố ý KHÔNG liệt kê danh sách trường hợp lệ trong lỗi trả về:
                // đó chính là bản đồ cấu trúc bảng. Danh sách nằm ở tài liệu OpenAPI.
                throw (ValidationException)
                        new ValidationException().withDetail(field, "SORT_FIELD_NOT_ALLOWED", field);
            }
            boolean desc = tokens.length > 1 && "desc".equalsIgnoreCase(tokens[1].trim());
            orders.add(desc ? Sort.Order.desc(field) : Sort.Order.asc(field));
        }
        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }
}
