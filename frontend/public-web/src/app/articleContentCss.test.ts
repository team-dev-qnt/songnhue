import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  ALIGN_CLASSES,
  IMAGE_WIDTH_CLASSES,
  PORTAL_STYLED_TAGS,
} from 'design-tokens/editor-schema';
import { describe, expect, it } from 'vitest';

/**
 * **Cạnh thứ ba của tam giác: thứ bộ lọc cho qua thì cổng phải hiển thị được.**
 *
 * <h3>Bài kiểm này sinh ra từ lỗi nặng nhất tìm được khi rà soát trình soạn thảo</h3>
 *
 * Phần thân bài trên cổng mang class `prose`, mà gói `@tailwindcss/typography` **chưa từng
 * được cài** — nên `prose` không sinh ra quy tắc nào. Cộng với preflight của Tailwind xoá
 * hình dạng mặc định của trình duyệt, bài viết lên cổng ở dạng gần như chữ trơn: danh sách
 * mất dấu đầu dòng, bảng mất viền, `h3`/`h4` bằng cỡ chữ đoạn văn, chú thích ảnh không phân
 * biệt được với một câu trong bài, và `sn-align-*` bị bỏ qua hoàn toàn.
 *
 * <p>Điều đáng sợ của lỗi này: **màn hình xem trước trong admin-app vẫn đúng**, vì nó dùng
 * CSS của trình soạn thảo. Biên tập viên định dạng kỹ, xem trước thấy đẹp, xuất bản, và
 * không bao giờ mở lại trang công khai để đối chiếu.
 *
 * <h3>⚠⚠ Bản đầu của chính bài kiểm này XANH trong khi không kiểm được gì</h3>
 *
 * Nó hỏi `CSS.includes('.sn-align-center')`. Kiểm chứng ngược bằng cách xoá hẳn quy tắc
 * `text-align: center` → **vẫn xanh**, vì chuỗi `.sn-align-center` còn xuất hiện ở một quy
 * tắc khác trong cùng tệp (`figure.sn-align-center`). Tức là bài canh chống lỗi im lặng lại
 * chính là một lỗi im lặng — lần thứ n trong dự án này.
 *
 * Nay nó tách tệp thành từng quy tắc và hỏi **thuộc tính CSS có thật sự được khai không**.
 *
 * <h3>Bài kiểm này vẫn KHÔNG chứng minh được gì</h3>
 *
 * Nó không biết quy tắc đó đẹp hay đúng, và không thay được một lượt mở trang bằng mắt. Thứ
 * nó chặn là **quên hẳn** — và quên hẳn đúng là chuyện đã xảy ra. Ghi rõ giới hạn ở đây để
 * về sau không ai đọc màu xanh của nó thành "phần hiển thị đã được kiểm".
 */

const CSS = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), 'article-content.css'),
  'utf8',
);

/** Vùng thân bài trên cổng — trang bài viết gắn class này. */
const VUNG = '.sn-article';

interface QuyTac {
  /** Từng bộ chọn đã tách theo dấu phẩy, đã cắt khoảng trắng. */
  boChon: string[];
  /** Phần thân, dùng để hỏi một thuộc tính có được khai hay không. */
  than: string;
}

/**
 * Tách tệp CSS thành danh sách quy tắc.
 *
 * Không dùng thư viện phân tích CSS: thêm một phụ thuộc chỉ để phục vụ một bài kiểm là cái
 * giá không đáng, và tệp này do chính ta viết nên hình dạng của nó nằm trong tầm kiểm soát.
 *
 * ⚠ Gỡ vỏ `@media` trước, nếu không thì cả khối con nằm gọn trong "thân" của quy tắc `@media`
 * và các quy tắc bên trong không bao giờ được nhìn thấy.
 */
