import Link from 'next/link';

import { ROUTES } from '@/lib/routes';
import { RealtimeFrame } from '../realtime/RealtimeFrame';

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
 * Theo §7, khối vẫn dựng đủ khung và để trạng thái chờ dữ liệu. Không dựng sẵn một lưới trạm
 * bơm rỗng: xem ghi chú cùng loại ở {@code WaterLevelBlock} (luật 7 · §10.54).
 */
export function OperationsBlock({ refreshSeconds, updatedAt }: OperationsBlockProps) {
  return (
    <section
      aria-label="Vận hành công trình"
      className="overflow-hidden rounded-xl border border-emerald-200/70 bg-gradient-to-r from-emerald-50/70 via-white to-emerald-50/70 p-4 shadow-xs sm:p-5"
    >
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-emerald-700 text-white shadow-xs">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
              />
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
              />
            </svg>
          </div>
          <div>
            <h2 className="text-sm font-bold tracking-tight text-emerald-900 sm:text-base">
              Vận hành công trình
            </h2>
            <p className="text-xs text-surface-textSecondary">
              Số liệu vận hành trạm bơm của từng Xí nghiệp tại ngày truy cập
            </p>
          </div>
        </div>

        <Link
          href={ROUTES.quanLyVanHanh.vanHanhCongTrinh}
          className="inline-flex items-center gap-1.5 self-start rounded-lg bg-emerald-700 px-3 py-1.5 text-xs font-bold text-white shadow-xs transition-colors hover:bg-emerald-800"
        >
          <span>Xem chi tiết</span>
          <span aria-hidden="true">➔</span>
        </Link>
      </div>

      <div className="mt-4">
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
