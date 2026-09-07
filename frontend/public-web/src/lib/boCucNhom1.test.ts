import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { lopCotTin } from './boCucNhom1';

const PAGE = join(process.cwd(), 'src/app/page.tsx');

/**
 * Bất biến: **mẹo `lg:absolute lg:inset-0` chỉ được bật khi slider thật sự dựng ra một khung.**
 *
 * Lỗi 7/9 trên production: `banners` rỗng ⇒ `AnhCarousel` trả `khiRong` trần (mất `tiLeKhung`) ⇒
 * hàng lưới co còn ~90px ⇒ thẻ tin bị `inset-0` ép xuống 90px, vẽ tràn và đè lên khối dưới.
 *
 * Bộ đo Playwright canh đúng lỗi này nhưng **không nằm trong CI** (T38.10), và bài TIỀN ĐỀ của nó
 * đòi ≥8 bài viết ⇒ nó không bao giờ chạy tới trạng thái rỗng. Bài này chạy được trong CI vì phép
 * quyết định đã tách khỏi React.
 */
describe('lopCotTin — mẹo chiều cao chỉ bật khi có khung slider', () => {
  it('⭐⭐ KHÔNG có khung slider ⇒ không lớp absolute nào (đúng trạng thái production 7/9)', () => {
    const lop = lopCotTin(false);
    expect(lop.trong, 'thẻ trong phải rỗng — có mẹo là có đè').toBe('');
    expect(lop.ngoai).not.toContain('absolute');
    expect(lop.ngoai).not.toContain('relative');
    expect(lop.ngoai, 'vẫn phải giữ đúng 4/12').toContain('lg:col-span-4');
  });

  it('⭐ CÓ khung slider ⇒ giữ nguyên mẹo cũ', () => {
    const lop = lopCotTin(true);
    expect(lop.ngoai).toContain('lg:relative');
    expect(lop.ngoai).toContain('lg:col-span-4');
    expect(lop.trong).toContain('lg:absolute');
    expect(lop.trong).toContain('lg:inset-0');
  });

  it('⭐⭐ hai trạng thái phải cho hai kết quả KHÁC nhau', () => {
    // Luật 9: một hàm trả cùng một thứ cho mọi đầu vào thì nó không quyết định gì. Đây là phép
    // duy nhất trong tệp không chia sẻ giả định nào với hai phép trên.
    expect(JSON.stringify(lopCotTin(true))).not.toBe(JSON.stringify(lopCotTin(false)));
  });
});

describe('page.tsx phải DÙNG hàm ấy, không viết thẳng lớp', () => {
  const nguon = readFileSync(PAGE, 'utf8');

  it('đọc được tệp — thiếu khẳng định này thì mọi phép dưới xanh khi đường dẫn sai', () => {
    // Luật 7: `readFileSync` ném khi thiếu tệp, nhưng một tệp RỖNG thì không — và `not.toMatch`
    // trên chuỗi rỗng luôn đúng.
    expect(nguon.length).toBeGreaterThan(2000);
    expect(nguon).toContain('HomeCategoryNews');
  });

  it('⭐⭐ không nơi nào trong page.tsx viết thẳng `lg:absolute` trong JSX', () => {
    // Luật 14: lớp ấy chỉ được sinh ở `boCucNhom1.ts`. Viết lại trong JSX là dựng lại đúng cái
    // "hai nơi phải nhớ" mà bản vá này gỡ đi.
    const jsx = nguon
      .split('\n')
      // Bỏ dòng chú thích — javadoc của tệp CÓ nhắc tên lớp, và phải được phép nhắc.
      .filter((d) => {
        const t = d.trim();
        return !(
          t.startsWith('*') ||
          t.startsWith('//') ||
          t.startsWith('/*') ||
          t.startsWith('⛔')
        );
      })
      .join('\n');
    expect(jsx, 'lớp bố cục phải đến từ lopCotTin()').not.toMatch(/className="[^"]*lg:absolute/);
  });

  it('⭐ điều kiện phải hỏi thứ SẼ ĐƯỢC RENDER, không hỏi banners.length', () => {
    // `site.slider.max-items = 0` cũng cho một slider rỗng, nên `banners.length` là câu hỏi sai.
    expect(nguon).toMatch(/const anhSlider = \(banners \?\? \[\]\)\.slice\(0, soAnhSlider\)/);
    expect(nguon).toMatch(/lopCotTin\(anhSlider\.length > 0\)/);
    expect(nguon, 'slider và cột tin phải đọc CÙNG một danh sách đã cắt').toMatch(
      /banners=\{anhSlider\}/,
    );
  });
});
