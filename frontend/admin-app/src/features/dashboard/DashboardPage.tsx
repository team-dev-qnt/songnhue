import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Col, Row, Space, Statistic, Tag, Typography } from 'antd';
import { Link } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { StatusBadge } from '@/components/business/StatusBadge';
import { HEALTH_STATUS } from '@/components/business/statusVocabulary';
import { type BackupStatusView, type HealthView } from '@/shared/api-types';
import { api } from '@/shared/apiClient';
import { formatAge, formatDateTime } from '@/shared/format';
import { statusColors } from 'design-tokens';

/**
 * Trang chủ quản trị.
 *
 * Phase 0 chưa có số liệu nghiệp vụ nào (công trình, thủy văn, nhân sự đều thuộc Phase
 * 1–2), nên trang này cố ý chỉ hiện **hai thứ người trực cần biết trước khi làm gì khác**:
 * hệ thống có đang chạy đủ thành phần không, và có bản sao lưu dùng được không.
 *
 * Widget nào cũng gọi API mà tài khoản có thể không có quyền — nên mỗi khối tự kiểm quyền
 * trước khi gọi, thay vì để cả trang đỏ vì một ô 403.
 */
export function DashboardPage() {
  const { user, hasPermission } = useAuth();

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        style={{
          background: 'linear-gradient(135deg, #e6f4ff 0%, #f0f2f5 100%)',
          border: 'none',
        }}
      >
        <Typography.Title level={4} style={{ marginBottom: 4 }}>
          Xin chào, {user?.fullName ?? user?.username}
        </Typography.Title>
        <Space wrap>
          {(user?.roles ?? []).map((role) => (
            <Tag key={role} color="blue">
              {role}
            </Tag>
          ))}
        </Space>
      </Card>

      <Row gutter={[16, 16]}>
        {hasPermission('adm:backup:view') && (
          <Col xs={24} lg={12}>
            <BackupCard />
          </Col>
        )}
        {hasPermission('adm:health:view') && (
          <Col xs={24} lg={12}>
            <HealthCard />
          </Col>
        )}
      </Row>

      {!hasPermission('adm:backup:view') && !hasPermission('adm:health:view') && (
        <Alert
          type="info"
          showIcon
          message="Chưa có thông tin tổng quan cho tài khoản này"
          description="Các chức năng nghiệp vụ (vận hành công trình, thủy văn, nhân sự) sẽ bổ sung ở giai đoạn tiếp theo."
        />
      )}
    </Space>
  );
}

function BackupCard() {
  const { data, isLoading } = useQuery({
    queryKey: ['backups', 'status'],
    queryFn: () => api.get<BackupStatusView>('/backups/status'),
    refetchInterval: 60_000,
  });

  return (
    <Card title="Sao lưu" loading={isLoading} extra={<Link to="/quan-tri/sao-luu">Chi tiết</Link>}>
      <Statistic
        title="Thành công gần nhất"
        value={formatAge(data?.ageSeconds)}
        valueStyle={{ color: data?.stale ? statusColors.danger : statusColors.normal }}
      />
      <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
        {data?.lastSuccess
          ? formatDateTime(data.lastSuccess.startedAt)
          : 'Hệ thống chưa từng được sao lưu'}
      </Typography.Paragraph>
      {data?.stale && (
        <Alert
          type="error"
          showIcon
          style={{ marginTop: 12 }}
          message={`Quá ngưỡng ${data.staleThresholdHours} giờ — không có bản khôi phục nào đủ mới`}
        />
      )}
    </Card>
  );
}

function HealthCard() {
  const { data, isLoading } = useQuery({
    queryKey: ['system', 'health'],
    queryFn: () => api.get<HealthView>('/system/health'),
    refetchInterval: 30_000,
  });

  const components = Object.entries(data?.components ?? {});
  const down = components.filter(([, component]) => component.status !== 'UP');

  return (
    <Card
      title="Tình trạng hệ thống"
      loading={isLoading}
      extra={<Link to="/quan-tri/tinh-trang">Chi tiết</Link>}
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <StatusBadge value={data?.status} vocabulary={HEALTH_STATUS} />
        {down.length === 0 ? (
          <Typography.Text type="secondary">
            {components.length} thành phần đều bình thường
          </Typography.Text>
        ) : (
          <Alert
            type="error"
            showIcon
            message="Thành phần đang có vấn đề"
            description={down.map(([name]) => name).join(', ')}
          />
        )}
      </Space>
    </Card>
  );
}
