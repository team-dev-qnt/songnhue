import { useMutation } from '@tanstack/react-query';
import { Alert, App, DatePicker, Form, Input, Modal, Select } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';

import { type ManualEntryRequest, type Station } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

/**
 * Nhập tay số đo khi API gián đoạn — CN-03.2 (WS-32 / T32.7).
 *
 * <h3>⭐⭐ Đường này KHÁC đường tự động ở đúng chỗ quan trọng — và người dùng phải biết</h3>
 *
 * Số đo của **máy** không lấy lại được (nguồn ⛔ không có API lịch sử), nên một giá trị lạ vẫn
 * được ghi kèm cờ *Nghi ngờ*. Số đo của **người** thì gõ lại được ngay — nên một giá trị ngoài
 * khoảng vật lý bị **từ chối thẳng** (`HYD-2001`), ⛔ không lặng lẽ tạo ra một dòng chờ duyệt mà
 * chính người vừa gõ sẽ phải đi duyệt.
 *
 * <h3>⛔ Ô đã có số đo thì ⛔ KHÔNG ghi đè</h3>
 *
 * Bản ghi đang nằm đó là bằng chứng nguyên trạng của thứ nguồn đã trả về. Ô bị chiếm bởi một bản
 * ghi *Nghi ngờ* trả `HYD-2002` và **chỉ đường** sang màn hình Dữ liệu nghi ngờ; ô bị chiếm bởi
 * một bản ghi hợp lệ trả `HYD-2007`.
 *
 * <h3>⚠ `giaTri` là CHUỖI trên cả hai chiều dây</h3>
 *
 * `2.300` gửi đi dưới dạng số JSON thành `2.3`, và với mực nước thì chữ số thập phân thứ ba là
 * **milimét**. Ô nhập vì thế là `<Input>` chữ, ⛔ không phải `<InputNumber>` — quy tắc 2.
 */
