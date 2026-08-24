package com.songnhue.content.domain;

/**
 * Hai cây menu độc lập trong cùng một bảng.
 *
 * <p>Không phải danh mục do Công ty vận hành (quy tắc 16), nên đây là enum trong mã: thêm một vị trí
 * thứ ba là phải có chỗ trên giao diện cổng để hiển thị nó — tức là việc của lập trình, không phải
 * việc của người dùng.
 */
public enum MenuPosition {
    HEADER,
    FOOTER
}
