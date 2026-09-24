import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { afterEach, describe, expect, it, vi } from 'vitest';

/**
 * ⚠ `connection()` NÉM khi gọi ngoài phạm vi một request ("called outside a request scope") — đó
 * chính là cơ chế làm nó chặn được lượt prerender. Nên bài hành vi bên dưới buộc phải giả lập nó.
 *
 * ⭐ Và lượt giả lập ấy MẠNH HƠN một phép quét mã nguồn: nó khẳng định `robots()` **thực sự gọi**
 * công tắc, ⛔ phải "tệp có chứa chuỗi ấy ở đâu đó". Hai vế giữ hai đầu khác nhau — quét mã bắt
 * lượt đổi sang `force-dynamic`, còn spy bắt lượt ai đó gỡ lời gọi mà quên gỡ import.
 */
const connectionGia = vi.hoisted(() => vi.fn(() => Promise.resolve()));
vi.mock('next/server', () => ({ connection: connectionGia }));

/**
 * `robots.txt` phải nói ĐÚNG về môi trường đang chạy — T68.12.
 *
 * <h3>Khuyết tật đã đo được, 19/09/2026</h3>
 *
 * `staging.songnhue.com/robots.txt` khai `Allow: /` kèm `Host: https://thuyloisongnhue.vn`;
 * canonical và og:url của trang chủ staging cũng trỏ về production. Nghĩa là nhánh *"chặn lập
 * chỉ mục ở staging"* ngay trong `robots.ts` — viết từ T9.3 — **chưa bao giờ chạy** (luật 7).
 * Thứ duy nhất còn che là header `x-robots-tag` đặt ở nginx biên; gỡ nó ra là Google có hai bản
 * của cùng nội dung và có thể xếp bản staging lên trước.
 *
 * <h3>⛔⛔ Nguyên nhân có HAI tầng, và tầng thứ hai là tầng dễ quên</h3>
 *
 * 1. `NEXT_PUBLIC_SITE_URL` bị Next thay bằng **chuỗi hằng lúc build**, mà staging và
 *    production dùng **chung một ảnh Docker** ⇒ chúng buộc phải mang chung giá trị.
 * 2. Và ngay cả khi đọc lúc chạy, `/robots.txt` vẫn sai: đo bằng `next build` ngày 24/09, trong
 *    20 route của cổng nó là **route DUY NHẤT** Next prerender tĩnh (`○`) — 18 route kia và cả
 *    `/sitemap.xml` đều `ƒ` vì chúng `await` dữ liệu. Hàm `robots()` chỉ đọc một biến nên Next
 *    kết luận kết quả bất biến và **nướng tệp vào ảnh**.
 *
 * ⇒ Vá một tầng thôi thì mọi thứ *trông như* đã xong — sitemap đúng, canonical đúng — trong khi
 * **đúng tệp mà dòng nợ tố cáo** vẫn sai, và ⛔ cổng kiểm nào báo. Bài này neo vào **cả hai**.
 *
 * <h3>Vì sao có vế "⛔ thành phần máy khách nào nhập SITE_URL"</h3>
 *
 * Bỏ tiền tố `NEXT_PUBLIC_` nghĩa là biến ⛔ còn đi xuống trình duyệt. Một thành phần
 * `'use client'` nhập `SITE_URL` sẽ nhận giá trị dự phòng `http://localhost:3000` **trong im
 * lặng** — ⛔ lỗi biên dịch, ⛔ cảnh báo, chỉ là một liên kết sai. Kho ⛔ có gói `server-only`
 * để biến việc ấy thành lỗi biên dịch, nên phạm vi phải do một bộ canh **ĐO** (luật 28).
 */

const GOC = process.cwd();
const NGUON_ROBOTS = readFileSync(join(GOC, 'src/app/robots.ts'), 'utf8');

/** Mọi tệp `.ts`/`.tsx` dưới `src/`, đo từ đĩa — ⛔ một danh sách gõ tay (luật 28). */
function moiTepNguon(thuMuc: string, gom: string[] = []): string[] {
  for (const ten of readdirSync(thuMuc)) {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) moiTepNguon(duong, gom);
    else if (/\.tsx?$/.test(ten) && !/\.test\.tsx?$/.test(ten)) gom.push(duong);
  }
  return gom;
}

