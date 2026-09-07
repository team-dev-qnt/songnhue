import { Alert, Skeleton, Space, Typography } from 'antd';
import { statusColors } from 'design-tokens';
import { useMemo } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';

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
import { useDashboard, useMapPoints, useStationLayer } from './useDashboard';

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
  const navigate = useNavigate();
  const wall = useWallMode(thamSo);

  const { data, isLoading, isError, dataUpdatedAt } = useDashboard();
  const { data: diem } = useMapPoints();
  const lopDiemDo = useStationLayer();
  // ⚠ `null` khi CHƯA TẢI XONG — khác hẳn 0. Quy tắc 16: số 0 là một câu khẳng định, và ở đây nó
  //   sẽ khẳng định "mọi điểm đo đã có toạ độ" đúng vào lúc chưa ai đếm.
  //
  // ⚠⚠ `?.` trên CẢ HAI mức, ⛔ không phải chỉ trên `data`. Bản đầu viết
  //    `lopDiemDo.data ? lopDiemDo.data.chuaSoHoaViTri.length : null` và **làm sập cả trang
  //    dashboard** ngay lượt chạy bài kiểm đầu tiên: một phản hồi đúng-kiểu-nhưng-sai-hình-dạng
  //    (API cũ, hoặc lỗi mạng trả thân rỗng) cho `chuaSoHoaViTri === undefined`, và
  //    `undefined.length` ném ngay trong lúc render. TypeScript ⛔ không thấy được điều đó — nó
  //    tin kiểu ta khai. Đây là T35.10 áp cho màn hình quản trị: một lớp phụ hỏng ⛔ không được
  //    làm sập màn hình điều hành.
  const chuaSoHoaViTri = lopDiemDo.data?.chuaSoHoaViTri?.length ?? null;

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
            title="Bản đồ công trình và điểm đo"
            /* ⭐ T35.2 — nói ra ĐÍCH XÁC còn thiếu bao nhiêu toạ độ, ⛔ không để bản đồ trống tự
               giải thích. Hôm nay là 19/19 điểm đo (mục G8), và con số ấy chính là thứ Công ty
               cần thấy để biết phải cấp những gì. ⛔ Không hiện "0 điểm đo" khi chưa tải xong —
               `?? 0` ở đây sẽ là một khẳng định sai trong khoảng thời gian chờ. */
            note={
              chuaSoHoaViTri === null
                ? 'Chấm tròn: công trình. Quả trám: điểm đo thuỷ văn — màu theo mức cảnh báo, viền nét đứt khi số đo bị đánh dấu nghi ngờ.'
                : `Chấm tròn: công trình. Quả trám: điểm đo thuỷ văn. ⚠ ${chuaSoHoaViTri} điểm đo chưa có toạ độ nên chưa lên bản đồ (mục G8).`
            }
            wall={wall}
          >
            <ConstructionMap
              points={diem ?? []}
              diemDo={lopDiemDo.data?.diemDo ?? []}
              config={data?.map}
              height={wall ? 520 : 380}
            />
          </ChartCard>

          <ChartCard title="Phân bố theo trạng thái" wall={wall}>
            <BaseChart
              wall={wall}
              height={wall ? 340 : 260}
              empty={!thongKe || thongKe.byStatus.length === 0}
              option={optionTron(thongKe?.byStatus ?? [], CONSTRUCTION_STATUS)}
              onClick={(p) => {
                const data = p?.data as Record<string, unknown> | undefined;
                if (data?.bucketKey) {
                  navigate(`/van-hanh/cong-trinh?status=${data.bucketKey}`);
                }
              }}
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
              onClick={(p) => {
                const data = p?.data as Record<string, unknown> | undefined;
                if (data?.bucketKey) {
                  navigate(`/van-hanh/cong-trinh?type=${data.bucketKey}`);
                }
              }}
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
              onClick={(p) => {
                const data = p?.data as Record<string, unknown> | undefined;
                if (data?.bucketKey) {
                  navigate(`/van-hanh/cong-trinh?level=${data.bucketKey}`);
                }
              }}
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
