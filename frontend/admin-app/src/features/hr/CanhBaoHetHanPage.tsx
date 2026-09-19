import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Space, Table, Tag, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';

import { api } from '@/shared/apiClient';

import { type CanhBaoHetHanView, type MucCanhBao, nhanConLai } from './hrVocabulary';

/**
 * Cảnh báo hợp đồng lao động và chứng chỉ sắp / ĐÃ hết hạn — CN-04.5 · M4.9 · T61.20.
 *
 * ⛔ Trước 14/09/2026 `GET /hr/canh-bao-het-han` có **0 nơi gọi** (`EndpointCoNoiGoiTest`), trong khi
 * kiểu `CanhBaoHetHanView` đã khai sẵn ở `hrVocabulary.ts` và CLAUDE.md ghi M4.9 là *"đã dựng"*. Thứ
 * duy nhất chạm tới là ô KPI `hopDongSapHetHan` của báo cáo nhân sự — một CON SỐ, ⛔ phải danh sách
 * người cần gia hạn (luật 27).
 *
 * ⚠ Ngưỡng ngày đọc TỪ API (`nguongNgay…`), ⛔ ghi cứng: người vận hành đổi `settings` thì tiêu đề
 * đổi theo. ⚠ `soNgayCon` ÂM là đã quá hạn — hiện *"Quá hạn N ngày"*, ⛔ kẹp về 0: số ngày quá hạn
 * quyết định việc nào làm trước. Backend đã sắp hạn sớm nhất lên đầu.
 */
export function CanhBaoHetHanPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['hr', 'canh-bao-het-han'],
    queryFn: () => api.get<CanhBaoHetHanView>('/hr/canh-bao-het-han'),
  });

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <div>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Cảnh báo hết hạn
        </Typography.Title>
        <Typography.Text type="secondary">
          Hợp đồng lao động và chứng chỉ, giấy tờ sắp hết hạn hoặc đã quá hạn
        </Typography.Text>
      </div>
      {/* T58.1 — hai người ở hai đơn vị thấy hai danh sách, và cả hai đều đúng: phải NÓI RA. */}
      <Alert
        type="info"
        showIcon
        title="Danh sách đã lọc theo phạm vi đơn vị của tài khoản đang đăng nhập."
      />
      {isError && <Alert type="error" showIcon title="Không tải được danh sách cảnh báo" />}
      <BangCanhBao
        tieuDe={
          data
            ? `Hợp đồng lao động hết hạn trong ${data.nguongNgayHopDong} ngày tới hoặc đã quá hạn`
            : 'Hợp đồng lao động'
        }
        muc={data?.hopDong}
        dangTai={isLoading}
        rong={data ? `Không có hợp đồng nào hết hạn trong ${data.nguongNgayHopDong} ngày tới` : ''}
      />
      <BangCanhBao
        tieuDe={
          data
            ? `Chứng chỉ, giấy tờ hết hiệu lực trong ${data.nguongNgayChungChi} ngày tới hoặc đã quá hạn`
            : 'Chứng chỉ, giấy tờ'
        }
        muc={data?.chungChi}
        dangTai={isLoading}
        rong={
          data
            ? `Không có chứng chỉ nào hết hiệu lực trong ${data.nguongNgayChungChi} ngày tới`
            : ''
        }
      />
    </Space>
  );
}

function BangCanhBao({
  tieuDe,
  muc,
  dangTai,
  rong,
}: {
  tieuDe: string;
  muc: MucCanhBao[] | undefined;
  dangTai: boolean;
  rong: string;
}) {
  const cot: ColumnsType<MucCanhBao> = [
    { title: 'Mã cán bộ', dataIndex: 'maCanBo', width: 120 },
    { title: 'Họ tên', dataIndex: 'hoTen', width: 220 },
    { title: 'Nội dung', dataIndex: 'moTa' },
    {
      title: 'Ngày hết hạn',
      dataIndex: 'hetHan',
      width: 130,
      render: (ngay: string) => dayjs(ngay).format('DD/MM/YYYY'),
    },
    {
      title: 'Còn lại',
      dataIndex: 'soNgayCon',
      width: 150,
      render: (so: number) => {
        const { chu, mau } = nhanConLai(so);
        return <Tag color={mau}>{chu}</Tag>;
      },
    },
  ];
  return (
    <Card size="small" title={<Typography.Text strong>{tieuDe}</Typography.Text>}>
      <Table<MucCanhBao>
        size="small"
        rowKey={(r) => `${r.hoSoPublicId}-${r.moTa}-${r.hetHan}`}
        columns={cot}
        dataSource={muc ?? []}
        loading={dangTai}
        pagination={false}
        scroll={{ x: 760 }}
        locale={{ emptyText: rong }}
      />
    </Card>
  );
}
