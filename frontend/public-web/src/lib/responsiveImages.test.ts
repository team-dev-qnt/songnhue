import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';

/**
 * **Ảnh nội dung phải đi qua {@link PortalImage} — không thẻ `<img>` trần nào.**
 *
 * <h2>Vì sao cần một bài kiểm chứ không phải một lời dặn</h2>
 *
 * Yêu cầu 29/08: *"các thẻ display ảnh cần responsive để ảnh fit với thẻ to nhỏ khác nhau"*.
 * Luật thì ngắn — khung mang tỉ lệ, ảnh phủ khung — nhưng nó phải đúng ở **bảy** cỡ ảnh khác
 * nhau trên cùng trang chủ, và người viết component thứ tám sẽ không đọc lại luật ấy.
 *
 * <p>Đây đúng hình dạng luật 12: một bảo đảm phải đúng ở nhiều đường vào thì đặt nó ở chỗ dữ
 * liệu ĐI QUA, không đặt ở nơi gọi. Chỗ đi qua là {@code PortalImage}; bài này đếm đủ các
 * đường vào còn lại.
 *
 * <h2>Lỗi đã bắt được khi dựng bài này</h2>
 *
 * {@code app/bai-viet/[slug]/page.tsx} vẽ ảnh bìa bằng
 * {@code <img className="w-full object-cover">} trong một div **không có chiều cao**.
 * {@code object-cover} chỉ có tác dụng khi khung đã có kích thước, nên nó là một khai báo
 * chết; và khung cao 0 cho tới lúc ảnh về rồi bung ra, đẩy cả bài viết xuống. Hai lỗi trong
 * một dòng, cả hai đều im lặng.
 */
const GOC = join(process.cwd(), 'src');

/** `<img` mở thẻ — không khớp `<image`, `<imgFoo`. */
const THE_ANH = /<img[\s/>]/g;

/**
 * Nơi được vẽ `<img>` trần, và ĐÚNG bao nhiêu chỗ.
 *
 * - `PortalImage.tsx` **1** — chính nó, nơi luật được viết ra.
 * - `SiteHeader.tsx` / `SiteFooter.tsx` **1** — logo Công ty. Logo dùng `object-contain` và
 *   KHÔNG được cắt: cắt logo là cắt nhận diện pháp nhân. Nó không thuộc luật "phủ khung".
 * - `HomeBannerSlider.tsx` **1** — mỗi ảnh slider LÀ một khung tuyệt đối phủ kín ô trượt, tức
 *   khung đã có kích thước từ trước; bọc thêm một khung tỉ lệ nữa là lồng hai khung.
 *
 * ⛔ Ngưỡng khớp CHÍNH XÁC hai chiều: cao hơn thực tế là một khoảng trống cho thẻ `<img>` trần
 *    kế tiếp trôi vào.
 */
const NGOAI_LE: Record<string, number> = {
  'components/PortalImage.tsx': 1,
  'components/SiteHeader.tsx': 1,
  'components/SiteFooter.tsx': 1,
  'components/home/HomeBannerSlider.tsx': 1,
};

function timTsx(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) return timTsx(duong);
    return ten.endsWith('.tsx') && !ten.includes('.test.') ? [duong] : [];
  });
}

const MA = timTsx(GOC).map((duong) => ({
  ten: duong.slice(GOC.length + 1),
  nguon: boChuThich(readFileSync(duong, 'utf8')),
}));

const NGUON_PORTAL_IMAGE = MA.find(({ ten }) => ten === 'components/PortalImage.tsx')?.nguon ?? '';

describe('Ảnh của cổng luôn vừa khung chứa', () => {
  it('⚠ tìm được tệp để soi — bài kiểm chạy qua tập rỗng thì xanh mà không canh gì (luật 7)', () => {
    expect(MA.length).toBeGreaterThanOrEqual(20);
    expect(NGUON_PORTAL_IMAGE.length).toBeGreaterThan(500);
  });

  it.each(Object.entries(NGOAI_LE))('⭐ ngoại lệ %s có thật và ĐÚNG số chỗ', (tep, soCho) => {
    const m = MA.find(({ ten }) => ten === tep);
    expect(
      m,
      `\`${tep}\` không còn — SỬA bảng NGOAI_LE, đừng để nó trỏ vào chỗ trống`,
    ).toBeDefined();
    expect(
      (m!.nguon.match(THE_ANH) ?? []).length,
      `Số thẻ <img> trần trong \`${tep}\` đã đổi. Thêm thì sửa NGOAI_LE kèm lý do; bớt thì cũng sửa.`,
    ).toBe(soCho);
  });

  it('⛔ không tệp nào KHÁC vẽ <img> trần — ảnh nội dung phải đi qua PortalImage', () => {
    const pham = MA.filter(({ ten }) => !(ten in NGOAI_LE)).flatMap(({ ten, nguon }) =>
      (nguon.match(THE_ANH) ?? []).map(() => ten),
    );
    expect(
      pham,
      'Ảnh nội dung phải dùng `<PortalImage>`: khung mang tỉ lệ (`aspect-*`), ảnh phủ khung ' +
        '(`object-cover`), và ô rỗng vẫn giữ chỗ. Vẽ `<img>` trần là mất cả ba — `object-cover` ' +
        'trong một khung chưa có chiều cao không làm gì cả, và bố cục nhảy khi ảnh về.',
    ).toEqual([]);
  });

  it('⭐ PortalImage giữ đủ BA luật — không chỉ tồn tại cho có', () => {
    // Canh giá trị ĐÃ GIẢI, không canh cái nghe có vẻ đúng (luật 3 + luật 9).
    expect(
      /ratio = 'aspect-\[[\d/]+\]'/.test(NGUON_PORTAL_IMAGE),
      'PortalImage phải có tỉ lệ MẶC ĐỊNH: nơi gọi quên truyền `ratio` mà khung vẫn có tỉ lệ.',
    ).toBe(true);

    expect(
      NGUON_PORTAL_IMAGE.includes('object-cover'),
      'Thiếu `object-cover` thì ảnh méo theo khung thay vì được cắt cho vừa.',
    ).toBe(true);

    expect(
      NGUON_PORTAL_IMAGE.includes('${ratio}'),
      'Tỉ lệ phải rơi vào KHUNG NGOÀI. Đặt lên chính thẻ <img> thì khung vẫn cao 0 trước khi ' +
        'ảnh về — vẫn nhảy bố cục, chỉ khác là nhìn qua tưởng đã sửa.',
    ).toBe(true);
  });

  it('⭐ ô rỗng vẫn là một ô — trả null là lưới co lại, thẻ cạnh nhau cao thấp so le', () => {
    // Nhánh `src == null` phải vẫn vẽ ra khung: tìm dấu hiệu cấu trúc, không tìm chữ.
    const coNhanhRong = /\) : \(\s*<div/.test(NGUON_PORTAL_IMAGE);
    expect(
      coNhanhRong,
      'Nhánh không có ảnh phải vẽ một `<div>` giữ chỗ, không `return null`.',
    ).toBe(true);
    expect(NGUON_PORTAL_IMAGE).not.toMatch(/if \(!src\) return null/);
  });
});
