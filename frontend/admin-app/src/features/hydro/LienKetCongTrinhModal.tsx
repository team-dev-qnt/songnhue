import { DeleteOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, App, Button, Form, Modal, Popconfirm, Select, Switch, Table, Tag } from 'antd';

import { type ConstructionRow, type Station, type StationLinkRequest } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import {
  VAI_TRO_KHONG_CAN_CONG_TRINH,
  VAI_TRO_VI_TRI,
  VAI_TRO_VI_TRI_OPTIONS,
} from './hydroVocabulary';

/**
 * Khai liên kết **điểm đo ↔ công trình** — T28.19.
 *
 * ⭐⭐ Đây là chỗ **hai luồng dữ liệu của hệ thống gặp nhau**: tình hình vận hành *công trình* do
 * người trực nhập (mục A) và mực nước do API lấy về (mục B, chỉ mang một mã `F#####`). Chúng đứng
 * cạnh nhau trên màn hình **vì cùng trỏ về một công trình**, ⛔ không phải vì được trộn vào một bảng.
 *
 * Bảng `station_constructions` đã có đủ lược đồ, entity, repository, 4 chỉ mục và một mã lỗi riêng
 * từ 31/08 — mà ⛔ **không một dòng mã nào tạo được một hàng**. Màn hình này là nửa ghi còn thiếu.
 *
 * ⛔ Chọn công trình bằng ô **tìm theo tên/mã**, ⛔ không bắt gõ UUID — T27.24 vừa gỡ đúng lỗi ấy ở
 * một màn hình khác.
 */
export function LienKetCongTrinhModal({
  diemDo,
  onClose,
  onDone,
}: {
  diemDo: Station | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const [form] = Form.useForm<StationLinkRequest>();
  const { message } = App.useApp();

  const congTrinhQuery = useQuery({
    queryKey: ['ops', 'constructions', 'chon-lien-ket'],
    // ⚠ `sort` gửi TƯỜNG MINH một cột nằm trong danh sách cho phép của backend. Bỏ trống thì
    //   `PageUtils.parseSort` nhận mặc định của trang gọi — và đúng chỗ đó đã làm trang Danh mục
    //   công trình trả 422 ngay lượt tải đầu vì mặc định là `updatedAt`, một cột không được phép.
    queryFn: () =>
      api.getPage<ConstructionRow>('/ops/constructions', { size: 100, sort: 'code,asc' }),
    enabled: !!diemDo,
  });

  const them = useMutation({
    mutationFn: (body: StationLinkRequest) =>
      api.post(`/hyd/stations/${diemDo?.id}/lien-ket`, body),
    onSuccess: () => {
      message.success('Đã khai liên kết');
      form.resetFields();
      onDone();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không khai được liên kết');
    },
  });

  const bo = useMutation({
    mutationFn: (lienKetId: string) => api.delete(`/hyd/stations/lien-ket/${lienKetId}`),
    onSuccess: () => {
      message.success('Đã bỏ liên kết');
      onDone();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không bỏ được liên kết'),
  });

  const khongCanCongTrinh = !!diemDo && VAI_TRO_KHONG_CAN_CONG_TRINH.includes(diemDo.positionRole);

  return (
    <Modal
      open={!!diemDo}
      title={`Liên kết công trình — ${diemDo?.code ?? ''}`}
      width={760}
      onCancel={() => {
        form.resetFields();
        onClose();
      }}
      footer={null}
      destroyOnClose
    >
      {khongCanCongTrinh && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={`Điểm đo vai trò “${VAI_TRO_VI_TRI[diemDo!.positionRole]}” không bắt buộc liên kết công trình`}
          description="Đây là trạm thuỷ văn tham chiếu — “chưa liên kết” là dữ liệu ĐỦ, không phải dữ liệu thiếu. Vẫn khai được nếu Công ty muốn gắn nó vào một công trình cụ thể."
        />
      )}

      <Table
        rowKey="id"
        size="small"
        style={{ marginBottom: 24 }}
        dataSource={diemDo?.constructions ?? []}
        pagination={false}
        locale={{ emptyText: 'Chưa liên kết công trình nào' }}
        scroll={{ x: 640 }}
        columns={[
          {
            title: 'Công trình',
            width: 300,
            render: (_, r) =>
              // ⛔ Không giấu dòng đi khi công trình đã bị xoá: một liên kết trỏ vào chỗ trống là
              //   thứ người vận hành cần THẤY để dọn, không phải thứ nên biến mất lặng lẽ.
              r.constructionCode ? (
                `${r.constructionCode} — ${r.constructionName}`
              ) : (
                <Tag color="red">Công trình đã bị xoá</Tag>
              ),
          },
          {
            title: 'Vai trò',
            width: 150,
            render: (_, r) => <Tag>{VAI_TRO_VI_TRI[r.role]}</Tag>,
          },
          {
            title: 'Liên kết chính',
            width: 130,
            render: (_, r) => (r.primary ? <Tag color="blue">Chính</Tag> : null),
          },
          {
            title: '',
            width: 60,
            align: 'right',
            render: (_, r) => (
              <Popconfirm title="Bỏ liên kết này?" onConfirm={() => bo.mutate(r.id)}>
                <Button type="text" danger icon={<DeleteOutlined />} loading={bo.isPending} />
              </Popconfirm>
            ),
          },
        ]}
      />

      <Form
        form={form}
        layout="vertical"
        initialValues={{ role: diemDo?.positionRole, primary: false }}
        onFinish={(v) => them.mutate(v)}
      >
        <Form.Item
          name="constructionId"
          label="Công trình"
          rules={[{ required: true, message: 'Chọn công trình' }]}
          extra="Gõ để tìm theo mã hoặc tên. Danh mục công trình đang chờ dữ liệu của Công ty (G8) — ô này rỗng là vì vậy, không phải vì lỗi."
        >
          <Select
            showSearch
            optionFilterProp="label"
            loading={congTrinhQuery.isLoading}
            placeholder="Chọn công trình"
            options={(congTrinhQuery.data?.items ?? []).map((c) => ({
              value: c.publicId,
              label: `${c.code} — ${c.name}`,
            }))}
          />
        </Form.Item>

        <Form.Item
          name="role"
          label="Vai trò của điểm đo với công trình này"
          rules={[{ required: true, message: 'Chọn vai trò' }]}
          extra={`Một điểm đo có thể là hạ lưu của cống này ĐỒNG THỜI là thượng lưu của cống kế tiếp — vai trò khai theo từng liên kết. Riêng liên kết CHÍNH phải trùng vai trò của hồ sơ điểm đo (“${diemDo ? VAI_TRO_VI_TRI[diemDo.positionRole] : ''}”).`}
        >
          <Select options={VAI_TRO_VI_TRI_OPTIONS} />
        </Form.Item>

        <Form.Item
          name="primary"
          label="Là liên kết chính"
          valuePropName="checked"
          extra="Mỗi điểm đo có tối đa MỘT liên kết chính — bật ở đây thì liên kết chính cũ tự chuyển thành phụ. Đây là liên kết mà biểu tổng hợp theo tuyến sông dùng để xếp cột TL/HL."
        >
          <Switch />
        </Form.Item>

        <Button type="primary" htmlType="submit" loading={them.isPending}>
          Khai liên kết
        </Button>
      </Form>
    </Modal>
  );
}

export default LienKetCongTrinhModal;
