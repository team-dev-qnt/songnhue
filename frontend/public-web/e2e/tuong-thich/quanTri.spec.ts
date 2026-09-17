import { expect, test } from '@playwright/test';

import { batBuocBien, doChuHienRa, doTranNgang, ghiLoi } from './doKiem';

/**
 * NFR-09 — giao diện quản trị. Trang đăng nhập đo LUÔN. Màn hình sau đăng nhập cần tài khoản đo
 * (`TUONG_THICH_ADMIN_USER`/`_PASS`, ⛔ 2FA); thiếu thì ĐỎ — trừ khi khai tường minh
 * `TUONG_THICH_CHI_DANG_NHAP=1` (một lượt `skip` im lặng đọc như đã đo — luật 24).
 */

const MAN_HINH = [
  '/',
  '/van-hanh/dieu-hanh',
  '/van-hanh/cong-trinh',
  '/thuy-van/diem-do',
  '/thuy-van/bieu-do-muc-nuoc',
  '/noi-dung/bai-viet',
  '/nhan-su/ho-so',
  '/nhan-su/danh-ba',
  '/quan-tri/tai-khoan',
  '/quan-tri/cau-hinh',
] as const;

test('quản trị /dang-nhap', async ({ page }) => {
  const goc = batBuocBien('TUONG_THICH_ADMIN_URL');
  const nk = ghiLoi(page, goc);
  const ra = await page.goto(goc + '/dang-nhap');
  expect(ra?.status()).toBeLessThan(400);
  await expect(page.locator('input[type="password"]')).toBeVisible();
  const tran = await doTranNgang(page);
  expect(tran.px, `tràn ngang ${tran.px}px — ${tran.thoRa.join(' · ')}`).toBeLessThanOrEqual(1);
  expect(nk.loiJs).toEqual([]);
  expect(nk.taiNguyenHong).toEqual([]);
});

test.describe('quản trị — sau đăng nhập', () => {
  const chiDangNhap = process.env.TUONG_THICH_CHI_DANG_NHAP === '1';
  test.skip(
    chiDangNhap,
    'TUONG_THICH_CHI_DANG_NHAP=1 — khai tường minh: ⛔ đo màn hình sau đăng nhập',
  );

  for (const duong of MAN_HINH) {
    test(`quản trị ${duong}`, async ({ page }) => {
      const goc = batBuocBien('TUONG_THICH_ADMIN_URL');
      const nguoiDung = batBuocBien('TUONG_THICH_ADMIN_USER');
      const matKhau = batBuocBien('TUONG_THICH_ADMIN_PASS');

      await page.goto(goc + '/dang-nhap');
      await page
        .locator('input[autocomplete="username"], input#username, input[name="username"]')
        .first()
        .fill(nguoiDung);
      await page.locator('input[type="password"]').fill(matKhau);
      await page.locator('button[type="submit"]').click();
      await page.waitForURL((u) => !u.pathname.startsWith('/dang-nhap'), { timeout: 20_000 });
      expect(page.url(), 'tài khoản đo ⛔ được bật 2FA').not.toContain('/xac-thuc-2-buoc');

      const nk = ghiLoi(page, goc);
      await page.goto(goc + duong);
      await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => {});
      const tran = await doTranNgang(page);
      expect(
        tran.px,
        `${duong}: tràn ngang ${tran.px}px — ${tran.thoRa.join(' · ')}`,
      ).toBeLessThanOrEqual(1);
      expect(await doChuHienRa(page), `${duong}: khối chính gần như trống`).toBeGreaterThan(20);
      expect(nk.loiJs, `${duong}: lỗi JS`).toEqual([]);
      expect(nk.taiNguyenHong, `${duong}: tài nguyên cùng nguồn hỏng`).toEqual([]);
    });
  }
});
