import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';

/**
 * Bảng văn bản — năm cột, và **đường lui trên điện thoại phải còn nguyên**.
 *
 * <h2>Bất biến đắt nhất của bảng này không phải năm cột, mà là ba cột bị ẩn</h2>
 *
 * Cổng tham chiếu (`thuyloisongday.vn/van-ban`, đo 01/09/2026) ẩn cột 3·4·5 dưới 768px và bù lại
 * bằng hai khối vốn ẩn ở desktop: `.date-mobile` trong cột 1 (ngày ban hành + thời gian đăng tải)
 * và `a.mobile` trong cột 2 (nút "Xem chi tiết").
 *
 * <p>Chép nửa đầu mà quên nửa sau thì bảng **vẫn chạy, vẫn đẹp trên máy tính**, và trên điện thoại
 * người đọc mất cả ngày ban hành lẫn lối vào bài — trên đúng nhóm thiết bị chiếm phần lớn lượt
 * truy cập một cổng thông tin. Không có gì đỏ, và người phát hiện sẽ là người dùng thật. Đúng hình
 * dạng quy tắc 19: *việc làm xong nửa đường trông y hệt việc làm xong*.
 *
 * <h2>⚠ Phạm vi của bài này (luật 28)</h2>
 *
 * Nó đọc **chuỗi lớp trong mã nguồn**, nên nó không chứng minh được bảng hiện đúng trên trình
 * duyệt ở 375px. Nó chỉ chứng minh hai khối `md:hidden` chưa bị ai xoá. Phép đo thật thuộc về
 * `e2e/boCucTrangChu.spec.ts`, và bộ ấy **chưa vào CI** (T38.10).
 */

const NGUON = boChuThich(
  readFileSync(join(process.cwd(), 'src/components/DocumentTable.tsx'), 'utf8'),
);

/** Năm tiêu đề cột, đúng thứ tự của cổng tham chiếu. */
const COT = ['Số ký hiệu', 'Trích yếu', 'Nội dung chi tiết', 'Ngày ban hành', 'Thời gian đăng tải'];

