package com.songnhue.core.spi;

/**
 * Một hành động người đang đăng nhập được phép làm trên bản ghi ở trạng thái hiện tại.
 *
 * <p>Giao diện render nút từ danh sách này và <b>không tự suy ra từ trạng thái</b> (conventions.md
 * §3): luật nằm trong bảng {@code workflow_transitions}, mà giao diện thì không đọc DB — tự suy là
 * chắc chắn có ngày lệch, và lệch theo hướng nguy hiểm nhất là hiện một nút mà máy chủ sẽ từ chối.
 *
 * @param toState trạng thái sau khi bấm — để giao diện nói trước hệ quả cho người dùng
 */
public record AllowedAction(String action, String label, String toState) {}
