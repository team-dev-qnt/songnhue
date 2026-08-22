import dayjs, { type Dayjs } from 'dayjs';
import timezone from 'dayjs/plugin/timezone';
import utc from 'dayjs/plugin/utc';

dayjs.extend(utc);
dayjs.extend(timezone);

/**
 * Hiển thị ngày giờ và số theo quy ước Việt Nam (conventions.md §3).
 *
 * <h3>Vì sao ép cứng múi giờ thay vì dùng giờ máy</h3>
 *
 * Backend lưu `timestamptz` UTC (CLAUDE.md quy tắc 1). Nếu FE đổi sang **giờ máy người
 * xem**, cùng một mốc sẽ hiện khác nhau giữa hai máy đặt sai múi giờ — và đây là hệ điều
 * hành công trình thuỷ lợi: "cống mở lúc 14:30" phải là một mốc duy nhất, không phụ thuộc
 * ai đang nhìn. Máy trạm trong đơn vị hay bị lệch múi giờ sau khi cài lại Windows, nên
 * "để hệ điều hành lo" là đúng về lý thuyết mà sai trên thực địa.
 */
export const APP_TIMEZONE = 'Asia/Ho_Chi_Minh';

const DATE_TIME_FORMAT = 'DD/MM/YYYY HH:mm';
const DATE_FORMAT = 'DD/MM/YYYY';
const DATE_TIME_SECONDS_FORMAT = 'DD/MM/YYYY HH:mm:ss';

/** Ô trống dùng chung — mọi bảng hiện cùng một ký tự cho "không có dữ liệu". */
export const EMPTY_MARK = '—';

type DateLike = string | number | Date | Dayjs | null | undefined;

function toZoned(value: DateLike): Dayjs | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.tz(APP_TIMEZONE) : null;
}

/** `dd/MM/yyyy HH:mm` theo UTC+7. */
export function formatDateTime(value: DateLike): string {
  return toZoned(value)?.format(DATE_TIME_FORMAT) ?? EMPTY_MARK;
}

/** Có giây — dùng cho nhật ký kiểm toán và sự kiện bảo mật, nơi thứ tự trong phút có nghĩa. */
export function formatDateTimeWithSeconds(value: DateLike): string {
  return toZoned(value)?.format(DATE_TIME_SECONDS_FORMAT) ?? EMPTY_MARK;
}

export function formatDate(value: DateLike): string {
  return toZoned(value)?.format(DATE_FORMAT) ?? EMPTY_MARK;
}

/** Đổi giá trị người dùng chọn trên lịch (giờ địa phương của trình duyệt) sang ISO UTC để gửi API. */
export function toApiInstant(value: Dayjs | null | undefined): string | undefined {
  return value ? value.toISOString() : undefined;
}

// =============================================================================
// Số
// =============================================================================

const vietnameseNumber = new Intl.NumberFormat('vi-VN');

/**
 * Dấu chấm ngăn nghìn, dấu phẩy thập phân — kiểu Việt Nam.
 *
 * ⚠ Chỉ để **hiển thị**. Mọi phép tính nằm ở backend (CLAUDE.md quy tắc 3): `number` của
 * JavaScript là dấu phẩy động nhị phân, đúng thứ mà quy tắc "cấm float/double cho số đo
 * và tiền" cấm ở backend. Cộng vài giá trị đo trên FE là tự tạo ra sai số mà không ai
 * đối chiếu lại được.
 */
export function formatNumber(
  value: number | string | null | undefined,
  fractionDigits = 0,
): string {
  if (value === null || value === undefined || value === '') {
    return EMPTY_MARK;
  }
  const numeric = typeof value === 'string' ? Number(value) : value;
  if (!Number.isFinite(numeric)) {
    return EMPTY_MARK;
  }
  return new Intl.NumberFormat('vi-VN', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(numeric);
}

export function formatInteger(value: number | null | undefined): string {
  return value === null || value === undefined ? EMPTY_MARK : vietnameseNumber.format(value);
}

/** Dung lượng tệp — cơ số 1024, đơn vị theo cách gọi quen thuộc. */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) {
    return EMPTY_MARK;
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${formatNumber(value, unit === 0 ? 0 : 1)} ${units[unit]}`;
}

/** Khoảng thời gian ngắn (mili giây) — dùng cho thời lượng một lượt sao lưu, một job. */
export function formatDuration(milliseconds: number | null | undefined): string {
  if (milliseconds === null || milliseconds === undefined) {
    return EMPTY_MARK;
  }
  const totalSeconds = Math.round(milliseconds / 1000);
  if (totalSeconds < 60) {
    return `${totalSeconds} giây`;
  }
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes < 60) {
    return seconds === 0 ? `${minutes} phút` : `${minutes} phút ${seconds} giây`;
  }
  const hours = Math.floor(minutes / 60);
  return `${hours} giờ ${minutes % 60} phút`;
}

/**
 * "Cách đây N …" từ số giây.
 *
 * Dùng cho tuổi bản sao lưu gần nhất: "cách đây 31 giờ" nói ngay là có vấn đề, còn
 * "15/08/2026 02:00" thì người đọc phải tự trừ.
 */
export function formatAge(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined) {
    return 'chưa từng';
  }
  if (seconds < 60) {
    return 'vừa xong';
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} phút trước`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 48) {
    return `${hours} giờ trước`;
  }
  return `${Math.floor(hours / 24)} ngày trước`;
}

/** 
 * Đổi số tiền (VNĐ) sang định dạng đọc được (tỷ/triệu VNĐ).
 */
export function formatInvestment(val: number | null | undefined): string | null {
  if (val === null || val === undefined) return null;
  if (val >= 1_000_000_000) {
    return `~ ${(val / 1_000_000_000).toLocaleString('vi-VN', { maximumFractionDigits: 2 })} tỷ VNĐ`;
  }
  if (val >= 1_000_000) {
    return `~ ${(val / 1_000_000).toLocaleString('vi-VN', { maximumFractionDigits: 2 })} triệu VNĐ`;
  }
  return `${val.toLocaleString('vi-VN')} VNĐ`;
}
