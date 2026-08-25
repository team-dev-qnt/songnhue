import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * **Không route nào đọc API mà được dựng sẵn trong lượt `next build`.**
 *
 * <h2>Lỗi đã có thật — §10.54</h2>
 *
 * `export const revalidate = 300` mà không có gì chặn thì Next dựng trang NGAY TRONG LƯỢT BUILD
 * và ghi HTML kết quả vào image. Lượt build chạy ở CI, nơi backend không tồn tại: `fetch` hỏng,
 * `apiGet` nuốt lỗi và trả `null`, nên thứ được nướng vào image là một trang không có nội dung.
 *
 * Đo được ở máy ngày 25/8, trên đúng cây mã đang chạy staging:
 *
 * <pre>
 *   prerender-manifest: / | /_global-error | /_not-found | /robots.txt | /sitemap.xml
 *   .next/server/app/index.html → 19 liên kết /bai-viet/, KHÔNG cái nào có thật
 *   .next/server/app/sitemap.xml.body → đúng MỘT url, và host là http://localhost:3000
 * </pre>
 *
 * Mỗi container mới phục vụ đúng bản ấy cho tới khi ISR dựng lại xong — tức sau **mỗi** lượt
 * triển khai, mà `--force-recreate` (§10.53) bảo đảm là mỗi lần. Người dùng mở cổng ngay sau
 * lượt deploy thấy một trang chủ không có bài seed nào; lượt đo bằng `curl` vài phút sau thấy
 * đủ 5/5. Hai người nhìn cùng một URL và thấy hai thứ khác nhau.
 *
 * <h2>Bảo đảm đặt ở đâu</h2>
 *
 * Ở `apiGetWithMeta` — chỗ **duy nhất** mọi lượt đọc API đi qua. Đặt ở từng route thì mỗi route
 * mới lại là một dòng phải nhớ, và `sitemap.ts` chứng minh chuyện đó xảy ra thật: nó đọc API
 * bằng một đường không ai để ý tới cho tới khi lượt build in ra `○` (luật 12).
 *
 * <h2>Bài này canh mã nguồn, không canh bản dựng</h2>
 *
 * Câu chính xác nhất — "route nào nằm trong `prerender-manifest`" — chỉ trả lời được sau một
 * lượt `next build` đầy đủ, thứ không chạy ở job test. Nên bài này canh điều kiện *sinh ra* nó,
 * đúng cách `DeployImageProofTest` canh `deploy.yml`. Phép đo thật đã chạy hai chiều và ghi ở
 * §10.54: trước bản vá `/` nằm trong manifest, sau bản vá thì không.
 */

const GOC_APP = join(process.cwd(), 'src/app');

function boChuThich(nguon: string): string {
  return nguon
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .map((dong) => (dong.trimStart().startsWith('//') ? '' : dong))
    .join('\n');
}

function doc(duongTuongDoi: string): string {
  return boChuThich(readFileSync(join(process.cwd(), duongTuongDoi), 'utf8'));
}

function timRoute(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) return timRoute(duong);
    return /\.(ts|tsx)$/.test(ten) && !ten.includes('.test.') ? [duong] : [];
  });
}

const API = doc('src/lib/api.ts');

describe('Không dựng sẵn thứ gì đọc API', () => {
  it('⭐⭐ `apiGetWithMeta` gọi `connection()` TRƯỚC khi mở fetch', () => {
    const viTriConnection = API.indexOf('await connection()');
    const viTriFetch = API.indexOf('await fetch(');

    expect(
      viTriConnection,
      '`src/lib/api.ts` không gọi `await connection()`. Thiếu dòng đó, Next nướng một trang RỖNG ' +
        'vào image ở lượt build (CI không có backend), và mỗi container mới phục vụ đúng bản ' +
        'rỗng ấy cho tới khi ISR dựng lại xong.',
    ).toBeGreaterThan(-1);
    expect(viTriFetch).toBeGreaterThan(-1);
    expect(
      viTriConnection,
      'gọi `connection()` SAU `fetch` thì lượt fetch vẫn chạy trong lúc prerender — đúng thứ ' +
        'cần chặn.',
    ).toBeLessThan(viTriFetch);
  });

  it('⭐ nhập `connection` từ `next/server`', () => {
    expect(API).toMatch(/import \{ connection \} from 'next\/server'/);
  });

  it('⛔ không route nào của cổng dùng `force-dynamic`', () => {
    // `force-dynamic` hạ mặc định fetch xuống no-store, nên backend phải trả lời MỌI lượt truy
    // cập thay vì 1 lần / 5 phút. `connection()` ở `apiGetWithMeta` cho kết quả tương đương mà
    // giữ nguyên cache.
    //
    // ⚠ `sitemap.ts` từng phải khai riêng `force-dynamic` — đó là lúc `connection()` còn nằm ở
    //   `page.tsx`. Sau khi chuyển bảo đảm về chokepoint thì ngoại lệ ấy tự tiêu: đo lại bằng
    //   `next build`, sitemap vẫn ra `ƒ` khi đã gỡ công tắc. Một luật không có trường hợp riêng
    //   là một luật không ai phải nhớ.
    const pham = timRoute(GOC_APP)
      .filter((duong) => /export const dynamic\s*=\s*'force-dynamic'/.test(boChuThich(readFileSync(duong, 'utf8'))))
      .map((duong) => duong.slice(GOC_APP.length + 1))
      // `src/app/api/**` là Route Handler của chính Next (`/api/health`, `/api/revalidate`,
      // proxy `/api/v1/*`). Chúng PHẢI dynamic và không đọc qua `apiGet`; luật này nói về
      // trang của cổng, không nói về chúng.
      .filter((ten) => !ten.startsWith('api/'))

    expect(pham, `những route này dùng force-dynamic: ${pham.join(', ')}`).toEqual([]);
  });

  it('⛔ không route nào khai `generateStaticParams` — nó kéo lượt đọc API về lúc build', () => {
    const pham = timRoute(GOC_APP)
      .filter((duong) => boChuThich(readFileSync(duong, 'utf8')).includes('generateStaticParams'))
      .map((duong) => duong.slice(GOC_APP.length + 1));
    expect(pham, `những route này sẽ dựng sẵn lúc build: ${pham.join(', ')}`).toEqual([]);
  });

  it('⚠ quét được route để soi — bài chạy qua tập rỗng thì xanh mà không canh gì (luật 7)', () => {
    const route = timRoute(GOC_APP);
    expect(route.length).toBeGreaterThan(5);
    expect(route.map((d) => d.slice(GOC_APP.length + 1))).toContain('sitemap.ts');
  });

  it('⛔ kiểm chứng ngược: các phép trên bắt được khi dòng thật bị gỡ', () => {
    const goCall = API.replace('await connection();', '');
    const goImport = API.replace("import { connection } from 'next/server';", '');
    expect(goCall).not.toContain('await connection()');
    expect(goImport).not.toMatch(/import \{ connection \} from 'next\/server'/);
    // Và bản THẬT phải khác cả hai — nếu không thì đang so rỗng với rỗng.
    expect(API).not.toEqual(goCall);
    expect(API).not.toEqual(goImport);
  });
});
