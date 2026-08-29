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
 * Bảng ngoại lệ: tệp nào được ép chữ hoa, và ĐÚNG bao nhiêu chỗ.
 *
 * <h2>Ranh giới — đọc trước khi thêm dòng vào bảng này</h2>
 *
 * Được ép hoa: nhãn do **chính giao diện** đặt ra và không bao giờ đổi theo dữ liệu — mục điều
 * hướng, tiêu đề khối. Cấm ép hoa: mọi thứ **người dùng nhập** — tên Công ty, nhãn menu lấy từ
 * `settings`, tiêu đề bài viết, tên chuyên mục. CR-42 gọi đúng tên vấn đề: *"ép hoa ở đây là giao
 * diện tự quyết định thay người nhập"*. Cần chữ hoa cho dữ liệu thì viết hoa trong GIÁ TRỊ (xem
 * `site.header.display-name`, dựng ở `V202608291042`), không phải trong CSS.
 *
 * <h2>Từng dòng, và vì sao con số đó</h2>
 *
 * - `PortalNav` **4**: thanh ngang cấp 1 (`LOP_MUC_CAP1`), nhãn cấp 1 trong ngăn kéo, **menu con
 *   ở máy tính**, và **menu con trong ngăn kéo**.
 *   ⚠ Con số này đã đổi hai lần trong một ngày, và mỗi lần vì một quyết định đọc được:
 *   **3 → 2** khi nút Tìm kiếm chuyển lên dải nhận diện ({@code SiteHeader}) để trả bề rộng lại
 *   cho thanh; **2 → 4** khi Công ty yêu cầu menu con cũng viết hoa (29/08 chiều).
 *   ⛔ Vì sao chữ hoa của menu con nằm ở CSS chứ không ở nhãn trong CSDL: nhãn menu do Công ty
 *   nhập từ màn hình quản trị, nên đặt ở dữ liệu là hôm nay 12 mục đúng còn mục thứ 13 thêm vào
 *   tuần sau sẽ chữ thường — một quy ước phụ thuộc trí nhớ con người mà **không cổng kiểm nào bắt
 *   được**, vì nó nằm trong CSDL chứ không trong mã. Đúng cảnh ấy đã xảy ra: một migration đổi
 *   nhãn thành chữ hoa cho MỘT mục cấp 2 tạo ra *một mục hoa, mười một mục thường*; migration ấy
 *   đã bị gỡ và thay bằng hai chỗ ép hoa ở đây.
 * - `SectionTitle` **1**: tiêu đề khối trang chủ, bố cục Công ty duyệt 29/08. Một chỗ duy nhất
 *   phục vụ cả mười một khối — đó chính là lý do nó được vào bảng này: gom về một nơi thì ngưỡng
 *   đếm còn có nghĩa, chứ mười một khối tự viết `uppercase` thì bảng này thành vô dụng.
 *
 * ⛔ Ngưỡng phải khớp CHÍNH XÁC theo cả hai chiều. Cao hơn thực tế là một khoảng trống lặng lẽ
 *    cho chỗ ép hoa kế tiếp trôi vào.
 */
const NGOAI_LE: Record<string, number> = {
  'components/nav/PortalNav.tsx': 4,
  'components/home/SectionTitle.tsx': 1,
};

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

  it.each(Object.entries(NGOAI_LE))(
    '⭐ ngoại lệ %s có thật và ĐÚNG số chỗ — không phải tấm vé trắng cho cả tệp',
    (tep, soCho) => {
      const m = MA.find(({ ten }) => ten === tep);
      // Ngoại lệ trỏ vào một tệp không còn tồn tại thì bài dưới sẽ trừ đi một tập rỗng, và bộ canh
      // im lặng nới rộng ra cả cây (luật 7).
      expect(
        m,
        `\`${tep}\` không còn — SỬA bảng NGOAI_LE, đừng để nó trỏ vào chỗ trống`,
      ).toBeDefined();
      expect(
        (m!.nguon.match(EP_HOA) ?? []).length,
        `Số chỗ ép hoa trong \`${tep}\` đã đổi. Thêm chỗ mới thì sửa NGOAI_LE kèm lý do; ` +
          'bớt đi thì cũng sửa — một ngưỡng cao hơn thực tế là một khoảng trống lặng lẽ.',
      ).toBe(soCho);
    },
  );

  it('⛔⛔ chữ hoa của thanh điều hướng nằm trên CHÍNH mục, không trên khung chứa', () => {
    const nav = MA.find(({ ten }) => ten === 'components/nav/PortalNav.tsx')?.nguon ?? '';
    const lopMuc = /const LOP_MUC_CAP1 =\s*'([^']*)'/.exec(nav)?.[1];
    const lopChu = /const LOP_CHU_CAP1 =\s*'([^']*)'/.exec(nav)?.[1];

    expect(
      lopMuc,
      'không đọc được `LOP_MUC_CAP1` — SỬA bài kiểm, đừng để nó soi tập rỗng',
    ).toBeDefined();
    expect(
      lopChu,
      'không đọc được `LOP_CHU_CAP1` — SỬA bài kiểm, đừng để nó soi tập rỗng',
    ).toBeDefined();

    expect(
      lopMuc,
      'Mục `linkType=NONE` vẽ ra `<button>`, và UA stylesheet khai thẳng `text-transform: none` ' +
        'trên `button` — một khai báo trên chính phần tử luôn thắng giá trị kế thừa. Preflight của ' +
        'Tailwind v4 reset `font`/`letter-spacing`/`color` cho form control đúng vì lý do này, ' +
        'nhưng KHÔNG reset `text-transform`. Đặt chữ hoa lên `<ul>` thì "Giới thiệu" và ' +
        '"Quản lý, vận hành" — đúng hai mục NONE — lặng lẽ không viết hoa (đo 28/08).',
    ).toContain('uppercase');

    expect(
      lopChu,
      '`LOP_CHU_CAP1` gắn lên `<ul>` khung chứa. Để `uppercase` ở đó là quay lại đúng lỗi vừa sửa: ' +
        'sáu mục `<a>` viết hoa, hai mục `<button>` thì không, và nhìn như lỗi dữ liệu menu.',
    ).not.toContain('uppercase');
  });

  it('⛔ không tệp nào KHÁC ép `uppercase`', () => {
    const pham = MA.filter(({ ten }) => !(ten in NGOAI_LE)).flatMap(({ ten, nguon }) =>
      (nguon.match(EP_HOA) ?? []).map(() => ten),
    );
    expect(
      pham,
      `Chỉ ${Object.keys(NGOAI_LE)
        .map((t) => `\`${t}\``)
        .join(' và ')} được ép chữ hoa. ` +
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
