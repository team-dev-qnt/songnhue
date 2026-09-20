import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { HopThoaiMaXacThuc } from '@/components/business/HopThoaiMaXacThuc';
import {
  type BiMatTinhTrangView,
  type LoaiBiMat,
  type MucCauHinhView,
  type TongQuanCauHinhView,
  type TrangThaiCauHinh,
} from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { formatDateTime } from '@/shared/format';

/**
 * Tình trạng cấu hình hệ thống + bí mật tích hợp — T61.41 / T61.44.
 *
 * <h3>Vì sao phần lớn các mục ở đây CHỈ XEM được</h3>
 *
 * Quyết định 15/09/2026 (architecture-review.md §12.1): biến cần trước khi có CSDL, biến do
 * nginx/Prometheus/Alertmanager đọc, kênh cảnh báo, gác chuyển hướng thư, SMTP và công tắc bảo mật
 * **phải ở `.env`**. Màn hình này nói cho người quản trị cao nhất biết cái nào đã đặt, cái nào thiếu
 * và thiếu thì hỏng gì — ⛔ bao giờ hiện giá trị.
 *
 * Thứ duy nhất ghi được là **bí mật tích hợp** (ứng dụng là người đọc duy nhất): ghi một chiều, mã
 * hoá AES-256-GCM, và mỗi lượt đặt/xoá phải nhập lại mã 2FA.
 */
export function CauHinhHeThongPage() {
  const { hasPermission } = useAuth();
  const tongQuan = useQuery({
    queryKey: ['system', 'cau-hinh'],
    queryFn: () => api.get<TongQuanCauHinhView>('/system/cau-hinh'),
    refetchInterval: 60_000,
  });

  if (tongQuan.error) {
    return (
      <Alert
        type="error"
        showIcon
        title={
          tongQuan.error instanceof ApiClientError
            ? tongQuan.error.message
            : 'Không đọc được tình trạng cấu hình'
        }
      />
    );
  }

  const muc = tongQuan.data?.muc ?? [];
  const soChan = tongQuan.data?.soChan ?? 0;
  const soCanhBao = tongQuan.data?.soCanhBao ?? 0;

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Card title="Tình trạng cấu hình" loading={tongQuan.isLoading}>
        {soChan > 0 && (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 12 }}
            title={`${soChan} mục CHẶN cần xử lý`}
            description="Thiếu hoặc sai ở những mục này là đang mất dữ liệu, mất chuông cảnh báo hoặc mở lỗ bảo mật."
          />
        )}
        {soChan === 0 && soCanhBao > 0 && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            title={`${soCanhBao} mục cần chú ý`}
          />
        )}
        {soChan === 0 && soCanhBao === 0 && !tongQuan.isLoading && (
          <Alert
            type="success"
            showIcon
            style={{ marginBottom: 12 }}
            title="Không mục nào đang thiếu — các mục 'Ngoài tầm nhìn' vẫn phải tự kiểm theo ghi chú"
          />
        )}
        <Typography.Paragraph type="secondary">
          Màn hình ⛔ bao giờ hiện giá trị cấu hình. Mục ở <b>.env</b> đổi trên máy chủ rồi tạo lại
          container; lý do từng mục không lên giao diện ghi ở cột Ghi chú.
        </Typography.Paragraph>
        <Table<MucCauHinhView>
          rowKey="ma"
          size="small"
          dataSource={muc}
          columns={COT_MUC}
          pagination={false}
          scroll={{ x: 1100 }}
        />
      </Card>

      <BangBiMat coQuyenGhi={hasPermission('adm:system-config:secret')} />
    </Space>
  );
}

const NHAN_TRANG_THAI: Record<TrangThaiCauHinh, { nhan: string; mau: string }> = {
  DAT: { nhan: 'Đã đặt', mau: 'success' },
  THIEU: { nhan: 'Thiếu', mau: 'error' },
  SAI: { nhan: 'Sai', mau: 'error' },
  NGOAI_TAM_NHIN: { nhan: 'Ngoài tầm nhìn', mau: 'processing' },
  KHONG_AP_DUNG: { nhan: 'Không áp dụng', mau: 'default' },
};

