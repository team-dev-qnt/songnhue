import { expect, test, type Page } from '@playwright/test';

import { brandColors, statusColors } from 'design-tokens';

/**
 * Biểu đồ diễn biến §7.1 — đo **PIXEL ĐÃ VẼ**, ⛔ không đọc chuỗi class và ⛔ không hỏi "có hiển
 * thị ⛔ không".
 *
 * <h2>⛔⛔ Vì sao bộ này phải tồn tại — 11 bài đơn vị ⛔ KHÔNG thấy được thứ nó thấy</h2>
 *
 * `bieuDoDienBien.test.ts` kiểm hai hàm thuần (điểm nào vào đường chính) và canh văn bản nguồn
 * (`connectNulls: false` có mặt, `MarkLineComponent` đã đăng ký). Cả hai đều <b>đúng</b> và cả hai
 * đều <b>mù</b> trước câu hỏi thật: <i>ECharts có vẽ ra hai đường ⛔ không</i>.
 *
 * <p>Ba cách biểu đồ hỏng mà mọi bài đơn vị vẫn xanh trọn vẹn:
 *
 * <ul>
 *   <li>một chuỗi bị đặt sai {@code stack} ⇒ dải chồng nuốt luôn đường cong — mã ⛔ không đổi một
 *       ký tự nào so với bản đúng;
 *   <li>{@code echarts.init} ném lúc chạy (theme chưa đăng ký, container cao 0) ⇒ khung trắng;
 *   <li>chuỗi toàn {@code null} ⇒ trục vẽ đủ, đường ⛔ không có gì — đúng thứ §7.3 cấm.
 * </ul>
 *
 * <h2>⚠ TIỀN ĐỀ: bộ này cần stack ĐANG CHẠY và CÓ SỐ ĐO</h2>
 *
 * `playwright.config.ts` đã ghi vì sao ⛔ không khai {@code webServer}. Thêm một tiền đề nữa ở
 * đây: một công trình phải có <b>số đo trong cửa sổ đang xem</b>, nếu ⛔ không thì biểu đồ đúng
 * mà vẫn ⛔ không có đường nào để đo.
 *
 * <p>⛔ Tiền đề ấy được kiểm <b>tường minh và ĐỎ khi ⛔ không đạt</b>, ⛔ không phải `test.skip`.
 * Một bộ đo tự bỏ qua chính mình khi thiếu dữ liệu là một bộ đo xanh trong đúng tình huống nó sinh
 * ra để bắt (§11.19) — và ⛔ không ai đọc dòng "skipped" trong một lượt chạy 30 phép đo.
 */

/** Bề rộng đo: một desktop và một điện thoại — §7.3 đòi biểu đồ co giãn theo khung. */
const BE_RONG = [
  { ten: 'laptop phổ thông', width: 1366, height: 900 },
  { ten: 'điện thoại', width: 375, height: 812 },
] as const;

type ThongKeMau = { tongDiem: number; soMauKhacNhau: number; doDam: number; xanhDam: number };

/**
 * Đếm pixel theo NHÓM MÀU trong `<canvas>` của biểu đồ.
 *
 * <p>⛔ Phân biệt <b>đường</b> với <b>dải nền</b> bằng độ bão hoà, ⛔ không bằng "có màu đỏ ⛔
 * không": dải chênh lệch cũng là đỏ/xanh nhưng ở opacity 15% trên nền trắng, tức một sắc RẤT nhạt
 * (≈ 253,222,223). Đường thì bão hoà ({@code #f5222d} ≈ 245,34,45). Nếu chỉ hỏi "có điểm đỏ nào ⛔
 * không" thì một biểu đồ <b>mất hẳn đường cong</b> mà còn dải vẫn đi lọt.
 */
