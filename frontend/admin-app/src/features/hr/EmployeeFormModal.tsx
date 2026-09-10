import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Col,
  DatePicker,
  Divider,
  Form,
  type FormInstance,
  Input,
  Modal,
  Row,
  Select,
  Skeleton,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useMemo } from 'react';

import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import { ApiClientError, api } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import {
  type ContractType,
  type EmployeeDetail,
  type EmployeeRequest,
  type EmploymentStatus,
  type Gender,
  GIOI_TINH_OPTIONS,
  LOAI_HOP_DONG_OPTIONS,
  type MaritalStatus,
  NGAY_SINH_DEN,
  NGAY_SINH_TU,
  type PositionView,
  TINH_TRANG_HON_NHAN_OPTIONS,
  TRANG_THAI_CONG_TAC_OPTIONS,
  TRANG_THAI_DA_NGHI,
} from './hrVocabulary';

/**
 * Biểu mẫu hồ sơ cán bộ nhân viên — CN-04.2 (WS-51).
 *
 * <h2>⛔⛔ `PUT` là THAY TOÀN PHẦN — và §11.19 nói điều đó nghĩa là gì</h2>
 *
 * Mọi trường vắng trong thân JSON được ghi thành `null`. Ngày 09/09 một `PUT` thiếu trường đã xoá
 * trắng tuyến sông/lý trình của **19 điểm đo** và vô hình suốt hai tuần, vì mọi ô vốn đã rỗng nên
 * ⛔ không ai thấy gì. Hồ sơ nhân sự có **23 trường** và phần lớn ⛔ không bắt buộc — cùng hình
 * dạng, chỉ nhiều ô hơn.
 *
 * <p>Ba lớp chặn ở đây, ⛔ không lớp nào là một lời dặn:
 *
 * <ol>
 *   <li>{@code EmployeeRequest} khai **đủ 23 trường ⛔ không optional** ⇒ dựng payload thiếu là
 *       lỗi biên dịch, ⛔ không phải một ô lặng lẽ về `null`.
 *   <li>Biểu mẫu chỉ dựng **sau khi** dữ liệu đã về ({@link BieuMauHoSo} nhận `initialValues`),
 *       theo đúng khuôn `ArticleEditorPage`: ⛔ không có khoảng giữa nào để mất gì.
 *   <li>Ô "Chức vụ" **giữ lại chức vụ đang gán kể cả khi nó đã ngừng dùng** — xem {@link dsChucVu}.
 * </ol>
 *
 * <h2>Vì sao ⛔ không dùng `useEffect` để đổ dữ liệu vào biểu mẫu</h2>
 *
 * ESLint chặn (`react-hooks/set-state-in-effect`), và lý do sâu hơn cái tên của luật: màn hình vẽ
 * một lượt với ô trống rồi vẽ lại với dữ liệu, nên nếu người dùng kịp gõ vào khoảng giữa thì cú gõ
 * ấy bị effect ghi đè. `ArticleEditorPage` đã trả giá cho đúng chuyện đó (T41.9).
 */
