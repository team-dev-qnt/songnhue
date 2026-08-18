import { CloudDownloadOutlined, WarningOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  Modal,
  Row,
  Space,
  Statistic,
  Table,
  Tooltip,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { StatusBadge } from '@/components/business/StatusBadge';
import { BACKUP_STATUS, BACKUP_TRIGGER } from '@/components/business/statusVocabulary';
import { type BackupStatusView, type BackupView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { formatAge, formatBytes, formatDateTime, formatDuration } from '@/shared/format';
import { statusColors } from 'design-tokens';

import { isRestoreVisible } from './restoreAccess';

/** Chuỗi xác nhận — phải khớp `RestoreService.CONFIRMATION_PHRASE` phía backend. */
const CONFIRMATION_PHRASE = 'SONGNHUE';
const REASON_MIN_LENGTH = 10;

/**
 * Sao lưu & khôi phục — M5.10 và M5.11.
 *
 * <h3>Vì sao lượt sao lưu THẤT BẠI cũng nằm trong bảng</h3>
 *
 * Lượt hỏng **không** để lại tệp nào. Nếu màn hình chỉ đọc thư mục chứa dump thì đúng cái
 * ngày sao lưu chết lại là ngày không có gì hiện ra — trông y hệt "chưa tới giờ chạy".
 * Sổ đăng ký `system_backups` ghi cả lượt hỏng, và đó là dòng đáng đọc nhất ở đây.
 *
 * <h3>Không có PITR — nên "quá hạn" là chuyện nghiêm trọng</h3>
 *
 * Hệ thống chấp nhận RPO ≤ 24 giờ, không có replica, không có WAL archiving
 * (`architecture-review.md` §6.5). Bản dump đêm là **đường phục hồi duy nhất**; nó cũ quá
 * ngưỡng nghĩa là đang chạy không lưới an toàn, nên ô đó tô đỏ chứ không phải vàng.
 */
export function BackupPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { hasPermission, hasRole } = useAuth();
  const [restoring, setRestoring] = useState<BackupView | null>(null);

  const status = useQuery({
    queryKey: ['backups', 'status'],
    queryFn: () => api.get<BackupStatusView>('/backups/status'),
    refetchInterval: 60_000,
  });

  const history = useQuery({
    queryKey: ['backups', 'history'],
    queryFn: () => api.get<BackupView[]>('/backups'),
  });

  const runBackup = useMutation({
    mutationFn: () => api.post<{ jobId: string; status: string }>('/backups'),
    onSuccess: async () => {
      message.success('Đã đặt lệnh sao lưu — việc chạy nền, xem kết quả ở bảng lịch sử');
      await queryClient.invalidateQueries({ queryKey: ['backups'] });
    },
    onError: (caught: unknown) => {
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không đặt được lệnh sao lưu',
      );
    },
  });

  const data = status.data;
  const canRestore = isRestoreVisible(hasRole('SUPER_ADMIN'), data);

  const columns: ColumnsType<BackupView> = [
    {
      title: 'Bắt đầu',
      dataIndex: 'startedAt',
      width: 170,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 130,
      render: (value: string) => <StatusBadge value={value} vocabulary={BACKUP_STATUS} />,
    },
    {
      title: 'Nguồn',
      dataIndex: 'trigger',
      width: 160,
      render: (value: string) => <StatusBadge value={value} vocabulary={BACKUP_TRIGGER} />,
    },
    { title: 'Tên tệp', dataIndex: 'fileName', ellipsis: true },
    {
      title: 'Dung lượng',
      dataIndex: 'sizeBytes',
      width: 120,
      align: 'right',
      render: (value: number | null) => formatBytes(value),
    },
    {
      title: 'Thời lượng',
      dataIndex: 'durationMs',
      width: 130,
      render: (value: number | null) => formatDuration(value),
    },
    {
      title: 'Ghi chú',
      key: 'ghi-chu',
      width: 220,
      render: (_value, row) =>
        row.errorMessage ? (
          <Tooltip title={row.errorMessage}>
            <Typography.Text type="danger" ellipsis>
              {row.errorMessage}
            </Typography.Text>
          </Tooltip>
        ) : (
          <Tooltip title={row.checksumSha256 ?? ''}>
            <Typography.Text type="secondary">
              {row.checksumSha256 ? `SHA-256 ${row.checksumSha256.slice(0, 12)}…` : '—'}
            </Typography.Text>
          </Tooltip>
        ),
    },
    ...(canRestore
      ? [
          {
            title: '',
            key: 'khoi-phuc',
            width: 130,
            render: (_value: unknown, row: BackupView) =>
              row.status === 'SUCCEEDED' ? (
                <Button type="link" danger onClick={() => setRestoring(row)}>
                  Khôi phục
                </Button>
              ) : null,
          } as ColumnsType<BackupView>[number],
        ]
      : []),
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card title="Tình trạng sao lưu" loading={status.isLoading}>
        {data?.stale && (
          <Alert
            type="error"
            showIcon
            icon={<WarningOutlined />}
            style={{ marginBottom: 16 }}
            message="Bản sao lưu gần nhất đã quá hạn"
            description={`Ngưỡng đang đặt là ${data.staleThresholdHours} giờ. Hệ thống không có PITR — bản dump đêm là đường phục hồi duy nhất. Kiểm tra ngay theo docs/runbook/sao-luu-hong.md.`}
          />
        )}
        {data && !data.scheduleEnabled && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="Sao lưu tự động đang TẮT"
            description="Hệ thống đang chạy không có lưới an toàn. Bật lại ở màn hình Cấu hình, tham số backup.schedule-enabled."
          />
        )}

        <Row gutter={16}>
          <Col xs={24} sm={8}>
            <Statistic
              title="Sao lưu thành công gần nhất"
              value={formatAge(data?.ageSeconds)}
              valueStyle={{ color: data?.stale ? statusColors.danger : statusColors.normal }}
            />
          </Col>
          <Col xs={24} sm={8}>
            <Statistic
              title="Thời điểm"
              value={data?.lastSuccess ? formatDateTime(data.lastSuccess.startedAt) : 'Chưa từng'}
            />
          </Col>
          <Col xs={24} sm={8}>
            <Statistic
              title="Dung lượng bản gần nhất"
              value={formatBytes(data?.lastSuccess?.sizeBytes)}
            />
          </Col>
        </Row>

        {hasPermission('adm:backup:create') && (
          <Button
            type="primary"
            icon={<CloudDownloadOutlined />}
            style={{ marginTop: 16 }}
            loading={runBackup.isPending}
            onClick={() => runBackup.mutate()}
          >
            Sao lưu ngay
          </Button>
        )}

        {data && !data.restoreAvailable && (
          <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0 }}>
            Khôi phục qua giao diện chưa bật trên môi trường này. Đường khôi phục chính thức là
            runbook <Typography.Text code>docs/runbook/khoi-phuc-du-lieu.md</Typography.Text>.
          </Typography.Paragraph>
        )}
      </Card>

      <Card title="Lịch sử sao lưu">
        <Table<BackupView>
          columns={columns}
          dataSource={history.data ?? []}
          rowKey="id"
          loading={history.isLoading}
          scroll={{ x: 1200 }}
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <RestoreModal backup={restoring} onClose={() => setRestoring(null)} />
    </Space>
  );
}

