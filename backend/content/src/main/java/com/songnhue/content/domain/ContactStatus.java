package com.songnhue.content.domain;

/**
 * Trạng thái xử lý một liên hệ — CN-01.4.
 *
 * <p>⚠⚠ <b>Sửa 20/09/2026 (T68.41)</b> — câu cũ ở đây khai <i>"chỉ HAI giá trị đầu đang có mã ghi
 * vào; bốn giá trị sau thuộc phần CHƯA DỰNG của CN-01.4"</i>, và nó <b>hết đúng từ 06/09/2026</b>:
 * WS-36 đã dựng trọn quy trình <b>sáu trạng thái</b> qua Workflow engine
 * ({@code ContactController#chuyenTrangThai} → {@code WorkflowPort}). Một chú thích khai <i>"chưa
 * dựng"</i> cho thứ đã chạy đắt hơn hẳn một chú thích thiếu — nó dẫn lượt rà sau đi dựng lại.
 *
 * <p>{@link #MOI} khi người dân gửi, {@link #DA_DOC} khi cán bộ mở lần đầu, bốn giá trị còn lại do
 * các bước chuyển của Workflow engine ghi. Enum phải khai <b>đủ</b> bộ giá trị của ràng buộc
 * {@code CHECK}: thiếu một giá trị so với CSDL là chừa sẵn một lỗi ánh xạ cho lượt sau.
 *
 * <p>⛔ Đây <b>không</b> phải một máy trạng thái tự quản — mọi bước chuyển đi qua Workflow engine
 * (quy tắc 4). Đừng thêm một phương thức {@code chuyen()} vào lớp này.
 */
public enum ContactStatus {
    MOI,
    DA_DOC,
    DANG_XU_LY,
    DA_PHAN_HOI,
    DONG,
    LUU_TRU,
}
