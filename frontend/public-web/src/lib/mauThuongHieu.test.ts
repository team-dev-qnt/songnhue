import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { brandColors, portalChrome } from '@songnhue/design-tokens';
import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';
import { ANH_XA_MAU, cssMauThuongHieu, laMaMauHopLe } from './mauThuongHieu';

/**
 * Màu nhận diện đổi được từ admin — T75.7.
 *
 * <h2>Vì sao bài này tồn tại</h2>
 *
 * Cơ chế cũ (`site.color.*`, 19/08–28/08/2026) chết vì **⛔ ai đọc**: admin đặt màu, hệ báo *lưu
 * thành công*, cổng ⛔ đổi gì. Nó ⛔ có một bài kiểm nào — và ⛔ thể có, vì ⛔ có hàm nào để gọi.
 *
 * Nên bài này canh đúng hai thứ mà lượt trước thiếu:
 *   1. **Có đọc thật** — một giá trị vào thì một biến CSS ra.
 *   2. **⛔ đọc bừa** — giá trị rác ⛔ được phép chảy vào thẻ `<style>` của cổng công khai.
 *
 * <p>⚠ Phạm vi (luật 28): bài này canh **hàm dựng chuỗi**. Vế *"Tailwind có sinh ra CSS đọc được
 * biến ấy ⛔"* nằm ở `tailwindMauDoiDuoc` bên dưới, và vế *"trình duyệt vẽ đúng màu ⛔"* thì ⛔ bài
 * jsdom nào chứng minh được — nó thuộc lượt đi đường người dùng thật.
 */
describe('cssMauThuongHieu — đường đọc mà cơ chế cũ ⛔ có', () => {
  it('⛔ có khoá nào ⇒ chuỗi RỖNG, để trang rơi về token', () => {
    // Đây là trạng thái MẶC ĐỊNH của hệ khi giao, ⛔ phải một ca lỗi: `design-tokens` đã mang đúng
    // màu bộ nhận diện, nên ⛔ ghi đè là hiển thị đúng.
    expect(cssMauThuongHieu({})).toBe('');
    expect(cssMauThuongHieu(null)).toBe('');
    expect(cssMauThuongHieu(undefined)).toBe('');
  });

  it('⛔ trả `:root{}` khi rỗng — một thẻ rỗng đọc như "đã có ghi đè"', () => {
    // Người mở DevTools đi tìm *"màu này tới từ đâu"* mà thấy một `:root{}` sẽ kết luận sai rằng
    // cơ chế đang chạy và giá trị của nó rỗng. Nơi gọi dựa vào chuỗi rỗng để ⛔ dựng thẻ nào cả.
    expect(cssMauThuongHieu({ 'site.brand.primary': '' })).toBe('');
    expect(cssMauThuongHieu({ 'site.brand.primary': '   ' })).toBe('');
  });

  it('một màu hợp lệ ⇒ đúng một biến', () => {
    expect(cssMauThuongHieu({ 'site.brand.primary': '#ff0000' })).toBe(
      ':root{--sn-brand-primary:#ff0000}',
    );
  });

  it('hai màu hợp lệ ⇒ hai biến, đúng thứ tự khai báo', () => {
    expect(
      cssMauThuongHieu({ 'site.brand.primary': '#1758BF', 'site.brand.accent': '#FAC036' }),
    ).toBe(':root{--sn-brand-primary:#1758bf;--sn-brand-accent:#fac036}');
  });

  it('nền đầu trang / chân trang đi ra đúng biến của chúng', () => {
    // Hai khoá của T77.1. Nếu ánh xạ lệch thì admin đặt màu chân trang mà ĐẦU trang đổi — một
    // khuyết tật `lưu thành công` khác, chỉ là ồn ào hơn `site.color.*` ngày xưa.
    expect(
      cssMauThuongHieu({ 'site.brand.header': '#123456', 'site.brand.footer': '#654321' }),
    ).toBe(':root{--sn-brand-header:#123456;--sn-brand-footer:#654321}');
  });

  it('bỏ qua ĐÚNG khoá rác, giữ khoá hợp lệ còn lại', () => {
    // Vế quan trọng: một giá trị hỏng ⛔ được kéo theo giá trị lành. Nếu hàm trả rỗng khi gặp rác
    // thì một lượt gõ nhầm ở ô thứ hai sẽ lặng lẽ tắt cả màu chủ đạo.
    expect(cssMauThuongHieu({ 'site.brand.primary': '#1758bf', 'site.brand.accent': 'vàng' })).toBe(
      ':root{--sn-brand-primary:#1758bf}',
    );
  });
});