export function NhapTaySoDoModal({
  open,
  diemDo,
  onClose,
  onDone,
}: {
  open: boolean;
  diemDo: Station[];
  onClose: () => void;
  onDone: () => void;
}) {
  const [form] = Form.useForm<{
    diemDoId: string;
    maLoaiChiSo: string;
    mocDo: Dayjs;
    giaTri: string;
    ghiChu?: string;
  }>();
  const { message } = App.useApp();

  const diemDoDangChon = Form.useWatch('diemDoId', form);
  const loaiDangChon = Form.useWatch('maLoaiChiSo', form);
  const tramDangChon = diemDo.find((s) => s.id === diemDoDangChon);
  // ⚠ Đơn vị đi kèm NHÃN Ô NHẬP, không chỉ nằm trong danh sách chọn: người gõ nhìn vào ô, không
  //   nhìn lại ô phía trên. Nguồn trả cm còn hệ thống lưu m — thiếu nhãn là sai đúng 100 lần.
  const donVi = tramDangChon?.measurementTypes.find((t) => t.code === loaiDangChon)?.unit;

  const ghi = useMutation({
    mutationFn: (body: ManualEntryRequest) => api.post('/hyd/so-do/nhap-tay', body),
    onSuccess: () => {
      message.success('Đã ghi số đo nhập tay');
      form.resetFields();
      onDone();
    },
    // ⭐ T28.31: mutation không có `onError` là mutation im lặng tuyệt đối — người dùng bấm Lưu,
    //   không có gì xảy ra và không có gì báo. Ba mã lỗi của đường này (HYD-2001 / HYD-2002 /
    //   HYD-2007) đều vô nghĩa nếu không tới được màn hình.
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không ghi được số đo');
    },
  });

  return (
    <Modal
      open={open}
      title="Nhập tay số đo"
      okText="Lưu"
      cancelText="Đóng"
      confirmLoading={ghi.isPending}
      onCancel={() => {
        form.resetFields();
        onClose();
      }}
      onOk={() =>
        form.validateFields().then((v) =>
          ghi.mutate({
            diemDoId: v.diemDoId,
            maLoaiChiSo: v.maLoaiChiSo,
            // ⚠ Gửi ISO UTC — cùng quy ước với mọi mốc thời gian khác của hệ. Phép đổi múi giờ
            //   nằm ở đúng một chỗ trong giao diện; hai quy ước là chỗ mọi lỗi lệch 7 tiếng ra đời.
            mocDo: v.mocDo.toISOString(),
            giaTri: v.giaTri.trim(),
            ghiChu: v.ghiChu?.trim() || undefined,
          }),
        )
      }
      destroyOnClose
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Dùng khi API gián đoạn"
        description="Dòng ghi ở đây mang tên người nhập và được đánh dấu nguồn Nhập tay — nhờ vậy về sau vẫn phân biệt được 'poller chết' với 'nguồn không phát'. ⛔ Không ghi đè được số đo đã có."
        // ⚠ Câu này phải đúng: §10.69 — một dòng chữ hứa điều mã không làm còn tệ hơn không có dòng nào.
      />

      <Form form={form} layout="vertical" initialValues={{ mocDo: dayjs() }}>
        <Form.Item
          name="diemDoId"
          label="Điểm đo"
          rules={[{ required: true, message: 'Chọn điểm đo' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="Chọn điểm đo"
            options={diemDo.map((s) => ({ value: s.id, label: `${s.code} — ${s.name}` }))}
            onChange={() => form.setFieldValue('maLoaiChiSo', undefined)}
          />
        </Form.Item>

        <Form.Item
          name="maLoaiChiSo"
          label="Loại chỉ số"
          rules={[{ required: true, message: 'Chọn loại chỉ số' }]}
          // ⚠ Chỉ chào những loại chỉ số ĐÃ TÍCH cho điểm đo ấy. Chào cả danh mục là mời người
          //   dùng ghi lượng mưa vào một trạm chỉ đo mực nước — đúng loại sai số liệu câm.
          extra={
            tramDangChon && tramDangChon.measurementTypes.length === 0
              ? '⚠ Điểm đo này chưa tích loại chỉ số nào trong hồ sơ — vào Điểm đo tích ô "Loại chỉ số" trước'
              : 'Chỉ những loại chỉ số đã tích trong hồ sơ điểm đo'
          }
        >
          <Select
            disabled={!tramDangChon}
            placeholder={tramDangChon ? 'Chọn loại chỉ số' : 'Chọn điểm đo trước'}
            options={(tramDangChon?.measurementTypes ?? []).map((t) => ({
              value: t.code,
              label: `${t.name} (${t.unit})`,
            }))}
          />
        </Form.Item>

        <Form.Item
          name="mocDo"
          label="Thời điểm đo"
          rules={[{ required: true, message: 'Chọn thời điểm đo' }]}
          extra="Là mốc ĐO ĐƯỢC tại trạm, ⛔ không phải lúc nhập. Không nhận mốc ở tương lai — một dòng đề ngày mai sẽ ghim mực nước hiện tại của trạm cho tới khi tới ngày ấy"
        >
          <DatePicker showTime style={{ width: '100%' }} format="DD/MM/YYYY HH:mm" />
        </Form.Item>

        <Form.Item
          name="giaTri"
          label={donVi ? `Giá trị (${donVi})` : 'Giá trị'}
          rules={[
            { required: true, message: 'Nhập giá trị đo' },
            {
              pattern: /^-?\d{1,9}([.,]\d{1,3})?$/,
              message: 'Số thập phân, tối đa 3 chữ số sau dấu phẩy',
            },
          ]}
          extra="Đơn vị CHUẨN HOÁ của loại chỉ số (mực nước tính bằng m, ⛔ không phải cm như nguồn trả). Ngoài khoảng vật lý đã cấu hình sẽ bị từ chối"
          normalize={(v?: string) => v?.replace(',', '.')}
        >
          <Input placeholder="2.345" />
        </Form.Item>

        <Form.Item
          name="ghiChu"
          label="Ghi chú"
          extra="Vì sao phải nhập tay — VD “API chết từ 3h sáng, đọc thước tại trạm”"
        >
          <Input.TextArea rows={2} maxLength={500} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}

export default NhapTaySoDoModal;
