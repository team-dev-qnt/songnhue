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
 * <h2>⭐ 28/08: Công ty yêu cầu chữ hoa TRỞ LẠI — nhưng chỉ ở thanh điều hướng</h2>
 *
 * Quyết định của Công ty, và nó **thu hẹp** bài này chứ không xoá nó: `PortalNav` được phép ép
 * chữ hoa, mọi tệp khác vẫn cấm. Lý do giữ nguyên ở chỗ còn lại — nhất là `{siteName}`, thứ phải
 * hiện **nguyên văn giá trị trong `settings`** (CR-42).
 *
 * <p>⚠ Ngoại lệ có **ngưỡng đếm**, không phải một tấm vé trắng cho cả tệp: thêm chỗ ép hoa thứ tư
 * vào `PortalNav` cũng làm bài này đỏ. Muốn thêm thật thì sửa `SO_CHO_HOA_TRONG_NAV` và nói rõ vì
 * sao — để nó là một quyết định đọc được, không phải một lớp CSS trôi vào.
 *
 * <p>⛔ `tracking-wider` **vẫn cấm ở mọi nơi, kể cả `PortalNav`** — không phải vì thẩm mỹ mà vì
 * ngân sách bề rộng: đo được nó ngốn thêm ~38px và đẩy thanh điều hướng về sát mép khung 1192px.
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
 * Tệp DUY NHẤT được ép chữ hoa, và số chỗ nó được phép ép.
 *
 * Ba chỗ: thanh ngang cấp 1 (`ul`), nút Tìm kiếm cạnh nó, và nhãn cấp 1 trong ngăn kéo. Menu con
 * KHÔNG nằm trong số đó — nhãn cấp 2 dài hơn và xếp dọc trong một danh sách dày.
 */
const TEP_NGOAI_LE = 'components/nav/PortalNav.tsx';
const SO_CHO_HOA_TRONG_NAV = 3;

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

  it('⭐ ngoại lệ có thật và ĐÚNG số chỗ — không phải tấm vé trắng cho cả tệp', () => {
    const nav = MA.find(({ ten }) => ten === TEP_NGOAI_LE);
    // Ngoại lệ trỏ vào một tệp không còn tồn tại thì bài dưới sẽ trừ đi một tập rỗng, và bộ canh
    // im lặng nới rộng ra cả cây (luật 7).
    expect(
      nav,
      `\`${TEP_NGOAI_LE}\` không còn — SỬA hằng số ngoại lệ, đừng để nó trỏ vào chỗ trống`,
    ).toBeDefined();
    expect(
      (nav!.nguon.match(EP_HOA) ?? []).length,
      'Số chỗ ép hoa trong thanh điều hướng đã đổi. Thêm chỗ mới thì sửa `SO_CHO_HOA_TRONG_NAV` ' +
        'kèm lý do; bớt đi thì cũng sửa — một ngưỡng cao hơn thực tế là một khoảng trống lặng lẽ.',
    ).toBe(SO_CHO_HOA_TRONG_NAV);
  });

  it('⛔ không tệp nào KHÁC ép `uppercase`', () => {
    const pham = MA.filter(({ ten }) => ten !== TEP_NGOAI_LE).flatMap(({ ten, nguon }) =>
      (nguon.match(EP_HOA) ?? []).map(() => ten),
    );
    expect(
      pham,
      `Chỉ \`${TEP_NGOAI_LE}\` được ép chữ hoa (Công ty yêu cầu 28/08, chỉ cho thanh điều hướng). ` +
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
