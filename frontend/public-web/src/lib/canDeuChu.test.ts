import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * `text-justify` — căn đều hai bên lề, đúng chỗ và chỉ đúng chỗ.
 *
 * <h2>Luật áp dụng: CHỈ chữ nhiều dòng</h2>
 *
 * Yêu cầu QuanTran 01/09: *"phần hiển thị text của văn bản tôi muốn căn đều 2 bên lề"*, phạm
 * vi đã chốt là *"nội dung của văn bản, bài báo, tin tức"*. Cổng tham chiếu căn đều **tiêu đề
 * thẻ và đoạn tóm tắt**, không căn đều nhãn hay siêu dữ liệu.
 *
 * <p>Lý do kỹ thuật: trên một dòng, `justify` là **no-op** — dòng cuối luôn căn đầu. Trên
 * `truncate`/`line-clamp-1` thì cũng chỉ có một dòng. Nên đặt ở đó không sai về hiển thị mà
 * sai về Ý ĐỊNH: người sau đọc lớp ấy sẽ tưởng nó đang làm gì đó.
 *
 * <p>Bài này dùng khuôn ĐẾM CHỐT của `noForcedUppercase.test.ts` và `responsiveImages.test.ts`:
 * thêm `text-justify` ở một tệp mới là bài kiểm đỏ, buộc người thêm phải nói ra vì sao.
 */

/** Tệp → số lần `text-justify` được phép. Thêm dòng ở đây là một quyết định, không phải một lượt vá. */
const CHO_PHEP: Record<string, number> = {
  // ⚠ 01/09/2026: 2 → 1. Khối "Công bố thông tin" đổi từ lưới hai cột sang BẢNG năm cột
  //   (`DocumentTable`), nên tiêu đề bài nay nằm trong một ô bảng rộng khoảng 300px. Căn đều
  //   trong một ô hẹp thì khoảng cách giữa các từ giãn ra thành sông chữ — `text-justify` chỉ
  //   đúng với đoạn văn nhiều dòng đủ rộng. Con số còn lại là đoạn giải thích ở cột phải.
  //   Sửa CÓ CHỦ ĐÍCH, không phải nới ngưỡng cho bài kiểm hết đỏ.
  'components/home/PublishedDocumentsSection.tsx': 1,
  'components/home/AnhCarousel.tsx': 1,
  'components/home/HomeNewsColumn.tsx': 1,
  'components/home/HomeCategoryNews.tsx': 2,
  'components/ArticleCard.tsx': 2,
};

/** Lớp không được đi cùng `text-justify` — hoặc chỉ có một dòng, hoặc mâu thuẫn trực tiếp. */
const XUNG_DOT = ['truncate', 'line-clamp-1', 'text-center', 'text-right', 'text-left'];

function moiTepTsx(thuMuc: string, gom: string[] = []): string[] {
  for (const ten of readdirSync(thuMuc)) {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) moiTepTsx(duong, gom);
    else if (ten.endsWith('.tsx') && !ten.endsWith('.test.tsx')) gom.push(duong);
  }
  return gom;
}

const GOC = join(process.cwd(), 'src');

describe('text-justify — đếm chốt theo tệp', () => {
  const tep = moiTepTsx(GOC);

  it('quét được tệp', () => {
    expect(tep.length).toBeGreaterThanOrEqual(25);
  });

  it('⭐ đúng những tệp trong danh sách, đúng số lần', () => {
    const dem: Record<string, number> = {};
    for (const duong of tep) {
      const ten = duong.slice(GOC.length + 1);
      const n = (readFileSync(duong, 'utf8').match(/\btext-justify\b/g) ?? []).length;
      if (n > 0) dem[ten] = n;
    }
    expect(dem).toEqual(CHO_PHEP);
  });

  it('tổng số lần khớp — bắt được cả trường hợp bù trừ giữa hai tệp', () => {
    const tong = tep.reduce(
      (s, d) => s + (readFileSync(d, 'utf8').match(/\btext-justify\b/g) ?? []).length,
      0,
    );
    expect(tong).toBe(Object.values(CHO_PHEP).reduce((a, b) => a + b, 0));
  });

  it('⭐⭐ không chuỗi lớp nào ghép `text-justify` với lớp một-dòng hoặc căn lề khác', () => {
    const xau: string[] = [];
    for (const duong of tep) {
      const ma = readFileSync(duong, 'utf8');
      for (const m of ma.matchAll(/className=(?:"([^"]*)"|\{`([^`]*)`\})/g)) {
        const lop = m[1] ?? m[2] ?? '';
        if (!/\btext-justify\b/.test(lop)) continue;
        for (const x of XUNG_DOT) {
          if (new RegExp(`\\b${x}\\b`).test(lop)) {
            xau.push(`${duong.slice(GOC.length + 1)}: \`${x}\` đi cùng text-justify → ${lop}`);
          }
        }
      }
    }
    expect(xau, `\n${xau.join('\n')}\n`).toHaveLength(0);
  });
});

describe('Thân bài viết — căn đều là MẶC ĐỊNH, không phải một khai báo tranh chấp', () => {
  const css = readFileSync(join(GOC, 'app/article-content.css'), 'utf8');

  it('`.sn-article` khai `text-align: justify`', () => {
    expect(css).toMatch(/\.sn-article\s*\{[^}]*text-align:\s*justify/);
  });

  it('⭐⭐ KHÔNG khai trên `.sn-article p` — sẽ nuốt mất căn lề tác giả tự chọn', () => {
    // `text-align` KẾ THỪA, nên khai ở khối cha là đủ, và một giá trị kế thừa luôn thua một
    // khai báo trực tiếp ⇒ `.sn-align-*` vẫn ghi đè được từng khối. Khai trên `.sn-article p`
    // thì nó thành một khai báo cạnh tranh, và lựa chọn của biên tập viên — vốn là DỮ LIỆU —
    // phải đi tranh độ ưu tiên với một mặc định của cổng.
    expect(css).not.toMatch(/\.sn-article\s+p\s*\{[^}]*text-align/);
  });

  it('⭐ ba lớp căn lề của trình soạn thảo vẫn thắng được mặc định', () => {
    for (const [lop, gt] of [
      ['left', 'left'],
      ['center', 'center'],
      ['right', 'right'],
    ]) {
      const m = new RegExp(`\\.sn-article\\s+\\.sn-align-${lop}\\s*\\{[^}]*text-align:\\s*${gt}`);
      expect(css, `\`.sn-align-${lop}\` không còn khai text-align`).toMatch(m);
    }
    // Độ ưu tiên: `.sn-article .sn-align-*` = (0,2,0) > `.sn-article` = (0,1,0). Khẳng định
    // bằng cấu trúc chọn tử, vì đó là thứ quyết định ai thắng — không phải thứ tự dòng.
    expect(css).toMatch(/\.sn-article\s+\.sn-align-left/);
  });

  it('tiêu đề trong bài không căn đều', () => {
    expect(css).toMatch(/\.sn-article\s+h2[\s\S]{0,80}text-align:\s*left/);
  });
});
