/**
 * Từ vựng + hình dạng dây của MOD-04 (HRM) — **một nơi duy nhất**.
 *
 * <h2>Vì sao cả kiểu lẫn nhãn nằm chung một tệp</h2>
 *
 * `hydroVocabulary.ts` chỉ giữ nhãn và nhập kiểu từ `shared/api-types.ts`. Ở đây thì không, và lý
 * do là một ràng buộc chứ không phải sở thích: bốn union type dưới đây **là** từ vựng — mỗi hằng
 * của chúng có đúng một câu tiếng Việt, và hai thứ ấy phải đổi cùng lúc. Tách kiểu sang
 * `shared/api-types.ts` rồi để nhãn ở đây là dựng đúng cặp *"con người phải nhớ hai nơi"* mà luật
 * 14 nói tới, và `Record<Enum, string>` **không** bắt được lỗi ấy theo chiều nguy hiểm: thêm một
 * hằng vào union thì TypeScript đỏ, nhưng đổi **ý nghĩa** một hằng thì không.
 *
 * ⛔ Và tệp này ⛔ KHÔNG được đặt tên `api.ts`: `apiKhongMoCoi.test.ts` đếm mọi tệp `api.ts` của
 * `admin-app` và đỏ khi số ấy lệch danh sách `CLIENT` nó khai. Màn hình HR gọi thẳng `api.get/post/
 * put/delete` như 47 tệp khác của kho — ⛔ không dựng client tập trung thứ hai.
 *
 * <h2>⛔⛔ Bảng CBNV RỖNG là câu trả lời ĐÚNG hôm nay</h2>
 *
 * `G6-a` (danh sách cán bộ nhân viên) còn mở. CLAUDE.md cấm seed dữ liệu *"cho đẹp demo"*, và
 * §10.54 đã trả giá cho đúng chuyện đó: 19 bài viết bịa làm một trang rỗng trông đầy. Nên trạng
 * thái rỗng của màn hình phải **nói ra lý do**, ⛔ không được che bằng một dòng mẫu nào.
 */

import { type StatusMeaning } from '@/components/business/statusVocabulary';

// =============================================================================
// Bốn union type — bản sao của enum backend (`com.songnhue.hr.domain.**`)
// =============================================================================

/** `Gender.java`. */
export type Gender = 'NAM' | 'NU' | 'KHAC';

/** `MaritalStatus.java`. */
export type MaritalStatus = 'DOC_THAN' | 'DA_KET_HON' | 'KHAC';

/**
 * `ContractType.java` — BLLĐ 2019 Điều 20 chỉ có **hai** loại hợp đồng lao động.
 *
 * `THU_VIEC` là hợp đồng thử việc (Điều 24), `KHAC` giữ chỗ cho hợp đồng dịch vụ/khoán việc.
 */
export type ContractType = 'KHONG_XAC_DINH_THOI_HAN' | 'XAC_DINH_THOI_HAN' | 'THU_VIEC' | 'KHAC';

/** `EmploymentStatus.java`. */
export type EmploymentStatus =
  'DANG_LAM' | 'THU_VIEC' | 'THAI_SAN' | 'KHONG_LUONG' | 'NGHI_VIEC' | 'NGHI_HUU';

// =============================================================================
// Nhãn tiếng Việt
// =============================================================================

/**
 * Học vấn cao nhất — CN-04.3.
 *
 * ⛔ Đây là **Khung trình độ quốc gia** (QĐ 1982/QĐ-TTg 2016) + ba bậc phổ thông, ⛔ không phải một
 * danh mục Công ty tự đặt — nên nó là enum ba nơi chứ ⛔ không phải bảng có CRUD (quy tắc 16 nói về
 * danh mục **do khách vận hành**). Thứ tự khai ở đây đi từ **cao xuống thấp** và trùng
 * `EducationLevel.bac()` phía backend, nên ô chọn hiện đúng thứ tự người dùng mong đợi.
 */
export type EducationLevel =
  | 'TIEN_SI'
  | 'THAC_SI'
  | 'DAI_HOC'
  | 'CAO_DANG'
  | 'TRUNG_CAP'
  | 'SO_CAP'
  | 'THPT'
  | 'THCS'
  | 'KHAC';

