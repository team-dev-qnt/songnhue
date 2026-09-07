'use client';

import { useEffect, useTransition } from 'react';
import { useRouter } from 'next/navigation';

import { formatDateTime } from '@/lib/routes';

interface RealtimeFrameProps {
  /** Mốc thời gian của **số liệu**, không phải của lượt dựng trang. `null` = chưa có nguồn. */
  updatedAt: string | null;
  /** Chu kỳ tự làm mới, giây. `0` = tắt, chỉ còn nút bấm tay. Đọc từ `settings`. */
  refreshSeconds: number;
  /**
   * Nguồn dữ liệu đang gián đoạn hoặc chưa đấu nối. Khi `true`, khung hiện đúng câu CR-36
   * yêu cầu và **giữ nguyên bố cục** — không thu khối lại thành một dòng chữ.
   */
  unavailable?: boolean;
  /** Lý do cụ thể, hiện dưới câu chung. Nói thẳng vì sao chưa có, không nói "đang cập nhật". */
  unavailableReason?: string;
  children?: React.ReactNode;
}

/**
 * Khung chung cho mọi khối lấy dữ liệu từ nguồn bên ngoài — §7 của "YÊU CẦU CHỈNH SỬA
 * WEBSITE" 27/08/2026 (CR-35, CR-36, CR-37).
 *
 * <h2>Vì sao một khung dùng chung thay vì chép ba dòng vào từng khối</h2>
 *
 * §7 áp **cùng một bộ ba ràng buộc** cho mọi khối realtime: dòng "Cập nhật lúc", nút làm mới
 * tay, và trạng thái dự phòng khi mất kết nối. Chép chúng vào từng khối là ba nơi phải nhớ,
 * và nơi thứ tư — khối mà ai đó thêm sau — sẽ quên. Quy tắc 12: đặt bảo đảm ở chỗ *dữ liệu đi
 * qua*, đừng đặt ở *nơi gọi*.
 *
 * <h2>⛔ Khi không có dữ liệu, khối này KHÔNG hiện số nào</h2>
 *
 * Không có bộ số dự phòng, không có `0`, không có dấu `--` giả làm một phép đo. §10.54 là cái
 * giá đã trả cho hướng ngược lại: 5 trạm quan trắc có mực nước và một mức cảnh báo BĐ I trên
 * tên cống có thật đã lên staging, và không ai nhìn ra là đường dữ liệu đã chết hoàn toàn.
 *
 * <h2>Tự làm mới bằng `router.refresh()`</h2>
 *
 * Khối là Server Component, nên làm mới nghĩa là hỏi lại máy chủ Next — chính là
 * `router.refresh()`. Cách này giữ trạng thái cuộn trang và không tải lại bundle, khác hẳn
 * `location.reload()`. Backend vẫn chỉ bị hỏi theo chu kỳ `revalidate` của `apiGet`, nên nút
 * này không phải một đường để ai đó nện vào CSDL.
 */
export function RealtimeFrame({
  updatedAt,
  refreshSeconds,
  unavailable = false,
  unavailableReason,
  children,
}: RealtimeFrameProps) {
  const router = useRouter();
  const [dangLamMoi, batDau] = useTransition();

  useEffect(() => {
    if (refreshSeconds <= 0) {
      return;
    }
    const dinhKy = setInterval(() => router.refresh(), refreshSeconds * 1000);
    return () => clearInterval(dinhKy);
  }, [refreshSeconds, router]);

  // ⚠ Mốc hiển thị đến từ MÁY CHỦ, không phải `new Date()` phía máy khách: đồng hồ máy khách
  //   sai thì cả trang nói sai theo và không ai đối chiếu được. Đó cũng là lý do `getServerTime`
  //   gọi với `revalidate: 0` — một mốc "cập nhật lúc" nằm trong bộ đệm 5 phút thì nó nói dối
  //   đúng 5 phút, mà cả lý do tồn tại của dòng này là để người xem biết số liệu mới đến bao giờ.
  const mocThoiGian = formatDateTime(updatedAt);

  return (
    <div className="flex flex-col gap-3">
      {unavailable ? (
        <div className="rounded-lg border border-dashed border-amber-300 bg-amber-50/70 px-4 py-6 text-center">
          <p className="text-sm font-semibold text-amber-900">Dữ liệu tạm thời chưa khả dụng</p>
          {unavailableReason ? (
            <p className="mt-1.5 text-xs text-amber-800/90">{unavailableReason}</p>
          ) : null}
          <p className="mt-2 text-xs text-amber-800/80">
            {mocThoiGian
              ? `Thời điểm cập nhật gần nhất: ${mocThoiGian}`
              : 'Chưa có lần cập nhật nào để đối chiếu.'}
          </p>
        </div>
      ) : (
        children
      )}

      {/* ───── Dải chân khối: mốc cập nhật + nút làm mới (CR-35, CR-37) ───── */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-surface-border pt-2.5 text-xs text-surface-textSecondary">
        <span>
          Cập nhật lúc:{' '}
          <span className="font-semibold text-surface-textBase">{mocThoiGian || 'chưa rõ'}</span>
        </span>
        <button
          type="button"
          onClick={() => batDau(() => router.refresh())}
          disabled={dangLamMoi}
          className="inline-flex items-center gap-1.5 rounded-md border border-surface-border bg-white px-2.5 py-1 font-semibold text-brand-primary transition-colors hover:border-brand-primary disabled:cursor-not-allowed disabled:opacity-50"
        >
          <svg
            className={`h-3.5 w-3.5 ${dangLamMoi ? 'animate-spin' : ''}`}
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
            />
          </svg>
          <span>{dangLamMoi ? 'Đang làm mới…' : 'Làm mới'}</span>
        </button>
      </div>
    </div>
  );
}
