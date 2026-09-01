import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';

/**
 * `boChuThich` là một **cơ chế canh gác** — hai bộ canh đọc-mã-nguồn dựa vào nó để phân biệt
 * *lời giải thích* với *lời thi hành*. `conventions.md` §1.5: mỗi cơ chế canh gác phải có bài
 * kiểm chứng minh nó bắt được vi phạm.
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

  it('URL trong chuỗi KHÔNG bị cắt — `//` phải ở đầu dòng mới tính là chú thích', () => {
    // ⚠ Lượt viết đầu của bài này khẳng định NGƯỢC LẠI ("chuỗi chứa `//` bị cắt nhầm") và đỏ
    //   ngay. Người viết chép giới hạn ấy từ trực giác, không từ chính mẫu regex mình vừa viết:
    //   `^\s*\/\/.*$` đòi `//` đứng ở ĐẦU dòng. Ghi lại vì một giới hạn khai sai còn tốn thời
    //   gian hơn không khai — §10.42, một chú thích tự tin mà sai chiều.
    expect(boChuThich('  const url = "https://songnhue.vn";')).toContain('songnhue.vn');
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

  it('⚠ giới hạn THẬT: chuỗi ký tự chứa `/*` bị cắt nhầm', () => {
    // Luật 28: một cơ chế canh gác phải nói ra phạm vi của chính nó. Đây là hành vi SAI về mặt
    // cú pháp JavaScript, ghi thành khẳng định để người đọc sau biết ranh giới thay vì phát
    // hiện lại bằng một lượt đỏ khó hiểu.
    expect(boChuThich('const mau = "/* dau */"; const sau = 1;')).not.toContain('dau');
  });
});
