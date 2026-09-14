import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, App, DatePicker, Descriptions, Form, Modal, Select, Input, Spin } from 'antd';
import { type Dayjs } from 'dayjs';
import { useState } from 'react';

import { ApiClientError } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import {
  LOAI_NGHI,
  LOAI_NGHI_LABEL,
  SO_NGAY_LE_THEO_LUAT,
  type DonNghiRequest,
  type LeaveType,
} from './hrVocabulary';
import { nghiPhepApi } from './nghiPhepApi';

interface GiaTriBieuMau {
  leaveType: LeaveType;
  khoang: [Dayjs, Dayjs];
  reason?: string;
}

/**
 * Hộp thoại nộp đơn nghỉ — CN-04.9.
 *
 * <h2>⛔⛔ Ba con số trên hộp thoại này ⛔ KHÔNG do giao diện tính</h2>
 *
 * Số ngày công, số dư và cảnh báo trùng lịch đều gọi `POST /nghi-phep/xem-truoc`. Đếm ngày ở trình
 * duyệt là dựng **bản sao thứ hai** của luật đếm — và bản ở đây ⛔ **không biết ngày lễ**, nên nó
 * sẽ nói "5 ngày" cho một tuần có 30/4 (quy tắc 3).
 *
 * <h2>⛔ Đường xem trước ⛔ không ghi gì</h2>
 *
 * Nó chạy mỗi lần đổi ngày/loại nghỉ. Backend khai rõ nó là `readOnly` — nếu ⛔ không thì mỗi lượt
 * gõ phím là một bản ghi.
 *
 * <h2>⚠ Cảnh báo ≠ chặn</h2>
 *
 * `vuotPhep` và `canhBaoTrungLich` **⛔ không** khoá nút Gửi ở đây; chốt chặn thật là backend
 * (`HR-2001`, và trùng lịch thì cố ý ⛔ không chặn — đó là việc của người duyệt). Khoá nút ở giao
 * diện là dựng một luật nghiệp vụ thứ hai, đặt ở đúng chỗ ⛔ không ai kiểm được.
 */
export function NopDonModal({
  mo,
  onDong,
  onXong,
}: {
  mo: boolean;
  onDong: () => void;
  onXong: () => void;
}) {
  // ⛔⛔ Hộp thoại chỉ TỒN TẠI khi đang mở — và đó là cơ chế tường minh thay cho ba biện pháp
  //    phòng chồng nhau đã đo được là ⛔ KHÔNG cộng lại thành an toàn (T53.7).
  //
  //    Vấn đề gốc: `Form.useForm()` đặt ở component NGOÀI hộp thoại thì ⛔ không unmount theo
  //    `destroyOnHidden`, còn `rc-field-form` chỉ áp `initialValues` ở lượt `init` — đó chính là
  //    cách hai hộp thoại HRM đã TRỘN dữ liệu giữa hai con người (T51.12). Bản đầu của tệp này
  //    vá bằng `useEffect` + `resetFields`, và `react-hooks/set-state-in-effect` đỏ ở cổng
  //    `Frontend — lint` — một cổng mà `tsc` lẫn `vitest` đều ⛔ không thấy (T55.8, lần thứ ba).
  //
  //    ⇒ Tháo hẳn khe hở: `mo = false` ⇒ cây con bị THÁO, nên ⛔ không có trạng thái nào sống sót
  //      sang lượt mở sau. ⛔ Không effect, ⛔ không `resetFields`, ⛔ không thứ tự nào để sai.
  return mo ? <NopDonNoiDung onDong={onDong} onXong={onXong} /> : null;
}

