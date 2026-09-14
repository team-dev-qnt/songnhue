import { expect, test } from '@playwright/test';

import { doChuHienRa, doTranNgang, ghiLoi } from './doKiem';

/**
 * Tự kiểm bộ đo — chạy trên CẢ 12 dự án, ⛔ cần stack (`setContent`). Mỗi bộ đo phải ĐỎ trên trang hỏng
 * và XANH trên trang lành; thiếu một vế là bộ đo ⛔ đo gì (luật 1, luật 9).
 */

test('⭐ tràn ngang: phần tử rộng hơn khung ⇒ px > 0 và gọi tên; bảng cuộn TRONG khung ⇒ 0', async ({
  page,
}) => {
  await page.setContent(
    `<body style="margin:0"><main><div id="thoRa" style="width:4000px;height:10px"></div></main></body>`,
  );
  const hong = await doTranNgang(page);
  expect(hong.px).toBeGreaterThan(100);
  expect(hong.thoRa.join(' ')).toContain('div#thoRa');

  await page.setContent(
    `<body style="margin:0"><main><div style="overflow-x:auto"><table style="width:4000px"><tr><td>x</td></tr></table></div></main></body>`,
  );
  expect((await doTranNgang(page)).px, 'bảng cuộn trong khung của nó ⛔ phải lỗi').toBe(0);
});

test('⭐ lỗi JS: ném trong script ⇒ ghi lại; trang sạch ⇒ rỗng', async ({ page }) => {
  const nk = ghiLoi(page, 'http://tu-kiem.invalid');
  await page.setContent(
    `<body><main>ok</main><script>throw new Error("hỏng-có-chủ-đích")</script></body>`,
  );
  await expect.poll(() => nk.loiJs.length).toBeGreaterThan(0);
  expect(nk.loiJs.join()).toContain('hỏng-có-chủ-đích');

  const page2 = await page.context().newPage();
  const nk2 = ghiLoi(page2, 'http://tu-kiem.invalid');
  await page2.setContent(`<body><main>ok</main><script>1 + 1</script></body>`);
  expect(nk2.loiJs).toEqual([]);
});

test('⭐ chữ hiện ra: main rỗng ⇒ 0; main có nội dung ⇒ > 0', async ({ page }) => {
  await page.setContent(`<body><main>   </main></body>`);
  expect(await doChuHienRa(page)).toBe(0);
  await page.setContent(`<body><main><h1>Công ty Thủy lợi Sông Nhuệ</h1></main></body>`);
  expect(await doChuHienRa(page)).toBeGreaterThan(10);
});