/** Loại mục lý lịch & chuyên môn — CN-04.3. */
export type QualificationKind = 'BANG_CAP' | 'CHUNG_CHI' | 'NGOAI_NGU' | 'TIN_HOC' | 'PHAN_MEM';

/**
 * Mười loại sự kiện timeline — CN-04.4, nguyên văn đặc tả.
 *
 * ⛔ `BO_NHIEM_MIEN_NHIEM` và `NGHI_VIEC_HUU` mỗi cái là **một** giá trị, đúng như đặc tả liệt kê.
 * Tách ra "cho rõ" là làm lệch con số 10 mà bộ lọc và báo cáo đang dựa vào.
 */
export type EmployeeEventType =
  | 'TUYEN_DUNG'
  | 'HOP_DONG'
  | 'DIEU_DONG'
  | 'BO_NHIEM_MIEN_NHIEM'
  | 'NANG_LUONG'
  | 'KHEN_THUONG'
  | 'KY_LUAT'
  | 'DAO_TAO'
  | 'NGHI_DAI_HAN'
  | 'NGHI_VIEC_HUU';

/** Bảy thư mục cố định của hồ sơ tài liệu — CN-04.5. */
export type HoSoThuMuc =
  'GIAY_TO_TUY_THAN' | 'BANG_CAP' | 'HOP_DONG' | 'QUYET_DINH' | 'ANH' | 'HO_SO_Y_TE' | 'KHAC';

export const GIOI_TINH: Record<Gender, string> = {
  NAM: 'Nam',
  NU: 'Nữ',
  KHAC: 'Khác',
};

export const TINH_TRANG_HON_NHAN: Record<MaritalStatus, string> = {
  DOC_THAN: 'Độc thân',
  DA_KET_HON: 'Đã kết hôn',
  KHAC: 'Khác',
};

export const LOAI_HOP_DONG: Record<ContractType, string> = {
  KHONG_XAC_DINH_THOI_HAN: 'Không xác định thời hạn',
  XAC_DINH_THOI_HAN: 'Xác định thời hạn',
  THU_VIEC: 'Thử việc',
  KHAC: 'Khác',
};

/**
 * Sáu trạng thái công tác — dùng lại bộ máy `StatusBadge` của kho.
 *
 * ⛔ Màu là **khoá token** (`design-tokens`), ⛔ không phải mã hex: `noHardcodedColors.test.ts` giữ
 * trần 46 mã cho toàn `admin-app`, và `statusVocabulary.ts` đã khai thẳng *"cấm page tự đặt màu và
 * nhãn"*.
 *
 * ⚠ `THAI_SAN` và `KHONG_LUONG` cố ý **⛔ không** màu cảnh báo: chúng là quyền nghỉ hợp pháp, ⛔
 * không phải sự cố. Tô vàng cho một trạng thái bình thường là dạy người dùng bỏ qua màu vàng —
 * §10.42.
 */
export const TRANG_THAI_CONG_TAC: Record<EmploymentStatus, StatusMeaning> = {
  DANG_LAM: { label: 'Đang làm việc', color: 'normal' },
  THU_VIEC: {
    label: 'Thử việc',
    color: 'warning',
    hint: 'Hết thời gian thử việc phải ký hợp đồng chính thức (BLLĐ 2019 Điều 24)',
  },
  THAI_SAN: { label: 'Nghỉ thai sản', color: 'unknown' },
  KHONG_LUONG: { label: 'Nghỉ không lương', color: 'unknown' },
  NGHI_VIEC: { label: 'Đã nghỉ việc', color: 'inactive' },
  NGHI_HUU: { label: 'Đã nghỉ hưu', color: 'inactive' },
};

