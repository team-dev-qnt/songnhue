package com.songnhue.core.spi;

/**
 * Một hành động người đang đăng nhập được phép làm trên bản ghi ở trạng thái hiện tại.
 *
 * <p>Giao diện render nút từ danh sách này và <b>không tự suy ra từ trạng thái</b> (conventions.md
 * §3): luật nằm trong bảng {@code workflow_transitions}, mà giao diện thì không đọc DB — tự suy là
 * chắc chắn có ngày lệch, và lệch theo hướng nguy hiểm nhất là hiện một nút mà máy chủ sẽ từ chối.
 *
 * <p>⛔ Record này là <b>hợp đồng dây</b>. Mọi trường ở đây phải có nơi điền; đừng thêm trường
 * trình bày (màu nút, nút chính) — backend không biết gì về thẩm mỹ, và một trường không ai ghi là
 * một trường luôn rỗng ở phía đọc. Đã trả giá đúng chuyện này: kiểu {@code AllowedAction} phía
 * giao diện từng mang thêm {@code primary}/{@code danger}/{@code requiresReason} mà không nơi nào
 * điền, nên hộp thoại nhập lý do <b>không bao giờ mở</b>.
 *
 * @param toState trạng thái sau khi bấm — để giao diện nói trước hệ quả cho người dùng
 * @param requiresReason bước chuyển này bắt buộc kèm lý do; giao diện mở ô nhập, và
 *     {@code WorkflowEngine.execute} từ chối nếu thiếu. Cả hai đọc cùng một dòng
 *     {@code workflow_transitions.requires_reason}, nên không lệch nhau được.
 */
public record AllowedAction(String action, String label, String toState, boolean requiresReason) {}