async function demMau(page: Page, mauDo: string, mauXanh: string): Promise<ThongKeMau | null> {
  return page.evaluate(
    ({ mauDo, mauXanh }) => {
      const canvas = document.querySelector('canvas');
      if (!canvas) return null;
      const ctx = (canvas as HTMLCanvasElement).getContext('2d');
      if (!ctx) return null;
      const { width, height } = canvas as HTMLCanvasElement;
      if (width === 0 || height === 0) return null;

      const rgb = (hex: string) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
      const [dr, dg, db] = rgb(mauDo);
      const [xr, xg, xb] = rgb(mauXanh);
      // ⛔ Ngưỡng CHẶT quanh ĐÚNG màu của token, ⛔ không phải "kênh nào trội". Lượt đo đầu dùng
      //    kiểu trội-kênh và nó đếm nhầm một sắc xanh-tím (101,120,186) của bảng chủ đề thành
      //    đường hạ lưu — bài kiểm XANH trên mã đã bỏ hẳn đường ấy (luật 9).
      const gan = (r: number, g: number, b: number, t: number[]) =>
        Math.abs(r - t[0]) < 40 && Math.abs(g - t[1]) < 40 && Math.abs(b - t[2]) < 40;

      const d = ctx.getImageData(0, 0, width, height).data;
      const mau = new Set<string>();
      let doDam = 0;
      let xanhDam = 0;
      for (let i = 0; i < d.length; i += 4) {
        const r = d[i];
        const g = d[i + 1];
        const b = d[i + 2];
        if (d[i + 3] < 128) continue;
        mau.add(`${r >> 3},${g >> 3},${b >> 3}`);
        if (gan(r, g, b, [dr, dg, db])) doDam++;
        if (gan(r, g, b, [xr, xg, xb])) xanhDam++;
      }
      return { tongDiem: (width * height) | 0, soMauKhacNhau: mau.size, doDam, xanhDam };
    },
    { mauDo, mauXanh },
  );
}

/** Mã công trình có đủ cặp thượng lưu + hạ lưu — 5/14 công trình thoả, xem V202609091074. */
const MA_CONG_TRINH = 'LMAC';

