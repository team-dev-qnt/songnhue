import { type StatusColorKey } from 'design-tokens';

/**
 * Bộ từ vựng trạng thái — **một nơi duy nhất** dịch enum của backend sang nhãn + màu.
 *
 * ⛔ Cấm page tự đặt màu và nhãn. Cùng một trạng thái xuất hiện ở bảng danh sách, ô chi
 * tiết và (Phase 2) lớp GIS; ba chỗ tự dịch riêng là ba cách gọi khác nhau cho cùng một
 * thứ, mà năm màu trạng thái ở đây mang nghĩa nghiệp vụ chứ không phải thẩm mỹ.
 *
 * Tách khỏi `StatusBadge.tsx` vì đây là **dữ liệu**, không phải component — để lẫn thì
 * hot-reload của Vite mất khả năng giữ state mỗi lần sửa một nhãn.
 */
export interface StatusMeaning {
  label: string;
  color: StatusColorKey;
  /** Giải thích thêm khi nhãn ngắn dễ bị hiểu nhầm. */
  hint?: string;
}

export type StatusVocabulary = Record<string, StatusMeaning>;

/** Trạng thái tài khoản — `PENDING_ACTIVATION` là đã tạo nhưng chưa đăng nhập lần nào. */
export const USER_STATUS: StatusVocabulary = {
  ACTIVE: { label: 'Đang hoạt động', color: 'normal' },
  LOCKED: { label: 'Đã khóa', color: 'danger' },
  PENDING_ACTIVATION: {
    label: 'Chờ kích hoạt',
    color: 'warning',
    hint: 'Đã cấp mật khẩu tạm, người dùng chưa đăng nhập lần nào',
  },
};

export const BACKUP_STATUS: StatusVocabulary = {
  SUCCEEDED: { label: 'Thành công', color: 'normal' },
  RUNNING: { label: 'Đang chạy', color: 'warning' },
  FAILED: { label: 'Thất bại', color: 'danger' },
};

export const BACKUP_TRIGGER: StatusVocabulary = {
  SCHEDULED: { label: 'Theo lịch', color: 'normal' },
  MANUAL: { label: 'Thủ công', color: 'normal' },
  PRE_RESTORE: {
    label: 'Trước khôi phục',
    color: 'warning',
    hint: 'Bản chụp bắt buộc ngay trước khi ghi đè dữ liệu — đường lùi duy nhất',
  },
};

export const JOB_STATUS: StatusVocabulary = {
  PENDING: { label: 'Chờ chạy', color: 'unknown' },
  RUNNING: { label: 'Đang chạy', color: 'warning' },
  SUCCEEDED: { label: 'Xong', color: 'normal' },
  FAILED: { label: 'Thất bại', color: 'danger' },
};

export const HEALTH_STATUS: StatusVocabulary = {
  UP: { label: 'Bình thường', color: 'normal' },
  DOWN: { label: 'Hỏng', color: 'danger' },
  OUT_OF_SERVICE: { label: 'Ngừng phục vụ', color: 'warning' },
  UNKNOWN: { label: 'Không xác định', color: 'unknown' },
};

/**
 * Trạng thái vận hành công trình (MOD-02) — ⛔ **giá trị dẫn xuất**, không ai sửa tay được.
 *
 * Thứ tự ưu tiên khi backend tính: sự cố → bảo trì → cảnh báo ngưỡng → mã tình hình vận
 * hành → bình thường; vòng đời hồ sơ (`LifecycleState`) đứng trên tất cả.
 *
 * ⚠ Đây là **cùng một bảng** mà biểu đồ tròn "theo trạng thái" và marker trên bản đồ dùng
 * để lấy màu. Một màn hình tự chọn sắc đỏ khác là người trực đọc sai mức nghiêm trọng.
 */
export const CONSTRUCTION_STATUS: StatusVocabulary = {
  BINH_THUONG: { label: 'Bình thường', color: 'normal' },
  CANH_BAO: {
    label: 'Cảnh báo',
    color: 'warning',
    hint: 'Có cảnh báo ngưỡng thuỷ văn đang xảy ra (Phase 2)',
  },
  SU_CO: { label: 'Sự cố', color: 'danger', hint: 'Có bản ghi khắc phục sự cố đang mở' },
  BAO_TRI: { label: 'Đang bảo trì', color: 'warning' },
  NGUNG_MUA_VU: { label: 'Ngừng mùa vụ', color: 'unknown' },
  DA_THANH_LY: { label: 'Đã thanh lý', color: 'inactive' },
};

