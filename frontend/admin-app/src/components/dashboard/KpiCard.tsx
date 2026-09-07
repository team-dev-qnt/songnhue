import { Card, Tag, Tooltip, Typography } from 'antd';
import { statusColors } from 'design-tokens';

import { KPI_TONE } from '@/components/business/statusVocabulary';
import { formatNumber } from '@/shared/format';
import { type KpiView } from '@/shared/api-types';

/**
 * Một ô KPI trên dashboard điều hành — T23.7.
 *
 * <h3>⛔ "Chưa có dữ liệu" phải trông KHÁC HẲN số 0</h3>
 *
 * Đây là toàn bộ lý do component này tồn tại thay vì dùng thẳng {@code <Statistic>} của
 * AntD. Ô "Sự cố chưa xử lý" hiện số 0 là một **câu khẳng định**: không có sự cố nào. Ô
 * chưa đấu nối nguồn dữ liệu mà cũng hiện 0 thì người trực ca đọc được đúng câu đó, và
 * tin nó — trong khi hệ thống chưa hề biết gì.
 *
 * <p>Nên ô không có số hiện dấu gạch, chữ xám, kèm **lý do** và **mốc sẽ có**. Người nhìn
 * phải hiểu ngay rằng đây là chỗ chưa xong, không phải một con số đáng mừng.
 *
 * <p>⚠ Backend đã ép ràng buộc "không có số thì phải nói lý do" ở tầng kiểu
 * ({@code DashboardService.Kpi}), nên `unavailableReason` luôn có khi `value` rỗng. Ở đây
 * vẫn có câu dự phòng vì FE không được sập nếu một bản API cũ hơn trả về thiếu.
 */
export function KpiCard({ kpi, wall = false }: { kpi: KpiView; wall?: boolean }) {
  const chuaCo = kpi.value === null || kpi.value === undefined;
  const mau = statusColors[KPI_TONE[kpi.tone]?.color ?? 'unknown'];

  return (
    <Card
      size={wall ? 'default' : 'small'}
      style={{ height: '100%' }}
      styles={{ body: { padding: wall ? '20px 24px' : 16 } }}
    >
      <Typography.Text
        type="secondary"
        style={{ fontSize: wall ? 'clamp(13px, 0.62vw, 22px)' : 13, display: 'block' }}
        ellipsis={{ tooltip: kpi.label }}
      >
        {kpi.label}
      </Typography.Text>

      {chuaCo ? (
        <Tooltip title={kpi.unavailableReason ?? undefined}>
          <div>
            <Typography.Text
              style={{
                color: statusColors.unknown,
                fontSize: wall ? 'clamp(24px, 1.6vw, 56px)' : 26,
                fontWeight: 600,
                lineHeight: 1.2,
                display: 'block',
              }}
            >
              —
            </Typography.Text>
            <Typography.Text
              type="secondary"
              style={{ fontSize: wall ? 'clamp(11px, 0.55vw, 18px)' : 12 }}
            >
              Chưa có dữ liệu
            </Typography.Text>
            {kpi.availableIn && (
              <div style={{ marginTop: 6 }}>
                <Tag style={{ margin: 0 }}>{kpi.availableIn}</Tag>
              </div>
            )}
          </div>
        </Tooltip>
      ) : (
        <div>
          <span
            style={{
              color: mau,
              fontSize: wall ? 'clamp(28px, 2.2vw, 72px)' : 30,
              fontWeight: 700,
              lineHeight: 1.15,
            }}
          >
            {formatNumber(kpi.value)}
          </span>
          {kpi.total !== null && kpi.total !== undefined && (
            <span
              style={{
                color: statusColors.unknown,
                fontSize: wall ? 'clamp(14px, 0.9vw, 30px)' : 16,
                marginLeft: 6,
              }}
            >
              / {formatNumber(kpi.total)}
            </span>
          )}
        </div>
      )}
    </Card>
  );
}
