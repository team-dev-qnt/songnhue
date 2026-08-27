import Link from 'next/link';

import { ROUTES } from '@/lib/routes';
import { RealtimeFrame } from '../realtime/RealtimeFrame';

interface WaterLevelBlockProps {
  hotline?: string;
  refreshSeconds: number;
  /** Mốc của số liệu. `null` khi chưa có nguồn — xem ghi chú về việc KHÔNG lấy giờ máy khách. */
  updatedAt: string | null;
}

/**
 * Khối **MỰC NƯỚC, LƯỢNG MƯA** trên trang chủ — CR-13, CR-14, CR-33, CR-35, CR-36.
 *
 * <h2>⛔ Khối này hiện KHÔNG có nguồn dữ liệu, và nó nói thẳng điều đó</h2>
 *
 * Trả lời <b>OI-01</b>: API mực nước <b>chưa</b> sẵn sàng. Module MOD-03 (Quản lý dữ liệu thủy
 * văn) chưa được dựng — thư mục `hydro/` của backend mới chỉ có khai báo gói. Nguồn
 * `songnhue.bhh40.net` đã khảo sát xong (2 phút/lần, 19 điểm đo, <b>không có API lượng mưa</b>
 * và không có API lịch sử) nhưng chưa có poller nào chạy.
 *
 * Theo §7: *"Nếu tại thời điểm bàn giao chưa có API, khối vẫn phải dựng đầy đủ và để trạng
 * thái chờ dữ liệu, sẵn sàng đấu nối khi có nguồn."* Nên ở đây có đủ khung, dòng "Cập nhật
 * lúc", nút làm mới, đường dẫn xem sâu — và một câu nói rõ vì sao chưa có số.
 *
 * <h2>Vì sao không dựng sẵn lưới 10 cống với dấu gạch</h2>
 *
 * Vì luật 7: <i>một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai</i>. Một lưới ô mà
 * không lượt chạy nào từng đổ dữ liệu thật vào là mã chưa được kiểm, đội lốt mã đã xong — và
 * danh sách 10 cống trục chính còn đang chờ Công ty chốt (<b>OI-03</b>). Bản trước của khối
 * này có 5 trạm quan trắc viết cứng <b>kèm mực nước và một mức cảnh báo BĐ I trên tên cống có
 * thật</b>; chúng lên staging và không ai nhìn ra đường dữ liệu đã chết (§10.54).
 */
export function WaterLevelBlock({ hotline = '', refreshSeconds, updatedAt }: WaterLevelBlockProps) {
  return (
    <section
      aria-label="Mực nước, lượng mưa"
      className="overflow-hidden rounded-xl border border-brand-primary/20 bg-gradient-to-r from-blue-50/80 via-white to-blue-50/80 p-4 shadow-xs sm:p-5"
    >
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-primary text-white shadow-xs">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M13 10V3L4 14h7v7l9-11h-7z"
              />
            </svg>
          </div>
          <div>
            <h2 className="text-sm font-bold tracking-tight text-brand-primary sm:text-base">
              Mực nước, lượng mưa
            </h2>
            <p className="text-xs text-surface-textSecondary">
              Số liệu 10 cống trên trục chính tại giờ truy cập
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {/* CR-14: xem theo TUẦN / THÁNG phải đăng nhập. Đường dẫn dẫn sang trang chi tiết,
              nơi chính trang ấy chặn ở tầng route — không ẩn nút ở đây rồi coi là đã phân
              quyền (§2: "không xử lý bằng cách ẩn ở giao diện"). */}
          <Link
            href={ROUTES.quanLyVanHanh.mucNuocLuongMua}
            className="inline-flex items-center gap-1.5 rounded-lg bg-brand-primary px-3 py-1.5 text-xs font-bold text-white shadow-xs transition-colors hover:bg-brand-primaryGradientFrom"
          >
            <span>Xem chi tiết</span>
            <span aria-hidden="true">➔</span>
          </Link>
          {hotline ? (
            <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50/90 px-3.5 py-1.5 text-xs text-red-900 shadow-2xs">
              <span className="flex h-2 w-2 rounded-full bg-red-600 animate-pulse"></span>
              <span className="font-semibold">Trực ban PCTT 24/7:</span>
              <a
                href={`tel:${hotline.replace(/\D/g, '')}`}
                className="font-bold text-red-700 hover:underline"
              >
                {hotline}
              </a>
            </div>
          ) : null}
        </div>
      </div>

      <div className="mt-4">
        <RealtimeFrame
          updatedAt={updatedAt}
          refreshSeconds={refreshSeconds}
          unavailable
          unavailableReason="Mô-đun Quản lý dữ liệu thủy văn (MOD-03) chưa được đấu nối, nên chưa có mực nước và lượng mưa để hiển thị."
        />
      </div>
    </section>
  );
}
