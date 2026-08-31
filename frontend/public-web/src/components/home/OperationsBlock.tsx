import Link from 'next/link';

import type { OperationStatusRow } from '@/lib/api';
import { COT_VAN_HANH } from '@/lib/homeDataColumns';
import { ROUTES } from '@/lib/routes';
import { RealtimeFrame } from '../realtime/RealtimeFrame';
import { ColumnHeaderRow } from './ColumnHeaderRow';
import { OperationStatusRows } from './OperationStatusRows';

/** Lưới 6 cột — dùng chung cho hàng tiêu đề và hàng dữ liệu; lệch một chỗ là cột lệch nhau. */
const LUOI = 'grid-cols-[1.7fr_1.2fr_1.3fr_1fr_1.1fr_1.1fr]';
const BE_RONG_TOI_THIEU = 'min-w-[760px]';

interface OperationsBlockProps {
  refreshSeconds: number;
  updatedAt: string | null;
  /**
   * Tình hình vận hành hiện hành, từ `getOperationStatuses()`.
   *
   * ⛔ Rỗng ⇒ khối hiện trạng thái chờ dữ liệu, **không** dựng lưới dấu gạch. `null` (API hỏng)
   * cũng vậy — nơi gọi truyền `?? []`.
   */
  rows: OperationStatusRow[];
}

/**
 * Khối **VẬN HÀNH CÔNG TRÌNH** trên trang chủ — CR-15, CR-34, CR-35, CR-36 (§5.3).
 *
 * <p>Khối này <b>chưa từng có</b> trên bản dev; §8 xếp nó vào danh sách "phần còn thiếu so với
 * bố cục đã duyệt".
 *
 * <h2>⭐ 31/08: khối này ĐÃ ĐẤU NỐI — nhưng chỉ đúng phần có nguồn</h2>
 *
 * Ba điều từng thiếu ở đây, nay còn hai:
 *
 * <ul>
 *   <li>✅ <b>endpoint công khai</b> — {@code GET /api/v1/public/constructions/operation-statuses}.
 *       Đây từng là mục "chưa có" duy nhất sửa được bằng mã, và nó không phải một dòng mã: bảng
 *       {@code construction_operation_status} thuộc phạm vi đơn vị (lọc tầng 3), nên mở ra công
 *       khai là một quyết định về <b>phạm vi công bố</b>. Chốt 31/08: công bố đúng sáu cột của
 *       {@code COT_VAN_HANH}; {@code note} và người cập nhật ở lại bên trong;
 *   <li>⬜ chưa có <b>API tự động</b> cấp trạng thái trạm bơm, số máy đang chạy, lưu lượng —
 *       §5.3 đòi đúng bốn trường ấy theo ngày, còn bảng hiện có ghi <i>một</i> giá trị tham số
 *       mỗi lần. <b>OI-02 còn mở</b>, và bảng này không thay thế được nó;
 *   <li>⬜ chưa có <b>dữ liệu</b>: danh mục công trình tổng thể thuộc G8, Công ty chưa gửi.
 * </ul>
 *
 * <p>⛔ Vì mục thứ ba, khối vẫn rỗng cho tới khi có công trình. Rỗng thì hiện trạng thái chờ dữ
 * liệu — <b>không</b> dựng sẵn một lưới mười cống với dấu gạch: <i>một lưới mà không lượt chạy nào
 * từng đổ dữ liệu thật vào là mã chưa được kiểm, đội lốt mã đã xong</i> (luật 7 · §10.54).
 * Khác trước ở chỗ: nay có một đường thật để dữ liệu chảy qua, và nó đã được đi thử.
 */
export function OperationsBlock({ refreshSeconds, updatedAt, rows }: OperationsBlockProps) {
  const coDuLieu = rows.length > 0;

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

      <ColumnHeaderRow cot={COT_VAN_HANH} luoi={LUOI} beRongToiThieu={BE_RONG_TOI_THIEU} />

      {coDuLieu ? (
        <OperationStatusRows rows={rows} luoi={LUOI} beRongToiThieu={BE_RONG_TOI_THIEU} />
      ) : null}

      <div className="p-4 sm:p-5">
        <RealtimeFrame
          updatedAt={updatedAt}
          refreshSeconds={refreshSeconds}
          unavailable={!coDuLieu}
          unavailableReason="Chưa công trình nào được ghi nhận tình hình vận hành — danh mục công trình tổng thể thuộc G8, Công ty chưa gửi. Số liệu tự động của trạm bơm (§5.3) vẫn chờ API nguồn — OI-02."
        />
      </div>
    </section>
  );
}
