package com.songnhue.content.domain;

/**
 * Ba cây menu độc lập trong cùng một bảng.
 *
 * <p>Không phải danh mục do Công ty vận hành (quy tắc 16), nên đây là enum trong mã: thêm một vị trí
 * là phải có chỗ trên giao diện cổng để hiển thị nó — tức là việc của lập trình, không phải việc của
 * người dùng.
 *
 * <p>⭐ {@link #LIEN_KET} thêm ngày 28/08/2026 và thoả đúng điều kiện ấy: chỗ hiển thị (dải "Liên kết
 * Cổng TTĐT" ở cuối trang chủ) đã tồn tại từ WS-16, chỉ có điều nó đọc một hằng số <b>viết cứng
 * trong mã giao diện</b>. CR-21 yêu cầu Công ty rà soát lại tên và địa chỉ bốn cơ quan ấy — rà xong
 * mà không sửa được thì yêu cầu ấy không đóng được (xem {@code V202608281036}).
 */
public enum MenuPosition {
    HEADER,
    FOOTER,
    /** Liên kết sang cổng TTĐT của cơ quan quản lý cấp trên — dải cuối trang chủ (CR-21). */
    LIEN_KET
}
