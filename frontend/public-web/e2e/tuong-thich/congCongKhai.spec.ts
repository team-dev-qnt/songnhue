import { expect, test, type Page } from '@playwright/test';

import { batBuocBien, doChuHienRa, doTranNgang, ghiLoi } from './doKiem';

/**
 * NFR-09 — cổng công khai. Trang TĨNH theo menu + MỘT bài viết và MỘT danh mục lấy từ chính trang chủ
 * (⛔ gõ slug: dữ liệu mỗi môi trường khác nhau). Trang chủ ⛔ có liên kết bài viết ⇒ ĐỎ, ⛔ bỏ qua
 * (luật 7 — bộ đo chạy qua tập rỗng vẫn xanh).
 */

const TRANG = [
  '/',
  '/tim-kiem?q=th%E1%BB%A7y%20l%E1%BB%A3i',
  '/lien-he',
  '/gop-y',
  '/gioi-thieu/co-cau-to-chuc',
  '/gioi-thieu/lanh-dao',
  '/gioi-thieu/xi-nghiep',
  '/quan-ly-van-hanh/muc-nuoc-luong-mua',
  '/quan-ly-van-hanh/danh-muc-cong-trinh',
  '/quan-ly-van-hanh/van-hanh-cong-trinh',
] as const;

/** Tối thiểu số ký tự hiện ra — dưới mức này là khung trống / trang lỗi. */
const CHU_TOI_THIEU = 40;

async function doTrang(page: Page, goc: string, duong: string) {
  const nk = ghiLoi(page, goc);
  const ra = await page.goto(goc + duong, { waitUntil: 'load' });
  expect(ra?.status(), `${duong}: mã HTTP`).toBeLessThan(400);
  await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => {
    /* trang có poll định kỳ (widget thuỷ văn) ⛔ bao giờ networkidle — đo tiếp */
  });
  const tran = await doTranNgang(page);
  expect(
    tran.px,
    `${duong}: tràn ngang ${tran.px}px — ${tran.thoRa.join(' · ')}`,
  ).toBeLessThanOrEqual(1);
  expect(await doChuHienRa(page), `${duong}: khối chính gần như trống`).toBeGreaterThan(
    CHU_TOI_THIEU,
  );
  expect(nk.loiJs, `${duong}: lỗi JS`).toEqual([]);
  expect(nk.taiNguyenHong, `${duong}: tài nguyên cùng nguồn hỏng`).toEqual([]);
}

for (const duong of TRANG) {
  test(`cổng ${duong}`, async ({ page }) => {
    await doTrang(page, batBuocBien('TUONG_THICH_PUBLIC_URL'), duong);
  });
}

test('cổng — một bài viết và một danh mục lấy từ trang chủ', async ({ page }) => {
  const goc = batBuocBien('TUONG_THICH_PUBLIC_URL');
  await page.goto(goc + '/');
  const bai = await page.locator('a[href^="/bai-viet/"]').first().getAttribute('href');
  const danhMuc = await page.locator('a[href^="/danh-muc/"]').first().getAttribute('href');
  expect(bai, 'trang chủ ⛔ có liên kết bài viết nào — ⛔ đo được trang chi tiết').toBeTruthy();
  expect(danhMuc, 'trang chủ ⛔ có liên kết danh mục nào').toBeTruthy();
  // ⚠ Mỗi trang đo trên một TAB MỚI. Đo 15/09/2026 trên staging: điều hướng tiếp trên cùng tab làm
  //   WebKit báo `pageerror` "Fetch API cannot load …?_rsc=… due to access control checks" cho MỌI
  //   prefetch RSC mà trang chủ đang chạy dở — lỗi của lượt HUỶ, ghi vào trang SAU. Chromium/Firefox im.
  for (const duong of [bai!, danhMuc!]) {
    const tab = await page.context().newPage();
    await doTrang(tab, goc, duong);
    await tab.close();
  }
});
