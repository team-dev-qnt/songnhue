import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';

/**
 * **Bề rộng của ô ảnh chỉ được đặt qua prop `rong` của `PortalImage`.**
 *
 * <h2>Lỗi mà bài này sinh ra để chặn — đo được, không phải giả định</h2>
 *
 * `PortalImage` đặt `w-full` cho khung ngoài rồi nối `className` của nơi gọi vào **cùng một
 * chuỗi lớp**. Ba nơi gọi truyền `w-[103px]`, và cả ba đều không có tác dụng: đo trên CSS đang
 * chạy ngày 29/08, `.w-\[103px\]` ở byte 28977 còn `.w-full` ở byte 29021 — cùng độ ưu tiên,
 * lớp đứng sau thắng, và lớp đứng sau luôn là `w-full`.
 *
 * <p>Triệu chứng không phải "ảnh hơi to": ảnh chiếm trọn hàng `flex`, ô chữ `min-w-0` bên cạnh
 * co về bề rộng 0, nên **tiêu đề bài viết biến mất khỏi trang chủ** còn dòng ngày tràn sang cột
 * kế bên. Đúng thứ nghiệm thu 29/08 mô tả là *"một số bài viết chỉ hiển thị ảnh và không hiển
 * thị title"*.
 *
 * <h2>Vì sao một bài kiểm chứ không phải một dòng chú thích</h2>
 *
 * `w-[103px] shrink-0 rounded-md` là chuỗi lớp mà **bất kỳ ai** cũng sẽ viết — nó đọc đúng, nó
 * được Tailwind sinh ra thật, và nó im lặng. Luật 14: chỗ nào con người phải nhớ hai nơi thì
 * chỗ đó cần một phép kiểm nhớ hộ.
 *
 * <p>⚠ Bài này canh **nơi gọi**; ràng buộc ở phía `PortalImage` (một ô lớp duy nhất cho bề rộng)
 * được canh riêng bên dưới. Thiếu vế thứ hai thì ai đó thêm lại `w-full` cứng vào khung là mọi
 * `rong` chết lặng mà bài vẫn xanh (luật 28 — bộ canh phải nói ra phạm vi của chính nó).
 */
const GOC = join(process.cwd(), 'src');

/**
 * Lớp bề rộng của Tailwind, kể cả biến thể theo bề rộng màn hình.
 *
 * ⚠ Phải có ký tự phân cách đứng trước `w-`: nếu không thì `max-w-full` và `min-w-0` — hai lớp
 * hợp lệ và cần thiết ở nơi gọi — bị bắt oan. Bài kiểm chứng ngược bên dưới khẳng định đúng
 * điều đó bằng một con SỐ, không bằng cảm giác.
 */
const LOP_BE_RONG =
  /(?:^|[\s"'`])(?:(?:sm|md|lg|xl|2xl):)?w-(?:full|auto|screen|fit|min|max|px|\d|\[)/;

/** Mỗi lượt gọi `<PortalImage …/>` trong một tệp nguồn. */
const GOI = /<PortalImage\b[^>]*\/>/g;

function timTsx(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) return timTsx(duong);
    return ten.endsWith('.tsx') && !ten.includes('.test.') ? [duong] : [];
  });
}

const MA = timTsx(GOC).map((duong) => ({
  ten: duong.slice(GOC.length + 1),
  nguon: boChuThich(readFileSync(duong, 'utf8')),
}));

const NGUON_PORTAL_IMAGE = MA.find(({ ten }) => ten === 'components/PortalImage.tsx')?.nguon ?? '';

/** Trả về `className` của mọi lượt gọi `PortalImage` có đặt bề rộng trong đó. */
export function beRongDatSaiCho(nguon: string): string[] {
  return (nguon.match(GOI) ?? []).flatMap((the) => {
    const lop = the.match(/className="([^"]*)"/)?.[1];
    return lop && LOP_BE_RONG.test(lop) ? [lop] : [];
  });
}

