import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Card,
  ColorPicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
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
import { CONSTRUCTION_STATUS } from '@/components/business/statusVocabulary';
import {
  type OperationStatusCode,
  type OperationStatusCodeCreateRequest,
  type OperationStatusCodeUpdateRequest,
} from '@/shared/api-types';
import { api } from '@/shared/apiClient';

export function OperationStatusCodesPage() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<OperationStatusCodeCreateRequest>();

  const [modalVisible, setModalVisible] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const hasParameter = Form.useWatch('hasParameter', form);

  const query = useQuery({
    queryKey: ['ops', 'operation-status-codes'],
    queryFn: () => api.get<OperationStatusCode[]>('/ops/operation-status-codes'),
  });

  const createMutation = useMutation({
    mutationFn: (data: OperationStatusCodeCreateRequest) =>
      api.post<OperationStatusCode>('/ops/operation-status-codes', data),
    onSuccess: () => {
      message.success('Thêm mới thành công');
      setModalVisible(false);
      queryClient.invalidateQueries({ queryKey: ['ops', 'operation-status-codes'] });
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: { id: number; payload: OperationStatusCodeUpdateRequest }) =>
      api.put<OperationStatusCode>(`/ops/operation-status-codes/${data.id}`, data.payload),
    onSuccess: () => {
      message.success('Cập nhật thành công');
      setModalVisible(false);
      queryClient.invalidateQueries({ queryKey: ['ops', 'operation-status-codes'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/ops/operation-status-codes/${id}`),
    onSuccess: () => {
      message.success('Xoá thành công');
      queryClient.invalidateQueries({ queryKey: ['ops', 'operation-status-codes'] });
    },
  });

  const openCreateModal = () => {
    setEditingId(null);
    form.resetFields();
    form.setFieldsValue({
      colorHex: '#1890ff',
      active: true,
      hasParameter: false,
      sortOrder: 1,
    });
    setModalVisible(true);
  };

  const openEditModal = (record: OperationStatusCode) => {
    setEditingId(record.id);
    form.resetFields();
    form.setFieldsValue({
      ...record,
      parameterUnit: record.parameterUnit ?? undefined,
      mappedStatus: record.mappedStatus ?? undefined,
    });
    setModalVisible(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      // Chuyển đổi ColorPicker object thành hex string nếu cần
      if (typeof values.colorHex !== 'string') {
        // @ts-expect-error ColorPicker trả về object nếu bị thay đổi
        values.colorHex = values.colorHex.toHexString();
      }

      if (editingId) {
        updateMutation.mutate({ id: editingId, payload: values });
      } else {
        createMutation.mutate(values);
      }
    } catch {
      // Validate failed
    }
  };

  const columns: ColumnsType<OperationStatusCode> = [
    {
      title: 'STT',
      dataIndex: 'sortOrder',
      width: 80,
      align: 'center',
    },
    {
      title: 'Mã',
      dataIndex: 'code',
      width: 120,
    },
    {
      title: 'Tên trạng thái',
      dataIndex: 'name',
    },
    {
      title: 'Màu sắc',
      dataIndex: 'colorHex',
      width: 120,
      render: (val: string) => (
        <Space>
          <div
            style={{
              width: 16,
              height: 16,
              backgroundColor: val,
              border: '1px solid #d9d9d9',
              borderRadius: 2,
            }}
          />
          {val}
        </Space>
      ),
    },
    {
      title: 'Tham số',
      dataIndex: 'hasParameter',
      width: 120,
      render: (val: boolean, row) =>
        val ? <Tag color="blue">{row.parameterUnit || 'Có'}</Tag> : '-',
    },
    {
      title: 'Map trạng thái HT',
      dataIndex: 'mappedStatus',
      width: 160,
      render: (val: string | null) => (val ? CONSTRUCTION_STATUS[val]?.label || val : '-'),
    },
    {
      title: 'Sử dụng',
      dataIndex: 'active',
      width: 100,
      align: 'center',
      render: (val: boolean) =>
        val ? <Tag color="success">Có</Tag> : <Tag color="default">Không</Tag>,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 120,
      align: 'center',
      render: (_, record) => (
        <Space>
          {hasPermission('ops:operation-status-code:manage') && (
            <>
              <Button type="text" icon={<EditOutlined />} onClick={() => openEditModal(record)} />
              <Popconfirm
                title="Xác nhận xoá?"
                description="Bạn có chắc chắn muốn xoá mã này không?"
                onConfirm={() => deleteMutation.mutate(record.id)}
              >
                <Button type="text" danger icon={<DeleteOutlined />} />
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Typography.Title level={4} style={{ margin: 0 }}>
        Danh mục Tình trạng Vận hành
      </Typography.Title>

      <Card
        extra={
          hasPermission('ops:operation-status-code:manage') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
              Thêm mới
            </Button>
          )
        }
      >
        <Table<OperationStatusCode>
          columns={columns}
          dataSource={query.data ?? []}
          rowKey="id"
          loading={query.isLoading}
          pagination={false}
          scroll={{ x: 1000 }}
        />
      </Card>

      <Modal
        title={editingId ? 'Sửa Tình trạng Vận hành' : 'Thêm Tình trạng Vận hành'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        onOk={handleSubmit}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="code"
            label="Mã tình trạng"
            rules={[{ required: true, message: 'Bắt buộc nhập' }]}
          >
            <Input placeholder="Ví dụ: BOM_CHAY_1, BAO_TRI..." disabled={!!editingId} />
          </Form.Item>

          <Form.Item
            name="name"
            label="Tên hiển thị"
            rules={[{ required: true, message: 'Bắt buộc nhập' }]}
          >
            <Input placeholder="Ví dụ: Chạy 1 máy bơm, Bảo trì..." />
          </Form.Item>

          <Form.Item
            name="colorHex"
            label="Màu sắc"
            rules={[{ required: true, message: 'Bắt buộc nhập' }]}
            getValueFromEvent={(e) => (typeof e === 'string' ? e : e.toHexString())}
          >
            <ColorPicker showText />
          </Form.Item>

          <Form.Item name="mappedStatus" label="Map trạng thái hệ thống (Tuỳ chọn)">
            <Select
              allowClear
              options={Object.entries(CONSTRUCTION_STATUS).map(([key, val]) => ({
                value: key,
                label: val.label,
              }))}
              placeholder="Trạng thái công trình khi ở tình trạng này"
            />
          </Form.Item>

          <Space size="large" align="start">
            <Form.Item name="hasParameter" label="Có tham số nhập" valuePropName="checked">
              <Switch />
            </Form.Item>

            {hasParameter && (
              <Form.Item name="parameterUnit" label="Đơn vị tham số">
                <Input placeholder="Ví dụ: m3/s, giờ..." />
              </Form.Item>
            )}
          </Space>

          <Space size="large" align="start">
            <Form.Item name="sortOrder" label="Thứ tự hiển thị" rules={[{ required: true }]}>
              <InputNumber min={1} />
            </Form.Item>

            <Form.Item name="active" label="Trạng thái sử dụng" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Space>
  );
}
