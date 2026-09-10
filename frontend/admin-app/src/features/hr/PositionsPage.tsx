import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import { type PositionRequest, type PositionView } from './hrVocabulary';

/**
 * Danh mục chức vụ — CN-04.2 (WS-51).
 *
 * <h2>Vì sao đường ĐỌC gác bằng `hr:employee:view`, ⛔ không bằng một quyền riêng</h2>
 *
 * Ô "Chức vụ" của biểu mẫu hồ sơ nạp danh sách bằng **đúng** endpoint này
 * (`PositionController:36-44` giải thích nguyên văn). Đòi một quyền mà người dựng hồ sơ ⛔ không có
 * thì ô ấy rỗng vĩnh viễn và ⛔ **không tạo nổi một hồ sơ đầy đủ nào** — sự cố đã đo được ở WS-28.
 * ⇒ Tuyến mở bằng quyền XEM, ba nút ghi tự khoá theo quyền của **endpoint chúng gọi** (T27.28).
 *
 * <h2>⛔ Mã chức vụ ⛔ không sửa được sau khi tạo</h2>
 *
 * Nó là khoá nối duy nhất giữa danh mục này và hồ sơ CBNV đang giữ chức vụ ấy. Ô nhập bị khoá khi
 * sửa; backend từ chối bản trùng bằng `HR-1002`.
 *
 * <h2>Xoá bị chặn khi còn người giữ — `HR-2002`</h2>
 *
 * Đây là một **chặn có lý do**, ⛔ không phải một lỗi: xoá mềm một chức vụ mà hồ sơ vẫn trỏ tới nó
 * cho ra một cột "Chức vụ" trống ở màn hình danh sách, và triệu chứng ấy đọc như *dữ liệu chưa
 * nhập* chứ ⛔ không như *danh mục vừa bị xoá*.
 */
