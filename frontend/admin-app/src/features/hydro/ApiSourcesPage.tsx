import { EditOutlined, KeyOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import {
  type ApiSource,
  type ApiSourceCreateRequest,
  type ApiSourceRequest,
} from '@/shared/api-types';
import { api } from '@/shared/apiClient';

/**
 * Nguồn dữ liệu quan trắc bên thứ 3 — CN-03.2 (T28.2).
 *
 * ⛔ **Màn hình này không bao giờ hiển thị mã số**, kể cả cho SUPER_ADMIN: backend không trả
 * credential ra khỏi CSDL (`conventions.md` §4.7). Muốn đổi thì gõ lại mã mới; không có ô
 * "xem mã hiện tại", và cũng không có bản che một phần — vài ký tự cuối của một mã ngắn thu
 * hẹp không gian tìm kiếm rất nhiều, đổi lại chỉ đỡ phải hỏi người giữ mã.
 *
 * ⚠ Bốn ô tham số nhịp để trống nghĩa là **dùng tham số chung** ở Cấu hình hệ thống, không
 * phải "chưa cấu hình". Cột *Đang chạy theo* hiện giá trị ĐÃ GIẢI kèm nguồn gốc, vì một ô
 * trống không nói gì sẽ khiến người vận hành kết luận nhầm.
 */
export function ApiSourcesPage() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [formSua] = Form.useForm<ApiSourceRequest>();
  const [formTao] = Form.useForm<ApiSourceCreateRequest>();
  const [formMaSo] = Form.useForm<{ maSo: string }>();

  const [dangSua, setDangSua] = useState<ApiSource | null>(null);
  const [taoMoi, setTaoMoi] = useState(false);
  const [datMaSoCho, setDatMaSoCho] = useState<ApiSource | null>(null);

  const coQuanLy = hasPermission('hyd:api-source:manage');

  const query = useQuery({
    queryKey: ['hyd', 'api-sources'],
    queryFn: () => api.get<ApiSource[]>('/hyd/api-sources'),
  });

  const lamMoi = () => queryClient.invalidateQueries({ queryKey: ['hyd', 'api-sources'] });

  const createMutation = useMutation({
    mutationFn: (data: ApiSourceCreateRequest) => api.post<ApiSource>('/hyd/api-sources', data),
    onSuccess: () => {
      message.success('Đã thêm nguồn dữ liệu');
      setTaoMoi(false);
      void lamMoi();
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: { id: string; payload: ApiSourceRequest }) =>
      api.put<ApiSource>(`/hyd/api-sources/${data.id}`, data.payload),
    onSuccess: () => {
      message.success('Đã cập nhật nguồn');
      setDangSua(null);
      void lamMoi();
    },
  });

  const maSoMutation = useMutation({
    mutationFn: (data: { id: string; maSo: string }) =>
      api.put(`/hyd/api-sources/${data.id}/credential`, { maSo: data.maSo }),
    onSuccess: () => {
      message.success('Đã lưu mã số — hệ thống chỉ giữ bản đã mã hoá');
      setDatMaSoCho(null);
      void lamMoi();
    },
  });

  const xoaMaSoMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/hyd/api-sources/${id}/credential`),
    onSuccess: () => {
      message.success('Đã gỡ mã số — nguồn về trạng thái chưa cấu hình');
      void lamMoi();
    },
  });

  const moSua = (nguon: ApiSource) => {
    formSua.resetFields();
    formSua.setFieldsValue({
      name: nguon.name,
      baseUrl: nguon.baseUrl,
      frameMinutes: nguon.frameMinutes,
      timeoutSeconds: nguon.timeoutSeconds,
      maxRetry: nguon.maxRetry,
      cron: nguon.cron,
      status: nguon.status,
      description: nguon.description ?? undefined,
    });
    setDangSua(nguon);
  };

  const columns: ColumnsType<ApiSource> = [
    { title: 'Mã', dataIndex: 'code', width: 110 },
    { title: 'Tên nguồn', dataIndex: 'name' },
    {
      title: 'Địa chỉ',
      dataIndex: 'baseUrl',
      render: (url: string) => (
        <Space size={4}>
          <Typography.Text code>{url}</Typography.Text>
          {url.startsWith('http://') && (
            <Tooltip title="Nguồn chỉ có HTTP. Trình duyệt không gọi thẳng — mọi lượt gọi đi từ máy chủ.">
              <Tag color="orange">HTTP</Tag>
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: 'Mã số',
      dataIndex: 'credentialDaCauHinh',
      width: 150,
      render: (daCo: boolean) =>
        daCo ? <Tag color="green">Đã cấu hình</Tag> : <Tag color="red">Chưa cấu hình</Tag>,
    },
    {
      title: 'Điểm đo',
      dataIndex: 'soDiemDo',
      width: 90,
      align: 'center',
    },
    {
      title: 'Đang chạy theo',
      width: 260,
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <span>
            <Typography.Text code>{r.cronHieuLuc}</Typography.Text>{' '}
            {r.cronDungChung ? <Tag>tham số chung</Tag> : <Tag color="blue">riêng</Tag>}
          </span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            khung {r.khungNguonPhutHieuLuc} phút · chờ {r.timeoutGiayHieuLuc}s · thử lại{' '}
            {r.soLanThuLaiHieuLuc}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Sức khoẻ',
      width: 190,
      render: (_, r) =>
        r.consecutiveFailures > 0 ? (
          <Tooltip title={r.lastFailureReason ?? ''}>
            <Tag color="red">{r.consecutiveFailures} lượt hỏng liên tiếp</Tag>
          </Tooltip>
        ) : r.lastSuccessAt ? (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Lần cuối lấy được: {new Date(r.lastSuccessAt).toLocaleString('vi-VN')}
          </Typography.Text>
        ) : (
          <Tag>Chưa lấy lần nào</Tag>
        ),
    },
    {
      title: '',
      width: 140,
      align: 'right',
      render: (_, r) =>
        coQuanLy ? (
          <Space>
            <Tooltip title="Đặt / thay mã số truy cập">
              <Button
                type="text"
                icon={<KeyOutlined />}
                onClick={() => {
                  formMaSo.resetFields();
                  setDatMaSoCho(r);
                }}
              />
            </Tooltip>
            <Button type="text" icon={<EditOutlined />} onClick={() => moSua(r)} />
          </Space>
        ) : null,
    },
  ];

  const chuaCoMaSo = (query.data ?? []).filter((n) => !n.credentialDaCauHinh);

  return (
    <Card
      title="Nguồn dữ liệu quan trắc"
      extra={
        coQuanLy ? (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              formTao.resetFields();
              formTao.setFieldsValue({ adapterType: 'BHH40' });
              setTaoMoi(true);
            }}
          >
            Thêm nguồn
          </Button>
        ) : null
      }
    >
      {chuaCoMaSo.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message={`${chuaCoMaSo.length} nguồn chưa có mã số — lượt lấy dữ liệu sẽ KHÔNG chạy`}
          description={
            <>
              Nguồn thiếu mã số thì hệ thống từ chối gọi và ghi rõ lý do, thay vì gọi bằng một mã
              rỗng rồi báo &quot;nguồn không phản hồi&quot;. Bấm biểu tượng chìa khoá để đặt mã số.
              <br />⚠ Mã số của <code>bhh40.net</code> có <b>dấu chấm phẩy ở cuối</b> — giữ nguyên
              khi dán, thiếu nó nguồn trả <code>not.working</code>, trông y hệt lỗi sai mã.
            </>
          }
        />
      )}

      <Table
        rowKey="id"
        loading={query.isLoading}
        dataSource={query.data ?? []}
        columns={columns}
        pagination={false}
        expandable={{
          expandedRowRender: (r) => (
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label="Adapter">{r.adapterType}</Descriptions.Item>
              <Descriptions.Item label="Trạng thái">
                {r.status === 'HOAT_DONG' ? 'Đang dùng' : 'Tạm dừng'}
              </Descriptions.Item>
              <Descriptions.Item label="Cron đặt riêng">
                {r.cron ?? '— dùng chung'}
              </Descriptions.Item>
              <Descriptions.Item label="Khung nguồn đặt riêng">
                {r.frameMinutes ?? '— dùng chung'}
              </Descriptions.Item>
              <Descriptions.Item label="Chờ tối đa đặt riêng">
                {r.timeoutSeconds ?? '— dùng chung'}
              </Descriptions.Item>
              <Descriptions.Item label="Thử lại đặt riêng">
                {r.maxRetry ?? '— dùng chung'}
              </Descriptions.Item>
              <Descriptions.Item label="Ghi chú" span={2}>
                {r.description ?? '—'}
              </Descriptions.Item>
            </Descriptions>
          ),
        }}
      />

      <Modal
        open={taoMoi}
        title="Thêm nguồn dữ liệu"
        onCancel={() => setTaoMoi(false)}
        onOk={async () => {
          const values = await formTao.validateFields();
          createMutation.mutate(values);
        }}
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form form={formTao} layout="vertical">
          <Form.Item name="code" label="Mã nguồn" rules={[{ required: true }]}>
            <Input placeholder="BHH40" />
          </Form.Item>
          <Form.Item name="name" label="Tên nguồn" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="adapterType"
            label="Adapter"
            rules={[{ required: true }]}
            extra="Mỗi giá trị ứng với đúng một bộ đọc trong hệ thống."
          >
            <Select
              options={[
                { value: 'BHH40', label: 'BHH40 — bhh40.net (getmn.aspx)' },
                { value: 'MOCK', label: 'MOCK — nguồn giả, chỉ dùng khi phát triển' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="baseUrl"
            label="Địa chỉ gốc"
            rules={[{ required: true }]}
            extra="Bắt đầu bằng http:// hoặc https://"
          >
            <Input placeholder="http://songnhue.bhh40.net" />
          </Form.Item>
          <Form.Item name="description" label="Ghi chú">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={!!dangSua}
        title={`Sửa nguồn ${dangSua?.code ?? ''}`}
        onCancel={() => setDangSua(null)}
        onOk={async () => {
          const values = await formSua.validateFields();
          if (dangSua) updateMutation.mutate({ id: dangSua.id, payload: values });
        }}
        confirmLoading={updateMutation.isPending}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="Để TRỐNG một ô tham số = dùng tham số chung ở Cấu hình hệ thống"
          description="Chỉ điền khi nguồn này thật sự cần nhịp khác các nguồn còn lại."
        />
        <Form form={formSua} layout="vertical">
          <Form.Item name="name" label="Tên nguồn" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="baseUrl" label="Địa chỉ gốc" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="status" label="Trạng thái" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'HOAT_DONG', label: 'Đang dùng' },
                { value: 'TAM_DUNG', label: 'Tạm dừng — poller bỏ qua và ghi rõ lý do' },
              ]}
            />
          </Form.Item>
          <Form.Item name="cron" label="Cron riêng" extra="Trống = dùng tham số chung">
            <Input placeholder="45 1/2 * * * *" />
          </Form.Item>
          <Form.Item name="frameMinutes" label="Khung nguồn riêng (phút)">
            <InputNumber min={1} max={1440} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="timeoutSeconds" label="Chờ tối đa riêng (giây)">
            <InputNumber min={5} max={300} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="maxRetry" label="Số lần thử lại riêng">
            <InputNumber min={0} max={10} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="Ghi chú">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={!!datMaSoCho}
        title={`Mã số truy cập — ${datMaSoCho?.name ?? ''}`}
        onCancel={() => setDatMaSoCho(null)}
        confirmLoading={maSoMutation.isPending}
        onOk={async () => {
          const values = await formMaSo.validateFields();
          if (datMaSoCho) maSoMutation.mutate({ id: datMaSoCho.id, maSo: values.maSo });
        }}
        okText="Lưu mã số"
        footer={(nut) => (
          <Space>
            {datMaSoCho?.credentialDaCauHinh && (
              <Button
                danger
                loading={xoaMaSoMutation.isPending}
                onClick={() => {
                  if (datMaSoCho) xoaMaSoMutation.mutate(datMaSoCho.id);
                }}
              >
                Gỡ mã số
              </Button>
            )}
            {nut}
          </Space>
        )}
        destroyOnClose
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="Hệ thống không hiển thị lại mã số sau khi lưu"
          description={
            <>
              Mã số được mã hoá trước khi ghi vào cơ sở dữ liệu và không có đường nào đọc ngược ra
              màn hình. Muốn đổi thì gõ lại mã mới.
              <br />⚠ <b>Giữ nguyên dấu chấm phẩy cuối</b> nếu mã số có — thiếu nó nguồn trả{' '}
              <code>not.working</code>, trông y hệt lỗi sai mã số.
            </>
          }
        />
        <Form form={formMaSo} layout="vertical">
          <Form.Item
            name="maSo"
            label="Mã số truy cập"
            rules={[{ required: true, message: 'Nhập mã số' }]}
          >
            <Input.Password placeholder="dán nguyên văn, kể cả dấu ';' cuối" autoComplete="off" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

export default ApiSourcesPage;
