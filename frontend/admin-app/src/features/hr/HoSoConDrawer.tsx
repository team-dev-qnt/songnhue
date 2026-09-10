import { DeleteOutlined, EditOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Drawer,
  Empty,
  Popconfirm,
  Progress,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Timeline,
  Tooltip,
  Typography,
  Upload,
  message,
} from 'antd';
import { useState } from 'react';

import { ApiClientError, api } from '@/shared/apiClient';
import { EMPTY_MARK, formatDate, formatDateTime } from '@/shared/format';

import { LyLichFormModal } from './LyLichFormModal';
import { SuKienFormModal } from './SuKienFormModal';
import {
  type HoSoThuMuc,
  LOAI_LY_LICH,
  LOAI_SU_KIEN,
  type LyLichView,
  type SuKienView,
  THU_MUC_HO_SO,
  THU_MUC_HO_SO_OPTIONS,
  type TaiLieuView,
  type TinhTrangHoSoView,
} from './hrVocabulary';

/**
 * Ba lớp hồ sơ con của một CBNV — CN-04.3 (lý lịch), CN-04.4 (timeline), CN-04.5 (tài liệu).
 *
 * <h2>⚠ `destroyOnHidden` ở đây là phòng SÂU, ⛔ KHÔNG phải chốt chặn — và tôi đã suýt viết ngược</h2>
 *
 * Bản đầu của chú thích này khẳng định `destroyOnHidden` là *"thứ **duy nhất** ngăn dữ liệu của hồ
 * sơ trước hiện dưới tên hồ sơ sau"*. Lượt kiểm chứng ngược **bác câu ấy**: gỡ nó ra, bài
 * `hoSoConKhongTronDuLieu.test.tsx` **vẫn xanh**. Lý do là ba tab ở đây đọc dữ liệu qua `useQuery`
 * với `publicId` **nằm trong khoá truy vấn**, nên đổi người là đổi khoá — react-query tự nạp lại.
 *
 * ⇒ Chốt chặn thật nằm ở **hai hộp thoại biểu mẫu** (`LyLichFormModal`, `SuKienFormModal`), nơi
 * `Form.useForm()` giữ một kho giá trị **⛔ không** đi theo khoá truy vấn nào — đúng cơ chế T51.12.
 * Bài kiểm vì thế nhắm vào chúng, ⛔ không nhắm vào ngăn kéo.
 *
 * ⚠ Vẫn **giữ** `destroyOnHidden`: nó tháo trạng thái cục bộ của tab (bộ lọc loại sự kiện, thư mục
 * đang chọn) và ⛔ không tốn gì. Nhưng một chú thích nói quá về thứ nó bảo đảm là **nguy hiểm hơn
 * ⛔ không có chú thích**: lượt rà sau sẽ đọc nó rồi thôi ⛔ không đi tìm chốt chặn thật.
 */
export function HoSoConDrawer({
  open,
  publicId,
  tenCanBo,
  coSua,
  onClose,
}: {
  open: boolean;
  publicId: string | null;
  tenCanBo: string | null;
  coSua: boolean;
  onClose: () => void;
}) {
  return (
    <Drawer
      title={tenCanBo ? `Hồ sơ chi tiết — ${tenCanBo}` : 'Hồ sơ chi tiết'}
      open={open}
      onClose={onClose}
      width={920}
      // ⛔⛔ Xem javadoc trên. ⛔ Đừng đổi thành `false` "cho mượt".
      destroyOnHidden
    >
      {publicId ? <NoiDung publicId={publicId} coSua={coSua} /> : null}
    </Drawer>
  );
}

function NoiDung({ publicId, coSua }: { publicId: string; coSua: boolean }) {
  return (
    <Tabs
      items={[
        {
          key: 'ly-lich',
          label: 'Lý lịch & chuyên môn',
          children: <TabLyLich publicId={publicId} coSua={coSua} />,
        },
        {
          key: 'timeline',
          label: 'Lịch sử công tác',
          children: <TabTimeline publicId={publicId} coSua={coSua} />,
        },
        {
          key: 'tai-lieu',
          label: 'Hồ sơ tài liệu',
          children: <TabTaiLieu publicId={publicId} coSua={coSua} />,
        },
      ]}
    />
  );
}

