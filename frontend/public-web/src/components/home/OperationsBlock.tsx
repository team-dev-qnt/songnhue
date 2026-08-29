import Link from 'next/link';

import { COT_VAN_HANH } from '@/lib/homeDataColumns';
import { ROUTES } from '@/lib/routes';
import { RealtimeFrame } from '../realtime/RealtimeFrame';
import { ColumnHeaderRow } from './ColumnHeaderRow';

interface OperationsBlockProps {
  refreshSeconds: number;
  updatedAt: string | null;
}

/**
 * Khối **VẬN HÀNH CÔNG TRÌNH** trên trang chủ — CR-15, CR-34, CR-35, CR-36 (§5.3).
 *
 * <p>Khối này <b>chưa từng có</b> trên bản dev; §8 xếp nó vào danh sách "phần còn thiếu so với
 * bố cục đã duyệt".
 *
 * <h2>⛔ Chưa có nguồn — trả lời OI-02</h2>
 *
 * Bảng {@code construction_operation_status} đã tồn tại từ WS-19 (CN-02.11: tình hình vận hành
 * <b>nhập tay</b>, mã trạng thái là danh mục có CRUD). Nhưng ba điều còn thiếu, và không điều
 * nào sửa được ở tầng giao diện:
 *
 * <ul>
 *   <li>chưa có <b>API tự động</b> nào cấp trạng thái trạm bơm, số máy đang chạy, lưu lượng —
 *       §5.3 đòi đúng bốn trường ấy, còn bảng hiện có ghi <i>một</i> giá trị tham số mỗi lần;
 *   <li>chưa có <b>dữ liệu</b>: danh mục công trình tổng thể thuộc G8, Công ty chưa gửi;
 *   <li>chưa có endpoint công khai cho nhật ký vận hành — bảng ấy thuộc phạm vi đơn vị
 *       (lọc tầng 3), nên mở nó ra công khai là một quyết định riêng, không phải một dòng mã.
 * </ul>
 *
 * Theo §7, khối vẫn dựng đủ khung và để trạng thái chờ dữ liệu — từ 29/08 gồm cả hàng
 * <b>tiêu đề sáu cột</b> của CN-02.11 ({@code COT_VAN_HANH}). Ranh giới giống hệt khối mực
 * nước: được dựng tên cột, cấm dựng dòng. Xem ghi chú đầy đủ ở {@code WaterLevelBlock}
 * (luật 7 · §10.54).
 */
export function OperationsBlock({ refreshSeconds, updatedAt }: OperationsBlockProps) {
  return (
    <section
      aria-label="Vận hành công trình"
      className="overflow-hidden rounded-lg border border-emerald-200 bg-white shadow-xs"
    >
      <div className="flex flex-col gap-4 bg-gradient-to-r from-emerald-50/80 to-white p-4 sm:p-5 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-center gap-4">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-emerald-700 text-white shadow-xs">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M3 21h18M5 21V8l7-5 7 5v13M10 21v-6h4v6"
              />
            </svg>
          </div>
          <div className="min-w-0">
            <h2 className="text-base font-bold tracking-tight text-emerald-900 sm:text-[17px]">
              Vận hành công trình
            </h2>
            {/* Chốt G4: mã tình hình vận hành KHÔNG có trong API thủy văn — nó được nhập tay ở
                màn hình quản trị. Nói ra ở đây để người đọc không chờ một con số tự chạy về. */}
            <p className="mt-0.5 text-xs leading-relaxed text-surface-textSecondary">
              Tình hình vận hành hiện hành của từng cống — cập nhật khi có thay đổi
            </p>
          </div>
        </div>

        <Link
          href={ROUTES.quanLyVanHanh.vanHanhCongTrinh}
          className="inline-flex shrink-0 items-center gap-1.5 self-start rounded-lg bg-emerald-700 px-4 py-2 text-[13px] font-bold text-white shadow-xs transition-colors hover:bg-emerald-800"
        >
          <span>Xem chi tiết</span>
          <span aria-hidden="true">➔</span>
        </Link>
      </div>

      <ColumnHeaderRow
        cot={COT_VAN_HANH}
        luoi="grid-cols-[1.7fr_1.2fr_1.3fr_1fr_1.1fr_1.1fr]"
        beRongToiThieu="min-w-[760px]"
      />

      <div className="p-4 sm:p-5">
        <RealtimeFrame
          updatedAt={updatedAt}
          refreshSeconds={refreshSeconds}
          unavailable
          unavailableReason="Chưa có nguồn số liệu vận hành trạm bơm tự động (OI-02), và danh mục công trình chưa được nhập (G8)."
        />
      </div>
    </section>
  );
}
