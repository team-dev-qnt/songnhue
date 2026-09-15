package com.songnhue.core.spi;

/**
 * Danh mục bí mật tích hợp sửa được trên giao diện — T61.44 (architecture-review.md §12.1).
 *
 * <p>⛔⛔ Một bí mật chỉ vào danh mục này khi đủ BA điều kiện, cả ba đều đã đo cho từng mục:
 *
 * <ol>
 *   <li><b>Ứng dụng Spring là người đọc DUY NHẤT</b> — ⛔ nginx, Prometheus, Alertmanager, public-web. Bí mật mà tiến
 *       trình khác cũng đọc (VD {@code REVALIDATE_SECRET}) ⛔ vào đây được: hai nguồn sẽ lệch nhau.
 *   <li><b>⛔ cần trước khi có CSDL</b> — khoá AES, khoá JWT, mật khẩu CSDL ⛔ bao giờ vào đây.
 *   <li><b>Lộ ra thì thiệt hại ⛔ vượt khỏi ứng dụng</b> — SMTP ⛔ vào đây (đổi host = rút thư mang dữ liệu cá nhân,
 *       mở kết nối tới host:port tuỳ ý). Bí mật reCAPTCHA lộ ra chỉ cho phép vượt captcha của biểu mẫu liên hệ, và
 *       hạn mức tần suất vẫn chạy.
 * </ol>
 *
 * <p>⛔ Thêm một mục = thêm một NGƯỜI ĐỌC trong mã, tức phải deploy — nên danh mục ở mã chứ ⛔ ở bảng (quy tắc 15).
 */
public enum LoaiBiMat {
    RECAPTCHA_SECRET_KEY(
            "app.recaptcha.secret",
            "Khoá bí mật reCAPTCHA",
            "Google reCAPTCHA v3 — kiểm biểu mẫu liên hệ/góp ý của cổng công khai (G13). Khoá CÔNG KHAI (site key) ⛔ "
                    + "đặt ở đây.");

    private final String thuocTinh;
    private final String ten;
    private final String moTa;

    LoaiBiMat(String thuocTinh, String ten, String moTa) {
        this.thuocTinh = thuocTinh;
        this.ten = ten;
        this.moTa = moTa;
    }

    /** Thuộc tính Spring của giá trị MỒI từ {@code .env} — dùng khi giao diện chưa đặt. */
    public String thuocTinh() {
        return thuocTinh;
    }

    public String ten() {
        return ten;
    }

    public String moTa() {
        return moTa;
    }
}