describe('laMaMauHopLe — chốt chặn tiêm CSS', () => {
  it.each(['#1758bf', '#FAC036', '#000000', '#ffffff', '  #1758bf  '])('nhận %s', (gia) => {
    expect(laMaMauHopLe(gia)).toBe(true);
  });

  /**
   * ⛔⛔ Bảy ca dưới đây ⛔ phải "đầu vào lạ" — chúng là **đầu vào tấn công**.
   *
   * Giá trị này được ghép vào một khối `<style>` phục vụ mọi người dân tra cứu. Dấu `;` đóng khai
   * báo và `}` đóng luật, nên một chuỗi lọt qua đây viết được luật CSS mới: phủ một lớp trong suốt
   * lên toàn trang, đổi `content` của một phần tử, hay tải một ảnh nền về một máy chủ lạ (⛔ đủ để
   * đánh cắp dữ liệu, nhưng đủ để đo xem ai đang đọc trang nào).
   *
   * ⚠ `rgb(255,0,0)` là CSS **hợp lệ** và vẫn bị từ chối — đúng ý đồ. Vị từ ở đây ⛔ phải *"CSS có
   * hiểu ⛔"* mà là *"có đúng hình dạng ta cho phép ⛔"*. Nới nó ra để chiều một người dùng gõ
   * `rgb()` là mở lại đúng cánh cửa này.
   */
  it.each([
    ['#fff', 'dạng 3 ký tự — CSS hiểu, nhưng ô nhập mô tả 6 chữ số (quy tắc 14)'],
    ['red', 'tên màu'],
    ['rgb(255,0,0)', 'CSS hợp lệ mà vẫn từ chối — hình dạng ⛔ khớp'],
    ['#1758bf;}body{display:none', 'THOÁT khai báo rồi ẩn cả trang'],
    ['#1758bf}*{background:url(//kẻ-lạ/x)', 'THOÁT luật rồi gọi máy chủ lạ'],
    ['#12345', '5 chữ số'],
    ['#1234567', '7 chữ số'],
    ['#gggggg', 'ngoài bảng hex'],
    ['', 'rỗng'],
  ])('từ chối %s — %s', (gia) => {
    expect(laMaMauHopLe(gia)).toBe(false);
  });

  it('từ chối cả null/undefined/⛔ phải chuỗi', () => {
    expect(laMaMauHopLe(null)).toBe(false);
    expect(laMaMauHopLe(undefined)).toBe(false);
  });

  it('⚠ chuỗi tấn công ⛔ BAO GIỜ lọt được vào chuỗi trả về', () => {
    // Vế chống-xanh-vì-lý-do-sai: khẳng định trên `laMaMauHopLe` ⛔ chứng minh hàm dựng chuỗi có
    // GỌI nó. Đây là phép đo ở đầu ra — thứ thật sự đi vào trang.
    const ra = cssMauThuongHieu({ 'site.brand.primary': '#1758bf;}body{display:none' });
    expect(ra).not.toContain('display');
    expect(ra).not.toContain('}body');
    expect(ra).toBe('');
  });
});

describe('Ánh xạ khoá ↔ biến ↔ token', () => {
  it('mỗi khoá là `site.brand.*` và mỗi biến là `--sn-brand-*` — tên khớp nhau', () => {
    // Quy tắc 14: khoá `settings`, biến CSS và token `design-tokens` dùng CHUNG một hậu tố, nên ⛔
    // còn phép ánh xạ nào để ai đó nhớ sai. Bài này đỏ ngay lượt ai đó đặt lệch một cái tên.
    //
    // ⚠ Từ T77.1 hậu tố đến từ HAI nhóm token (`brandColors` cho bộ nhận diện, `portalChrome` cho
    //   khung cổng). Nên vế thứ ba ⛔ còn là *"`brandColors` có thuộc tính này"* mà là **đúng MỘT
    //   nhóm sở hữu nó**: nếu hai nhóm cùng khai một cái tên thì `<x>` ⛔ còn trỏ tới một giá trị
    //   xác định, và người đọc sau ⛔ có cách biết ô nhập đang điều khiển con số nào.
    const nhomToken = { brandColors, portalChrome };
    for (const [khoa, bien] of ANH_XA_MAU) {
      const hauTo = khoa.replace('site.brand.', '');
      expect(khoa).toMatch(/^site\.brand\.[a-z]+$/);
      expect(bien).toBe(`--sn-brand-${hauTo}`);
      const chuSoHuu = Object.entries(nhomToken)
        .filter(([, bang]) => hauTo in bang)
        .map(([ten]) => ten);
      expect(chuSoHuu, `hậu tố \`${hauTo}\` phải là tên của ĐÚNG một token`).toHaveLength(1);
    }
  });

  it('⛔ nhận thêm khoá nào ngoài bốn vai trò đã quyết', () => {
    // Trần chỉ-được-giảm. Thêm một núm màu là một quyết định (nền trang và màu chữ CỐ Ý ⛔ có núm —
    // xem javadoc `brandColors`; năm bậc navy CỐ Ý ⛔ có núm riêng — xem `portalChrome`), ⛔ phải
    // một dòng thêm vào mảng cho tiện.
    expect(ANH_XA_MAU.map(([k]) => k)).toEqual([
      'site.brand.primary',
      'site.brand.accent',
      'site.brand.header',
      'site.brand.footer',
    ]);
  });
});

