/**
 * Thanh điều hướng ngang có vừa khung không — phần **quyết định**, tách khỏi phần đo.
 *
 * <h2>Vì sao tách ra một tệp riêng</h2>
 *
 * Phần đo (`ResizeObserver`, `getComputedStyle`, `scrollWidth`) chỉ chạy được trong trình duyệt
 * thật: jsdom không dựng bố cục nên mọi bề rộng ở đó là `0`. Nếu để phép so nằm lẫn trong
 * component thì **không bài kiểm nào chạm tới được nó**, và một dấu `<=` viết nhầm thành `<`
 * sẽ sống mãi.
 *
 * Tách ra thì ranh giới rõ: trình duyệt lo *đo*, hàm này lo *kết luận*, và phần kết luận —
 * phần duy nhất có thể sai theo kiểu im lặng — kiểm được bằng số.
 */

/** `gap-2` giữa thanh menu và nút Tìm kiếm, tính bằng px. */
export const GAP_NGOAI_PX = 8;

export interface SoDoThanhNgang {
  /** Bề rộng bên trong khung chứa, đã trừ đệm trái/phải. */
  trong: number;
  /** Bề rộng TỰ NHIÊN của thước đo — bản vô hình của các mục cấp 1. */
  thuoc: number;
  /** Bề rộng thật của nút Tìm kiếm (đổi theo breakpoint vì nhãn chữ chỉ hiện từ `xl`). */
  tim: number;
}

/**
 * `true` = hiện thanh ngang · `false` = rơi về ngăn kéo · `null` = **chưa kết luận được**.
 *
 * ⚠ `null` không phải "không vừa". Khung có bề rộng `0` ở những lúc hoàn toàn bình thường — tab
 * chạy nền, lượt vẽ để in, phần tử chưa gắn vào DOM. Trả `false` ở đó sẽ đá thanh về ngăn kéo
 * rồi bật lại khi người dùng quay lại tab: nhấp nháy mà không ai lần ra nguyên nhân. Nơi gọi phải
 * GIỮ NGUYÊN kết luận cũ khi nhận `null`.
 */
export function vuaThanhNgang({ trong, thuoc, tim }: SoDoThanhNgang): boolean | null {
  if (!Number.isFinite(trong) || trong <= 0) return null;
  if (!Number.isFinite(thuoc) || !Number.isFinite(tim)) return null;
  return thuoc + GAP_NGOAI_PX + tim <= trong;
}