// ============================================================================
// CN-04.3 — Lý lịch & chuyên môn
// ============================================================================

function TabLyLich({ publicId, coSua }: { publicId: string; coSua: boolean }) {
  const qc = useQueryClient();
  // ⚠ Chụp MỘT LẦN mỗi lượt mở — ⛔ không đọc đồng hồ trong thân render (react-hooks/purity).
  const [homNay] = useState(() => Date.now());
  const [dangSua, setDangSua] = useState<LyLichView | null>(null);
  const [moBieuMau, setMoBieuMau] = useState(false);

  const duong = `/hr/employees/${publicId}/ly-lich`;
  const danhSach = useQuery({
    queryKey: ['hr', 'ly-lich', publicId],
    queryFn: () => api.get<LyLichView[]>(duong),
  });

  const xoa = useMutation({
    mutationFn: (mucId: string) => api.delete(`${duong}/${mucId}`),
    onSuccess: () => {
      message.success('Đã xoá mục lý lịch');
      void qc.invalidateQueries({ queryKey: ['hr', 'ly-lich', publicId] });
    },
    // ⛔ Thiếu nhánh này thì người dùng bấm Xoá, ⛔ không có gì xảy ra và ⛔ không có gì báo —
    //    `moiLuotGhiPhaiBaoLoi.test.ts` canh đúng chuyện ấy, và nó bắt được bản đầu của tệp này.
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được'),
  });

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {coSua && (
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setDangSua(null);
            setMoBieuMau(true);
          }}
        >
          Thêm mục
        </Button>
      )}

      <Table<LyLichView>
        rowKey="publicId"
        size="small"
        loading={danhSach.isLoading}
        dataSource={danhSach.data ?? []}
        pagination={false}
        // ⛔ 150+auto+110+200+110+130+90 — thiếu `scroll.x` thì cột dài nhất (tên bằng cấp, nơi
        //    cấp) bị BÓP còn một ký tự mỗi dòng ở màn hình hẹp, ⛔ không một dòng báo lỗi.
        scroll={{ x: 950 }}
        locale={{ emptyText: <Empty description="Chưa có bằng cấp/chứng chỉ nào được nhập" /> }}
        columns={[
          {
            title: 'Loại',
            dataIndex: 'kind',
            width: 150,
            render: (k: LyLichView['kind']) => LOAI_LY_LICH[k],
          },
          { title: 'Tên', dataIndex: 'name' },
          {
            title: 'Xếp loại',
            dataIndex: 'grade',
            width: 110,
            render: (v: string | null) => v || EMPTY_MARK,
          },
          {
            title: 'Nơi cấp',
            dataIndex: 'institution',
            width: 200,
            render: (v: string | null) => v || EMPTY_MARK,
          },
          {
            title: 'Ngày cấp',
            dataIndex: 'issuedOn',
            width: 110,
            render: (v: string | null) => (v ? formatDate(v) : EMPTY_MARK),
          },
          {
            title: 'Hết hiệu lực',
            dataIndex: 'expiresOn',
            width: 130,
            // ⛔ Rỗng ở đây có nghĩa RÕ RÀNG: "không hết hiệu lực". Hiện dấu gạch trần sẽ đọc
            //    thành "chưa nhập" — hai trạng thái khác hẳn nhau (luật 9).
            render: (v: string | null) =>
              v ? (
                <HanSuDung ngay={v} homNay={homNay} />
              ) : (
                <Typography.Text type="secondary">Không hết hạn</Typography.Text>
              ),
          },
          ...(coSua
            ? [
                {
                  title: '',
                  key: 'thao-tac',
                  width: 90,
                  align: 'right' as const,
                  render: (_: unknown, row: LyLichView) => (
                    <Space size={0}>
                      <Button
                        type="text"
                        icon={<EditOutlined />}
                        onClick={() => {
                          setDangSua(row);
                          setMoBieuMau(true);
                        }}
                      />
                      <Popconfirm
                        title="Xoá mục này?"
                        okText="Xoá"
                        cancelText="Huỷ"
                        onConfirm={() => xoa.mutate(row.publicId)}
                      >
                        <Button type="text" danger icon={<DeleteOutlined />} />
                      </Popconfirm>
                    </Space>
                  ),
                },
              ]
            : []),
        ]}
      />

      <LyLichFormModal
        open={moBieuMau}
        hoSoId={publicId}
        muc={dangSua}
        onClose={() => setMoBieuMau(false)}
        onSaved={() => {
          setMoBieuMau(false);
          void qc.invalidateQueries({ queryKey: ['hr', 'ly-lich', publicId] });
        }}
      />
    </Space>
  );
}

