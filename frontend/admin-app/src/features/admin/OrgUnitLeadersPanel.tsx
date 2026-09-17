import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Card,
  Form,
  type FormInstance,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Typography,
} from 'antd';
import { useLayoutEffect, useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { type OrgUnitLeaderRow } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

/**
 * Danh bạ lãnh đạo của một đơn vị — CR-25, CR-26.
 *
 * <h2>⚠⚠ Vì sao màn hình này tồn tại</h2>
 *
 * Bảng `org_unit_leaders` dựng ngày 27/08/2026 kèm repository và endpoint công khai đọc nó ra cổng.
 * Đo lại ngày 28/8: **không có đường ghi nào** — không controller, không màn hình. Trang
 * `/gioi-thieu/lanh-dao` của cổng và cột "Giám đốc XN" của bảng Xí nghiệp đọc một bảng mà không ai
 * điền được, và cả hai trang ấy đã dựng xong, đã có bài kiểm, đã lên staging.
 *
 * Đây là vụ **thứ ba** cùng hình dạng trong một tuần (`categories.visible` T24.25 ·
 * `OrgUnit.address/phone/email`): *việc làm xong nửa đường trông y hệt việc làm xong* — luật 19.
 *
 * <h2>Điều gì hiện ra công khai, và điều gì không</h2>
 *
 * Cột **Hiện trên cổng** là công tắc `active`. Tắt ≠ xoá: người chuyển công tác thì dòng rời khỏi
 * cổng ngay, nhưng còn nguyên trong bảng để đối chiếu và để bật lại nếu tắt nhầm. Bộ lọc nằm ở
 * repository chứ không ở nơi gọi, nên không có đường đọc nào quên lọc (quy tắc 12).
 *
 * ⛔ Email nhập ở đây **không** hiện trên cổng: `SubsidiaryRow` của endpoint công khai chỉ mang
 * tên, chức danh và điện thoại (CR-25 ghi rõ bảng 3 cột). Ô email phục vụ liên hệ nội bộ.
 */
export function OrgUnitLeadersPanel({ orgUnitPublicId }: { orgUnitPublicId: string }) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('adm:org-unit:manage');

  const [editing, setEditing] = useState<OrgUnitLeaderRow | null>(null);
  const [creating, setCreating] = useState(false);

  const goc = `/org-units/${orgUnitPublicId}/leaders`;
  const khoa = ['org-units', orgUnitPublicId, 'leaders'];

  const danhSach = useQuery({
    queryKey: khoa,
    queryFn: () => api.get<OrgUnitLeaderRow[]>(goc),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: khoa });

  const doiTrangThai = useMutation({
    mutationFn: ({ publicId, active }: { publicId: string; active: boolean }) =>
      api.put<OrgUnitLeaderRow>(`${goc}/${publicId}/active`, { active }),
    onSuccess: async () => {
      await invalidate();
    },
    onError: (caught: unknown) => {
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không đổi được trạng thái',
      );
    },
  });

  const xoa = useMutation({
    mutationFn: (publicId: string) => api.delete<void>(`${goc}/${publicId}`),
    onSuccess: async () => {
      message.success('Đã xoá dòng danh bạ');
      await invalidate();
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được');
    },
  });

  return (
    <Card
      size="small"
      title="Lãnh đạo công bố trên cổng"
      style={{ marginTop: 16 }}
      loading={danhSach.isLoading}
      extra={
        canManage && (
          <Button size="small" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
            Thêm
          </Button>
        )
      }
    >
      {/* ⚠ Cổng công khai dựng lại trang theo chu kỳ ISR (`revalidate = 300`), và cơ chế bắn
          xoá cache (`PortalCache`) nằm ở module `content` — `org_units` thuộc `core` nên không
          gọi được nó qua ranh giới module (quy tắc 6). Hệ quả ĐO ĐƯỢC: sửa ở đây thì cổng đổi
          theo sau **tối đa 5 phút**. Nói thẳng ra ở đây, vì người nhập liệu không thấy thay đổi
          ngay sẽ tưởng mình lưu hỏng và nhập lại. Nợ T25.15. */}
      <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 8 }}>
        Thay đổi ở đây hiện lên cổng công khai sau tối đa 5 phút.
      </Typography.Paragraph>

      <Table<OrgUnitLeaderRow>
        size="small"
        rowKey="publicId"
        // Bề ngang tối thiểu: hẹp hơn thì CUỘN NGANG, không bóp chữ.
        // Vì sao cần — xem chú thích cột "Địa chỉ" ở `features/hydro/ApiSourcesPage.tsx`.
        scroll={{ x: 780 }}
        pagination={false}
        dataSource={danhSach.data ?? []}
        locale={{
          emptyText:
            'Chưa có dòng nào. Trang "Lãnh đạo Công ty" và cột "Giám đốc XN" trên cổng đọc đúng bảng này.',
        }}
        columns={[
          { title: 'Họ và tên', dataIndex: 'fullName' },
          { title: 'Chức danh', dataIndex: 'title' },
          {
            title: 'Điện thoại',
            dataIndex: 'phone',
            // ⛔ Ô trống hiện dấu gạch, KHÔNG hiện một số mặc định nào — luật 16: chưa công bố số
            //    phải phân biệt được với đã công bố.
            render: (v: string | null) => v ?? '—',
          },
          {
            title: 'Hiện trên cổng',
            dataIndex: 'active',
            width: 120,
            render: (active: boolean, row) => (
              <Switch
                size="small"
                checked={active}
                disabled={!canManage || doiTrangThai.isPending}
                onChange={(value) => doiTrangThai.mutate({ publicId: row.publicId, active: value })}
              />
            ),
          },
          ...(canManage
            ? [
                {
                  title: '',
                  width: 88,
                  render: (_: unknown, row: OrgUnitLeaderRow) => (
                    <Space size={4}>
                      {/* ⛔ Nút chỉ có icon thiếu nhãn được trình đọc màn hình đọc thành
                          "button" — trống rỗng, và hai nút liền nhau ⛔ phân biệt được (T63.9).
                          Nhãn mang cả HỌ TÊN vì bảng nhiều dòng: "Sửa" một mình ⛔ nói dòng nào,
                          mà nhầm dòng ở đây là sửa hồ sơ một người khác. */}
                      <Button
                        size="small"
                        aria-label={`Sửa dòng danh bạ ${row.fullName}`}
                        icon={<EditOutlined />}
                        onClick={() => setEditing(row)}
                      />
                      <Popconfirm
                        title="Xoá dòng này?"
                        okText="Xoá"
                        cancelText="Huỷ"
                        onConfirm={() => xoa.mutate(row.publicId)}
                      >
                        <Button
                          size="small"
                          danger
                          aria-label={`Xoá dòng danh bạ ${row.fullName}`}
                          icon={<DeleteOutlined />}
                        />
                      </Popconfirm>
                    </Space>
                  ),
                },
              ]
            : []),
        ]}
      />

      <LeaderModal
        goc={goc}
        open={creating || editing !== null}
        row={editing}
        onClose={() => {
          setCreating(false);
          setEditing(null);
        }}
        onDone={invalidate}
      />
    </Card>
  );
}

