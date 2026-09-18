import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { App, Button, Card, DatePicker, Modal, Space, Table, Tag, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { type BaoCaoNhanhKyView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { bayGio, formatDateTime, toApiInstant } from '@/shared/format';

import { NHAN_TRANG_THAI } from './baoCaoNhanhRules';

const GOC = '/ops/bao-cao-nhanh';

/** Khung mặc định của mẫu: 6h → 16h hôm nay (UTC+7). ⛔ `dayjs()` trần — giờ máy (T63.18). */
function khungMacDinh(): [Dayjs, Dayjs] {
  const homNay = bayGio().startOf('day');
  return [homNay.hour(6), homNay.hour(16)];
}

/**
 * Báo cáo nhanh — danh sách kỳ + tạo kỳ.
 *
 * Kỳ là một bản ghi CÓ VÒNG ĐỜI (Đang nhập → Đã chốt → Mở lại), ⛔ một mã trong danh mục "chọn mã →
 * tải CSV" của trang Báo cáo vận hành.
 */
export function BaoCaoNhanhPage() {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const coLap = hasPermission('ops:quick-report:manage');
  const [trang, setTrang] = useState(1);
  const [moTao, setMoTao] = useState(false);
  const [khung, setKhung] = useState<[Dayjs, Dayjs] | null>(null);

  const ds = useQuery({
    queryKey: ['ops', 'bao-cao-nhanh', 'ds', trang],
    queryFn: () => api.getPage<BaoCaoNhanhKyView>(GOC, { page: trang, size: 20 }),
  });

  const tao = useMutation({
    mutationFn: (k: [Dayjs, Dayjs]) =>
      api.post<BaoCaoNhanhKyView>(GOC, {
        tuThoiDiem: toApiInstant(k[0]),
        denThoiDiem: toApiInstant(k[1]),
      }),
    onSuccess: (ky) => {
      setMoTao(false);
      void navigate(`/van-hanh/bao-cao-nhanh/${ky.publicId}`);
    },
    onError: (caught: unknown) =>
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không tạo được kỳ báo cáo',
      ),
  });

  const cot: ColumnsType<BaoCaoNhanhKyView> = [
    {
      title: 'Kỳ báo cáo',
      key: 'khung',
      width: 340,
      render: (_, k) => (
        <Link to={`/van-hanh/bao-cao-nhanh/${k.publicId}`}>
          {formatDateTime(k.tuThoiDiem)} → {formatDateTime(k.denThoiDiem)}
        </Link>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'trangThai',
      width: 140,
      render: (t: BaoCaoNhanhKyView['trangThai']) => (
        <Tag color={NHAN_TRANG_THAI[t].mau}>{NHAN_TRANG_THAI[t].nhan}</Tag>
      ),
    },
    {
      title: 'Tạo lúc',
      dataIndex: 'createdAt',
      width: 180,
      render: (v: string) => formatDateTime(v),
    },
  ];

  return (
    <Card
      title="Báo cáo nhanh — ứng phó ngập lụt, úng"
      extra={
        <Button
          type="primary"
          icon={<PlusOutlined />}
          disabled={!coLap}
          title={coLap ? undefined : 'Thiếu quyền ops:quick-report:manage'}
          onClick={() => {
            setKhung(khungMacDinh());
            setMoTao(true);
          }}
        >
          Tạo kỳ báo cáo
        </Button>
      }
    >
      <Table
        rowKey="publicId"
        columns={cot}
        dataSource={ds.data?.items ?? []}
        loading={ds.isLoading}
        scroll={{ x: 660 }}
        pagination={{
          current: trang,
          pageSize: 20,
          total: ds.data?.meta.totalElements ?? 0,
          onChange: setTrang,
          showSizeChanger: false,
        }}
      />

      <Modal
        title="Tạo kỳ báo cáo"
        open={moTao}
        okText="Tạo"
        cancelText="Huỷ"
        confirmLoading={tao.isPending}
        okButtonProps={{ disabled: !khung }}
        onOk={() => khung && tao.mutate(khung)}
        onCancel={() => setMoTao(false)}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text>
            Từ … đến … (giờ Việt Nam) — in vào tiêu đề báo cáo, Bảng 3 lấy mực nước tại giờ kết
            thúc.
          </Typography.Text>
          <DatePicker.RangePicker
            showTime={{ format: 'HH:mm' }}
            format="DD/MM/YYYY HH:mm"
            value={khung}
            onChange={(v) => setKhung(v as [Dayjs, Dayjs] | null)}
            style={{ width: '100%' }}
          />
        </Space>
      </Modal>
    </Card>
  );
}

export default BaoCaoNhanhPage;
