import { useMutation } from '@tanstack/react-query';
import { App, DatePicker, Form, Input, InputNumber, Modal, Radio, Select } from 'antd';
import type dayjs from 'dayjs';

import { bayGio } from '@/shared/format';

import { useAuth } from '@/app/auth/useAuth';
import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import { INCIDENT_SEVERITY, MAINTENANCE_TYPE } from '@/components/business/statusVocabulary';
import { type MaintenanceRow, type MaintenanceType } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import {
  type MaintenanceFormValues,
  dungPayloadSuaBanGhi,
  dungPayloadSuaChua,
  giaTriTuBanGhi,
} from '../constructionRules';

/**
 * Ghi nhận MỚI, hoặc SỬA một bản ghi đã lưu khi có `banGhi` — T61.18.
 *
 * ⚠⚠ Nơi gọi PHẢI đặt `key={banGhi.id}` cho lối sửa. `Form.useForm()` sống trong component này, và
 * `initialValues` của `rc-field-form` chỉ áp lúc mount (T51.12): ⛔ remount thì mở bản ghi A rồi B là
 * biểu mẫu mang dữ liệu của A và `PUT` ghi nó lên B. `key` là cơ chế DUY NHẤT ở đây — ⛔ chồng thêm
 * `setFieldsValue` trong effect (T53.7: hai biện pháp chồng nhau cho ra ô RỖNG).
 */
export function MaintenanceFormModal({
  constructionPublicId,
  open,
  onClose,
  onSaved,
  banGhi,
  loaiMacDinh,
  alertEventId,
}: {
  constructionPublicId: string;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
  banGhi?: MaintenanceRow | null;
  /**
   * Ép loại công việc lúc MỞ — T33.10, đường vào từ *Lịch sử cảnh báo*.
   *
   * ⚠ Nó chỉ đổi **giá trị ban đầu**, ⛔ khoá ô: người ghi vẫn sửa được nếu hoá ra đây là việc
   * bảo trì chứ ⛔ phải khắc phục sự cố. Khoá lại là quyết định hộ người đang đứng ở hiện trường.
   */
  loaiMacDinh?: MaintenanceType;
  /**
   * Cảnh báo ngưỡng đã dẫn tới bản ghi này — `OPS-2021` từ chối một giá trị ⛔ trỏ vào cảnh báo nào.
   */
  alertEventId?: string;
}) {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const [form] = Form.useForm<MaintenanceFormValues>();

  const workType = Form.useWatch('workType', form);
  const performerKind = Form.useWatch('performerKind', form);
  const laSuCo = workType === 'KHAC_PHUC_SU_CO';

  const luu = useMutation({
    mutationFn: (values: MaintenanceFormValues) => {
      if (banGhi) {
        return api.put<MaintenanceRow>(
          `/ops/maintenance-logs/${banGhi.id}`,
          dungPayloadSuaBanGhi(values, banGhi),
        );
      }
      const payload = dungPayloadSuaChua(values, constructionPublicId, alertEventId);
      // ⚠ Hai đường tạo, hai quyền khác nhau (ma trận §6): cán bộ vận hành CHỈ ghi nhận được sự cố,
      // không ghi được công việc bảo trì. Chọn sai đường là 403 với người đáng lẽ có quyền.
      return api.post<MaintenanceRow>(
        laSuCo ? '/ops/maintenance-logs/incidents' : '/ops/maintenance-logs',
        payload,
      );
    },
    onSuccess: () => {
      message.success(banGhi ? 'Đã cập nhật bản ghi' : 'Đã ghi nhận công việc');
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

  // Lối sửa: quyền thật (ops:maintenance:update hoặc cửa sổ tác giả) kiểm ở backend — T18.9. Lọc loại
  // theo quyền TẠO ở đây là để một bản ghi sự cố mở ra với ô loại RỖNG ở người chỉ có quyền bảo trì.
  const loaiChoPhep = Object.entries(MAINTENANCE_TYPE).filter(([ma]) =>
    banGhi
      ? true
      : ma === 'KHAC_PHUC_SU_CO'
        ? hasPermission('ops:maintenance:report-incident')
        : hasPermission('ops:maintenance:create'),
  );

  return (
    <Modal
      title={banGhi ? `Sửa bản ghi ${banGhi.code}` : 'Ghi nhận công việc sửa chữa'}
      open={open}
      onCancel={onClose}
      onOk={() => void form.validateFields().then((v) => luu.mutate(v))}
      confirmLoading={luu.isPending}
      width={720}
      destroyOnHidden
      afterClose={() => form.resetFields()}
    >
      <Form<MaintenanceFormValues>
        form={form}
        layout="vertical"
        initialValues={
          banGhi
            ? giaTriTuBanGhi(banGhi)
            : {
                // ⚠ `loaiMacDinh` chỉ thắng khi nó nằm trong danh sách CHO PHÉP: ép một loại mà
                //   người đang mở ⛔ có quyền tạo là dựng một ô chọn hiện giá trị ⛔ gửi nổi (403),
                //   và triệu chứng ấy đọc như "hệ thống hỏng" chứ ⛔ như "thiếu quyền".
                workType: (loaiChoPhep.some(([ma]) => ma === loaiMacDinh)
                  ? loaiMacDinh
                  : loaiChoPhep[0]?.[0]) as MaintenanceType,
                // ⛔ `dayjs()` trần: ngày này được GHI XUỐNG CSDL. Trên máy trạm lệch múi giờ,
                // quanh nửa đêm nó lệch CẢ MỘT NGÀY và ⛔ có gì báo — T63.18.
                startedOn: bayGio(),
                performerKind: 'INTERNAL',
              }
        }
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
