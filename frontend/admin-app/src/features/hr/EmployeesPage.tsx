import { DeleteOutlined, EditOutlined, LockOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Input,
  Popconfirm,
  Select,
  Space,
  Tooltip,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { DataTable } from '@/components/DataTable';
import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import { StatusBadge } from '@/components/business/StatusBadge';
import { usePagination } from '@/components/usePagination';
import { ApiClientError, api } from '@/shared/apiClient';
import { EMPTY_MARK, formatDate, formatDateTime } from '@/shared/format';

import { EmployeeFormModal } from './EmployeeFormModal';
import { SensitiveModal } from './SensitiveModal';
import {
  type EmployeeRow,
  type EmploymentStatus,
  type PositionView,
  SAP_XEP_CBNV,
  SAP_XEP_MAC_DINH,
  TRANG_THAI_CONG_TAC,
  TRANG_THAI_CONG_TAC_OPTIONS,
} from './hrVocabulary';

/**
 * Danh sách hồ sơ cán bộ nhân viên — CN-04.2, CN-04.7 (WS-51).
 *
 * <h2>⛔⛔ Bảng này RỖNG hôm nay, và đó là câu trả lời ĐÚNG</h2>
 *
 * `G6-a` — danh sách CBNV do Công ty cung cấp — còn **mở**. CLAUDE.md cấm seed dữ liệu *"cho đẹp
 * demo"*, và §10.54 đã trả giá cho đúng chuyện đó: 19 bài viết, 4 văn bản có số hiệu, 5 trạm thuỷ
 * văn và 9 số điện thoại **bịa** làm một trang rỗng trông đầy — tất cả đã lên staging.
 *
 * <p>⇒ Trạng thái rỗng ở đây phải **nói ra lý do**, và phải phân biệt được **hai** thứ (luật 9):
 * *"chưa có dữ liệu nào"* với *"bộ lọc ⛔ không khớp hồ sơ nào"*. Một câu chung cho cả hai là dạy
 * người dùng đọc sai chính cái họ vừa làm.
 *
 * <h2>⛔⛔ Sort mặc định phải nằm trong danh sách backend cho phép</h2>
 *
 * `PageUtils` ném **400** (`SYS-0003`) với mọi trường ngoài `EmployeeService.SAP_XEP_CHO_PHEP`. Một 400 ở lượt
 * tải ĐẦU TIÊN trông y hệt một bảng vốn rỗng — mà bảng này *đúng là* rỗng. Vì thế ô "Sắp xếp"
 * dựng từ hằng {@link SAP_XEP_CBNV}, ⛔ không có chuỗi sort nào gõ tay trên màn hình.
 *
 * <h2>Ba việc, ba quyền — tuyến mở bằng quyền RỘNG NHẤT</h2>
 *
 * Trang chứa xem (`hr:employee:view`) · thêm/sửa/xoá (`:create`/`:update`/`:delete`) · trường 🔒
 * (`:view-sensitive`, mà **ADMIN cố ý ⛔ không có**). Tuyến gác bằng `:view`, từng nút tự ẩn theo
 * quyền của **endpoint nó gọi** — T27.28: gác cả trang bằng quyền hẹp nhất là chôn trang sau nút
 * của nó.
 */
export function EmployeesPage() {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const pagination = usePagination(20);

  const [tuKhoa, setTuKhoa] = useState('');
  const [donViId, setDonViId] = useState<string | undefined>();
  const [chucVuId, setChucVuId] = useState<string | undefined>();
  const [trangThai, setTrangThai] = useState<EmploymentStatus | undefined>();
  // ⛔ Giá trị này PHẢI nằm trong `SAP_XEP_CHO_PHEP` của backend — xem javadoc lớp.
  const [sapXep, setSapXep] = useState<string>(SAP_XEP_MAC_DINH);

  const [dangSuaId, setDangSuaId] = useState<string | null>(null);
  const [bieuMauMo, setBieuMauMo] = useState(false);
  const [xemBaoMat, setXemBaoMat] = useState<EmployeeRow | null>(null);

  const coThem = hasPermission('hr:employee:create');
  const coSua = hasPermission('hr:employee:update');
  const coXoa = hasPermission('hr:employee:delete');
  const coBaoMat = hasPermission('hr:employee:view-sensitive');

  const coLoc = !!tuKhoa || !!donViId || !!chucVuId || !!trangThai;

  const chucVu = useQuery({
    queryKey: ['hr', 'positions'],
    queryFn: () => api.get<PositionView[]>('/hr/positions'),
  });

  const danhSach = useQuery({
    queryKey: [
      'hr',
      'employees',
      { tuKhoa, donViId, chucVuId, trangThai, sapXep, ...pagination.params },
    ],
    queryFn: () =>
      api.getPage<EmployeeRow>('/hr/employees', {
        ...pagination.params,
        sort: sapXep,
        q: tuKhoa || undefined,
        orgUnitId: donViId,
        positionId: chucVuId,
        status: trangThai,
      }),
  });

  const xoaMutation = useMutation({
    mutationFn: (publicId: string) => api.delete(`/hr/employees/${publicId}`),
    onSuccess: async () => {
      message.success('Đã xoá hồ sơ');
      await queryClient.invalidateQueries({ queryKey: ['hr', 'employees'] });
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được hồ sơ'),
  });

  const moThemMoi = () => {
    setDangSuaId(null);
    setBieuMauMo(true);
  };

  const moSua = (row: EmployeeRow) => {
    setDangSuaId(row.publicId);
    setBieuMauMo(true);
  };

  const datLaiLoc = (thayDoi: () => void) => {
    thayDoi();
    // Giữ nguyên trang cũ sau khi đổi bộ lọc thì rất dễ rơi vào một trang trống — và trang trống
    // ấy đọc y hệt "⛔ không có hồ sơ nào khớp".
    pagination.reset();
  };

  const columns: ColumnsType<EmployeeRow> = [
    { title: 'Mã cán bộ', dataIndex: 'code', width: 130 },
    { title: 'Họ và tên', dataIndex: 'fullName', width: 220, ellipsis: true },
    {
      title: 'Đơn vị công tác',
      dataIndex: 'orgUnitName',
      width: 200,
      ellipsis: true,
      render: (ten: string | null) => ten ?? EMPTY_MARK,
    },
    {
      title: 'Chức vụ',
      dataIndex: 'positionName',
      width: 170,
      ellipsis: true,
      render: (ten: string | null) => ten ?? EMPTY_MARK,
    },
    {
      title: 'Chức danh',
      dataIndex: 'jobTitle',
      width: 170,
      ellipsis: true,
      render: (ten: string | null) => ten ?? EMPTY_MARK,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 150,
      render: (value: EmploymentStatus) => (
        <StatusBadge value={value} vocabulary={TRANG_THAI_CONG_TAC} />
      ),
    },
    {
      // ⚠ Cột này chỉ HIỂN THỊ. Cảnh báo hết hạn HĐLĐ là CN-04.5 — một máy quét theo lịch, ⛔ không
      //   phải một màu trên bảng. Tô đỏ ở đây mà ⛔ không có ai được thông báo là dựng nửa cặp
      //   đọc–ghi ở tầng giao diện (luật 27): người mở trang thì thấy, người cần biết thì không.
      title: 'HĐ hết hạn',
      dataIndex: 'contractExpiresAt',
      width: 130,
      render: (value: string | null) => (value ? formatDate(value) : EMPTY_MARK),
    },
    {
      title: 'Cập nhật lúc',
      dataIndex: 'updatedAt',
      width: 160,
      render: (value: string | null) => formatDateTime(value),
    },
    {
      title: '',
      key: 'thao-tac',
      width: 140,
      align: 'right',
      render: (_, row) => (
        <Space size={0}>
          {coBaoMat && (
            <Tooltip title="Trường bảo mật (CCCD, lương, tài khoản) — mỗi lượt mở đều ghi nhật ký">
              <Button type="text" icon={<LockOutlined />} onClick={() => setXemBaoMat(row)} />
            </Tooltip>
          )}
          {coSua && (
            <Tooltip title="Sửa hồ sơ">
              <Button type="text" icon={<EditOutlined />} onClick={() => moSua(row)} />
            </Tooltip>
          )}
          {coXoa && (
            <Popconfirm
              title="Xoá hồ sơ này?"
              description="Xoá mềm — hồ sơ vẫn còn trong nhật ký kiểm toán."
              okText="Xoá"
              cancelText="Huỷ"
              onConfirm={() => xoaMutation.mutate(row.publicId)}
            >
              <Button type="text" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  // ⛔⛔ `!danhSach.isError` ⛔ KHÔNG thừa. Thiếu nó thì một lượt gọi HỎNG (data `undefined`)
  //    cũng rơi vào nhánh này và màn hình nói *"Công ty chưa cung cấp danh sách CBNV (G6-a)"* —
  //    tức hệ thống khai một sự thật NGHIỆP VỤ để che một sự cố KỸ THUẬT. Ba trạng thái
  //    (chưa có dữ liệu · bộ lọc ⛔ không khớp · gọi hỏng) phải phân biệt được (luật 9); gộp
  //    hai cái đầu đã là mất mát, gộp cả cái thứ ba là nói dối người vận hành.
  const chuaCoDuLieu =
    !coLoc && !danhSach.isError && (danhSach.data?.meta.totalElements ?? 0) === 0;

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Typography.Title level={4} style={{ margin: 0 }}>
        Hồ sơ cán bộ nhân viên
      </Typography.Title>

      {chuaCoDuLieu && !danhSach.isLoading && (
        <Alert
          type="info"
          showIcon
          message="Chưa có dữ liệu cán bộ nhân viên"
          description={
            <>
              Công ty chưa cung cấp danh sách CBNV (mục <b>G6-a</b>). Hệ thống ⛔ <b>không</b> tạo
              hồ sơ mẫu để bảng trông có dữ liệu — một dòng bịa ở đây sẽ đi vào báo cáo quân số và
              ⛔ không ai phân biệt được nó với người thật. Dùng nút <b>Thêm hồ sơ</b> để nhập từng
              người, hoặc chờ danh sách chính thức.
            </>
          }
        />
      )}

      <Card>
        <Space wrap size="middle" style={{ width: '100%' }}>
          <Input.Search
            allowClear
            style={{ width: 260 }}
            placeholder="Tìm theo mã hoặc họ tên"
            onSearch={(v) => datLaiLoc(() => setTuKhoa(v.trim()))}
          />
          <div style={{ width: 260 }}>
            <OrgUnitTreeSelect
              value={donViId}
              onChange={(v) => datLaiLoc(() => setDonViId(v))}
              placeholder="Lọc theo đơn vị"
            />
          </div>
          <Select
            allowClear
            style={{ width: 220 }}
            placeholder="Lọc theo chức vụ"
            loading={chucVu.isLoading}
            value={chucVuId}
            onChange={(v) => datLaiLoc(() => setChucVuId(v))}
            options={(chucVu.data ?? []).map((c) => ({ value: c.publicId, label: c.name }))}
          />
          <Select
            allowClear
            style={{ width: 200 }}
            placeholder="Lọc theo trạng thái"
            value={trangThai}
            onChange={(v) => datLaiLoc(() => setTrangThai(v))}
            options={TRANG_THAI_CONG_TAC_OPTIONS}
          />
          <Select
            style={{ width: 240 }}
            value={sapXep}
            onChange={(v) => datLaiLoc(() => setSapXep(v))}
            options={SAP_XEP_CBNV.map((o) => ({ value: o.value, label: `Sắp xếp: ${o.label}` }))}
          />
        </Space>
      </Card>

      <Card
        extra={
          coThem ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={moThemMoi}>
              Thêm hồ sơ
            </Button>
          ) : null
        }
      >
        <DataTable<EmployeeRow>
          columns={columns}
          rows={danhSach.data?.items}
          meta={danhSach.data?.meta}
          loading={danhSach.isLoading}
          error={danhSach.error}
          rowKey="publicId"
          onPageChange={pagination.onPageChange}
          // ⛔ HAI câu khác nhau cho HAI trạng thái khác nhau (luật 9). Một câu chung là để người
          //    dùng vừa đặt bộ lọc đọc thành "hệ thống chưa có ai", và người mở lần đầu đọc thành
          //    "bộ lọc của tôi sai".
          emptyText={
            coLoc
              ? 'Không có hồ sơ nào khớp bộ lọc đang đặt'
              : 'Chưa có dữ liệu cán bộ nhân viên — Công ty chưa gửi danh sách (G6-a)'
          }
          // 130+220+200+170+170+150+130+160+140 = 1470.
          scrollX={1470}
        />
      </Card>

      <EmployeeFormModal
        open={bieuMauMo}
        publicId={dangSuaId}
        onClose={() => setBieuMauMo(false)}
        onSaved={() => setBieuMauMo(false)}
      />

      <SensitiveModal
        open={xemBaoMat !== null}
        publicId={xemBaoMat?.publicId ?? null}
        tenCanBo={xemBaoMat ? `${xemBaoMat.code} · ${xemBaoMat.fullName}` : null}
        onClose={() => setXemBaoMat(null)}
      />
    </Space>
  );
}

export default EmployeesPage;
