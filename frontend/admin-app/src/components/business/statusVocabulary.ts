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

export const SCAN_STATUS: StatusVocabulary = {
  PENDING: { label: 'Đang quét', color: 'warning' },
  CLEAN: { label: 'Đã quét sạch', color: 'normal' },
  INFECTED: { label: 'Phát hiện mã độc', color: 'danger' },
  SKIPPED: { label: 'Bỏ qua quét', color: 'unknown' },
};
