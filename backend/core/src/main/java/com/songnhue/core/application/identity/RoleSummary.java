package com.songnhue.core.application.identity;

/**
 * Một dòng trong danh mục vai trò — màn hình phân quyền (T6.15).
 *
 * <p>Khai ở tầng application chứ không ở repository: đây là hình dạng dữ liệu mà <i>use-case</i> cần,
 * còn repository chỉ là nơi lấy nó về. Để record này nằm trong repository thì controller muốn trả nó
 * ra phải import {@code infra} — và luật ArchUnit {@code api_khong_duoc_goi_thang_repository} đỏ
 * ngay, đúng như đã xảy ra khi bộ luật T10.2 chạy lần đầu.
 *
 * @param permissionCount số quyền đang gắn — cột đếm ở màn hình danh mục, không phải danh sách quyền
 * @param isSystem vai trò hệ thống ⇒ ⛔ <b>không sửa được quyền</b> (T27.31). Trả ra tới tận giao diện
 *     là có chủ đích: bảo đảm này ép ở backend ({@code ADM-2014}), nhưng nếu FE ⛔ không biết thì
 *     người dùng chỉ phát hiện ra luật bằng cách <i>va vào nó</i> — sửa xong, bấm lưu, nhận 403 và
 *     mất việc vừa làm. Một ràng buộc chỉ ép ở một phía là một ràng buộc <b>ẩn</b>
 */
public record RoleSummary(String code, String name, String description, int permissionCount, boolean isSystem) {}