/**
 * ⛔⛔ Hai trạng thái **BẮT BUỘC** đi kèm ngày nghỉ, và hai chiều.
 *
 * `ck_employees_terminated_pairs` ép `(terminated_at IS NOT NULL) = (status IN (…))` ở CSDL, và
 * `EmployeeService.kiemNgayNghi` ném **400** (`SYS-0003`) kèm tên trường `terminatedAt`. Biểu mẫu dựng luật hiển
 * thị **từ hằng số này**, ⛔ không gõ lại hai tên hằng — gõ lại là dựng bản sao thứ ba của cùng
 * một luật rồi một hôm ba bản nói khác nhau (luật 14).
 */
export const TRANG_THAI_DA_NGHI: readonly EmploymentStatus[] = ['NGHI_VIEC', 'NGHI_HUU'];

export const GIOI_TINH_OPTIONS = (Object.keys(GIOI_TINH) as Gender[]).map((value) => ({
  value,
  label: GIOI_TINH[value],
}));

export const TINH_TRANG_HON_NHAN_OPTIONS = (
  Object.keys(TINH_TRANG_HON_NHAN) as MaritalStatus[]
).map((value) => ({ value, label: TINH_TRANG_HON_NHAN[value] }));

export const LOAI_HOP_DONG_OPTIONS = (Object.keys(LOAI_HOP_DONG) as ContractType[]).map(
  (value) => ({ value, label: LOAI_HOP_DONG[value] }),
);

export const TRANG_THAI_CONG_TAC_OPTIONS = (
  Object.keys(TRANG_THAI_CONG_TAC) as EmploymentStatus[]
).map((value) => ({ value, label: TRANG_THAI_CONG_TAC[value].label }));

/**
 * Ngày sinh hợp lệ — `ck_employees_dob_range`, tương ứng 18 ≤ tuổi ≤ 70 (CN-04.2).
 *
 * ⚠ Mốc **tuyệt đối**, ⛔ không tính từ `now()`: CHECK ở CSDL phải IMMUTABLE nên nó dùng đúng hai
 * mốc này, và biểu mẫu phải chặn đúng khoảng ấy. Lệch một ngày là người dùng nhận một lỗi ràng
 * buộc CSDL trần, ⛔ không chỉ được ô nào sai.
 */
export const HOC_VAN: Record<EducationLevel, string> = {
  TIEN_SI: 'Tiến sĩ',
  THAC_SI: 'Thạc sĩ',
  DAI_HOC: 'Đại học',
  CAO_DANG: 'Cao đẳng',
  TRUNG_CAP: 'Trung cấp',
  SO_CAP: 'Sơ cấp',
  THPT: 'THPT',
  THCS: 'THCS',
  KHAC: 'Khác',
};

export const LOAI_LY_LICH: Record<QualificationKind, string> = {
  BANG_CAP: 'Bằng cấp',
  CHUNG_CHI: 'Chứng chỉ',
  NGOAI_NGU: 'Ngoại ngữ',
  TIN_HOC: 'Tin học',
  PHAN_MEM: 'Phần mềm chuyên dụng',
};

export const LOAI_SU_KIEN: Record<EmployeeEventType, string> = {
  TUYEN_DUNG: 'Tuyển dụng',
  HOP_DONG: 'Ký / gia hạn hợp đồng',
  DIEU_DONG: 'Điều động',
  BO_NHIEM_MIEN_NHIEM: 'Bổ nhiệm / Miễn nhiệm',
  NANG_LUONG: 'Nâng lương',
  KHEN_THUONG: 'Khen thưởng',
  KY_LUAT: 'Kỷ luật',
  DAO_TAO: 'Đào tạo',
  NGHI_DAI_HAN: 'Nghỉ dài hạn',
  NGHI_VIEC_HUU: 'Nghỉ việc / Nghỉ hưu',
};

export const THU_MUC_HO_SO: Record<HoSoThuMuc, string> = {
  GIAY_TO_TUY_THAN: 'Giấy tờ tuỳ thân',
  BANG_CAP: 'Bằng cấp',
  HOP_DONG: 'Hợp đồng lao động',
  QUYET_DINH: 'Quyết định nhân sự',
  ANH: 'Ảnh',
  HO_SO_Y_TE: 'Hồ sơ y tế',
  KHAC: 'Khác',
};

