import { useQuery } from '@tanstack/react-query';
import { Typography } from 'antd';

import type { PasswordPolicyResponse } from './api-types';
import { api } from './apiClient';

/**
 * Danh sách yêu cầu mật khẩu, viết thành câu — dùng ở **mọi** màn hình đặt/đổi mật khẩu.
 *
 * <h2>Vì sao một component dùng chung thay vì hai đoạn chữ</h2>
 *
 * Có ba màn hình đặt mật khẩu (thêm tài khoản, đổi mật khẩu, bắt buộc đổi lần đầu). Ba đoạn chữ
 * là ba nơi phải nhớ sửa khi chính sách đổi — quy tắc 14, và là đúng lớp lỗi vừa gây ra sự cố
 * 01/09 ở một chỗ khác của cùng chức năng này.
 *
 * <p><b>Trước lượt này</b> màn hình đổi mật khẩu chỉ nói: *"Yêu cầu độ mạnh do quản trị hệ thống
 * đặt trong phần cấu hình; hệ thống sẽ báo cụ thể nếu mật khẩu chưa đạt."* Vế đầu đúng mà vô
 * dụng — người dùng không mở được màn hình cấu hình. Vế sau **không đúng**: hệ thống trả
 * `AUTH-0006` = *"Mật khẩu chưa đạt yêu cầu an toàn"*, và ba `rule` cụ thể đi kèm thì
 * `fieldErrors()` vứt hết (xem `loiTheoTruong.ts`). Nên người dùng đoán, thử, và thử lại.
 *
 * <p>Còn màn hình *Thêm tài khoản* thì chú thích ô mật khẩu chỉ nói *"Người dùng bắt buộc đổi ở
 * lần đăng nhập đầu tiên"* — đúng, và không một chữ nào về yêu cầu độ mạnh, dù đó chính là thứ
 * làm lượt bấm "Tạo" thất bại.
 *
 * <p>⚠ Không có prop nào. Lượt viết đầu có `tenDangNhap?: string` để quyết định có nói luật
 * "không chứa tên đăng nhập" hay không — và nơi gọi đầu tiên đã truyền chuỗi `"x"` cho có. Luật
 * ấy áp ở CẢ hai đường gọi (`PasswordPolicyService.validate` chỉ bỏ qua khi `username == null`,
 * mà cả hai nơi gọi đều truyền tên thật), nên một prop tuỳ chọn chỉ tạo ra cơ hội quên.
 */
export function HuongDanMatKhau() {
  // ⚠ `staleTime` dài có chủ đích: chính sách đổi vài tháng một lần, còn màn hình đặt mật khẩu
  //   thì mở ra đóng vào liên tục. Một lượt gọi mỗi phiên là đủ.
  //
  // ⛔ `retry: false` — endpoint này là PHỤ TRỢ. Không lấy được thì hướng dẫn biến mất, biểu mẫu
  //    vẫn dùng được và backend vẫn từ chối mật khẩu yếu kèm lý do cụ thể. Thử lại ba lần chỉ
  //    làm màn hình đứng lâu hơn cho một thứ không chặn ai.
  //
  // ⚠ Truy vấn nằm THẲNG trong component, không tách ra một hook xuất khẩu: nơi gọi duy nhất là
  //   đây, và một tệp vừa xuất component vừa xuất hook làm hỏng fast-refresh (eslint cảnh báo).
  const { data: chinhSach } = useQuery({
    queryKey: ['auth', 'password-policy'],
    queryFn: () => api.get<PasswordPolicyResponse>('/auth/password-policy'),
    staleTime: 30 * 60 * 1000,
    retry: false,
  });

  // ⛔ Chưa lấy được chính sách thì KHÔNG hiện gì. Đây là quy tắc 16 ở dạng chữ: một dòng
  //    "ít nhất … ký tự" với chỗ trống, hoặc với một con số mặc định bịa ra, tệ hơn hẳn việc
  //    không nói gì — người dùng sẽ tin con số ấy.
  if (!chinhSach) return null;

  const yeuCau = [`ít nhất ${chinhSach.minLength} ký tự`];
  if (chinhSach.requireLetterAndDigit) yeuCau.push('có cả chữ và số');
  yeuCau.push('không chứa tên đăng nhập');

  return (
    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
      Yêu cầu: {yeuCau.join(', ')}.
    </Typography.Text>
  );
}