describe('Bề rộng ô ảnh chỉ đặt qua prop `rong`', () => {
  it('⚠ có tệp để soi VÀ có lượt gọi để soi — chạy qua tập rỗng thì xanh mà không canh gì (luật 7)', () => {
    expect(MA.length).toBeGreaterThanOrEqual(20);
    // Ba nơi gọi truyền bề rộng cộng các nơi gọi khác; ngưỡng đặt dưới con số thật để một lượt
    // dọn dẹp hợp lệ không làm đỏ, nhưng đủ để bắt trường hợp regex `GOI` thôi khớp.
    const soGoi = MA.reduce((tong, { nguon }) => tong + (nguon.match(GOI) ?? []).length, 0);
    expect(soGoi).toBeGreaterThanOrEqual(5);
  });

  it('⛔ không nơi gọi nào đặt lớp bề rộng trong `className`', () => {
    const pham = MA.flatMap(({ ten, nguon }) =>
      beRongDatSaiCho(nguon).map((lop) => `${ten}: ${lop}`),
    );
    expect(
      pham,
      'Bề rộng phải truyền qua prop `rong`. Đặt trong `className` thì `w-full` của khung ' +
        'thắng (nó đứng sau trong tệp CSS) — ảnh kín bề rộng cột, ô chữ co về 0, tiêu đề biến mất.',
    ).toEqual([]);
  });

  it('⭐ `PortalImage` phát ĐÚNG MỘT ô lớp bề rộng, và mặc định là `w-full`', () => {
    expect(
      /rong = 'w-full'/.test(NGUON_PORTAL_IMAGE),
      'Mất mặc định thì nơi gọi nào quên `rong` sẽ có khung không bề rộng.',
    ).toBe(true);
    expect(
      NGUON_PORTAL_IMAGE.includes('${ratio} ${rong} overflow-hidden'),
      '`rong` phải nằm ở đúng ô mà `w-full` từng chiếm trong chuỗi lớp của KHUNG NGOÀI.',
    ).toBe(true);
    // Khung ngoài không được còn `w-full` ghi cứng — nếu còn thì mọi `rong` lại thua như cũ.
    const khungNgoai = NGUON_PORTAL_IMAGE.match(/<div className=\{`relative[^`]*`\}/)?.[0] ?? '';
    expect(khungNgoai.length, 'Không tìm thấy khung ngoài để soi').toBeGreaterThan(30);
    expect(khungNgoai).not.toMatch(/\bw-full\b/);
  });

  it('⭐⭐ bài kiểm chứng ngược — bộ canh THẤY vi phạm, và KHÔNG thấy lớp hợp lệ', () => {
    // ⚠ Chuỗi lớp dựng lúc chạy: viết thẳng `w-[103px]` vào tệp này là nạp nó vào bộ quét
    //   nguồn của Tailwind, và lớp ấy quay lại CSS dù không component nào dùng.
    const w = (px: number) => 'w-' + `[${px}px]`;
    const hong = `<PortalImage src={x} alt="" ratio="aspect-[103/68]" className="${w(103)} shrink-0 rounded-md" />`;
    const hongBienThe = `<PortalImage src={x} alt="" className="shrink-0 lg:${w(120)}" />`;
    const lanh = `<PortalImage src={x} alt="" rong="${w(103)}" className="min-w-0 max-w-full shrink-0 rounded-md" />`;

    // Khẳng định về SỐ LƯỢNG, không về hình dạng: nó không chia sẻ giả định nào với `LOP_BE_RONG`
    // (luật 29 — hai lượt kiểm chứng ngược từng sai theo đúng cách thứ chúng kiểm chứng đang sai).
    expect(beRongDatSaiCho(hong + '\n' + hongBienThe + '\n' + lanh)).toHaveLength(2);
    expect(beRongDatSaiCho(lanh)).toEqual([]);
    expect(beRongDatSaiCho('<PortalImage src={x} alt="" className="rounded-lg" />')).toEqual([]);
  });
});