function tachQuyTac(css: string): QuyTac[] {
  const khongChuThich = css.replace(/\/\*[\s\S]*?\*\//g, '');
  // Bỏ dòng mở `@media …{` và dấu `}` đóng tương ứng ở cuối — nội dung bên trong nổi lên cấp trên.
  const phang = khongChuThich.replace(/@media[^{]*\{([\s\S]*?)\n\}/g, '$1');

  const quyTac: QuyTac[] = [];
  const mau = /([^{}]+)\{([^{}]*)\}/g;
  let khop: RegExpExecArray | null;
  while ((khop = mau.exec(phang)) !== null) {
    quyTac.push({
      boChon: khop[1]
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
      than: khop[2],
    });
  }
  return quyTac;
}

const QUY_TAC = tachQuyTac(CSS);

/** Có quy tắc nào có bộ chọn khớp `mau` và thân khai thuộc tính `thuocTinh` không? */
function coKhai(mau: RegExp, thuocTinh?: string): boolean {
  return QUY_TAC.some(
    (qt) =>
      qt.boChon.some((bc) => mau.test(bc)) &&
      (thuocTinh === undefined || new RegExp(`(^|[;{\\s])${thuocTinh}\\s*:`).test(qt.than)),
  );
}

describe('CSS nội dung bài của cổng công khai', () => {
  it('tách được quy tắc từ tệp — thiếu dòng này thì mọi phép dưới đây xanh khi tệp rỗng', () => {
    expect(QUY_TAC.length).toBeGreaterThan(20);
    expect(QUY_TAC.some((qt) => qt.boChon.includes(VUNG))).toBe(true);
  });

  it('⭐⭐ mọi thẻ mà bộ chuẩn hoá xoá hình dạng đều được khai lại', () => {
    const chuaKhai = PORTAL_STYLED_TAGS.filter(
      (the) => !coKhai(new RegExp(`\\.sn-article\\s+(\\w+\\s+)*${the}(\\b|$)`)),
    );

    expect(
      chuaKhai,
      `Những thẻ này soạn thảo tạo ra được và HtmlSanitizer cho qua, nhưng cổng công khai ` +
        `không có quy tắc CSS nào cho chúng. Preflight của Tailwind đã xoá hình dạng mặc ` +
        `định, nên chúng sẽ hiển thị như chữ thường — không lỗi, không cảnh báo. Khai thêm ` +
        `trong article-content.css, hoặc bỏ khỏi PORTAL_STYLED_TAGS nếu thật sự không cần.`,
    ).toEqual([]);
  });

  it('⭐ danh sách phải có dấu đầu dòng — preflight xoá đúng thứ này', () => {
    // Không hỏi "có quy tắc cho `ul` không" mà hỏi "`list-style` có được khai lại không":
    // một quy tắc chỉ đặt lề cho `ul` vẫn để danh sách trần trụi không dấu đầu dòng.
    expect(coKhai(/\.sn-article\s+ul$/, 'list-style')).toBe(true);
    expect(coKhai(/\.sn-article\s+ol$/, 'list-style')).toBe(true);
    expect(coKhai(/\.sn-article\s+(ul|ol)$/, 'padding-inline-start')).toBe(true);
  });

  it('⭐ tiêu đề trong bài phải khác cỡ chữ đoạn văn', () => {
    for (const the of ['h2', 'h3', 'h4']) {
      expect(coKhai(new RegExp(`\\.sn-article\\s+${the}$`), 'font-size'), the).toBe(true);
      expect(coKhai(new RegExp(`\\.sn-article\\s+${the}$`), 'font-weight'), the).toBe(true);
    }
  });

  it('⭐ bảng phải có viền — không có viền thì bảng số liệu vô nghĩa', () => {
    expect(coKhai(/\.sn-article\s+(th|td)$/, 'border-width')).toBe(true);
  });

  it('⭐⭐ ba class căn lề đều khai `text-align` — đây đúng là chỗ đã hỏng', () => {
    const chuaKhai = ALIGN_CLASSES.filter(
      (c) => !coKhai(new RegExp(`\\.sn-article\\s+\\.${c}$`), 'text-align'),
    );

    expect(
      chuaKhai,
      'Thiếu định nghĩa thì người soạn bấm căn giữa, thấy đúng ở màn hình xem trước, và bài ' +
        'lên cổng vẫn căn trái.',
    ).toEqual([]);
  });

  it('⭐ ba class bề ngang ảnh đều khai `width`', () => {
    const chuaKhai = IMAGE_WIDTH_CLASSES.filter(
      (c) => !coKhai(new RegExp(`\\.sn-article\\s+\\.${c}$`), 'width'),
    );
    expect(chuaKhai).toEqual([]);
  });

  it('figure căn giữa/căn phải phải đổi lề ngoài, không chỉ đặt text-align', () => {
    // `text-align` căn phần nội dung BÊN TRONG một khối, nó không đẩy được chính khối đó
    // sang bên. Thiếu quy tắc lề ngoài thì ảnh hẹp hơn khung bài vẫn nằm bên trái.
    expect(coKhai(/figure\.sn-align-center$/, 'margin-inline')).toBe(true);
    expect(coKhai(/figure\.sn-align-right$/, 'margin-inline-start')).toBe(true);
  });

  it('bảng phải cuộn ngang được — bài thuỷ lợi hay có bảng sáu, bảy cột', () => {
    expect(coKhai(/\.sn-article\s+table$/, 'overflow-x')).toBe(true);
  });

  it('ảnh hẹp phải trở lại toàn khung trên điện thoại', () => {
    // Quy tắc này nằm trong `@media` — phép kiểm sẽ xanh giả nếu `tachQuyTac` không gỡ được
    // vỏ `@media`, nên đây cũng là bài kiểm cho chính bộ tách.
    const trongMedia = tachQuyTac(CSS).filter((qt) =>
      qt.boChon.some((bc) => /\.sn-w-1-2$/.test(bc)),
    );
    expect(trongMedia.length, 'phải thấy cả quy tắc gốc lẫn quy tắc trong @media').toBe(2);
  });
});