/**
 * Loại công trình.
 *
 * Màu ở đây **không mang nghĩa mức nghiêm trọng** — chúng chỉ để phân biệt cột trên biểu
 * đồ. Nên tất cả đều `normal`: dùng đỏ cho "đê điều" thì người đọc lướt qua sẽ hiểu là
 * đê đang có vấn đề.
 */
export const CONSTRUCTION_TYPE: StatusVocabulary = {
  TRAM_BOM: { label: 'Trạm bơm', color: 'normal' },
  CONG: { label: 'Cống', color: 'normal' },
  KENH_MUONG: { label: 'Kênh mương', color: 'normal' },
  DE_DIEU: { label: 'Đê điều', color: 'normal' },
  KHAC: { label: 'Khác', color: 'normal' },
};

/** Cấp quản lý — thông tin hành chính, ⛔ không quyết định phạm vi dữ liệu. */
export const MANAGEMENT_LEVEL: StatusVocabulary = {
  CONG_TY: { label: 'Công ty', color: 'normal' },
  XI_NGHIEP: { label: 'Xí nghiệp', color: 'normal' },
  CUM: { label: 'Cụm', color: 'normal' },
};

/**
 * Loại công việc trong lịch sử sửa chữa — CN-02.2.
 *
 * ⛔ `KHAC_PHUC_SU_CO` KHÔNG phải một entity riêng (quy tắc 15): sự cố là một dòng
 * `maintenance_logs` mang loại này. Không có mã `SC-`, không có bảng `incidents`.
 */
export const MAINTENANCE_TYPE: StatusVocabulary = {
  SUA_CHUA: { label: 'Sửa chữa', color: 'normal' },
  BAO_TRI_DINH_KY: { label: 'Bảo trì định kỳ', color: 'normal' },
  NANG_CAP: { label: 'Nâng cấp', color: 'normal' },
  THAY_THE_THIET_BI: { label: 'Thay thế thiết bị', color: 'normal' },
  KHAC_PHUC_SU_CO: { label: 'Khắc phục sự cố', color: 'danger' },
};

/** Trạng thái xử lý — khớp `MaintenanceState.TAT_CA` và ràng buộc CHECK của CSDL. */
export const MAINTENANCE_STATUS: StatusVocabulary = {
  MOI: { label: 'Mới', color: 'warning' },
  DANG_XU_LY: { label: 'Đang xử lý', color: 'warning' },
  // `normal` chứ không phải `success`: bảng màu chỉ có năm khoá mang nghĩa nghiệp vụ
  // (design-tokens `statusColors`), và `normal` là khoá dành cho "bình thường / đã duyệt /
  // hoạt động". Không thêm khoá thứ sáu cho riêng một bộ từ vựng — năm màu này còn dùng
  // chung với lớp GIS và dashboard điều hành.
  DA_XU_LY: { label: 'Đã xử lý', color: 'normal' },
};

/** Mức độ sự cố — chỉ có ở bản ghi loại "Khắc phục sự cố" (OPS-2003). */
export const INCIDENT_SEVERITY: StatusVocabulary = {
  NGHIEM_TRONG: { label: 'Nghiêm trọng', color: 'danger' },
  CAO: { label: 'Cao', color: 'danger' },
  TRUNG_BINH: { label: 'Trung bình', color: 'warning' },
  THAP: { label: 'Thấp', color: 'normal' },
};

/** Sắc thái một ô KPI trên dashboard — backend gửi xuống, FE chỉ dịch sang màu. */
export const KPI_TONE: StatusVocabulary = {
  NORMAL: { label: 'Bình thường', color: 'normal' },
  WARNING: { label: 'Cảnh báo', color: 'warning' },
  DANGER: { label: 'Nguy cấp', color: 'danger' },
  UNKNOWN: { label: 'Chưa xác định', color: 'unknown' },
};

export const SCAN_STATUS: StatusVocabulary = {
  PENDING: { label: 'Đang quét', color: 'warning' },
  CLEAN: { label: 'Đã quét sạch', color: 'normal' },
  INFECTED: { label: 'Phát hiện mã độc', color: 'danger' },
  SKIPPED: { label: 'Bỏ qua quét', color: 'unknown' },
};
