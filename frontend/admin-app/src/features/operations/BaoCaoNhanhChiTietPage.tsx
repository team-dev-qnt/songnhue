import {
  ClockCircleOutlined,
  DownloadOutlined,
  SaveOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Input,
  InputNumber,
  Modal,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { ApprovalActions } from '@/components/business/ApprovalActions';
import {
  type BaoCaoNhanhChiTiet,
  type BcnChinO,
  type BcnDongBang4View,
  type BcnDongBang5View,
  type BcnMucNuocView,
  type BcnNhomView,
} from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import {
  APP_TIMEZONE,
  EMPTY_MARK,
  formatDateTime,
  formatNumber,
  toApiInstant,
} from '@/shared/format';
import { luuTep } from '@/shared/luuTep';

import {
  NHAN_TRANG_THAI,
  O_NHAP_XA,
  giaTriVanHanh,
  locBang2,
  nhapXaTu,
  payloadLuongMua,
  payloadNgapUng,
  payloadVanHanh,
  type NhapVanHanh,
  type NhapXa,
} from './baoCaoNhanhRules';

const GOC = '/ops/bao-cao-nhanh';
const LA_MA = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI', 'XII'];
const THIEU_QUYEN_NHAP = 'Thiếu quyền ops:quick-report:manage';

/** Ô số của văn bản: trống là trống (⛔ "0" — quy tắc 16). */
function so(v: number | null | undefined, le = 0): string {
  if (v === null || v === undefined) {
    return '';
  }
  // Số nguyên in ⛔ phần lẻ — cùng cách bản Word in (`SoVanBan.thapPhan`): 115, ⛔ 115,00.
  return formatNumber(v, Number.isInteger(v) ? 0 : le);
}

/**
 * Chi tiết một kỳ Báo cáo nhanh.
 *
 * ⛔ **Mọi con số dẫn xuất (Bảng 1, Mục 1, Mục 3, cột Cộng, ghi chú Yên Nghĩa) do backend tính** và trả
 * về sau mỗi lượt lưu — màn hình ⛔ cộng gì (quy tắc 3). Chuỗi một chiều: Bảng 2 → Bảng 1 → Mục 1.
 */
export function BaoCaoNhanhChiTietPage() {
  const { publicId = '' } = useParams<{ publicId: string }>();
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const khoa = ['ops', 'bao-cao-nhanh', publicId];

  const ct = useQuery({
    queryKey: khoa,
    queryFn: () => api.get<BaoCaoNhanhChiTiet>(`${GOC}/${publicId}`),
    enabled: publicId !== '',
  });

  const capNhat = (moi: BaoCaoNhanhChiTiet) => {
    queryClient.setQueryData(khoa, moi);
    void queryClient.invalidateQueries({ queryKey: ['ops', 'bao-cao-nhanh', 'ds'] });
  };

  const thucHien = async (action: string, lyDo?: string) => {
    const moi =
      action === 'MO_LAI'
        ? await api.post<BaoCaoNhanhChiTiet>(`${GOC}/${publicId}/mo-lai`, { lyDo })
        : await api.post<BaoCaoNhanhChiTiet>(`${GOC}/${publicId}/chot`);
    capNhat(moi);
  };

  const taiWord = async () => {
    try {
      const { blob, tenTep } = await api.getTep(`${GOC}/${publicId}/xuat`);
      luuTep(blob, tenTep ?? 'bao-cao-nhanh.docx');
    } catch (caught: unknown) {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không tải được bản Word');
    }
  };

  const c = ct.data;
  if (!c) {
    return <Card loading={ct.isLoading} title="Báo cáo nhanh" />;
  }
  const daChot = c.ky.trangThai === 'DA_CHOT';
  const coNhap = hasPermission('ops:quick-report:manage') && !daChot;
  const coXuat = hasPermission('ops:report:export');

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title={
          <Space wrap>
            Báo cáo nhanh
            <Tag color={NHAN_TRANG_THAI[c.ky.trangThai].mau}>
              {NHAN_TRANG_THAI[c.ky.trangThai].nhan}
            </Tag>
          </Space>
        }
        extra={
          <Space wrap>
            <SuaKhung c={c} coNhap={coNhap} onLuu={capNhat} />
            <ApprovalActions actions={c.hanhDong} onAction={thucHien} />
            <Button
              icon={<DownloadOutlined />}
              disabled={!coXuat}
              title={coXuat ? undefined : 'Thiếu quyền ops:report:export'}
              onClick={() => void taiWord()}
            >
              Tải bản Word
            </Button>
          </Space>
        }
      >
        <Descriptions size="small" column={{ xs: 1, md: 2 }}>
          <Descriptions.Item label="Từ">{formatDateTime(c.ky.tuThoiDiem)}</Descriptions.Item>
          <Descriptions.Item label="Đến">{formatDateTime(c.ky.denThoiDiem)}</Descriptions.Item>
        </Descriptions>
        {daChot ? (
          <Alert
            type="success"
            showIcon
            title="Kỳ đã chốt — mọi ô nhập đã khoá. Văn bản giữ đúng số của lúc chốt, kể cả khi danh mục máy bơm đổi sau đó."
          />
        ) : null}
        {c.ky.lyDoMoLai ? (
          <Alert
            style={{ marginTop: 8 }}
            type="warning"
            showIcon
            title={`Kỳ này đã được MỞ LẠI sau khi chốt — lý do: ${c.ky.lyDoMoLai}`}
          />
        ) : null}
      </Card>

      <Tabs
        items={[
          {
            key: 'bang2',
            label: 'Vận hành trạm bơm (Bảng 2)',
            children: (
              <TabVanHanh
                key={`${publicId}-${c.ky.trangThai}`}
                c={c}
                coNhap={coNhap}
                onLuu={capNhat}
              />
            ),
          },
          {
            key: 'bang5',
            label: 'Diện tích ngập úng (Bảng 5)',
            children: (
              <TabNgapUng
                key={`${publicId}-${c.ky.trangThai}`}
                c={c}
                coNhap={coNhap}
                onLuu={capNhat}
              />
            ),
          },
          {
            key: 'bang3',
            label: 'Mực nước & lượng mưa (Bảng 3–4)',
            children: (
              <TabMucNuoc
                key={`${publicId}-${c.ky.trangThai}`}
                c={c}
                coNhap={coNhap}
                onLuu={capNhat}
              />
            ),
          },
          { key: 'xem', label: 'Xem trước', children: <TabXemTruoc c={c} /> },
        ]}
      />
    </Space>
  );
}

