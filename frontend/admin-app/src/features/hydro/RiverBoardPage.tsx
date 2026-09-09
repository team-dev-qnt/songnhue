import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, App, Button, Card, DatePicker, Space, Typography } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';

import { type RiverBoardReport } from '@/shared/api-types';
import { api } from '@/shared/apiClient';
import { formatDateTime } from '@/shared/format';

import { useAuth } from '@/app/auth/useAuth';

import { RiverSummaryBoard } from './RiverSummaryBoard';
import { useXuatBaoCao } from './useXuatBaoCao';

/**
 * ⭐ Chu kỳ làm mới của **bảng nội bộ** — 2 phút.
 *
 * ⛔ Cố ý ⛔ KHÁC con số 5 phút của widget cổng công khai (OI-09), và ⛔ đừng gộp thành một tham số:
 * hai con số ấy trả lời hai câu hỏi khác nhau — người trực cần thấy số mới nhất, còn cổng công khai
 * cân bằng giữa độ tươi và tải máy chủ. *"Một công tắc cho hai bóng đèn cũng là lỗi."*
 */
const NHIP_LAM_MOI_MS = 2 * 60 * 1000;

/**
 * **BC-11 — Biểu tổng hợp mực nước theo tuyến sông** (T34.4).
 *
 * ⭐⭐ Trang này chỉ là **khung**: toàn bộ phần hiển thị nằm ở {@link RiverSummaryBoard}, và chính
 * component ấy sẽ được **màn hình tường 4K** dùng lại nguyên vẹn (WS-35). Làm một lần dùng hai nơi
 * là yêu cầu nguyên văn của T34.4 — dựng một bố cục thứ hai cho wall mode là mở đường cho hai con
 * số khác nhau về cùng một mực nước, và chúng sẽ lệch đúng vào ngày có sự cố.
 */
export function RiverBoardPage() {
  const [ngay, setNgay] = useState<Dayjs>(() => dayjs());
  const { message } = App.useApp();
  const { hasPermission } = useAuth();
  const { xuat, dangCho } = useXuatBaoCao();

  const laHomNay = ngay.isSame(dayjs(), 'day');
  const bieu = useQuery({
    queryKey: ['hyd', 'bao-cao', 'tuyen-song', ngay.format('YYYY-MM-DD')],
    queryFn: () =>
      api.get<RiverBoardReport>(`/hyd/bao-cao/tuyen-song?ngay=${ngay.format('YYYY-MM-DD')}`),
    // ⚠ Chỉ tự làm mới khi đang xem HÔM NAY. Một ngày trong quá khứ ⛔ không đổi nữa, và hỏi lại
    //   máy chủ hai phút một lần cho một tập số liệu đã đóng băng là tải thừa suốt cả buổi.
    refetchInterval: laHomNay ? NHIP_LAM_MOI_MS : false,
  });

  return (
    <Card
      title="BC-11 — Biểu tổng hợp mực nước theo tuyến sông"
      extra={
        <Space wrap>
          <Typography.Text type="secondary">
            {bieu.dataUpdatedAt
              ? `Cập nhật ${formatDateTime(new Date(bieu.dataUpdatedAt).toISOString())}`
              : ''}
          </Typography.Text>
          {/*
            ⭐ Nút Xuất thêm 09/09/2026. Trước đó BC-11 là báo cáo DUY NHẤT ⛔ không có đường xuất
               nào — 97 dòng, 0 nút — trong khi đặc tả gọi nó là biểu "ưu tiên cao nhất" và nó mô
               phỏng đúng biểu Công ty đang dùng trên giấy hằng ngày.
            ⚠ Gác bằng `hyd:report:export`, ⛔ KHÔNG bằng quyền của cả trang: xem và mang ra ngoài
              là hai việc — cùng luật với BC-13 và BC-05.
            ⚠ `tuNgay` = `denNgay`: BC-11 là ảnh chụp một ngày, và backend TỪ CHỐI nếu hai giá trị
              lệch nhau (`SYS-0003`), nên ⛔ không có đường nào sinh ra một tệp mang tên sai kỳ.
          */}
          {hasPermission('hyd:report:export') ? (
            <Button
              icon={<DownloadOutlined />}
              loading={dangCho}
              onClick={() => {
                const d = ngay.format('YYYY-MM-DD');
                void xuat({ loai: 'BC11', tuNgay: d, denNgay: d })
                  .then((ten) => message.success(`Đã tải ${ten}`))
                  .catch((e: unknown) =>
                    message.error(e instanceof Error ? e.message : 'Không kết xuất được'),
                  );
              }}
            >
              {dangCho ? 'Đang dựng tệp…' : 'Xuất CSV'}
            </Button>
          ) : null}
          <Button
            icon={<ReloadOutlined />}
            loading={bieu.isFetching}
            onClick={() => void bieu.refetch()}
          >
            Làm mới
          </Button>
          <DatePicker
            allowClear={false}
            value={ngay}
            onChange={(v) => v && setNgay(v)}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
          />
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={
          laHomNay
            ? `Cột "Hiện tại" là giá trị HỢP LỆ mới nhất — tự làm mới ${NHIP_LAM_MOI_MS / 60000} phút một lần`
            : 'Đang xem một ngày trong quá khứ — cột "Hiện tại" vẫn là giá trị mới nhất của điểm đo, ⛔ không phải giá trị cuối ngày ấy'
        }
        description={
          <>
            Điểm đo <b>mất tín hiệu vẫn có mặt</b>, ô số liệu để trống kèm lý do — đó là thứ biểu
            này sinh ra để chỉ ra, ⛔ không phải thứ nên ẩn đi.
            <br />
            <Typography.Text type="secondary">
              Cột <b>Lượng mưa</b> luôn trống: loại chỉ số đã khai nhưng chưa gắn cho điểm đo nào
              (mục G3-a). Cột <b>Tình hình vận hành</b> lấy từ chính nguồn mà cổng công khai đang
              công bố — một định nghĩa &quot;hiện hành&quot;, ⛔ không hai.
            </Typography.Text>
          </>
        }
      />

      <RiverSummaryBoard tuyen={bieu.data?.tuyen ?? []} loading={bieu.isLoading} />
    </Card>
  );
}

export default RiverBoardPage;
