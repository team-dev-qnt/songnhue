import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Col, Descriptions, Empty, Row, Space, Typography } from 'antd';

import { StatusBadge } from '@/components/business/StatusBadge';
import { HEALTH_STATUS } from '@/components/business/statusVocabulary';
import { type HealthComponentView, type HealthView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';

/**
 * Tình trạng hệ thống — M5.12.
 *
 * Đọc từ `/api/v1/system/health` chứ **không** từ `/actuator/health`: actuator bị nginx
 * chặn khỏi Internet và trả bản rút gọn (`show-details: never`), vì chi tiết ở đó lộ cấu
 * trúc hạ tầng. Endpoint này yêu cầu quyền `adm:health:view` nên trả được đầy đủ.
 */
const COMPONENT_LABELS: Record<string, { label: string; hint: string }> = {
  db: { label: 'Cơ sở dữ liệu', hint: 'PostgreSQL + PostGIS — hỏng là toàn hệ thống dừng' },
  storage: { label: 'Kho tệp (MinIO)', hint: 'Tệp đính kèm, ảnh, bản kết xuất báo cáo' },
  mail: { label: 'Máy chủ thư', hint: 'Kênh thông báo ngoài ứng dụng; không cấu hình thì bỏ qua' },
  backup: {
    label: 'Sao lưu',
    hint: 'Tuổi bản sao lưu thành công gần nhất so với ngưỡng trong cấu hình',
  },
  telemetry: {
    label: 'Nguồn thủy văn',
    hint: 'API bên thứ ba — Phase 2 mới đấu nối thật, hiện là chỗ giữ sẵn',
  },
};

export function HealthPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['system', 'health'],
    queryFn: () => api.get<HealthView>('/system/health'),
    refetchInterval: 30_000,
  });

  if (error) {
    return (
      <Alert
        type="error"
        showIcon
        message={
          error instanceof ApiClientError ? error.message : 'Không đọc được tình trạng hệ thống'
        }
      />
    );
  }

  const components = Object.entries(data?.components ?? {});

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card loading={isLoading}>
        <Space align="center" size="middle">
          <Typography.Title level={5} style={{ margin: 0 }}>
            Tình trạng chung
          </Typography.Title>
          <StatusBadge value={data?.status} vocabulary={HEALTH_STATUS} />
          <Typography.Text type="secondary">Tự làm mới mỗi 30 giây</Typography.Text>
        </Space>
      </Card>

      {components.length === 0 && !isLoading && <Empty description="Không có thành phần nào" />}

      <Row gutter={[16, 16]}>
        {components.map(([name, component]) => (
          <Col xs={24} md={12} key={name}>
            <ComponentCard name={name} component={component} />
          </Col>
        ))}
      </Row>
    </Space>
  );
}

function ComponentCard({ name, component }: { name: string; component: HealthComponentView }) {
  const meta = COMPONENT_LABELS[name];
  const details = Object.entries(component.details ?? {});

  return (
    <Card
      size="small"
      title={
        <Space>
          <span>{meta?.label ?? name}</span>
          <StatusBadge value={component.status} vocabulary={HEALTH_STATUS} />
        </Space>
      }
    >
      {meta && (
        <Typography.Paragraph
          type="secondary"
          style={{ marginBottom: details.length > 0 ? 12 : 0 }}
        >
          {meta.hint}
        </Typography.Paragraph>
      )}
      {details.length > 0 && (
        <Descriptions column={1} size="small">
          {details.map(([key, value]) => (
            <Descriptions.Item key={key} label={key}>
              {formatDetail(value)}
            </Descriptions.Item>
          ))}
        </Descriptions>
      )}
    </Card>
  );
}

/** Chi tiết là `Map<String, Object>` bên backend — kiểu gì cũng có thể tới, in cho đọc được. */
function formatDetail(value: unknown): string {
  if (value === null || value === undefined) {
    return '—';
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}
