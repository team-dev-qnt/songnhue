import { defineConfig, devices } from '@playwright/test';

/**
 * NFR-02a · `DOD4.5` — trang chủ < 3s, đo **từ một trình duyệt thật**, gồm **cả lượt ISR nguội**.
 *
 * <h2>Vì sao tệp này ra đời — 18/09/2026 (T63.22)</h2>
 *
 * `DOD4.5` đòi một con số LCP từ ngày Phase 4 mở, và tới hôm nay kho có **0** công cụ đo nó:
 * `git ls-files | grep -ic lighthouse` = **0**. Con số duy nhất đang có là `http_req_duration` của
 * k6 — **thời gian máy chủ trả byte đầu**, ⛔ phải thời gian người dùng **nhìn thấy** trang. Hai
 * con số ấy lệch nhau đúng ở phần đắt nhất của một trang Next: nạp JS, hydrate, vẽ.
 *
 * <h2>Chạy</h2>
 *
 * ```bash
 * cd frontend/public-web
 * npx playwright install chromium                      # lần đầu
 * HIEU_NANG_URL=https://staging.songnhue.com \
 * REVALIDATE_SECRET=<bí mật của môi trường ấy> \
 *   npx playwright test -c playwright.hieu-nang.config.ts
 * ```
 *
 * <h2>⚠ Giới hạn — nói ra thay vì để người đọc tự suy (luật 28)</h2>
 *
 * <ul>
 *   <li><b>Chromium thôi.</b> `largest-contentful-paint` là API của Chromium; Firefox và WebKit ⛔
 *       phát entry ấy, nên thêm chúng vào đây là dựng ba dự án mà hai trong ba ⛔ đo được gì.
 *   <li><b>⛔ chạy trong CI.</b> Nó cần một môi trường đang phục vụ; CI ⛔ dựng được. Chạy tay trước
 *       mỗi lượt nghiệm thu, rồi ghi số vào `docs/nghiem-thu-nfr.md` kèm **ngày đo · nguồn đo**
 *       (`DOD4.10`).
 *   <li><b>⛔ đo từ máy dev rồi ghi vào sổ.</b> LCP gồm cả đường truyền: một lượt đo từ máy lập
 *       trình viên ⛔ nói gì về *"máy ở Việt Nam"* mà `DOD4.5` đòi. Máy bắn đặt ở đâu là một phần
 *       của số đo.
 *   <li>Một lượt đo ⛔ phải một phân phối. Con số này để đối chiếu với ngưỡng 3s, ⛔ thay được cho
 *       P95 của lượt tải thật (`tools/tai-thu/`).
 * </ul>
 */
const URL_DO = process.env.HIEU_NANG_URL;

if (!URL_DO) {
  // ⛔ có giá trị mặc định — cùng lý do với `BASE_URL` của bộ đo tải: một mặc định trỏ vào
  // production biến một lượt đo thành một lượt xoá đệm trang chủ của người dùng thật.
  throw new Error(
    'Thiếu HIEU_NANG_URL (VD https://staging.songnhue.com). ⛔ có mặc định: bộ đo này GỌI ' +
      '/api/revalidate, tức nó XOÁ đệm ISR của môi trường nó trỏ tới.',
  );
}

export default defineConfig({
  testDir: './e2e/hieu-nang',
  // Thứ tự có nghĩa ở đây: lượt ẤM phải đo TRƯỚC lượt xoá đệm, nếu không thì "ấm" đo nhầm một
  // trang vừa dựng lại. Một worker, ⛔ song song.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  outputDir: 'test-results-hieu-nang',
  timeout: 120_000,
  expect: { timeout: 20_000 },
  use: { baseURL: URL_DO, screenshot: 'only-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
