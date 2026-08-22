package com.songnhue.content.domain;

/**
 * Loại đích của một mục menu — CN-01.5.
 *
 * <p>Mỗi giá trị ứng với đúng một cột đích ở bảng {@code menu_items}, và ràng buộc
 * {@code ck_menu_items_target} bắt cặp chúng lại ở tầng CSDL. Giữ được cặp đó thì nơi hiển thị không
 * bao giờ phải đoán xem mục này dẫn đi đâu.
 */
public enum MenuLinkType {

    /** Trang danh sách của một danh mục. Đổi slug danh mục thì menu tự đúng theo. */
    CATEGORY,

    /** Một bài cụ thể — dùng cho các trang tĩnh (Giới thiệu, Liên hệ…). */
    ARTICLE,

    /** Đường dẫn tự nhập, trong hoặc ngoài cổng. */
    URL,

    /**
     * Hệ thống văn bản điều hành (CN-01.7).
     *
     * <p>Tách khỏi {@link #URL} vì hai thứ khác nhau về tương lai chứ không khác nhau về hiện tại:
     * khi G5 có câu trả lời, những mục loại này sẽ đi qua bước đăng nhập tự động bằng mã số, còn
     * {@code URL} thì không. Gộp làm một bây giờ là sau này phải đoán mục nào cần xử lý riêng.
     */
    EXTERNAL_DOC,

    /** Chỉ để mở menu con, bấm vào không đi đâu — VD "Giới thiệu ▾". */
    NONE
}
