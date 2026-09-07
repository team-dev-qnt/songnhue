import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
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
  message,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { type MeasurementType, type MeasurementTypeRequest } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

/**
 * Danh mục loại chỉ số quan trắc — CN-03.1 (T28.1).
 *
 * ⚠ Ô **Đơn vị** là đơn vị ĐÃ CHUẨN HOÁ trong CSDL, không phải đơn vị nguồn trả về: nguồn
 * `bhh40.net` trả mực nước bằng **cm**, hệ thống lưu **m**. Người vận hành sửa ô này thành
 * "cm" sẽ không làm số liệu đổi — chỉ làm nhãn nói sai. Ghi chú ngay trên biểu mẫu vì đây
 * là chỗ hiểu nhầm rẻ nhất để tránh và đắt nhất để phát hiện.
 */
export function MeasurementTypesPage() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<MeasurementTypeRequest>();
  const [modalVisible, setModalVisible] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);

  const coQuanLy = hasPermission('hyd:station:manage');

  const query = useQuery({
    queryKey: ['hyd', 'measurement-types'],
    queryFn: () => api.get<MeasurementType[]>('/hyd/measurement-types'),
  });

  const lamMoi = () => queryClient.invalidateQueries({ queryKey: ['hyd', 'measurement-types'] });

  const createMutation = useMutation({
    mutationFn: (data: MeasurementTypeRequest) =>
      api.post<MeasurementType>('/hyd/measurement-types', data),
    onSuccess: () => {
      message.success('Đã thêm loại chỉ số');
      setModalVisible(false);
      void lamMoi();
    },
    // ⭐ 01/09 (T28.31): mutation này TRƯỚC ĐÂY không có `onError` nào. HYD-1002/2005/2006 và cả
    //    403 đều im lặng tuyệt đối — người dùng bấm Lưu, không có gì xảy ra và không có gì báo.
    //    Mã lỗi đã khớp đủ bốn nơi từ T28.10, nhưng không màn hình nào hiện chúng.
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không thêm được loại chỉ số',
      );
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: { id: string; payload: MeasurementTypeRequest }) =>
      api.put<MeasurementType>(`/hyd/measurement-types/${data.id}`, data.payload),
    onSuccess: () => {
      message.success('Đã cập nhật');
      setModalVisible(false);
      void lamMoi();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không cập nhật được loại chỉ số',
      );
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/hyd/measurement-types/${id}`),
    onSuccess: () => {
      message.success('Đã xoá');
      void lamMoi();
    },
    onError: (caught: unknown) =>
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không xoá được loại chỉ số',
      ),
  });

  const moTaoMoi = () => {
    setEditingId(null);
    form.resetFields();
    form.setFieldsValue({ valueScale: 3, sortOrder: 10, active: true });
    setModalVisible(true);
  };

  const moSua = (record: MeasurementType) => {
    setEditingId(record.id);
    form.resetFields();
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      unit: record.unit,
      valueScale: record.valueScale,
      sortOrder: record.sortOrder,
      active: record.active,
      description: record.description ?? undefined,
    });
    setModalVisible(true);
  };

  const luu = async () => {
    try {
      const values = await form.validateFields();
      if (editingId) {
        updateMutation.mutate({ id: editingId, payload: values });
      } else {
        createMutation.mutate(values);
      }
    } catch {
      // Form tự hiện lỗi từng trường.
    }
  };

  const columns: ColumnsType<MeasurementType> = [
    { title: 'STT', dataIndex: 'sortOrder', width: 70, align: 'center' },
    { title: 'Mã', dataIndex: 'code', width: 140 },
    { title: 'Tên loại chỉ số', dataIndex: 'name', width: 240, ellipsis: true },
    {
      title: 'Đơn vị lưu',
      dataIndex: 'unit',
      width: 110,
      render: (unit: string) => <Tag>{unit}</Tag>,
    },
    {
      title: 'Số lẻ',
      dataIndex: 'valueScale',
      width: 80,
      align: 'center',
    },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: 120,
      render: (active: boolean) => (active ? <Tag color="green">Đang dùng</Tag> : <Tag>Ngừng</Tag>),
    },
    {
      title: '',
      width: 110,
      align: 'right',
      render: (_, record) =>
        coQuanLy ? (
          <Space>
            <Button type="text" icon={<EditOutlined />} onClick={() => moSua(record)} />
            <Popconfirm
              title="Xoá loại chỉ số này?"
              description="Chặn nếu còn điểm đo đang gắn."
              onConfirm={() => deleteMutation.mutate(record.id)}
            >
              <Button type="text" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  return (
    <Card
      title="Loại chỉ số quan trắc"
      extra={
        coQuanLy ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={moTaoMoi}>
            Thêm loại chỉ số
          </Button>
        ) : null
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Đơn vị ở đây là đơn vị hệ thống LƯU, không phải đơn vị nguồn trả về"
        description={
          <>
            Nguồn <code>bhh40.net</code> trả mực nước bằng <b>cm</b>; hệ thống quy đổi và lưu bằng{' '}
            <b>m</b> ngay khi nhận. Sửa ô này thành &quot;cm&quot; không làm số liệu đổi — chỉ làm
            nhãn nói sai.
            <br />
            <b>Lượng mưa</b> vẫn nằm trong danh mục dù nguồn hiện tại chưa cung cấp — cột lượng mưa
            của biểu tổng hợp <b>nhập tay</b> cho tới khi có nguồn.
          </>
        }
      />
      <Table
        rowKey="id"
        loading={query.isLoading}
        dataSource={query.data ?? []}
        columns={columns}
        pagination={false}
        // 70+140+240+110+80+120+110 = 870. Xem chú thích cột "Địa chỉ" ở `ApiSourcesPage` để
        // biết vì sao thiếu `scroll` là bóp chữ chứ không phải cuộn.
        scroll={{ x: 870 }}
      />

      <Modal
        open={modalVisible}
        title={editingId ? 'Sửa loại chỉ số' : 'Thêm loại chỉ số'}
        onOk={luu}
        onCancel={() => setModalVisible(false)}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="code"
            label="Mã"
            rules={[{ required: true, message: 'Nhập mã loại chỉ số' }]}
          >
            <Input placeholder="MUC_NUOC" disabled={!!editingId} />
          </Form.Item>
          <Form.Item name="name" label="Tên" rules={[{ required: true, message: 'Nhập tên' }]}>
            <Input placeholder="Mực nước" />
          </Form.Item>
          <Form.Item
            name="unit"
            label="Đơn vị hệ thống lưu"
            rules={[{ required: true, message: 'Nhập đơn vị' }]}
            extra="Đơn vị sau khi chuẩn hoá, không phải đơn vị nguồn trả về."
          >
            <Input placeholder="m" />
          </Form.Item>
          <Form.Item
            name="valueScale"
            label="Số chữ số thập phân"
            extra="Mực nước 3 (tới mm), lượng mưa 1."
          >
            <InputNumber min={0} max={6} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="sortOrder" label="Thứ tự hiển thị">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Đang dùng" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="description" label="Ghi chú">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
        Mã loại chỉ số không sửa được sau khi tạo — nó là khoá nối trong công thức tính và trong báo
        cáo đã xuất.
      </Typography.Paragraph>
    </Card>
  );
}

export default MeasurementTypesPage;
