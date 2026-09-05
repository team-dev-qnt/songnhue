import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';

/**
 * Dải "Liên kết website" — nhãn **không bao giờ rời khỏi cây DOM**.
 *
 * <h2>Bất biến này canh cái gì</h2>
 *
 * QuanTran 01/09 chốt hai điều cho dải này: ảnh kéo kín khung (`object-fill`), và một công tắc
 * quản trị bật/tắt phần chữ *"vì có thể phần text sẽ bị ảnh hưởng bởi phần màu"*.
 *
 * <p>Cách hiển nhiên nhất để làm điều thứ hai là `{hienNhan ? <span>{label}</span> : null}`. Nó
 * chạy đúng, trông đúng, và **hỏng một thứ không nhìn thấy được**: khi tắt, thẻ `<a>` chỉ còn một
 * `<img alt="">` bên trong. Một liên kết không có nội dung văn bản nào là một liên kết **không có
 * tên** — trình đọc màn hình đọc địa chỉ URL thay cho tên cơ quan. Đó là WCAG 2.4.4 (Link
 * Purpose), tức là một lỗi tiếp cận, không phải một lựa chọn trình bày; và cổng của doanh nghiệp
 * nhà nước phải dùng được bằng trình đọc màn hình.
 *
 * <p>Nên nhánh tắt là `sr-only`, không phải `null`: nhãn vẫn nằm trong DOM, chỉ không hiện ra
 * bằng mắt. Bài này khẳng định đúng điều đó, và **khẳng định cả chiều ngược lại** — mục chưa có
 * logo thì nhãn luôn hiện, vì tắt chữ ở một thẻ không ảnh là để lại một ô trắng rỗng.
 */

const NGUON = boChuThich(
  readFileSync(join(process.cwd(), 'src/components/home/AffiliatedUnitsLinks.tsx'), 'utf8'),
);

/** Lát mã của khối `portalLinks` — không soi cả tệp, vì nửa trên là lưới Xí nghiệp. */
const KHOI_LIEN_KET = NGUON.slice(NGUON.indexOf('portalLinks.map'));

describe('Liên kết website — nhãn và banner', () => {
  it('⛔ TIỀN ĐỀ: lát mã đúng khối, không rỗng', () => {
    expect(
      KHOI_LIEN_KET.length,
      'không tìm thấy khối portalLinks — bài kiểm soi tập rỗng',
    ).toBeGreaterThan(400);
    expect(KHOI_LIEN_KET).toContain('muc.label');
  });

  it('⭐⭐ nhánh TẮT là `sr-only`, KHÔNG phải `null` — nhãn phải ở lại trong DOM', () => {
    expect(KHOI_LIEN_KET).toContain("'sr-only'");
    // Đây là hình dạng phải cấm: điều kiện `hienNhan` mà nhánh sai là `null`/rỗng.
    expect(
      KHOI_LIEN_KET,
      'nhãn biến mất khỏi DOM khi tắt ⇒ liên kết chỉ còn <img alt=""> ⇒ không có tên (WCAG 2.4.4)',
    ).not.toMatch(/hienNhan[\s\S]{0,120}\?[\s\S]{0,200}:\s*null/);
    expect(KHOI_LIEN_KET).not.toMatch(/\{hienNhan\s*&&/);
  });

  it('⭐ mục CHƯA có logo thì nhãn luôn hiện, bất kể công tắc', () => {
    // Tắt chữ ở một thẻ không có ảnh là để lại một ô trắng không nội dung (quy tắc 16).
    expect(KHOI_LIEN_KET).toMatch(/hienNhan\s*\|\|\s*!logo/);
  });

  it('⭐ ảnh kéo kín khung — `object-fill`, cao 90px, kín bề rộng ô', () => {
    // QuanTran chốt 01/09. Ghi tường minh chứ không bỏ trống `object-fit`: một lớp có tên thì
    // `grep` thấy được, còn một mặc định của trình duyệt thì không ai đọc ra từ mã.
    expect(KHOI_LIEN_KET).toContain('object-fill');
    expect(KHOI_LIEN_KET).toContain('h-[90px] w-full');
    // ⛔ Và KHÔNG được lẫn hai chế độ cũ: `contain` để lại hai dải trắng, `cover` cắt mép logo.
    expect(KHOI_LIEN_KET).not.toContain('object-contain');
    expect(KHOI_LIEN_KET).not.toContain('object-cover');
  });

  it('⭐ lưới BA cột ở desktop — số học của khung 381×90', () => {
    // (1184 − 2×20)/3 = 381px; ở chiều cao 90px cho ra đúng tỉ lệ dải logo 377×90 của cổng tham
    // chiếu. Bốn cột cho 281×90 (3,1:1) và mọi banner tải lên méo thêm một lần nữa.
    // ⚠ Soi LÁT của khối liên kết, không soi cả tệp: lưới "Xí nghiệp trực thuộc" ở nửa trên vẫn
    //   là 4 cột và đúng như vậy — thẻ Xí nghiệp không có banner nào để giữ tỉ lệ.
    const luoiLienKet = NGUON.slice(
      NGUON.indexOf('Liên kết website'),
      NGUON.indexOf('portalLinks.map'),
    );
    expect(luoiLienKet).toContain('lg:grid-cols-3');
    expect(luoiLienKet, 'lưới 4 cột phá tỉ lệ banner ~4:1').not.toContain('lg:grid-cols-4');
  });

  it('⭐⭐ KIỂM CHỨNG NGƯỢC: bản gỡ nhãn khỏi DOM PHẢI bị bắt', () => {
    // Luật 1 + 29. Đây đúng là đoạn mã mà một người viết cẩn thận sẽ viết ra đầu tiên.
    const banHong = boChuThich(`
      {hienNhan ? (
        <span className="px-4 py-3 text-sm">{muc.label}</span>
      ) : null}
    `);
    expect(banHong).toMatch(/hienNhan[\s\S]{0,120}\?[\s\S]{0,200}:\s*null/);
    expect(banHong).not.toContain("'sr-only'");

    // Khẳng định về SỐ LƯỢNG — không chia sẻ giả định nào với hai mẫu regex ở trên.
    const banDung = [KHOI_LIEN_KET, banHong].filter((ma) => ma.includes("'sr-only'"));
    expect(banDung, 'bản hỏng và bản đúng cho cùng kết quả ⇒ bộ canh không canh gì').toHaveLength(
      1,
    );
  });
});
