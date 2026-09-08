import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { COT_MUC_NUOC, LUOI_MUC_NUOC } from '@/lib/homeDataColumns';

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
 *
 * <h2>⭐ 08/09/2026 — bất biến này nay được ép bằng KIỂU, và bài kiểm đổi theo</h2>
 *
 * Bốn chuỗi ghi cứng đã được gom về hai hằng số `LUOI_MUC_NUOC` / `BE_RONG_TOI_THIEU_MUC_NUOC`
 * trong `homeDataColumns.ts`. Bốn nơi gọi nay **⛔ không thể lệch nhau** — chúng là cùng một biến.
 *
 * ⛔ Nhưng ⛔ KHÔNG xoá bộ canh này đi. Bất biến cũ (*"bốn chuỗi phải bằng nhau"*) trở nên hiển
 * nhiên, còn bất biến **thật** thì vẫn cần canh và nay khó thấy hơn: *⛔ không nơi gọi nào được
 * quay lại ghi một chuỗi lưới THẲNG vào JSX*. Thêm một trang thứ ba, chép-dán từ trang cũ, là
 * chuyện của một buổi chiều — và lúc ấy hằng số ⛔ không cứu được ai.
 *
 * ⚠ Đây là chỗ bài kiểm cũ **canh văn bản** (luật 2): nó so chuỗi trên mã nguồn, nên một lượt
 * refactor đúng đắn cũng làm nó đỏ — và nó đã đỏ ở đúng lượt refactor ấy. Bản này canh **cấu
 * trúc**: đếm số lượt ghi cứng (phải là 0) và đếm số lượt dùng hằng (phải đủ bốn).
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
  it('⛔⛔ ⛔ KHÔNG nơi gọi nào ghi CỨNG chuỗi lưới hay bề rộng vào JSX', () => {
    // Bất biến thay cho bài "bốn chuỗi phải bằng nhau" (nay hiển nhiên vì chúng là cùng một biến).
    // Thứ còn hỏng được: một trang thứ ba chép-dán chuỗi cũ vào. Đếm, ⛔ không so.
    const ghiCung = TEP.flatMap((t) => [
      ...doc(t).matchAll(/(?:luoi|beRongToiThieu)="(?:grid-cols|min-w)-\[[^\]]+\]"/g),
    ]);

    expect(ghiCung.map((m) => m[0])).toEqual([]);
  });

  it('⭐ bốn lượt truyền đều đi qua HẰNG SỐ dùng chung', () => {
    const dungHang = TEP.flatMap((t) => [
      ...doc(t).matchAll(
        /(?:luoi=\{LUOI_MUC_NUOC\}|beRongToiThieu=\{BE_RONG_TOI_THIEU_MUC_NUOC\})/g,
      ),
    ]);

    // ⚠ Vế chống tập rỗng (luật 7 + 29): mẫu khớp hụt trả mảng rỗng, và một bài chỉ khẳng định
    //   "⛔ không có chuỗi ghi cứng" sẽ XANH TRỌN VẸN trên một tệp đã bị xoá sạch nội dung.
    //   Hai bảng × hai lượt truyền × hai thuộc tính = 8.
    expect(dungHang.length).toBeGreaterThanOrEqual(8);
  });

  it('số cột trong chuỗi lưới bằng đúng số tiêu đề cột đã duyệt', () => {
    // ⭐ Nay đọc từ chính hằng số, ⛔ không đọc từ văn bản của một component.
    const khop = /grid-cols-\[([^\]]+)\]/.exec(LUOI_MUC_NUOC);
    expect(khop).not.toBeNull();

    expect(khop![1].split('_')).toHaveLength(COT_MUC_NUOC.length);
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
