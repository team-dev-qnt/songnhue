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
// ⚠ `staleTime` dài có chủ đích: chính sách đổi vài tháng một lần, còn màn hình đặt mật khẩu thì mở
//   ra đóng vào liên tục. Một lượt gọi mỗi phiên là đủ — và HAI component dưới đây dùng CHUNG một khoá
//   đệm, nên hộp thoại hiện cả hai vẫn chỉ gọi một lần.
//
// ⛔ `retry: false` — endpoint này là PHỤ TRỢ. Không lấy được thì hướng dẫn biến mất, biểu mẫu vẫn dùng
//    được và backend vẫn từ chối mật khẩu yếu kèm lý do cụ thể. Thử lại ba lần chỉ làm màn hình đứng
//    lâu hơn cho một thứ không chặn ai.
//
// ⚠ Hook KHÔNG xuất khẩu: một tệp vừa xuất component vừa xuất hook làm hỏng fast-refresh (eslint cảnh báo).
function useChinhSachMatKhau() {
  return useQuery({
    queryKey: ['auth', 'password-policy'],
    queryFn: () => api.get<PasswordPolicyResponse>('/auth/password-policy'),
    staleTime: 30 * 60 * 1000,
    retry: false,
  });
}

export function HuongDanMatKhau() {
  const { data: chinhSach } = useChinhSachMatKhau();

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

/**
 * Hạn dùng của mật khẩu tạm — T73.8 (ASVS 2.3.1). Chỉ hiện ở màn hình **quản trị phát** mật khẩu tạm
 * (thêm tài khoản · đặt lại mật khẩu), để người phát dặn người nhận dùng trước khi hết hạn; quá hạn thì
 * đăng nhập nhận `AUTH-0010` và phải nhờ đặt lại.
 *
 * ⛔ Con số đọc từ `security.password.temp-ttl-hours` qua `/auth/password-policy` — ghi cứng "72 giờ"
 * là nói dối ngay lần đầu tham số đổi (§10.69). Chưa có số thì ⛔ nói gì (quy tắc 16).
 */
export function HanMatKhauTam() {
  const { data: chinhSach } = useChinhSachMatKhau();
  if (typeof chinhSach?.tempPasswordTtlHours !== 'number') return null;

  return (
    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
      Mật khẩu tạm hết hiệu lực sau {chinhSach.tempPasswordTtlHours} giờ nếu người dùng chưa đăng
      nhập để đổi — quá hạn thì phải đặt lại.
    </Typography.Text>
  );
}
