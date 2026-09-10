import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Col, Form, Input, Modal, Row, Skeleton } from 'antd';

import { useAuth } from '@/app/auth/useAuth';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import { type SensitiveFields } from './hrVocabulary';

/**
 * Hộp thoại trường 🔒 của hồ sơ CBNV — CN-04.2, quy tắc 10 và 13, NĐ 13/2023/NĐ-CP.
 *
 * <h2>⛔ Đây là UX, ⛔ KHÔNG phải bảo mật</h2>
 *
 * Ẩn nút theo `hr:employee:view-sensitive` chỉ để người dùng khỏi bấm vào thứ chắc chắn 403 —
 * conventions.md §4.2 tầng 1. Chốt chặn thật là `@RequirePermission` trên
 * `EmployeeSensitiveController`, và **ADMIN bị loại trừ tường minh** khỏi quyền này
 * (`V202608131007:169`): chỉ SUPER_ADMIN và ADMIN_HR đọc được.
 *
 * <h2>⛔⛔ `PUT` là THAY TOÀN PHẦN ⇒ phải ĐỌC XONG mới cho GHI</h2>
 *
 * `EmployeeSensitiveService.luu` ghi **cả 8 cột** mỗi lượt: một ô ⛔ không gửi lên là một ô bị
 * xoá. Nên nút Lưu khoá cho tới khi lượt `GET` trả về — mở hộp thoại rồi bấm Lưu ngay mà ⛔ không
 * chặn là xoá trắng cả 8 ô, ⛔ không log, ⛔ không mã lỗi. Đúng hình dạng §11.19 và T47.14.
 *
 * <h2>⛔ Vì sao lương và ngày cấp CCCD là ô CHỮ, ⛔ không phải `InputNumber`/`DatePicker`</h2>
 *
 * Cột ở CSDL là bản mã AES-256-GCM (`TEXT`) và DTO khai `String` — thứ đi qua dây luôn là chuỗi
 * người dùng gõ. Hai hệ quả, cả hai đã có tiền lệ trong kho:
 *
 * <ul>
 *   <li>`InputNumber` cho **lương** là đưa số tiền qua dấu phẩy động của JavaScript — đúng thứ quy
 *       tắc 2 cấm. Và T46.6 đo được: dán `21.048201, 105.782500` vào `InputNumber` cho ra `21`,
 *       ⛔ không một dòng báo lỗi.
 *   <li>`DatePicker` cho **ngày cấp CCCD** phải phân tích ngược một chuỗi tự do. Chuỗi ⛔ không
 *       phân tích được sẽ thành `null`, và trên một `PUT` thay-toàn-phần thì `null` nghĩa là
 *       **xoá**. Một ô nhập lặng lẽ xoá dữ liệu nó vừa đọc được là cái giá quá đắt cho một cái
 *       lịch bấm.
 * </ul>
 *
 * <h2>Mỗi lượt MỞ hộp thoại này để lại một dòng `security_events`</h2>
 *
 * `audit_logs` chỉ sinh dòng khi có **thay đổi**. Thiếu nhật ký đọc thì một người có quyền mở lần
 * lượt toàn bộ hồ sơ để chép số tài khoản ⛔ không để lại dấu vết ở bảng nào. Câu ấy nói thẳng ra
 * trên giao diện — người dùng có quyền biết mình đang bị ghi nhận.
 */
