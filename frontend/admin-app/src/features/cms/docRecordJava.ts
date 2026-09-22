import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

/**
 * Đọc danh sách thành phần của một `record` Java — dùng chung cho các bộ canh hợp đồng BE ↔ FE.
 *
 * <h3>Vì sao tách ra khỏi `soanBaiVongKhuHoi.test.tsx`</h3>
 *
 * Tới T84.5 có **ba** bộ canh cần đúng phép đọc này (vòng khứ hồi của `SaveRequest`, chiều RA của
 * `ArticleDetail`/`ArticleSummary` ở admin, và bản anh em ở `public-web`). Chép nó sang từng nơi là
 * dựng ba bản có thể lệch nhau — và bản yếu nhất sẽ cho ra tập NHỎ HƠN, tức xanh vì lý do sai
 * (T48.4 đã trả giá đúng hình dạng ấy).
 *
 * ⚠ Đây là tệp **chỉ dùng trong bộ kiểm** — nó đọc `node:fs`. Đặt ở `features/cms` chứ ⛔ ở
 * `shared/` vì `shared/` vào bundle sản phẩm.
 */

/** Gốc kho, tính từ vị trí tệp này (`frontend/admin-app/src/features/cms`). */
export const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');

export function docTep(duongDanTuGocKho: string): string {
  return readFileSync(join(GOC_KHO, duongDanTuGocKho), 'utf8');
}

/**
 * Tên các thành phần của `record <ten>(...)` trong `nguon`.
 *
 * <p>⚠ Bóc theo thứ tự: chú thích khối → chú thích dòng → annotation → generic. Bỏ generic phải
 * **lặp cho tới khi ổn định**, vì `List<Map<String, UUID>>` có generic lồng nhau và một lượt
 * `replace` chỉ gỡ được lớp trong cùng.
 */
export function truongCuaRecord(nguon: string, ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(nguon);
  if (!m) throw new Error(`Không tìm thấy record ${ten}`);
  let than = m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ');
  let truoc = '';
  while (truoc !== than) {
    truoc = than;
    than = than.replace(/<[^<>]*>/g, '');
  }
  return than
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}
