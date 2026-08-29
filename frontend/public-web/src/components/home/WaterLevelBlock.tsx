import Link from 'next/link';

import { COT_MUC_NUOC } from '@/lib/homeDataColumns';
import { ROUTES } from '@/lib/routes';
import { RealtimeFrame } from '../realtime/RealtimeFrame';
import { ColumnHeaderRow } from './ColumnHeaderRow';

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
 * <h2>⭐ 29/08: có hàng TIÊU ĐỀ CỘT, vẫn không có DÒNG nào</h2>
 *
 * Bản vẽ đòi khối dựng đủ khung, và §7 nói thẳng lý do: người duyệt cần biết *"khi có số thì
 * tôi sẽ đọc được những gì"*. Nên tám tên cột của CN-03.4 nay hiện ra ({@code COT_MUC_NUOC}).
 *
 * <p>⛔ Ranh giới hẹp và cố ý: được dựng <b>tên cột</b>, cấm dựng <b>dòng</b>. Luật 7 —
 * <i>một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai</i> — vẫn áp cho phần thân: một
 * lưới ô mà không lượt chạy nào từng đổ dữ liệu thật vào là mã chưa được kiểm, đội lốt mã đã
 * xong, và danh sách 10 cống trục chính còn đang chờ Công ty chốt (<b>OI-03</b>). Bản trước
 * của khối này có 5 trạm quan trắc viết cứng <b>kèm mực nước và một mức cảnh báo BĐ I trên tên
 * cống có thật</b>; chúng lên staging và không ai nhìn ra đường dữ liệu đã chết (§10.54). Một
 * cái tên cột không thể bị đọc nhầm thành một phép đo; một dòng có tên cống và một con số thì
 * có — đó là toàn bộ chỗ ranh giới nằm.
 */
export function WaterLevelBlock({ hotline = '', refreshSeconds, updatedAt }: WaterLevelBlockProps) {
  return (
    <section
      aria-label="Mực nước, lượng mưa"
      className="overflow-hidden rounded-lg border border-brand-primary/25 bg-white shadow-xs"
    >
      {/* ── Đầu khối: biểu tượng 44px · tên khối · đường dây nóng · lối vào trang chi tiết ── */}
      <div className="flex flex-col gap-4 bg-gradient-to-r from-blue-50/80 to-white p-4 sm:p-5 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-center gap-4">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-brand-primary text-white shadow-xs">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 2.7s5.5 6 5.5 10a5.5 5.5 0 0 1-11 0c0-4 5.5-10 5.5-10z"
              />
            </svg>
          </div>
          <div className="min-w-0">
            <h2 className="text-base font-bold tracking-tight text-brand-primary sm:text-[17px]">
              Mực nước, lượng mưa
            </h2>
            {/* ⛔ Không hứa "10 cống trên trục chính": danh sách ấy là OI-03, Công ty chưa chốt.
                Câu này chỉ mô tả HÌNH DẠNG của bảng — thứ đúng ngay cả khi chưa có dòng nào. */}
            <p className="mt-0.5 text-xs leading-relaxed text-surface-textSecondary">
              Biểu tổng hợp theo tuyến sông — mỗi công trình một cặp thượng lưu và hạ lưu
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          {/* Đường dây nóng đứng TRƯỚC nút, đúng bản vẽ: người mở trang vì đang có sự cố cần
              thấy số điện thoại trước khi thấy một lối đi đọc thêm. */}
          {hotline ? (
            <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50/90 px-3.5 py-2 text-xs text-red-900 shadow-2xs">
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
          {/* CR-14: xem theo TUẦN / THÁNG phải đăng nhập. Đường dẫn dẫn sang trang chi tiết,
              nơi chính trang ấy chặn ở tầng route — không ẩn nút ở đây rồi coi là đã phân
              quyền (§2: "không xử lý bằng cách ẩn ở giao diện"). */}
          <Link
            href={ROUTES.quanLyVanHanh.mucNuocLuongMua}
            className="inline-flex items-center gap-1.5 rounded-lg bg-brand-primary px-4 py-2 text-[13px] font-bold text-white shadow-xs transition-colors hover:bg-brand-primaryGradientFrom"
          >
            <span>Xem chi tiết</span>
            <span aria-hidden="true">➔</span>
          </Link>
        </div>
      </div>

      {/* Hàng tiêu đề 8 cột của CN-03.4 — lược đồ của bảng, không phải dữ liệu của bảng. */}
      <ColumnHeaderRow
        cot={COT_MUC_NUOC}
        luoi="grid-cols-[1.1fr_1.7fr_0.9fr_1fr_1fr_0.95fr_1.1fr_0.9fr]"
        beRongToiThieu="min-w-[920px]"
      />

      <div className="p-4 sm:p-5">
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