/**
 * Ngày hết hạn, tô đỏ khi **đã** qua.
 *
 * ⛔⛔ Cố ý **⛔ KHÔNG** có nhánh *"sắp hết hạn"* ở đây, dù nó nghe hữu ích. Ngưỡng *"sắp"* là một
 * **tham số nghiệp vụ** (`hr.certificate.expiry-warning-days`, sửa được trên màn hình Cấu hình) —
 * ghi con số ấy lần thứ hai vào giao diện là dựng hai nguồn sự thật cho một câu hỏi, và chúng sẽ
 * lệch đúng vào ngày Công ty đổi ngưỡng (luật 14). Vế *"sắp hết hạn"* sống ở màn hình cảnh báo
 * M4.9, nơi ngưỡng **đi kèm dữ liệu** từ máy chủ.
 *
 * ⚠ `homNay` truyền vào chứ ⛔ không gọi `Date.now()` trong thân render: một lời gọi impure lúc
 * render cho kết quả đổi giữa hai lượt dựng lại của cùng một trạng thái, và `react-hooks/purity`
 * chặn nó ở cổng `Frontend — lint`.
 */
function HanSuDung({ ngay, homNay }: { ngay: string; homNay: number }) {
  const con = Math.round((new Date(ngay).getTime() - homNay) / 86_400_000);
  if (con < 0) {
    return (
      <Tag color="error">
        {formatDate(ngay)} · quá {Math.abs(con)} ngày
      </Tag>
    );
  }
  return <>{formatDate(ngay)}</>;
}

// ============================================================================
// CN-04.4 — Lịch sử công tác
// ============================================================================