const COT_MUC: ColumnsType<MucCauHinhView> = [
  { title: 'Nhóm', dataIndex: 'nhom', width: 150 },
  {
    title: 'Mục',
    dataIndex: 'ten',
    width: 260,
    render: (ten: string, row) => (
      <Space orientation="vertical" size={0}>
        <Typography.Text strong>{ten}</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {row.nguoiDoc}
        </Typography.Text>
      </Space>
    ),
  },
  {
    title: 'Trạng thái',
    key: 'trang-thai',
    width: 150,
    render: (_v, row) => {
      const nhan = NHAN_TRANG_THAI[row.trangThai];
      // Thiếu/sai ở mục chỉ-thông-tin ⇒ màu dịu, ⛔ báo động giả.
      const mau =
        (row.trangThai === 'THIEU' || row.trangThai === 'SAI') && row.mucDo !== 'CHAN'
          ? 'warning'
          : nhan.mau;
      return (
        <Space size={4} wrap>
          <Tag color={mau}>{nhan.nhan}</Tag>
          {row.mucDo === 'CHAN' && (row.trangThai === 'THIEU' || row.trangThai === 'SAI') && (
            <Tag color="error">Chặn</Tag>
          )}
        </Space>
      );
    },
  },
  {
    title: 'Đặt ở đâu',
    dataIndex: 'datO',
    width: 260,
    render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
  },
  { title: 'Ghi chú', dataIndex: 'ghiChu' },
];