function NopDonNoiDung({ onDong, onXong }: { onDong: () => void; onXong: () => void }) {
  const { message } = App.useApp();
  const [form] = Form.useForm<GiaTriBieuMau>();
  const [nhap, setNhap] = useState<DonNghiRequest | null>(null);

  const xemTruoc = useQuery({
    queryKey: ['hr', 'nghi-phep', 'xem-truoc', nhap],
    queryFn: () => nghiPhepApi.xemTruoc(nhap as DonNghiRequest),
    enabled: nhap !== null,
  });

  const nopMutation = useMutation({
    mutationFn: (body: DonNghiRequest) => nghiPhepApi.nop(body),
    onSuccess: () => {
      message.success('Đã gửi đơn — đang chờ duyệt');
      onXong();
      onDong();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không gửi được đơn');
    },
  });

  const doiGiaTri = () => {
    const v = form.getFieldsValue();
    if (!v.leaveType || !v.khoang?.[0] || !v.khoang?.[1]) {
      setNhap(null);
      return;
    }
    setNhap({
      leaveType: v.leaveType,
      fromDate: v.khoang[0].format('YYYY-MM-DD'),
      toDate: v.khoang[1].format('YYYY-MM-DD'),
      reason: v.reason,
    });
  };

  const gui = async () => {
    try {
      const v = await form.validateFields();
      nopMutation.mutate({
        leaveType: v.leaveType,
        fromDate: v.khoang[0].format('YYYY-MM-DD'),
        toDate: v.khoang[1].format('YYYY-MM-DD'),
        reason: v.reason,
      });
    } catch {
      // Biểu mẫu tự hiện lỗi từng trường. ⛔ Đừng để lời hứa rơi ra ngoài — `Modal.onOk` ⛔ không
      // chờ giá trị trả về, nên một `validateFields()` hỏng thành unhandled rejection.
    }
  };

  const xt = xemTruoc.data;

  return (
    <Modal
      open
      title="Nộp đơn nghỉ phép"
      okText="Gửi đơn"
      cancelText="Huỷ"
      onOk={() => void gui()}
      onCancel={onDong}
      confirmLoading={nopMutation.isPending}
      destroyOnHidden
      width={640}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ leaveType: 'PHEP_NAM' }}
        onValuesChange={doiGiaTri}
      >
        <Form.Item
          name="leaveType"
          label="Loại nghỉ"
          rules={[{ required: true, message: 'Chọn loại nghỉ' }]}
        >
          <Select
            options={LOAI_NGHI.map((l) => ({ value: l, label: LOAI_NGHI_LABEL[l] }))}
            placeholder="Chọn loại nghỉ"
          />
        </Form.Item>

        <Form.Item
          name="khoang"
          label="Từ ngày — đến ngày"
          rules={[{ required: true, message: 'Chọn khoảng nghỉ' }]}
          extra="Cuối tuần và ngày lễ không tính vào ngày công — hệ thống tự trừ."
        >
          <DatePicker.RangePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item name="reason" label="Lý do" rules={[{ max: 1000 }]}>
          <Input.TextArea rows={2} placeholder="Nêu ngắn gọn lý do nghỉ" />
        </Form.Item>
      </Form>

      {nhap === null ? null : xemTruoc.isLoading ? (
        <Spin />
      ) : xt ? (
        <>
          <Descriptions size="small" bordered column={1}>
            <Descriptions.Item label="Số ngày công">
              <b>{xt.soNgayCong}</b> ngày
              {xt.soNgayLeTru > 0 ? ` (đã trừ ${xt.soNgayLeTru} ngày lễ trong khoảng)` : ''}
            </Descriptions.Item>
            <Descriptions.Item label="Số dư còn lại sau đơn này">
              {xt.soDu.conLai} ngày
            </Descriptions.Item>
            {xt.canCapHaiDuyet ? (
              <Descriptions.Item label="Quy trình">
                Đơn này dài nên cần <b>hai cấp duyệt</b>.
              </Descriptions.Item>
            ) : null}
          </Descriptions>

          {/* ⛔⛔ ⛔ KHÔNG hiện một cờ xanh/đỏ *"đã cấu hình ngày lễ"*. Seed đặt sẵn 4 ngày dương
              lịch cố định mỗi năm, nên một cờ như vậy nói CÓ trong khi **Tết vẫn thiếu** — đúng ca
              nguy hiểm nhất. Hiện CON SỐ đã khai so với 11 ngày Điều 112 BLLĐ 2019 (luật 9). */}
          {!xt.duNgayLeTheoLuat ? (
            <Alert
              type="warning"
              showIcon
              style={{ marginTop: 12 }}
              message={`Danh mục ngày lễ mới khai ${xt.soNgayLeDaKhai}/${SO_NGAY_LE_THEO_LUAT} ngày`}
              description={
                <>
                  Điều 112 Bộ luật Lao động 2019 quy định <b>{SO_NGAY_LE_THEO_LUAT}</b> ngày nghỉ lễ
                  mỗi năm. Ngày lễ âm lịch (Tết Nguyên đán, Giỗ Tổ Hùng Vương) đổi ngày dương mỗi
                  năm nên hệ thống ⛔ không tự suy ra được — chúng phải được khai ở{' '}
                  <b>Nhân sự › Ngày nghỉ lễ</b>. Chừng nào còn thiếu, số ngày công ở trên có thể{' '}
                  <b>cao hơn thực tế</b>.
                </>
              }
            />
          ) : null}

          {xt.vuotPhep ? (
            <Alert
              type="error"
              showIcon
              style={{ marginTop: 12 }}
              message="Vượt số dư phép năm"
              description="Đơn này xin nhiều hơn số ngày còn lại — hệ thống sẽ từ chối khi gửi."
            />
          ) : null}

          {xt.canhBaoTrungLich ? (
            <Alert
              type="warning"
              showIcon
              style={{ marginTop: 12 }}
              message="Trùng lịch nghỉ trong đơn vị"
              description={`${xt.canhBaoTrungLich}. Đây là cảnh báo để người duyệt cân nhắc, không phải một lệnh chặn.`}
            />
          ) : null}
        </>
      ) : null}
    </Modal>
  );
}