describe('robots.txt đọc môi trường LÚC CHẠY', () => {
  afterEach(() => {
    vi.resetModules();
  });

  it('⛔⛔ gọi `await connection()` — thiếu nó là tệp bị nướng vào ảnh lúc build', () => {
    // Vế CẤU TRÚC, và nó ⛔ thay thế được bằng vế hành vi bên dưới: gọi thẳng `robots()` trong
    // bộ kiểm thì LÚC NÀO cũng chạy lúc chạy, nên nó xanh cả trên bản bị prerender. Hai bài
    // hỏi hai câu khác nhau (luật 9).
    expect(NGUON_ROBOTS).toMatch(/await connection\(\)/);
    expect(NGUON_ROBOTS).toMatch(/import \{ connection \} from 'next\/server'/);
    // ⛔⛔ Và PHẢI ⛔ phải `force-dynamic`: `noBuildTimePrerender.test.ts` cấm nó trên mọi route
    //    của cổng vì nó hạ mặc định fetch xuống `no-store` ⇒ backend phải trả lời MỌI lượt truy
    //    cập thay vì 1 lần / 5 phút. Khẳng định ngược này giữ cho bản vá ⛔ trôi về cách cũ ở một
    //    lượt sửa sau — lúc ấy bài kia sẽ đỏ, nhưng đỏ ở một tệp khác và nói một câu khác.
    expect(NGUON_ROBOTS).not.toMatch(/export const dynamic\s*=\s*'force-dynamic'/);
  });

  it('⛔ đọc biến có tiền tố NEXT_PUBLIC_ — biến ấy bị nướng vào bundle lúc build', () => {
    const nguonSite = readFileSync(join(GOC, 'src/lib/site.ts'), 'utf8');
    const dongSiteUrl = nguonSite
      .split('\n')
      .filter((d) => d.includes('export const SITE_URL'))
      .join('\n');
    expect(dongSiteUrl).toContain('process.env.SITE_URL ||');
    expect(dongSiteUrl).not.toContain('NEXT_PUBLIC_SITE_URL');
    // `??` ⛔ đỡ được chuỗi rỗng mà Docker/compose gán khi biến ⛔ truyền (luật 3, §10.38).
    expect(dongSiteUrl).not.toContain('??');
  });

  it('⛔ thành phần máy khách nào nhập SITE_URL — nó sẽ nhận localhost trong im lặng', () => {
    const tep = moiTepNguon(join(GOC, 'src'));
    // Tiền đề (luật 7): quét trên tập RỖNG thì bài này xanh mà ⛔ đọc gì.
    expect(tep.length).toBeGreaterThan(20);

    const viPham = tep.filter((duong) => {
      const ma = readFileSync(duong, 'utf8');
      const laMayKhach = /^\s*['"]use client['"]/m.test(ma);
      const nhapSiteUrl = /import\s*\{[^}]*\bSITE_URL\b[^}]*\}\s*from\s*['"]@\/lib\/site['"]/.test(
        ma,
      );
      return laMayKhach && nhapSiteUrl;
    });
    expect(viPham, `thành phần máy khách nhập SITE_URL: ${viPham.join(', ')}`).toEqual([]);
  });

  // ⭐ Vế HÀNH VI — nó trả lời câu *"nhánh chặn staging có chạy ⛔"*, thứ mà ba vế cấu trúc
  //   trên ⛔ nói gì về. Đây chính là nhánh đã nằm im từ T9.3 tới 19/09 mà ⛔ ai biết.
  it.each([
    ['https://staging.songnhue.com', true],
    ['http://localhost:3000', true],
    ['https://thuyloisongnhue.vn', false],
  ])('%s ⇒ chặn lập chỉ mục = %s', async (diaChi, phaiChan) => {
    process.env.SITE_URL = diaChi;
    vi.resetModules();
    connectionGia.mockClear();
    const { default: robots } = await import('@/app/robots');
    const ket = await robots();

    // Vế mạnh nhất của bài này: công tắc chống-prerender phải ĐƯỢC GỌI, ⛔ chỉ có mặt trong tệp.
    expect(
      connectionGia,
      '`robots()` ⛔ gọi `connection()` ⇒ Next sẽ prerender tệp này vào ảnh lúc build, và ' +
        '`SITE_URL` đọc lúc chạy ⛔ cứu được gì (T68.12).',
    ).toHaveBeenCalled();
    const luat = Array.isArray(ket.rules) ? ket.rules[0] : ket.rules;

    if (phaiChan) {
      expect(luat.disallow).toBe('/');
      expect(ket.host).toBeUndefined();
    } else {
      expect(luat.allow).toBe('/');
      expect(ket.host).toBe(diaChi);
    }
  });
});
