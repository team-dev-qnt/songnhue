import { useMutation } from '@tanstack/react-query';
import { DatePicker, Form, type FormInstance, Input, Modal, Select, message } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useLayoutEffect } from 'react';

import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import {
  LOAI_LY_LICH_OPTIONS,
  type LyLichRequest,
  type LyLichView,
  type QualificationKind,
} from './hrVocabulary';

interface GiaTriBieuMau {
  kind: QualificationKind;
  name: string;
  grade?: string;
  major?: string;
  institution?: string;
  certificateNo?: string;
  issuedOn?: Dayjs;
  expiresOn?: Dayjs;
  note?: string;
}

/**
 * Thêm / sửa một mục lý lịch & chuyên môn — CN-04.3.
 *
 * ⛔⛔ Đây là màn hình thứ **ba** của module dính cơ chế `rc-field-form` mà T51.12 đã trả giá, và
 * nó **đã tái phát thật** ở lượt kiểm đầu: ô *Tên* hiện tên bằng cấp của người A khi đang sửa mục
 * của người B. Bằng cấp và chứng chỉ là dữ liệu cá nhân theo NĐ 13/2023.
 *
 * ⛔ Cách chữa ở đây **khác** `EmployeeFormModal`: đặt giá trị **tường minh** ở lượt dựng (xem
 * {@link BieuMauLyLich}), ⛔ không phải `clearOnDestroy`. Lý do đo được ghi ngay trong hàm ấy —
 * chồng hai cơ chế sinh ra một khuyết tật thứ ba.
 */
export function LyLichFormModal({
  open,
  hoSoId,
  muc,
  onClose,
  onSaved,
}: {
  open: boolean;
  hoSoId: string;
  muc: LyLichView | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<GiaTriBieuMau>();
  const laSua = muc !== null;
  const duong = `/hr/employees/${hoSoId}/ly-lich`;

  const luu = useMutation({
    mutationFn: (than: LyLichRequest) =>
      laSua ? api.put(`${duong}/${muc.publicId}`, than) : api.post(duong, than),
    onSuccess: () => {
      message.success(laSua ? 'Đã cập nhật mục lý lịch' : 'Đã thêm mục lý lịch');
      onSaved();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được');
    },
  });

  const initialValues: Partial<GiaTriBieuMau> = muc
    ? {
        kind: muc.kind,
        name: muc.name,
        grade: muc.grade ?? undefined,
        major: muc.major ?? undefined,
        institution: muc.institution ?? undefined,
        certificateNo: muc.certificateNo ?? undefined,
        issuedOn: muc.issuedOn ? dayjs(muc.issuedOn) : undefined,
        expiresOn: muc.expiresOn ? dayjs(muc.expiresOn) : undefined,
        note: muc.note ?? undefined,
      }
    : { kind: 'BANG_CAP' };

  return (
    <Modal
      title={laSua ? 'Sửa mục lý lịch' : 'Thêm mục lý lịch'}
      open={open}
      onCancel={onClose}
      onOk={() => {
        void form.validateFields().then((v) =>
          luu.mutate({
            kind: v.kind,
            name: v.name,
            grade: v.grade ?? null,
            major: v.major ?? null,
            institution: v.institution ?? null,
            certificateNo: v.certificateNo ?? null,
            issuedOn: v.issuedOn ? v.issuedOn.format('YYYY-MM-DD') : null,
            expiresOn: v.expiresOn ? v.expiresOn.format('YYYY-MM-DD') : null,
            note: v.note ?? null,
          }),
        );
      }}
      confirmLoading={luu.isPending}
      okText="Lưu"
      cancelText="Huỷ"
      destroyOnHidden
    >
      <BieuMauLyLich
        // ⛔⛔ `key` ⛔ KHÔNG thừa bên cạnh `clearOnDestroy` — CẦN CẢ HAI.
        //    `clearOnDestroy` chỉ dọn kho khi phần tử `<Form>` UNMOUNT; mà hộp thoại đóng
        //    bằng hoạt ảnh nên lượt unmount ⛔ không xảy ra trước lượt mở kế tiếp. `key` ép React
        //    dựng lại ngay khi đổi bản ghi, và `clearOnDestroy` dọn kho ở đúng lượt ấy.
        //    ⚠ Đo được: thiếu `key`, bài `hoSoConKhongTronDuLieu.test.tsx` in ra tên bằng cấp
        //    của người A trong ô của người B — T51.12 lần thứ BA.
        key={muc?.publicId ?? 'moi'}
        khoa={muc?.publicId ?? 'moi'}
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
function BieuMauLyLich({
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
      <Form.Item name="kind" label="Loại" rules={[{ required: true, message: 'Chưa chọn loại' }]}>
        <Select options={LOAI_LY_LICH_OPTIONS} />
      </Form.Item>
      <Form.Item name="name" label="Tên" rules={[{ required: true, message: 'Chưa nhập tên' }]}>
        <Input placeholder="VD: Kỹ sư Thuỷ lợi · IELTS · AutoCAD" />
      </Form.Item>
      <Form.Item name="major" label="Chuyên ngành">
        <Input />
      </Form.Item>
      <Form.Item name="grade" label="Xếp loại / trình độ đạt được">
        <Input placeholder="VD: Giỏi · 6.5 · B1 · Thành thạo" />
      </Form.Item>
      <Form.Item name="institution" label="Nơi cấp">
        <Input />
      </Form.Item>
      <Form.Item name="certificateNo" label="Số hiệu">
        <Input />
      </Form.Item>
      <Form.Item name="issuedOn" label="Ngày cấp">
        <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
      </Form.Item>
      {/* ⛔ Để TRỐNG nghĩa là "không hết hiệu lực" — đó là trạng thái ĐÚNG cho bằng đại học.
            ⛔ Đừng gợi ý một ngày xa: chuông cảnh báo M4.9 sẽ kêu vào năm ấy (quy tắc 3). */}
      <Form.Item
        name="expiresOn"
        label="Ngày hết hiệu lực"
        extra="Để trống nếu không hết hiệu lực (bằng cấp chuyên môn)"
      >
        <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
      </Form.Item>
      <Form.Item name="note" label="Ghi chú">
        <Input.TextArea rows={2} />
      </Form.Item>
    </Form>
  );
}
