package com.songnhue.hr.application;

import java.util.UUID;

import com.songnhue.hr.domain.EmploymentStatus;

/**
 * Bộ lọc màn hình danh sách CBNV — CN-04.7.
 *
 * <p>⚠ Đây là lọc <b>nghiệp vụ</b>. Lọc <b>phạm vi đơn vị</b> (tầng 3) ⛔ không nằm ở đây và ⛔
 * không được nằm ở đây: nó do bộ lọc Hibernate áp cho mọi truy vấn, kể cả những truy vấn viết sau
 * này mà người viết quên mất phạm vi (quy tắc 5).
 */
public record EmployeeFilter(String tuKhoa, UUID donViPublicId, UUID chucVuPublicId, EmploymentStatus trangThai) {

    public static EmployeeFilter rong() {
        return new EmployeeFilter(null, null, null, null);
    }

    /**
     * Chuỗi {@code LIKE} thô, hoặc {@code null} khi ⛔ không lọc.
     *
     * <p>⛔ Rỗng phải thành {@code null} chứ ⛔ không thành {@code "%%"}: {@code "%%"} khớp mọi hàng
     * nhưng vẫn bắt PostgreSQL chạy hàm bỏ dấu trên từng dòng — một lượt quét toàn bảng vô ích ở
     * đúng màn hình mở nhiều nhất.
     *
     * <p>⭐ ⛔ KHÔNG bỏ dấu ở đây. Phép bỏ dấu do <b>SQL</b> làm, ở cả hai vế, bằng cùng một hàm
     * {@code sn_khong_dau} — bỏ dấu ở Java rồi so với một bản bỏ dấu ở SQL là hai nguồn sự thật cho
     * cùng một phép so, và chúng lệch đúng vào ngày ai đó sửa một bên (luật 14).
     */
    public String tuKhoaLike() {
        if (tuKhoa == null || tuKhoa.isBlank()) {
            return null;
        }
        return "%" + tuKhoa.trim() + "%";
    }
}
