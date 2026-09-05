/**
 * Nguồn ô bản đồ — khai **một lần**, ở đây.
 *
 * <h2>⛔ Vì sao không viết thẳng URL vào component</h2>
 *
 * Địa chỉ này phải khớp với `img-src` của CSP ở `next.config.ts`. Đó là hai nơi con người phải
 * nhớ giống nhau, và luật 14 nói rõ chỗ nào như vậy thì chỗ đó cần một phép kiểm nhớ hộ —
 * `mapTiles.test.ts` đối chiếu hằng số này với CSP đang khai.
 *
 * <p>Hỏng kiểu này im lặng đúng theo cách khó chịu nhất: bản đồ vẫn dựng, vẫn kéo thả được,
 * chỉ là **toàn màu xám** vì mọi ô ảnh bị CSP chặn — và lỗi chỉ hiện trong console của trình
 * duyệt, nơi không cổng kiểm nào nhìn.
 */

/** Host của nguồn ô — tách riêng để CSP và URL không thể lệch nhau. */
export const TILE_HOST = 'https://tile.openstreetmap.org';

export const TILE_URL = `${TILE_HOST}/{z}/{x}/{y}.png`;

/**
 * Ghi công bắt buộc theo điều khoản của OpenStreetMap. Bỏ dòng này là dùng dữ liệu của người
 * khác mà không ghi nguồn — không phải chuyện thẩm mỹ.
 */
export const TILE_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';
