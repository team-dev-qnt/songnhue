import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { _test } from '@/components/home/BangLuoiMucNuoc';
import type { CongTrinhLuoi } from '@/lib/api';

/**
 * Bảng lưới mực nước — **WS-44**, spec §5.2 và §6.1.2.
 *
 * <h2>Bất biến chịu lực: `rowSpan` phải khớp SỐ DÒNG THẬT của nhóm</h2>
 *
 * Ô "Tuyến sông" gộp toàn bộ dòng của nhóm; ô "Công trình" gộp các dòng chỉ tiêu của chính nó.
 * Sai một đơn vị thì trình duyệt ⛔ **không báo lỗi** — nó lặng lẽ đẩy các ô còn lại sang phải,
 * và người đọc thấy một mực nước nằm dưới **sai tên công trình**. Đó là loại lỗi TypeScript
 * ⛔ không thấy (cả hai đều là `number`) và mắt thường ⛔ không bắt được trên một bảng 12 cột.
 *
 * ⇒ Phép tính ấy được bóc ra thành hàm thuần và kiểm ở đây, ⛔ không để nó nằm inline trong JSX.
 */
describe('BangLuoiMucNuoc — phép gộp ô', () => {
  const dong = (n: number) =>
    Array.from({ length: n }, (_, i) => ({
      chiTieu: `c${i}`,
      loai: 'DO' as const,
      o: [],
    }));

  const ct = (ma: string, soDong: number): CongTrinhLuoi => ({
    maCongTrinh: ma,
    tenCongTrinh: ma,
    lyTrinh: null,
    trucChinh: false,
    dong: dong(soDong),
  });

  it('rowSpan của ô Tuyến sông = TỔNG số dòng của mọi công trình trong nhóm', () => {
    // Liên Mạc 3 dòng (TL + HL + Chênh lệch) · Hà Đông 1 dòng · Đồng Quan 3 dòng
    expect(_test.demDong([ct('LMAC', 3), ct('HDONG', 1), ct('DQUAN', 3)])).toBe(7);
  });

  it('⚠ Vế chống tập rỗng — nhóm ⛔ không có công trình nào cho 0, ⛔ không ném', () => {
    expect(_test.demDong([])).toBe(0);
  });

  it('Một công trình một dòng cho rowSpan = 1, ⛔ không phải 0', () => {
    // Sai thành 0 thì `rowSpan={0}` nghĩa là "trải tới hết phần còn lại của bảng" theo HTML —
    // một ô nuốt trọn mọi dòng phía dưới, và trình duyệt ⛔ không kêu một tiếng nào.
    expect(_test.demDong([ct('LCO', 1)])).toBe(1);
  });

  it('Nhãn cột là giờ VIỆT NAM, ⛔ không theo múi giờ của trình duyệt', () => {
    // 03:20 UTC = 10:20 giờ VN. Một người mở cổng từ nước ngoài phải thấy CÙNG một trục thời
    // gian với biểu của Công ty, nếu không hai bên đọc hai bảng khác nhau về cùng một trận lũ.
    expect(_test.gioPhut('2026-09-09T03:20:00Z')).toBe('10:20');
  });
});

/**
 * Bất biến về CẤU TRÚC — thứ ⛔ không kiểm được bằng hàm thuần vì nó nằm trong JSX.
 *
 * ⚠ Đây là canh văn bản, và luật 2 nói canh cấu trúc thì tốt hơn. Kho ⛔ chưa có bộ dựng DOM cho
 * `public-web` (0 `@testing-library`, 0 `jsdom`), nên đây là mức tốt nhất đo được hôm nay —
 * và giới hạn ấy ghi thẳng ra đây thay vì để cái xanh của bài đọc như một bảo đảm rộng hơn
 * (luật 28).
 */
describe('BangLuoiMucNuoc — bất biến cấu trúc', () => {
  const nguon = readFileSync(
    join(process.cwd(), 'src/components/home/BangLuoiMucNuoc.tsx'),
    'utf-8',
  );

  it('Dùng <table> thật — `rowSpan` ⛔ không tồn tại trên `div`', () => {
    expect(nguon).toContain('<table');
    expect(nguon).toContain('rowSpan={');
    expect(nguon).toContain('colSpan={');
  });

  it('⛔ Ô thiếu dữ liệu ⛔ KHÔNG được lấp bằng 0 hay một dấu gạch', () => {
    // spec §6.2: "Không được hiển thị 0.0 cho ô mất dữ liệu — hai trạng thái này khác nhau về
    // nghiệp vụ." Ba cách lấp phổ biến, chặn cả ba.
    expect(nguon).not.toMatch(/giaTri\s*\?\?/);
    expect(nguon).not.toMatch(/'0\.00'|"0\.00"/);
    expect(nguon).not.toMatch(/>\s*—\s*</);
  });

  it('Ô nghi ngờ hiện KÈM SỐ và KÈM dấu cảnh báo, ⛔ không bị ẩn đi', () => {
    expect(nguon).toContain('NGHI_NGO');
    expect(nguon).toContain('⚠');
    // Có nhãn cho trình đọc màn hình: một dấu ⚠ thuần hình ảnh ⛔ không tới được người khiếm thị.
    expect(nguon).toContain('sr-only');
  });

  it('Bảng cuộn ngang trong VÙNG CỦA CHÍNH NÓ — thân trang ⛔ không được cuộn ngang', () => {
    expect(nguon).toContain('overflow-x-auto');
    expect(nguon).toContain('sticky left-0');
  });

  it('⛔ ⛔ Không ghi cứng mã màu — màu đi qua token (docs/ui-styles.md, nợ T25.23)', () => {
    expect(nguon).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
  });
});
