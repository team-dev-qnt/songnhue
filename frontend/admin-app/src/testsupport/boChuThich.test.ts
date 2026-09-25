import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';

/**
 * `boChuThich` là một **cơ chế canh gác** — 31 tệp kiểm đọc-mã-nguồn dựa vào nó để phân biệt *lời
 * giải thích* với *lời thi hành*. `conventions.md` §1.5: mỗi cơ chế canh gác phải có bài kiểm
 * chứng minh nó bắt được vi phạm.
 *
 * <p>⛔ Cái bẫy riêng của hàm này: một bộ cắt cắt QUÁ TAY thì mọi bộ canh dựa vào nó đều xanh
 * vĩnh viễn — nó xoá luôn thứ cần soi. Nên nửa dưới của bài này quan trọng ngang nửa trên.
 */
describe('boChuThich — phân biệt lời giải thích với lời thi hành', () => {
  it('cắt chú thích JSX `{/* … */}`, kể cả nhiều dòng', () => {
    expect(boChuThich('{/* câu bị gỡ */}\n<p>còn lại</p>')).not.toContain('câu bị gỡ');
    expect(boChuThich('{/* dòng một\n   dòng hai */}\n<p>còn lại</p>')).not.toContain('dòng hai');
  });

  it('cắt chú thích khối `/* … */` và chú thích dòng `// …`', () => {
    expect(boChuThich('/* câu bị gỡ */\nconst a = 1;')).not.toContain('câu bị gỡ');
    expect(boChuThich('// câu bị gỡ\nconst a = 1;')).not.toContain('câu bị gỡ');
    expect(boChuThich(' *  câu trong javadoc\n')).toContain('câu trong javadoc');
  });

  it('⭐ KHÔNG cắt phần thi hành — nếu cắt, mọi bộ canh dựa vào nó xanh vĩnh viễn', () => {
    // Đây là nửa dễ quên. Một bộ cắt quá tay không làm bài kiểm nào đỏ; nó chỉ lặng lẽ biến
    // mọi bộ canh thành trang trí — đúng hình dạng "cơ chế canh gác xanh mà không chạy".
    expect(boChuThich('<p>hệ thống sẽ báo cụ thể</p>')).toContain('hệ thống sẽ báo cụ thể');
    expect(boChuThich('const a = 1;\nconst b = 2;')).toContain('const b = 2;');
    expect(boChuThich('<Upload showUploadList={false}>')).toContain('<Upload');
  });

  it('giữ nguyên mã nằm SAU chú thích trên cùng một dòng logic', () => {
    const ma = '// giải thích\n<span onClick={(e) => e.stopPropagation()}>';
    const sau = boChuThich(ma);
    expect(sau).not.toContain('giải thích');
    expect(sau).toContain('stopPropagation');
  });

  it('⭐⭐ KHÔNG quay lui qua nhiều chú thích — bản trước nuốt mất 8.174 ký tự vì lỗi này', () => {
    // Dựng lại đúng bố cục đã làm bản cũ hỏng: một `{` theo sau là xuống dòng rồi javadoc,
    // rồi (cách xa) một chú thích JSX kết thúc bằng `*/}`. Bản cũ chạy mẫu JSX trước, quay
    // lui từ `{` đầu tới `*/}` cuối và xoá sạch mọi thứ ở giữa.
    const ma = [
      'export interface Muc {',
      '  /** tài liệu của trường. */',
      '  khoa: string;',
      '}',
      'const GIU_LAI = 1;',
      'export function PhaiConDay() {',
      '  return <div>{/* chú thích JSX */}<span>noi dung</span></div>;',
      '}',
    ].join('\n');

    const sau = boChuThich(ma);
    expect(sau, 'phần thi hành giữa hai chú thích bị nuốt mất').toContain('const GIU_LAI = 1;');
    expect(sau).toContain('export function PhaiConDay');
    expect(sau).toContain('noi dung');
    expect(sau, 'chú thích JSX vẫn phải bị cắt').not.toContain('chú thích JSX');
    expect(sau, 'javadoc của trường vẫn phải bị cắt').not.toContain('tài liệu của trường');
  });
});

