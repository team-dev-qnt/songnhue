import { Alert, Button, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { ApiClientError, api } from '@/shared/apiClient';

import { AuthShell } from './AuthShell';

interface ChangePasswordForm {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

/**
 * Đổi mật khẩu — dùng cho cả hai tình huống: bắt buộc lần đầu, và tự đổi sau này.
 *
 * <h3>Đổi xong là phải đăng nhập lại — cố ý</h3>
 *
 * Backend thu hồi **toàn bộ** token đang sống của tài khoản khi mật khẩu đổi (§4.1). Đó
 * chính là điều người ta trông đợi khi nghi bị lộ mật khẩu: đổi mật khẩu phải đá kẻ khác
 * ra khỏi hệ thống. Nên FE không cố giữ phiên hiện tại — dọn token và về trang đăng nhập.
 *
 * Yêu cầu độ mạnh nằm ở bảng `settings` (M5.15) nên FE **không** chép lại luật: chỉ kiểm
 * hai ô mới có khớp nhau không, còn lại để backend trả `AUTH-0006`. Chép luật xuống đây
 * là mỗi lần Công ty siết chính sách lại phải phát hành bản FE mới.
 */
export function ChangePasswordPage() {
  const { user, endSession } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const forced = user?.mustChangePassword === true;

  const onFinish = async (values: ChangePasswordForm) => {
    setError(null);
    setSubmitting(true);
    try {
      await api.post<void>('/auth/change-password', {
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      // ⚠⚠ Phải là `endSession()`, KHÔNG phải `clearTokens()`. Bản đầu gọi `clearTokens()`
      // — chỉ xoá token trong `apiClient`, còn `status` vẫn `authenticated` và
      // `user.mustChangePassword` vẫn `true`. Guard đọc đúng hai giá trị đó, nên lượt
      // `navigate('/dang-nhap')` ngay bên dưới bị `RequireAnonymous` đẩy **ngược về đây**.
      //
      // Người dùng thấy biểu mẫu hiện lại y nguyên → tưởng thất bại → bấm gửi lần nữa →
      // lần này backend trả **403 AUTH-0005**, vì đổi mật khẩu đã thu hồi phiên và xoá
      // cookie CSRF. Nhật ký máy chủ ghi rõ trình tự: `change-password → 204` rồi hai lượt
      // `change-password → 403`. Việc đã xong ngay từ lượt đầu; thứ hỏng chỉ là đường ra.
      //
      // ⚠⚠ Bổ sung 25/8 — lượt sửa trên chữa TRIỆU CHỨNG THỨ HAI. Mắt xích đầu tiên nằm
      // xa hơn về phía trước: `api.post` ở trên **ném ngay trên 204**, nên `endSession()`
      // chưa từng chạy. `unwrap` không kiểm mã trạng thái, mà axios đặt `data = ''` cho
      // thân rỗng → `''.success` là `undefined` → ném `SYS-0001`. Cùng lỗi ấy áp cho cả
      // 24 endpoint trả 204, không riêng màn hình này — nên bản vá và bài kiểm đặt ở
      // `shared/apiClient.ts`, chỗ cả 24 đường vào cùng đi qua (luật 12).
      //
      // 📌 Bài học giữ lại: sửa ở màn hình thì 23 màn hình còn lại vẫn hỏng trong im lặng.
      endSession();
      navigate('/dang-nhap', {
        replace: true,
        state: { notice: 'Đã đổi mật khẩu. Vui lòng đăng nhập lại.' },
      });
      // ⛔ Cố ý KHÔNG gọi `navigate(0)` để tải lại trang. Nó là thứ che mất lỗi trên: khi
      // hoạt động thì nó dọn hộ trạng thái, khi không thì không ai biết vì sao. `endSession()`
      // đã dọn tường minh, và một lượt điều hướng tất định thì đọc ra được là đúng hay sai.
    } catch (caught) {
      setError(caught instanceof ApiClientError ? caught.message : 'Không đổi được mật khẩu');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      title="Đổi mật khẩu"
      subtitle={
        forced
          ? 'Tài khoản đang dùng mật khẩu tạm — phải đổi trước khi sử dụng hệ thống.'
          : 'Sau khi đổi, mọi thiết bị đang đăng nhập sẽ bị đăng xuất.'
      }
    >
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

      <Form<ChangePasswordForm>
        layout="vertical"
        onFinish={(values) => void onFinish(values)}
        requiredMark={false}
      >
        <Form.Item
          name="currentPassword"
          label="Mật khẩu hiện tại"
          rules={[{ required: true, message: 'Nhập mật khẩu hiện tại' }]}
        >
          <Input.Password autoComplete="current-password" size="large" autoFocus />
        </Form.Item>

        <Form.Item
          name="newPassword"
          label="Mật khẩu mới"
          rules={[{ required: true, message: 'Nhập mật khẩu mới' }]}
        >
          <Input.Password autoComplete="new-password" size="large" />
        </Form.Item>

        <Form.Item
          name="confirmPassword"
          label="Nhập lại mật khẩu mới"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: 'Nhập lại mật khẩu mới' },
            ({ getFieldValue }) => ({
              validator: (_rule, value: string) =>
                !value || getFieldValue('newPassword') === value
                  ? Promise.resolve()
                  : Promise.reject(new Error('Hai mật khẩu không khớp')),
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" size="large" />
        </Form.Item>

        <Typography.Paragraph type="secondary">
          Yêu cầu độ mạnh do quản trị hệ thống đặt trong phần cấu hình; hệ thống sẽ báo cụ thể nếu
          mật khẩu chưa đạt.
        </Typography.Paragraph>

        <Button type="primary" htmlType="submit" block size="large" loading={submitting}>
          Đổi mật khẩu
        </Button>
      </Form>
    </AuthShell>
  );
}
