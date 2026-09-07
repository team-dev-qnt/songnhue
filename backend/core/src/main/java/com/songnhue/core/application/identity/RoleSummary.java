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
 */
public record RoleSummary(String code, String name, String description, int permissionCount) {}