describe('design-tokens mang đúng bộ nhận diện 20/09/2026', () => {
  it('mặc định là màu Công ty gửi, ⛔ phải màu cũ', () => {
    // Đây là thứ người dùng thấy khi CHƯA ai đặt gì — tức trạng thái lúc giao. Ghim nó để một lượt
    // "chỉnh cho đẹp" ⛔ lặng lẽ kéo cổng ra khỏi bộ nhận diện đã duyệt.
    expect(brandColors.primary).toBe('#1758bf');
    expect(brandColors.accent).toBe('#fac036');
    expect(brandColors.link).toBe(brandColors.primary);
  });

  it('nền khung cổng mặc định vẫn là navy đã nghiệm thu 27/08, ⛔ phải màu nhận diện', () => {
    // ⚠ Đây là vế *"vẫn set default màu như vậy"*. Bộ nhận diện 20/09 ⛔ nói gì về nền đầu/chân
    // trang, và văn bản nghiệm thu 27/08 chốt *"hệ màu GIỮ NGUYÊN"* — nên mặc định phải là sắc
    // navy đang chạy, ⛔ phải `#1758bf`. Một lượt "cho đồng bộ với màu chủ đạo" sẽ đỏ ở đây.
    expect(portalChrome.header).toBe(portalChrome.navy800);
    expect(portalChrome.footer).toBe(portalChrome.navy700);
    expect(portalChrome.header).not.toBe(brandColors.primary);
  });
});

/**
 * Vế CẤU TRÚC — thứ làm biến CSS ở trên có tác dụng thật (luật 2: canh cấu trúc, ⛔ canh văn bản).
 *
 * <h3>⭐ Phép đo đã chạy, ⛔ phải suy từ phiên bản</h3>
 *
 * Rủi ro thật của thiết kế này: `border-brand-primary/30` (dùng ở 7 chỗ trong `public-web`). Một
 * màu dạng `var()` **⛔ dùng được** với opacity modifier ở Tailwind 3 — cú pháp ở đó là
 * `<alpha-value>`, và khi thiếu nó thì CSS hỏng **trong im lặng**: ⛔ lỗi build, ⛔ cảnh báo, chỉ
 * mất viền trên bảy thành phần.
 *
 * Đo ngày 20/09/2026 bằng cách chạy CHÍNH trình biên dịch của kho (`@tailwindcss/postcss`) trên
 * một tệp gá, rồi đọc CSS sinh ra:
 *
 * <pre>
 *   .border-brand-primary\/30 {
 *     border-color: var(--color-brand-primary);                                   ← dự phòng
 *     &#64;supports (color: color-mix(in lab, red, red)) {
 *       border-color: color-mix(in oklab, var(--color-brand-primary) 30%, transparent);
 *     }
 *   }
 * </pre>
 *
 * ⇒ Chạy được, và Tailwind 4 còn tự phát một dòng dự phòng cho trình duyệt ⛔ có `color-mix`.
 *
 * <p>⚠ Vì kết luận ấy **phụ thuộc phiên bản chính**, bài dưới ghim nó. Hạ Tailwind về 3 là lớp lỗi
 * kia quay lại y nguyên, và ⛔ gì khác trong kho báo được điều đó.
 *
 * <h3>⭐ 20/09 — đo thêm vế GRADIENT (T77.1), vì nó hỏng theo một kiểu khác</h3>
 *
 * Nền đầu/chân trang ⛔ đi qua `background-color` mà đi qua các chặng `from-*`/`via-*`/`to-*`.
 * Chạy cùng trình biên dịch ấy trên `from-chrome-header via-chrome-headerMid to-chrome-header`:
 *
 * <pre>
 *   .from-chrome-header  { --tw-gradient-from: var(--color-chrome-header); … }
 *   .via-chrome-headerMid{ --tw-gradient-via:  var(--color-chrome-headerMid); … }
 *   &#64;property --tw-gradient-from { syntax: "&lt;color&gt;"; initial-value: #0000; }
 * </pre>
 *
 * ⇒ Chạy được. ⛔⛔ Nhưng dòng `&#64;property` là điều phải nhớ: đó là custom property **đã đăng
 * ký**, nên một giá trị ⛔ phải màu ⛔ rơi về `var()` fallback mà rơi về `initial-value` —
 * **trong suốt**. Ở hai khoá này, rác lọt qua ⇒ mất hẳn đầu trang, ⛔ phải "sai màu".
 */