export const HOC_VAN_OPTIONS = (Object.keys(HOC_VAN) as EducationLevel[]).map((value) => ({
  value,
  label: HOC_VAN[value],
}));

export const LOAI_LY_LICH_OPTIONS = (Object.keys(LOAI_LY_LICH) as QualificationKind[]).map(
  (value) => ({ value, label: LOAI_LY_LICH[value] }),
);

export const LOAI_SU_KIEN_OPTIONS = (Object.keys(LOAI_SU_KIEN) as EmployeeEventType[]).map(
  (value) => ({ value, label: LOAI_SU_KIEN[value] }),
);

export const THU_MUC_HO_SO_OPTIONS = (Object.keys(THU_MUC_HO_SO) as HoSoThuMuc[]).map((value) => ({
  value,
  label: THU_MUC_HO_SO[value],
}));

export const NGAY_SINH_TU = '1930-01-01';
export const NGAY_SINH_DEN = '2015-12-31';

/**
 * Trường được phép sắp xếp — bản sao của `EmployeeService.SAP_XEP_CHO_PHEP`.
 *
 * ⛔⛔ `PageUtils` ném **400** (`SYS-0003`) với mọi trường ngoài tập này, và một 400 ở lượt tải ĐẦU TIÊN trông
 * **y hệt** một bảng vốn rỗng — mà bảng CBNV hôm nay *đúng là* rỗng (G6-a). Hai trạng thái ⛔
 * không phân biệt được là đúng hình dạng luật 9. Vì thế danh sách này là nguồn của ô "Sắp xếp",
 * ⛔ không có chuỗi sort nào gõ tay ở màn hình.
 */
export const SAP_XEP_CBNV = [
  { value: 'updatedAt,desc', label: 'Mới cập nhật trước' },
  { value: 'code,asc', label: 'Mã cán bộ tăng dần' },
  { value: 'fullName,asc', label: 'Họ tên A → Z' },
  { value: 'hiredAt,desc', label: 'Vào làm gần nhất' },
  { value: 'contractExpiresAt,asc', label: 'Hợp đồng sắp hết hạn trước' },
  { value: 'status,asc', label: 'Trạng thái công tác' },
  { value: 'createdAt,desc', label: 'Mới tạo trước' },
] as const;

/** Sort mặc định — phải nằm trong {@link SAP_XEP_CBNV}, xem ghi chú ở đó. */
export const SAP_XEP_MAC_DINH = SAP_XEP_CBNV[0].value;

// =============================================================================
// Hình dạng dây — `com.songnhue.hr.api.HrDtos`
// =============================================================================

/** `HrDtos.PositionView`. */
export interface PositionView {
  publicId: string;
  code: string;
  name: string;
  positionGroup: string | null;
  description: string | null;
  sortOrder: number | null;
  active: boolean | null;
}

/** `HrDtos.PositionRequest`. */
export interface PositionRequest {
  code: string;
  name: string;
  positionGroup?: string | null;
  description?: string | null;
  sortOrder?: number | null;
  active?: boolean | null;
}

/** `HrDtos.EmployeeRow` — một dòng danh sách. ⛔ KHÔNG trường 🔒. */
export interface EmployeeRow {
  publicId: string;
  code: string;
  fullName: string;
  orgUnitName: string | null;
  positionName: string | null;
  jobTitle: string | null;
  status: EmploymentStatus;
  contractExpiresAt: string | null;
  /** ISO-8601 UTC — hiển thị qua `formatDateTime`. */
  updatedAt: string | null;
}

/**
 * `HrDtos.EmployeeSensitiveStatus` — **ô nào đã có dữ liệu**, ⛔ không kèm giá trị nào.
 *
 * ⭐ Nó có mặt để người dùng phân biệt *"chưa nhập"* với *"⛔ không được xem"*. Hai trạng thái ấy
 * mà trông giống nhau thì sẽ có người nhập đè lên dữ liệu đang có — T50.1 là một sự cố đúng hình
 * dạng ấy.
 */
export interface EmployeeSensitiveStatus {
  cccdDaCo: boolean;
  luongDaCo: boolean;
  taiKhoanDaCo: boolean;
  mstDaCo: boolean;
  bhxhDaCo: boolean;
}

