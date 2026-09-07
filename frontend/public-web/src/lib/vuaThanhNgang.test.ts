import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { GAP_NGOAI_PX, vuaThanhNgang } from './vuaThanhNgang';

/**
 * **Ngưỡng của thanh điều hướng là một phép đo, và đây là phần kết luận của phép đo ấy.**
 *
 * Số dùng trong bài này KHÔNG bịa: chúng lấy từ lượt đo ngày 28/08 bằng chính font đang chạy
 * (Noto Sans 600 của `@fontsource`, qua fontTools) trên tám nhãn thật của `/menus/HEADER`.
 * <p>⚠ Khung chứa = 1232px trừ `px-6` hai bên = **1184px** — không phải 1192. Cho tới 29/08 bài
 * này lấy 1192 (suy từ bề rộng khung CŨ, 1240) trong khi `PortalNav` đã chạy ở 1232: bộ canh rộng
 * hơn nơi nó phải chặn đúng **8px**, tức nó xác nhận "vừa" cho một cấu hình tràn 8px thật. Đúng luật 28 ở dạng nhỏ nhất — một bộ canh không nói ra phạm vi
 * của chính nó thì phạm vi ấy trôi. Cùng lượt này bề rộng khung của cả cổng gom về 1232px
 * (đầu trang, thanh điều hướng, dải thông tin, trang chủ, các trang trong, chân trang).
 */
const KHUNG = 1184;

/** Nút Tìm kiếm ở cỡ 12px `px-2`: đệm 16 + kính lúp 16 + `gap-1.5` 6 + chữ "TÌM KIẾM" ≈ 51. */
const TIM = 89;

describe('Thanh ngang có vừa khung không', () => {
  it('⭐ bộ nhãn đang chạy — HOA 12px px-2 — thì VỪA', () => {
    // Tổng đo được 1150,6px; trừ gap và nút Tìm kiếm ra bề rộng riêng của thanh mục.
    expect(vuaThanhNgang({ trong: KHUNG, thuoc: 1150.6 - GAP_NGOAI_PX - TIM, tim: TIM })).toBe(
      true,
    );
  });

  it('⛔ chính bộ nhãn ấy ở cỡ cũ — HOA 13px px-3 — thì KHÔNG vừa', () => {
    // 1297,5px: cấu hình sẽ tràn 105,5px, tức đúng lỗi §10.62 tái phát.
    expect(vuaThanhNgang({ trong: KHUNG, thuoc: 1297.5 - GAP_NGOAI_PX - TIM, tim: TIM })).toBe(
      false,
    );
  });

  it('⭐⭐ vừa KHÍT vẫn là vừa, và thừa một pixel là không', () => {
    // Biên. Một dấu `<` viết nhầm thành `<=` (hoặc ngược lại) chỉ lộ ra ở đúng hai trường hợp này.
    const thuoc = KHUNG - GAP_NGOAI_PX - TIM;
    expect(vuaThanhNgang({ trong: KHUNG, thuoc, tim: TIM })).toBe(true);
    expect(vuaThanhNgang({ trong: KHUNG, thuoc: thuoc + 1, tim: TIM })).toBe(false);
  });

  it('⛔ khung bề rộng 0 trả `null`, KHÔNG trả `false`', () => {
    // Tab chạy nền và lượt vẽ để in đều cho `clientWidth = 0`. Trả `false` ở đó là đá thanh về
    // ngăn kéo rồi bật lại khi người dùng quay lại tab — nhấp nháy không ai lần ra nguyên nhân.
    expect(vuaThanhNgang({ trong: 0, thuoc: 100, tim: TIM })).toBeNull();
    expect(vuaThanhNgang({ trong: -5, thuoc: 100, tim: TIM })).toBeNull();
    expect(vuaThanhNgang({ trong: Number.NaN, thuoc: 100, tim: TIM })).toBeNull();
    expect(vuaThanhNgang({ trong: KHUNG, thuoc: Number.NaN, tim: TIM })).toBeNull();
  });

  it('⭐ nút Tìm kiếm được TÍNH VÀO — nếu không, thanh sẽ đè lên nó', () => {
    // Bỏ quên nó là lỗi dễ mắc nhất: thanh mục vừa khít khung, nhưng nút Tìm kiếm nằm cùng hàng.
    const thuoc = KHUNG - GAP_NGOAI_PX - TIM + 1;
    expect(vuaThanhNgang({ trong: KHUNG, thuoc, tim: TIM })).toBe(false);
    expect(vuaThanhNgang({ trong: KHUNG, thuoc, tim: 0 })).toBe(true);
  });
});

