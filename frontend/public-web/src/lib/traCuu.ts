/**
 * Trạng thái một lượt tra cứu trên trang Tìm kiếm — **T61.17 (WS-72)**.
 *
 * `apiGet*` (`lib/api.ts`) trả `null` cho CẢ 404 lẫn *backend ⛔ trả lời được* (429 · 5xx · mất kết
 * nối), và gộp như vậy là cố ý ở tầng ấy. Trang tìm kiếm thì ⛔ gộp được: trước bản vá, `null` rơi vào
 * ô rỗng của danh sách và trang in *"Không tìm thấy…"* — tức khẳng định *"đã tìm, ⛔ có"* cho một lượt
 * chưa hề tìm (quy tắc 16).
 *
 * Tìm kiếm là chỗ 429 cắn người dùng thật: `public-web` gọi backend lúc dựng trang từ **IP của
 * container**, nên mọi khách chung một xô PUBLIC 300/phút; mỗi từ khoá là một URL riêng nên đệm dữ
 * liệu ⛔ đỡ được. (Next 16.3.5 chỉ đệm phản hồi **200** — `patch-fetch.js`, `res.status === 200` —
 * nên một lượt 429 ⛔ bị giữ lại; lượt sau hỏi lại backend.)
 */
export type TrangThaiTraCuu = 'khong-tra-loi' | 'rong' | 'co';

export function trangThaiTraCuu(
  ketQua: { content: readonly unknown[] } | null | undefined,
): TrangThaiTraCuu {
  if (ketQua == null) return 'khong-tra-loi';
  return ketQua.content.length === 0 ? 'rong' : 'co';
}

/**
 * Dấu hiệu CẤU TRÚC của trạng thái `khong-tra-loi` trong HTML trả về — ⛔ dựa vào câu chữ, vì câu chữ
 * là thứ người ta sửa thoải mái nhất.
 *
 * `tools/tai-thu/cong-cong-khai.js` đếm nó thành chỉ số `tim_kiem_khong_tra_loi`; `KichBanTaiThuTest`
 * đối chiếu ba nơi (tệp này · kịch bản · máy chủ giả của bộ tự kiểm) — quy tắc 14.
 */
export const THUOC_TINH_KHONG_TRA_LOI = { 'data-tra-cuu': 'khong-tra-loi' } as const;
