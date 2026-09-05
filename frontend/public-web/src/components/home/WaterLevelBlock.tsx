import Link from 'next/link';

import type { WaterLevelRow } from '@/lib/api';
import { COT_MUC_NUOC } from '@/lib/homeDataColumns';
import { ROUTES } from '@/lib/routes';
import { RealtimeFrame } from '../realtime/RealtimeFrame';
import { ColumnHeaderRow } from './ColumnHeaderRow';
import { WaterLevelRows } from './WaterLevelRows';

interface WaterLevelBlockProps {
  hotline?: string;
  refreshSeconds: number;
  /** Mốc của số liệu. `null` khi chưa có nguồn — xem ghi chú về việc KHÔNG lấy giờ máy khách. */
  updatedAt: string | null;
  /**
   * Dòng số liệu — **T35.7**.
   *
   * ⚠ `null` = **lượt gọi API hỏng** (`apiGet` gộp 404 và lỗi mạng làm một); `[]` = gọi được
   * nhưng **chưa điểm đo nào đang hoạt động**. Hai trạng thái khác nhau và khối này nói hai câu
   * khác nhau — gộp lại là để một sự cố backend trông y hệt một hệ thống chưa có dữ liệu.
   */
  rows: WaterLevelRow[] | null;
}

/**
 * Khối **MỰC NƯỚC, LƯỢNG MƯA** trên trang chủ — CR-13, CR-14, CR-33, CR-35, CR-36.
 *
 * <h2>⭐⭐ 04/09/2026 — khối này NAY CÓ NGUỒN DỮ LIỆU THẬT (T35.7)</h2>
 *
 * Số đến từ {@code GET /api/v1/public/hydro/muc-nuoc}, đọc bảng {@code hydro_latest} do poller
 * bhh40 ghi. Trả lời <b>OI-01</b>: API mực nước <b>đã đấu nối</b>.
 *
 * <p>⚠ Ba giới hạn của nguồn vẫn còn nguyên và cổng phải nói ra, ⛔ không được lấp liếm:
 *
 * <ul>
 *   <li><b>Không có API lượng mưa</b> — cột "Lượng mưa" rỗng vĩnh viễn cho tới khi chốt
 *       <b>G3-a</b>. ⛔ Không `?? 0`: `0 mm` là một khẳng định về thời tiết.
 *   <li><b>Tuyến sông và lý trình chưa có</b> — mục <b>G8</b>; các dòng gom vào "Chưa phân tuyến".
 *   <li><b>Không có API lịch sử</b> — dữ liệu quá khứ chỉ có kể từ ngày poller chạy lần đầu.
 * </ul>
 *
 * <h2>⛔⛔ Ranh giới cũ đã được gỡ ĐÚNG CÁCH, ⛔ không phải bị nới</h2>
 *
 * Từ 29/08 khối này cố ý chỉ dựng <b>tên cột</b> và cấm dựng <b>dòng</b>, vì lúc ấy chưa có
 * endpoint nào đứng sau — và bản trước đó đã có 5 trạm quan trắc viết cứng <b>kèm mực nước và một
 * mức "Cảnh báo BĐ I" gắn tên cống có thật</b>; chúng lên staging và không ai nhìn ra đường dữ
 * liệu đã chết (§10.54).
 *
 * <p>Ranh giới ấy nay hết hạn vì <b>đã có nguồn thật</b>, ⛔ không phải vì ai đó thấy trang trống
 * quá. ⛔ Vẫn cấm tuyệt đối: mảng `DEFAULT_*`, `rows.length >= n ? rows : [...rows, ...BIA]`, và
 * mọi con số viết trong tệp này. `noFabricatedContent.test.ts` quét toàn cây và phải vẫn xanh.
 */
export function WaterLevelBlock({
  hotline = '',
  refreshSeconds,
  updatedAt,
  rows,
}: WaterLevelBlockProps) {
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
              quyền (§2: "không xử lý bằng cách ẩn ở giao diện").

              ⚠ 04/09: trang đích còn có thể trả 404 khi công tắc `lib/khoiVanHanh.ts` tắt. Liên
              kết này an toàn vì cả khối chỉ được vẽ BÊN TRONG nhánh `hienDieuHanh` của
              `app/page.tsx` — an toàn do VỊ TRÍ trong JSX, không do một phép kiểm nào. */}
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
        {/* ⚠ `unavailable` CHỈ khi lượt gọi hỏng (`rows === null`) — T35.10: widget hỏng ⛔ không
            được làm sập trang chủ, và cũng ⛔ không được lộ lỗi kỹ thuật ra ngoài. Danh sách rỗng
            là một trạng thái KHÁC và có câu nói riêng bên dưới. */}
        <RealtimeFrame
          updatedAt={updatedAt}
          refreshSeconds={refreshSeconds}
          unavailable={rows === null}
          unavailableReason="Chưa lấy được số liệu mực nước. Số liệu sẽ hiện lại khi kết nối tới nguồn được khôi phục."
        >
          {rows !== null && rows.length > 0 ? (
            <WaterLevelRows
              rows={rows}
              luoi="grid-cols-[1.1fr_1.7fr_0.9fr_1fr_1fr_0.95fr_1.1fr_0.9fr]"
              beRongToiThieu="min-w-[920px]"
            />
          ) : (
            /* ⛔ Rỗng THẬT — nói thẳng, ⛔ không dựng một lưới dấu gạch cho "đỡ trống" (§10.61). */
            <p className="px-3.5 py-6 text-center text-[13px] text-surface-textSecondary">
              Chưa điểm đo nào đang hoạt động để công bố số liệu.
            </p>
          )}
        </RealtimeFrame>
      </div>
    </section>
  );
}
