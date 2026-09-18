import { defineConfig, devices } from '@playwright/test';

/**
 * NFR-09 — tương thích trình duyệt × bề rộng màn hình. T61.29 (QuanTran chốt 15/09/2026).
 *
 * Đặc tả (`function-spec.md` NFR-09): *Chrome/Firefox/Edge/Safari · responsive 360px–2560px*.
 * Ma trận: 3 engine (chromium = Chrome + Edge · firefox · webkit = Safari) × 4 bề rộng = 12 dự án.
 *
 * <h2>Chạy</h2>
 *
 * ```bash
 * cd frontend/public-web
 * npx playwright install chromium firefox webkit        # lần đầu
 * TUONG_THICH_PUBLIC_URL=https://staging.songnhue.com \
 * TUONG_THICH_ADMIN_URL=https://admin-staging.songnhue.com \
 *   npx playwright test -c playwright.tuong-thich.config.ts
 * # thêm TUONG_THICH_ADMIN_USER / TUONG_THICH_ADMIN_PASS (tài khoản đo, ⛔ 2FA) để đo màn hình sau đăng nhập
 * ```
 *
 * <h2>⚠ Giới hạn — nói ra (luật 28)</h2>
 *
 * <ul>
 *   <li>⛔ Chạy trong CI của PR (chốt: chạy tay/theo lịch). Cần một stack đang chạy.
 *   <li>Edge ⛔ đo riêng: cùng engine Blink với Chrome. Safari đo bằng WebKit của Playwright — gần,
 *       ⛔ trùng khít Safari iOS thật (font hệ thống, thanh địa chỉ co giãn).
 *   <li>Đo <b>hỏng thô</b>: tải được · ⛔ lỗi JS · ⛔ tràn ngang · khối chính hiện ra. ⛔ Đo thẩm mỹ.
 * </ul>
 */

const ENGINE = [
  { ten: 'chromium', thietBi: devices['Desktop Chrome'] },
  { ten: 'firefox', thietBi: devices['Desktop Firefox'] },
  { ten: 'webkit', thietBi: devices['Desktop Safari'] },
] as const;

/** Hai mép của NFR-09 (360 · 2560) + máy tính bảng dọc + laptop phổ thông. */
const BE_RONG = [
  { width: 360, height: 740 },
  { width: 768, height: 1024 },
  { width: 1440, height: 900 },
  { width: 2560, height: 1440 },
] as const;

export default defineConfig({
  testDir: './e2e/tuong-thich',
  fullyParallel: true,
  // Nhẹ tay với môi trường đo: xô PUBLIC 300 lượt/phút mỗi IP (T61.6) — ⛔ nới hạn mức để đo.
  workers: 2,
  retries: 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report-tuong-thich', open: 'never' }]],
  outputDir: 'test-results-tuong-thich',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  use: { screenshot: 'only-on-failure' },
  projects: ENGINE.flatMap((e) =>
    BE_RONG.map((v) => ({
      name: `${e.ten}-${v.width}`,
      use: { ...e.thietBi, viewport: v, deviceScaleFactor: 1 },
    })),
  ),
});
