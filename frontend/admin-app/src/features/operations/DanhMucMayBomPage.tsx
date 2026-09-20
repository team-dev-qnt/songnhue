import { DeleteOutlined, EditOutlined, UploadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  InputNumber,
  Popconfirm,
  Space,
  Table,
  Tooltip,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { ImportModal } from '@/components/business/ImportModal';
import { type CoMayView, type NhomMayView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { EMPTY_MARK, formatNumber } from '@/shared/format';

const GOC = '/ops/may-bom';

/**
 * Danh mục máy bơm của Báo cáo nhanh — 9 cỡ máy (cột Bảng 1) + nhóm máy từng trạm (dòng Bảng 2).
 *
 * ⛔ Nhóm máy CHỈ vào bằng đường nhập tệp (⛔ seed, ⛔ ô nhập tay từng dòng): danh mục của Công ty có
 * ~830 máy / 178 trạm, và ba sheet trong tệp gốc mâu thuẫn nhau — người nhập phải chọn sheet
 * `TB Tiêu (KH)` (OI-BC9).
 *
 * ⛔ Cỡ máy chỉ SỬA BIÊN, ⛔ thêm/xoá: số cột cố định theo mẫu Word. Công ty trả lời OI-BC8 ngày
 * 19/09/2026: nhãn cột là cỡ danh định ĐÃ LÀM TRÒN (máy 43.200 m³/h in ở cột "43"), giữ nguyên 9 cột và
 * xếp theo biên đã gửi — máy có Q nằm giữa hai nhãn (25.200 · 7.300 · 1.950) rơi vào cột theo biên ấy.
 */
export function DanhMucMayBomPage() {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const coSuaBien = hasPermission('ops:construction:update');
  const coNhap = hasPermission('ops:construction:create');
  const coXoa = hasPermission('ops:construction:update');

  const [moNhap, setMoNhap] = useState(false);
  const [bien, setBien] = useState<Record<
    string,
    { qTu: number | null; qDen: number | null }
  > | null>(null);

  const coMay = useQuery({
    queryKey: ['ops', 'may-bom', 'co-may'],
    queryFn: () => api.get<CoMayView[]>(`${GOC}/co-may`),
  });
  const nhomMay = useQuery({
    queryKey: ['ops', 'may-bom', 'nhom-may'],
    queryFn: () => api.get<NhomMayView[]>(`${GOC}/nhom-may`),
  });

  const luuBien = useMutation({
    mutationFn: (b: Record<string, { qTu: number | null; qDen: number | null }>) =>
      api.put<CoMayView[]>(`${GOC}/co-may`, {
        bien: Object.entries(b).map(([publicId, v]) => ({ publicId, qTu: v.qTu, qDen: v.qDen })),
      }),
    onSuccess: () => {
      message.success('Đã lưu biên cỡ máy');
      setBien(null);
      void queryClient.invalidateQueries({ queryKey: ['ops', 'may-bom'] });
    },
    onError: (caught: unknown) =>
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không lưu được biên cỡ máy',
      ),
  });

  const xoaNhom = useMutation({
    mutationFn: (publicId: string) => api.delete<void>(`${GOC}/nhom-may/${publicId}`),
    onSuccess: () => {
      message.success('Đã xoá nhóm máy');
      void queryClient.invalidateQueries({ queryKey: ['ops', 'may-bom', 'nhom-may'] });
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được nhóm máy'),
  });

  const batDauSua = () => {
    const ban: Record<string, { qTu: number | null; qDen: number | null }> = {};
    for (const c of coMay.data ?? []) {
      ban[c.publicId] = { qTu: c.qTu, qDen: c.qDen };
    }
    setBien(ban);
  };

  const oBien = (c: CoMayView, truong: 'qTu' | 'qDen') => {
    const giaTri = bien ? (bien[c.publicId]?.[truong] ?? null) : c[truong];
    if (!bien) {
      return giaTri === null ? '∞' : formatNumber(giaTri);
    }
    return (
      <InputNumber
        aria-label={`${truong === 'qTu' ? 'Cận dưới' : 'Cận trên'} của cỡ ${c.nhan}`}
        min={0}
        value={giaTri}
        placeholder="∞ (để trống)"
        onChange={(v) =>
          setBien((cu) =>
            cu ? { ...cu, [c.publicId]: { ...cu[c.publicId]!, [truong]: v ?? null } } : cu,
          )
        }
      />
    );
  };

  const cotCo: ColumnsType<CoMayView> = [
    { title: 'Cột Bảng 1 (1.000 m³/h)', dataIndex: 'nhan', width: 180 },
    { title: 'Từ Q (m³/h, gồm)', key: 'tu', width: 200, render: (_, c) => oBien(c, 'qTu') },
    {
      title: 'Đến Q (m³/h, không gồm)',
      key: 'den',
      width: 200,
      render: (_, c) => oBien(c, 'qDen'),
    },
  ];

  const cotNhom: ColumnsType<NhomMayView> = [
    {
      title: 'Xí nghiệp',
      dataIndex: 'tenDonVi',
      width: 200,
      render: (v: string | null) => v ?? EMPTY_MARK,
    },
    { title: 'Mã trạm', dataIndex: 'maCongTrinh', width: 140 },
    { title: 'Tên trạm', dataIndex: 'tenCongTrinh', width: 240 },
    { title: 'Số máy', dataIndex: 'soMay', width: 90, align: 'right' },
    {
      title: 'Q 1 máy (m³/h)',
      dataIndex: 'qMotMayM3h',
      width: 130,
      align: 'right',
      render: (v: number) => formatNumber(v),
    },
    { title: 'Cỡ (cột Bảng 1)', dataIndex: 'coMay', width: 130 },
    {
      title: 'Nguồn tưới, hướng tiêu',
      dataIndex: 'nguonTuoiHuongTieu',
      width: 220,
      render: (v: string | null) => v ?? EMPTY_MARK,
    },
    {
      title: '',
      key: 'xoa',
      width: 60,
      render: (_, n) => (
        <Popconfirm
          title={`Xoá nhóm ${n.soMay} máy × ${formatNumber(n.qMotMayM3h)} m³/h của ${n.tenCongTrinh}?`}
          description="Kỳ báo cáo đã chốt vẫn giữ nguyên số đã chụp."
          okText="Xoá"
          cancelText="Huỷ"
          disabled={!coXoa}
          onConfirm={() => xoaNhom.mutate(n.publicId)}
        >
          <Tooltip
            title={
              coXoa ? `Xoá nhóm máy của ${n.tenCongTrinh}` : 'Thiếu quyền ops:construction:update'
            }
          >
            <Button
              danger
              type="text"
              icon={<DeleteOutlined />}
              aria-label={`Xoá nhóm máy ${formatNumber(n.qMotMayM3h)} m³/h của ${n.tenCongTrinh}`}
              disabled={!coXoa}
            />
          </Tooltip>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title="Cỡ máy — 9 cột của Bảng 1 Báo cáo nhanh"
        loading={coMay.isLoading}
        extra={
          bien ? (
            <Space>
              <Button onClick={() => setBien(null)}>Huỷ</Button>
              <Button
                type="primary"
                loading={luuBien.isPending}
                onClick={() => luuBien.mutate(bien)}
              >
                Lưu biên
              </Button>
            </Space>
          ) : (
            <Button
              icon={<EditOutlined />}
              disabled={!coSuaBien}
              title={coSuaBien ? undefined : 'Thiếu quyền ops:construction:update'}
              onClick={batDauSua}
            >
              Sửa biên
            </Button>
          )
        }
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          title="Nhãn cột là cỡ máy danh định đã làm tròn — ví dụ máy 43.200 m³/h in ở cột “43”"
          description="Máy có Q nằm giữa hai nhãn được xếp theo biên dưới đây (Công ty xác nhận 19/09/2026). Biên phải liền nhau: cận dưới của cỡ trên = cận trên của cỡ dưới; cỡ lớn nhất để trống cận trên, cỡ nhỏ nhất để trống cận dưới."
        />
        <Table
          rowKey="publicId"
          size="small"
          columns={cotCo}
          dataSource={coMay.data ?? []}
          pagination={false}
          scroll={{ x: 580 }}
        />
      </Card>

      <Card
        title="Nhóm máy của từng trạm bơm"
        loading={nhomMay.isLoading}
        extra={
          <Button
            icon={<UploadOutlined />}
            disabled={!coNhap}
            title={coNhap ? undefined : 'Thiếu quyền ops:construction:create'}
            onClick={() => setMoNhap(true)}
          >
            Nhập từ tệp
          </Button>
        }
      >
        <Typography.Paragraph type="secondary">
          Mỗi dòng là một nhóm máy (số máy × lưu lượng một máy) — đúng một dòng của Bảng 2. Trạm
          phải có sẵn trong <b>Hồ sơ công trình</b> với loại <b>Trạm bơm</b>. Nhập lại cùng (trạm,
          Q) thì cập nhật số máy; nhóm vắng khỏi tệp <b>không</b> bị xoá.
        </Typography.Paragraph>
        <Table
          rowKey="publicId"
          size="small"
          columns={cotNhom}
          dataSource={nhomMay.data ?? []}
          pagination={{ pageSize: 50, showSizeChanger: false }}
          scroll={{ x: 1310 }}
          locale={{ emptyText: 'Chưa có nhóm máy nào — nhập từ tệp danh mục của Công ty' }}
        />
      </Card>

      <ImportModal
        open={moNhap}
        onClose={() => setMoNhap(false)}
        title="Nhập danh mục trạm bơm từ tệp bảng tính"
        moTa="Một dòng một nhóm máy. Trạm chưa có trong danh mục sẽ được TẠO — điền mã đơn vị và tên trạm; bỏ trống mã công trình thì hệ tự sinh. Dòng bỏ trống cả mã lẫn tên là nhóm máy thứ hai của trạm ngay trên."
        duongDan={{
          xemTruoc: '/ops/may-bom/nhom-may/nhap/xem-truoc',
          nhap: '/ops/may-bom/nhom-may/nhap',
          mau: '/ops/may-bom/nhom-may/mau-nhap',
        }}
        tenTepMau="mau-nhap-tram-bom.csv"
        khoaCanLamMoi={['ops', 'may-bom']}
      />
    </Space>
  );
}

export default DanhMucMayBomPage;
