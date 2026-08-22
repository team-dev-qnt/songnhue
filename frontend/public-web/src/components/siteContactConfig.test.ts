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
    const soDienThoai = /\(\d{3,4}\)\s*\d{3,4}\s*\d{3,4}/g;
    expect(doc().match(soDienThoai) ?? []).toEqual([]);
  });

  it('thanh đầu trang đọc số đường dây nóng từ cấu hình', () => {
    expect(NGUON_HEADER).toContain("'company.hotline'");
  });

  it('⛔ không còn email hay tên miền của Công ty ghi cứng trong mã', () => {
    expect(NGUON).not.toMatch(/[\w.+-]+@[\w-]+\.[\w.]+/);
  });

  it('⛔ không còn địa chỉ trụ sở ghi cứng trong mã', () => {
    expect(NGUON).not.toMatch(/Thanh Bình|Mộ Lao|Hà Đông/);
  });
});
