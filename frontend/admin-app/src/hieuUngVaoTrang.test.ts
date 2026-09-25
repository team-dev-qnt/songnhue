import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './testsupport/boChuThich';

/**
 * ⛔⛔ Hoạt ảnh GIỮ KHUNG CUỐI ⛔ được để lại một `transform` — T67.7 (WS-67).
 *
 * <h2>Khuyết tật (đo 19/09/2026 trên trình duyệt thật, có sẵn từ phase 1)</h2>
 *
 * `.sn-page-enter` (bọc nội dung MỌI trang quản trị) chạy `sn-fade-in` với `fill-mode: both`, và
 * khung cuối là `transform: translateY(0)`. Trình duyệt tính ra `matrix(1, 0, 0, 1, 0, 0)` — ⛔ phải
 * `none` — và GIỮ nó mãi sau khi hoạt ảnh xong. Một `transform` khác `none` biến phần tử ấy thành
 * **khối chứa** của mọi con cháu `position: fixed`, nên `WallFrame` (`position: fixed; inset: 0`)
 * bị nhốt trong div ấy: cao đúng **24 px**, nội dung biến mất — ở cả 1440 lẫn 1920 px, trên cả antd 5
 * lẫn antd 6. Phép đo: đi ngược tổ tiên của `[data-testid="khung-wall"]`, đúng MỘT tổ tiên có
 * thuộc tính tạo khối chứa, và đó là `.sn-page-enter`.
 *
 * <h2>Vì sao canh CẢ LỚP chứ ⛔ riêng `sn-fade-in`</h2>
 *
 * Luật 28: bộ canh phải ĐO phạm vi. Ở đây nó liệt kê mọi luật có `animation` giữ khung cuối
 * (`both`/`forwards`), tra `@keyframes` tương ứng, rồi đòi khung cuối ⛔ mang `transform` NÀO — kể cả
 * `none`. ⚠ Bản vá đầu của tôi đổi khung cuối thành `none` và bộ canh bản đầu tha nó; đo trên Chrome
 * thì phần tử VẪN mang `matrix(1, 0, 0, 1, 0, 0)`: trình duyệt nội suy `translateY(8px)` → `none` bằng
 * hàm đồng nhất. Thứ phải bỏ là việc GIỮ khung cuối (`backwards`). Hoạt ảnh mới rơi đúng vào lưới.
 *
 * ⚠ Giới hạn: chỉ đọc `admin-global.css` (tệp CSS toàn cục duy nhất bọc trang). jsdom ⛔ tính khối
 * chứa, nên bài này canh NGUYÊN NHÂN (khung cuối), ⛔ canh hậu quả (chiều cao khung wall).
 */
/**
 * ⚠ ⛔ dùng `import.meta.url`: dưới jsdom nó là `http://localhost/…` (xem `setup.ts`). Đi lên từ
 * `process.cwd()` tới khi gặp `src/admin-global.css` — cùng khuôn `noHardcodedColors.test.ts`.
 */
function timCss(): string {
  let d = process.cwd();
  for (let i = 0; i < 5; i++) {
    for (const ung of [
      join(d, 'src/admin-global.css'),
      join(d, 'admin-app/src/admin-global.css'),
    ]) {
      if (existsSync(ung)) return ung;
    }
    d = dirname(d);
  }
  throw new Error(`⛔ Không tìm thấy admin-global.css tính từ ${process.cwd()}`);
}
const CSS = readFileSync(timCss(), 'utf8');

/** Thân khối `{…}` bắt đầu ở `mo` (vị trí của `{`) — cân ngoặc, ⛔ regex tham lam. */
function khoi(src: string, mo: number): string {
  let sau = 0;
  for (let i = mo; i < src.length; i++) {
    if (src[i] === '{') sau++;
    else if (src[i] === '}' && --sau === 0) return src.slice(mo + 1, i);
  }
  throw new Error('khối CSS ⛔ đóng');
}

/**
 * ⭐ T28.41 — bản chép riêng ở đây đã GỠ (25/09/2026). Nó là một trong **tám** bản `boChuThich`
 * với **sáu** thuật toán khác nhau mà bốn lượt đo trước đều đếm thiếu; bản này thuộc nhóm yếu
 * nhất — ⛔ xử lý `//` một chút nào, nên một chú thích dòng nhắc tới thứ bị cấm vẫn được tính là mã. Nay dùng chung bản lexer, bản duy nhất bỏ qua được chuỗi ký tự.
 */

