import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

const GOC = join(process.cwd(), 'src');

/**
 * Bảng mực nước trên cổng — bất biến về **nội dung**, sau khi WS-44 thay lưới CSS bằng `<table>`.
 *
 * <h2>⚠ Bài này ĐÃ ĐỔI ĐỐI TƯỢNG, và đó là lý do nó ⛔ không bị xoá</h2>
 *
 * Bản trước canh một bất biến của **lưới CSS**: bốn lượt truyền `grid-cols-[…]` phải đi qua cùng
 * một hằng số, nếu không tiêu đề "Lượng mưa" đứng trên cột "Thời điểm đo". WS-44 thay hai bảng ấy
 * bằng `<table>` thật (§6.1.2 đòi gộp ô, thứ `div` ⛔ không làm được), nên `LUOI_MUC_NUOC` /
 * `COT_MUC_NUOC` / `WaterLevelRows` đã bị **xoá** — bất biến cũ ⛔ không còn đối tượng.
 *
 * <p>⛔ Nhưng hai khẳng định dưới đây thì <b>không</b> mất đối tượng, và chúng là phần đắt nhất của
 * bài cũ: chúng canh §10.54 ở dạng hẹp nhất — <i>một ô ⛔ không có số ⛔ không được giả vờ có</i>.
 * Xoá cả bài đi là để hai bài học ấy chết cùng một lượt dọn dẹp.
 *
 * ⚠ Giới hạn: kho ⛔ chưa có bộ dựng DOM cho `public-web` (0 `@testing-library`, 0 `jsdom`), nên
 * đây là canh **văn bản nguồn**. Luật 2 nói canh cấu trúc tốt hơn; luật 28 đòi bộ canh nói ra phạm
 * vi của chính nó — dòng này là chỗ nói.
 */
const BANG = ['components/home/BangTrangChuMucNuoc.tsx', 'components/home/BangLuoiMucNuoc.tsx'];

function doc(tuongDoi: string): string {
  return readFileSync(join(GOC, tuongDoi), 'utf8');
}

describe('bảng mực nước — ô rỗng ⛔ không được giả vờ có số', () => {
  it('⚠ Vế chống tập rỗng — cả hai tệp phải ĐANG TỒN TẠI và có nội dung', () => {
    // ⛔ Thiếu vế này thì một lượt đổi tên tệp làm `doc()` ném… hoặc tệ hơn, một lượt xoá nội dung
    //   làm mọi khẳng định `not.toMatch` bên dưới XANH TRỌN VẸN trên chuỗi rỗng (luật 7).
    for (const t of BANG) {
      expect(doc(t).length, `${t} rỗng hoặc ⛔ không tồn tại`).toBeGreaterThan(500);
    }
  });

  it('⛔ ⛔ KHÔNG có giá trị dự phòng cho ô thiếu số — `?? 0` là một khẳng định', () => {
    // §10.54 ở dạng hẹp nhất. `0 mm` biến "chưa có nguồn" (G3-a) thành một khẳng định về thời
    // tiết, và nó sai mỗi ngày trời mưa. `noFabricatedContent` ⛔ không bắt được vì `0` ⛔ không
    // phải một chuỗi bịa — nên nó cần khẳng định riêng.
    for (const t of BANG) {
      const ma = doc(t);
      expect(ma, t).not.toMatch(/giaTri\s*(\?\?|\|\|)/);
      expect(ma, t).not.toMatch(/luongMua\s*(\?\?|\|\|)\s*0/);
      expect(ma, t).not.toMatch(/'0\.00'|"0\.00"/);
    }
  });

  it('⛔ Lý do ô rỗng vào DOM, ⛔ không chỉ vào tooltip', () => {
    // Bản in và trình đọc màn hình ⛔ không có tooltip; ở đó một ô chỉ-có-`title` trở lại thành
    // một dấu gạch vô nghĩa, và các tình huống khác hẳn nhau trông giống hệt nhau.
    for (const t of BANG) {
      expect(doc(t), t).toContain('aria-label');
    }
  });

  it('⛔ Lý do cột lượng mưa đến từ BACKEND, ⛔ không ghi cứng ở FE', () => {
    // G3-a là một sự thật về NGUỒN DỮ LIỆU. Cổng ⛔ không biết nó, và ngày nguồn mưa có thật thì
    // một câu ghi cứng ở đây thành lời nói dối ⛔ không ai nhớ để xoá.
    const ma = doc('components/home/BangTrangChuMucNuoc.tsx');
    expect(ma).toContain('meta.lyDoLuongMua');
    expect(ma).not.toMatch(/G3-a[^\n]*['"]/);
  });

  it('⛔ ⛔ Không ghi cứng mã màu — màu đi qua design-tokens (nợ T25.23)', () => {
    for (const t of BANG) {
      expect(doc(t), t).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
      expect(doc(t), t).toContain('alertLevelColors');
    }
  });
});
