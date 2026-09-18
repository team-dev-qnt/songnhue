import { describe, expect, it } from 'vitest';

import { type MocSoLieu, mocSoLieu } from './mocSoLieu';

/**
 * Bài **tự-kiểm-chứng** cho nhãn `MocSoLieu` — luật 1: *mỗi cơ chế canh gác phải có bài chứng
 * minh nó bắt được vi phạm.*
 *
 * ⛔⛔ Nhãn này là một cổng kiểm ở **tầng biên dịch**, nên nó ⛔ không hỏng theo kiểu một khẳng
 * định lúc chạy hỏng — nó hỏng bằng cách **lặng lẽ hết tác dụng**: ai đó nới `updatedAt` về
 * `string`, hoặc đổi `MocSoLieu` thành một bí danh trần (`type MocSoLieu = string`), và mọi bài
 * kiểm lúc chạy vẫn xanh trọn vẹn vì hành vi ⛔ không đổi một chút nào.
 *
 * ⇒ Thứ bắt được chuyện đó là `@ts-expect-error`: nếu dòng bên dưới **thôi ⛔ còn lỗi**, chính
 * `tsc` báo *"Unused '@ts-expect-error' directive"* và `npm run typecheck` **đỏ**. Tức bài này
 * đỏ đúng vào ngày nhãn mất hiệu lực, ⛔ không phải vào ngày ai đó nhớ ra phải kiểm lại.
 *
 * ⚠ Đây là lý do bài nằm ở tệp `.test.ts`: `tsconfig.json` của `public-web` nạp cả tệp kiểm
 * (đo 09/09/2026 — 41 trong 127 tệp `tsc` nạp là `.test.ts`), nên `@ts-expect-error` ở đây
 * **được chạy bởi cổng bắt buộc** `Frontend — lint`, ⛔ không chỉ bởi `vitest`.
 */
describe('MocSoLieu — nhãn chống truyền nhầm mốc dựng trang', () => {
  it('⛔ TỪ CHỐI một chuỗi trần — đây chính là khuyết tật T43.9', () => {
    // @ts-expect-error một `string` bất kỳ ⛔ KHÔNG được gán vào `MocSoLieu`.
    //   Ngày dòng này thôi báo lỗi là ngày nhãn hết tác dụng và T43.9 tái phát được.
    const sai: MocSoLieu = new Date().toISOString();
    expect(typeof sai).toBe('string');
  });

  it('⛔ TỪ CHỐI đúng biểu thức đã gây ra T43.9 — kết quả một lượt gọi giờ máy chủ', () => {
    // Mô phỏng `getServerTime(): Promise<string | null>` — hàm đã bị gỡ ở lượt này.
    const gioMayChu: string | null = '2026-09-09T10:00:00Z';
    // @ts-expect-error `string | null` ⛔ KHÔNG gán được vào `MocSoLieu | null`.
    const sai: MocSoLieu | null = gioMayChu;
    expect(sai).toBe('2026-09-09T10:00:00Z');
  });

  it('⭐ ĐỐI CHỨNG — mốc đi qua `mocSoLieu()` phải được NHẬN', () => {
    // ⚠ Vế này chịu lực ngang hai vế trên: một nhãn từ chối MỌI thứ cũng "bắt được vi phạm",
    //   và nó làm cả bốn nơi gọi hợp lệ ⛔ không biên dịch nổi. Bài phải phân biệt được BA
    //   trạng thái — nhận đúng, từ chối sai, và ⛔ không phải "từ chối tất" (luật 9).
    const tuBackend: string | null = '2026-09-09T10:00:00Z';
    const dung: MocSoLieu | null = mocSoLieu(tuBackend);
    expect(dung).toBe('2026-09-09T10:00:00Z');
  });

  it('giữ nguyên `null` — chưa có số liệu thì ⛔ không bịa một mốc (quy tắc 16)', () => {
    expect(mocSoLieu(null)).toBeNull();
    expect(mocSoLieu(undefined)).toBeNull();
  });
});
