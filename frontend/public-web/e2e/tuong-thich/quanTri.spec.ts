import { expect, test, type Page } from '@playwright/test';

import { batBuocBien, doChuHienRa, doTranNgang, ghiLoi } from './doKiem';

/**
 * NFR-09 — giao diện quản trị. Trang đăng nhập đo LUÔN. Màn hình sau đăng nhập cần tài khoản đo
 * (`TUONG_THICH_ADMIN_USER`/`_PASS`, ⛔ 2FA); thiếu thì ĐỎ — trừ khi khai tường minh
 * `TUONG_THICH_CHI_DANG_NHAP=1` (một lượt `skip` im lặng đọc như đã đo — luật 24).
 *
 * <h2>⛔⛔ Đăng nhập MỘT lần mỗi dự án — T61.29 (WS-72)</h2>
 *
 * Bản trước cho mỗi màn hình một bài tự đăng nhập, tức mỗi bài ba lượt vào đường xác thực: làm mới
 * phiên lúc trang đăng nhập khởi động · đăng nhập · làm mới phiên lúc tải lại màn hình đích. 10 màn
 * hình × 12 dự án ⇒ ~360 lượt từ MỘT IP, trong khi nginx `api_auth` cho 20 lượt/phút (burst 10) và —
 * trước WS-72 — xô LOGIN của backend (30 lượt/15′ theo IP) còn chặn sớm hơn: ~90/120 bài đỏ vì hạn
 * mức, ⛔ vì giao diện.
 *
 * Nay mỗi dự án đăng nhập một lần rồi đi qua 10 màn hình trong CÙNG một phiên SPA, chuyển màn hình
 * phía client (`pushState` + `popstate` — react-router 7.18.2 `handlePop` gọi thẳng router) để ⛔ tải
 * lại trang, tức ⛔ gọi `/auth/refresh` lần nào. `lichDangNhapQuanTri.test.ts` (vitest, chạy trong CI)
 * canh đúng một lượt đăng nhập và đúng hai `page.goto`.
 *
 * ⚠ Mỗi màn hình phải làm ĐỔI chữ của vùng chính (luật 9): nếu một lượt nâng react-router làm cách
 * chuyển này hết tác dụng thì cả 10 bước đo lại cùng một trang và ĐỀU XANH. `ForbiddenPage` in mã quyền
 * đòi hỏi, nên hai màn hình bị cấm liền nhau vẫn khác chữ.
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

/** Chữ của vùng chính (`main`, ⛔ có thì `body`) — dấu vân tay để biết màn hình đã đổi. */
function vanBanChinh(page: Page): Promise<string> {
  return page.evaluate(() => {
    const goc = document.querySelector('main') ?? document.body;
    return (goc as HTMLElement).innerText.replace(/\s+/g, ' ').trim();
  });
}

/** Chuyển màn hình phía client — ⛔ tải lại trang, nên ⛔ gọi `/auth/refresh`. */
async function chuyenManHinh(page: Page, duong: string): Promise<void> {
  await page.evaluate((d) => {
    const idx = ((window.history.state?.idx as number | undefined) ?? 0) + 1;
    window.history.pushState({ usr: null, key: `e2e${idx}`, idx }, '', d);
    window.dispatchEvent(new PopStateEvent('popstate', { state: window.history.state }));
  }, duong);
}

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

  test(`quản trị — ${MAN_HINH.length} màn hình, một lượt đăng nhập`, async ({ page }) => {
    test.setTimeout(60_000 + MAN_HINH.length * 30_000);
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
    await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => {});

    const nk = ghiLoi(page, goc);
    let vanBanTruoc = await vanBanChinh(page);

    for (const duong of MAN_HINH) {
      await test.step(duong, async () => {
        nk.loiJs.length = 0;
        nk.taiNguyenHong.length = 0;

        if (new URL(page.url()).pathname !== duong) {
          await chuyenManHinh(page, duong);
          // ⛔ Hỏng ở đây là hỏng CÁCH ĐO, ⛔ phải hỏng một màn hình ⇒ dừng hẳn thay vì đo tiếp các
          //    màn hình còn lại mà thật ra là cùng một trang.
          await expect
            .poll(() => vanBanChinh(page), {
              message: `${duong}: vùng chính ⛔ đổi sau popstate — router ⛔ chuyển màn hình`,
              timeout: 15_000,
            })
            .not.toBe(vanBanTruoc);
        }
        await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => {});

        const tran = await doTranNgang(page);
        expect
          .soft(tran.px, `${duong}: tràn ngang ${tran.px}px — ${tran.thoRa.join(' · ')}`)
          .toBeLessThanOrEqual(1);
        expect
          .soft(await doChuHienRa(page), `${duong}: khối chính gần như trống`)
          .toBeGreaterThan(20);
        expect.soft(nk.loiJs, `${duong}: lỗi JS`).toEqual([]);
        expect.soft(nk.taiNguyenHong, `${duong}: tài nguyên cùng nguồn hỏng`).toEqual([]);

        vanBanTruoc = await vanBanChinh(page);
      });
    }
  });
});
