'use client';

/**
 * Trang 500 của khu vực công khai.
 *
 * ⛔ **Không hiển thị `error.message`.** Trên máy chủ, Next đã thay thông điệp lỗi thật
 * bằng một chuỗi rỗng kèm `digest` trước khi gửi xuống trình duyệt — chính vì lộ nó ra
 * là lộ chi tiết nội bộ cho người lạ. Đây là trang ai cũng vào được, khác hẳn trang 500
 * của admin-app (nơi hiện `traceId` cho cán bộ đọc lại cho quản trị viên).
 *
 * `digest` thì hiện được: nó là một mã băm, tra trong log máy chủ ra đúng lỗi.
 */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="mx-auto max-w-3xl animate-fade-in px-4 py-20 text-center">
      <p className="text-6xl font-black text-status-danger">500</p>
      <h1 className="mt-4 text-xl font-semibold text-surface-textBase">
        Trang đang gặp sự cố kỹ thuật
      </h1>
      <p className="mt-2 text-surface-textSecondary">
        Vui lòng thử lại sau ít phút. Nếu vẫn lỗi, liên hệ quản trị hệ thống của Công ty.
      </p>
      {error.digest && (
        <p className="mt-4 text-xs text-surface-textSecondary">Mã tra cứu: {error.digest}</p>
      )}
      <button
        type="button"
        onClick={reset}
        className="mt-6 rounded-lg bg-brand-primary px-6 py-2.5 font-medium text-white shadow-sm transition-all duration-200 ease-smooth hover:-translate-y-0.5 hover:bg-brand-primaryHover hover:shadow-md"
      >
        Tải lại
      </button>
    </div>
  );
}