function TabTimeline({ publicId, coSua }: { publicId: string; coSua: boolean }) {
  const qc = useQueryClient();
  const [loc, setLoc] = useState<string | undefined>();
  const [dangSua, setDangSua] = useState<SuKienView | null>(null);
  const [moBieuMau, setMoBieuMau] = useState(false);

  const duong = `/hr/employees/${publicId}/timeline`;
  const danhSach = useQuery({
    queryKey: ['hr', 'timeline', publicId, loc],
    queryFn: () => api.get<SuKienView[]>(loc ? `${duong}?loai=${loc}` : duong),
  });

  const xoa = useMutation({
    mutationFn: (id: string) => api.delete(`${duong}/${id}`),
    onSuccess: () => {
      message.success('Đã xoá sự kiện');
      void qc.invalidateQueries({ queryKey: ['hr', 'timeline', publicId] });
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được'),
  });

  const muc = danhSach.data ?? [];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        {coSua && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setDangSua(null);
              setMoBieuMau(true);
            }}
          >
            Ghi sự kiện
          </Button>
        )}
        <Select
          allowClear
          placeholder="Lọc theo loại sự kiện"
          style={{ width: 240 }}
          value={loc}
          onChange={setLoc}
          options={Object.entries(LOAI_SU_KIEN).map(([value, label]) => ({ value, label }))}
        />
      </Space>

      {muc.length === 0 ? (
        <Empty description="Chưa ghi sự kiện công tác nào" />
      ) : (
        <Timeline
          mode="left"
          items={muc.map((s) => ({
            // ⛔ Nhãn là ngày HIỆU LỰC, ⛔ không phải ngày ký hay ngày nhập — đó là trục của
            //    timeline theo đặc tả, và ba mốc ấy khác nhau.
            label: formatDate(s.effectiveOn),
            children: (
              <Space direction="vertical" size={2}>
                <Space wrap>
                  <Tag>{LOAI_SU_KIEN[s.eventType]}</Tag>
                  <Typography.Text strong>{s.title}</Typography.Text>
                  {coSua && (
                    <>
                      <Button
                        size="small"
                        type="text"
                        icon={<EditOutlined />}
                        onClick={() => {
                          setDangSua(s);
                          setMoBieuMau(true);
                        }}
                      />
                      <Popconfirm
                        title="Xoá sự kiện này?"
                        okText="Xoá"
                        cancelText="Huỷ"
                        onConfirm={() => xoa.mutate(s.publicId)}
                      >
                        <Button size="small" type="text" danger icon={<DeleteOutlined />} />
                      </Popconfirm>
                    </>
                  )}
                </Space>
                {s.decisionNo && (
                  <Typography.Text type="secondary">
                    Quyết định {s.decisionNo}
                    {s.decisionDate ? ` — ký ngày ${formatDate(s.decisionDate)}` : ''}
                  </Typography.Text>
                )}
                {s.detail && <Typography.Text>{s.detail}</Typography.Text>}
              </Space>
            ),
          }))}
        />
      )}

      <SuKienFormModal
        open={moBieuMau}
        hoSoId={publicId}
        suKien={dangSua}
        onClose={() => setMoBieuMau(false)}
        onSaved={() => {
          setMoBieuMau(false);
          void qc.invalidateQueries({ queryKey: ['hr', 'timeline', publicId] });
        }}
      />
    </Space>
  );
}

// ============================================================================
// CN-04.5 — Hồ sơ tài liệu
// ============================================================================

