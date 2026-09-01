import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * `AnhCarousel` phải nhận một TỈ LỆ viết nguyên văn, và khung ảnh không được mang `flex-1`.
 *
 * <h2>Hai cái bẫy bài này canh</h2>
 *
 * <ol>
 *   <li><b>`flex-1` trên khung ảnh</b> — khung nhận chiều cao mà hàng lưới phát cho, nên tỉ
 *       lệ trở thành *tai nạn của cột bên cạnh*. Đo 01/09 ra ba tỉ lệ khác nhau ở ba bề rộng
 *       (1,357 · 3,273 · 1,559) trên cùng một lượt dựng.
 *   <li><b>`tiLeKhung` ghép chuỗi lúc chạy</b> — bộ quét nguồn của Tailwind đọc mã chứ không
 *       chạy mã, nên lớp không được sinh ra và khung tụt về chiều cao 0. Triệu chứng: ảnh
 *       biến mất. **Không bài kiểm nào đỏ, không lỗi nào được ghi.**
 * </ol>
 *
 * ⚠ Bài này canh HÌNH DẠNG mã nguồn. Nó không đo được tỉ lệ thật — việc ấy thuộc
 * `e2e/boCucTrangChu.spec.ts`, chạy trên trình duyệt. Nói ra để cái xanh của bài này không
 * đọc như một lời bảo đảm rộng hơn thứ nó kiểm (luật 28).
 */

function moiTepTsx(thuMuc: string, gom: string[] = []): string[] {
  for (const ten of readdirSync(thuMuc)) {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) moiTepTsx(duong, gom);
    else if (ten.endsWith('.tsx') && !ten.endsWith('.test.tsx')) gom.push(duong);
  }
  return gom;
}

const GOC = join(process.cwd(), 'src');

/**
 * Bỏ chú thích trước khi soi — §10.62 ở chiều ngược lại.
 *
 * ⚠ Lượt viết đầu của bài này KHÔNG cắt, và `indexOf('data-khung-anh')` bắt trúng **chú thích
 * của chính bản vá** (nó nhắc tới `data-khung-anh` và tới `flex-1` để giải thích vì sao gỡ).
 * Khối cắt ra vì thế ôm cả lời giải thích, và bài kiểm đỏ oan ngay lượt chạy đầu.
 * *Canh văn bản thì phải biết văn bản nào đang chạy.*
 */
