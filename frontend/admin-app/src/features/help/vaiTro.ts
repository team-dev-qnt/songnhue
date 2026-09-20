/**
 * Tên hiển thị của 12 vai trò — **chỉ để đọc cho dễ**, ⛔ phải nguồn sự thật về phân quyền.
 *
 * <h2>⛔⛔ Ở đây KHÔNG có một mã quyền nào, và đó là điều quan trọng nhất của tệp này</h2>
 *
 * Ma trận vai-trò-↔-quyền là thứ **Công ty tự sửa** trên màn hình *Vai trò & phân quyền*
 * (CN-05.2, mở cho sửa từ T27.31). Chép ma trận ấy vào đây là dựng một bản sao **sai kể từ ngày
 * họ chỉnh ô đầu tiên**, và sai trong im lặng — đúng hình dạng lỗi mà `CLAUDE.md` đã ghi lại
 * nhiều lần. Bộ quyền dùng để lọc **luôn** đến từ một trong hai nguồn sống:
 *
 * - **Vai trò của tôi** → `user.permissions` trong token, tức thứ backend vừa cấp.
 * - **Một vai trò khác** → `GET /admin/users/roles/{ma}/permissions`, đòi `adm:role:view`.
 *
 * <h2>⚠ Cái có thể lệch ở đây, và vì sao chấp nhận được</h2>
 *
 * Nếu Công ty **đổi tên** một vai trò thì nhãn dưới đây thành tên cũ. Đó là một **nhãn**, ⛔ phải
 * một quyết định phân quyền: đọc nhầm tên không làm ai mất dữ liệu. Còn danh sách **mã** thì ⛔
 * được lệch, và `vaiTro.test.ts` đọc thẳng migration seed để canh đúng vế ấy.
 */
export const TEN_VAI_TRO: Readonly<Record<string, string>> = {
  SUPER_ADMIN: 'Quản trị tối cao',
  ADMIN: 'Quản trị hệ thống',
  ADMIN_HR: 'Quản trị nhân sự',
  CONTENT_EDITOR: 'Biên tập viên',
  CONTENT_MANAGER: 'Quản trị nội dung',
  CLERK: 'Cán bộ văn thư',
  TECHNICIAN: 'Cán bộ kỹ thuật',
  XN_MANAGER: 'Quản lý Xí nghiệp',
  XN_OPERATOR: 'Cán bộ vận hành Xí nghiệp',
  DUTY_OFFICER: 'Trực ban điều hành',
  EXECUTIVE: 'Ban giám đốc / Điều hành',
  VIEWER: 'Cán bộ nội bộ',
};

/** Tên đọc được của một mã vai trò; mã lạ thì trả về chính nó thay vì chuỗi rỗng (quy tắc 16). */
export function tenVaiTro(ma: string): string {
  return TEN_VAI_TRO[ma] ?? ma;
}