function TabTaiLieu({ publicId, coSua }: { publicId: string; coSua: boolean }) {
  const qc = useQueryClient();
  const [homNay] = useState(() => Date.now());
  const [thuMuc, setThuMuc] = useState<HoSoThuMuc>('GIAY_TO_TUY_THAN');
  const duong = `/hr/employees/${publicId}/tai-lieu`;

  const danhSach = useQuery({
    queryKey: ['hr', 'tai-lieu', publicId],
    queryFn: () => api.get<TaiLieuView[]>(duong),
  });
  const tinhTrang = useQuery({
    queryKey: ['hr', 'tai-lieu', publicId, 'tinh-trang'],
    queryFn: () => api.get<TinhTrangHoSoView>(`${duong}/tinh-trang`),
  });

  const lamMoi = () => {
    void qc.invalidateQueries({ queryKey: ['hr', 'tai-lieu', publicId] });
  };

  const xoa = useMutation({
    mutationFn: (id: string) => api.delete(`${duong}/${id}`),
    onSuccess: () => {
      message.success('Đã xoá tài liệu');
      lamMoi();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được'),
  });

  const tt = tinhTrang.data;

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {/* ⛔⛔ HAI câu cho HAI trạng thái. `daCauHinh === false` nghĩa là Công ty CHƯA khai thư mục
          nào bắt buộc — hiện "0%" ở đó là công bố một con số dựa trên một luật không ai duyệt. */}
      {tt &&
        (tt.daCauHinh ? (
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Typography.Text type="secondary">Mức hoàn thiện hồ sơ</Typography.Text>
            <Progress percent={tt.phanTram ?? 0} size="small" />
            {tt.conThieu.length > 0 && (
              <Typography.Text type="warning">
                Còn thiếu: {tt.conThieu.map((t) => THU_MUC_HO_SO[t]).join(', ')}
              </Typography.Text>
            )}
          </Space>
        ) : (
          <Alert
            type="info"
            showIcon
            message="Chưa cấu hình danh sách tài liệu bắt buộc"
            description="Quản trị › Cấu hình hệ thống › nhóm Nhân sự › “Thư mục tài liệu BẮT BUỘC”. Chưa khai thì hệ thống không tính % hoàn thiện — nó không giả định thay Công ty."
          />
        ))}

      {coSua && (
        <Space wrap>
          <Select<HoSoThuMuc>
            value={thuMuc}
            onChange={setThuMuc}
            style={{ width: 220 }}
            options={THU_MUC_HO_SO_OPTIONS}
          />
          <Upload
            accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.webp"
            showUploadList={false}
            customRequest={({ file, onSuccess, onError }) => {
              const form = new FormData();
              form.append('file', file as Blob);
              api
                .upload(`${duong}?thuMuc=${thuMuc}`, form)
                .then((r) => {
                  message.success('Đã tải tài liệu lên');
                  lamMoi();
                  onSuccess?.(r);
                })
                .catch((e: unknown) => onError?.(e as Error));
            }}
          >
            <Button icon={<UploadOutlined />}>Tải tệp vào thư mục này</Button>
          </Upload>
          {tt && (
            <Typography.Text type="secondary">
              Đã dùng {(tt.dungLuongDaDungByte / 1024 / 1024).toFixed(1)} MB
            </Typography.Text>
          )}
        </Space>
      )}

      <Table<TaiLieuView>
        rowKey="publicId"
        size="small"
        loading={danhSach.isLoading}
        dataSource={danhSach.data ?? []}
        pagination={false}
        scroll={{ x: 900 }}
        locale={{ emptyText: <Empty description="Chưa có tài liệu nào trong hồ sơ" /> }}
        columns={[
          {
            title: 'Thư mục',
            dataIndex: 'thuMuc',
            width: 170,
            // ⛔ `null` = giá trị `purpose` trong CSDL ⛔ không giải được (bản khôi phục cũ). Vẫn
            //    hiện hàng ấy: giấu nó đi là một tệp tính vào hạn mức mà ⛔ không ai thấy.
            render: (t: HoSoThuMuc | null) =>
              t ? THU_MUC_HO_SO[t] : <Typography.Text type="secondary">(không rõ)</Typography.Text>,
          },
          { title: 'Tên tệp', dataIndex: 'tenGoc' },
          { title: 'Bản', dataIndex: 'phienBan', width: 60 },
          {
            title: 'Dung lượng',
            dataIndex: 'soByte',
            width: 110,
            render: (b: number) => `${(b / 1024).toFixed(0)} KB`,
          },
          {
            title: 'Hết hạn',
            dataIndex: 'hetHan',
            width: 130,
            render: (v: string | null) => (v ? <HanSuDung ngay={v} homNay={homNay} /> : EMPTY_MARK),
          },
          {
            title: 'Tải lúc',
            dataIndex: 'taiLuc',
            width: 150,
            render: (v: string) => formatDateTime(v),
          },
          {
            title: '',
            key: 'thao-tac',
            width: 110,
            align: 'right',
            render: (_, row) => (
              <Space size={0}>
                <Tooltip
                  title={row.taiDuoc ? 'Tải tệp' : 'Tệp chưa quét virus xong hoặc đã bị cách ly'}
                >
                  <Button
                    type="text"
                    size="small"
                    disabled={!row.taiDuoc}
                    onClick={() => {
                      void api
                        .get<{ url: string }>(`${duong}/${row.publicId}/download-url`)
                        .then((r) => window.open(r.url, '_blank', 'noopener'));
                    }}
                  >
                    Tải
                  </Button>
                </Tooltip>
                {coSua && (
                  <Popconfirm
                    title="Xoá tài liệu này?"
                    okText="Xoá"
                    cancelText="Huỷ"
                    onConfirm={() => xoa.mutate(row.publicId)}
                  >
                    <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />
    </Space>
  );
}
