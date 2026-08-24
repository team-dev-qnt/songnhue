import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { afterEach, describe, expect, it, vi } from 'vitest';

/**
 * Canh cho mọi hằng số cấu hình của cổng sống sót khi biến môi trường **rỗng**.
 *
 * <h3>Chuyện đã xảy ra</h3>
 *
 * `Dockerfile` khai `ARG NEXT_PUBLIC_SITE_URL` không có giá trị mặc định, rồi `ENV
 * NEXT_PUBLIC_SITE_URL=$NEXT_PUBLIC_SITE_URL`. Khi CI không truyền build-arg (biến kho
 * `PUBLIC_SITE_URL` chưa đặt), Docker không bỏ trống biến — nó gán vào **chuỗi rỗng**.
 * `site.ts` khi đó dùng `??`, mà chuỗi rỗng không nullish, nên giá trị mặc định không bao
 * giờ chạm tới; `new URL('')` trong `layout.tsx` ném `ERR_INVALID_URL` giữa lúc prerender
 * và giết cả lượt `next build`.
 *
 * Đây đúng dạng "canh giá trị ĐÃ GIẢI, đừng canh giá trị MẶC ĐỊNH" (CLAUDE.md luật 3):
 * chính lượt CI hỏng ấy còn in ra cảnh báo *"chưa đặt PUBLIC_SITE_URL → sitemap sẽ trỏ về
 * localhost"* — tức là nó tin có một mặc định đang đỡ phía dưới, trong khi mặc định ấy chưa
 * bao giờ được dùng tới.
 *
 * <h3>Vì sao kiểm hành vi chứ không grep `??`</h3>
 *
 * Grep toán tử là canh **văn bản**; đổi sang `String(x ?? '') || y` là lách được mà vẫn
 * xanh. Ở đây nạp thật module với biến rỗng rồi kiểm chính bất biến mà `layout.tsx`,
 * `sitemap.ts` và `apiGet` dựa vào.
 */

/** Bất biến của từng hằng số — khớp đúng thứ nơi gọi thật sự cần. */
const HANG_SO = [
  {
    bien: 'NEXT_PUBLIC_SITE_URL',
    xuat: 'SITE_URL',
    // `layout.tsx` gọi `new URL(SITE_URL)`; `sitemap.ts`/`robots.ts` nối đường dẫn vào đây.
    phaiTuyetDoi: true,
  },
  {
    bien: 'NEXT_PUBLIC_API_BASE_URL',
    xuat: 'API_BASE_URL',
    // Cố ý tương đối (cùng origin, đi qua `rewrites()`) — chỉ cấm rỗng.
    phaiTuyetDoi: false,
  },
  {
    bien: 'API_INTERNAL_BASE_URL',
    xuat: 'API_INTERNAL_BASE_URL',
    // `fetch` phía máy chủ Next không có gốc để nối, nên bắt buộc tuyệt đối.
    phaiTuyetDoi: true,
  },
] as const;

const NGUON = readFileSync(join(process.cwd(), 'src/lib/site.ts'), 'utf8');

async function napLai(moiTruong: Record<string, string | undefined>) {
  for (const [khoa, giaTri] of Object.entries(moiTruong)) {
    if (giaTri === undefined) delete process.env[khoa];
    else process.env[khoa] = giaTri;
  }
  vi.resetModules();
  return (await import('@/lib/site')) as unknown as Record<string, string>;
}

describe('hằng số cấu hình cổng sống sót khi biến môi trường rỗng', () => {
  const banGoc = Object.fromEntries(HANG_SO.map(({ bien }) => [bien, process.env[bien]]));

  afterEach(() => {
    for (const [khoa, giaTri] of Object.entries(banGoc)) {
      if (giaTri === undefined) delete process.env[khoa];
      else process.env[khoa] = giaTri;
    }
    vi.resetModules();
  });

  // Hai trạng thái này KHÁC NHAU và đó là toàn bộ nguyên nhân của vụ hỏng build:
  // Docker gán chuỗi rỗng, còn `??` chỉ đỡ được trạng thái "chưa đặt".
  const trangThai = [
    { ten: 'gán chuỗi rỗng (Docker ARG không truyền)', giaTri: '' },
    { ten: 'không đặt', giaTri: undefined },
  ] as const;

  for (const { ten, giaTri } of trangThai) {
    describe(ten, () => {
      it.each(HANG_SO)('$xuat không rỗng', async ({ bien, xuat }) => {
        const mod = await napLai({ [bien]: giaTri });
        expect(mod[xuat]).toBeTruthy();
      });

      it.each(HANG_SO.filter((h) => h.phaiTuyetDoi))(
        '$xuat là URL tuyệt đối hợp lệ',
        async ({ bien, xuat }) => {
          const mod = await napLai({ [bien]: giaTri });
          // Chính lượt gọi đã ném ERR_INVALID_URL trên CI.
          expect(() => new URL(mod[xuat])).not.toThrow();
        },
      );
    });
  }

  it('mọi biến môi trường site.ts đọc đều nằm trong danh sách trên', () => {
    // conventions.md §1.5 + CLAUDE.md luật 14: thêm biến thứ tư mà quên kiểm thì bài này đỏ,
    // thay vì lặng lẽ để lọt đúng cái bẫy vừa trả giá.
    const doc = [...NGUON.matchAll(/process\.env\.([A-Z0-9_]+)/g)].map(([, ten]) => ten);
    expect(doc.length).toBeGreaterThan(0);
    expect([...new Set(doc)].sort()).toEqual([...HANG_SO.map(({ bien }) => bien)].sort());
  });
});
