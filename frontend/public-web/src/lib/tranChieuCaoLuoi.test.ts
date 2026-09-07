import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { viPhamTranLuoi } from './tranChieuCaoLuoi';

/**
 * `conventions.md` §1.5: mỗi cơ chế canh gác phải có bài kiểm chứng minh nó bắt được vi phạm.
 *
 * <p>Nửa dưới (quét toàn cây) quan trọng ngang nửa trên: một vị từ đúng mà không ai gọi là
 * đúng hình dạng "cơ chế canh gác xanh mà không chạy" — kho này đã có năm cái như thế.
 */

/** Chuỗi lớp NGUYÊN VĂN của `page.tsx` bản 31/08, bản đã dựng ra 200,4px chồng lấn. */
const BAN_HONG_3108 =
  'grid grid-cols-1 items-stretch gap-6 lg:max-h-[calc(100svh-17rem)] lg:min-h-[300px] lg:grid-cols-12 lg:gap-9';

describe('viPhamTranLuoi — trần chiều cao đặt trên khung lưới', () => {
  it('⭐⭐ BẮT được chuỗi đã gây lỗi 01/09 (200,4px chồng lấn, đo trên trình duyệt)', () => {
    const v = viPhamTranLuoi(BAN_HONG_3108);
    expect(v, 'bộ canh mù trước đúng chuỗi nó sinh ra để bắt').not.toBeNull();
    expect(v!.ly_do).toContain('grid-auto-rows');
  });

  it('KHÔNG báo khi có `overflow-*` — trần đi kèm cắt thì nó thật sự chặn', () => {
    expect(viPhamTranLuoi('grid grid-cols-1 gap-4 max-h-[510px] overflow-y-auto')).toBeNull();
    expect(viPhamTranLuoi('grid max-h-96 overflow-hidden')).toBeNull();
    expect(viPhamTranLuoi('grid lg:max-h-[400px] lg:overflow-y-auto')).toBeNull();
  });

  it('KHÔNG báo khi khai tường minh chiều cao hàng', () => {
    expect(viPhamTranLuoi('grid max-h-[500px] grid-rows-[minmax(0,1fr)]')).toBeNull();
    expect(viPhamTranLuoi('grid max-h-[500px] auto-rows-fr')).toBeNull();
  });

  it('KHÔNG báo cho `max-h` ngoài ngữ cảnh lưới — ngăn kéo của PortalNav là hợp lệ', () => {
    // Chuỗi thật của `PortalNav`, giữ nguyên văn: nó có `max-h` nhưng không phải `grid`,
    // và có `overflow-y-auto`. Bắt nhầm nó là bộ canh hẹp hơn nơi nó phải chặn (luật 28).
    expect(
      viPhamTranLuoi('max-h-[calc(100vh-3.5rem)] overflow-y-auto border-t border-surface-border'),
    ).toBeNull();
    expect(viPhamTranLuoi('flex flex-col max-h-64')).toBeNull();
  });

  it('KHÔNG báo cho lưới không có trần — phần lớn lưới của kho', () => {
    expect(
      viPhamTranLuoi('grid grid-cols-1 items-stretch gap-6 lg:grid-cols-12 lg:gap-9'),
    ).toBeNull();
    expect(viPhamTranLuoi('grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3')).toBeNull();
  });

  it('⚠ `max-h` không kèm `grid` thì không phải việc của bộ canh này', () => {
    expect(viPhamTranLuoi('max-h-[510px] space-y-4 overflow-y-auto')).toBeNull();
    expect(viPhamTranLuoi('max-h-screen')).toBeNull();
  });
});

/** Mọi tệp `.tsx` dưới `src/`. */
function moiTepTsx(thuMuc: string, gom: string[] = []): string[] {
  for (const ten of readdirSync(thuMuc)) {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) moiTepTsx(duong, gom);
    else if (ten.endsWith('.tsx') && !ten.endsWith('.test.tsx')) gom.push(duong);
  }
  return gom;
}

describe('Toàn cây `src/` — không nơi nào đặt trần lên khung lưới mà không cắt', () => {
  const tep = moiTepTsx(join(process.cwd(), 'src'));

  it('quét được tệp — thiếu khẳng định này thì mọi phép dưới xanh khi danh sách rỗng', () => {
    // Luật 7. Một phép quét trên tập rỗng xanh trọn vẹn và không chứng minh gì.
    expect(tep.length, 'không tìm thấy tệp .tsx nào').toBeGreaterThanOrEqual(25);
  });

  it('⭐ không tệp nào vi phạm', () => {
    const viPham: string[] = [];
    for (const duong of tep) {
      const ma = readFileSync(duong, 'utf8');
      // Lấy mọi chuỗi trong `className="..."` và `className={`...`}`.
      for (const m of ma.matchAll(/className=(?:"([^"]*)"|\{`([^`]*)`\})/g)) {
        const lop = m[1] ?? m[2] ?? '';
        const v = viPhamTranLuoi(lop);
        if (v) viPham.push(`${duong.replace(process.cwd(), '.')}\n    ${v.lop}\n    → ${v.ly_do}`);
      }
    }
    expect(viPham, `\n${viPham.join('\n\n')}\n`).toHaveLength(0);
  });

  it('⭐⭐ phép quét THẬT SỰ nhìn thấy chuỗi lớp — nhét bản hỏng vào là phải bắt', () => {
    // Luật 29: bài kiểm chứng ngược ở trên chỉ chứng minh VỊ TỪ đúng, không chứng minh phép
    // quét gọi tới nó đúng chỗ. Ở đây kiểm chứng chính đường đi: dựng nguyên văn một dòng
    // JSX như trong `page.tsx`, chạy qua đúng biểu thức tách chuỗi ở trên.
    const dongJsx = `<div className="${BAN_HONG_3108}">`;
    const bat = [...dongJsx.matchAll(/className=(?:"([^"]*)"|\{`([^`]*)`\})/g)]
      .map((m) => viPhamTranLuoi(m[1] ?? m[2] ?? ''))
      .filter((v) => v !== null);
    expect(
      bat,
      'biểu thức tách chuỗi không lấy được className ⇒ phép quét là trang trí',
    ).toHaveLength(1);
  });
});
