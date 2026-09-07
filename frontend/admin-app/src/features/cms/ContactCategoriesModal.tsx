import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Empty, Form, Input, InputNumber, Modal, Space, Switch, Table } from 'antd';

import { ApiClientError } from '@/shared/apiClient';

import { cmsApi } from './api';
import { type ContactCategoryView } from './types';

/**
 * Quản lý danh mục **phân loại liên hệ** — CN-01.4, CLAUDE.md quy tắc 16.
 *
 * <h3>Vì sao một màn hình CRUD cho danh mục vài dòng</h3>
 *
 * Vì đây là danh mục **của Công ty**, không phải của lập trình viên. Một enum trong mã nghĩa là
 * thêm mục "Kiến nghị về giá nước" phải chờ một lượt deploy — và trong lúc chờ, cán bộ sẽ gán bừa
 * vào một mục gần đúng, làm hỏng chính con số mà việc phân loại sinh ra để có.
 *
 * <h3>⛔ Danh mục ra đời RỖNG — và màn hình nói thẳng điều đó</h3>
 *
 * Chưa có văn bản nào của Công ty cấp danh sách này. Ô trống ở đây kèm **lý do**, ⛔ không phải một
 * bảng dấu gạch trông như đã cấu hình xong (CLAUDE.md quy tắc 16 về "số 0 là một câu khẳng định").
 *
 * <h3>Xoá là chuyện khó, tắt là chuyện dễ</h3>
 *
 * Phân loại đã gán cho liên hệ nào thì backend trả **CMS-2020** khi xoá. Đường đúng là **tắt**:
 * biến mất khỏi ô chọn, còn nguyên trong lịch sử — nếu không thì báo cáo theo phân loại của quý
 * trước đổi kết quả mà ⛔ không ai hiểu vì sao.
 */