/**
 * **Ba chỗ SÁU bản cũ trả lời khác nhau — T28.41.**
 *
 * Trước 25/09/2026 kho có **tám** định nghĩa `boChuThich` với **sáu** thuật toán, khác nhau ở đúng
 * ba trục dưới đây. Mỗi bài ở đây ghim một trục, và cả ba đều là hành vi mà **ít nhất một** bản cũ
 * làm ngược lại — nên chúng ⛔ phải khẳng định trang trí: chúng là định nghĩa của *bản nào đã
 * thắng*, và chúng đỏ ngay ngày ai đó lùi về một thuật toán cũ.
 */
describe('boChuThich — ba trục từng làm sáu bản cũ lệch nhau', () => {
  it('⛔⛔ Trục 1 — chuỗi chứa `/*` KHÔNG bị cắt nhầm nữa (bản regex cắt)', () => {
    // ⚠ Bài này trước 25/09 khẳng định **NGƯỢC LẠI**: nó ghi *"giới hạn THẬT: chuỗi ký tự chứa
    //   `/*` bị cắt nhầm"* và coi đó là phạm vi đã khai (luật 28). Khai một khuyết tật ra là
    //   đúng; nhưng nó vẫn là một khuyết tật, và việc đảo được khẳng định này chính là bằng
    //   chứng bản lexer đã lên thay.
    const sau = boChuThich('const mau = "/* dau */"; const sau = 1;');
    expect(sau).toContain('/* dau */');
    expect(sau).toContain('const sau = 1;');
  });

  it('⛔⛔ Trục 2 — `//` GIỮA dòng là chú thích, nhưng `//` trong chuỗi thì ⛔', () => {
    // Hai bản cũ trả lời ngược nhau ở đây: bản regex đòi `//` đứng **đầu dòng** (nên bỏ sót chú
    // thích đuôi dòng ⇒ một tên bị cấm nhắc trong đó vẫn tính là mã ⇒ **đỏ giả**); bản quét dòng
    // cắt `//` ở bất kỳ đâu ngoài chuỗi. Bản lexer làm vế đúng của cả hai.
    expect(boChuThich('const a = 1; // câu bị gỡ')).not.toContain('câu bị gỡ');
    expect(boChuThich('const a = 1; // câu bị gỡ')).toContain('const a = 1;');
    expect(boChuThich('  const url = "https://songnhue.vn";')).toContain('songnhue.vn');
  });

  it('⭐ Trục 3 — cặp ngoặc rỗng còn lại của `{/* … */}` được dọn', () => {
    // Chỉ bản `admin-app` dọn; bản `public-web` thì ⛔. Một bộ canh đếm `{` chuyển giữa hai app
    // sẽ ra hai con số khác nhau trên cùng một tệp.
    expect(boChuThich('<div>{/* x */}</div>')).not.toContain('{');
  });

  it('⚠ Vế chống cắt-quá-tay cho CẢ ba trục — chuỗi và mã phải còn nguyên vẹn', () => {
    // Thiếu vế này thì cả ba bài trên vẫn xanh khi hàm trả chuỗi RỖNG với mọi đầu vào (luật 7).
    const ma = 'const a = "giu/*nguyen"; // bo\nconst b = `https://x`;';
    const sau = boChuThich(ma);
    expect(sau).toContain('giu/*nguyen');
    expect(sau).toContain('https://x');
    expect(sau).not.toContain('bo\n');
    expect(sau.split('\n')).toHaveLength(2);
  });

  it('⚠ Giới hạn CÒN LẠI, khai ra thay vì để người sau tìm lại (luật 28)', () => {
    // Bản lexer ⛔ hiểu biểu thức chính quy dạng literal: `/` theo sau `/` trong một regex bị đọc
    // thành mở chú thích. ⛔ có nạn nhân nào trong 31 tệp đang gọi, nhưng nó là giới hạn thật.
    expect(boChuThich('const r = /a//b/;')).not.toContain('b/');
  });
});

