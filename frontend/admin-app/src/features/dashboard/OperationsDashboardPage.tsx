import { Alert, Skeleton, Space, Typography } from 'antd';
import { statusColors } from 'design-tokens';
import { useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

import {
  CONSTRUCTION_STATUS,
  CONSTRUCTION_TYPE,
  MANAGEMENT_LEVEL,
} from '@/components/business/statusVocabulary';
import { BaseChart } from '@/components/charts/BaseChart';
import {
  optionCotDoc,
  optionCotNgang,
  optionDongHo,
  optionTron,
} from '@/components/charts/chartOptions';
import { ChartCard } from '@/components/dashboard/ChartCard';
import { ConstructionMap } from '@/components/dashboard/ConstructionMap';
import { KpiCard } from '@/components/dashboard/KpiCard';
import { boCucTheoBeRong, cotThanhCss } from '@/components/dashboard/gridLayout';
import { useElementWidth } from '@/components/dashboard/useElementWidth';
import { formatDateTime } from '@/shared/format';

import { WallFrame } from './WallFrame';
import { useWallMode } from './wallMode';
import { useDashboard, useMapPoints } from './useDashboard';

/**
 * Dashboard điều hành — CN-02.5, CN-02.6.
 *
 * <h3>Một màn hình, hai chế độ — không phải hai bố cục</h3>
 *
 * `?mode=wall` đổi **theme và cỡ chữ**, không đổi cấu trúc: cùng route, cùng cây
 * component, cùng lưới. Dựng hai bố cục riêng thì mỗi lần thêm một ô KPI phải nhớ thêm ở
 * hai chỗ, và chỗ bị quên là chỗ không ai mở hằng ngày — tức là màn hình treo tường.
 *
 * <h3>⛔ Không con số nào ở đây được tính bằng JavaScript</h3>
 *
 * Quy tắc 3 của dự án. Tỉ lệ số hoá toạ độ trông như một phép chia vô hại, nhưng nó là
 * phép chia trên hai con số **đã bị lọc theo phạm vi đơn vị** — làm ở FE thì mẫu số và
 * tử số có thể đến từ hai lượt gọi khác nhau. Cả hai đều lấy từ một phản hồi duy nhất.
 */
export function OperationsDashboardPage() {
  const [thamSo] = useSearchParams();
  const wall = useWallMode(thamSo);

  const { data, isLoading, isError, dataUpdatedAt } = useDashboard();
  const { data: diem } = useMapPoints();

  const { ref, beRong } = useElementWidth<HTMLDivElement>();
  const boCuc = boCucTheoBeRong(beRong);

  const thongKe = data?.statistics;

  const tyLeSoHoa = useMemo(() => {
    if (!thongKe || thongKe.total === 0) {
      return null;
    }
    // Con số này backend đã trả sẵn cả tử lẫn mẫu trong cùng một phản hồi — phép chia ở
    // đây chỉ là quy đổi sang phần trăm để vẽ, không phải một chỉ tiêu tự tính.
    return ((thongKe.total - thongKe.withoutLocation) / thongKe.total) * 100;
  }, [thongKe]);

  if (isLoading) {
    return <Skeleton active paragraph={{ rows: 10 }} />;
  }

  const noiDung = (
    <div ref={ref}>
      <Space direction="vertical" size={wall ? 'large' : 'middle'} style={{ width: '100%' }}>
        {isError && (
          <Alert
            type="warning"
            showIcon
            message="Dữ liệu chưa cập nhật"
            description={
              dataUpdatedAt > 0
                ? `Không gọi được máy chủ. Số liệu đang hiện là của lúc ${formatDateTime(new Date(dataUpdatedAt).toISOString())}.`
                : 'Không gọi được máy chủ và chưa có số liệu nào để hiện.'
            }
          />
        )}

        {/* --- KPI --- */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: cotThanhCss(boCuc.cotKpi),
            gap: wall ? 16 : 12,
          }}
          data-testid="luoi-kpi"
        >
          {(data?.kpis ?? []).map((kpi) => (
            <KpiCard key={kpi.key} kpi={kpi} wall={wall} />
          ))}
        </div>

        {/* --- Bản đồ + phân bố trạng thái --- */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: boCuc.cotKhoi >= 2 ? '2fr 1fr' : '1fr',
            gap: wall ? 16 : 12,
          }}
        >
          <ChartCard
            title="Bản đồ công trình"
            note="Chỉ hiện hồ sơ đã số hoá toạ độ. Màu marker theo trạng thái vận hành."
            wall={wall}
          >
            <ConstructionMap points={diem ?? []} config={data?.map} height={wall ? 520 : 380} />
          </ChartCard>

          <ChartCard title="Phân bố theo trạng thái" wall={wall}>
            <BaseChart
              wall={wall}
              height={wall ? 340 : 260}
              empty={!thongKe || thongKe.byStatus.length === 0}
              option={optionTron(thongKe?.byStatus ?? [], CONSTRUCTION_STATUS)}
            />
            <BaseChart
              wall={wall}
              height={wall ? 200 : 160}
              empty={tyLeSoHoa === null}
              emptyText="Chưa có hồ sơ công trình nào"
              option={optionDongHo(tyLeSoHoa ?? 0, 'Đã số hoá toạ độ')}
            />
          </ChartCard>
        </div>

        {/* --- Thống kê CN-02.6 --- */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: cotThanhCss(boCuc.cotKhoi),
            gap: wall ? 16 : 12,
          }}
          data-testid="luoi-thong-ke"
        >
          <ChartCard title="Theo loại công trình" wall={wall}>
            <BaseChart
              wall={wall}
              height={wall ? 300 : 240}
              empty={!thongKe || thongKe.byType.length === 0}
              option={optionCotDoc(thongKe?.byType ?? [], CONSTRUCTION_TYPE)}
            />
          </ChartCard>

          <ChartCard
            title="Theo đơn vị quản lý"
            note="Số liệu đã lọc theo phạm vi đơn vị của tài khoản đang xem."
            wall={wall}
          >
            <BaseChart
              wall={wall}
              height={wall ? 300 : 240}
              empty={!thongKe || thongKe.byOrgUnit.length === 0}
              option={optionCotNgang(thongKe?.byOrgUnit ?? [])}
            />
          </ChartCard>

          <ChartCard title="Theo cấp quản lý" wall={wall}>
            <BaseChart
              wall={wall}
              height={wall ? 300 : 240}
              empty={!thongKe || thongKe.byManagementLevel.length === 0}
              option={optionCotDoc(thongKe?.byManagementLevel ?? [], MANAGEMENT_LEVEL)}
            />
          </ChartCard>
        </div>

        {!wall && (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Cập nhật lúc {formatDateTime(data?.generatedAt)} · tự làm mới mỗi{' '}
            {Math.round((data?.autoRefreshSeconds ?? 0) / 60)} phút (sửa ở Cấu hình hệ thống) ·{' '}
            <a href="?mode=wall" style={{ color: statusColors.normal }}>
              mở chế độ màn hình lớn
            </a>
          </Typography.Text>
        )}
      </Space>
    </div>
  );

  if (!wall) {
    return noiDung;
  }
  return (
    <WallFrame
      capNhatLuc={data?.generatedAt}
      rotateSeconds={data?.wallRotateSeconds ?? 30}
      mat={isError}
    >
      {noiDung}
    </WallFrame>
  );
}
