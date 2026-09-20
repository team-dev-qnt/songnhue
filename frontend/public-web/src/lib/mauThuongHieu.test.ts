import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { brandColors } from '@songnhue/design-tokens';
import { describe, expect, it } from 'vitest';

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
    for (const [khoa, bien] of ANH_XA_MAU) {
      const hauTo = khoa.replace('site.brand.', '');
      expect(khoa).toMatch(/^site\.brand\.[a-z]+$/);
      expect(bien).toBe(`--sn-brand-${hauTo}`);
      expect(brandColors).toHaveProperty(hauTo);
    }
  });

  it('⛔ nhận thêm khoá nào ngoài hai vai trò của bộ nhận diện', () => {
    // Trần chỉ-được-giảm. Thêm một núm màu là một quyết định (nền trang và màu chữ CỐ Ý ⛔ có núm —
    // xem javadoc `brandColors`), ⛔ phải một dòng thêm vào mảng cho tiện.
    expect(ANH_XA_MAU.map(([k]) => k)).toEqual(['site.brand.primary', 'site.brand.accent']);
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
 */
/**
 * Bỏ chú thích `//` và `/* *\/` nhưng GIỮ nguyên chuỗi ký tự.
 *
 * ⚠ Bỏ khối trước rồi mới tới dòng: làm ngược thì một `//` nằm TRONG khối `/* … *\/` sẽ cắt mất
 * phần đuôi của khối và để hở dấu đóng.
 */
function boChuThich(ma: string): string {
  return ma.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');
}

describe('Tailwind — tầng làm cho biến CSS có tác dụng', () => {
  const cauHinh = readFileSync(
    join(dirname(fileURLToPath(import.meta.url)), '../../tailwind.config.ts'),
    'utf8',
  );

  it('primary · link · accent đều bọc `var()` với token làm dự phòng', () => {
    // Phá một dòng trong số này ⇒ khoá `settings` tương ứng thành nửa cặp đọc–ghi trở lại: admin
    // đặt màu, cổng ⛔ đổi. Đúng khuyết tật `V202608281037` đã gỡ.
    for (const ten of ['primary', 'link', 'accent']) {
      expect(cauHinh, `brand.${ten} phải đổi được lúc chạy`).toMatch(
        new RegExp(`${ten}:\\s*doiDuocLucChay\\(`),
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
    expect(boChuThich(cauHinh)).not.toMatch(/#[0-9a-fA-F]{6}/);
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

  it('⛔⛔ Tailwind phải là bản 4 trở lên — bản 3 làm opacity modifier VỠ TRONG IM LẶNG', () => {
    const pkg = JSON.parse(
      readFileSync(join(dirname(fileURLToPath(import.meta.url)), '../../package.json'), 'utf8'),
    );
    const ban = (pkg.devDependencies?.tailwindcss ?? pkg.dependencies?.tailwindcss ?? '') as string;
    const chinh = Number(ban.replace(/^[^0-9]*/, '').split('.')[0]);
    expect(chinh, `đo được tailwindcss=${ban}`).toBeGreaterThanOrEqual(4);
  });
});