/** Bí mật tích hợp — ghi một chiều, ⛔ đọc lại giá trị. */
function BangBiMat({ coQuyenGhi }: { coQuyenGhi: boolean }) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [dangSua, setDangSua] = useState<BiMatTinhTrangView | null>(null);
  const [giaTri, setGiaTri] = useState('');
  const [choXacThuc, setChoXacThuc] = useState<
    { loai: 'dat'; ma: LoaiBiMat; giaTri: string } | { loai: 'xoa'; ma: LoaiBiMat } | null
  >(null);
  const [loiXacThuc, setLoiXacThuc] = useState<string | null>(null);

  const danhSach = useQuery({
    queryKey: ['system', 'cau-hinh', 'bi-mat'],
    queryFn: () => api.get<BiMatTinhTrangView[]>('/system/cau-hinh/bi-mat'),
  });

  const xong = async (cau: string) => {
    setChoXacThuc(null);
    setLoiXacThuc(null);
    setDangSua(null);
    setGiaTri('');
    message.success(cau);
    await queryClient.invalidateQueries({ queryKey: ['system', 'cau-hinh'] });
  };

  const loi = (caught: unknown, macDinh: string) => {
    if (
      caught instanceof ApiClientError &&
      (caught.code === 'ADM-2023' || caught.code === 'ADM-2024')
    ) {
      setLoiXacThuc(caught.message);
      return;
    }
    setChoXacThuc(null);
    setLoiXacThuc(null);
    message.error(caught instanceof ApiClientError ? caught.message : macDinh);
  };

  const dat = useMutation({
    mutationFn: (bien: { ma: LoaiBiMat; giaTri: string; maXacThuc: string }) =>
      api.put<BiMatTinhTrangView>(`/system/cau-hinh/bi-mat/${bien.ma}`, {
        giaTri: bien.giaTri,
        maXacThuc: bien.maXacThuc,
      }),
    onSuccess: () => xong('Đã lưu bí mật — mã hoá trong CSDL, có hiệu lực trong vòng 60 giây'),
    onError: (caught) => loi(caught, 'Không lưu được bí mật'),
  });

  const xoa = useMutation({
    mutationFn: (bien: { ma: LoaiBiMat; maXacThuc: string }) =>
      api.post<BiMatTinhTrangView>(`/system/cau-hinh/bi-mat/${bien.ma}/xoa`, {
        maXacThuc: bien.maXacThuc,
      }),
    onSuccess: () => xong('Đã xoá bí mật khỏi CSDL'),
    onError: (caught) => loi(caught, 'Không xoá được bí mật'),
  });

  const cot: ColumnsType<BiMatTinhTrangView> = [
    {
      title: 'Bí mật',
      dataIndex: 'ten',
      width: 280,
      render: (ten: string, row) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text strong>{ten}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.moTa}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Nguồn đang dùng',
      key: 'nguon',
      width: 220,
      render: (_v, row) => {
        if (!row.giaiMaDuoc) {
          return <Tag color="error">Không giải mã được — đặt lại</Tag>;
        }
        if (row.nguon === 'GIAO_DIEN') {
          return (
            <Space orientation="vertical" size={0}>
              <Tag color="success">Đặt trên giao diện</Tag>
              {row.capNhatLuc && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {formatDateTime(row.capNhatLuc)}
                </Typography.Text>
              )}
            </Space>
          );
        }
        return row.nguon === 'MOI_TRUONG' ? (
          <Tag color="warning">Giá trị mồi ở .env</Tag>
        ) : (
          <Tag>Chưa có</Tag>
        );
      },
    },
    {
      title: '',
      key: 'thao-tac',
      width: 180,
      render: (_v, row) =>
        coQuyenGhi ? (
          <Space size={0}>
            <Button type="link" onClick={() => setDangSua(row)}>
              {row.nguon === 'GIAO_DIEN' ? 'Thay' : 'Đặt'}
            </Button>
            <Tooltip
              title={
                row.coGiaTriMoi
                  ? 'Xoá xong thì giá trị mồi ở .env lại có hiệu lực — gỡ nó khỏi .env nếu muốn tắt hẳn'
                  : undefined
              }
            >
              <Button
                type="link"
                danger
                disabled={row.nguon !== 'GIAO_DIEN'}
                onClick={() => {
                  setLoiXacThuc(null);
                  setChoXacThuc({ loai: 'xoa', ma: row.loai });
                }}
              >
                Xoá
              </Button>
            </Tooltip>
          </Space>
        ) : null,
    },
  ];

  return (
    <Card title="Bí mật tích hợp" loading={danhSach.isLoading}>
      <Typography.Paragraph type="secondary">
        Chỉ những bí mật mà ứng dụng là người đọc <b>duy nhất</b>. Giá trị ⛔ bao giờ hiển thị lại —
        muốn đổi thì đặt giá trị mới. Mỗi lượt đặt/xoá phải nhập lại mã xác thực hai bước và để lại
        sự kiện bảo mật.
      </Typography.Paragraph>
      <Table<BiMatTinhTrangView>
        rowKey="loai"
        size="small"
        dataSource={danhSach.data ?? []}
        columns={cot}
        pagination={false}
        scroll={{ x: 700 }}
      />

      <Modal
        title={dangSua ? `Đặt ${dangSua.ten}` : ''}
        open={dangSua !== null}
        destroyOnHidden
        okText="Tiếp tục"
        okButtonProps={{ disabled: giaTri.trim() === '' }}
        onCancel={() => {
          setDangSua(null);
          setGiaTri('');
        }}
        onOk={() => {
          if (dangSua) {
            setLoiXacThuc(null);
            setChoXacThuc({ loai: 'dat', ma: dangSua.loai, giaTri: giaTri.trim() });
          }
        }}
      >
        <Typography.Paragraph type="secondary">
          Dán giá trị do nhà cung cấp cấp. Khoảng trắng/xuống dòng ở hai đầu được bỏ.
        </Typography.Paragraph>
        <Input.Password
          aria-label="Giá trị bí mật"
          autoComplete="off"
          maxLength={512}
          value={giaTri}
          onChange={(e) => setGiaTri(e.target.value)}
        />
      </Modal>

      <HopThoaiMaXacThuc
        open={choXacThuc !== null}
        title={choXacThuc?.loai === 'xoa' ? 'Xác nhận xoá bí mật' : 'Xác nhận lưu bí mật'}
        loi={loiXacThuc}
        dangGui={dat.isPending || xoa.isPending}
        onHuy={() => {
          setChoXacThuc(null);
          setLoiXacThuc(null);
        }}
        onXacNhan={(maXacThuc) => {
          if (choXacThuc?.loai === 'dat') {
            dat.mutate({ ma: choXacThuc.ma, giaTri: choXacThuc.giaTri, maXacThuc });
          } else if (choXacThuc?.loai === 'xoa') {
            xoa.mutate({ ma: choXacThuc.ma, maXacThuc });
          }
        }}
      />
    </Card>
  );
}
