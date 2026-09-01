import { defineConfig } from '@playwright/test';

/**
 * Bộ đo BỐ CỤC bằng trình duyệt thật.
 *
 * <h2>Vì sao tệp này ra đời — 01/09/2026</h2>
 *
 * `vitest.config.mts` **cố ý** không dựng DOM, và lý do ghi ở đó vẫn đúng: một tầng mock nửa
 * vời chỉ tạo ra thứ xanh mà không chứng minh gì. Nhưng hệ quả là **bố cục chưa từng có cổng
 * kiểm nào** — và đó chính là chỗ đã hỏng.
 *
 * <p>PR #70 (31/08) ép Nhóm 1 lọt trong một khung nhìn bằng `max-h-[calc(100svh-17rem)]`, với
 * một chú thích tự khai: *"Đây là số học từ mã nguồn, KHÔNG phải số đo trên trình duyệt"*. Con
 * số ấy đúng phép cộng và **sai cơ chế**: `max-height` trên khung lưới không chặn được hàng
 * lưới. Kết quả là hai khối chồng lên nhau trên màn hình người dùng thật.
 *
 * <p>Không phép kiểm nào trong 204 bài của kho có thể thấy điều đó — chúng đọc **chuỗi class**,
 * mà chuỗi class thì đúng. Thứ sai là **hộp mà trình duyệt vẽ ra**. Nên bộ này đo
 * `getBoundingClientRect()`, không khẳng định gì về mã nguồn.
 *
 * <h2>⚠⚠ Giới hạn — nói ra thay vì để người đọc tự suy (luật 28)</h2>
 *
 * <ul>
 *   <li><b>Bộ này KHÔNG chạy trong CI.</b> Nó cần một stack đang chạy (`make dev-docker` +
 *       nội dung seed), mà CI chưa dựng được. Chạy tay trước mỗi lượt đụng vào bố cục trang
 *       chủ. Nợ đã mở trong `master-tracking.md`.
 *   <li>Nó đo <b>đúng một trang</b> — trang chủ. Các trang khác chưa có bộ đo nào.
 *   <li>Nó đo trên <b>dữ liệu seed local</b>. Ít bài hơn thì cột tin có thể không đủ cao để
 *       tràn, và phép đo chồng lấn sẽ xanh vì tập rỗng — nên có một khẳng định canh SỐ LƯỢNG
 *       bài trước khi đo (luật 7).
 * </ul>
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  reporter: [['list']],
  use: {
    baseURL: process.env.PUBLIC_WEB_URL ?? 'http://localhost:13000',
    // Bố cục là thứ đang đo — ảnh chụp chỉ để người đọc đối chiếu khi một khẳng định đỏ.
    screenshot: 'only-on-failure',
  },
  // ⚠ Không khai `webServer`: stack là Docker do `make dev-docker` dựng, Playwright không sở
  //   hữu vòng đời của nó. Khai vào đây là dựng thêm một tiến trình Next thứ hai trỏ vào cùng
  //   backend — hai nguồn cho cùng một phép đo.
  timeout: 30_000,
  expect: { timeout: 10_000 },
});