export function EmployeeFormModal({
  open,
  publicId,
  onClose,
  onSaved,
}: {
  open: boolean;
  /** `null` = thêm mới. */
  publicId: string | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<GiaTriBieuMau>();
  const laSua = publicId !== null;

  const chiTiet = useQuery({
    queryKey: ['hr', 'employees', publicId],
    queryFn: () => api.get<EmployeeDetail>(`/hr/employees/${publicId}`),
    enabled: open && laSua,
  });

  const chucVu = useQuery({
    queryKey: ['hr', 'positions'],
    queryFn: () => api.get<PositionView[]>('/hr/positions'),
    enabled: open,
  });

  const lamMoi = async () => {
    await queryClient.invalidateQueries({ queryKey: ['hr', 'employees'] });
  };

  const createMutation = useMutation({
    mutationFn: (payload: EmployeeRequest) => api.post<EmployeeDetail>('/hr/employees', payload),
    onSuccess: async () => {
      message.success('Đã thêm hồ sơ cán bộ');
      await lamMoi();
      onSaved();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không thêm được hồ sơ');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (payload: EmployeeRequest) =>
      api.put<EmployeeDetail>(`/hr/employees/${publicId}`, payload),
    onSuccess: async () => {
      message.success('Đã cập nhật hồ sơ cán bộ');
      await lamMoi();
      onSaved();
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được hồ sơ');
    },
  });

  /**
   * ⛔⛔ Danh sách chức vụ chọn được — lọc "đang dùng" **NHƯNG giữ lại cái đang gán**.
   *
   * Thiếu vế sau là một §11.19 mới: hồ sơ đang giữ một chức vụ vừa bị ngừng dùng, người sửa mở
   * biểu mẫu ra, ô Chức vụ ⛔ không tìm thấy giá trị nên hiện trống, và cú Lưu kế tiếp gửi `null`
   * — chức vụ biến mất khỏi hồ sơ mà ⛔ không ai bấm gì vào ô ấy.
   */
  const dsChucVu = useMemo(() => {
    const dangGan = chiTiet.data?.positionId ?? null;
    return (chucVu.data ?? []).filter((c) => c.active !== false || c.publicId === dangGan);
  }, [chucVu.data, chiTiet.data?.positionId]);

  const dangTaiHoSo = laSua && chiTiet.isLoading;
  const sanSang = !laSua || !!chiTiet.data;

  const luu = async () => {
    try {
      const values = await form.validateFields();
      const payload = dungPayload(values);
      if (laSua) {
        updateMutation.mutate(payload);
      } else {
        createMutation.mutate(payload);
      }
    } catch {
      // Biểu mẫu tự hiện lỗi từng trường. ⛔ Đừng để lời hứa này rơi ra ngoài: `Modal.onOk` KHÔNG
      // chờ giá trị trả về, nên một `validateFields()` hỏng trở thành unhandled rejection — một
      // dòng đỏ ở console cho một luồng HOÀN TOÀN bình thường (người dùng bỏ trống ô bắt buộc).
    }
  };

  return (
    <Modal
      open={open}
      title={laSua ? `Sửa hồ sơ ${chiTiet.data?.code ?? ''}` : 'Thêm hồ sơ cán bộ'}
      onCancel={onClose}
      onOk={() => void luu()}
      okText="Lưu"
      cancelText="Huỷ"
      okButtonProps={{ disabled: !sanSang }}
      confirmLoading={createMutation.isPending || updateMutation.isPending}
      width={860}
      destroyOnClose
    >
      {chiTiet.isError && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Không tải được hồ sơ"
          description={
            chiTiet.error instanceof ApiClientError
              ? chiTiet.error.message
              : 'Lỗi không xác định — đóng hộp thoại và thử lại.'
          }
        />
      )}
      {dangTaiHoSo && <Skeleton active paragraph={{ rows: 10 }} />}
      {sanSang && (
        <BieuMauHoSo
          // ⚠ `key` ép dựng lại khi chuyển sang hồ sơ khác: `initialValues` của AntD chỉ đổ dữ
          //   liệu ở lượt mount ĐẦU (T41.9 — đúng lỗi đã làm mất nội dung ở luồng Phục hồi).
          key={publicId ?? 'moi'}
          form={form}
          initialValues={dungGiaTriBanDau(chiTiet.data)}
          laSua={laSua}
          dsChucVu={dsChucVu}
          dangTaiChucVu={chucVu.isLoading}
        />
      )}
    </Modal>
  );
}

// =============================================================================
// Lớp trong — chỉ dựng biểu mẫu, dữ liệu đã có sẵn
// =============================================================================

interface GiaTriBieuMau {
  code: string;
  fullName: string;
  dateOfBirth: Dayjs | null;
  gender: Gender | null;
  ethnicity: string;
  hometown: string;
  address: string;
  phone: string;
  workEmail: string;
  personalEmail: string;
  maritalStatus: MaritalStatus | null;
  emergencyContactName: string;
  emergencyContactPhone: string;
  orgUnitId: string | undefined;
  positionId: string | undefined;
  jobTitle: string;
  hiredAt: Dayjs | null;
  contractType: ContractType | null;
  contractSignedAt: Dayjs | null;
  contractExpiresAt: Dayjs | null;
  status: EmploymentStatus;
  terminatedAt: Dayjs | null;
  terminationReason: string;
}

function BieuMauHoSo({
  form,
  initialValues,
  laSua,
  dsChucVu,
  dangTaiChucVu,
}: {
  form: FormInstance<GiaTriBieuMau>;
  initialValues: GiaTriBieuMau;
  laSua: boolean;
  dsChucVu: PositionView[];
  dangTaiChucVu: boolean;
}) {
  const trangThai = Form.useWatch('status', form) ?? initialValues.status;
  const daNghi = TRANG_THAI_DA_NGHI.includes(trangThai);

  return (
    <Form<GiaTriBieuMau>
      form={form}
      layout="vertical"
      initialValues={initialValues}
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
      <Divider orientation="left">Thông tin cá nhân</Divider>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            name="code"
            label="Mã cán bộ"
            rules={[{ required: true, message: 'Nhập mã cán bộ' }, { max: 50 }]}
            extra={
              laSua
                ? '⛔ Không đổi được suốt quá trình công tác (CN-04.2).'
                : 'Không đổi được sau khi lưu.'
            }
          >
            <Input placeholder="NV-2019-001" disabled={laSua} />
          </Form.Item>
        </Col>
        <Col span={10}>
          <Form.Item
            name="fullName"
            label="Họ và tên"
            rules={[{ required: true, message: 'Nhập họ và tên' }, { max: 255 }]}
          >
            <Input />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item
            name="dateOfBirth"
            label="Ngày sinh"
            // ⛔⛔ `ck_employees_dob_range` ép khoảng này ở CSDL vì đó là chỗ MỌI đường ghi đi qua
            //    (quy tắc 12). Chặn thêm ở đây ⛔ không phải trùng lặp: thiếu nó thì người dùng
            //    nhận một lỗi ràng buộc CSDL trần, ⛔ không chỉ được ô nào sai.
            extra="18 ≤ tuổi ≤ 70 theo đặc tả."
          >
            <DatePicker
              format="DD/MM/YYYY"
              style={{ width: '100%' }}
              disabledDate={(d) =>
                d.isBefore(NGAY_SINH_TU, 'day') || d.isAfter(NGAY_SINH_DEN, 'day')
              }
            />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={6}>
          <Form.Item name="gender" label="Giới tính">
            <Select allowClear options={GIOI_TINH_OPTIONS} />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item name="maritalStatus" label="Tình trạng hôn nhân">
            <Select allowClear options={TINH_TRANG_HON_NHAN_OPTIONS} />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item name="ethnicity" label="Dân tộc" rules={[{ max: 100 }]}>
            <Input placeholder="Kinh" />
          </Form.Item>
        </Col>
        <Col span={6}>
          <Form.Item name="phone" label="Điện thoại" rules={[{ max: 30 }]}>
            <Input />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item name="hometown" label="Quê quán" rules={[{ max: 255 }]}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="address" label="Địa chỉ thường trú" rules={[{ max: 500 }]}>
            <Input />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="workEmail"
            label="Email công việc"
            rules={[{ type: 'email', message: 'Email không hợp lệ' }, { max: 255 }]}
          >
            <Input />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            name="personalEmail"
            label="Email cá nhân"
            rules={[{ type: 'email', message: 'Email không hợp lệ' }, { max: 255 }]}
          >
            <Input />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="emergencyContactName"
            label="Người liên hệ khẩn cấp"
            rules={[{ max: 255 }]}
          >
            <Input />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            name="emergencyContactPhone"
            label="Điện thoại liên hệ khẩn cấp"
            rules={[{ max: 30 }]}
          >
            <Input />
          </Form.Item>
        </Col>
      </Row>

      <Divider orientation="left">Thông tin công tác</Divider>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="orgUnitId"
            label="Đơn vị công tác"
            // ⛔⛔ BẮT BUỘC — `org_unit_id NOT NULL`. Một hồ sơ ⛔ không thuộc đơn vị nào là hồ sơ
            //    ⛔ không ai chịu trách nhiệm và MỌI người đọc được: bộ lọc phạm vi ⛔ không cắt
            //    được nó. Đây là dữ liệu cá nhân theo NĐ 13/2023.
            rules={[{ required: true, message: 'Chọn đơn vị công tác' }]}
          >
            <OrgUnitTreeSelect />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="positionId" label="Chức vụ">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              loading={dangTaiChucVu}
              placeholder="Chọn chức vụ trong danh mục"
              options={dsChucVu.map((c) => ({
                value: c.publicId,
                label: c.positionGroup ? `${c.name} — ${c.positionGroup}` : c.name,
              }))}
            />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="jobTitle"
            label="Chức danh công việc"
            rules={[{ max: 255 }]}
            extra="Câu chữ tự do — khác với Chức vụ trong danh mục."
          >
            <Input placeholder="Kỹ sư thuỷ lợi" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="hiredAt" label="Ngày vào làm">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
      </Row>

      <Divider orientation="left">Hợp đồng lao động</Divider>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item name="contractType" label="Loại hợp đồng">
            <Select allowClear options={LOAI_HOP_DONG_OPTIONS} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="contractSignedAt" label="Ngày ký">
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="contractExpiresAt"
            label="Ngày hết hạn"
            dependencies={['contractSignedAt']}
            extra="Để trống với hợp đồng không xác định thời hạn."
            rules={[
              ({ getFieldValue }) => ({
                // Cùng luật với `ck_employees_contract_dates`. Chặn ở đây để người dùng thấy ngay
                // tại ô nhập; CSDL vẫn là nơi chốt — giao diện ⛔ không phải nơi giữ luật.
                validator(_, value?: Dayjs | null) {
                  const ky = getFieldValue('contractSignedAt') as Dayjs | null | undefined;
                  if (!value || !ky || !value.isBefore(ky, 'day')) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('Ngày hết hạn phải từ ngày ký trở đi'));
                },
              }),
            ]}
          >
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
      </Row>

      <Divider orientation="left">Trạng thái công tác</Divider>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            name="status"
            label="Trạng thái"
            rules={[{ required: true, message: 'Chọn trạng thái công tác' }]}
          >
            <Select options={TRANG_THAI_CONG_TAC_OPTIONS} />
          </Form.Item>
        </Col>
        {daNghi && (
          <>
            <Col span={8}>
              <Form.Item
                name="terminatedAt"
                label="Ngày nghỉ"
                // ⛔⛔ Bắt buộc HAI CHIỀU — `ck_employees_terminated_pairs`. Tổ hợp "đã nghỉ mà vẫn
                //    DANG_LAM" làm mọi phép đếm quân số sai mà ⛔ không màn hình nào báo. Chiều
                //    ngược lại do `dungPayload` lo: ô ẩn của AntD vẫn giữ giá trị cũ trong store.
                rules={[{ required: true, message: 'Nhập ngày nghỉ' }]}
              >
                <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="terminationReason" label="Lý do nghỉ" rules={[{ max: 500 }]}>
                <Input />
              </Form.Item>
            </Col>
          </>
        )}
      </Row>
      {daNghi && (
        <Alert
          type="warning"
          showIcon
          message="Hồ sơ chuyển sang trạng thái đã nghỉ"
          description="Hồ sơ vẫn nằm nguyên trong hệ thống và vẫn tra cứu được — chỉ thôi được tính vào quân số đang làm việc."
        />
      )}
    </Form>
  );
}