/**
 * **Bánh cóc: kho chỉ được có ĐÚNG HAI định nghĩa** — T28.41.
 *
 * ⛔ Rút về một bản được: `admin-app` và `public-web` là hai workspace npm riêng và ⛔ nhập khẩu
 * chéo được. ⇒ Bất biến phải là *hai bản, giống nhau tới từng byte*, và ⛔ có bản thứ ba.
 *
 * ⚠ Bốn lượt đo trước đều đếm mẫu số bằng tay và **cả bốn đều sai** (1 → 5 → 5 → 8). Nên bộ canh
 * này **ĐO** chứ ⛔ giữ một danh sách gõ tay — luật 28.
 */
describe('boChuThich — bánh cóc chống bản chép thứ ba', () => {
  const GOC = join(process.cwd(), '..');
  const DUONG_ADMIN = join(GOC, 'admin-app/src/testsupport/boChuThich.ts');
  const DUONG_PUBLIC = join(GOC, 'public-web/src/lib/boChuThich.ts');

  function moiTepNguon(thuMuc: string): string[] {
    const ra: string[] = [];
    for (const muc of readdirSync(thuMuc)) {
      if (muc === 'node_modules' || muc === 'dist' || muc.startsWith('.')) continue;
      const duong = join(thuMuc, muc);
      if (statSync(duong).isDirectory()) ra.push(...moiTepNguon(duong));
      else if (/\.tsx?$/.test(muc)) ra.push(duong);
    }
    return ra;
  }

  it('⚠ Vế chống tập rỗng — phép quét phải thấy CẢ HAI workspace', () => {
    // Thiếu vế này thì bài dưới xanh khi `moiTepNguon` trả `[]` vì đường dẫn gốc sai (luật 7).
    const tep = [
      ...moiTepNguon(join(GOC, 'admin-app/src')),
      ...moiTepNguon(join(GOC, 'public-web/src')),
    ];
    expect(tep.filter((t) => t.includes('/admin-app/')).length).toBeGreaterThan(100);
    expect(tep.filter((t) => t.includes('/public-web/')).length).toBeGreaterThan(50);
  });

  it('⛔⛔ Toàn kho có ĐÚNG HAI định nghĩa `boChuThich`, và cả hai là bản dùng chung', () => {
    const noiKhai = [
      ...moiTepNguon(join(GOC, 'admin-app/src')),
      ...moiTepNguon(join(GOC, 'public-web/src')),
    ].filter((t) => /(?:export )?function boChuThich\s*\(/.test(readFileSync(t, 'utf8')));

    expect(
      noiKhai.map((t) => t.slice(GOC.length + 1)).sort(),
      'Một bản chép thứ ba là một bộ canh đổi nghĩa trong im lặng khi ai đó chuyển khẳng định ' +
        'giữa hai tệp. Nhập khẩu bản dùng chung thay vì viết lại.',
    ).toEqual(['admin-app/src/testsupport/boChuThich.ts', 'public-web/src/lib/boChuThich.ts']);
  });

  it('⛔⛔ Hai bản dùng chung phải GIỐNG NHAU TỚI TỪNG BYTE', () => {
    // Hai workspace ⛔ nhập khẩu chéo được nên ⛔ có cách nào ép bằng kiểu. Phép so byte là thứ
    // duy nhất còn lại — và nó đỏ ngay ngày ai đó sửa một bên mà quên bên kia.
    expect(readFileSync(DUONG_PUBLIC, 'utf8')).toBe(readFileSync(DUONG_ADMIN, 'utf8'));
  });
});
