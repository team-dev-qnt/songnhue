import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';

/**
 * Nhóm 5 "Video giới thiệu" — hai cột phải CHIA ĐÔI, không phải 7/5.
 *
 * <h2>Bất biến này canh cái gì</h2>
 *
 * Khung video và khung ảnh cùng mang `aspect-[16/9]`. Cùng tỉ lệ chỉ cho ra cùng chiều cao khi
 * **cùng bề rộng** — nên hai ô lưới buộc phải cùng số cột. Bản 7/5 trước đó có đủ mọi thứ đúng
 * *trừ* điều kiện ấy, và hệ quả đo được trên trình duyệt 01/09 là:
 *
 * <pre>
 *   ≥1280   video 673,7×378,9  ·  ảnh 472,3×265,7  ⇒ lệch rộng 201,4px · lệch cao 113,2px
 *   1024    video 552,3×310,7  ·  ảnh 385,7×216,9  ⇒ lệch rộng 166,6px · lệch cao  93,8px
 * </pre>
 *
 * Sau khi chia đôi: **lệch 2,0px rộng / 1,1px cao ở cả bốn bề rộng** — đúng bằng viền 1px mỗi
 * bên của thẻ bọc video, không hơn.
 *
 * <h2>⚠ Phạm vi của chính bài này (luật 28)</h2>
 *
 * Bài này đọc **chuỗi lớp trong mã nguồn**. Nó không chứng minh được hai khung trùng khít trên
 * trình duyệt — việc ấy thuộc `e2e/boCucTrangChu.spec.ts` ("Nhóm 5: khung video và khung ảnh
 * TRÙNG KHÍT"), chạy trên stack thật và **chưa vào CI** (T38.10). Cái xanh của bài này chỉ nói
 * *"không ai đổi lại thành hai số khác nhau"*, không nói *"bố cục đúng"*.
 */

const NGUON = boChuThich(
  readFileSync(join(process.cwd(), 'src/components/home/HomeMediaGallery.tsx'), 'utf8'),
);

/** Mọi `lg:col-span-N` trong phần THI HÀNH (chú thích đã bị cắt trước đó). */
function moiSpan(ma: string): number[] {
  return [...ma.matchAll(/lg:col-span-(\d+)/g)].map((m) => Number(m[1]));
}

describe('HomeMediaGallery — hai cột của khối Video chia đôi', () => {
  it('⛔ TIỀN ĐỀ: cắt chú thích xong phần thi hành vẫn còn nguyên', () => {
    // Một bộ cắt quá tay không làm bài nào đỏ — nó biến mọi khẳng định dưới thành xanh vĩnh
    // viễn (đúng thứ đã đo được 01/09: bản cắt cũ nuốt 8.174 ký tự của `AnhCarousel.tsx`).
    expect(NGUON).toContain('export function HomeMediaGallery');
    expect(NGUON).toContain('data-khung-video');
    expect(NGUON).toContain('tiLeKhung="aspect-[16/9]"');
  });

  it('⭐⭐ mọi `lg:col-span-*` của khối này là CÙNG MỘT số', () => {
    const spans = moiSpan(NGUON);
    expect(spans.length, 'không thấy ô lưới nào — bài kiểm đang soi tập rỗng').toBe(2);

    // Khẳng định về SỐ LƯỢNG giá trị phân biệt, không phải "phải bằng 6". Nó không chia sẻ
    // giả định nào với con số cụ thể, nên vẫn bắt được nếu ai đó đổi sang 5/7 hay 4/8.
    expect(
      new Set(spans).size,
      `hai cột khác số ô lưới (${spans.join('/')}) ⇒ hai khung 16:9 khác chiều cao`,
    ).toBe(1);
  });

  it('tổng hai cột phủ kín 12 ô — không để hở một dải trống bên phải', () => {
    const spans = moiSpan(NGUON);
    expect(spans.reduce((a, b) => a + b, 0)).toBe(12);
  });

  it('⭐ khung video mang mốc đo `data-khung-video` và đúng tỉ lệ của khung ảnh', () => {
    // Hai khung phải cùng tỉ lệ; nếu không thì bề rộng bằng nhau cũng vô ích.
    const khoiVideo = NGUON.slice(
      NGUON.indexOf('data-khung-video'),
      NGUON.indexOf('data-khung-video') + 120,
    );
    expect(khoiVideo.length).toBeGreaterThan(50);
    expect(khoiVideo).toContain('aspect-[16/9]');
  });

  it('⭐⭐ KIỂM CHỨNG NGƯỢC: chuỗi 7/5 nguyên văn của bản cũ phải BỊ BẮT', () => {
    // Luật 1 + 29. Nếu bản cũ vẫn qua được thì bộ canh này không canh gì.
    const banCu = `
      <div className="mt-5 grid grid-cols-1 items-stretch gap-6 lg:grid-cols-12 lg:gap-9">
        <div className="flex flex-col lg:col-span-7">
        <div className="flex flex-col lg:col-span-5">
    `;
    const spansCu = moiSpan(boChuThich(banCu));
    expect(spansCu).toEqual([7, 5]);
    expect(new Set(spansCu).size, 'bản 7/5 vẫn lọt ⇒ khẳng định trên vô nghĩa').toBe(2);

    // Và một chuỗi có chú thích NHẮC TỚI 7/5 thì KHÔNG được bắt — đó là chỗ bộ cắt phải làm việc.
    const chiLaChuThich = `
      {/* Bản trước chia lg:col-span-7 và lg:col-span-5 — xem lý do ở đây. */}
      <div className="flex flex-col lg:col-span-6">
      <div className="flex flex-col lg:col-span-6">
    `;
    expect(new Set(moiSpan(boChuThich(chiLaChuThich))).size).toBe(1);
  });
});
