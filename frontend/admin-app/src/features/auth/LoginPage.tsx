import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, Button, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { ApiClientError } from '@/shared/apiClient';

import { AuthShell } from './AuthShell';

interface LoginForm {
  username: string;
  password: string;
}

interface RedirectState {
  from?: string;
}

/**
 * Đăng nhập bước 1.
 *
 * <h3>Ba điều cố ý ở màn hình này</h3>
 *
 * 1. **Lỗi hiện ngay trong thẻ, không phải toast góc màn hình** — vì thế `AUTH-0001` và
 *    `AUTH-0003` được xếp `handling: 'caller'` trong `error-map`. Người vừa gõ sai mật
 *    khẩu cần thấy lời báo ngay cạnh ô nhập.
 * 2. **Không nói tài khoản có tồn tại hay không.** Backend cố ý trả cùng một câu cho
 *    "sai tên" và "sai mật khẩu" (§4.1); FE hiện nguyên văn câu đó, không "cải thiện"
 *    thành "tài khoản không tồn tại" — đó là kênh dò tên tài khoản.
 * 3. **Bước tiếp theo do backend quyết** qua trường `stage`, FE không tự đoán theo vai trò.
 */
export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const redirectTo = (location.state as RedirectState | null)?.from ?? '/';

  const onFinish = async (values: LoginForm) => {
    setError(null);
    setSubmitting(true);
    try {
      const result = await login(values.username, values.password);

      switch (result.stage) {
        case 'TWO_FACTOR_REQUIRED':
          navigate('/xac-thuc-2-buoc', {
            state: { challengeToken: result.challengeToken, mode: 'verify' },
            replace: true,
          });
          break;
        case 'TWO_FACTOR_ENROLL_REQUIRED':
          navigate('/dang-ky-2fa', {
            state: { challengeToken: result.challengeToken },
            replace: true,
          });
          break;
        case 'AUTHENTICATED':
          navigate(result.mustChangePassword ? '/doi-mat-khau' : redirectTo, { replace: true });
          break;
      }
    } catch (caught) {
      setError(
        caught instanceof ApiClientError
          ? caught.message
          : 'Không đăng nhập được, vui lòng thử lại',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      title="Hệ thống Quản trị điều hành"
      subtitle="Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ"
    >
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

      <Form<LoginForm>
        layout="vertical"
        onFinish={(values) => void onFinish(values)}
        requiredMark={false}
      >
        <Form.Item
          name="username"
          label="Tên đăng nhập"
          rules={[{ required: true, message: 'Nhập tên đăng nhập' }]}
        >
          <Input prefix={<UserOutlined />} autoComplete="username" autoFocus size="large" />
        </Form.Item>

        <Form.Item
          name="password"
          label="Mật khẩu"
          rules={[{ required: true, message: 'Nhập mật khẩu' }]}
        >
          <Input.Password prefix={<LockOutlined />} autoComplete="current-password" size="large" />
        </Form.Item>

        <Button type="primary" htmlType="submit" block size="large" loading={submitting}>
          Đăng nhập
        </Button>
      </Form>

      {/*
        Chưa có chức năng tự đặt lại mật khẩu: backend Phase 0 không có endpoint nào cho
        việc đó, và làm nửa vời (gửi liên kết đặt lại qua email) là mở thêm một đường vào
        hệ thống mà chưa ai rà. Đường chính thức lúc này là quản trị viên cấp lại mật khẩu
        tạm — ghi trong nợ #35 của phase0-tracking.md.
      */}
      <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0 }}>
        Quên mật khẩu? Liên hệ quản trị hệ thống để được cấp lại mật khẩu tạm.
      </Typography.Paragraph>
    </AuthShell>
  );
}