export function ContactCategoriesModal({
  open,
  onClose,
  coQuyenGhi,
  lyDoThieuQuyen,
}: {
  open: boolean;
  onClose: () => void;
  coQuyenGhi: boolean;
  lyDoThieuQuyen: string;
}) {
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<{ code: string; name: string; sortOrder: number }>();

  const danhSach = useQuery({
    queryKey: ['cms', 'contact-categories'],
    queryFn: () => cmsApi.listContactCategories(),
    enabled: open,
  });

  const lamMoi = () => {
    void queryClient.invalidateQueries({ queryKey: ['cms', 'contact-categories'] });
    // ⚠ Danh sách liên hệ mang TÊN phân loại đã tra ở backend — đổi tên ở đây mà ⛔ không xoá đệm
    //   thì bảng bên ngoài còn hiện tên cũ cho tới lượt tải lại trang.
    void queryClient.invalidateQueries({ queryKey: ['cms', 'contacts'] });
  };

  const bao = (caught: unknown, macDinh: string) =>
    message.error(caught instanceof ApiClientError ? caught.message : macDinh);

  const them = useMutation({
    mutationFn: (v: { code: string; name: string; sortOrder: number }) =>
      cmsApi.createContactCategory(v),
    onSuccess: () => {
      form.resetFields();
      lamMoi();
      message.success('Đã thêm phân loại');
    },
    onError: (caught: unknown) => bao(caught, 'Không thêm được phân loại'),
  });

  const sua = useMutation({
    mutationFn: (v: ContactCategoryView) =>
      cmsApi.updateContactCategory(v.publicId, {
        name: v.name,
        active: v.active,
        sortOrder: v.sortOrder,
      }),
    onSuccess: lamMoi,
    onError: (caught: unknown) => bao(caught, 'Không cập nhật được phân loại'),
  });

  const xoa = useMutation({
    mutationFn: (publicId: string) => cmsApi.deleteContactCategory(publicId),
    onSuccess: () => {
      lamMoi();
      message.success('Đã xoá phân loại');
    },
    // ⛔ CMS-2020 khi phân loại còn liên hệ đang gán — thông điệp đến từ `error-map`, ⛔ không dựng
    //   lại một câu riêng ở đây (luật 14: một luật ở hai nơi nhớ là một luật sẽ lệch).
    onError: (caught: unknown) => bao(caught, 'Không xoá được phân loại'),
  });

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title="Phân loại liên hệ"
      footer={<Button onClick={onClose}>Đóng</Button>}
      width={720}
      destroyOnHidden
    >
      <Table<ContactCategoryView>
        rowKey="publicId"
        size="small"
        loading={danhSach.isPending}
        dataSource={danhSach.data ?? []}
        pagination={false}
        // Bốn cột cố định đã ăn 420px; hẹp hơn thì CUỘN NGANG, ⛔ không bóp cột Tên về
        // một ký tự mỗi dòng. Canh bởi `bangCuonNgang.test.ts` — và nó bắt được ngay
        // lượt đầu tiên của bảng này.
        scroll={{ x: 620 }}
        locale={{
          emptyText: (
            <Empty
              description={
                'Chưa có phân loại nào. Danh sách này do Công ty tự khai — ' +
                'thêm một mục ở dưới, không cần cài đặt lại hệ thống.'
              }
            />
          ),
        }}
        columns={[
          { title: 'Mã', dataIndex: 'code', key: 'code', width: 160 },
          { title: 'Tên', dataIndex: 'name', key: 'name' },
          {
            title: 'Thứ tự',
            dataIndex: 'sortOrder',
            key: 'sortOrder',
            width: 90,
          },
          {
            title: 'Đang dùng',
            dataIndex: 'active',
            key: 'active',
            width: 110,
            render: (dangDung: boolean, r) => (
              <Switch
                size="small"
                checked={dangDung}
                disabled={!coQuyenGhi || sua.isPending}
                onChange={(v) => sua.mutate({ ...r, active: v })}
              />
            ),
          },
          {
            title: '',
            key: 'thaoTac',
            width: 60,
            render: (_: unknown, r) => (
              <Button
                type="text"
                danger
                size="small"
                icon={<DeleteOutlined />}
                disabled={!coQuyenGhi}
                title={coQuyenGhi ? undefined : lyDoThieuQuyen}
                onClick={() =>
                  modal.confirm({
                    title: `Xoá phân loại "${r.name}"?`,
                    content:
                      'Chỉ xoá được khi chưa liên hệ nào gán phân loại này. Đã dùng rồi thì tắt ' +
                      'nó đi — liên hệ cũ vẫn giữ được phân loại, và báo cáo cũ vẫn đọc lại được.',
                    okText: 'Xoá',
                    okButtonProps: { danger: true },
                    cancelText: 'Huỷ',
                    onOk: () => xoa.mutateAsync(r.publicId),
                  })
                }
              />
            ),
          },
        ]}
      />

      <Form
        form={form}
        layout="inline"
        style={{ marginTop: 16 }}
        initialValues={{ sortOrder: 0 }}
        onFinish={(v) => them.mutate(v)}
      >
        <Form.Item
          name="code"
          rules={[
            { required: true, message: 'Nhập mã' },
            {
              // ⚠ Cùng một biểu thức với `ck_contact_categories_code` ở CSDL và với
              //   `ContactCategoryService.chuanHoaMa`. Ba nơi là hai nơi quá nhiều — nhưng hai tầng
              //   dưới chặn đường ghi thẳng, còn tầng này trả lời được người dùng trước khi gửi.
              pattern: /^[A-Za-z][A-Za-z0-9_]{1,39}$/,
              message: 'Chữ cái đầu, sau đó chữ/số/gạch dưới, 2–40 ký tự',
            },
          ]}
        >
          <Input placeholder="MÃ (VD: KIEN_NGHI)" style={{ width: 200 }} disabled={!coQuyenGhi} />
        </Form.Item>
        <Form.Item name="name" rules={[{ required: true, message: 'Nhập tên' }]}>
          <Input placeholder="Tên hiển thị" style={{ width: 240 }} disabled={!coQuyenGhi} />
        </Form.Item>
        <Form.Item name="sortOrder">
          <InputNumber min={0} max={999} style={{ width: 90 }} disabled={!coQuyenGhi} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button
              type="primary"
              htmlType="submit"
              icon={<PlusOutlined />}
              loading={them.isPending}
              disabled={!coQuyenGhi}
              title={coQuyenGhi ? undefined : lyDoThieuQuyen}
            >
              Thêm
            </Button>
          </Space>
        </Form.Item>
      </Form>
    </Modal>
  );
}

export default ContactCategoriesModal;
