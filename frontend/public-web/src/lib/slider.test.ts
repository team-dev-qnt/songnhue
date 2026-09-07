import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';
import { coTuChay, docHieuUngSlider } from './slider';

const CHAY = { autoplay: true, tamDung: false, soAnh: 5, intervalSeconds: 5 };

describe('Slider chỉ tự chạy khi đủ bốn điều kiện', () => {
  it('⭐ cấu hình mặc định của cổng thì CHẠY', () => {
    expect(coTuChay(CHAY)).toBe(true);
  });

  it.each([
    ['tắt tự chạy ở màn hình cấu hình', { autoplay: false }],
    ['con trỏ đang nằm trên ảnh', { tamDung: true }],
    ['chỉ có một ảnh — không có gì để chuyển', { soAnh: 1 }],
    ['không có ảnh nào', { soAnh: 0 }],
    ['nhịp = 0 ⇒ TẮT HẲN, không rơi về mặc định', { intervalSeconds: 0 }],
    ['nhịp âm do nhập sai cũng phải dừng, không chạy 0ms', { intervalSeconds: -3 }],
  ])('⛔ %s ⇒ KHÔNG chạy', (_ten, doi) => {
    expect(coTuChay({ ...CHAY, ...doi })).toBe(false);
  });

  it('⭐ hai ảnh là đủ để chuyển — biên dưới của `soAnh`', () => {
    // Một khẳng định phải phân biệt được hai trạng thái (luật 9): `> 1` và `>= 1` chỉ khác
    // nhau ở đúng `soAnh = 1`, nên phải có cả hai vế mới nói được điều gì.
    expect(coTuChay({ ...CHAY, soAnh: 2 })).toBe(true);
    expect(coTuChay({ ...CHAY, soAnh: 1 })).toBe(false);
  });
});

describe('docHieuUngSlider — `site.slider.effect`, T36.11', () => {
  it('⭐ Khoá VẮNG ⇒ FADE — hành vi ĐANG CHẠY từ WS-16', () => {
    // ⛔ Mặc định SLIDE là đổi diện mạo trang chủ bằng một lượt deploy mà không ai bấm gì.
    expect(docHieuUngSlider(undefined)).toBe('FADE');
    expect(docHieuUngSlider('')).toBe('FADE');
  });

  it('⭐ Đọc được SLIDE — và vế này phân biệt hai trạng thái (luật 9)', () => {
    // ⛔ Không có nó thì một hàm `return "FADE"` trần cũng xanh trọn vẹn ở mọi bài trên.
    expect(docHieuUngSlider('SLIDE')).toBe('SLIDE');
  });

  it('⭐ Chịu được hoa/thường và khoảng trắng — người vận hành gõ vào một ô chữ', () => {
    // Quy tắc 12 ở dạng nhỏ nhất: ép chuẩn hoá ở CHỖ DỮ LIỆU ĐI QUA, không ở từng nơi gọi.
    expect(docHieuUngSlider('slide')).toBe('SLIDE');
    expect(docHieuUngSlider('  Slide  ')).toBe('SLIDE');
  });

  it('⛔ Giá trị lạ rơi về FADE, ⛔ KHÔNG ném', () => {
    // Đây là dữ liệu người vận hành gõ; một trang chủ trắng vì gõ nhầm một ký tự là cái giá sai.
    expect(docHieuUngSlider('banana')).toBe('FADE');
    expect(docHieuUngSlider('FADE')).toBe('FADE');
  });
});

describe('Nơi đọc THẬT của `site.slider.effect` — quy tắc 15', () => {
  // ⛔⛔ Khoá này bị `V202608271032` XOÁ vì "chưa từng có nơi đọc", và `V202609071069` seed lại.
  //    Nó chỉ được phép quay lại kèm một nơi đọc — bài này là chỗ nhớ hộ điều đó.
  //
  // ⚠ `PortalSettingsReadTest` phía backend chỉ hỏi **chuỗi khoá có xuất hiện** trong
  //   `public-web/src`. Nó không biết giá trị ấy có đi tới component nào không. Bài này canh
  //   vế đó — và nó soi cả CHUỖI TRUYỀN, không chỉ chỗ đọc.
  const PAGE = readFileSync(join(process.cwd(), 'src/app/page.tsx'), 'utf8');
  const CAROUSEL = readFileSync(join(process.cwd(), 'src/components/home/AnhCarousel.tsx'), 'utf8');

  it('⭐⭐ `app/page.tsx` đọc khoá QUA `docHieuUngSlider` và truyền xuống CẢ HAI slider', () => {
    expect(PAGE).toContain("docHieuUngSlider(config?.['site.slider.effect'])");
    // ⚠ Hai slider của trang chủ đọc CÙNG bộ khoá `site.slider.*` (yêu cầu 29/08). Truyền cho
    //   một cái là dựng đúng thứ luật 27 gọi là nửa cặp đọc–ghi — Công ty đổi hiệu ứng, một
    //   khối đổi, khối kia không, và không có gì đỏ.
    expect((PAGE.match(/hieuUng=\{hieuUngSlider\}/g) ?? []).length).toBe(2);
  });

  it('⭐⭐ `AnhCarousel` THẬT SỰ dựng hai nhánh khác nhau — ⛔ không phải một prop bị bỏ rơi', () => {
    // ⛔ Đây là vế chịu lực: một prop nhận vào rồi không dùng là đúng hình dạng nợ T25.x —
    //    màn hình cấu hình có ô chọn, giá trị đi tới tận component, và không đổi một pixel nào.
    expect(CAROUSEL).toContain("hieuUng === 'SLIDE'");
    expect(CAROUSEL).toContain('translateX(');
    // Nhánh FADE vẫn phải còn: nó là hành vi mặc định và là thứ đang chạy trên cổng.
    expect(CAROUSEL).toContain('transition-opacity');
  });

  it('⛔ Độ dịch đi bằng `style`, ⛔ KHÔNG bằng lớp Tailwind ghép lúc chạy', () => {
    // Bộ quét nguồn của Tailwind ĐỌC mã, không CHẠY mã. Một lớp ghép lúc chạy không được sinh
    // ra, dải đứng im ở ảnh đầu, và ⛔ không bài kiểm nào đỏ — đúng bẫy `tiLeKhung` đã ghi.
    //
    // ⚠⚠ ĐO ĐƯỢC: bản đầu của bài này ĐỎ trên chính tệp nó canh, vì javadoc của prop `hieuUng`
    //    *nói ra điều cấm bằng cách gõ đúng cái mẫu bị cấm*. Đây là lần thứ BA trong một phiên
    //    cùng một hình dạng (luật 2) ⇒ bỏ chú thích TRƯỚC khi soi, đừng canh văn bản thô.
    const ma = boChuThich(CAROUSEL);
    expect(
      ma.includes('AnhCarouselProps'),
      '⚠ vế đối chứng phải-tìm-thấy: bỏ chú thích xong vẫn phải còn MÃ — ⛔ không có nó thì một ' +
        '`boChuThich` hỏng (trả chuỗi rỗng) làm cả bài này xanh trọn vẹn.',
    ).toBe(true);
    expect(/-translate-x-\[\$\{/.test(ma)).toBe(false);
    expect(ma).toContain('transform: `translateX(');
  });
});