export function SensitiveModal({
  open,
  publicId,
  tenCanBo,
  onClose,
}: {
  open: boolean;
  publicId: string | null;
  tenCanBo: string | null;
  onClose: () => void;
}) {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<SensitiveFields>();

  const coQuyen = hasPermission('hr:employee:view-sensitive');
  const dangMo = open && !!publicId && coQuyen;

  const query = useQuery({
    queryKey: ['hr', 'employees', publicId, 'sensitive'],
    queryFn: () => api.get<SensitiveFields>(`/hr/employees/${publicId}/sensitive`),
    enabled: dangMo,
    // ⛔ ⛔ Không giữ lại trong bộ nhớ đệm: đây là dữ liệu cá nhân đã giải mã, và mỗi lượt đọc phải
    //    đi qua máy chủ để `security_events` ghi được. Đệm nó là biến lượt đọc thứ hai thành một
    //    lượt đọc ⛔ không ai nhìn thấy.
    gcTime: 0,
    staleTime: 0,
  });

  const luuMutation = useMutation({
    mutationFn: (payload: SensitiveFields) =>
      api.put<void>(`/hr/employees/${publicId}/sensitive`, payload),
    onSuccess: async () => {
      message.success('Đã lưu trường bảo mật');
      // Cờ "ô nào đã có dữ liệu" nằm ở `EmployeeDetail.sensitive` — ⛔ không xoá đệm thì biểu mẫu
      // hồ sơ mở sau đó vẫn nói "chưa nhập" cho một ô vừa được nhập.
      await queryClient.invalidateQueries({ queryKey: ['hr', 'employees'] });
      onClose();
    },
    onError: (caught: unknown) => {
      // `HR-1003` (CCCD trùng) khai `handling: 'form'`, nhưng backend ném `ConflictException`
      // ⛔ không kèm `details[]` — nên `datLoiTheoTruong` trả `false` và câu lỗi rơi xuống toast.
      // Giữ CẢ HAI nhánh: thiếu nhánh sau là một lỗi 409 hiện ra cho ⛔ không ai.
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không lưu được trường bảo mật',
      );
    },
  });

  const daDoc = query.data !== undefined;

  const luu = async () => {
    try {
      const values = await form.validateFields();
      luuMutation.mutate({
        nationalId: chu(values.nationalId),
        nationalIdIssuedOn: chu(values.nationalIdIssuedOn),
        nationalIdIssuedPlace: chu(values.nationalIdIssuedPlace),
        baseSalary: chu(values.baseSalary),
        salaryCoefficient: chu(values.salaryCoefficient),
        bankAccount: chu(values.bankAccount),
        taxCode: chu(values.taxCode),
        socialInsuranceNo: chu(values.socialInsuranceNo),
      });
    } catch {
      // Biểu mẫu tự hiện lỗi từng trường. ⛔ Đừng để lời hứa này rơi ra ngoài: `Modal.onOk` KHÔNG
      // chờ giá trị trả về, nên một `validateFields()` hỏng trở thành unhandled rejection — một
      // dòng đỏ ở console cho một luồng HOÀN TOÀN bình thường (người dùng bỏ trống ô bắt buộc).
    }
  };

  return (
    <Modal
      open={open}
      title={`Trường bảo mật — ${tenCanBo ?? ''}`}
      onCancel={onClose}
      onOk={() => void luu()}
      okText="Lưu"
      cancelText="Đóng"
      okButtonProps={{ disabled: !daDoc }}
      confirmLoading={luuMutation.isPending}
      width={720}
      destroyOnClose
    >
      {!coQuyen && (
        <Alert
          type="warning"
          showIcon
          message="Không có quyền xem trường bảo mật"
          description="Chỉ Super Admin và Quản trị nhân sự đọc được nhóm trường này (NĐ 13/2023 — nguyên tắc tối thiểu). Quản trị hệ thống cố ý KHÔNG có quyền ấy."
        />
      )}

      {coQuyen && query.isError && (
        <Alert
          type="error"
          showIcon
          message="Không đọc được trường bảo mật"
          description={
            query.error instanceof ApiClientError
              ? query.error.message
              : 'Lỗi không xác định — đóng hộp thoại và thử lại.'
          }
        />
      )}

      {coQuyen && query.isLoading && <Skeleton active paragraph={{ rows: 6 }} />}

      {coQuyen && daDoc && (
        <>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="Lượt mở này đã được ghi vào nhật ký bảo mật"
            description={
              <>
                Mỗi lượt đọc nhóm trường này để lại một dòng <code>security_events</code> kèm mã cán
                bộ và người đọc. <b>Lưu là thay toàn phần</b>: ô để trống sẽ xoá giá trị đang có, ⛔
                không phải giữ nguyên.
              </>
            }
          />
          <Form<SensitiveFields>
            form={form}
            layout="vertical"
            initialValues={query.data}
            // ⛔⛔ `clearOnDestroy` ⛔ KHÔNG phải một tuỳ chọn dọn dẹp cho gọn — nó là thứ
            //    DUY NHẤT ngăn dữ liệu của hồ sơ TRƯỚC ghi đè lên hồ sơ SAU.
            //    `Form.useForm()` sống ở component NGOÀI nên nó ⛔ không unmount theo
            //    `key`; `rc-field-form` áp `initialValues` bằng `setInitialValues(v, init)`
            //    với `init = !initialized`, nên lượt mở THỨ HAI ⛔ không ghi đè kho giá trị,
            //    và `preserve` mặc định `true` khiến `destroyOnClose` cũng ⛔ không dọn.
            //    ⛔ `afterClose + resetFields()` ⛔ KHÔNG chữa được: `resetFields` đưa kho về
            //    `initialValues` của lượt TRƯỚC. Đo được ở `hoSoKhongTronDuLieu.test.tsx`:
            //    mở A → đóng → mở B cho ra "Nguyễn Văn A" trong ô của B.
            clearOnDestroy
          >
            <Row gutter={16}>
              <Col span={8}>
                <Form.Item name="nationalId" label="Số CCCD" rules={[{ max: 20 }]}>
                  <Input placeholder="001199001234" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item
                  name="nationalIdIssuedOn"
                  label="Ngày cấp"
                  rules={[{ max: 30 }]}
                  extra="Gõ theo cách Công ty đang ghi trên hồ sơ giấy."
                >
                  <Input placeholder="12/05/2021" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name="nationalIdIssuedPlace" label="Nơi cấp" rules={[{ max: 255 }]}>
                  <Input placeholder="Cục Cảnh sát QLHC về TTXH" />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="baseSalary"
                  label="Lương cơ bản (VNĐ)"
                  rules={[{ max: 30 }]}
                  extra="Hệ thống chỉ LƯU TRỮ — không có module tính lương, không chấm công (chốt C4)."
                >
                  <Input placeholder="7500000" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="salaryCoefficient" label="Hệ số lương" rules={[{ max: 20 }]}>
                  <Input placeholder="2.34" />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={8}>
                <Form.Item name="bankAccount" label="Số tài khoản" rules={[{ max: 50 }]}>
                  <Input />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name="taxCode" label="Mã số thuế" rules={[{ max: 20 }]}>
                  <Input />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name="socialInsuranceNo" label="Số sổ BHXH" rules={[{ max: 30 }]}>
                  <Input />
                </Form.Item>
              </Col>
            </Row>
          </Form>
        </>
      )}
    </Modal>
  );
}

/** `null` cho ô trống — cùng lý do với biểu mẫu hồ sơ: backend `rutGon` phân biệt rỗng với null. */
function chu(giaTri: string | null | undefined): string | null {
  const rut = giaTri?.trim();
  return rut ? rut : null;
}

export default SensitiveModal;