test.describe('Biểu đồ diễn biến §7.1', () => {
  test('⚠ TIỀN ĐỀ — công trình đo thử phải CÓ số đo, nếu ⛔ không mọi phép đo dưới đây vô nghĩa', async ({
    request,
  }) => {
    const ra = await request.get(
      `/api/v1/public/hydro/bieu-do/${MA_CONG_TRINH}?cheDo=PHUT&soCot=144`,
    );
    expect(ra.status(), 'endpoint biểu đồ phải trả 200 cho khách vô danh').toBe(200);

    const than = await ra.json();
    const ct = than?.data?.congTrinh ?? than?.congTrinh;
    expect(ct, `⛔ ⛔ Không tìm thấy công trình ${MA_CONG_TRINH} — seed chưa chạy?`).toBeTruthy();

    const coSo = (ct.dong ?? []).some((d: { o: { giaTri: string | null }[] }) =>
      d.o.some((o) => o.giaTri !== null),
    );
    expect(
      coSo,
      `⛔ ⛔ Công trình ${MA_CONG_TRINH} ⛔ không có số đo nào trong 144 mốc gần nhất. Bộ đo này ` +
        'CẦN dữ liệu — chạy poller hoặc chèn vài bản ghi `hydro_readings` trước. ⛔ Đừng nới ' +
        'khẳng định cho hết đỏ: đỏ ở đây nghĩa là phép đo ⛔ không có gì để đo.',
    ).toBe(true);
  });

  for (const br of BE_RONG) {
    test(`⭐⭐ ${br.ten} — canvas VẼ THẬT, và có ĐỦ hai đường bão hoà đỏ + xanh`, async ({
      page,
    }) => {
      await page.setViewportSize({ width: br.width, height: br.height });
      await page.goto(`/quan-ly-van-hanh/muc-nuoc-luong-mua/${MA_CONG_TRINH}`);

      // ⛔ Chờ chính `<canvas>` chứ ⛔ không chờ một khoảng thời gian cố định: biểu đồ nạp bằng
      //    `dynamic(ssr:false)` nên nó tới SAU lượt dựng đầu, và một `waitForTimeout` là một cuộc
      //    đua sẽ đỏ ngẫu nhiên trên máy chậm.
      await page.locator('canvas').first().waitFor({ state: 'visible' });

      const tk = await demMau(page, statusColors.danger, brandColors.info);
      expect(tk, '⛔ ⛔ Không tìm thấy `<canvas>` nào — biểu đồ ⛔ không được dựng').not.toBeNull();

      expect(
        tk!.soMauKhacNhau,
        '⛔ Canvas gần như một màu ⇒ khung trắng, ⛔ không phải một biểu đồ. Đây là nhánh ' +
          '`echarts.init` ném lúc chạy mà mọi bài đơn vị ⛔ không thấy.',
      ).toBeGreaterThan(10);

      expect(
        tk!.doDam,
        '⛔ ⛔ ⛔ Không có điểm ĐỎ BÃO HOÀ nào ⇒ đường THƯỢNG LƯU ⛔ không được vẽ. ⚠ Dải chênh ' +
          'lệch cũng đỏ nhưng nhạt (15% trên trắng) nên nó ⛔ không lọt qua ngưỡng này — đó là ' +
          'chủ đích của phép đo.',
      ).toBeGreaterThan(20);

      expect(
        tk!.xanhDam,
        '⛔ ⛔ Không có điểm XANH BÃO HOÀ nào ⇒ đường HẠ LƯU ⛔ không được vẽ.',
      ).toBeGreaterThan(20);
    });
  }

  test('⭐ §7.3 — mã công trình lạ hiện CÂU CHỮ, ⛔ KHÔNG vẽ một khung trục rỗng', async ({
    page,
  }) => {
    await page.goto('/quan-ly-van-hanh/muc-nuoc-luong-mua/KHONG-CO-THAT');

    await expect(
      page.getByText('Không tìm thấy công trình', { exact: false }),
      'Backend ép "hoặc CÓ công trình, hoặc CÓ lý do" ở hàm dựng — câu ấy phải tới được người đọc',
    ).toBeVisible();

    // ⛔ Vế PHÂN BIỆT: một khung trục rỗng trông y hệt một biểu đồ mà mọi giá trị bằng 0. Khẳng
    //    định "có câu chữ" một mình ⛔ không loại được nhánh ấy — biểu đồ vẫn có thể vẽ bên dưới.
    await expect(
      page.locator('canvas'),
      '⛔ §7.3: "⛔ không có dữ liệu → hiển thị câu chữ, ⛔ KHÔNG vẽ biểu đồ trống"',
    ).toHaveCount(0);
  });

  test('⭐ Nút »» của §6.1.2 dẫn tới ĐÚNG trang chi tiết của công trình ấy', async ({ page }) => {
    await page.goto('/quan-ly-van-hanh/muc-nuoc-luong-mua');
    const nut = page.getByRole('link', { name: /Xem biểu đồ diễn biến/ }).first();
    await expect(nut, '⚠ Vế chống tập rỗng: bảng phải có ít nhất một nút »»').toBeVisible();

    const nhan = (await nut.getAttribute('aria-label')) ?? '';
    const tenCongTrinh = nhan.replace('Xem biểu đồ diễn biến ', '').trim();
    expect(tenCongTrinh.length, 'nhãn a11y phải mang TÊN công trình').toBeGreaterThan(0);

    await nut.click();
    await expect(
      page.getByRole('heading', { name: new RegExp(tenCongTrinh.slice(0, 12)) }).first(),
      '⛔ Bấm »» ở dòng công trình A mà mở ra công trình B là lỗi ⛔ không ai nhìn ra bằng mắt',
    ).toBeVisible();
  });
});
