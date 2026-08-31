import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * **`PageResult` chỉ được sinh ra từ `api.getPage`, không bao giờ từ `api.get`.**
 *
 * <h2>Lỗi tìm được ngày 31/08/2026 — kiểu nói dối, và không ai thấy vì bảng đang rỗng</h2>
 *
 * `api.get<T>` trả về `envelope.data`, tức **mảng phần tử**. `api.getPage<T>` mới dựng
 * `{ items, meta }` từ `envelope.data` + `envelope.meta`. Ba nơi khai `api.get<PageResult<X>>(…)`:
 * TypeScript tin rằng nó cầm `{ items, meta }`, còn lúc chạy nó cầm một **mảng**. Hệ quả:
 *
 * - `constructions.data?.items` → `undefined` → `?? []` → **bảng Hồ sơ công trình luôn 0 dòng**;
 * - `constructions.data?.meta?.totalElements` → `undefined` → `?? 0` → phân trang luôn nói "0";
 * - ô chọn công trình trong Modal "Nhập nhanh" cũng rỗng ⇒ **T19.6 không dùng được** dù backend đúng.
 *
 * <p>⛔ Không một phép kiểm nào bắt được, và `tsc` cũng không: khai kiểu là một **lời khẳng định**
 * của người viết, không phải một phép đo. Nó nằm im vì bảng công trình đang rỗng thật (danh mục
 * công trình thuộc G8, Công ty chưa gửi) — nghĩa là triệu chứng của lỗi **trùng khít** với trạng
 * thái đúng. Đúng họ với §10.62: một cơ chế che mất lỗi thì lỗi không bao giờ nổi lên.
 *
 * <h2>⚠ Giới hạn (luật 28)</h2>
 *
 * Bài này soi **`admin-app/src`** bằng văn bản nguồn. Nó bắt đúng một hình dạng — `api.get<` kèm
 * `PageResult` trong cùng một biểu thức. Nó <b>không</b> biết một biến trung gian có kiểu
 * `PageResult` được gán từ `api.get` ở hai dòng cách nhau. Đừng đọc bài xanh này thành *"mọi lượt
 * đọc phân trang đã đúng"*.
 */
const GOC = join(process.cwd(), 'src');

/** `api.get<PageResult<…>>` — lời khẳng định sai kiểu, phải dùng `api.getPage` (xem javadoc). */
const MAU_SAI = /api\.get\s*<\s*PageResult\s*</;

export function viPhamPhanTrang(ma: string): boolean {
  return MAU_SAI.test(ma);
}

function moiTepNguon(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) {
      return moiTepNguon(duong);
    }
    return /\.tsx?$/.test(ten) && !/\.test\.tsx?$/.test(ten) ? [duong] : [];
  });
}

describe('Đọc phân trang — PageResult chỉ đến từ api.getPage', () => {
  const tep = moiTepNguon(GOC);

  it('quét được cây nguồn — chống xanh trên tập rỗng', () => {
    // ⛔ Đổi bố cục thư mục mà quên bài này thì khẳng định dưới chạy trên danh sách rỗng.
    expect(tep.length).toBeGreaterThan(50);
  });

  it('không tệp nào khai api.get<PageResult<…>>', () => {
    const pham = tep.filter((duong) => viPhamPhanTrang(readFileSync(duong, 'utf8')));

    expect(
      pham,
      `những tệp này khai api.get<PageResult<…>> — lúc chạy chúng nhận một MẢNG, nên ` +
        `.items và .meta đều undefined và bảng luôn rỗng. Dùng api.getPage<…> thay thế: ` +
        pham.join(', '),
    ).toEqual([]);
  });

  describe('kiểm chứng ngược — vị từ phải bắt được bản đã gây ra lỗi', () => {
    it('bản hỏng (nguyên văn dòng của ConstructionsPage trước 31/08) → bị bắt', () => {
      expect(
        viPhamPhanTrang("      api.get<PageResult<ConstructionRow>>('/ops/constructions', {"),
      ).toBe(true);
    });

    it('có khoảng trắng giữa các dấu ngoặc vẫn bị bắt', () => {
      expect(viPhamPhanTrang('api.get < PageResult < Row >>(url)')).toBe(true);
    });

    it('bản đúng KHÔNG bị bắt — vị từ không phải "luôn đỏ"', () => {
      expect(viPhamPhanTrang("api.getPage<ConstructionRow>('/ops/constructions', {})")).toBe(false);
      expect(viPhamPhanTrang("api.get<ConstructionDetail>('/ops/constructions/x')")).toBe(false);
    });
  });
});