// =============================================================================
// Sửa khung giờ — chỉ khi kỳ đang nhập
// =============================================================================

/**
 * Sửa "Từ … đến …" của kỳ. Bảng 3 đọc mực nước tại giờ KẾT THÚC, nên đổi khung là đổi Bảng 3 —
 * backend dựng lại toàn bộ nội dung và trả về.
 */
function SuaKhung({
  c,
  coNhap,
  onLuu,
}: {
  c: BaoCaoNhanhChiTiet;
  coNhap: boolean;
  onLuu: (moi: BaoCaoNhanhChiTiet) => void;
}) {
  const { message } = App.useApp();
  const [khung, setKhung] = useState<[Dayjs, Dayjs] | null>(null);

  const luu = useMutation({
    mutationFn: (k: [Dayjs, Dayjs]) =>
      api.put<BaoCaoNhanhChiTiet>(`${GOC}/${c.ky.publicId}/khung`, {
        tuThoiDiem: toApiInstant(k[0]),
        denThoiDiem: toApiInstant(k[1]),
      }),
    onSuccess: (moi) => {
      setKhung(null);
      onLuu(moi);
      message.success('Đã đổi khung giờ — Bảng 3 đã đọc lại mực nước tại giờ kết thúc mới');
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không đổi được khung giờ'),
  });

  return (
    <>
      <Button
        icon={<ClockCircleOutlined />}
        disabled={!coNhap}
        title={coNhap ? undefined : THIEU_QUYEN_NHAP}
        onClick={() =>
          setKhung([
            dayjs(c.ky.tuThoiDiem).tz(APP_TIMEZONE),
            dayjs(c.ky.denThoiDiem).tz(APP_TIMEZONE),
          ])
        }
      >
        Sửa khung giờ
      </Button>
      <Modal
        title="Sửa khung giờ kỳ báo cáo"
        open={khung !== null}
        okText="Lưu"
        cancelText="Huỷ"
        confirmLoading={luu.isPending}
        onOk={() => khung && luu.mutate(khung)}
        onCancel={() => setKhung(null)}
      >
        <DatePicker.RangePicker
          showTime={{ format: 'HH:mm' }}
          format="DD/MM/YYYY HH:mm"
          value={khung}
          onChange={(v) => setKhung(v as [Dayjs, Dayjs] | null)}
          style={{ width: '100%' }}
        />
      </Modal>
    </>
  );
}

// =============================================================================
// Tab 1 — Bảng 2
// =============================================================================

type DongBang2 =
  | { key: string; loai: 'khoi'; so: string; ten: string; tongMay: number }
  | { key: string; loai: 'nhom'; so: string; ten: string; nguon: string | null; nhom: BcnNhomView };

function TabVanHanh({
  c,
  coNhap,
  onLuu,
}: {
  c: BaoCaoNhanhChiTiet;
  coNhap: boolean;
  onLuu: (moi: BaoCaoNhanhChiTiet) => void;
}) {
  const { message } = App.useApp();
  const [nhap, setNhap] = useState<NhapVanHanh>({});
  const [chiHoatDong, setChiHoatDong] = useState(false);
  const [tuKhoa, setTuKhoa] = useState('');

  const payload = payloadVanHanh(c.bang2, nhap);
  const luu = useMutation({
    mutationFn: () => api.put<BaoCaoNhanhChiTiet>(`${GOC}/${c.ky.publicId}/van-hanh`, payload),
    onSuccess: (moi) => {
      setNhap({});
      onLuu(moi);
      message.success('Đã lưu Bảng 2 — Bảng 1 và Mục 1 đã tính lại');
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được Bảng 2'),
  });

  const dong = useMemo<DongBang2[]>(() => {
    const ket: DongBang2[] = [];
    const bang = locBang2(c.bang2, chiHoatDong, tuKhoa, nhap);
    bang.forEach((khoi, i) => {
      ket.push({
        key: `k${i}`,
        loai: 'khoi',
        so: LA_MA[i] ?? String(i + 1),
        ten: khoi.tenDonVi ?? '',
        tongMay: khoi.tongMayThietKe,
      });
      khoi.tram.forEach((tram, j) => {
        tram.nhom.forEach((n, k) => {
          ket.push({
            key: n.nhomMayPublicId,
            loai: 'nhom',
            so: k === 0 ? String(j + 1) : '',
            ten: k === 0 ? tram.ten : '',
            nguon: k === 0 ? tram.nguonTuoiHuongTieu : null,
            nhom: n,
          });
        });
      });
    });
    return ket;
  }, [c.bang2, chiHoatDong, tuKhoa, nhap]);

  const cot: ColumnsType<DongBang2> = [
    { title: 'TT', dataIndex: 'so', width: 60 },
    {
      title: 'Tên công trình',
      key: 'ten',
      width: 260,
      render: (_, d) => (d.loai === 'khoi' ? <b>{d.ten}</b> : d.ten),
    },
    {
      title: 'Tổng số máy',
      key: 'tongMay',
      width: 100,
      align: 'right',
      render: (_, d) => (d.loai === 'khoi' ? <b>{d.tongMay}</b> : d.nhom.soMayThietKe),
    },
    {
      title: 'Q 1 máy (m³/h)',
      key: 'q',
      width: 120,
      align: 'right',
      render: (_, d) => (d.loai === 'nhom' ? formatNumber(d.nhom.qMotMayM3h) : null),
    },
    {
      title: 'Cỡ',
      key: 'co',
      width: 90,
      render: (_, d) => (d.loai === 'nhom' ? d.nhom.coMay : null),
    },
    {
      title: 'Tình hình vận hành (số máy chạy)',
      key: 'chay',
      width: 170,
      render: (_, d) =>
        d.loai === 'nhom' ? (
          <InputNumber
            aria-label={`Số máy đang chạy — ${d.ten || 'nhóm máy'} ${formatNumber(d.nhom.qMotMayM3h)} m³/h`}
            min={0}
            max={d.nhom.soMayThietKe}
            precision={0}
            disabled={!coNhap}
            title={coNhap ? undefined : THIEU_QUYEN_NHAP}
            value={giaTriVanHanh(nhap, d.nhom.nhomMayPublicId, d.nhom.soMayVanHanh)}
            onChange={(v) => setNhap((cu) => ({ ...cu, [d.nhom.nhomMayPublicId]: v ?? null }))}
          />
        ) : null,
    },
    {
      title: 'Nguồn tưới, hướng tiêu',
      key: 'nguon',
      width: 220,
      render: (_, d) => (d.loai === 'nhom' ? (d.nguon ?? '') : null),
    },
  ];

  return (
    <Card>
      <Space wrap style={{ marginBottom: 12 }}>
        <Input.Search
          allowClear
          placeholder="Tìm trạm theo tên"
          aria-label="Tìm trạm theo tên"
          onChange={(e) => setTuKhoa(e.target.value)}
          style={{ width: 260 }}
        />
        <Space>
          <Switch
            checked={chiHoatDong}
            onChange={setChiHoatDong}
            aria-label="Chỉ hiện trạm đang hoạt động"
          />
          <Typography.Text>Chỉ hiện trạm đang hoạt động</Typography.Text>
        </Space>
        <Button
          type="primary"
          icon={<SaveOutlined />}
          disabled={!coNhap || payload.o.length === 0}
          title={coNhap ? undefined : THIEU_QUYEN_NHAP}
          loading={luu.isPending}
          onClick={() => luu.mutate()}
        >
          Lưu {payload.o.length > 0 ? `(${payload.o.length} ô)` : ''}
        </Button>
      </Space>
      <Typography.Paragraph type="secondary">
        Chỉ nhập <b>số máy đang chạy</b> của từng nhóm máy. Ô trống = chưa nhập (khác 0). Công tắc
        chỉ lọc hiển thị — Bảng 1 vẫn tính đủ mọi trạm.
      </Typography.Paragraph>
      <Table
        rowKey="key"
        size="small"
        columns={cot}
        dataSource={dong}
        pagination={false}
        scroll={{ x: 1020, y: 620 }}
        locale={{ emptyText: 'Chưa có nhóm máy nào — nhập danh mục ở màn hình Danh mục máy bơm' }}
      />
    </Card>
  );
}

// =============================================================================
// Tab 2 — Bảng 5
// =============================================================================

function TabNgapUng({
  c,
  coNhap,
  onLuu,
}: {
  c: BaoCaoNhanhChiTiet;
  coNhap: boolean;
  onLuu: (moi: BaoCaoNhanhChiTiet) => void;
}) {
  const { message } = App.useApp();
  const [nhap, setNhap] = useState<Record<string, NhapXa>>({});
  const payload = payloadNgapUng(c.bang5, nhap);

  const luu = useMutation({
    mutationFn: () => api.put<BaoCaoNhanhChiTiet>(`${GOC}/${c.ky.publicId}/ngap-ung`, payload),
    onSuccess: (moi) => {
      setNhap({});
      onLuu(moi);
      message.success('Đã lưu Bảng 5 — Mục 3 đã tính lại');
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được Bảng 5'),
  });

  const oNhap = (x: BcnDongBang5View, truong: keyof NhapXa, nhan: string) => {
    const ban = nhap[x.xaPublicId] ?? nhapXaTu(x.o);
    return (
      <InputNumber
        aria-label={`${nhan} — ${x.ten}`}
        min={0}
        decimalSeparator=","
        disabled={!coNhap}
        title={coNhap ? undefined : THIEU_QUYEN_NHAP}
        value={ban[truong]}
        onChange={(v) =>
          setNhap((cu) => ({
            ...cu,
            [x.xaPublicId]: { ...(cu[x.xaPublicId] ?? nhapXaTu(x.o)), [truong]: v ?? null },
          }))
        }
      />
    );
  };

  type Dong = { key: string; tt: string; ten: string; xa: BcnDongBang5View | null; o: BcnChinO };
  const dong: Dong[] = [
    { key: 'iii', tt: 'III', ten: 'Công ty TL Sông Nhuệ', xa: null, o: c.bang5CongTy },
    ...c.bang5.map((x) => ({ key: x.xaPublicId, tt: String(x.thuTu), ten: x.ten, xa: x, o: x.o })),
  ];

  const nhapHoacSo = (d: Dong, truong: keyof NhapXa, nhan: string) =>
    d.xa ? oNhap(d.xa, truong, nhan) : <b>{so(d.o[truong], 2)}</b>;
  const cong = (v: number | null) => so(v, 2);

  const cot: ColumnsType<Dong> = [
    { title: 'TT', dataIndex: 'tt', width: 60 },
    {
      title: 'Địa phương',
      key: 'ten',
      width: 200,
      render: (_, d) => (d.xa ? d.ten : <b>{d.ten}</b>),
    },
    {
      title: 'Ngập trắng (ha)',
      children: [
        {
          title: 'Lúa',
          key: 'ntl',
          width: 120,
          render: (_, d) => nhapHoacSo(d, 'ngapTrangLua', 'Ngập trắng — lúa'),
        },
        {
          title: 'Rau, màu',
          key: 'ntr',
          width: 120,
          render: (_, d) => nhapHoacSo(d, 'ngapTrangRau', 'Ngập trắng — rau màu'),
        },
        { title: 'Cộng', key: 'ntc', width: 90, render: (_, d) => cong(d.o.ngapTrangCong) },
      ],
    },
    {
      title: 'Sâu nước (ha)',
      children: [
        {
          title: 'Lúa',
          key: 'snl',
          width: 120,
          render: (_, d) => nhapHoacSo(d, 'sauNuocLua', 'Sâu nước — lúa'),
        },
        {
          title: 'Rau, màu',
          key: 'snr',
          width: 120,
          render: (_, d) => nhapHoacSo(d, 'sauNuocRau', 'Sâu nước — rau màu'),
        },
        { title: 'Cộng', key: 'snc', width: 90, render: (_, d) => cong(d.o.sauNuocCong) },
      ],
    },
    {
      title: 'Tổng cộng (ha)',
      children: [
        { title: 'Lúa', key: 'tl', width: 90, render: (_, d) => cong(d.o.tongLua) },
        { title: 'Rau, màu', key: 'tr', width: 90, render: (_, d) => cong(d.o.tongRau) },
        { title: 'Cộng', key: 'tc', width: 90, render: (_, d) => cong(d.o.tongCong) },
      ],
    },
  ];

  return (
    <Card>
      <Space style={{ marginBottom: 12 }}>
        <Button
          type="primary"
          icon={<SaveOutlined />}
          disabled={!coNhap || payload.dong.length === 0}
          title={coNhap ? undefined : THIEU_QUYEN_NHAP}
          loading={luu.isPending}
          onClick={() => luu.mutate()}
        >
          Lưu {payload.dong.length > 0 ? `(${payload.dong.length} xã)` : ''}
        </Button>
        <Typography.Text type="secondary">
          Nhập {O_NHAP_XA.length} ô mỗi xã; cột “Cộng”, “Tổng cộng” và dòng III do hệ thống tính. Ba
          công ty thuỷ lợi còn lại để trống.
        </Typography.Text>
      </Space>
      <Table
        rowKey="key"
        size="small"
        bordered
        columns={cot}
        dataSource={dong}
        pagination={false}
        scroll={{ x: 1190 }}
      />
    </Card>
  );
}

// =============================================================================
// Tab 3 — Bảng 3 (tự động) + Bảng 4 (nhập tay)
// =============================================================================

const DUONG_CAU_HINH = '/van-hanh/bao-cao-nhanh/cau-hinh';

function TabMucNuoc({
  c,
  coNhap,
  onLuu,
}: {
  c: BaoCaoNhanhChiTiet;
  coNhap: boolean;
  onLuu: (moi: BaoCaoNhanhChiTiet) => void;
}) {
  type Dong = { key: string; cong: string; lyTrinh: string; o: BcnMucNuocView };
  const dong: Dong[] = c.bang3.flatMap((d, i) => [
    { key: `${i}tl`, cong: d.nhanCong, lyTrinh: d.lyTrinh, o: d.tl },
    { key: `${i}hl`, cong: '', lyTrinh: '', o: d.hl },
  ]);
  const cot: ColumnsType<Dong> = [
    { title: 'Cống', dataIndex: 'cong', width: 140 },
    { title: 'Lý trình', dataIndex: 'lyTrinh', width: 120 },
    { title: 'Vế', key: 've', width: 120, render: (_, d) => d.o.nhan },
    {
      title: `Mực nước hồi ${formatDateTime(c.ky.denThoiDiem)} (m)`,
      key: 'gt',
      width: 200,
      align: 'right',
      render: (_, d) => (d.o.giaTriM === null ? EMPTY_MARK : formatNumber(d.o.giaTriM, 2)),
    },
    {
      title: 'Ghi chú',
      key: 'gc',
      width: 360,
      render: (_, d) =>
        d.o.lyDo ? (
          <Typography.Text type="secondary">{d.o.lyDo}</Typography.Text>
        ) : d.o.dungMoc ? null : (
          <Typography.Text type="warning">
            Số đo gần nhất lúc {formatDateTime(d.o.mocDo)}
          </Typography.Text>
        ),
    },
  ];
  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title="Bảng 3 — Mực nước hệ thống sông Nhuệ (cống)"
        extra={
          <Link to={DUONG_CAU_HINH}>
            <SettingOutlined /> Cấu hình cống & điểm đo
          </Link>
        }
      >
        <Typography.Paragraph type="secondary">
          Giá trị tức thời tại giờ kết thúc kỳ — số đo HỢP LỆ gần nhất trước mốc, trong vòng 24 giờ.
          Tự động từ dữ liệu thuỷ văn, không nhập tay. Ô trống thì cột “Ghi chú” nói vì sao — gắn
          công trình cho cống ở màn hình Cấu hình, liên kết điểm đo với công trình ở màn hình Điểm
          đo.
        </Typography.Paragraph>
        <Table
          rowKey="key"
          size="small"
          columns={cot}
          dataSource={dong}
          pagination={false}
          scroll={{ x: 940 }}
        />
      </Card>
      <Bang4 c={c} coNhap={coNhap} onLuu={onLuu} />
    </Space>
  );
}

/**
 * Bảng 4 — lượng mưa NHẬP TAY (mm) của 8 điểm Sông Nhuệ. Nguồn tự động (G3-a) chưa có; ô để trống
 * thì bản Word để trống, ⛔ in 0.
 */
function Bang4({
  c,
  coNhap,
  onLuu,
}: {
  c: BaoCaoNhanhChiTiet;
  coNhap: boolean;
  onLuu: (moi: BaoCaoNhanhChiTiet) => void;
}) {
  const { message } = App.useApp();
  const [nhap, setNhap] = useState<Record<string, number | null>>({});
  const payload = payloadLuongMua(c.bang4, nhap);

  const luu = useMutation({
    mutationFn: () => api.put<BaoCaoNhanhChiTiet>(`${GOC}/${c.ky.publicId}/luong-mua`, payload),
    onSuccess: (moi) => {
      setNhap({});
      onLuu(moi);
      message.success('Đã lưu Bảng 4');
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được Bảng 4'),
  });

  const cot: ColumnsType<BcnDongBang4View> = [
    { title: 'STT', dataIndex: 'thuTu', width: 60 },
    { title: 'Điểm đo mưa', dataIndex: 'ten', width: 200 },
    {
      title: `Lượng mưa ${formatDateTime(c.ky.tuThoiDiem)} → ${formatDateTime(c.ky.denThoiDiem)} (mm)`,
      key: 'mm',
      width: 320,
      render: (_, d) => (
        <InputNumber
          aria-label={`Lượng mưa (mm) — ${d.ten}`}
          min={0}
          step={0.1}
          decimalSeparator=","
          disabled={!coNhap}
          title={coNhap ? undefined : THIEU_QUYEN_NHAP}
          value={d.diemMuaPublicId in nhap ? nhap[d.diemMuaPublicId] : d.luongMuaMm}
          onChange={(v) => setNhap((cu) => ({ ...cu, [d.diemMuaPublicId]: v ?? null }))}
        />
      ),
    },
  ];

  return (
    <Card title="Bảng 4 — Lượng mưa (nhập tay)">
      <Space style={{ marginBottom: 12 }} wrap>
        <Button
          type="primary"
          icon={<SaveOutlined />}
          disabled={!coNhap || payload.o.length === 0}
          title={coNhap ? undefined : THIEU_QUYEN_NHAP}
          loading={luu.isPending}
          onClick={() => luu.mutate()}
        >
          Lưu {payload.o.length > 0 ? `(${payload.o.length} điểm)` : ''}
        </Button>
        <Typography.Text type="secondary">
          Chưa có nguồn lượng mưa tự động (G3-a) — nhập tay theo kỳ. Ô để trống thì bản Word để
          trống; 30 điểm của ba công ty còn lại để trống.
        </Typography.Text>
      </Space>
      <Table
        rowKey="diemMuaPublicId"
        size="small"
        columns={cot}
        dataSource={c.bang4}
        pagination={false}
        scroll={{ x: 580 }}
      />
    </Card>
  );
}

// =============================================================================
// Tab 4 — Xem trước (chỉ đọc)
// =============================================================================

function TabXemTruoc({ c }: { c: BaoCaoNhanhChiTiet }) {
  const b1 = c.bang1SongNhue;
  type Dong1 = {
    key: string;
    congTy: string;
    tram: string;
    may: string;
    co: string[];
    luuLuong: string;
  };
  const dongBang1: Dong1[] = [
    {
      key: 'sn',
      congTy: 'Sông Nhuệ',
      tram: so(b1?.tongTram),
      may: so(b1?.tongMay),
      co: c.coMay.map((_, i) => (b1 && b1.theoCo[i] ? formatNumber(b1.theoCo[i]) : '')),
      luuLuong: so(b1?.tongLuuLuongM3h),
    },
  ];
  const cotBang1: ColumnsType<Dong1> = [
    { title: 'Công ty thuỷ lợi', dataIndex: 'congTy', width: 140 },
    { title: 'Tổng số trạm', dataIndex: 'tram', width: 100, align: 'right' },
    { title: 'Tổng số máy', dataIndex: 'may', width: 100, align: 'right' },
    {
      title: 'Trong đó loại máy bơm (1.000 m³/h)',
      children: c.coMay.map((nhan, i) => ({
        title: nhan,
        key: `co${i}`,
        width: 60,
        align: 'right' as const,
        render: (_: unknown, d: Dong1) => d.co[i],
      })),
    },
    { title: 'Tổng lưu lượng (m³/h)', dataIndex: 'luuLuong', width: 150, align: 'right' },
  ];

  const yn = c.ghiChuYenNghia;
  const muc3 = c.muc3;

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Card title="Mục 1 — Tình hình vận hành trạm bơm (dòng Sông Nhuệ, lấy từ Bảng 1)">
        <Descriptions size="small" column={{ xs: 1, md: 3 }} bordered>
          <Descriptions.Item label="Tổng số trạm">
            {so(c.muc1.tongTram) || EMPTY_MARK}
          </Descriptions.Item>
          <Descriptions.Item label="Tổng số máy">
            {so(c.muc1.tongMay) || EMPTY_MARK}
          </Descriptions.Item>
          <Descriptions.Item label="Tổng lưu lượng (m³/h)">
            {so(c.muc1.tongLuuLuongM3h) || EMPTY_MARK}
          </Descriptions.Item>
        </Descriptions>
        <Typography.Paragraph style={{ marginTop: 12, marginBottom: 0 }}>
          <b>Ghi chú:</b>{' '}
          {yn.cau ?? (
            <Typography.Text type="warning">
              {yn.trangThai === 'CHUA_NHAP'
                ? 'Chưa nhập số máy chạy của Trạm bơm Yên Nghĩa — bản Word giữ nguyên dấu “…” của mẫu.'
                : yn.trangThai === 'CHUA_GAN_TRAM'
                  ? 'Chưa chọn công trình cho Trạm bơm Yên Nghĩa (màn hình Cấu hình) — bản Word giữ nguyên dấu “…”.'
                  : 'Danh mục máy bơm chưa có nhóm máy nào của trạm gắn cho Yên Nghĩa — bản Word giữ nguyên dấu “…”.'}
            </Typography.Text>
          )}
        </Typography.Paragraph>
      </Card>

      <Card title="Bảng 1 — Tổng hợp theo cỡ máy">
        <Table
          rowKey="key"
          size="small"
          bordered
          columns={cotBang1}
          dataSource={dongBang1}
          pagination={false}
          scroll={{ x: 1030 }}
        />
        <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
          Ba công ty thuỷ lợi còn lại và dòng “Tổng cộng” để trống — hệ thống chỉ có số của Sông
          Nhuệ.
        </Typography.Paragraph>
      </Card>

      <Card title="Mục 3 — Diện tích ngập úng (dòng Sông Nhuệ, lấy từ Bảng 5)">
        <Descriptions size="small" column={{ xs: 1, md: 3 }} bordered>
          <Descriptions.Item label="Ngập trắng — lúa">
            {so(muc3.ngapTrangLua, 2) || EMPTY_MARK}
          </Descriptions.Item>
          <Descriptions.Item label="Ngập trắng — rau màu">
            {so(muc3.ngapTrangRau, 2) || EMPTY_MARK}
          </Descriptions.Item>
          <Descriptions.Item label="Ngập trắng — cộng">
            {so(muc3.ngapTrangCong, 2) || EMPTY_MARK}
          </Descriptions.Item>
          <Descriptions.Item label="Sâu nước — lúa">
            {so(muc3.sauNuocLua, 2) || EMPTY_MARK}
          </Descriptions.Item>
          <Descriptions.Item label="Sâu nước — rau màu">
            {so(muc3.sauNuocRau, 2) || EMPTY_MARK}
          </Descriptions.Item>
          <Descriptions.Item label="Sâu nước — cộng">
            {so(muc3.sauNuocCong, 2) || EMPTY_MARK}
          </Descriptions.Item>
          <Descriptions.Item label="Tổng — lúa">
            {so(muc3.tongLua, 2) || EMPTY_MARK}
          </Descriptions.Item>
          <Descriptions.Item label="Tổng — rau màu">
            {so(muc3.tongRau, 2) || EMPTY_MARK}
          </Descriptions.Item>
          <Descriptions.Item label="Tổng — cộng">
            {so(muc3.tongCong, 2) || EMPTY_MARK}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </Space>
  );
}

export default BaoCaoNhanhChiTietPage;
