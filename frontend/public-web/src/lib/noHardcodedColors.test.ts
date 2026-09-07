import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';

/**
 * **Không component nào của cổng được khai mã màu tại chỗ.**
 *
 * <h2>Lỗi đã có thật, và nó nằm ở đúng chỗ dễ tin là đã xong nhất</h2>
 *
 * `docs/ui-styles.md` §2.1 viết ⛔ *"Cấm khai màu cứng (hardcoded hex/rgb) tại chỗ trong
 * page/component"* từ WS-15. Đo ngày 28/08/2026: `SiteHeader` và `SiteFooter` có **12 chỗ**
 * ghi hex thẳng vào class Tailwind, gồm **7 sắc navy khác nhau** cho cái lẽ ra là một dải màu.
 *
 * <p>Tệ hơn con số: §2.3 của chính tài liệu ấy ghi dải gradient navbar là
 * `from-[#0c366e] … to-[#0c366e]`, trong khi thứ chạy thật là `#061b37`. Tài liệu và mã nói hai
 * chuyện khác nhau suốt 13 ngày, và **đọc bên nào cũng thấy hợp lý** — không có lượt kiểm nào
 * đối chiếu chúng. Đúng hình dạng quy tắc 14: *chỗ nào con người phải nhớ hai nơi thì chỗ đó
 * cần một phép kiểm nhớ hộ*.
 *
 * <h2>Vì sao canh ở tầng mã nguồn chứ không so ảnh chụp màn hình</h2>
 *
 * Thứ cần khẳng định không phải *màu nào hiện ra* — hai bên đang hiện đúng cùng một màu — mà là
 * **màu ấy khai ở đâu**. Một mã màu chép tay vẫn cho ra đúng pixel; nó chỉ hỏng vào ngày ai đó
 * đổi token và bảy chỗ chép tay ở lại phía sau. Nguồn của một giá trị thì nhìn thấy ở mã, không
 * nhìn thấy ở ảnh (cùng lý lẽ với `noFabricatedContent.test.ts`).
 *
 * <h2>⚠ Phạm vi: CHỈ `public-web` — và giới hạn ấy đã có bộ canh song sinh từ 07/09/2026</h2>
 *
 * `admin-app` có bộ canh riêng: `admin-app/src/shared/noHardcodedColors.test.ts`. Ghi ra giới hạn
 * là đúng (luật 28), nhưng lượt đo 07/09 cho thấy **ghi ra là chưa đủ**: câu ở đây trước hôm ấy
 * viết *"admin-app còn 25 mã hex ở 12 tệp (đo 28/08/2026)"*, và trong 10 ngày ⛔ không có gì đỏ khi
 * ai đó thêm màu mới bên ấy — đo lại ra **62 mã / 16 tệp**, gấp **2,4 lần**, trong đó
 * `richTextEditor.css` mang 18 mã và ra đời ngày 04/09, tức **sau** lượt đo cũ.
 *
 * <p>⇒ Bài học: **một con số ghi trong chú thích đứng yên từ ngày viết, còn mã thì không.** Ghi
 * giới hạn ra là để người sau biết; chặn nó lớn lên thì phải có một bộ canh. Nợ: **T25.23**.
 * (Câu cũ trỏ nợ vào `T25.14` — mục ấy là *chân trang*, đã đóng 28/08. Một con trỏ sai.)
 *
 * ⚠ Bỏ chú thích trước khi soi — dùng chung `boChuThich` với bộ canh dữ liệu bịa. Ghi chú
 * *giải thích* một mã màu (và tệp này, và `design-tokens`) phải được phép nhắc tới nó; cấm cả
 * trong chú thích là buộc người sau phải mô tả lịch sử mà không được gọi tên nó.
 */

const GOC = join(process.cwd(), 'src');

/** Hex 3 hoặc 6 chữ số, có `#` đứng trước — dạng duy nhất Tailwind nhận trong `[...]`. */
const HEX = /#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?\b/g;

/** `rgb()` / `rgba()` / `hsl()` — cùng luật, khác cú pháp. */
const HAM_MAU = /\b(?:rgba?|hsla?)\s*\(/g;

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

describe('Màu của cổng chỉ đến từ design-tokens', () => {
  it('⚠ tìm được tệp để soi — bài kiểm chạy qua tập rỗng thì xanh mà không canh gì (luật 7)', () => {
    expect(MA.length).toBeGreaterThanOrEqual(30);
    // Và tệp phải có nội dung sau khi bỏ chú thích: một `boChuThich` hỏng trả về chuỗi rỗng
    // sẽ làm mọi bài dưới đây xanh trọn vẹn.
    expect(MA.reduce((t, m) => t + m.nguon.length, 0)).toBeGreaterThan(50_000);
  });

  it('⛔ không tệp nào khai mã hex', () => {
    const pham = MA.flatMap(({ ten, nguon }) =>
      (nguon.match(HEX) ?? []).map((m) => `${ten}: ${m}`),
    );
    expect(
      pham,
      'Khai màu qua `design-tokens` rồi dùng class Tailwind (`bg-chrome-navy800`, ' +
        '`text-brand-gold`). `ui-styles.md` §2.1. Nếu đây là màu mới thì thêm vào ' +
        '`frontend/design-tokens/src/index.ts` — đó là nơi duy nhất một mã màu được viết ra.',
    ).toEqual([]);
  });

  it('⛔ không tệp nào gọi rgb()/hsl()', () => {
    const pham = MA.flatMap(({ ten, nguon }) =>
      (nguon.match(HAM_MAU) ?? []).map((m) => `${ten}: ${m}`),
    );
    expect(pham).toEqual([]);
  });

  it('⛔ kiểm chứng ngược: hai bộ canh bắt được đúng thứ chúng phải bắt', () => {
    // Luật 1 — mỗi cơ chế canh gác phải có bài chứng minh nó bắt được vi phạm. Không có bài
    // này thì một regex gõ sai vẫn cho hai bài trên xanh trọn vẹn mãi mãi.
    expect('className="bg-[#061b37]"'.match(HEX)).toEqual(['#061b37']);
    expect('color: #fff;'.match(HEX)).toEqual(['#fff']);
    expect('rgba(0, 0, 0, 0.08)'.match(HAM_MAU)).toEqual(['rgba(']);
    // …và không bắt nhầm thứ không phải màu:
    expect('href="/bai-viet#muc-2"'.match(HEX)).toBeNull();
    expect('const n = 12;'.match(HEX)).toBeNull();
  });

  it('⭐ chú thích được phép nhắc tới mã màu — nếu không, không ai giải thích được lịch sử', () => {
    expect(boChuThich('// dải cũ là #061b37\nconst a = 1;').match(HEX)).toBeNull();
    expect(boChuThich('/* #0c366e ở ui-styles.md */ const a = 1;').match(HEX)).toBeNull();
  });
});
