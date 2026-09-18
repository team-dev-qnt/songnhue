import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { _test } from '@/components/charts/BieuDoDienBien';
import type { OLuoi } from '@/lib/api';

const o = (giaTri: string | null, chatLuong: string | null = giaTri ? 'HOP_LE' : null): OLuoi => ({
  giaTri,
  chatLuong,
  lyDo: giaTri === null ? 'Không có dữ liệu tại mốc này' : null,
  khoaMauCanhBao: null,
  tenMucCanhBao: null,
});

/**
 * Biểu đồ diễn biến §7.1 — **WS-45**.
 *
 * <h2>Bất biến chịu lực: mốc mất dữ liệu phải thành `null`, ⛔ không bị bỏ qua</h2>
 *
 * ECharts chỉ **ngắt** đường ở phần tử `null` (với `connectNulls: false`). Trả một mảng đã lọc bỏ
 * mốc thiếu thì đường **nối liền** qua chỗ mất tín hiệu — nó vẽ ra một đoạn số liệu chưa ai đo,
 * trên đúng biểu đồ người ta đọc để ra quyết định vận hành. §7.1: *"điểm MISSING: ngắt đường,
 * ⛔ không nội suy"*.
 */
describe('BieuDoDienBien — điểm nào vào đường chính', () => {
  it('⭐⭐ Mốc ⛔ không có số thành `null` — độ dài mảng GIỮ NGUYÊN, ⛔ không bị lọc bỏ', () => {
    const v = _test.duongChinh([o('1.10'), o(null), o('1.30')]);
    expect(v).toHaveLength(3);
    expect(v[1]).toBeNull();
    expect(v).toEqual([1.1, null, 1.3]);
  });

  it('⭐⭐ Điểm NGHI_NGO ⛔ KHÔNG vào đường chính — §7.1 "⛔ không nối liền vào đường chính"', () => {
    // Nối nó vào là công bố một số đo mà chính hệ thống ⛔ không tin, dưới một hình dạng ⛔ không
    // phân biệt được với số đo tốt.
    const v = _test.duongChinh([o('1.10'), o('9.99', 'NGHI_NGO'), o('1.30')]);
    expect(v[1]).toBeNull();
    expect(v).toEqual([1.1, null, 1.3]);
  });

  it('⭐ Điểm NGHI_NGO đi vào chuỗi RIÊNG — để vẽ rỗng ruột, nét đứt', () => {
    const v = _test.diemNghiNgo([o('1.10'), o('9.99', 'NGHI_NGO'), o(null)]);
    expect(v).toEqual([null, 9.99, null]);
  });

  it('⚠ Vế chống tập rỗng — chuỗi rỗng cho mảng rỗng, ⛔ không ném', () => {
    // Thiếu vế này thì mọi khẳng định trên vẫn xanh khi hàm trả `[]` với mọi đầu vào (luật 7).
    expect(_test.duongChinh([])).toEqual([]);
    expect(_test.duongChinh(null)).toEqual([]);
    expect(_test.diemNghiNgo(null)).toEqual([]);
  });

  it('⛔ Công trình ⛔ không có chỉ tiêu ấy ⇒ `null`, KHÁC với "có chỉ tiêu mà ⛔ không có số"', () => {
    const bd = {
      meta: {
        lanLayCuoi: null,
        mocDoGanNhat: null,
        trangThaiNguon: 'DOWN' as const,
        donVi: 'm',
        lyDoLuongMua: null,
      },
      moc: [],
      congTrinh: {
        maCongTrinh: 'X',
        tenCongTrinh: 'X',
        lyTrinh: null,
        trucChinh: false,
        dong: [{ chiTieu: 'Thượng lưu', loai: 'DO' as const, o: [o('1.00')] }],
      },
      nguong: [],
      lyDoTrong: null,
    };
    expect(_test.chuoi(bd, 'Thượng lưu')).not.toBeNull();
    expect(_test.chuoi(bd, 'Hạ lưu')).toBeNull();
  });
});

/**
 * ⚠ Canh **văn bản nguồn** — kho ⛔ chưa có bộ dựng DOM cho `public-web`. Luật 28: bộ canh phải nói
 * ra phạm vi của chính nó, và đây là chỗ nói.
 */
describe('BieuDoDienBien — bất biến cấu trúc', () => {
  const nguon = readFileSync(
    join(process.cwd(), 'src/components/charts/BieuDoDienBien.tsx'),
    'utf-8',
  );
  const setup = readFileSync(join(process.cwd(), 'src/components/charts/setup.ts'), 'utf-8');

  it('⚠ Vế chống tập rỗng — hai tệp phải ĐANG TỒN TẠI và có nội dung', () => {
    expect(nguon.length).toBeGreaterThan(1000);
    expect(setup.length).toBeGreaterThan(200);
  });

  it('⛔⛔ `MarkLineComponent` phải được đăng ký — thiếu nó ECharts IM LẶNG ⛔ không vẽ ngưỡng', () => {
    // Khác `toolbox`/`dataZoom` (in console.error): `markLine` thiếu thì biểu đồ trông hoàn chỉnh,
    // và cái mất là đúng thứ nói cho người đọc biết đã vượt báo động hay chưa.
    expect(setup).toContain('MarkLineComponent');
    expect(nguon).toContain('markLine');
  });

  it('⛔ `connectNulls` phải là `false` ở MỌI đường — `true` là nội suy chỗ mất tín hiệu', () => {
    expect(nguon).not.toMatch(/connectNulls:\s*true/);
    expect((nguon.match(/connectNulls:\s*false/g) ?? []).length).toBeGreaterThanOrEqual(3);
  });

  it('⭐ A11y — hạ lưu NÉT ĐỨT bên cạnh màu, và `<canvas>` có nhãn đọc lên được', () => {
    // §7.1 tự nêu: đỏ/xanh là cặp khó phân biệt nhất với người rối loạn sắc giác.
    expect(nguon).toMatch(/type:\s*'dashed'/);
    expect(nguon).toContain('aria-label');
  });

  it('⛔ ⛔ Không ghi cứng mã màu — màu đi qua design-tokens (nợ T25.23)', () => {
    expect(nguon).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
    expect(nguon).toContain('design-tokens');
  });

  it('⭐ §7.3 — có nút xuất PNG và CSV, và CSV để ô thiếu số TRỐNG', () => {
    expect(nguon).toContain('getDataURL');
    expect(nguon).toContain('text/csv');
    // ⛔ `?? 0` trong CSV là một số liệu, và nó sẽ được ai đó cộng vào một bảng tính.
    expect(nguon).not.toMatch(/giaTri\s*\?\?\s*0/);
  });
});
