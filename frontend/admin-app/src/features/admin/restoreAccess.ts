import { type BackupStatusView } from '@/shared/api-types';

/**
 * Có hiện chức năng khôi phục dữ liệu (M5.11) hay không.
 *
 * Tách thành hàm thuần, đặt riêng một tệp, để bài kiểm nắm được — đây là điều kiện
 * nghiệm thu của WS-8 ("restore UI **không hiện** với non-Super-Admin") và là loại điều
 * kiện dễ bị sửa hỏng lúc dọn dẹp mã mà không ai nhận ra.
 *
 * Hai vế, và cả hai đều cần thiết:
 *
 * - **Vai trò Super Admin**, kiểm tường minh chứ không chỉ dựa vào quyền
 *   `adm:backup:restore` — quyền thì gán được cho vai trò khác bằng vài cú nhấp trên màn
 *   hình phân quyền, mà chức năng ghi đè toàn bộ CSDL không được phép nới ra kiểu đó.
 * - **Môi trường có bật khôi phục** (`DB_RESTORE_PASSWORD` không rỗng). Để trống là lựa
 *   chọn hợp lệ, không phải thiếu sót; khi đó đường khôi phục chính thức là runbook.
 *
 * ⛔ Vẫn là tầng 1 (§4.2). Backend kiểm lại đủ cả ba lớp, cộng mã 2FA nhập tươi.
 */
export function isRestoreVisible(
  isSuperAdmin: boolean,
  status: Pick<BackupStatusView, 'restoreAvailable'> | undefined,
): boolean {
  return isSuperAdmin && status?.restoreAvailable === true;
}
