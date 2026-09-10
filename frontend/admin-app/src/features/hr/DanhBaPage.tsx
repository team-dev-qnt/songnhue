import {
  AppstoreOutlined,
  BarsOutlined,
  MailOutlined,
  PhoneOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Avatar,
  Card,
  Col,
  Drawer,
  Empty,
  Input,
  Pagination,
  Row,
  Segmented,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';

import { type OrgUnitNode } from '@/shared/api-types';
import { api } from '@/shared/apiClient';

import { ToSang } from './toSang';
import {
  GIOI_TINH,
  GIOI_TINH_OPTIONS,
  type DanhBaChiTietView,
  type DanhBaTrangView,
  type DanhBaView,
  type PositionView,
} from './hrVocabulary';

/** Đặc tả CN-04.6 chốt đích danh **300ms** — ⛔ không phải một con số tuỳ hứng. */
const TRE_GO_PHIM_MS = 300;

const CO_TRANG = 24;

/**
 * **Danh bạ nội bộ** — CN-04.6 (SRS M4.11).
 *
 * ## ⛔⛔ Màn hình này ⛔ KHÔNG phải màn hình Hồ sơ cán bộ, và khác ở ba chỗ
 *
 * | | Hồ sơ CBNV (CN-04.7) | Danh bạ (CN-04.6) |
 * |---|---|---|
 * | Quyền | `hr:employee:view` — 3/12 vai trò | `hr:directory:view` — **11/12** |
 * | Phạm vi | cắt theo đơn vị | ⛔ **toàn Công ty** |
 * | Trường | 27 + cờ 🔒 | **9** trường liên hệ công vụ |
 *
 * Backend là chốt chặn thật (`DanhBaRepository` viết danh sách cột **bằng tay** nên một trường mới
 * trên `Employee` ⛔ không tự chảy ra đây). Màn hình này chỉ hiển thị.
 *
 * ## ⛔ ⛔ Không có ảnh đại diện, và nói thẳng ra
 *
 * Đặc tả vẽ thẻ có **ảnh**. Đo 10/09/2026: `employees` ⛔ không có cột ảnh nào, và thư mục
 * `HoSoThuMuc.ANH` là *"ảnh trong hồ sơ nhân sự"* — có thể là bản chụp giấy tờ, ⛔ không phải ảnh
 * chân dung để công bố cho 200 người. ⇒ Thẻ hiện **chữ cái đầu**; nợ T55.4.
 */
export function DanhBaPage() {
  const [tuKhoaGo, setTuKhoaGo] = useState('');
  const [tuKhoa, setTuKhoa] = useState('');
  const [donVi, setDonVi] = useState<string[]>([]);
  const [chucVu, setChucVu] = useState<string[]>([]);
  const [gioiTinh, setGioiTinh] = useState<string[]>([]);
  const [trang, setTrang] = useState(0);
  const [kieuXem, setKieuXem] = useState<'the' | 'bang'>('the');
  const [dangMo, setDangMo] = useState<DanhBaView | null>(null);

  // Debounce 300ms — đặc tả chốt đích danh. ⛔ Không gọi API mỗi phím: 200 người × mỗi ký tự là
  // một câu truy vấn `LIKE '%…%'` quét bảng.
  useEffect(() => {
    const hen = setTimeout(() => {
      setTuKhoa(tuKhoaGo);
      setTrang(0);
    }, TRE_GO_PHIM_MS);
    return () => clearTimeout(hen);
  }, [tuKhoaGo]);

  const donViList = useQuery({
    queryKey: ['org-units', 'selectable'],
    queryFn: () => api.get<OrgUnitNode[]>('/org-units/selectable'),
    staleTime: 5 * 60_000,
  });

  const chucVuList = useQuery({
    queryKey: ['hr', 'positions', 'danh-ba'],
    queryFn: () => api.get<PositionView[]>('/hr/positions?active=true'),
    staleTime: 5 * 60_000,
  });

  const danhBa = useQuery({
    queryKey: ['hr', 'danh-ba', tuKhoa, donVi, chucVu, gioiTinh, trang],
    queryFn: () =>
      api.get<DanhBaTrangView>('/hr/danh-ba', {
        q: tuKhoa || undefined,
        donVi: donVi.length ? donVi : undefined,
        chucVu: chucVu.length ? chucVu : undefined,
        gioiTinh: gioiTinh.length ? gioiTinh : undefined,
        page: trang,
        size: CO_TRANG,
      }),
  });

  const muc = danhBa.data?.muc ?? [];
  const tong = danhBa.data?.tong ?? 0;

  const cotBang: ColumnsType<DanhBaView> = useMemo(
    () => [
      {
        title: 'Họ và tên',
        dataIndex: 'fullName',
        width: 220,
        render: (ten: string) => <ToSang van={ten} tuKhoa={tuKhoa} />,
      },
      { title: 'Mã NV', dataIndex: 'code', width: 130 },
      {
        title: 'Chức vụ',
        key: 'chucVu',
        width: 200,
        render: (_v, r) => r.positionName ?? r.jobTitle ?? '—',
      },
      { title: 'Đơn vị', dataIndex: 'orgUnitName', width: 200 },
      {
        title: 'Điện thoại',
        dataIndex: 'phone',
        width: 140,
        render: (v: string | null) => v ?? '—',
      },
      { title: 'Email cơ quan', dataIndex: 'workEmail', render: (v: string | null) => v ?? '—' },
    ],
    [tuKhoa],
  );

  return (
    <Space direction="vertical" size={16} style={{ display: 'flex' }}>
      <Card>
        <Row gutter={[12, 12]}>
          <Col xs={24} md={8}>
            <Input.Search
              allowClear
              placeholder="Tìm theo tên, mã NV hoặc chức danh — gõ không dấu cũng được"
              value={tuKhoaGo}
              onChange={(e) => setTuKhoaGo(e.target.value)}
            />
          </Col>
          <Col xs={24} sm={12} md={5}>
            <Select
              mode="multiple"
              allowClear
              style={{ width: '100%' }}
              placeholder="Đơn vị"
              value={donVi}
              onChange={(v) => {
                setDonVi(v);
                setTrang(0);
              }}
              loading={donViList.isLoading}
              optionFilterProp="label"
              options={(donViList.data ?? []).map((o) => ({ value: o.publicId, label: o.name }))}
            />
          </Col>
          <Col xs={24} sm={12} md={5}>
            <Select
              mode="multiple"
              allowClear
              style={{ width: '100%' }}
              placeholder="Chức vụ"
              value={chucVu}
              onChange={(v) => {
                setChucVu(v);
                setTrang(0);
              }}
              loading={chucVuList.isLoading}
              optionFilterProp="label"
              options={(chucVuList.data ?? []).map((p) => ({ value: p.publicId, label: p.name }))}
            />
          </Col>
          <Col xs={24} sm={12} md={3}>
            <Select
              mode="multiple"
              allowClear
              style={{ width: '100%' }}
              placeholder="Giới tính"
              value={gioiTinh}
              onChange={(v) => {
                setGioiTinh(v);
                setTrang(0);
              }}
              options={GIOI_TINH_OPTIONS}
            />
          </Col>
          <Col xs={24} sm={12} md={3}>
            <Segmented
              block
              value={kieuXem}
              onChange={(v) => setKieuXem(v as 'the' | 'bang')}
              options={[
                { value: 'the', icon: <AppstoreOutlined /> },
                { value: 'bang', icon: <BarsOutlined /> },
              ]}
            />
          </Col>
        </Row>
      </Card>

      <Card
        title={
          <Space>
            <TeamOutlined />
            <span>{danhBa.isLoading ? 'Đang tìm…' : `Tìm thấy ${tong} người`}</span>
          </Space>
        }
      >
        {danhBa.isLoading ? (
          <Skeleton active paragraph={{ rows: 6 }} />
        ) : muc.length === 0 ? (
          <Empty description="Không có ai khớp bộ lọc hiện tại" />
        ) : kieuXem === 'bang' ? (
          <Table<DanhBaView>
            columns={cotBang}
            dataSource={muc}
            rowKey="publicId"
            pagination={false}
            scroll={{ x: 1100 }}
            onRow={(r) => ({ onClick: () => setDangMo(r), style: { cursor: 'pointer' } })}
          />
        ) : (
          <Row gutter={[12, 12]}>
            {muc.map((m) => (
              <Col key={m.publicId} xs={24} sm={12} lg={8} xxl={6}>
                <TheDanhBa muc={m} tuKhoa={tuKhoa} onMo={() => setDangMo(m)} />
              </Col>
            ))}
          </Row>
        )}

        {tong > CO_TRANG ? (
          <div style={{ marginTop: 16, textAlign: 'right' }}>
            <Pagination
              current={trang + 1}
              pageSize={CO_TRANG}
              total={tong}
              showSizeChanger={false}
              onChange={(p) => setTrang(p - 1)}
            />
          </div>
        ) : null}
      </Card>

      <ChiTietDrawer muc={dangMo} onClose={() => setDangMo(null)} onMoNguoiKhac={setDangMo} />
    </Space>
  );
}

// =============================================================================

function TheDanhBa({ muc, tuKhoa, onMo }: { muc: DanhBaView; tuKhoa: string; onMo: () => void }) {
  return (
    <Card size="small" hoverable onClick={onMo} styles={{ body: { padding: 12 } }}>
      <Space align="start" size={12}>
        {/* ⛔ Chữ cái đầu, ⛔ KHÔNG phải ảnh — xem javadoc `DanhBaPage`, nợ T55.4. */}
        <Avatar size={44}>{chuDau(muc.fullName)}</Avatar>
        <Space direction="vertical" size={2} style={{ minWidth: 0 }}>
          <Typography.Text strong ellipsis>
            <ToSang van={muc.fullName} tuKhoa={tuKhoa} />
          </Typography.Text>
          <Typography.Text type="secondary" ellipsis style={{ fontSize: 12 }}>
            {muc.positionName ?? muc.jobTitle ?? '—'} · {muc.orgUnitName}
          </Typography.Text>
          <Space size={4} wrap>
            {muc.phone ? (
              <Tag icon={<PhoneOutlined />} bordered={false}>
                {muc.phone}
              </Tag>
            ) : null}
            {muc.workEmail ? (
              <Tag icon={<MailOutlined />} bordered={false}>
                {muc.workEmail}
              </Tag>
            ) : null}
          </Space>
        </Space>
      </Space>
    </Card>
  );
}

function ChiTietDrawer({
  muc,
  onClose,
  onMoNguoiKhac,
}: {
  muc: DanhBaView | null;
  onClose: () => void;
  onMoNguoiKhac: (m: DanhBaView) => void;
}) {
  const chiTiet = useQuery({
    queryKey: ['hr', 'danh-ba', 'chi-tiet', muc?.publicId],
    queryFn: () => api.get<DanhBaChiTietView>(`/hr/danh-ba/${muc?.publicId}`),
    enabled: muc !== null,
  });

  return (
    <Drawer
      open={muc !== null}
      onClose={onClose}
      width={480}
      // ⛔ Tháo hẳn cây con khi đóng — cùng bài học T51.12/T53.7. Ở đây `useQuery` đã mang
      //   `publicId` trong khoá truy vấn nên dữ liệu tự đổi, nhưng trạng thái cuộn và mọi state
      //   cục bộ thêm về sau thì ⛔ không.
      destroyOnHidden
      title={muc?.fullName ?? ''}
    >
      {chiTiet.isLoading || !chiTiet.data ? (
        <Skeleton active />
      ) : (
        <Space direction="vertical" size={16} style={{ display: 'flex' }}>
          <Space align="start" size={12}>
            <Avatar size={64}>{chuDau(chiTiet.data.muc.fullName)}</Avatar>
            <Space direction="vertical" size={2}>
              <Typography.Text strong>{chiTiet.data.muc.fullName}</Typography.Text>
              <Typography.Text type="secondary">
                {chiTiet.data.muc.positionName ?? chiTiet.data.muc.jobTitle ?? '—'}
              </Typography.Text>
              <Typography.Text type="secondary">
                Mã NV: {chiTiet.data.muc.code}
                {chiTiet.data.muc.gender ? ` · ${GIOI_TINH[chiTiet.data.muc.gender]}` : ''}
              </Typography.Text>
            </Space>
          </Space>

          <Space direction="vertical" size={6}>
            {chiTiet.data.muc.phone ? (
              <Typography.Link href={`tel:${chiTiet.data.muc.phone}`}>
                <PhoneOutlined /> {chiTiet.data.muc.phone}
              </Typography.Link>
            ) : null}
            {chiTiet.data.muc.workEmail ? (
              <Typography.Link href={`mailto:${chiTiet.data.muc.workEmail}`}>
                <MailOutlined /> {chiTiet.data.muc.workEmail}
              </Typography.Link>
            ) : null}
          </Space>

          <div>
            <Typography.Text type="secondary">Vị trí trên sơ đồ tổ chức</Typography.Text>
            <div style={{ marginTop: 4 }}>
              {chiTiet.data.duongDanDonVi.map((ten, i) => (
                <Tag key={ten + String(i)} bordered={false}>
                  {ten}
                </Tag>
              ))}
            </div>
          </div>

          <div>
            <Typography.Text type="secondary">
              Đồng nghiệp cùng đơn vị ({chiTiet.data.dongNghiep.length})
            </Typography.Text>
            <div style={{ marginTop: 8 }}>
              {chiTiet.data.dongNghiep.length === 0 ? (
                <Typography.Text type="secondary">Chưa có ai khác trong đơn vị này</Typography.Text>
              ) : (
                <Space direction="vertical" size={4} style={{ display: 'flex' }}>
                  {chiTiet.data.dongNghiep.map((d) => (
                    <Typography.Link key={d.publicId} onClick={() => onMoNguoiKhac(d)}>
                      {d.fullName} — {d.positionName ?? d.jobTitle ?? '—'}
                    </Typography.Link>
                  ))}
                </Space>
              )}
            </div>
          </div>
        </Space>
      )}
    </Drawer>
  );
}

function chuDau(hoTen: string): string {
  const phan = hoTen.trim().split(/\s+/);
  return (phan[phan.length - 1]?.[0] ?? '?').toUpperCase();
}