interface RestoreForm {
  confirmation: string;
  reason: string;
  totpCode: string;
}

/**
 * Hộp thoại khôi phục — ba lớp chặn ở giao diện, ba lớp nữa ở backend.
 *
 * Bắt nhập lại **mã 2FA ngay lúc thao tác**, không chấp nhận "đã qua 2FA lúc đăng nhập":
 * một phiên mở từ sáng trên máy không khoá màn hình vẫn là phiên hợp lệ. Nhập mã chứng
 * minh người đang ngồi đó thật sự giữ thiết bị thứ hai.
 */
function RestoreModal({ backup, onClose }: { backup: BackupView | null; onClose: () => void }) {
  const { message } = App.useApp();
  const [form] = Form.useForm<RestoreForm>();

  const restore = useMutation({
    mutationFn: (values: RestoreForm) =>
      api.post<{ jobId: string }>(`/backups/${backup?.id}/restore`, values),
    onSuccess: () => {
      message.warning(
        'Đã bắt đầu khôi phục. Hệ thống chuyển sang chế độ bảo trì, mọi thao tác ghi bị chặn cho tới khi xong.',
      );
      form.resetFields();
      onClose();
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không khôi phục được');
    },
  });

  return (
    <Modal
      open={backup !== null}
      title="Khôi phục dữ liệu từ bản sao lưu"
      okText="Khôi phục"
      okButtonProps={{ danger: true }}
      cancelText="Hủy"
      confirmLoading={restore.isPending}
      onCancel={onClose}
      onOk={() => void form.submit()}
      width={620}
      destroyOnClose
    >
      <Alert
        type="error"
        showIcon
        style={{ marginBottom: 16 }}
        message="Thao tác này GHI ĐÈ toàn bộ cơ sở dữ liệu hiện tại"
        description="Mọi dữ liệu phát sinh sau thời điểm của bản sao lưu sẽ mất. Hệ thống tự chụp một bản trước khi ghi đè để còn đường lùi."
      />

      {backup && (
        <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
          <Descriptions.Item label="Tệp">{backup.fileName}</Descriptions.Item>
          <Descriptions.Item label="Thời điểm">
            {formatDateTime(backup.startedAt)}
          </Descriptions.Item>
          <Descriptions.Item label="Dung lượng">{formatBytes(backup.sizeBytes)}</Descriptions.Item>
        </Descriptions>
      )}

      <Form<RestoreForm>
        form={form}
        layout="vertical"
        preserve={false}
        onFinish={(values) => restore.mutate(values)}
      >
        <Form.Item
          name="confirmation"
          label={`Gõ chính xác "${CONFIRMATION_PHRASE}" để xác nhận`}
          rules={[
            {
              validator: (_rule, value: string) =>
                value === CONFIRMATION_PHRASE
                  ? Promise.resolve()
                  : Promise.reject(new Error(`Phải gõ đúng "${CONFIRMATION_PHRASE}"`)),
            },
          ]}
        >
          <Input autoComplete="off" placeholder={CONFIRMATION_PHRASE} />
        </Form.Item>

        <Form.Item
          name="reason"
          label="Lý do khôi phục"
          extra="Nội dung này đi vào nhật ký bảo mật và không sửa lại được"
          rules={[
            {
              required: true,
              min: REASON_MIN_LENGTH,
              message: `Tối thiểu ${REASON_MIN_LENGTH} ký tự`,
            },
          ]}
        >
          <Input.TextArea rows={3} />
        </Form.Item>

        <Form.Item
          name="totpCode"
          label="Mã xác thực hai bước"
          extra="Nhập mã đang hiển thị trên ứng dụng xác thực"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input inputMode="numeric" autoComplete="one-time-code" placeholder="123456" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
