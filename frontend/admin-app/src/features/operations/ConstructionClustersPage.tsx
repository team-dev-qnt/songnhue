import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import { type ClusterView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

const BASE = '/ops/construction-clusters';
const KHOA = ['ops', 'construction-clusters'] as const;

interface ThanCum {
  code: string;
  name: string;
  orgUnitId: string;
  description?: string | null;
  sortOrder?: number | null;
}

/**
 * **Quản lý cụm công trình** — đóng nợ **T27.30**.
 *
 * ## ⛔ Vì sao màn hình này phải tồn tại
 *
 * Ba endpoint ghi (`POST`/`PUT`/`DELETE /ops/construction-clusters`) có **0 nơi gọi** kể từ khi
 * chúng ra đời: backend có endpoint, có phân quyền, có bài kiểm — và ⛔ **không có đường nào tạo ra
 * một cụm**. `ClusterSelect` phải nói thẳng với người dùng *"chưa có màn hình quản lý cụm công
 * trình — bỏ trống ô này"*, tức là một ô nhập tự khai mình vô dụng.
 *
 * ⭐⭐ Và nó **phá một lời hứa khác**: tệp mẫu nhập danh mục công trình có cột `ma_cum`, mà giá trị
 * hợp lệ của cột ấy phải **có sẵn trong danh mục cụm**. Không có màn hình này thì mọi tệp Công ty
 * gửi có điền `ma_cum` đều ra lỗi dòng, và người vận hành ⛔ không có cách nào chữa.
 *
 * ## ⚠ Ba quyền khác nhau trên một trang
 *
 * Tạo (`ops:construction:create`) · sửa (`...:update`) · xoá (`...:delete`) là ba quyền riêng, đúng
 * như backend đang gác. Trang gác ở tầng 1 bằng quyền **xem**; từng nút tự gác bằng quyền của nó —
 * gác cả trang bằng `create` là chôn mất người chỉ được sửa.
 */
export function ConstructionClustersPage() {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ThanCum>();
  const [dangMo, setDangMo] = useState(false);
  const [dangSua, setDangSua] = useState<ClusterView | null>(null);

  const coTao = hasPermission('ops:construction:create');
  const coSua = hasPermission('ops:construction:update');
  const coXoa = hasPermission('ops:construction:delete');

  const danhSach = useQuery({
    queryKey: KHOA,
    queryFn: () => api.get<ClusterView[]>(BASE),
  });

  const lamMoi = () => void queryClient.invalidateQueries({ queryKey: KHOA });

  const bao = (caught: unknown, mac: string) => {
    if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
    message.error(caught instanceof ApiClientError ? caught.message : mac);
  };

  const luu = useMutation({
    mutationFn: (than: ThanCum) =>
      dangSua
        ? api.put<ClusterView>(`${BASE}/${dangSua.publicId}`, than)
        : api.post<ClusterView>(BASE, than),
    onSuccess: () => {
      message.success(dangSua ? 'Đã lưu thay đổi' : 'Đã thêm cụm công trình');
      setDangMo(false);
      setDangSua(null);
      lamMoi();
    },
    onError: (c) => bao(c, 'Không lưu được cụm công trình'),
  });

  const xoa = useMutation({
    mutationFn: (publicId: string) => api.delete<void>(`${BASE}/${publicId}`),
    onSuccess: () => {
      message.success('Đã xoá cụm công trình');
      lamMoi();
    },
    // ⛔ `OPS-2012` khi cụm còn công trình bên trong — câu chữ đến từ `error-map`, ⛔ không viết lại.
    onError: (c) => bao(c, 'Không xoá được cụm công trình'),
  });

  const moThem = () => {
    setDangSua(null);
    form.resetFields();
    setDangMo(true);
  };

  const moSua = (cum: ClusterView) => {
    setDangSua(cum);
    form.setFieldsValue({
      code: cum.code,
      name: cum.name,
      orgUnitId: cum.orgUnitId,
      description: cum.description,
      sortOrder: cum.sortOrder,
    });
    setDangMo(true);
  };

  const cot: ColumnsType<ClusterView> = [
    { title: 'Mã cụm', dataIndex: 'code', width: 160 },
    { title: 'Tên cụm', dataIndex: 'name', ellipsis: true },
    {
      title: 'Đơn vị quản lý',
      dataIndex: 'orgUnitName',
      width: 220,
      ellipsis: true,
      render: (v: string | null) => v ?? <Typography.Text type="secondary">—</Typography.Text>,
    },
    { title: 'Thứ tự', dataIndex: 'sortOrder', width: 90, align: 'right' },
    {
      title: 'Trạng thái',
      dataIndex: 'active',
      width: 120,
      render: (v: boolean) => (
        <Tag color={v ? 'green' : 'default'}>{v ? 'Đang dùng' : 'Đã ẩn'}</Tag>
      ),
    },
    {
      title: '',
      key: 'thaoTac',
      width: 100,
      align: 'right',
      render: (_, cum) => (
        <Space size={0}>
          {coSua && <Button type="text" icon={<EditOutlined />} onClick={() => moSua(cum)} />}
          {coXoa && (
            <Popconfirm
              title="Xoá cụm công trình?"
              // ⚠ Nói ra luật TRƯỚC khi bấm: backend từ chối khi cụm còn công trình (OPS-2012), và
              //   một lượt từ chối sau khi đã bấm đồng ý đọc như giao diện hỏng.
              description="Cụm còn công trình bên trong sẽ không xoá được — chuyển công trình sang cụm khác trước."
              okText="Xoá"
              cancelText="Huỷ"
              onConfirm={() => xoa.mutate(cum.publicId)}
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
      title="Cụm công trình"
      extra={
        coTao ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={moThem}>
            Thêm cụm
          </Button>
        ) : null
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Cụm chỉ để NHÓM và LỌC công trình — nó không phải một cấp trong bộ máy tổ chức"
        description="Chốt G15 (19/8): cụm là cách nhóm, đơn vị quản lý mới là cấp tổ chức. Mã cụm khai ở đây chính là giá trị hợp lệ của cột `ma_cum` trong tệp nhập danh mục công trình."
      />

      <Table<ClusterView>
        rowKey="publicId"
        columns={cot}
        dataSource={danhSach.data ?? []}
        loading={danhSach.isLoading}
        pagination={false}
        size="middle"
        // ⛔ Thiếu `scroll.x` thì bảng BÓP CHỮ ở màn hình hẹp thay vì cuộn ngang — mã cụm và tên
        //    đơn vị co về một ký tự mỗi dòng, và ⛔ không có gì báo lỗi. `bangCuonNgang.test.ts`
        //    bắt được đúng bảng này ở lượt chạy đầu.
        scroll={{ x: 900 }}
        locale={{ emptyText: 'Chưa khai cụm nào — bỏ trống cột `ma_cum` khi nhập công trình' }}
      />

      <Modal
        title={dangSua ? `Sửa cụm ${dangSua.code}` : 'Thêm cụm công trình'}
        open={dangMo}
        onCancel={() => setDangMo(false)}
        onOk={() => void form.submit()}
        confirmLoading={luu.isPending}
        okText="Lưu"
        cancelText="Huỷ"
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(v) => luu.mutate(v)}>
          <Form.Item
            name="code"
            label="Mã cụm"
            rules={[{ required: true, message: 'Bắt buộc nhập' }]}
            extra="Đây là giá trị điền vào cột `ma_cum` của tệp nhập danh mục công trình."
          >
            <Input maxLength={50} placeholder="VD: CUM-LMAC" />
          </Form.Item>
          <Form.Item
            name="name"
            label="Tên cụm"
            rules={[{ required: true, message: 'Bắt buộc nhập' }]}
          >
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item
            name="orgUnitId"
            label="Đơn vị quản lý"
            rules={[{ required: true, message: 'Bắt buộc chọn' }]}
          >
            <OrgUnitTreeSelect />
          </Form.Item>
          <Form.Item name="sortOrder" label="Thứ tự hiển thị">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="Mô tả">
            <Input.TextArea maxLength={500} rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

export default ConstructionClustersPage;
