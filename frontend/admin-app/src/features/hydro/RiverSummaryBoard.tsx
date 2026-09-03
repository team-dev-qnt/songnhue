import { Card, Empty, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';

import { boCucTheoBeRong, cotThanhCss } from '@/components/dashboard/gridLayout';
import { useElementWidth } from '@/components/dashboard/useElementWidth';
import { type RiverGroup, type RiverStationRow } from '@/shared/api-types';
import { formatDateTime } from '@/shared/format';

import { TRANG_THAI_TIN_HIEU, VAI_TRO_VI_TRI } from './hydroVocabulary';

/**
 * **BC-11 — Biểu tổng hợp mực nước theo tuyến sông** (T34.4).
 *
 * ⭐⭐ **Làm một lần, dùng hai nơi.** Component này là *toàn bộ* phần hiển thị của BC-11, và nó
 * nhận `wall` để dùng lại nguyên vẹn trên **màn hình tường 4K** (WS-35). Dựng một bố cục thứ hai
 * cho wall mode là mở đường cho hai con số khác nhau về cùng một mực nước — và hai con số ấy sẽ
 * lệch nhau đúng vào ngày có sự cố.
 *
 * ⭐ Số cột lấy từ `boCucTheoBeRong` — **hạ tầng wall đã có**, kèm trần 3 cột kể cả ở 4K vì màn 85"
 * treo tường được đọc từ 4–6 m. ⛔ Đừng tự tính lại theo `window.innerWidth`: TV tường ⛔ không bao
 * giờ bắn `resize`, nên phép đo phải đến từ `ResizeObserver` (`useElementWidth`).
 *
 * ⛔⛔ **Điểm đo im lặng KHÔNG bị lọc bỏ.** Một trạm mất tín hiệu là đúng thứ biểu này sinh ra để
 * chỉ ra; ẩn nó đi là để lại một bảng sạch sẽ đúng lúc nó phải kêu. Ô số liệu rỗng **kèm lý do**,
 * và cột Tín hiệu nói ra *loại* im lặng — ba tình huống cần ba hành động khác nhau.
 */
export function RiverSummaryBoard({
  tuyen,
  wall = false,
  loading = false,
}: {
  tuyen: RiverGroup[];
  wall?: boolean;
  loading?: boolean;
}) {
  const { ref: khungRef, beRong } = useElementWidth<HTMLDivElement>();
  const { cotKhoi } = boCucTheoBeRong(beRong);

  const cot: ColumnsType<RiverStationRow> = [
    {
      title: 'Điểm đo',
      dataIndex: 'stationName',
      ellipsis: true,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <span>{row.stationName}</span>
          <Typography.Text type="secondary" style={{ fontSize: wall ? 14 : 12 }}>
            {VAI_TRO_VI_TRI[row.positionRole as keyof typeof VAI_TRO_VI_TRI] ?? row.positionRole}
            {row.chainage ? ` · ${row.chainage}` : ''}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Hiện tại',
      dataIndex: 'giaTri',
      width: wall ? 160 : 130,
      align: 'right',
      render: (v: string | null, row) =>
        v === null ? (
          oRong(row.lyDoTrong)
        ) : (
          <Space direction="vertical" size={0} align="end">
            <b style={{ fontSize: wall ? 22 : 14 }}>
              {v} {row.unit}
            </b>
            <Typography.Text type="secondary" style={{ fontSize: wall ? 13 : 11 }}>
              {row.mocDo ? formatDateTime(row.mocDo) : ''}
            </Typography.Text>
          </Space>
        ),
    },
    {
      title: 'Thấp / Cao trong ngày',
      dataIndex: 'minNgay',
      width: wall ? 200 : 165,
      align: 'right',
      render: (_, row) =>
        row.minNgay === null || row.maxNgay === null ? (
          oRong(row.soBanGhiNgay === 0 ? 'Hôm nay chưa có số đo hợp lệ nào' : row.lyDoTrong)
        ) : (
          <span>
            {row.minNgay} / {row.maxNgay}{' '}
            <Typography.Text type="secondary">{row.unit}</Typography.Text>
          </span>
        ),
    },
    {
      title: 'Tín hiệu',
      dataIndex: 'trangThaiTinHieu',
      width: wall ? 170 : 145,
      render: (v: RiverStationRow['trangThaiTinHieu'], row) => {
        const vt = TRANG_THAI_TIN_HIEU[v];
        return (
          <Tooltip
            title={`${vt?.giaiThich ?? ''}${row.mocTinHieu ? ` — bản ghi gần nhất ${formatDateTime(row.mocTinHieu)}` : ''}`}
          >
            <Tag color={vt?.color}>{vt?.label ?? v}</Tag>
          </Tooltip>
        );
      },
    },
    {
      title: (
        <Tooltip title="Lượng mưa chưa có nguồn: loại chỉ số đã khai nhưng chưa gắn cho điểm đo nào (mục G3-a). Ô để trống là câu trả lời đúng — 0 mm sẽ là một khẳng định về thời tiết.">
          Lượng mưa
        </Tooltip>
      ),
      dataIndex: 'luongMua',
      width: wall ? 140 : 120,
      align: 'right',
      // ⛔⛔ `luongMua` LUÔN null hôm nay. ⛔ Đừng `?? 0` — xem tooltip.
      render: (v: string | null, row) => (v === null ? oRong(row.lyDoLuongMua) : `${v} mm`),
    },
    {
      title: 'Tình hình vận hành',
      dataIndex: 'tinhHinhVanHanh',
      width: wall ? 240 : 210,
      render: (v: RiverStationRow['tinhHinhVanHanh'], row) =>
        v === null ? (
          oRong(row.lyDoTinhHinh)
        ) : (
          <Space direction="vertical" size={0}>
            {/* ⛔ Màu đến TỪ DỮ LIỆU (danh mục mã có CRUD, chốt G4) — ⛔ không có bảng ánh xạ
                mã → màu thứ hai ở FE, vì thêm mã mới không được đòi deploy. */}
            <Tag color={v.mau}>{v.ten}</Tag>
            {v.thamSo !== null ? (
              <Typography.Text type="secondary">
                {v.thamSo} {v.donViThamSo ?? ''}
              </Typography.Text>
            ) : null}
          </Space>
        ),
    },
  ];

  if (tuyen.length === 0 && !loading) {
    return (
      <Empty
        description="Chưa có điểm đo nào khai loại chỉ số — biểu chưa có hàng nào để dựng"
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    );
  }

  return (
    <div
      ref={khungRef}
      style={{
        display: 'grid',
        gridTemplateColumns: cotThanhCss(cotKhoi),
        gap: wall ? 20 : 16,
      }}
    >
      {tuyen.map((nhom) => (
        <Card
          key={nhom.tenTuyen}
          // `minWidth: 0` — điều kiện để thẻ co được trong lưới thay vì đẩy khung tràn ngang.
          style={{ minWidth: 0 }}
          size={wall ? 'default' : 'small'}
          title={
            <Space size={6}>
              <span>{nhom.tenTuyen}</span>
              {nhom.chuaPhanTuyen ? (
                <Tooltip title="Tuyến sông và lý trình của điểm đo thuộc mục G8 — Công ty chưa cung cấp. Nhóm này là nơi tạm của những điểm đo chưa khai, ⛔ không phải một tuyến sông có thật.">
                  <Tag>chờ G8</Tag>
                </Tooltip>
              ) : null}
              <Typography.Text type="secondary">{nhom.diemDo.length} điểm đo</Typography.Text>
            </Space>
          }
        >
          <Table
            rowKey={(r) => `${r.stationCode}|${r.measurementTypeCode}`}
            size="small"
            loading={loading}
            dataSource={nhom.diemDo}
            columns={cot}
            pagination={false}
            // ⚠ Cuộn TRONG thẻ, ⛔ không để thân trang cuộn ngang — cùng khuôn `ColumnHeaderRow`
            //   của cổng công khai.
            scroll={{ x: wall ? 1200 : 1050 }}
          />
        </Card>
      ))}
    </div>
  );
}

/**
 * ⛔⛔ Ô rỗng **kèm lý do** — quy tắc 16 ở tầng hiển thị.
 *
 * ⛔ `—` trần thì người đọc ⛔ không phân biệt được *"trạm chưa từng phát"* với *"trạm đã ngừng"*
 * với *"đang tải"* — ba tình huống cần ba hành động khác nhau.
 */
function oRong(lyDo: string | null) {
  return (
    <Tooltip title={lyDo ?? 'Chưa đo được'}>
      {/* ⚠ `sn-o-rong` + `data-ly-do`: bản in ⛔ không có tooltip, nên lý do phải đi vào DOM —
          xem khối `@media print` ở `admin-global.css` (T34.10). */}
      <Typography.Text
        type="secondary"
        className="sn-o-rong"
        data-ly-do={lyDo ?? 'Chưa đo được'}
        style={{ cursor: 'help' }}
      >
        —
      </Typography.Text>
    </Tooltip>
  );
}

export default RiverSummaryBoard;