/** Tên keyframes → thân khung cuối (`to` hoặc `100%`). */
function khungCuoi(src: string): Map<string, string> {
  const ra = new Map<string, string>();
  for (const m of src.matchAll(/@keyframes\s+([\w-]+)\s*\{/g)) {
    const than = khoi(src, m.index + m[0].length - 1);
    const cuoi = [...than.matchAll(/(?:^|[\s}])(to|100%)\s*\{/g)].pop();
    if (cuoi) ra.set(m[1], khoi(than, cuoi.index + cuoi[0].length - 1));
  }
  return ra;
}

/** Vi phạm: `animation` giữ khung cuối mà khung ấy mang `transform` — bất kỳ giá trị nào, kể cả `none`. */
function viPham(cssGoc: string): string[] {
  const src = boChuThich(cssGoc);
  const cuoi = khungCuoi(src);
  const ra: string[] = [];
  for (const m of src.matchAll(/animation\s*:\s*([^;}]+)[;}]/g)) {
    const giaTri = m[1];
    if (!/\b(both|forwards)\b/.test(giaTri)) continue;
    for (const [ten, than] of cuoi) {
      if (!new RegExp(`(^|[\\s,])${ten}([\\s,]|$)`).test(giaTri)) continue;
      const t = /transform\s*:\s*([^;]+)/.exec(than);
      if (t) ra.push(`${ten}: khung cuối "transform: ${t[1].trim()}" được GIỮ (${giaTri.trim()})`);
    }
  }
  return ra;
}

describe('Hoạt ảnh giữ khung cuối ⛔ để lại transform (T67.7)', () => {
  it('⛔⛔ admin-global.css: ⛔ hoạt ảnh nào vừa GIỮ khung cuối vừa có transform ở khung ấy', () => {
    expect(
      viPham(CSS),
      'Một transform còn giữ lại sau hoạt ảnh biến phần tử thành khối chứa của mọi con cháu ' +
        'position: fixed — khung wall mode bị nhốt còn 24 px (T67.7)',
    ).toEqual([]);
  });

  it('⛔ chống tập rỗng: bộ đọc PHẢI thấy `sn-fade-in` (có transform ở khung cuối) và `.sn-page-enter` dùng nó', () => {
    const cuoi = khungCuoi(boChuThich(CSS));
    expect(cuoi.has('sn-fade-in')).toBe(true);
    expect(cuoi.get('sn-fade-in')).toMatch(/transform/);
    expect(boChuThich(CSS)).toMatch(/\.sn-page-enter\s*\{[^}]*animation\s*:[^;]*sn-fade-in/);
  });

  it('⭐ tự kiểm: bắt `translateY(0)` VÀ `none` được giữ; tha `backwards` và tha khung cuối ⛔ transform', () => {
    const hong =
      '@keyframes a { from { transform: scale(0) } to { transform: translateY(0); } } .x { animation: a 1s both; }';
    const giuNone =
      '@keyframes a { from { transform: scale(0) } to { transform: none; } } .x { animation: a 1s both; }';
    const backwards =
      '@keyframes a { from { transform: scale(0) } to { transform: translateY(0); } } .x { animation: a 1s backwards; }';
    const khongTransform =
      '@keyframes c { from { opacity: 0 } to { opacity: 1 } } .z { animation: c 1s both; }';
    const khongGiu =
      '@keyframes a { to { transform: translateY(0); } } .x { animation: a 1s ease; }';
    const phanTram =
      '@keyframes b { 0% { opacity: 0 } 100% { transform: rotate(0deg) } } .y { animation: b 1s forwards; }';
    expect(viPham(hong)).toHaveLength(1);
    expect(viPham(giuNone), '`none` vẫn nội suy ra ma trận — phải bị bắt').toHaveLength(1);
    expect(viPham(backwards)).toEqual([]);
    expect(viPham(khongTransform)).toEqual([]);
    expect(viPham(khongGiu)).toEqual([]);
    expect(viPham(phanTram)).toHaveLength(1);
  });
});