// =============================================================================
// Đổi qua lại giữa biểu mẫu và dây
// =============================================================================

/** `null` cho ô trống — ⛔ không `''`: backend phân biệt "chưa nhập" với "chuỗi rỗng" ở `rutGon`. */
function chu(giaTri: string | null | undefined): string | null {
  const rut = giaTri?.trim();
  return rut ? rut : null;
}

/**
 * ⛔⛔ `format('YYYY-MM-DD')` chứ **⛔ KHÔNG** `toISOString()`.
 *
 * Backend nhận `LocalDate`. `toISOString()` đổi sang UTC, và với người dùng ở UTC+7 thì một ngày
 * chọn lúc 00:00 sẽ **lùi một ngày**. Cùng bẫy đã ghi ở `ArticleEditorPage` cho `docIssuedDate`.
 */
function ngay(giaTri: Dayjs | null | undefined): string | null {
  return giaTri ? giaTri.format('YYYY-MM-DD') : null;
}

function dungGiaTriBanDau(d: EmployeeDetail | undefined): GiaTriBieuMau {
  return {
    code: d?.code ?? '',
    fullName: d?.fullName ?? '',
    dateOfBirth: d?.dateOfBirth ? dayjs(d.dateOfBirth) : null,
    gender: d?.gender ?? null,
    ethnicity: d?.ethnicity ?? '',
    hometown: d?.hometown ?? '',
    address: d?.address ?? '',
    phone: d?.phone ?? '',
    workEmail: d?.workEmail ?? '',
    personalEmail: d?.personalEmail ?? '',
    maritalStatus: d?.maritalStatus ?? null,
    emergencyContactName: d?.emergencyContactName ?? '',
    emergencyContactPhone: d?.emergencyContactPhone ?? '',
    orgUnitId: d?.orgUnitId ?? undefined,
    positionId: d?.positionId ?? undefined,
    jobTitle: d?.jobTitle ?? '',
    hiredAt: d?.hiredAt ? dayjs(d.hiredAt) : null,
    contractType: d?.contractType ?? null,
    contractSignedAt: d?.contractSignedAt ? dayjs(d.contractSignedAt) : null,
    contractExpiresAt: d?.contractExpiresAt ? dayjs(d.contractExpiresAt) : null,
    // Mặc định của CSDL là `THU_VIEC`; khai lại ở đây để ô ⛔ không bao giờ rỗng.
    status: d?.status ?? 'THU_VIEC',
    terminatedAt: d?.terminatedAt ? dayjs(d.terminatedAt) : null,
    terminationReason: d?.terminationReason ?? '',
  };
}

