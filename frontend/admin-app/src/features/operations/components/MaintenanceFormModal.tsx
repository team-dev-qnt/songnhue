import { useMutation } from '@tanstack/react-query';
import { App, DatePicker, Form, Input, InputNumber, Modal, Radio, Select } from 'antd';
import dayjs from 'dayjs';

import { useAuth } from '@/app/auth/useAuth';
import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import { INCIDENT_SEVERITY, MAINTENANCE_TYPE } from '@/components/business/statusVocabulary';
import { type MaintenanceRow, type MaintenanceType } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import { type MaintenanceFormValues, dungPayloadSuaChua } from '../constructionRules';

export function MaintenanceFormModal({
  constructionPublicId,
  open,
  onClose,
  onSaved,
}: {
  constructionPublicId: string;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const [form] = Form.useForm<MaintenanceFormValues>();

  const workType = Form.useWatch('workType', form);
  const performerKind = Form.useWatch('performerKind', form);
  const laSuCo = workType === 'KHAC_PHUC_SU_CO';

  const luu = useMutation({
    mutationFn: (values: MaintenanceFormValues) => {
      const payload = dungPayloadSuaChua(values, constructionPublicId);
      // ⚠ Hai đường tạo, hai quyền khác nhau (ma trận §6): cán bộ vận hành CHỈ ghi nhận được sự cố,
      // không ghi được công việc bảo trì. Chọn sai đường là 403 với người đáng lẽ có quyền.
      return api.post<MaintenanceRow>(
        laSuCo ? '/ops/maintenance-logs/incidents' : '/ops/maintenance-logs',
        payload,
      );
    },
    onSuccess: () => {
      message.success('Đã ghi nhận công việc');
      form.resetFields();
      onSaved();
      onClose();
    },
    onError: (caught: unknown) => {
      // ⛔ Bản trước KHÔNG có nhánh nào sau `if` — `details` rỗng (mọi lỗi nghiệp vụ không
      //    theo trường: OPS-2xxx, quyền, xung đột phiên bản) là màn hình im hoàn toàn. Đây là
      //    dạng nặng nhất của lớp lỗi 01/09: không phải "đặt lỗi vào chỗ không ai thấy" mà là
      //    "không có chỗ nào để đặt".
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được bản ghi');
    },
  });

  const loaiChoPhep = Object.entries(MAINTENANCE_TYPE).filter(([ma]) =>
    ma === 'KHAC_PHUC_SU_CO'
      ? hasPermission('ops:maintenance:report-incident')
      : hasPermission('ops:maintenance:create'),
  );

  return (
    <Modal
      title="Ghi nhận công việc sửa chữa"
      open={open}
      onCancel={onClose}
      onOk={() => void form.validateFields().then((v) => luu.mutate(v))}
      confirmLoading={luu.isPending}
      width={720}
      destroyOnClose
      afterClose={() => form.resetFields()}
    >
      <Form<MaintenanceFormValues>
        form={form}
        layout="vertical"
        initialValues={{
          workType: loaiChoPhep[0]?.[0] as MaintenanceType,
          startedOn: dayjs(),
          performerKind: 'INTERNAL',
        }}
      >
        <Form.Item name="workType" label="Loại công việc" rules={[{ required: true }]}>
          <Select
            options={loaiChoPhep.map(([ma, v]) => ({ value: ma, label: v.label }))}
            onChange={() => form.setFieldValue('severity', undefined)}
          />
        </Form.Item>

        {/* Chỉ hiện với sự cố — OPS-2003 từ chối `severity` ở loại khác, và `dungPayload` cũng
            xoá nó khi gửi. Hai lớp, vì ô ẩn của AntD vẫn giữ giá trị cũ trong form store. */}
        {laSuCo && (
          <Form.Item name="severity" label="Mức độ" rules={[{ required: true }]}>
            <Select
              options={Object.entries(INCIDENT_SEVERITY).map(([ma, v]) => ({
                value: ma,
                label: v.label,
              }))}
            />
          </Form.Item>
        )}

        <Form.Item name="content" label="Nội dung công việc" rules={[{ required: true }]}>
          <Input.TextArea rows={3} maxLength={20000} showCount />
        </Form.Item>

        <Form.Item name="itemOrEquipment" label="Hạng mục / thiết bị">
          <Input maxLength={255} />
        </Form.Item>

        <Form.Item name="startedOn" label="Ngày bắt đầu" rules={[{ required: true }]}>
          <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item
          name="completedOn"
          label="Ngày hoàn thành"
          dependencies={['startedOn']}
          rules={[
            ({ getFieldValue }) => ({
              // Cùng luật với OPS-2001 ở backend. Chặn ở đây để người dùng thấy ngay tại ô nhập,
              // backend vẫn là nơi chốt — giao diện không phải nơi giữ luật.
              validator(_, value?: dayjs.Dayjs) {
                const batDau = getFieldValue('startedOn') as dayjs.Dayjs | undefined;
                if (!value || !batDau || !value.isBefore(batDau, 'day')) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('Ngày hoàn thành phải từ ngày bắt đầu trở đi'));
              },
            }),
          ]}
        >
          <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item name="performerKind" label="Đơn vị thực hiện">
          <Radio.Group
            options={[
              { value: 'INTERNAL', label: 'Đơn vị nội bộ' },
              { value: 'EXTERNAL', label: 'Nhà thầu ngoài' },
            ]}
            optionType="button"
          />
        </Form.Item>

        {performerKind === 'EXTERNAL' ? (
          <Form.Item name="performerName" label="Tên nhà thầu" rules={[{ required: true }]}>
            <Input maxLength={255} />
          </Form.Item>
        ) : (
          <Form.Item name="performerOrgUnitId" label="Đơn vị nội bộ" rules={[{ required: true }]}>
            <OrgUnitTreeSelect />
          </Form.Item>
        )}

        <Form.Item name="costTrieu" label="Chi phí (triệu VNĐ)">
          <InputNumber min={0} step={0.1} style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item name="fundingSource" label="Nguồn kinh phí">
          <Input maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