interface LeaderForm {
  fullName: string;
  title: string;
  phone?: string;
  email?: string;
  sortOrder?: number;
}

function LeaderModal({
  goc,
  open,
  row,
  onClose,
  onDone,
}: {
  goc: string;
  open: boolean;
  row: OrgUnitLeaderRow | null;
  onClose: () => void;
  onDone: () => Promise<void>;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<LeaderForm>();

  const luu = useMutation({
    mutationFn: (values: LeaderForm) =>
      row
        ? api.put<OrgUnitLeaderRow>(`${goc}/${row.publicId}`, values)
        : api.post<OrgUnitLeaderRow>(goc, values),
    onSuccess: async () => {
      message.success(row ? 'Đã cập nhật' : 'Đã thêm dòng danh bạ');
      form.resetFields();
      onClose();
      await onDone();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được');
    },
  });

  return (
    <Modal
      open={open}
      title={row ? 'Sửa dòng danh bạ' : 'Thêm lãnh đạo'}
      okText="Lưu"
      cancelText="Huỷ"
      confirmLoading={luu.isPending}
      onCancel={onClose}
      onOk={() => void form.submit()}
      destroyOnClose
    >
      <BieuMauDanhBa
        // ⛔ `key` ép React dựng lại thân biểu mẫu ngay khi đổi dòng — xem javadoc của nó.
        key={row?.publicId ?? 'moi'}
        khoa={row?.publicId ?? 'moi'}
        form={form}
        row={row}
        onFinish={(values) => luu.mutate(values)}
      />
    </Modal>
  );
}

/**
 * Thân biểu mẫu danh bạ — tách ra để {@code key} ép dựng lại khi đổi dòng.
 *
 * <h2>⛔⛔⛔ Vì sao ⛔ để {@code initialValues} một mình — T51.12 lần thứ NĂM</h2>
 *
 * <p>{@code OrgUnitLeadersPanel} render {@code <LeaderModal open={…} row={editing} …/>} <b>vô điều
 * kiện</b>, và {@code Form.useForm()} nằm ở component NGOÀI {@code Modal} (AntD đòi vậy để
 * {@code Modal.onOk} gọi được {@code submit}). Nên kho giá trị sống lâu hơn hộp thoại, còn
 * {@code rc-field-form} chỉ áp {@code initialValues} khi {@code init}.
 *
 * <p>⚠ {@code destroyOnClose} + {@code preserve={false}} là <b>hai</b> biện pháp phòng và chúng
 * <b>⛔ cộng lại thành an toàn</b> (T53.7): {@code destroyOnClose} chỉ tháo cây con <b>sau khi hoạt
 * ảnh đóng chạy xong</b>, nên mở lại trước lúc ấy là kho giá trị còn nguyên của dòng TRƯỚC. Đo được
 * trước lượt vá này (bài {@code danhBaLanhDaoVongKhuHoi.test.tsx}, vế A → đóng → B): ô <i>Điện
 * thoại liên hệ</i> của <i>Trần Thị Hoà</i> vẫn hiện số của <i>Nguyễn Văn Thắng</i>, và một lượt Lưu
 * ghi tên/chức danh/điện thoại của người A đè lên bản ghi của người B.
 *
 * <p>⛔⛔ Hậu quả ở màn hình này ⛔ dừng trong nội bộ: {@code org_unit_leaders} là nguồn <b>duy
 * nhất</b> của trang <i>Lãnh đạo Công ty</i> (CR-25) và cột <i>"Giám đốc XN"</i> (CR-26) trên
 * <b>cổng công khai</b> — một lượt trộn là cổng đăng sai chức danh và số điện thoại của một người
 * có thật, dưới tên Công ty, sau tối đa 5 phút.
 *
 * <p>⇒ {@code useLayoutEffect} đặt giá trị <b>tường minh</b>: {@code resetFields()} gỡ cờ *đã chạm*
 * và lỗi hợp lệ hoá của lượt trước, rồi {@code setFieldsValue} ghi đè bằng dòng hiện tại.
 * {@code useLayoutEffect} chứ ⛔ {@code useEffect} — nó chạy TRƯỚC lượt vẽ nên người dùng ⛔ bao giờ
 * thấy một khung hình mang dữ liệu của người khác.
 *
 * <p>⛔⛔ Và ⛔ thêm {@code clearOnDestroy}: lượt dọn của cây con CŨ chạy <b>sau</b>
 * {@code useLayoutEffect} của cây con MỚI ⇒ ô ra RỖNG (T53.7 đã trả giá đủ ba lượt). MỘT cơ chế,
 * tường minh, có bài kiểm — ⛔ ba cơ chế chồng nhau.
 */
function BieuMauDanhBa({
  khoa,
  form,
  row,
  onFinish,
}: {
  khoa: string;
  form: FormInstance<LeaderForm>;
  row: OrgUnitLeaderRow | null;
  onFinish: (values: LeaderForm) => void;
}) {
  // ⚠ Nạp ĐỦ mọi trường của dòng đang sửa. Biểu mẫu nạp thiếu một trường thì mỗi lượt Lưu ghi đè
  //   giá trị đang có bằng rỗng, và ⛔ có thông báo nào — `ganTruong` quy rỗng về NULL, nên ô điện
  //   thoại **biến khỏi cổng** và người đọc hiểu là "chưa công bố số" (luật 16 ở chiều ngược).
  useLayoutEffect(() => {
    form.resetFields();
    form.setFieldsValue(
      row
        ? {
            fullName: row.fullName,
            title: row.title,
            phone: row.phone ?? undefined,
            email: row.email ?? undefined,
            sortOrder: row.sortOrder,
          }
        : { sortOrder: 0 },
    );
    // `khoa` là danh tính dòng; `row` là object dựng lại mỗi lượt render nên ⛔ đưa vào deps.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [khoa, form]);

  return (
    <Form<LeaderForm> form={form} layout="vertical" preserve={false} onFinish={onFinish}>
      <Form.Item
        name="fullName"
        label="Họ và tên"
        rules={[{ required: true, message: 'Bắt buộc' }]}
      >
        <Input />
      </Form.Item>
      <Form.Item
        name="title"
        label="Chức danh"
        rules={[{ required: true, message: 'Bắt buộc' }]}
        extra="Hiện nguyên văn ở cột 2 bảng Lãnh đạo Công ty trên cổng"
      >
        <Input />
      </Form.Item>
      <Form.Item name="phone" label="Điện thoại liên hệ">
        <Input />
      </Form.Item>
      <Form.Item
        name="email"
        label="Email"
        rules={[{ type: 'email', message: 'Email không hợp lệ' }]}
      >
        <Input />
      </Form.Item>
      <Form.Item name="sortOrder" label="Thứ tự hiển thị" extra="Số nhỏ đứng trước">
        <InputNumber min={0} style={{ width: '100%' }} />
      </Form.Item>
    </Form>
  );
}
