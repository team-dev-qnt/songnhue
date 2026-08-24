import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * Đầu trang và chân trang cổng **không được ghi cứng** thông tin liên hệ của Công ty.
 *
 * ## Lỗi đã có thật
 *
 * `SiteFooter.tsx` ghi thẳng địa chỉ trụ sở, số điện thoại, fax, email và số đường dây nóng phòng
 * chống thiên tai vào mã nguồn — số hotline còn xuất hiện **hai lần**. Cùng lúc đó năm tham số
 * `company.*` nằm trong bảng `settings`, bày ra trên màn hình cấu hình hệ thống, mà **không dòng mã
 * nào đọc**; ba trong năm còn để trống.
 *
 * Hậu quả không phải chuyện thẩm mỹ: đây là cổng của một doanh nghiệp nhà nước, và số điện thoại
 * hiện trên đó là số người dân gọi khi có sự cố công trình. Đổi nó lẽ ra là một ô nhập trên giao
 * diện, chứ không phải sửa mã nguồn rồi dựng lại image.
 *
 * ## Vì sao bài kiểm soi mã nguồn
 *
 * Kiểm bằng cách dựng component thì phải giả lập `getSiteConfig()`, và một bản giả trả về đủ giá
 * trị sẽ **xanh y hệt** dù component vẫn ghi cứng — vì chuỗi cứng và giá trị cấu hình trông giống
 * nhau trên màn hình. Thứ cần khẳng định ở đây là *nguồn* của dữ liệu, nên phải nhìn vào mã.
 */
const NGUON = readFileSync(join(process.cwd(), 'src/components/SiteFooter.tsx'), 'utf8');

/**
 * Thanh đầu trang cũng hiện số đường dây nóng — cùng một số, ghi cứng ở **hai** tệp. Bỏ sót tệp này
 * thì sửa số trên giao diện chỉ đổi được chân trang, còn đầu trang vẫn số cũ; và hai con số khác
 * nhau trên cùng một trang còn tệ hơn một con số cũ.
 */
const NGUON_HEADER = readFileSync(join(process.cwd(), 'src/components/SiteHeader.tsx'), 'utf8');

/** Những khoá chân trang bắt buộc lấy từ cấu hình. */
const KHOA_BAT_BUOC = [
  'company.address',
  'company.phone',
  'company.email',
  'company.hotline',
  'company.working-hours',
];

describe('Cổng lấy thông tin liên hệ của Công ty từ cấu hình', () => {
  it.each(KHOA_BAT_BUOC)('đọc khoá %s', (khoa) => {
    expect(NGUON).toContain(`'${khoa}'`);
  });

  it.each([
    ['SiteFooter.tsx', () => NGUON],
    ['SiteHeader.tsx', () => NGUON_HEADER],
  ])('⛔ %s không còn số điện thoại nào ghi cứng', (_ten, doc) => {
    // Bắt theo HÌNH DẠNG số điện thoại Việt Nam có mã vùng trong ngoặc, không bắt theo một chuỗi
    // cụ thể: khoá theo đúng số đang dùng thì người sau đổi sang số khác vẫn ghi cứng mà vẫn xanh.
    //
    // ⚠⚠ Bản đầu viết `\(\d{3,4}\)\s*\d{3,4}\s*\d{3,4}` — CHỈ chấp nhận khoảng trắng giữa các
    //    nhóm số, nên nó **bỏ lọt đúng định dạng Công ty đang dùng**: `(024) 33.546.247` có dấu
    //    chấm. Đo thật ngày 24/8: một bản vá giao diện đặt lại số điện thoại, fax và hotline vào
    //    mã nguồn, và bài kiểm này **xanh trọn vẹn** — chỉ bài canh email bắt được. Một bộ canh
    //    không nhận ra dạng dữ liệu thật thì nó canh cho ai?
    //
    //    Nay chấp nhận dấu chấm, gạch ngang hoặc khoảng trắng làm dấu phân nhóm.
    const soDienThoai = /\(\d{3,4}\)[\s.-]*\d{2,4}[\s.-]*\d{3,4}[\s.-]*\d{3,4}/g;
    expect(doc().match(soDienThoai) ?? []).toEqual([]);
  });

  it('thanh đầu trang đọc số đường dây nóng từ cấu hình', () => {
    expect(NGUON_HEADER).toContain("'company.hotline'");
  });

  it('⛔ không còn email hay tên miền của Công ty ghi cứng trong mã', () => {
    expect(NGUON).not.toMatch(/[\w.+-]+@[\w-]+\.[\w.]+/);
  });

  it('⛔ không còn địa chỉ trụ sở ghi cứng trong mã', () => {
    // ⚠⚠ Bản đầu phân biệt hoa thường, và địa chỉ mới của Công ty viết HOA toàn bộ
    //    (`… QUẬN HÀ ĐÔNG - THÀNH PHỐ HÀ NỘI.`) nên nó đi lọt — cùng một lượt vá giao diện, cùng
    //    một kiểu bỏ sót với bộ canh số điện thoại ở trên. Thêm cờ `i`, và thêm những mốc địa danh
    //    của địa chỉ mới chứ không chỉ giữ mốc của địa chỉ cũ.
    expect(NGUON).not.toMatch(/thanh bình|mộ lao|hà đông|xala|new house/i);
  });

  it('⛔ không dòng nào của chân trang dùng chuỗi cứng làm giá trị dự phòng cho `company.*`', () => {
    // Bài canh ở tầng CẤU TRÚC, phủ cả sáu khoá cùng lúc — ba bài trên bắt theo hình dạng từng
    // loại dữ liệu (điện thoại, email, địa chỉ) nên luôn có loại thứ tư lọt qua: giờ làm việc,
    // tên viết tắt, số fax nước ngoài… Ở đây khẳng định trực tiếp điều thật sự cần đúng: mọi khoá
    // `company.*` phải rơi về chuỗi RỖNG, không rơi về một giá trị bịa sẵn.
    const duPhongCung = [...NGUON.matchAll(/\['(company\.[\w.-]+)'\]\s*\?\?\s*(.+)/g)]
      .filter(([, , duPhong]) => !duPhong.trimStart().startsWith("''"))
      .map(([, khoa]) => khoa);

    expect(
      duPhongCung,
      `những khoá này có giá trị dự phòng ghi cứng: ${duPhongCung.join(', ')} — dữ liệu đã seed ở ` +
        'V202608241255, nên dự phòng phải là chuỗi rỗng',
    ).toEqual([]);
  });
});
