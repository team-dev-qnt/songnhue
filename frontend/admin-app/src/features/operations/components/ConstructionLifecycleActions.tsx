import { DeleteOutlined, SwapOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Button, Form, Input, Modal, Popconfirm, Select, Tooltip } from 'antd';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { CONSTRUCTION_STATUS } from '@/components/business/statusVocabulary';
import { type LifecycleState } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';

interface Props {
  publicId: string;
  name: string;
  lifecycleState: LifecycleState;
}

/** Ba giá trị vòng đời — nhãn lấy từ từ vựng dùng chung, không viết lại ở đây (luật 14). */
const VONG_DOI: readonly LifecycleState[] = ['DANG_HOAT_DONG', 'NGUNG_MUA_VU', 'DA_THANH_LY'];

/**
 * Đổi vòng đời và xoá mềm hồ sơ công trình — CN-02.1.
 *
 * <h2>Vì sao hai nút này ra đời ngày 31/08/2026</h2>
 *
 * <p>`PUT /ops/constructions/{id}/lifecycle` và `DELETE /ops/constructions/{id}` có từ WS-17 và
 * <b>không lời gọi nào</b> từ giao diện — nửa cặp đọc–ghi, luật 27. Hệ quả nhìn thấy được trên cổng
 * công khai: `PublicConstructionCatalogService` **lọc công trình đã thanh lý ra khỏi danh mục**,
 * nhưng Công ty không có cách nào đánh dấu một công trình là đã thanh lý. Bộ lọc ấy vì thế chưa
 * từng lọc gì.
 *
 * <h2>⛔ Vòng đời KHÁC trạng thái vận hành</h2>
 *
 * <p>`lifecycleState` là quyết định hành chính của con người (đang hoạt động / ngừng mùa vụ / đã
 * thanh lý). `operationalStatus` là **giá trị dẫn xuất** do `ConstructionStatusService` tính từ sự
 * cố, bảo trì, cảnh báo ngưỡng và mã tình hình vận hành — quy tắc 4 cấm mọi đường cho người dùng
 * đặt thẳng nó, và endpoint sẽ trả `OPS-3001` nếu client gửi lên. Ô chọn dưới đây <b>chỉ</b> liệt
 * kê ba giá trị vòng đời.
 *
 * <p>⚠ Backend đòi `reason` khác rỗng (`@NotBlank`): đổi vòng đời là thao tác đi vào nhật ký kiểm
 * toán, và một dòng nhật ký không có lý do thì vài tháng sau không ai đọc lại được.
 */
export function ConstructionLifecycleActions({ publicId, name, lifecycleState }: Props) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const [moModal, setMoModal] = useState(false);
  const [form] = Form.useForm<{ state: LifecycleState; reason: string }>();

  const lamMoi = () => queryClient.invalidateQueries({ queryKey: ['ops', 'constructions'] });

  const doiVongDoi = useMutation({
    mutationFn: (values: { state: LifecycleState; reason: string }) =>
      api.put(`/ops/constructions/${publicId}/lifecycle`, values),
    onSuccess: () => {
      setMoModal(false);
      form.resetFields();
      void lamMoi();
      message.success('Đã đổi vòng đời hồ sơ');
    },
    onError: (error) =>
      message.error(error instanceof ApiClientError ? error.message : 'Không đổi được vòng đời'),
  });

  const xoa = useMutation({
    mutationFn: () => api.delete(`/ops/constructions/${publicId}`),
    onSuccess: () => {
      void lamMoi();
      message.success('Đã xoá hồ sơ công trình');
    },
    onError: (error) =>
      message.error(error instanceof ApiClientError ? error.message : 'Không xoá được hồ sơ'),
  });

  return (
    <>
      {/* Canh đúng quyền mà ENDPOINT đòi, không phải quyền của màn hình chứa nút. */}
      {hasPermission('ops:construction:update') && (
        <Tooltip title="Đổi vòng đời hồ sơ">
          <Button type="text" icon={<SwapOutlined />} onClick={() => setMoModal(true)} />
        </Tooltip>
      )}

      {hasPermission('ops:construction:delete') && (
        <Popconfirm
          title="Xoá hồ sơ công trình?"
          description={`"${name}" sẽ bị xoá mềm — hồ sơ và nhật ký vẫn giữ, chỉ không còn trong danh sách.`}
          okText="Xoá"
          okButtonProps={{ danger: true, loading: xoa.isPending }}
          cancelText="Huỷ"
          onConfirm={() => xoa.mutate()}
        >
          <Tooltip title="Xoá hồ sơ">
            <Button type="text" danger icon={<DeleteOutlined />} />
          </Tooltip>
        </Popconfirm>
      )}

      <Modal
        title={`Đổi vòng đời — ${name}`}
        open={moModal}
        onCancel={() => setMoModal(false)}
        okText="Cập nhật"
        confirmLoading={doiVongDoi.isPending}
        onOk={() => form.submit()}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ state: lifecycleState }}
          onFinish={(values) => doiVongDoi.mutate(values)}
        >
          <Form.Item
            name="state"
            label="Vòng đời"
            rules={[{ required: true, message: 'Bắt buộc' }]}
            extra="Công trình đã thanh lý bị loại khỏi danh mục công bố trên cổng; ngừng mùa vụ thì vẫn hiện."
          >
            <Select
              options={VONG_DOI.map((ma) => ({
                value: ma,
                label: CONSTRUCTION_STATUS[ma]?.label ?? ma,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="reason"
            label="Lý do"
            rules={[{ required: true, message: 'Bắt buộc — lý do đi vào nhật ký kiểm toán' }]}
          >
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
