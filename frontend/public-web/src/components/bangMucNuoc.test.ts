import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { COT_MUC_NUOC } from '@/lib/homeDataColumns';

/**
 * Bảng "Mực nước, lượng mưa" — **T35.7**, bất biến về CẤU TRÚC.
 *
 * <h2>⚠ Bất biến chịu lực: hàng tiêu đề và hàng dữ liệu phải dùng CÙNG một lưới</h2>
 *
 * `ColumnHeaderRow` và `WaterLevelRows` là **hai component tách nhau** (cố ý — xem javadoc của
 * chúng), và mỗi nơi gọi phải truyền cùng một chuỗi `grid-cols-[…]` cho cả hai. Truyền lệch thì
 * tiêu đề "Lượng mưa" đứng trên cột "Thời điểm đo" — bảng vẫn dựng, ⛔ không lỗi nào, và người đọc
 * tin vào một con số đặt dưới sai tiêu đề.
 *
 * ⛔ Đây đúng loại lỗi mà TypeScript ⛔ không thấy: cả hai đều là `string`.
 *
 * <p>Bảng nay xuất hiện ở **hai** trang (trang chủ và trang chi tiết), tức bốn lượt truyền — và
 * "hai nơi con người phải nhớ" đã thành bốn. Luật 14: chỗ đó cần một phép kiểm nhớ hộ.
 */

const GOC = join(process.cwd(), 'src');

const TEP = [
  'components/home/WaterLevelBlock.tsx',
  'app/quan-ly-van-hanh/muc-nuoc-luong-mua/page.tsx',
];

function doc(tuongDoi: string): string {
  return readFileSync(join(GOC, tuongDoi), 'utf8');
}

describe('bảng mực nước — hàng tiêu đề và hàng dữ liệu khớp lưới', () => {
  it('⭐ mọi lượt truyền `luoi` của bảng mực nước dùng CÙNG một chuỗi grid', () => {
    const luoi = TEP.flatMap((t) =>
      [...doc(t).matchAll(/luoi="(grid-cols-\[[^\]]+\])"/g)].map((m) => m[1]),
    );

    // ⚠ Vế chống tập rỗng đứng TRƯỚC (luật 7 + 29): mẫu khớp hụt trả mảng rỗng, và
    //   `new Set([]).size <= 1` xanh trọn vẹn trong khi ⛔ không kiểm gì cả.
    expect(luoi.length).toBeGreaterThanOrEqual(4);
    expect(new Set(luoi).size).toBe(1);
  });

  it('⭐ bề rộng tối thiểu cũng phải khớp — hai khối cuộn ngang trong cùng một khung', () => {
    const beRong = TEP.flatMap((t) =>
      [...doc(t).matchAll(/beRongToiThieu="(min-w-\[[^\]]+\])"/g)].map((m) => m[1]),
    );

    expect(beRong.length).toBeGreaterThanOrEqual(4);
    expect(new Set(beRong).size).toBe(1);
  });

  it('số cột trong chuỗi lưới bằng đúng số tiêu đề cột đã duyệt', () => {
    const khop = /grid-cols-\[([^\]]+)\]/.exec(doc(TEP[0]));
    expect(khop).not.toBeNull();
    const soCot = khop![1].split('_').length;

    expect(soCot).toBe(COT_MUC_NUOC.length);
  });

  /**
   * ⛔⛔ §10.54 ở dạng hẹp nhất: cột lượng mưa ⛔ KHÔNG được có giá trị dự phòng.
   *
   * `?? 0` ở đây biến "chưa có nguồn" (mục G3-a) thành một **khẳng định về thời tiết**, và nó sai
   * mỗi ngày trời mưa. Bộ canh chung `noFabricatedContent` ⛔ không bắt được điều này vì `0` ⛔
   * không phải một chuỗi bịa — nên nó cần một khẳng định riêng.
   */
  it('⛔ cột lượng mưa ⛔ không có giá trị dự phòng — `0 mm` là một khẳng định sai', () => {
    const ma = doc('components/home/WaterLevelRows.tsx');

    expect(ma).toContain('row.luongMua === null');
    expect(ma).not.toMatch(/luongMua\s*\?\?/);
    expect(ma).not.toMatch(/luongMua\s*\|\|/);
  });

  /**
   * ⛔ Ô rỗng phải mang lý do **vào DOM**, ⛔ không chỉ vào tooltip.
   *
   * Bản in và trình đọc màn hình ⛔ không có tooltip; ở đó một ô rỗng chỉ-có-`title` trở lại thành
   * một dấu gạch vô nghĩa, và ba tình huống khác hẳn nhau (chưa gửi số / mất tín hiệu / chưa có
   * nguồn lượng mưa) trông giống hệt nhau.
   */
  it('⛔ lý do ô rỗng đi vào DOM, ⛔ không chỉ nằm ở tooltip', () => {
    const ma = doc('components/home/WaterLevelRows.tsx');

    expect(ma).toContain('sr-only');
    expect(ma).toContain('title={lyDo}');
  });
});