describe('DocumentTable — năm cột theo cổng tham chiếu', () => {
  it('⛔ TIỀN ĐỀ: cắt chú thích xong phần thi hành vẫn còn nguyên', () => {
    expect(NGUON).toContain('export function DocumentTable');
    expect(NGUON).toContain('<table');
  });

  it('⭐ đúng năm `<th>`, đúng thứ tự', () => {
    const tieuDe = [...NGUON.matchAll(/<th[\s\S]*?>\s*([^<>{]+?)\s*<\/th>/g)].map((m) =>
      m[1].trim(),
    );
    expect(tieuDe).toEqual(COT);
  });

  it('⭐⭐ ba cột cuối ẩn ở màn hình hẹp — `hidden md:table-cell`', () => {
    // Đếm chốt: 3 `<th>` + 3 `<td>` = 6. Bằng 5 nghĩa là một cột quên lớp ẩn và bảng tràn ngang
    // trên điện thoại; bằng 7 nghĩa là ai đó ẩn nhầm cột "Trích yếu".
    const soO = (NGUON.match(/hidden[^"']*\bmd:table-cell\b/g) ?? []).length;
    expect(soO, 'số ô mang `hidden md:table-cell` không phải 6 (3 th + 3 td)').toBe(6);
  });

  it('⭐⭐ ĐƯỜNG LUI MOBILE còn nguyên: hai khối `md:hidden` trong hai cột đầu', () => {
    const khoiLui = (NGUON.match(/\bmd:hidden\b/g) ?? []).length;
    expect(
      khoiLui,
      `Thiếu khối \`md:hidden\` ⇒ ẩn ba cột mà không bù lại gì. Trên điện thoại người đọc mất ngày
       ban hành (cột 4·5) và mất nút Xem chi tiết (cột 3). Cổng tham chiếu bù bằng \`.date-mobile\`
       và \`a.mobile\` — ở đây là hai khối này.`,
    ).toBe(2);

    // Và chúng phải mang ĐÚNG NỘI DUNG. Hai khối `md:hidden` rỗng cũng đếm được là 2 — con số
    // một mình không phân biệt được "có đường lui" với "có hai thẻ div" (luật 9).
    // ⚠ Cắt tới lần `md:hidden` KẾ TIẾP (hoặc hết chuỗi), không cắt tới `</div>` gần nhất: khối
    //   ngày có hai `<div>` lồng nhau nên mẫu `</div>` không tham lam dừng ngay ở thẻ con.
    const viTri = [...NGUON.matchAll(/\bmd:hidden\b/g)].map((m) => m.index ?? 0);
    const cacKhoi = viTri.map((v, i) => NGUON.slice(v, viTri[i + 1] ?? NGUON.length));
    expect(cacKhoi).toHaveLength(2);
    expect(cacKhoi[0], 'khối lui thứ nhất phải mang NGÀY (cột 4·5 bị ẩn)').toContain(
      'Ngày ban hành:',
    );
    expect(cacKhoi[0]).toContain('Đăng tải:');
    expect(cacKhoi[1], 'khối lui thứ hai phải mang NÚT (cột 3 bị ẩn)').toContain('NutXemChiTiet');
  });

  it('⛔ ô rỗng để TRỐNG — không dấu gạch, không "Đang cập nhật", không suy từ `publishedAt`', () => {
    // Quy tắc 16. Một dấu `—` trong ô "Ngày ban hành" trông như một giá trị; một ô trống thì không.
    expect(NGUON).not.toMatch(/[>{]\s*['"`]—['"`]/);
    expect(NGUON).not.toContain('Đang cập nhật');
    expect(NGUON).not.toContain('Chưa cập nhật');
    // ⛔ Và tuyệt đối không lấy `publishedAt` làm ngày ban hành khi cột kia rỗng.
    expect(NGUON).not.toMatch(/docIssuedDate\s*(\?\?|\|\|)\s*[\w.]*publishedAt/);
  });

  it('⭐ ngày ban hành đi qua `formatNgayThuan`, KHÔNG qua `formatDate`', () => {
    // `formatDate` đi qua `new Date()`; một chuỗi `YYYY-MM-DD` bị hiểu là nửa đêm UTC rồi quy múi
    // giờ — lệch đúng một ngày ở nửa số múi giờ. Đây là hai hàm KHÁC nhau cho hai loại dữ liệu
    // khác nhau, và chỗ dễ nhầm nhất là chính bảng này (nó dùng cả hai, cạnh nhau).
    const dongNgayBanHanh = NGUON.split('\n').filter((d) => d.includes('docIssuedDate'));
    expect(dongNgayBanHanh.length).toBeGreaterThanOrEqual(2);
    for (const dong of dongNgayBanHanh) {
      expect(dong, `\`formatDate(doc.docIssuedDate)\` lệch một ngày: ${dong.trim()}`).not.toMatch(
        /formatDate\(\s*doc\.docIssuedDate/,
      );
    }
    expect(NGUON).toContain('formatNgayThuan(doc.docIssuedDate)');
    // Ngược lại: `publishedAt` LÀ một mốc thời gian nên nó phải đi qua `formatDate`.
    expect(NGUON).toContain('formatDate(doc.publishedAt)');
  });

  it('⭐⭐ KIỂM CHỨNG NGƯỢC: một bảng chép nửa vời PHẢI bị bắt', () => {
    // Luật 1 + 29. Bản dưới đây có đủ năm cột và đủ lớp ẩn, chỉ THIẾU đường lui mobile — đúng
    // hình dạng lỗi mà bài trên sinh ra để bắt.
    const banThieuDuongLui = `
      <th className="w-[200px]">Số ký hiệu</th>
      <td className="hidden md:table-cell">x</td>
      <td className="hidden md:table-cell">y</td>
      <td className="hidden md:table-cell">z</td>
    `;
    expect((boChuThich(banThieuDuongLui).match(/\bmd:hidden\b/g) ?? []).length).toBe(0);

    // Và một chuỗi chỉ NHẮC TỚI `md:hidden` trong chú thích thì không được tính là có.
    const chiLaChuThich = `{/* trước đây dùng md:hidden ở đây */}\n<div className="mt-2">x</div>`;
    expect((boChuThich(chiLaChuThich).match(/\bmd:hidden\b/g) ?? []).length).toBe(0);
  });
});
