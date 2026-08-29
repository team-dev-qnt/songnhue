package com.songnhue.content.domain;

/**
 * Trạng thái xử lý một liên hệ — CN-01.4.
 *
 * <p>⚠ Chỉ <b>hai</b> giá trị đầu đang có mã ghi vào: {@link #MOI} lúc người dân gửi,
 * {@link #DA_DOC} lúc cán bộ mở lần đầu. Bốn giá trị sau thuộc phần chưa dựng của CN-01.4 (quy
 * trình xử lý, phản hồi, lưu trữ) và có mặt ở đây vì chúng đã nằm trong ràng buộc {@code CHECK}
 * của bảng: để enum thiếu giá trị so với CSDL là chừa sẵn một lỗi ánh xạ cho lượt sau.
 *
 * <p>⛔ Đây <b>không</b> phải một máy trạng thái tự quản. Khi phần còn lại của CN-01.4 được dựng,
 * bước chuyển phải đi qua Workflow engine như mọi entity nghiệp vụ khác (quy tắc 4) — đừng thêm
 * một phương thức {@code chuyen()} vào lớp này.
 */
public enum ContactStatus {
    MOI,
    DA_DOC,
    DANG_XU_LY,
    DA_PHAN_HOI,
    DONG,
    LUU_TRU,
}
