import { Alert, Button, Checkbox, Form, Input } from 'antd';
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { ApiClientError } from '@/shared/apiClient';

import { AuthShell } from './AuthShell';

interface ChallengeState {
  challengeToken?: string;
}

interface TwoFactorForm {
  code: string;
  recoveryCode: boolean;
}

/**
 * Đăng nhập bước 2 — nhập mã từ ứng dụng xác thực, hoặc mã khôi phục.
 *
 * Vé `challengeToken` truyền qua state của router chứ **không** qua URL: đưa vào query
 * string là nó nằm lại trong lịch sử trình duyệt, log của nginx và trường `Referer` gửi
 * sang bên thứ ba. Vé này thay cho mật khẩu trong khoảng vài phút, đủ để chiếm phiên.
 *
 * Hệ quả phải chấp nhận: F5 giữa chừng là mất vé → quay lại bước nhập mật khẩu. Đúng
 * hành vi mong muốn, không phải lỗi.
 */
export function TwoFactorPage() {
  const { verifyTwoFactor } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const challengeToken = (location.state as ChallengeState | null)?.challengeToken;
  if (!challengeToken) {
    return <Navigate to="/dang-nhap" replace />;
  }

  const onFinish = async (values: TwoFactorForm) => {
    setError(null);
    setSubmitting(true);
    try {
      const result = await verifyTwoFactor(challengeToken, values.code.trim(), values.recoveryCode);
      navigate(result.mustChangePassword ? '/doi-mat-khau' : '/', { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiClientError ? caught.message : 'Xác thực không thành công');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      title="Xác thực hai bước"
      subtitle="Mở ứng dụng xác thực trên điện thoại và nhập mã 6 chữ số đang hiển thị."
    >
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

      <Form<TwoFactorForm>
        layout="vertical"
        initialValues={{ recoveryCode: false }}
        onFinish={(values) => void onFinish(values)}
        requiredMark={false}
      >
        <Form.Item name="code" label="Mã xác thực" rules={[{ required: true, message: 'Nhập mã' }]}>
          <Input
            size="large"
            autoFocus
            autoComplete="one-time-code"
            inputMode="numeric"
            placeholder="123456"
          />
        </Form.Item>

        <Form.Item name="recoveryCode" valuePropName="checked">
          <Checkbox>Tôi dùng mã khôi phục (khi mất điện thoại)</Checkbox>
        </Form.Item>

        <Button type="primary" htmlType="submit" block size="large" loading={submitting}>
          Xác nhận
        </Button>
      </Form>
    </AuthShell>
  );
}
