import type { OperationStatusRow } from '@/lib/api';
import { mauTrangThaiHopLe } from '@/lib/mauTrangThai';
import { formatDateTime } from '@/lib/routes';

interface OperationStatusRowsProps {
  rows: OperationStatusRow[];
  /** Lớp `grid-cols-[…]` — phải TRÙNG với lớp truyền cho `ColumnHeaderRow`, nếu không cột lệch. */
  luoi: string;
  /** `min-w-[…]` — cũng phải trùng, vì hai khối cuộn ngang trong cùng một khung. */
  beRongToiThieu: string;
}

/**
 * Các dòng dữ liệu của bảng "Tình hình vận hành công trình" — CN-02.11, 6 cột.
 *
 * <h2>Vì sao tách khỏi `ColumnHeaderRow` thay vì gộp thành một bảng</h2>
 *
 * Hàng tiêu đề đã sống từ 29/08 với ranh giới ghi rõ: *"được dựng tên cột, cấm dựng dòng"* — vì
 * lúc ấy chưa có endpoint nào đứng sau. Nay có, và phần thêm vào là **đúng phần dòng**. Giữ hai
 * component tách nhau để lớp lưới (`luoi`) vẫn là một chuỗi literal ở nơi gọi — Tailwind phải
 * nhìn thấy nó lúc biên dịch, và đó cũng là lý do `ColumnHeaderRow` nhận nó qua prop.
 *
 * <h2>⛔ Ô trống nói thẳng là trống</h2>
 *
 * Mã không mang tham số ⇒ `parameterValue` là `null` ⇒ hiện `—`. Không quy về `0`: quy tắc 16,
 * *số 0 là một câu khẳng định*, và trên bảng vận hành thì "điều tiết 0,00 m" khác hẳn "mã này
 * không có tham số".
 */
export function OperationStatusRows({ rows, luoi, beRongToiThieu }: OperationStatusRowsProps) {
  return (
    <div className="overflow-x-auto">
      <div className={`${beRongToiThieu} divide-y divide-surface-border`}>
        {rows.map((row) => {
          const mau = mauTrangThaiHopLe(row.statusColor);
          return (
            <div key={row.constructionCode} className={`grid ${luoi} items-center`}>
              <div className="px-3.5 py-2.5">
                <span className="text-[13px] font-semibold text-surface-textBase">
                  {row.constructionName}
                </span>
                <span className="mt-0.5 block text-[11px] text-surface-textSecondary">
                  {row.constructionCode}
                </span>
              </div>

              <div className="px-3.5 py-2.5 text-[12px] text-surface-textBase">
                {/* ⛔ Không giấu dòng khi thiếu đơn vị: một công trình biến mất khỏi bảng mà không
                    dòng log nào báo là đúng loại mất mát không ai phát hiện. */}
                {row.unitName ?? (
                  <span className="text-surface-textSecondary">Chưa phân đơn vị quản lý</span>
                )}
              </div>

              <div className="px-3.5 py-2.5">
                <span
                  className="inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[12px] font-bold"
                  // Màu đến từ danh mục mã trong CSDL — Công ty tự đặt, không phải hằng số trong
                  // mã. `mauTrangThaiHopLe` là lớp chặn hình dạng; không hợp lệ thì rơi về nền
                  // trung tính của hệ thiết kế thay vì vẽ một badge không màu.
                  style={mau ? { backgroundColor: `${mau}1a`, color: mau } : undefined}
                >
                  {row.statusCode}
                </span>
                <span className="mt-0.5 block text-[11px] text-surface-textSecondary">
                  {row.statusName}
                </span>
              </div>

              <div className="px-3.5 py-2.5 text-[13px] tabular-nums text-surface-textBase">
                {row.parameterValue === null || row.parameterValue === undefined ? (
                  <span className="text-surface-textSecondary">—</span>
                ) : (
                  <>
                    {row.parameterValue}
                    {row.parameterUnit ? (
                      <span className="ml-1 text-[11px] text-surface-textSecondary">
                        {row.parameterUnit}
                      </span>
                    ) : null}
                  </>
                )}
              </div>

              <div className="px-3.5 py-2.5 text-[12px] text-surface-textBase">
                {formatDateTime(row.effectiveAt)}
              </div>

              <div className="px-3.5 py-2.5 text-[12px] text-surface-textSecondary">
                {formatDateTime(row.updatedAt) || '—'}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
