import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';
import { trangThaiTraCuu } from './traCuu';

/**
 * **Trang tìm kiếm ⛔ được nói "Không tìm thấy" khi backend chưa trả lời — T61.17 (WS-72).**
 *
 * <h2>Phạm vi (luật 28)</h2>
 *
 * Canh hai thứ: hàm thuần phân biệt đủ ba trạng thái, và trang `tim-kiem` rẽ nhánh theo nó cho CẢ HAI
 * phạm vi (bài viết · công trình). ⛔ Canh việc trình duyệt thật hiện đúng khối — `public-web` ⛔ có
 * `@testing-library/react` (xem `khoiVanHanh.test.ts`); vế ấy thuộc `tools/tai-thu` (chỉ số
 * `tim_kiem_khong_tra_loi`). ⛔ Canh các trang khác của cổng cũng in "chưa có" khi backend lỗi: ở đó
 * đệm 5 phút đỡ phần lớn lượt gọi, còn tìm kiếm thì mỗi từ khoá một URL.
 */
describe('trangThaiTraCuu — ba trạng thái, ⛔ hai', () => {
  it('⛔⛔ null / undefined (backend ⛔ trả lời: 429 · 5xx · mất kết nối) ⇒ khong-tra-loi, ⛔ phải rong', () => {
    expect(trangThaiTraCuu(null)).toBe('khong-tra-loi');
    expect(trangThaiTraCuu(undefined)).toBe('khong-tra-loi');
  });

  it('đã trả lời, 0 kết quả ⇒ rong · có kết quả ⇒ co', () => {
    expect(trangThaiTraCuu({ content: [] })).toBe('rong');
    expect(trangThaiTraCuu({ content: [{}] })).toBe('co');
  });
});

describe('trang tim-kiem rẽ nhánh theo trangThaiTraCuu', () => {
  const trang = boChuThich(readFileSync(join(process.cwd(), 'src/app/tim-kiem/page.tsx'), 'utf8'));

  it('⛔ hỏi trạng thái của ĐÚNG phạm vi đang xem — cả bài viết lẫn công trình', () => {
    const loiGoi = trang.match(/trangThaiTraCuu\(([^)]*)\)/g) ?? [];
    expect(loiGoi, 'chống tập rỗng: trang phải gọi trangThaiTraCuu').toHaveLength(1);
    // Một lời gọi chỉ đưa `ketQuaBai` vào thì phạm vi Công trình vẫn in "Không tìm thấy công trình
    // nào" khi backend lỗi — đúng khuyết tật cũ, chỉ còn ở nửa trang.
    expect(loiGoi[0]).toContain('ketQuaBai');
    expect(loiGoi[0]).toContain('ketQuaCongTrinh');
  });

  it('⛔ khối "chưa tra cứu được" mang dấu hiệu cấu trúc mà bộ tải thử đếm', () => {
    expect(trang.match(/\{\.\.\.THUOC_TINH_KHONG_TRA_LOI\}/g) ?? []).toHaveLength(1);
  });
});
