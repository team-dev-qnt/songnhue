import { expect, test, type Page } from '@playwright/test';

/**
 * NFR-02a · `DOD4.5` — *"trang chủ < 3s đo từ máy ở Việt Nam, gồm cả lượt ISR nguội"*.
 *
 * Bộ này đo **LCP** (Largest Contentful Paint): mốc trình duyệt vẽ xong khối nội dung lớn nhất —
 * thứ gần nhất với câu *"người dùng thấy trang"*. Nó ⛔ thay được `http_req_duration` của k6 mà
 * bù vào đúng chỗ con số ấy mù: nạp JS, hydrate, vẽ.
 *
 * ⚠ Hai trạng thái phải đo RIÊNG, vì chúng lệch nhau ở chặng đắt nhất:
 *   · **ẤM** — trang đã có trong đệm ISR, máy chủ trả bản dựng sẵn.
 *   · **NGUỘI** — đệm vừa bị xoá, lượt truy cập kế tiếp phải **dựng lại trang** (gọi backend, vẽ
 *     HTML). Đây là lượt người dùng đầu tiên sau mỗi lượt deploy hoặc mỗi lượt biên tập viên duyệt
 *     bài — tức là một trạng thái **thường xuyên**, ⛔ phải một ca hiếm.
 *
 * ⛔⛔ Đo mỗi lượt ẤM rồi ghi vào sổ là ghi một con số cho trạng thái DỄ NHẤT. `DOD4.5` viết
 * *"gồm cả lượt ISR nguội"* chính vì chuyện ấy, nên ở đây **⛔ có nhánh nào lặng lẽ bỏ vế nguội**:
 * thiếu `REVALIDATE_SECRET` là một dòng ĐỎ gọi đúng tên thứ còn thiếu, ⛔ phải một lượt bỏ qua im
 * lặng (luật 7 — một phép kiểm đi qua tập rỗng vẫn xanh trọn vẹn).
 */
const NGUONG_MS = Number(process.env.NGUONG_LCP_MS ?? 3000);
const BI_MAT = process.env.REVALIDATE_SECRET ?? '';
const BO_QUA_NGUOI = process.env.BO_QUA_ISR_NGUOI === '1';

/**
 * Chờ LCP lắng. `buffered: true` lấy cả entry phát ra TRƯỚC khi quan sát viên được tạo, nên mốc
 * này chỉ để đón một khối vẽ muộn (ảnh tải chậm). Nó là một **lựa chọn có giá**: dài hơn thì bắt
 * được nhiều lượt vẽ muộn hơn, ngắn hơn thì đo thiếu.
 */
const CHO_LCP_MS = 1000;

interface SoDo {
  /** `null` = trình duyệt ⛔ phát entry LCP nào — ⛔ đọc nó thành "nhanh". */
  lcpMs: number | null;
  ttfbMs: number;
  taiXongMs: number;
  dauCache: string | null;
  byte: number;
}

/** Thu LCP của trang ĐANG mở. Tách riêng để bài tự kiểm gọi được trên `about:blank`. */
async function thuLcp(page: Page): Promise<number | null> {
  return page.evaluate(
    (cho) =>
      new Promise<number | null>((xong) => {
        let lon: number | null = null;
        let bo: PerformanceObserver;
        try {
          bo = new PerformanceObserver((ds) => {
            for (const e of ds.getEntries()) {
              lon = e.startTime;
            }
          });
          bo.observe({ type: 'largest-contentful-paint', buffered: true });
        } catch {
          // Trình duyệt ⛔ biết loại entry này ⇒ trả `null`, ⛔ trả 0: hai trạng thái ấy
          // phải phân biệt được (luật 9).
          xong(null);
          return;
        }
        setTimeout(() => {
          bo.disconnect();
          xong(lon);
        }, cho);
      }),
    CHO_LCP_MS,
  );
}