function boChuThich(ma: string): string {
  return (
    ma
      // ⛔ THỨ TỰ NÀY LÀ BẮT BUỘC: cắt khối `/* … */` TRƯỚC, rồi mới dọn cặp ngoặc rỗng còn
      //    lại của `{/* … */}`.
      //
      //    ⚠⚠ Lượt viết đầu làm ngược — `\{\s*\/\*[\s\S]*?\*\/\s*\}` chạy trước — và **nuốt
      //    mất 8.174 ký tự** của `AnhCarousel.tsx`, gồm cả `export function AnhCarousel`.
      //    Cơ chế: mẫu ấy khớp từ `export interface MucCarousel {` (theo sau là xuống dòng
      //    rồi `/**`), rồi vì sau `*/` không phải `}` nên nó QUAY LUI và kéo dài mãi tới
      //    `*/}` đầu tiên của một chú thích JSX cách đó hàng trăm dòng. Một bộ cắt quá tay
      //    không làm bài nào đỏ — nó chỉ lặng lẽ biến mọi khẳng định dựa vào nó thành xanh
      //    vĩnh viễn. Ở đây nó đỏ chỉ vì có bài canh chính phép cắt (bài cuối describe dưới).
      //
      //    Cắt khối trước thì mỗi chú thích được xử lý riêng lẻ (`*?` dừng ở `*/` gần nhất),
      //    không có chỗ nào để quay lui.
      .replace(/\/\*[\s\S]*?\*\//g, ' ') // /* … */ và /** … */
      .replace(/^\s*\/\/.*$/gm, ' ') // // … trọn dòng
      .replace(/\{\s*\}/g, ' ') // cặp ngoặc rỗng còn lại của {/* … */}
  );
}

const NGUON_CAROUSEL = boChuThich(
  readFileSync(join(GOC, 'components/home/AnhCarousel.tsx'), 'utf8'),
);

describe('AnhCarousel — khung ảnh giữ tỉ lệ của chính nó', () => {
  it('khung ảnh dùng `${tiLeKhung}` và KHÔNG mang `flex-1`', () => {
    const khoi = NGUON_CAROUSEL.slice(
      NGUON_CAROUSEL.indexOf('data-khung-anh'),
      NGUON_CAROUSEL.indexOf('data-khung-anh') + 400,
    );
    expect(khoi.length, 'không tìm thấy khung ảnh — bài kiểm đang soi tập rỗng').toBeGreaterThan(
      100,
    );
    expect(khoi).toContain('${tiLeKhung}');
    expect(khoi, 'khung ảnh mang `flex-1` ⇒ tỉ lệ lại bị hàng lưới quyết định').not.toContain(
      'flex-1',
    );
    expect(khoi, 'thiếu `shrink-0` ⇒ flexbox vẫn ép được chiều cao khung').toContain('shrink-0');
  });

  it('⭐ gốc `<section>` KHÔNG mang `h-full` — nó là mắt xích dựng lại ô trắng 216px', () => {
    const goc = NGUON_CAROUSEL.slice(
      NGUON_CAROUSEL.indexOf('aria-roledescription="carousel"'),
      NGUON_CAROUSEL.indexOf('data-khung-anh'),
    );
    expect(goc.length).toBeGreaterThan(100);
    expect(goc).toContain('flex flex-col');
    expect(
      goc,
      '`h-full` ở gốc kéo thẻ cao bằng cột bên cạnh ⇒ phần dôi thành ô trắng',
    ).not.toContain('h-full');
  });

  it('prop cũ `chieuCaoToiThieu` đã biến mất hoàn toàn khỏi phần THI HÀNH', () => {
    // `NGUON_CAROUSEL` đã bỏ chú thích — javadoc CÓ nhắc tên cũ để giải thích vì sao đổi.
    expect(NGUON_CAROUSEL).not.toContain('chieuCaoToiThieu');
  });

  it('⭐⭐ phép cắt chú thích không cắt QUÁ TAY — nếu cắt, mọi khẳng định trên xanh vĩnh viễn', () => {
    // Nửa dễ quên: một bộ cắt quá tay không làm bài nào đỏ, nó chỉ lặng lẽ biến cả bộ canh
    // thành trang trí. Chứng minh phần thi hành vẫn còn nguyên trong chuỗi đã cắt.
    expect(NGUON_CAROUSEL).toContain('export function AnhCarousel');
    expect(NGUON_CAROUSEL).toContain('data-khung-anh');
    expect(boChuThich('{/* nhắc tới flex-1 */}\n<div className="shrink-0">')).not.toContain(
      'flex-1',
    );
    expect(boChuThich('{/* chú thích */}\n<div className="shrink-0">')).toContain('shrink-0');
  });
});

describe('Mọi nơi gọi <AnhCarousel> truyền tỉ lệ dạng HẰNG', () => {
  const noiGoi = moiTepTsx(GOC)
    .map((d) => ({ duong: d, ma: readFileSync(d, 'utf8') }))
    .filter((t) => t.ma.includes('<AnhCarousel'));

  it('tìm được nơi gọi — tập rỗng thì mọi khẳng định dưới vô nghĩa', () => {
    expect(noiGoi.length, 'không thấy nơi gọi <AnhCarousel> nào').toBeGreaterThanOrEqual(2);
  });

  it('⭐⭐ mỗi nơi gọi truyền `tiLeKhung="aspect-[...]"` viết nguyên văn', () => {
    for (const { duong, ma } of noiGoi) {
      const ten = duong.replace(process.cwd(), '.');
      // ⛔ Bắt buộc dạng chuỗi trực tiếp. `tiLeKhung={bien}` hay `tiLeKhung={`aspect-${x}`}`
      //    đều lọt khỏi bộ quét Tailwind và cho ra khung cao 0px trong im lặng.
      expect(ma, `${ten} không truyền tỉ lệ dạng hằng`).toMatch(/tiLeKhung="aspect-\[[\d/]+\]"/);
      expect(ma, `${ten} truyền tỉ lệ bằng biểu thức ⇒ Tailwind không sinh lớp`).not.toMatch(
        /tiLeKhung=\{/,
      );
    }
  });

  it('⭐ slider trang chủ dùng đúng 16/9 — số này có nguồn ngoài tệp', () => {
    // Nguồn: `docs/ui-styles.md:212` ("Cột 8: Tin đinh 16:9") và cổng tham chiếu
    // (`lg:h-[444px]` ở cột 8/12 khung 1232px ⇒ 785/444 = 1,77). Cùng khuôn với
    // `homeDataColumns.test.ts`: neo con số vào một nguồn KHÁC tệp đang kiểm.
    const slider = noiGoi.find((t) => t.duong.endsWith('HomeBannerSlider.tsx'));
    expect(slider, 'không tìm thấy HomeBannerSlider').toBeDefined();
    expect(slider!.ma).toContain('tiLeKhung="aspect-[16/9]"');
  });
});
