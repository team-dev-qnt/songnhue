package com.songnhue.core.application.identity;

/**
 * Một dòng trong danh mục quyền — nguồn dựng các ô đánh dấu của màn hình ma trận phân quyền (T27.31).
 *
 * <p>Khai ở tầng application cùng lý do với {@link RoleSummary}: controller trả nó ra mà record nằm ở
 * {@code infra} thì luật ArchUnit {@code api_khong_duoc_goi_thang_repository} đỏ ngay.
 *
 * <h2>⚠ Vì sao màn hình cần cả danh mục, ⛔ không chỉ cần quyền của vai trò đang chọn</h2>
 *
 * <p>Trước T27.31 chỉ có {@code GET /roles/{code}/permissions} — <b>quyền vai trò ĐANG CÓ</b>. Với
 * một màn hình chỉ xem thì đủ; với một màn hình <i>sửa</i> thì ⛔ không: muốn <b>thêm</b> một quyền,
 * người dùng phải nhìn thấy cả những quyền vai trò ấy <b>chưa có</b>. Một ô đánh dấu chỉ dựng được
 * từ hiệu của hai tập.
 *
 * @param module {@code cms}/{@code ops}/{@code hyd}/{@code hr}/{@code adm} — có ràng buộc CHECK ở
 *     CSDL, dùng để gom nhóm trên màn hình
 * @param description có thể {@code null} — cột {@code TEXT} ⛔ không NOT NULL
 */
public record PermissionSummary(String code, String module, String name, String description) {}