/**
 * **Mọi khung trang của cổng phải cùng một bề rộng.**
 *
 * <h2>Vì sao cần một bài kiểm chứ không phải một lời dặn</h2>
 *
 * Đo ngày 29/08: `SiteHeader`, `PortalNav`, `PortalInfoStrip` và trang chủ chạy
 * `max-w-[1232px]`, còn `SiteFooter`, `PageShell`, trang bài viết, trang danh mục và trang tìm
 * kiếm chạy khung CŨ rộng hơn 8px. Tức trên MỌI trang trong, mép trái của đầu trang và mép của
 * thân trang lệch nhau 4px — đủ để nhìn ra, không đủ để ai chỉ được tên nó, và không có gì đỏ.
 *
 * <p>Đây đúng luật 14: chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ.
 * Bài này đếm thay.
 *
 * ⚠ Chỉ soi các giá trị ≥ 1000px — đó là ngưỡng phân biệt *khung trang* với các `max-w` cục bộ
 * hợp lệ (`max-w-[288px]` của ô tìm kiếm, `min-w-[520px]` của cây tổ chức). Ngưỡng nói ra ở đây
 * chứ không để người đọc tự suy (luật 28).
 */
const BE_RONG_KHUNG = 1232;

function timNguon(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) return timNguon(duong);
    return /\.tsx?$/.test(ten) && !ten.includes('.test.') ? [duong] : [];
  });
}

describe('Bề rộng khung trang', () => {
  const GOC = join(process.cwd(), 'src');
  const TEP = timNguon(GOC);

  it('⚠ tìm được tệp để soi — bài chạy qua tập rỗng thì xanh mà không canh gì (luật 7)', () => {
    expect(TEP.length).toBeGreaterThan(20);
  });

  it(`⭐ mọi khung ≥ 1000px đều đúng ${BE_RONG_KHUNG}px`, () => {
    const lech = TEP.flatMap((duong) => {
      const nguon = readFileSync(duong, 'utf8');
      return [...nguon.matchAll(/max-w-\[(\d{4,})px\]/g)]
        .map(([, so]) => Number(so))
        .filter((so) => so >= 1000 && so !== BE_RONG_KHUNG)
        .map((so) => `${duong.slice(GOC.length + 1)} → max-w-[${so}px]`);
    });
    expect(
      lech,
      `khung lệch bề rộng: ${lech.join(', ')} — đầu trang và thân trang phải cùng một mép`,
    ).toEqual([]);
  });

  it('⛔ kiểm chứng ngược: mẫu bắt được một bề rộng lệch, và bỏ qua max-w cục bộ', () => {
    const bat = (chuoi: string) =>
      [...chuoi.matchAll(/max-w-\[(\d{4,})px\]/g)]
        .map(([, so]) => Number(so))
        .filter((so) => so >= 1000 && so !== BE_RONG_KHUNG);

    // ⚠ Tên lớp GHÉP lúc chạy, không viết liền một mạch trong mã. Bộ dò nguồn của Tailwind quét
    //   cả tệp kiểm và không hiểu ngữ cảnh, nên một mẫu vi phạm viết thẳng ở đây sẽ **sinh ra lớp
    //   CSS thật** trong bó đang chạy — và lúc ấy câu hỏi "bó CSS còn bề rộng cũ không" không trả
    //   lời được bằng chính bó CSS nữa. Đo được 29/08: lớp cũ vẫn nằm trong bó sau khi mọi tệp
    //   nguồn đã đổi, chỉ vì bài kiểm này (và một dòng chú thích) còn nhắc tên nó.
    const lop = (px: number) => `max-w-` + `[${px}px]`;
    expect(bat(`<div className="mx-auto ${lop(1240)} px-4">`)).toEqual([1240]);
    expect(bat(`<form className="w-full ${lop(288)} shrink-0">`)).toEqual([]);
    expect(bat(`<div className="mx-auto ${lop(BE_RONG_KHUNG)}">`)).toEqual([]);
  });
});