describe('Tailwind — tầng làm cho biến CSS có tác dụng', () => {
  const cauHinh = readFileSync(
    join(dirname(fileURLToPath(import.meta.url)), '../../tailwind.config.ts'),
    'utf8',
  );

  // ⚠ Mọi khẳng định cấu trúc bên dưới soi `maCauHinh` — bản ĐÃ BỎ CHÚ THÍCH. Quét cả tệp thì
  //   một đoạn javadoc mô tả cơ chế cũng làm bài xanh, tức bộ canh tự im được bằng một dòng văn.
  const maCauHinh = boChuThich(cauHinh);

  it('primary · link · accent đều bọc `var()` với token làm dự phòng', () => {
    // Phá một dòng trong số này ⇒ khoá `settings` tương ứng thành nửa cặp đọc–ghi trở lại: admin
    // đặt màu, cổng ⛔ đổi. Đúng khuyết tật `V202608281037` đã gỡ.
    for (const ten of ['primary', 'link', 'accent']) {
      expect(maCauHinh, `brand.${ten} phải đổi được lúc chạy`).toMatch(
        new RegExp(`\\b${ten}:\\s*doiDuocLucChay\\(`),
      );
    }
  });

  it('⛔ gõ lại mã hex nào trong MÃ của cấu hình — dự phòng phải LẤY TỪ token', () => {
    // Nếu ai đó viết `doiDuocLucChay('brand-primary', '<hex>')` thì có ngay nguồn màu thứ hai, và
    // một lượt đổi `design-tokens` sẽ ⛔ chạm tới cổng công khai (quy tắc 14).
    //
    // ⚠⚠ Quét trên MÃ ĐÃ BỎ CHÚ THÍCH, ⛔ trên cả tệp — và tôi bắt được điều này ở chính lượt viết
    //    bài: javadoc của `doiDuocLucChay` trích một mã hex để giải thích *"Tailwind nướng hex vào
    //    CSS lúc build"*, nên phép quét cả tệp sẽ ĐỎ GIẢ và phạt đúng đoạn văn đang dạy người đọc
    //    vì sao cơ chế này tồn tại. Lần thứ NĂM của hình dạng T46.7 · T54.8; cách sửa rẻ nhất (bỏ
    //    hex khỏi chú thích) là xoá bài học mà vẫn để bộ canh thủng.
    expect(maCauHinh).not.toMatch(/#[0-9a-fA-F]{6}/);
  });

  it('⚠ tự kiểm: `boChuThich` bỏ chú thích mà GIỮ mã', () => {
    // ⛔ cắt thô theo `//` — một URL `https://…` trong mã sẽ bị nuốt nửa dòng (đúng bẫy T49.6).
    expect(
      boChuThich("const a = 1; // #ffffff\n/* #000000 */\nconst b = '#123456';"),
    ).not.toContain('#ffffff');
    expect(
      boChuThich("const a = 1; // #ffffff\n/* #000000 */\nconst b = '#123456';"),
    ).not.toContain('#000000');
    expect(boChuThich("const b = '#123456';")).toContain('#123456');
  });

  it('sáu chặng của đầu trang / chân trang đều bọc `var()`', () => {
    // Bỏ sót MỘT chặng là một khuyết tật nhìn thấy được: đặt màu chân trang mà dải bản quyền ở
    // đáy vẫn navy ⇒ một vệt lạc lõng, và người dùng đi tìm ô nhập thứ hai ⛔ hề tồn tại.
    for (const ten of ['header', 'headerMid', 'footer', 'footerMid', 'footerDeep', 'footerBand']) {
      expect(maCauHinh, `chrome.${ten} phải đổi được lúc chạy`).toMatch(
        new RegExp(`\\b${ten}:\\s*doiDuocLucChay\\(`),
      );
    }
  });

  it('⭐⭐ MỘT biến, NHIỀU giá trị dự phòng — thứ giữ cho mặc định y hệt hôm nay', () => {
    // Đây là bất biến TRUNG TÂM của T77.1, và nó dễ bị "dọn dẹp" làm hỏng trong im lặng.
    //
    // Sáu chặng trỏ vào đúng HAI biến; mỗi chặng giữ dự phòng RIÊNG của nó. Ai gom chúng về một
    // dự phòng chung (`doiDuocLucChay('brand-footer', portalChrome.footer)` cho cả ba chặng) sẽ
    // thấy bộ kiểm vẫn xanh ở mọi bài khác — nhưng **chân trang mặc định thôi có dải chuyển sắc**,
    // tức lượt giao hàng ⛔ còn giống thứ Công ty nghiệm thu, mà ⛔ ai đặt màu nào cả.
    const goi = [...maCauHinh.matchAll(/doiDuocLucChay\('([a-z-]+)',\s*([A-Za-z][\w.]*)\)/g)];
    expect(goi.length, 'phải đọc ra được các lời gọi — regex hỏng thì mọi vế dưới vô nghĩa').toBe(
      9,
    );

    const theoBien = new Map<string, string[]>();
    for (const [, bien, duPhong] of goi) {
      expect(duPhong, 'dự phòng phải LẤY TỪ token, ⛔ gõ tay').toMatch(
        /^(brandColors|portalChrome)\./,
      );
      theoBien.set(bien, [...(theoBien.get(bien) ?? []), duPhong]);
    }

    for (const bien of ['brand-header', 'brand-footer']) {
      const duPhong = theoBien.get(bien) ?? [];
      expect(duPhong.length, `${bien} phải phủ nhiều chặng gradient`).toBeGreaterThan(1);
      expect(
        new Set(duPhong).size,
        `${bien}: các chặng phải giữ dự phòng KHÁC NHAU, nếu không dải chuyển sắc mặc định xẹp mất`,
      ).toBe(duPhong.length);
    }
  });

  it('đầu trang và chân trang thật sự DÙNG các chặng ấy — ⛔ còn `chrome-navy` nào sót', () => {
    // Vế chống-token-mồ-côi (quy tắc 15). Khai màu trong `tailwind.config.ts` mà component vẫn
    // gọi `chrome-navy800` thì bốn ô nhập lại thành nửa cặp đọc–ghi — đúng `site.color.*` lần hai.
    const doc = (ten: string) =>
      boChuThich(
        readFileSync(join(dirname(fileURLToPath(import.meta.url)), '../components', ten), 'utf8'),
      );
    const dauTrang = doc('SiteHeader.tsx');
    const chanTrang = doc('SiteFooter.tsx');

    expect(dauTrang).toContain('from-chrome-header via-chrome-headerMid to-chrome-header');
    expect(chanTrang).toContain('from-chrome-footer via-chrome-footerMid to-chrome-footerDeep');
    expect(chanTrang).toContain('bg-chrome-footerBand/80');
    expect(chanTrang).toContain('bg-chrome-footer ');

    expect(dauTrang, 'đầu trang còn sắc độ ⛔ đi qua núm').not.toMatch(/chrome-navy/);
    expect(chanTrang, 'chân trang còn sắc độ ⛔ đi qua núm').not.toMatch(/chrome-navy/);
  });

  it('⛔⛔ Tailwind phải là bản 4 trở lên — bản 3 làm opacity modifier VỠ TRONG IM LẶNG', () => {
    const pkg = JSON.parse(
      readFileSync(join(dirname(fileURLToPath(import.meta.url)), '../../package.json'), 'utf8'),
    );
    const ban = (pkg.devDependencies?.tailwindcss ?? pkg.dependencies?.tailwindcss ?? '') as string;
    const chinh = Number(ban.replace(/^[^0-9]*/, '').split('.')[0]);
    expect(chinh, `đo được tailwindcss=${ban}`).toBeGreaterThanOrEqual(4);
  });
});
