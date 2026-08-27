import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';

/**
 * **Cổng không ép chữ hoa bằng CSS.**
 *
 * <h2>Vì sao</h2>
 *
 * WS-25 bỏ `uppercase` khỏi thanh điều hướng, và lý do ghi trong `PortalNav` không chỉ là bề
 * rộng: *"chữ hoa tiếng Việt chồng dấu (ĐOÀN THỂ, HOẠT ĐỘNG) làm dấu thanh dính vào nhau và khó
 * đọc hơn hẳn chữ thường"*. Nhưng nó chỉ sửa **một** component. Đo ngày 27/08/2026: **31 chỗ**
 * còn `uppercase` trong **21 tệp** — nghĩa là thanh điều hướng nói một kiểu, toàn bộ thân trang
 * nói kiểu ngược lại.
 *
 * <h2>Chỗ đắt nhất: tên Công ty</h2>
 *
 * `SiteHeader` và `SiteFooter` đều vẽ `{siteName}` bằng `uppercase`, trong khi giá trị thật
 * trong `settings` là `Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ` — đúng cách Công
 * ty viết. Giao diện đang hiện `CÔNG TY TNHH MTV ĐẦU TƯ…`.
 *
 * <p>Đây **không phải** một lựa chọn thẩm mỹ mới: CR-42 đã chốt đúng luật ấy cho địa chỉ —
 * *"hiện nguyên văn giá trị trong `settings`, KHÔNG ép `uppercase`… ép hoa ở đây là giao diện tự
 * quyết định thay người nhập."* Nó chỉ chưa được áp cho tên Công ty ở hai chỗ còn lại.
 *
 * <h2>⚠ Giới hạn, nói ra thay vì để người đọc tự suy (luật 28)</h2>
 *
 * Bài này soi **`public-web`**. `admin-app` không nằm trong phạm vi — màn hình quản trị có quy
 * ước riêng và chưa được rà. Đừng đọc bài xanh này thành *"cả dự án đã hết ép chữ hoa"*.
 */
const GOC = join(process.cwd(), 'src');

/** `uppercase` như một class Tailwind đứng riêng — không khớp `text-uppercase-foo` hay `uppercased`. */
const EP_HOA = /(?<![\w-])uppercase(?![\w-])/g;

/**
 * Giãn chữ chỉ hợp với chữ hoa; để lại `tracking-wider` trên chữ thường là để lại đúng nửa vấn
 * đề. `tracking-tight` thì KHÔNG cấm — nó dùng cho tiêu đề chữ thường bình thường.
 */
const GIAN_CHU = /(?<![\w-])tracking-wider(?![\w-])/g;

function timNguon(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) return timNguon(duong);
    const laNguon = ten.endsWith('.tsx') || ten.endsWith('.ts');
    return laNguon && !ten.includes('.test.') ? [duong] : [];
  });
}

const MA = timNguon(GOC).map((duong) => ({
  ten: duong.slice(GOC.length + 1),
  nguon: boChuThich(readFileSync(duong, 'utf8')),
}));

describe('Cổng không ép chữ hoa bằng CSS', () => {
  it('⚠ tìm được tệp để soi — bài kiểm chạy qua tập rỗng thì xanh mà không canh gì (luật 7)', () => {
    expect(MA.length).toBeGreaterThanOrEqual(30);
    // `boChuThich` hỏng trả về chuỗi rỗng sẽ làm mọi bài dưới đây xanh trọn vẹn.
    expect(MA.reduce((t, m) => t + m.nguon.length, 0)).toBeGreaterThan(50_000);
  });

  it('⛔ không tệp nào ép `uppercase`', () => {
    const pham = MA.flatMap(({ ten, nguon }) => (nguon.match(EP_HOA) ?? []).map(() => ten));
    expect(
      pham,
      'Chữ hoa tiếng Việt chồng dấu khó đọc hơn chữ thường, và nhãn lấy từ `settings` phải hiện ' +
        'nguyên văn người nhập (CR-42). Cần nhấn mạnh thì dùng `font-bold`/`text-lg`/màu, ' +
        'đừng đổi chính con chữ. Nếu thật sự cần chữ hoa cho một nhãn cụ thể thì viết hoa trong ' +
        'NỘI DUNG và ghi lý do — để nó là một quyết định đọc được, không phải một lớp CSS.',
    ).toEqual([]);
  });

  it('⛔ không tệp nào để lại `tracking-wider` — giãn chữ chỉ hợp với chữ hoa', () => {
    const pham = MA.flatMap(({ ten, nguon }) => (nguon.match(GIAN_CHU) ?? []).map(() => ten));
    expect(
      pham,
      '`tracking-wider` đi kèm `uppercase` ở bản cũ. Bỏ chữ hoa mà giữ giãn chữ là giữ lại đúng ' +
        'nửa vấn đề: chữ thường giãn ra trông rời rạc. `tracking-tight` thì vẫn dùng được.',
    ).toEqual([]);
  });
});