/**
 * Dựng payload **đủ 23 trường**.
 *
 * ⚠ Cặp `status` ↔ `terminatedAt` bị ép về `null` khi trạng thái ⛔ không phải "đã nghỉ": ô ẩn của
 * AntD **vẫn giữ giá trị cũ** trong form store, nên chọn NGHI_VIEC → nhập ngày → đổi lại DANG_LAM
 * sẽ gửi lên một tổ hợp mà `ck_employees_terminated_pairs` từ chối. Hai lớp, đúng khuôn `severity`
 * ở `MaintenanceFormModal`.
 */
function dungPayload(v: GiaTriBieuMau): EmployeeRequest {
  const daNghi = TRANG_THAI_DA_NGHI.includes(v.status);
  return {
    code: v.code.trim(),
    fullName: v.fullName.trim(),
    dateOfBirth: ngay(v.dateOfBirth),
    gender: v.gender ?? null,
    ethnicity: chu(v.ethnicity),
    hometown: chu(v.hometown),
    address: chu(v.address),
    phone: chu(v.phone),
    workEmail: chu(v.workEmail),
    personalEmail: chu(v.personalEmail),
    maritalStatus: v.maritalStatus ?? null,
    emergencyContactName: chu(v.emergencyContactName),
    emergencyContactPhone: chu(v.emergencyContactPhone),
    orgUnitId: v.orgUnitId ?? null,
    positionId: v.positionId ?? null,
    jobTitle: chu(v.jobTitle),
    hiredAt: ngay(v.hiredAt),
    contractType: v.contractType ?? null,
    contractSignedAt: ngay(v.contractSignedAt),
    contractExpiresAt: ngay(v.contractExpiresAt),
    status: v.status,
    terminatedAt: daNghi ? ngay(v.terminatedAt) : null,
    terminationReason: daNghi ? chu(v.terminationReason) : null,
  };
}

export default EmployeeFormModal;
