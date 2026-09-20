import { readFileSync, readdirSync } from 'node:fs';
import { join, relative } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';
import { lienKetAnToan } from './lienKetAnToan';

describe('lienKetAnToan — T61.34', () => {
  it('⛔⛔ chặn javascript:/data:/vbscript: kể cả viết hoa, chen ký tự điều khiển, URL không giao thức', () => {
    for (const x of [
      'javascript:alert(1)',
      'JaVaScRiPt:alert(1)',
      ' javascript:alert(1)',
      'java\tscript:alert(1)',
      'java\nscript:alert(1)',
      '\u0001javascript:alert(1)',
      'data:text/html,<script>alert(1)</script>',
      'vbscript:msgbox(1)',
      '//ke-gian.example/x',
      '/\\ke-gian.example',
      'file:///etc/passwd',
      'duong-dan-tuong-doi',
    ]) {
      expect(lienKetAnToan(x), JSON.stringify(x)).toBeNull();
    }
  });

  it('⭐ đối chứng: http/https/mailto/tel, đường dẫn trong cổng, neo — giữ NGUYÊN văn', () => {
    for (const x of [
      'https://quanlyvanban.hanoi.gov.vn/qlvbdh/main?lang=vi',
      'http://songnhue.vn',
      'mailto:lienhe@songnhue.vn',
      'tel:0243000000',
      '/quan-ly-van-hanh/muc-nuoc-luong-mua',
      '#noi-dung',
    ]) {
      expect(lienKetAnToan(x)).toBe(x);
    }
    expect(lienKetAnToan('')).toBeNull();
    expect(lienKetAnToan(null)).toBeNull();
  });

  it('⛔ mọi nơi đưa URL do QUẢN TRỊ nhập vào href đều đi qua lienKetAnToan', () => {
    // Canh cấu trúc tối thiểu: nhánh URL của menu + ba khoá settings mang URL. Thêm nơi đọc mới mà quên bọc
    // thì đỏ ở đây — một lời dặn ⛔ phải cổng kiểm.
    const goc = join(__dirname, '..');
    const doc = (p: string) => readFileSync(join(goc, p), 'utf8');

    expect(doc('lib/routes.ts')).toMatch(
      /case 'EXTERNAL_DOC':\s*return lienKetAnToan\(item\.url\)/,
    );

    const footer = doc('components/SiteFooter.tsx');
    const docKhoa = [
      ...footer.matchAll(
        /config\?\.\['(site\.footer\.social\.[a-z]+|site\.external\.doc-system-url)'\]/g,
      ),
    ];
    expect(docKhoa.length, 'chống tập rỗng: footer đọc ≥ 4 khoá URL').toBeGreaterThanOrEqual(4);
    for (const m of docKhoa) {
      const truoc = footer.slice(Math.max(0, m.index! - 20), m.index);
      expect(truoc, `${m[1]} phải bọc lienKetAnToan(...)`).toContain('lienKetAnToan(');
    }

    for (const tep of [
      'components/PortalSidebar.tsx',
      'components/home/PublishedDocumentsSection.tsx',
    ]) {
      expect(doc(tep), `${tep}: href lấy thẳng docSystemUrl`).not.toMatch(/href=\{docSystemUrl\}/);
      expect(doc(tep), tep).toContain('lienKetAnToan(docSystemUrl)');
    }
  });
  /**
   * T73.2 — banner là đường ghi THỨ BA mang một địa chỉ do quản trị nhập ra cổng, và nó lọt khỏi bài trên vì bài ấy
   * LIỆT KÊ TAY bốn nơi (luật 28). Bài này ĐO phạm vi: mọi tệp nguồn dưới `src/`, mọi lượt đọc trường `.linkUrl`
   * (trên mã đã bỏ chú thích) phải nằm ngay trong `lienKetAnToan(`.
   */
  it('⛔ T73.2 — mọi lượt đọc `.linkUrl` (banner) trong src đều nằm trong lienKetAnToan(…)', () => {
    const goc = join(__dirname, '..');
    const moiTep = (thuMuc: string): string[] =>
      readdirSync(thuMuc, { withFileTypes: true }).flatMap((e) => {
        const day = join(thuMuc, e.name);
        if (e.isDirectory()) return moiTep(day);
        return /\.tsx?$/.test(e.name) && !/\.test\.tsx?$/.test(e.name) ? [day] : [];
      });

    let tong = 0;
    const xau: string[] = [];
    for (const tep of moiTep(goc)) {
      const ma = boChuThich(readFileSync(tep, 'utf8'));
      for (const m of ma.matchAll(/\.linkUrl\b/g)) {
        tong += 1;
        const truoc = ma.slice(Math.max(0, m.index - 60), m.index);
        if (!/lienKetAnToan\(\s*[\w?.]*$/.test(truoc)) {
          xau.push(`${relative(goc, tep)}: …${truoc.slice(-40)}.linkUrl`);
        }
      }
    }
    expect(tong, 'chống tập rỗng: cổng phải còn đọc linkUrl của banner').toBeGreaterThanOrEqual(1);
    expect(xau, 'đọc linkUrl mà ⛔ qua lienKetAnToan — javascript: sẽ vào href').toEqual([]);
  });
});
