import { useMutation } from '@tanstack/react-query';
import { DatePicker, Form, type FormInstance, Input, Modal, Select, message } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useLayoutEffect } from 'react';

import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import {
  type EmployeeEventType,
  LOAI_SU_KIEN_OPTIONS,
  type SuKienRequest,
  type SuKienView,
} from './hrVocabulary';

interface GiaTriBieuMau {
  eventType: EmployeeEventType;
  effectiveOn: Dayjs;
  decisionNo?: string;
  decisionDate?: Dayjs;
  title: string;
  detail?: string;
}

/**
 * Ghi / sửa một sự kiện công tác — CN-04.4.
 *
 * ⛔⛔ Màn hình thứ **tư** của module dính cơ chế `rc-field-form` mà T51.12 đã trả giá. Ở đây thứ
 * có thể lẫn giữa hai người là **quyết định kỷ luật**. Cách chữa: đặt giá trị tường minh ở lượt
 * dựng — xem {@link BieuMauSuKien}.
 */
export function SuKienFormModal({
  open,
  hoSoId,
  suKien,
  onClose,
  onSaved,
}: {
  open: boolean;
  hoSoId: string;
  suKien: SuKienView | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<GiaTriBieuMau>();
  const laSua = suKien !== null;
  const duong = `/hr/employees/${hoSoId}/timeline`;

  const luu = useMutation({
    mutationFn: (than: SuKienRequest) =>
      laSua ? api.put(`${duong}/${suKien.publicId}`, than) : api.post(duong, than),
    onSuccess: () => {
      message.success(laSua ? 'Đã cập nhật sự kiện' : 'Đã ghi sự kiện');
      onSaved();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được');
    },
  });

  const initialValues: Partial<GiaTriBieuMau> = suKien
    ? {
        eventType: suKien.eventType,
        effectiveOn: dayjs(suKien.effectiveOn),
        decisionNo: suKien.decisionNo ?? undefined,
        decisionDate: suKien.decisionDate ? dayjs(suKien.decisionDate) : undefined,
        title: suKien.title,
        detail: suKien.detail ?? undefined,
      }
    : { eventType: 'HOP_DONG' };

  return (
    <Modal
      title={laSua ? 'Sửa sự kiện công tác' : 'Ghi sự kiện công tác'}
      open={open}
      onCancel={onClose}
      onOk={() => {
        void form.validateFields().then((v) =>
          luu.mutate({
            eventType: v.eventType,
            effectiveOn: v.effectiveOn.format('YYYY-MM-DD'),
            decisionNo: v.decisionNo ?? null,
            decisionDate: v.decisionDate ? v.decisionDate.format('YYYY-MM-DD') : null,
            title: v.title,
            detail: v.detail ?? null,
          }),
        );
      }}
      confirmLoading={luu.isPending}
      okText="Lưu"
      cancelText="Huỷ"
      destroyOnHidden
    >
      <BieuMauSuKien
        // ⛔⛔ `key` ⛔ KHÔNG thừa bên cạnh `clearOnDestroy` — CẦN CẢ HAI.
        //    `clearOnDestroy` chỉ dọn kho khi phần tử `<Form>` UNMOUNT; mà hộp thoại đóng
        //    bằng hoạt ảnh nên lượt unmount ⛔ không xảy ra trước lượt mở kế tiếp. `key` ép React
        //    dựng lại ngay khi đổi bản ghi, và `clearOnDestroy` dọn kho ở đúng lượt ấy.
        //    ⚠ Đo được: thiếu `key`, bài `hoSoConKhongTronDuLieu.test.tsx` in ra tên bằng cấp
        //    của người A trong ô của người B — T51.12 lần thứ BA.
        key={suKien?.publicId ?? 'moi'}
        khoa={suKien?.publicId ?? 'moi'}
        form={form}
        initialValues={initialValues}
      />
    </Modal>
  );
}

/**
 * Thân biểu mẫu — tách ra để {@code key} ép dựng lại khi đổi bản ghi.
 *
 * ⛔ Hàm dựng {@code Form.useForm()} vẫn nằm ở component NGOÀI (AntD đòi vậy để {@code Modal.onOk}
 * gọi được {@code validateFields}), nên {@code key} một mình ⛔ không dọn kho giá trị — nó chỉ tạo
 * ra **lượt unmount** mà {@code clearOnDestroy} cần. Hai cơ chế, một bảo đảm.
 */
function BieuMauSuKien({
  khoa,
  form,
  initialValues,
}: {
  khoa: string;
  form: FormInstance<GiaTriBieuMau>;
  initialValues: Partial<GiaTriBieuMau>;
}) {
  // ⛔⛔⛔ ĐẶT GIÁ TRỊ TƯỜNG MINH — ⛔ không dựa vào `initialValues` cho lượt mở THỨ HAI.
  //
  //   `key` + `clearOnDestroy` nghe là đủ, và bài kiểm **bác điều đó**: React dựng cây con MỚI
  //   trong CÙNG một lượt commit với lượt tháo cây cũ, nên `rc-field-form` áp `initialValues` với
  //   `init === false` TRƯỚC khi `clearOnDestroy` của cây cũ kịp dọn kho — và kho vẫn mang giá trị
  //   của bản ghi TRƯỚC. Đo được: ô *Tên* hiện tên bằng cấp của người A khi đang sửa mục của B.
  //
  //   ⇒ Hai lệnh, đúng thứ tự: `resetFields()` gỡ trạng thái *đã chạm* của lượt trước (nếu chỉ
  //   `setFieldsValue` thì cờ `touched` và lỗi hợp lệ hoá của lượt trước còn nguyên), rồi
  //   `setFieldsValue` ghi ĐÈ bằng giá trị của bản ghi hiện tại.
  //
  //   ⚠ `useLayoutEffect` chứ ⛔ không `useEffect`: nó chạy TRƯỚC lượt vẽ, nên người dùng ⛔ không
  //   bao giờ thấy một khung hình mang dữ liệu của người khác — dù chỉ một khung.
  //
  // ⛔⛔⛔ VÀ `clearOnDestroy` ĐÃ BỊ GỠ KHỎI `<Form>` DƯỚI ĐÂY — ⛔ ĐỪNG THÊM LẠI.
  //   Hai biện pháp phòng chồng lên nhau sinh ra một khuyết tật THỨ BA, đo được: lượt dọn của cây
  //   con CŨ chạy **SAU** `useLayoutEffect` của cây con MỚI, nên nó xoá sạch giá trị vừa đặt và ô
  //   ra RỖNG. Nhiễm bẩn hết, nhưng biểu mẫu sửa ⛔ không hiện dữ liệu nào — một lỗi khác, cũng im
  //   lặng. ⇒ MỘT cơ chế, tường minh, có bài kiểm; ⛔ không phải ba cơ chế chồng nhau.
  useLayoutEffect(() => {
    form.resetFields();
    form.setFieldsValue(initialValues);
    // `khoa` là danh tính bản ghi; `initialValues` dựng mới mỗi lượt render nên ⛔ không đưa vào deps.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [khoa, form]);

  return (
    <Form<GiaTriBieuMau> form={form} layout="vertical" initialValues={initialValues}>
      <Form.Item
        name="eventType"
        label="Loại sự kiện"
        rules={[{ required: true, message: 'Chưa chọn loại' }]}
      >
        <Select options={LOAI_SU_KIEN_OPTIONS} />
      </Form.Item>
      {/* ⛔ BA mốc thời gian khác nhau, và trục timeline là mốc HIỆU LỰC. Một quyết định ký
            tháng 3 có hiệu lực từ tháng 1 phải nằm ở tháng 1 — nhãn nói rõ điều đó. */}
      <Form.Item
        name="effectiveOn"
        label="Ngày hiệu lực"
        extra="Trục của timeline — khác ngày ký quyết định"
        rules={[{ required: true, message: 'Chưa nhập ngày hiệu lực' }]}
      >
        <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
      </Form.Item>
      <Form.Item
        name="title"
        label="Nội dung"
        rules={[{ required: true, message: 'Chưa nhập nội dung' }]}
      >
        <Input placeholder="VD: Điều động về Xí nghiệp Thuỷ lợi Thanh Trì" />
      </Form.Item>
      <Form.Item name="decisionNo" label="Số quyết định">
        <Input placeholder="VD: 145/QĐ-TLSN" />
      </Form.Item>
      <Form.Item name="decisionDate" label="Ngày ký quyết định">
        <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
      </Form.Item>
      <Form.Item name="detail" label="Diễn giải">
        <Input.TextArea rows={3} />
      </Form.Item>
    </Form>
  );
}