async function doMotLuot(page: Page, duong: string): Promise<SoDo> {
  const phanHoi = await page.goto(duong, { waitUntil: 'load' });
  if (!phanHoi) {
    throw new Error(`⛔ nhận được phản hồi nào cho ${duong}`);
  }
  const than = await phanHoi.body();
  const dau = phanHoi.headers();
  const lcpMs = await thuLcp(page);
  const moc = await page.evaluate(() => {
    const n = performance.getEntriesByType('navigation')[0] as
      PerformanceNavigationTiming | undefined;
    return { ttfb: n?.responseStart ?? 0, taiXong: n?.loadEventEnd ?? 0 };
  });
  return {
    lcpMs,
    ttfbMs: Math.round(moc.ttfb),
    taiXongMs: Math.round(moc.taiXong),
    // Next đặt đầu này cho Full Route Cache. ⛔ có nó thì ⛔ kết luận gì về trạng thái đệm —
    // vì vậy bằng chứng "nguội" của bài dưới là lượt `POST /api/revalidate` trả 200, ⛔ phải
    // đầu phản hồi này.
    dauCache: dau['x-nextjs-cache'] ?? null,
    byte: than.length,
  };
}

function inRa(nhan: string, s: SoDo): void {
  console.log(
    `[${nhan}] LCP=${s.lcpMs === null ? '⛔ đo được' : `${Math.round(s.lcpMs)}ms`} · ` +
      `TTFB=${s.ttfbMs}ms · tải xong=${s.taiXongMs}ms · ` +
      `x-nextjs-cache=${s.dauCache ?? '⛔ có'} · ${s.byte} byte`,
  );
}

function khangDinh(nhan: string, s: SoDo): void {
  expect(
    s.byte,
    `[${nhan}] thân trang chủ quá nhỏ (${s.byte} byte) — một trang LỖI cũng trả NHANH`,
  ).toBeGreaterThan(20_000);
  expect(
    s.lcpMs,
    `[${nhan}] ⛔ có entry LCP nào. ⛔ đọc trạng thái này thành "nhanh": nó nghĩa là ⛔ đo được, ` +
      'hoặc trang ⛔ vẽ ra khối nội dung nào.',
  ).not.toBeNull();
  expect(s.lcpMs as number, `[${nhan}] LCP vượt ngưỡng NFR-02a`).toBeLessThan(NGUONG_MS);
}

test.describe(`NFR-02a · DOD4.5 — trang chủ dưới ${NGUONG_MS}ms`, () => {
  test('lượt ẤM — đệm ISR đang nóng', async ({ page }) => {
    // Lượt đầu chỉ để làm nóng; con số lấy ở lượt thứ hai.
    await doMotLuot(page, '/');
    const so = await doMotLuot(page, '/');
    inRa('ẤM', so);
    khangDinh('ẤM', so);
  });

  test('lượt NGUỘI — ngay sau khi xoá đệm ISR', async ({ page, request }) => {
    if (BO_QUA_NGUOI) {
      test.skip(
        true,
        'BO_QUA_ISR_NGUOI=1 — vế NGUỘI ⛔ được đo trong lượt này, nên lượt này ⛔ đóng được DOD4.5.',
      );
    }
    expect(
      BI_MAT,
      'Thiếu REVALIDATE_SECRET. `DOD4.5` đòi đo CẢ lượt ISR nguội, và đường duy nhất tạo ra trạng ' +
        'thái ấy theo yêu cầu là POST /api/revalidate. Đặt bí mật của môi trường đang đo, hoặc khai ' +
        'BO_QUA_ISR_NGUOI=1 để nói rõ rằng lượt này ⛔ đo vế nguội.',
    ).not.toBe('');

    const xoaDem = await request.post('/api/revalidate', {
      headers: { 'x-revalidate-secret': BI_MAT },
      data: { path: '/' },
    });
    expect(
      xoaDem.status(),
      'Xoá đệm ISR ⛔ thành công — lượt đo sau đây sẽ ⛔ phải một lượt NGUỘI',
    ).toBe(200);
    expect((await xoaDem.json()).revalidated, 'Máy chủ ⛔ xác nhận đã xoá đệm').toBe(true);

    const so = await doMotLuot(page, '/');
    inRa('NGUỘI', so);
    khangDinh('NGUỘI', so);
  });

  test('tự kiểm — bộ thu LCP phải trả `null` khi ⛔ có gì được vẽ', async ({ page }) => {
    // Vế ĐỐI CHỨNG (luật 9): ⛔ có nó thì một bộ thu luôn trả một con số nào đó cũng "đạt" cả hai
    // bài trên. `about:blank` ⛔ vẽ khối nội dung nào ⇒ ⛔ có entry `largest-contentful-paint`.
    await page.goto('about:blank');
    const lcp = await thuLcp(page);
    expect(
      lcp,
      'Trang trắng mà vẫn ra một con số LCP ⇒ bộ thu đang bịa, và hai bài trên vô nghĩa',
    ).toBeNull();
  });
});
