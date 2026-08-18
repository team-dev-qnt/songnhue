import { useMutation } from '@tanstack/react-query';
import { Alert, App, Button, Card, Form, Input, Select, Typography } from 'antd';

import { type BroadcastRequest, type NotificationSeverity } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';

const SEVERITY_OPTIONS: { value: NotificationSeverity; label: string }[] = [
  { value: 'INFO', label: 'Thông tin' },
  { value: 'WARNING', label: 'Cảnh báo' },
  { value: 'DANGER', label: 'Khẩn' },
];

/**
 * Thông báo hệ thống — M5.13.
 *
 * Gửi đi là **không thu hồi được**: thông báo đã nằm trong hộp thư của mọi người và email
 * đã rời khỏi máy chủ. Vì thế có bước xác nhận trước khi gửi, và mặc định là gửi toàn bộ
 * tài khoản đang hoạt động — chọn nhầm phạm vi ở đây tốn của cả công ty một lần đọc.
 */
export function BroadcastPage() {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<BroadcastRequest>();

  const send = useMutation({
    mutationFn: (values: BroadcastRequest) =>
      api.post<{ notificationId: string }>('/notifications/broadcast', values),
    onSuccess: () => {
      message.success('Đã gửi thông báo');
      form.resetFields();
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không gửi được thông báo');
    },
  });

  const confirmThenSend = (values: BroadcastRequest) => {
    modal.confirm({
      title: 'Gửi thông báo tới toàn bộ tài khoản đang hoạt động?',
      content:
        'Thông báo đã gửi không thu hồi được — nó vào hộp thư của mọi người và gửi kèm email.',
      okText: 'Gửi',
      cancelText: 'Xem lại',
      onOk: () => send.mutate(values),
    });
  };

  return (
    <Card title="Thông báo hệ thống">
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Gửi qua hộp thư trong ứng dụng và email"
        description="Hệ thống không gửi SMS (đã loại khỏi phạm vi v1 theo chốt BOQ đợt 1)."
      />

      <Form<BroadcastRequest>
        form={form}
        layout="vertical"
        style={{ maxWidth: 640 }}
        initialValues={{ severity: 'INFO' }}
        onFinish={confirmThenSend}
      >
        <Form.Item name="title" label="Tiêu đề" rules={[{ required: true, message: 'Bắt buộc' }]}>
          <Input maxLength={255} showCount />
        </Form.Item>

        <Form.Item name="body" label="Nội dung" rules={[{ required: true, message: 'Bắt buộc' }]}>
          <Input.TextArea rows={6} />
        </Form.Item>

        <Form.Item name="severity" label="Mức độ">
          <Select options={SEVERITY_OPTIONS} />
        </Form.Item>

        <Form.Item name="linkUrl" label="Liên kết đính kèm (tùy chọn)">
          <Input placeholder="/quan-tri/sao-luu" maxLength={500} />
        </Form.Item>

        <Typography.Paragraph type="secondary">
          Bỏ trống danh sách người nhận nghĩa là gửi cho toàn bộ tài khoản đang hoạt động.
        </Typography.Paragraph>

        <Button type="primary" htmlType="submit" loading={send.isPending}>
          Gửi thông báo
        </Button>
      </Form>
    </Card>
  );
}