/** `HrDtos.EmployeeDetail` — hồ sơ đầy đủ. ⛔ KHÔNG trường 🔒, chỉ có cờ {@link sensitive}. */
export interface EmployeeDetail {
  publicId: string;
  code: string;
  fullName: string;
  dateOfBirth: string | null;
  gender: Gender | null;
  educationLevel: EducationLevel | null;
  ethnicity: string | null;
  hometown: string | null;
  address: string | null;
  phone: string | null;
  workEmail: string | null;
  personalEmail: string | null;
  maritalStatus: MaritalStatus | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  /** `publicId` của đơn vị — cùng khoá mà `OrgUnitTreeSelect` phát ra. */
  orgUnitId: string | null;
  orgUnitName: string | null;
  /** `publicId` của chức vụ. */
  positionId: string | null;
  positionName: string | null;
  jobTitle: string | null;
  hiredAt: string | null;
  contractType: ContractType | null;
  contractSignedAt: string | null;
  contractExpiresAt: string | null;
  status: EmploymentStatus;
  terminatedAt: string | null;
  terminationReason: string | null;
  sensitive: EmployeeSensitiveStatus;
}

/**
 * `HrDtos.EmployeeRequest` — **thay toàn phần**.
 *
 * ⛔⛔ Mọi trường vắng trong thân JSON được ghi thành `null`. Đó là hành vi ĐÚNG cho một biểu mẫu
 * (người dùng xoá ô là muốn xoá giá trị) và là hành vi **tai hại** cho một payload dựng thiếu:
 * ngày 09/09 một `PUT` thiếu trường đã xoá trắng tuyến sông/lý trình của 19 điểm đo và vô hình
 * suốt hai tuần (§11.19). ⇒ Kiểu này khai **đủ 23 trường và ⛔ không trường nào optional**, để
 * `tsc` đỏ khi nơi gọi dựng thiếu. Đó là bảo đảm rẻ nhất chống lại đúng hình dạng ấy — một lời
 * dặn thì ⛔ không phải một cổng kiểm.
 *
 * ⚠ `code` vẫn có mặt ở bản **sửa** dù mã NV ⛔ không đổi được: nó là `@NotBlank` ở backend nên
 * thiếu nó là **400** (`SYS-0003`). `EmployeeService.update` cố ý bỏ qua giá trị gửi lên.
 */
export interface EmployeeRequest {
  code: string;
  fullName: string;
  dateOfBirth: string | null;
  gender: Gender | null;
  educationLevel: EducationLevel | null;
  ethnicity: string | null;
  hometown: string | null;
  address: string | null;
  phone: string | null;
  workEmail: string | null;
  personalEmail: string | null;
  maritalStatus: MaritalStatus | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  orgUnitId: string | null;
  positionId: string | null;
  jobTitle: string | null;
  hiredAt: string | null;
  contractType: ContractType | null;
  contractSignedAt: string | null;
  contractExpiresAt: string | null;
  status: EmploymentStatus;
  terminatedAt: string | null;
  terminationReason: string | null;
}

/**
 * `HrDtos.SensitiveView` / `SensitiveRequest` — **cùng một hình dạng**, cố ý.
 *
 * ⛔⛔ Mọi trường là **chuỗi**, kể cả lương và ngày cấp CCCD. Đó ⛔ không phải sự cẩu thả của
 * backend: cột ở CSDL là bản mã AES-256-GCM (`TEXT`), nên thứ đi qua dây luôn là chuỗi thô người
 * dùng gõ. Hệ quả cho biểu mẫu — đã ghi ở `SensitiveModal`: ⛔ không `InputNumber`, ⛔ không
 * `DatePicker` cho hai ô ấy.
 *
 * ⚠ `PUT` là **thay toàn phần**: `EmployeeSensitiveService.luu` ghi cả 8 cột mỗi lượt. Hộp thoại
 * vì thế phải **đọc trước rồi mới cho lưu** — mở ra và bấm Lưu ngay là xoá trắng cả 8 ô.
 */
