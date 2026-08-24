import { useQuery } from '@tanstack/react-query';
import { Card, Col, Row, Statistic } from 'antd';
import { statusColors } from 'design-tokens';

import { CONSTRUCTION_STATUS, CONSTRUCTION_TYPE } from '@/components/business/statusVocabulary';
import { type ConstructionStatisticsView } from '@/shared/api-types';
import { api } from '@/shared/apiClient';

export function ConstructionStatisticsBlock() {
  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'constructions', 'statistics'],
    queryFn: () => api.get<ConstructionStatisticsView>('/ops/constructions/statistics'),
  });

  if (isLoading || !data) {
    return null;
  }

  // Find counts for important statuses to show
  const warningCount = data.byStatus.find((s) => s.key === 'CANH_BAO')?.count ?? 0;
  const incidentCount = data.byStatus.find((s) => s.key === 'SU_CO')?.count ?? 0;
  const maintenanceCount = data.byStatus.find((s) => s.key === 'BAO_TRI')?.count ?? 0;

  // Find pump and sluice counts
  const pumpCount = data.byType.find((s) => s.key === 'TRAM_BOM')?.count ?? 0;
  const sluiceCount = data.byType.find((s) => s.key === 'CONG')?.count ?? 0;

  return (
    <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
      <Col span={4}>
        <Card size="small">
          <Statistic title="Tổng số công trình" value={data.total} />
        </Card>
      </Col>
      <Col span={4}>
        <Card size="small">
          <Statistic title={CONSTRUCTION_TYPE['TRAM_BOM']?.label ?? 'Trạm bơm'} value={pumpCount} />
        </Card>
      </Col>
      <Col span={4}>
        <Card size="small">
          <Statistic title={CONSTRUCTION_TYPE['CONG']?.label ?? 'Cống'} value={sluiceCount} />
        </Card>
      </Col>
      <Col span={4}>
        <Card size="small">
          <Statistic
            title={CONSTRUCTION_STATUS['CANH_BAO']?.label ?? 'Cảnh báo'}
            value={warningCount}
            valueStyle={{ color: warningCount > 0 ? statusColors.warning : undefined }}
          />
        </Card>
      </Col>
      <Col span={4}>
        <Card size="small">
          <Statistic
            title={CONSTRUCTION_STATUS['SU_CO']?.label ?? 'Sự cố'}
            value={incidentCount}
            valueStyle={{ color: incidentCount > 0 ? statusColors.danger : undefined }}
          />
        </Card>
      </Col>
      <Col span={4}>
        <Card size="small">
          <Statistic
            title={CONSTRUCTION_STATUS['BAO_TRI']?.label ?? 'Bảo trì'}
            value={maintenanceCount}
            valueStyle={{ color: maintenanceCount > 0 ? statusColors.warning : undefined }}
          />
        </Card>
      </Col>
    </Row>
  );
}