export function PositionsPage() {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<PositionRequest>();
  const [modalMo, setModalMo] = useState(false);
  const [dangSua, setDangSua] = useState<PositionView | null>(null);

  const coThem = hasPermission('hr:employee:create');
  const coSua = hasPermission('hr:employee:update');
  const coXoa = hasPermission('hr:employee:delete');

  const query = useQuery({
    queryKey: ['hr', 'positions'],
    queryFn: () => api.get<PositionView[]>('/hr/positions'),
  });

  const lamMoi = () => queryClient.invalidateQueries({ queryKey: ['hr', 'positions'] });

  const createMutation = useMutation({
    mutationFn: (data: PositionRequest) => api.post<PositionView>('/hr/positions', data),
    onSuccess: () => {
      message.success('Đã thêm chức vụ');
      setModalMo(false);
      void lamMoi();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không thêm được chức vụ');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: { publicId: string; payload: PositionRequest }) =>
      api.put<PositionView>(`/hr/positions/${data.publicId}`, data.payload),
    onSuccess: () => {
      message.success('Đã cập nhật chức vụ');
      setModalMo(false);
      void lamMoi();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không cập nhật được chức vụ',
      );
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (publicId: string) => api.delete(`/hr/positions/${publicId}`),
    onSuccess: () => {
      message.success('Đã xoá chức vụ');
      void lamMoi();
    },
    // ⚠ `HR-2002` (còn hồ sơ đang giữ) tới đây, và nó là **câu trả lời nghiệp vụ** chứ ⛔ không
    //   phải một trục trặc: `error-map` đã dịch sẵn thành *"hãy chuyển họ sang chức vụ khác trước
    //   khi xoá"*. Thiếu nhánh này thì người dùng bấm Xoá và ⛔ không có gì xảy ra, ⛔ không có gì
    //   báo — đúng lớp lỗi 01/09 mà `moiLuotGhiPhaiBaoLoi.test.ts` sinh ra để đóng.
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được chức vụ'),
  });

  const moTaoMoi = () => {
    setDangSua(null);
    form.resetFields();
    form.setFieldsValue({ sortOrder: 10, active: true });
    setModalMo(true);
  };

  const moSua = (record: PositionView) => {
    setDangSua(record);
    form.resetFields();
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      positionGroup: record.positionGroup ?? undefined,
      description: record.description ?? undefined,
      sortOrder: record.sortOrder ?? 0,
      active: record.active ?? true,
    });
    setModalMo(true);
  };

  const luu = async () => {
    try {
      const values = await form.validateFields();
      if (dangSua) {
        updateMutation.mutate({ publicId: dangSua.publicId, payload: values });
      } else {
        createMutation.mutate(values);
      }
    } catch {
      // Biểu mẫu tự hiện lỗi từng trường. ⛔ Đừng để lời hứa này rơi ra ngoài: `Modal.onOk` KHÔNG
      // chờ giá trị trả về, nên một `validateFields()` hỏng trở thành unhandled rejection — một
      // dòng đỏ ở console cho một luồng HOÀN TOÀN bình thường (người dùng bỏ trống ô bắt buộc).
    }
  };

  const columns: ColumnsType<PositionView> = [
    { title: 'STT', dataIndex: 'sortOrder', width: 70, align: 'center' },
    { title: 'Mã', dataIndex: 'code', width: 140 },
    { title: 'Tên chức vụ', dataIndex: 'name', width: 240, ellipsis: true },
    {
      title: 'Nhóm',
      dataIndex: 'positionGroup',
      width: 180,
      render: (nhom: string | null) => (nhom ? <Tag>{nhom}</Tag> : null),
    },
    { title: 'Mô tả', dataIndex: 'description', width: 260, ellipsis: true },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: 120,
      render: (active: boolean | null) =>
        active === false ? <Tag>Ngừng dùng</Tag> : <Tag color="green">Đang dùng</Tag>,
    },
    {
      title: '',
      key: 'thao-tac',
      width: 110,
      align: 'right',
      render: (_, record) => (
        <Space size={0}>
          {coSua && <Button type="text" icon={<EditOutlined />} onClick={() => moSua(record)} />}
          {coXoa && (
            <Popconfirm
              title="Xoá chức vụ này?"
              description="Bị từ chối nếu còn hồ sơ cán bộ đang giữ chức vụ."
              okText="Xoá"
              cancelText="Huỷ"
              onConfirm={() => deleteMutation.mutate(record.publicId)}
            >
              <Button type="text" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="Danh mục chức vụ"
      extra={
        coThem ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={moTaoMoi}>
            Thêm chức vụ
          </Button>
        ) : null
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Danh mục dùng chung toàn Công ty"
        description={
          <>
            &quot;Trưởng phòng&quot; ở Xí nghiệp 1 và Xí nghiệp 2 là <b>cùng một</b> chức vụ — danh
            mục này ⛔ không chia theo đơn vị. Ô <b>Nhóm</b> để Công ty tự đặt cách gom (lãnh đạo,
            chuyên môn, phục vụ…); hệ thống ⛔ không áp danh sách nào.
          </>
        }
      />

      <Table
        rowKey="publicId"
        loading={query.isLoading}
        dataSource={query.data ?? []}
        columns={columns}
        pagination={false}
        locale={{ emptyText: 'Chưa có chức vụ nào — bấm “Thêm chức vụ” để khai danh mục' }}
        // 70+140+240+180+260+120+110 = 1120. Thiếu `scroll` thì `rc-table` chọn
        // `tableLayout:'auto'` và `<col width>` chỉ còn là gợi ý — cột bị bóp còn vài chục px thay
        // vì bảng cuộn ngang (`bangCuonNgang.test.ts`).
        scroll={{ x: 1120 }}
      />

      <Modal
        open={modalMo}
        title={dangSua ? `Sửa chức vụ ${dangSua.code}` : 'Thêm chức vụ'}
        onOk={() => void luu()}
        onCancel={() => setModalMo(false)}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="code"
            label="Mã chức vụ"
            rules={[{ required: true, message: 'Nhập mã chức vụ' }, { max: 50 }]}
            extra={
              dangSua
                ? '⛔ Không sửa được: hồ sơ cán bộ đang trỏ tới mã này.'
                : 'Sau khi lưu sẽ không sửa được.'
            }
          >
            <Input placeholder="TRUONG_PHONG" disabled={!!dangSua} />
          </Form.Item>
          <Form.Item
            name="name"
            label="Tên chức vụ"
            rules={[{ required: true, message: 'Nhập tên chức vụ' }, { max: 255 }]}
          >
            <Input placeholder="Trưởng phòng" />
          </Form.Item>
          <Form.Item
            name="positionGroup"
            label="Nhóm chức vụ"
            rules={[{ max: 100 }]}
            extra="Do Công ty tự đặt — hệ thống không áp danh sách cố định."
          >
            <Input placeholder="Lãnh đạo" />
          </Form.Item>
          <Form.Item name="description" label="Mô tả" rules={[{ max: 500 }]}>
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
          <Form.Item name="sortOrder" label="Thứ tự hiển thị">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Đang dùng" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
        Ngừng dùng một chức vụ ⛔ không gỡ nó khỏi những hồ sơ đang giữ — nó chỉ thôi xuất hiện ở
        danh sách chọn của hồ sơ mới.
      </Typography.Paragraph>
    </Card>
  );
}

export default PositionsPage;