export interface SensitiveFields {
  nationalId: string | null;
  nationalIdIssuedOn: string | null;
  nationalIdIssuedPlace: string | null;
  baseSalary: string | null;
  salaryCoefficient: string | null;
  bankAccount: string | null;
  taxCode: string | null;
  socialInsuranceNo: string | null;
}

// ============================================================================
// WS-53 — CN-04.3 · CN-04.4 · CN-04.5
// ============================================================================

/** Một mục lý lịch & chuyên môn — CN-04.3. */
export interface LyLichView {
  publicId: string;
  kind: QualificationKind;
  name: string;
  grade: string | null;
  major: string | null;
  institution: string | null;
  certificateNo: string | null;
  issuedOn: string | null;
  /** ⛔ `null` = KHÔNG hết hiệu lực (bằng đại học) — ⛔ đừng điền một ngày xa để né null. */
  expiresOn: string | null;
  note: string | null;
  updatedAt: string;
}

export interface LyLichRequest {
  kind: QualificationKind;
  name: string;
  grade: string | null;
  major: string | null;
  institution: string | null;
  certificateNo: string | null;
  issuedOn: string | null;
  expiresOn: string | null;
  note: string | null;
}

/** Một sự kiện trên timeline công tác — CN-04.4. */
export interface SuKienView {
  publicId: string;
  eventType: EmployeeEventType;
  /** Ngày HIỆU LỰC — trục timeline. ⛔ Khác `decisionDate` (ngày ký). */
  effectiveOn: string;
  decisionNo: string | null;
  decisionDate: string | null;
  title: string;
  detail: string | null;
  updatedAt: string;
}

export interface SuKienRequest {
  eventType: EmployeeEventType;
  effectiveOn: string;
  decisionNo: string | null;
  decisionDate: string | null;
  title: string;
  detail: string | null;
}

/** Một tệp trong hồ sơ tài liệu — CN-04.5. */
export interface TaiLieuView {
  publicId: string;
  /** ⛔ `null` khi `purpose` trong CSDL ⛔ không giải được (bản khôi phục cũ) — vẫn phải hiện. */
  thuMuc: HoSoThuMuc | null;
  tenGoc: string;
  kieuNoiDung: string;
  soByte: number;
  /** Tải lại cùng thư mục ⇒ phiên bản kế tiếp, bản cũ **giữ nguyên**. */
  phienBan: number;
  /** `false` khi tệp chưa quét virus xong hoặc đã bị cách ly. */
  taiDuoc: boolean;
  hieuLucTu: string | null;
  hetHan: string | null;
  taiLuc: string;
}

/**
 * Tình trạng hoàn thiện hồ sơ — CN-04.5.
 *
 * ⛔⛔ Đọc `daCauHinh` chứ ⛔ **đừng** viết `phanTram === null`: Jackson của backend **bỏ hẳn**
 * trường `null` khỏi thân JSON, nên ô ấy tới đây dưới dạng `undefined`. Cờ boolean ⛔ không thể bị
 * bỏ, nên nó là thứ duy nhất phân biệt được *"chưa cấu hình"* với *"0%"* một cách bền.
 */
export interface TinhTrangHoSoView {
  daCauHinh: boolean;
  phanTram?: number | null;
  batBuoc: HoSoThuMuc[];
  conThieu: HoSoThuMuc[];
  soTepTheoThuMuc: Record<HoSoThuMuc, number>;
  dungLuongDaDungByte: number;
}

/** @param soNgayCon **âm** nghĩa là ĐÃ hết hạn — tô đỏ. ⛔ Đừng kẹp về 0. */
export interface MucCanhBao {
  hoSoPublicId: string;
  maCanBo: string;
  hoTen: string;
  moTa: string;
  hetHan: string;
  soNgayCon: number;
}

export interface CanhBaoHetHanView {
  /** ⛔ Đọc từ API chứ ⛔ đừng ghi cứng lại con số ở giao diện — hai nơi một sự thật sẽ lệch. */
  nguongNgayHopDong: number;
  nguongNgayChungChi: number;
  hopDong: MucCanhBao[];
  chungChi: MucCanhBao[];
}
