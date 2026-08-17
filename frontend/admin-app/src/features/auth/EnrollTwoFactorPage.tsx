import { Alert, Button, Form, Input, Space, Spin, Steps, Typography } from 'antd';
import { QRCodeSVG } from 'qrcode.react';
import { useEffect, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { type EnrollResponse } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';

import { AuthShell } from './AuthShell';

interface ChallengeState {
  challengeToken?: string;
}

/**
 * Đăng ký 2FA lần đầu — bắt buộc với Super Admin, Admin và Admin HR (chốt G12).
 *
 * <h3>Điểm không được làm sai: bí mật và mã khôi phục hiện đúng MỘT lần</h3>
 *
 * Backend không trả lại chúng lần thứ hai. Nếu người dùng đóng trang trước khi lưu mã
 * khôi phục, họ mất đường vào khi đổi điện thoại — và người duy nhất gỡ được là quản trị
 * viên khác. Vì thế màn hình bắt **tích xác nhận đã lưu** trước khi cho đi tiếp, chứ
 * không chỉ hiện một dòng chữ nhỏ.
 *
 * Đăng ký gọi ngay khi vào trang: người dùng đến đây là đã qua bước mật khẩu, và bắt bấm
 * thêm một nút "bắt đầu" chỉ thêm một chỗ để bỏ dở giữa chừng.
 */
export function EnrollTwoFactorPage() {
  const { confirmEnrollment } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [enrollment, setEnrollment] = useState<EnrollResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const challengeToken = (location.state as ChallengeState | null)?.challengeToken;

  useEffect(() => {
    if (!challengeToken) {
      return;
    }
    void (async () => {
      try {
        setEnrollment(await api.post<EnrollResponse>('/auth/2fa/enroll', { challengeToken }));
      } catch (caught) {
        setError(
          caught instanceof ApiClientError
            ? caught.message
            : 'Không khởi tạo được xác thực hai bước',
        );
      }
    })();
  }, [challengeToken]);

  if (!challengeToken) {
    return <Navigate to="/dang-nhap" replace />;
  }

  const onConfirm = async (values: { code: string }) => {
    setError(null);
    setSubmitting(true);
    try {
      const result = await confirmEnrollment(challengeToken, values.code.trim());
      navigate(result.mustChangePassword ? '/doi-mat-khau' : '/', { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiClientError ? caught.message : 'Mã xác nhận không đúng');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      title="Thiết lập xác thực hai bước"
      subtitle="Tài khoản quản trị bắt buộc dùng xác thực hai bước."
    >
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

      {!enrollment ? (
        <Spin />
      ) : (
        <Steps
          direction="vertical"
          size="small"
          current={saved ? 1 : 0}
          items={[
            {
              title: 'Quét mã và lưu mã khôi phục',
              description: (
                <Space direction="vertical" style={{ width: '100%' }}>
                  <div style={{ background: '#fff', padding: 8, width: 'fit-content' }}>
                    <QRCodeSVG value={enrollment.otpauthUri} size={168} />
                  </div>

                  <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    Không quét được thì nhập tay khóa bí mật:
                  </Typography.Paragraph>
                  <Typography.Text code copyable>
                    {enrollment.secret}
                  </Typography.Text>

                  <Alert
                    type="warning"
                    showIcon
                    message="Mã khôi phục — chỉ hiển thị một lần"
                    description={
                      <>
                        <Typography.Paragraph style={{ marginBottom: 8 }}>
                          In hoặc lưu vào nơi an toàn. Mất điện thoại thì đây là đường vào duy nhất.
                        </Typography.Paragraph>
                        <Typography.Text
                          code
                          copyable={{ text: enrollment.recoveryCodes.join('\n') }}
                        >
                          {enrollment.recoveryCodes.join('  ')}
                        </Typography.Text>
                      </>
                    }
                  />

                  <Button type="primary" onClick={() => setSaved(true)}>
                    Tôi đã lưu mã khôi phục
                  </Button>
                </Space>
              ),
            },
            {
              title: 'Xác nhận bằng mã đầu tiên',
              description: saved ? (
                <Form<{ code: string }>
                  layout="vertical"
                  onFinish={(values) => void onConfirm(values)}
                  requiredMark={false}
                >
                  <Form.Item name="code" rules={[{ required: true, message: 'Nhập mã 6 chữ số' }]}>
                    <Input size="large" inputMode="numeric" placeholder="123456" autoFocus />
                  </Form.Item>
                  <Button type="primary" htmlType="submit" block loading={submitting}>
                    Hoàn tất
                  </Button>
                </Form>
              ) : null,
            },
          ]}
        />
      )}
    </AuthShell>
  );
}
