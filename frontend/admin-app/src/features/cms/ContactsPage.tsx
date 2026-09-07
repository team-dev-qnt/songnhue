import { DeleteOutlined, DownloadOutlined, MailOutlined, TagsOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Badge,
  Button,
  Card,
  Descriptions,
  Divider,
  Empty,
  Input,
  List,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { ApprovalActions } from '@/components/business/ApprovalActions';
import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import { ApiClientError } from '@/shared/apiClient';

import { cmsApi, cmsKeys } from './api';
import { ContactCategoriesModal } from './ContactCategoriesModal';
import { type ContactStatus, type ContactView } from './types';

/**
 * Hộp thư tiếp nhận liên hệ / phản ánh từ cổng công khai — CN-01.4.
 *
 * <h3>Vì sao màn hình này ra đời CÙNG lượt với biểu mẫu</h3>
 *
 * Chú thích cũ ở trang Liên hệ của cổng từ chối dựng biểu mẫu với đúng một lý do: *"một form gửi
 * đi mà không ai nhận tệ hơn hẳn không có form: người dân tin là đã gửi được"*. Màn hình này
 * chính là nửa "có người nhận".
 *
 * <h3>⛔ Nội dung hiển thị bằng TEXT, tuyệt đối không dựng thành HTML</h3>
 *
 * `subject` và `content` do người lạ trên Internet nhập. React escape mặc định, và điều đó phải
 * được giữ: một `dangerouslySetInnerHTML` đặt lên hai trường này là XSS lưu trữ nhắm thẳng vào
 * người có quyền quản trị — cùng hình dạng lỗi §10 đã trả giá ở `settings`, chỉ khác là nạn nhân
 * có quyền cao hơn. ⚠ Ràng buộc ấy áp cho **cả** `resolutionNote` và ghi chú nội bộ.
 *
 * <h3>⛔ Nút chuyển trạng thái do BACKEND trả về, ⛔ không dựng ở đây</h3>
 *
 * `allowedActions` đã lọc theo `workflow_transitions` **và** theo quyền người đang đăng nhập, và
 * nó mang cờ `requiresReason`. Một bảng `if` ở giao diện là bản sao thứ hai của một luật đang nằm
 * trong CSDL — bản sao ấy lệch ngay lần đầu Công ty thêm một bước (conventions.md §3).
 *
 * <h3>Phạm vi lượt WS-36 (06/09)</h3>
 *
 * Quy trình sáu trạng thái · phân loại · chuyển phòng ban · ghi chú nội bộ · xoá (⛔ cấm khi đang
 * xử lý). ⛔ Chưa có: email báo/xác nhận, xuất Excel, nhắc SLA, reCAPTCHA (G13).
 */
const NHAN_TRANG_THAI: Record<ContactStatus, { nhan: string; mau: string }> = {
  MOI: { nhan: 'Mới', mau: 'red' },
  DA_DOC: { nhan: 'Đã đọc', mau: 'blue' },
  DANG_XU_LY: { nhan: 'Đang xử lý', mau: 'gold' },
  DA_PHAN_HOI: { nhan: 'Đã phản hồi', mau: 'green' },
  DONG: { nhan: 'Đóng', mau: 'default' },
  LUU_TRU: { nhan: 'Lưu trữ', mau: 'default' },
};

const CO_TRANG = 20;

const QUYEN_GHI = 'cms:contact:manage';
const LY_DO_THIEU_QUYEN = `Bạn không có quyền ${QUYEN_GHI}`;

/** UTC+7 ở mọi chỗ hiển thị thời gian — CLAUDE.md quy tắc 1. */
function gio(t: string) {
  return new Date(t).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
}

export function ContactsPage() {
  const [loc, datLoc] = useState<ContactStatus | undefined>(undefined);
  const [trang, datTrang] = useState(0);
  const [moDanhMuc, datMoDanhMuc] = useState(false);
  const { hasPermission } = useAuth();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();

  /**
   * ⚠ Tuyến `/noi-dung/hop-thu-lien-he` **đã** canh bằng đúng quyền này, và mọi endpoint của hộp
   * thư cũng vậy — nên tầng 1 và tầng 2 ⛔ không lệch ở màn hình này (khác `BannersTab`, nơi tuyến
   * canh bằng một quyền còn nút Lưu đòi một quyền khác). Vẫn đọc lại ở đây để ngày ai đó nới tuyến
   * ra `cms:*:view` thì nút ghi tự khoá, ⛔ không phải chờ một lượt 403 trên mặt người dùng.
   */
  const coQuyenGhi = hasPermission(QUYEN_GHI);

  const danhSach = useQuery({
    queryKey: cmsKeys.contacts(loc, trang),
    queryFn: () => cmsApi.listContacts(loc, trang, CO_TRANG),
  });

  const danhMuc = useQuery({
    queryKey: ['cms', 'contact-categories'],
    queryFn: () => cmsApi.listContactCategories(),
  });

  const lamMoiDanhSach = () =>
    void queryClient.invalidateQueries({ queryKey: ['cms', 'contacts'] });

  const bao = (caught: unknown, macDinh: string) =>
    message.error(caught instanceof ApiClientError ? caught.message : macDinh);

  const danhDau = useMutation({
    mutationFn: (publicId: string) => cmsApi.markContactRead(publicId),
    onSuccess: lamMoiDanhSach,
    // ⚠ Lượt đánh dấu này chạy ngầm khi mở một ý kiến, không có nút nào để bấm lại. Hỏng mà im
    //   lặng thì ý kiến ấy ở nguyên trạng thái "chưa đọc" và không ai biết vì sao.
    onError: (caught: unknown) => bao(caught, 'Không đánh dấu được'),
  });

  const chuyenTrangThai = useMutation({
    mutationFn: (v: { publicId: string; action: string; reason?: string }) =>
      cmsApi.contactTransition(v.publicId, v.action, v.reason),
    onSuccess: lamMoiDanhSach,
    onError: (caught: unknown) => bao(caught, 'Không chuyển được trạng thái'),
  });

  const datPhanLoai = useMutation({
    mutationFn: (v: { publicId: string; categoryPublicId: string | null }) =>
      cmsApi.setContactCategory(v.publicId, v.categoryPublicId),
    onSuccess: lamMoiDanhSach,
    onError: (caught: unknown) => bao(caught, 'Không gán được phân loại'),
  });

  const datDonVi = useMutation({
    mutationFn: (v: { publicId: string; orgUnitPublicId: string | null }) =>
      cmsApi.assignContact(v.publicId, v.orgUnitPublicId),
    onSuccess: lamMoiDanhSach,
    onError: (caught: unknown) => bao(caught, 'Không chuyển được đơn vị xử lý'),
  });

  const xuat = useMutation({
    mutationFn: async () => {
      const { blob, tenTep } = await cmsApi.exportContacts(loc);
      // ⛔ Dựng blob rồi bấm một thẻ <a> — ⛔ không `window.open` (tab mới ⛔ không mang header
      //   `Authorization`, và người dùng nhận một tab trắng).
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = tenTep ?? 'lien-he.csv';
      a.click();
      URL.revokeObjectURL(url);
    },
    // ⛔ CMS-2022 khi vượt trần dòng — câu chữ đến từ `error-map`, ⛔ không viết lại ở đây.
    onError: (caught: unknown) => bao(caught, 'Không xuất được danh sách'),
  });

  const xoa = useMutation({
    mutationFn: (publicId: string) => cmsApi.deleteContact(publicId),
    onSuccess: () => {
      lamMoiDanhSach();
      message.success('Đã xoá liên hệ');
    },
    // ⛔ CMS-2018 khi đang `DANG_XU_LY` — câu chữ đến từ `error-map`, ⛔ không viết lại ở đây.
    onError: (caught: unknown) => bao(caught, 'Không xoá được liên hệ'),
  });

  const cot = [
    {
      title: 'Người gửi',
      dataIndex: 'fullName',
      key: 'fullName',
      render: (ten: string, r: ContactView) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{ten}</Typography.Text>
          {/* ⛔ Không ghép email và điện thoại bằng dấu gạch khi một bên rỗng — một dấu gạch
              trông như một giá trị. */}
          {r.email ? <Typography.Text type="secondary">{r.email}</Typography.Text> : null}
          {r.phone ? <Typography.Text type="secondary">{r.phone}</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: 'Tiêu đề',
      dataIndex: 'subject',
      key: 'subject',
      render: (cd: string) => <Typography.Text>{cd}</Typography.Text>,
    },
    {
      title: 'Phân loại',
      dataIndex: 'categoryName',
      key: 'categoryName',
      width: 150,
      // ⛔ Rỗng nói ra là rỗng. Một ô trắng đọc như "đã xem và không thuộc loại nào".
      render: (ten: string | null) =>
        ten ? <Tag>{ten}</Tag> : <Typography.Text type="secondary">Chưa phân loại</Typography.Text>,
    },
    {
      title: 'Đơn vị xử lý',
      dataIndex: 'assignedUnitName',
      key: 'assignedUnitName',
      width: 170,
      render: (ten: string | null) =>
        ten ? ten : <Typography.Text type="secondary">Chưa chuyển</Typography.Text>,
    },
    {
      title: 'Nhận lúc',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (t: string) => gio(t),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (tt: ContactStatus) => (
        <Tag color={NHAN_TRANG_THAI[tt].mau}>{NHAN_TRANG_THAI[tt].nhan}</Tag>
      ),
    },
    {
      title: '',
      key: 'thaoTac',
      width: 60,
      render: (_: unknown, r: ContactView) => (
        <Button
          type="text"
          danger
          size="small"
          icon={<DeleteOutlined />}
          disabled={!coQuyenGhi}
          title={coQuyenGhi ? undefined : LY_DO_THIEU_QUYEN}
          onClick={() =>
            modal.confirm({
              title: 'Xoá liên hệ này?',
              content:
                'Xoá mềm — bản ghi vẫn nằm trong cơ sở dữ liệu và trong nhật ký. ' +
                'Liên hệ đang ở trạng thái "Đang xử lý" thì không xoá được.',
              okText: 'Xoá',
              okButtonProps: { danger: true },
              cancelText: 'Huỷ',
              onOk: () => xoa.mutateAsync(r.publicId),
            })
          }
        />
      ),
    },
  ];

  const soMoi = (danhSach.data?.items ?? []).filter((c) => c.status === 'MOI').length;

  return (
    <Card
      title={
        <Space>
          <MailOutlined />
          <span>Hộp thư liên hệ</span>
          {soMoi > 0 ? <Badge count={soMoi} /> : null}
        </Space>
      }
      extra={
        <Space>
          <Button
            icon={<DownloadOutlined />}
            loading={xuat.isPending}
            onClick={() => xuat.mutate()}
            // ⚠ Xuất theo ĐÚNG bộ lọc đang xem — người dùng lọc "Đang xử lý" rồi bấm Xuất mà
            //   nhận cả hộp thư là một tệp gửi đi ngoài với nhiều dữ liệu hơn họ định gửi.
            title="Xuất danh sách đang lọc ra tệp CSV (mở bằng Excel)"
          >
            Xuất Excel
          </Button>
          <Button icon={<TagsOutlined />} onClick={() => datMoDanhMuc(true)}>
            Phân loại
          </Button>
          <Select<ContactStatus | 'ALL'>
            value={loc ?? 'ALL'}
            style={{ width: 160 }}
            onChange={(v) => {
              datLoc(v === 'ALL' ? undefined : v);
              datTrang(0);
            }}
            options={[
              { value: 'ALL', label: 'Tất cả' },
              ...(Object.keys(NHAN_TRANG_THAI) as ContactStatus[]).map((k) => ({
                value: k,
                label: NHAN_TRANG_THAI[k].nhan,
              })),
            ]}
          />
        </Space>
      }
    >
      <Table<ContactView>
        rowKey="publicId"
        loading={danhSach.isPending}
        dataSource={danhSach.data?.items ?? []}
        columns={cot}
        locale={{
          emptyText: <Empty description="Chưa có liên hệ nào gửi từ cổng thông tin" />,
        }}
        // Bề ngang tối thiểu: hẹp hơn thì CUỘN NGANG, không bóp chữ.
        // Vì sao cần — xem chú thích cột "Địa chỉ" ở `features/hydro/ApiSourcesPage.tsx`.
        scroll={{ x: 1280 }}
        pagination={{
          current: trang + 1,
          pageSize: CO_TRANG,
          total: danhSach.data?.meta.totalElements ?? 0,
          onChange: (p) => datTrang(p - 1),
          showSizeChanger: false,
        }}
        expandable={{
          // Mở một dòng = đã đọc nó. Không dựng thêm một nút "đánh dấu đã đọc" riêng: hai
          // đường làm cùng một việc thì trạng thái lệch nhau tuỳ người dùng bấm cái nào.
          onExpand: (moRa, r) => {
            if (moRa && r.status === 'MOI') danhDau.mutate(r.publicId);
          },
          expandedRowRender: (r) => (
            <ChiTietLienHe
              lienHe={r}
              coQuyenGhi={coQuyenGhi}
              danhMuc={(danhMuc.data ?? []).filter(
                (c) => c.active || c.publicId === r.categoryPublicId,
              )}
              onChuyenTrangThai={async (action, reason) => {
                await chuyenTrangThai.mutateAsync({ publicId: r.publicId, action, reason });
              }}
              onPhanLoai={(categoryPublicId) =>
                datPhanLoai.mutate({ publicId: r.publicId, categoryPublicId })
              }
              onChuyenDonVi={(orgUnitPublicId) =>
                datDonVi.mutate({ publicId: r.publicId, orgUnitPublicId })
              }
            />
          ),
        }}
      />

      <ContactCategoriesModal
        open={moDanhMuc}
        onClose={() => datMoDanhMuc(false)}
        coQuyenGhi={coQuyenGhi}
        lyDoThieuQuyen={LY_DO_THIEU_QUYEN}
      />
    </Card>
  );
}

/**
 * Khối chi tiết của một liên hệ.
 *
 * ⚠ Tách thành component riêng ⛔ không phải để gọn: `useQuery` cho `allowedActions` và cho ghi chú
 * **phải** gắn với một dòng cụ thể, và hook ⛔ không gọi được trong `expandedRowRender` của bảng.
 */
function ChiTietLienHe({
  lienHe,
  coQuyenGhi,
  danhMuc,
  onChuyenTrangThai,
  onPhanLoai,
  onChuyenDonVi,
}: {
  lienHe: ContactView;
  coQuyenGhi: boolean;
  danhMuc: readonly { publicId: string; name: string }[];
  onChuyenTrangThai: (action: string, reason?: string) => Promise<void>;
  onPhanLoai: (categoryPublicId: string | null) => void;
  onChuyenDonVi: (orgUnitPublicId: string | null) => void;
}) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [ghiChuMoi, datGhiChuMoi] = useState('');

  const hanhDong = useQuery({
    queryKey: ['cms', 'contacts', lienHe.publicId, 'actions'],
    queryFn: () => cmsApi.contactActions(lienHe.publicId),
  });

  const ghiChu = useQuery({
    queryKey: ['cms', 'contacts', lienHe.publicId, 'notes'],
    queryFn: () => cmsApi.contactNotes(lienHe.publicId),
  });

  const themGhiChu = useMutation({
    mutationFn: (noiDung: string) => cmsApi.addContactNote(lienHe.publicId, noiDung),
    onSuccess: () => {
      datGhiChuMoi('');
      void queryClient.invalidateQueries({
        queryKey: ['cms', 'contacts', lienHe.publicId, 'notes'],
      });
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không thêm được ghi chú'),
  });

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Descriptions column={1} size="small" bordered>
        <Descriptions.Item label="Nội dung">
          {/* Xuống dòng giữ nguyên; nội dung vẫn là TEXT — React escape. */}
          <Typography.Paragraph style={{ whiteSpace: 'pre-line', marginBottom: 0 }}>
            {lienHe.content}
          </Typography.Paragraph>
        </Descriptions.Item>
        {lienHe.readAt ? (
          <Descriptions.Item label="Đọc lần đầu lúc">{gio(lienHe.readAt)}</Descriptions.Item>
        ) : null}
        {/* ⚠ Chỉ hiện khi CÓ. Một dòng "Nội dung phản hồi: (trống)" đọc như đã trả lời bằng
            một câu rỗng. */}
        {lienHe.resolutionNote ? (
          <Descriptions.Item label="Ghi nhận ở bước gần nhất">
            <Typography.Paragraph style={{ whiteSpace: 'pre-line', marginBottom: 0 }}>
              {lienHe.resolutionNote}
            </Typography.Paragraph>
          </Descriptions.Item>
        ) : null}
      </Descriptions>

      <Space wrap align="start" size="large">
        <Space direction="vertical" size={4}>
          <Typography.Text type="secondary">Phân loại</Typography.Text>
          <Select
            style={{ width: 240 }}
            allowClear
            placeholder={
              danhMuc.length === 0
                ? 'Chưa có phân loại nào — bấm "Phân loại" để thêm'
                : 'Chọn phân loại'
            }
            disabled={!coQuyenGhi}
            value={lienHe.categoryPublicId ?? undefined}
            onChange={(v) => onPhanLoai(v ?? null)}
            options={danhMuc.map((c) => ({ value: c.publicId, label: c.name }))}
          />
        </Space>
        <Space direction="vertical" size={4}>
          <Typography.Text type="secondary">Đơn vị xử lý</Typography.Text>
          <div style={{ width: 280 }}>
            <OrgUnitTreeSelect
              value={lienHe.assignedUnitPublicId ?? undefined}
              onChange={(v) => onChuyenDonVi(v ?? null)}
              disabled={!coQuyenGhi}
              placeholder="Chuyển phòng ban / Xí nghiệp"
            />
          </div>
        </Space>
      </Space>

      {/* ⛔ Nút do backend trả về — xem javadoc đầu tệp. */}
      <ApprovalActions
        actions={hanhDong.data}
        onAction={onChuyenTrangThai}
        disabled={!coQuyenGhi}
      />

      <Divider orientation="left" style={{ margin: '4px 0' }}>
        Ghi chú nội bộ
      </Divider>
      <List
        size="small"
        dataSource={ghiChu.data ?? []}
        loading={ghiChu.isPending}
        locale={{ emptyText: 'Chưa có ghi chú nào' }}
        renderItem={(n) => (
          <List.Item>
            <Space direction="vertical" size={0} style={{ width: '100%' }}>
              <Typography.Text style={{ whiteSpace: 'pre-line' }}>{n.content}</Typography.Text>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {gio(n.createdAt)}
              </Typography.Text>
            </Space>
          </List.Item>
        )}
      />
      <Space.Compact style={{ width: '100%' }}>
        <Input.TextArea
          value={ghiChuMoi}
          onChange={(e) => datGhiChuMoi(e.target.value)}
          placeholder="Ghi chú cho người xử lý tiếp theo — không hiển thị ra cổng"
          autoSize={{ minRows: 1, maxRows: 4 }}
          maxLength={2000}
          disabled={!coQuyenGhi}
        />
        <Button
          type="primary"
          loading={themGhiChu.isPending}
          disabled={!coQuyenGhi || ghiChuMoi.trim().length === 0}
          onClick={() => themGhiChu.mutate(ghiChuMoi)}
        >
          Thêm
        </Button>
      </Space.Compact>
    </Space>
  );
}

export default ContactsPage;
