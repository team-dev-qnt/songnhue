import { ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Select, Space, Typography } from 'antd';
import { useState } from 'react';

import { BaseChart } from '@/components/charts/BaseChart';
import { optionDuong } from '@/components/charts/chartOptions';
import { type Station, type WaterLevelChart } from '@/shared/api-types';
import { api } from '@/shared/apiClient';
import { formatDateTime } from '@/shared/format';

/**
 * ⭐ Nhịp làm mới **nội bộ** — 2 phút, bám chu kỳ poller (chốt G3).
 *
 * ⛔ Cố ý ⛔ KHÁC con số 5 phút của cổng công khai (OI-09), và ⛔ đừng gộp thành một tham số.
 * `HaiNhipLamMoiTest` canh sự tách rời ấy: bản gộp **chạy đúng** ở mọi bài kiểm hiện có, cái mất
 * chỉ lộ ra ngày Công ty hạ nhịp cổng và màn hình trực ban im lặng đi theo.
 */
const NHIP_LAM_MOI_MS = 2 * 60 * 1000;

/**
 * **Biểu đồ mực nước 24 giờ** — T35.4, **chuỗi thời gian đầu tiên của hệ thống**.
 *
 * <h3>⭐ Trang này là nơi gọi THẬT đầu tiên của `optionDuong`</h3>
 *
 * `optionDuong` sống trong `chartOptions.ts` từ Phase 1 với **0 nơi gọi** ngoài bài kiểm của chính
 * nó, và javadoc của nó tự đặt hạn: *"⛔ Nếu Phase 2 đến mà vẫn không ai gọi thì phải XOÁ, không
 * phải giữ"* (§10.33). Phase 2 đã đến **và mang theo dữ liệu thật** — nên nó được nối, ⛔ không
 * được gia hạn thêm một lần nữa.
 *
 * <h3>⛔⛔ Khoảng trống trên đường cong là THÔNG TIN, ⛔ không phải lỗi vẽ</h3>
 *
 * `optionDuong` đặt `connectNulls: false`, và điều đó chịu lực ở đây hơn ở bất kỳ biểu đồ nào khác:
 * một quãng trạm mất tín hiệu **phải nhìn thấy được**. Nối liền qua nó là vẽ ra một đoạn số liệu
 * **chưa từng được đo**, trên đúng màn hình mà người trực ban dùng để quyết định vận hành cống.
 *
 * <h3>⛔ Biểu đồ rỗng phải nói VÌ SAO (quy tắc 16)</h3>
 *
 * Backend trả `lyDoTrong` và ép ràng buộc *"hoặc có điểm, hoặc có lý do"* ở hàm dựng. Ở đây câu ấy
 * đi vào `emptyText` của `BaseChart` — ⛔ không vẽ một khung trục rỗng, thứ trông **y hệt** một biểu
 * đồ mà mọi giá trị bằng 0.
 */
export function WaterLevelChartPage() {
  const [diemDo, setDiemDo] = useState<string | undefined>();

  const dsDiemDo = useQuery({
    queryKey: ['hyd', 'stations'],
    queryFn: () => api.get<Station[]>('/hyd/stations'),
  });

  const bieu = useQuery({
    queryKey: ['hyd', 'bieu-do', 'muc-nuoc-24h', diemDo],
    queryFn: () => api.get<WaterLevelChart>(`/hyd/bieu-do/muc-nuoc-24h?stationPublicId=${diemDo}`),
    // ⛔ Chưa chọn điểm đo thì ⛔ không gọi: endpoint đòi `stationPublicId`, và một lượt gọi thiếu
    //   tham số trả 400 rồi hiện thành một dải đỏ ngay khi vừa mở trang.
    enabled: Boolean(diemDo),
    refetchInterval: NHIP_LAM_MOI_MS,
  });

  const diem = bieu.data?.diem ?? [];
  const donVi = bieu.data?.donVi ?? '';

  return (
    <Card
      title="Biểu đồ mực nước 24 giờ"
      extra={
        <Space wrap>
          <Typography.Text type="secondary">
            {bieu.dataUpdatedAt
              ? `Cập nhật ${formatDateTime(new Date(bieu.dataUpdatedAt).toISOString())}`
              : ''}
          </Typography.Text>
          <Select
            showSearch
            allowClear
            style={{ minWidth: 260 }}
            placeholder="Chọn điểm đo"
            loading={dsDiemDo.isLoading}
            value={diemDo}
            onChange={setDiemDo}
            optionFilterProp="label"
            // ⚠ `s.id` LÀ `public_id` — DTO của điểm đo cố ý ⛔ không lộ khoá bigint nội bộ
            //   (`dtoKhongLoKhoaNoiBo`). Đọc tên trường thành "khoá nội bộ" rồi đi tìm một
            //   `publicId` không tồn tại là bẫy đã mắc ngay lượt typecheck đầu của trang này.
            options={(dsDiemDo.data ?? []).map((s) => ({
              value: s.id,
              label: `${s.code} — ${s.name}`,
            }))}
          />
          <Button
            icon={<ReloadOutlined />}
            loading={bieu.isFetching}
            disabled={!diemDo}
            onClick={() => void bieu.refetch()}
          >
            Làm mới
          </Button>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Chỉ hiển thị số đo HỢP LỆ"
        description={
          <>
            Bản ghi đang chờ duyệt ở màn hình <b>Dữ liệu nghi ngờ</b> ⛔ không được vẽ lên đây — một
            số đo mà hệ thống chưa tin thì trên đường cong ⛔ không phân biệt được với số đo tốt.
            Khoảng trống trên đường là quãng trạm ⛔ không gửi số về; nó được giữ nguyên, ⛔ không
            nội suy.
          </>
        }
      />

      {!diemDo ? (
        <Typography.Text type="secondary">
          Chọn một điểm đo để xem đường cong 24 giờ gần nhất.
        </Typography.Text>
      ) : (
        <BaseChart
          height={420}
          loading={bieu.isLoading}
          // ⛔ `empty` khai TƯỜNG MINH, ⛔ không suy từ `option` — một phép đoán sai làm biểu đồ CÓ
          //    dữ liệu hiện ra chữ "không có dữ liệu", tức hỏng theo chiều nguy hiểm hơn.
          empty={diem.length === 0}
          emptyText={bieu.data?.lyDoTrong ?? 'Chưa có số đo hợp lệ trong 24 giờ qua'}
          option={optionDuong(
            diem.map((d) => formatDateTime(d.moc)),
            [
              {
                ten: donVi
                  ? `${bieu.data?.tenChiSo ?? 'Mực nước'} (${donVi})`
                  : (bieu.data?.tenChiSo ?? 'Mực nước'),
                // ⚠ `Number()` chỉ ở ĐÂY, ở sát tầng vẽ: ECharts nhận số. Giá trị đi qua dây dưới
                //   dạng chuỗi (quy tắc 2) và ⛔ không được đổi sớm hơn — đổi ở tầng API là mở đường
                //   cho một phép cộng nào đó về sau chạy trên `double`.
                giaTri: diem.map((d) => Number(d.giaTri)),
                mauKhoa: 'normal',
              },
            ],
          )}
        />
      )}
    </Card>
  );
}
