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

  /*
   * ⛔⛔ BA PHÉP CANH HÌNH DẠNG (điện thoại · email · địa chỉ) ĐÃ CHUYỂN SANG
   *     `noFabricatedContent.test.ts`, nơi chúng soi TOÀN BỘ `src/`.
   *
   *     Lý do chuyển, đo được 25/8: chúng chỉ đọc đúng hai tệp — `SiteFooter.tsx` và
   *     `SiteHeader.tsx` — trong khi `components/home/AffiliatedUnitsLinks.tsx` chứa TÁM số
   *     điện thoại bịa và `PortalSidebar.tsx` chứa một số trực ban PCTT ghi cứng. Chín con số
   *     nằm ngoài tầm với suốt thời gian bộ canh này báo xanh.
   *
   *     Và ngay cả khi đã soi đúng tệp thì regex cũ vẫn không bắt được: nó đòi ĐÚNG BỐN nhóm
   *     số vì được chỉnh cho `(024) 33.546.247`, nên `(024) 3382 4580` — dạng ba nhóm — đi
   *     lọt. Đây là lần thứ ba cùng một regex phải nới ra vì không khớp dữ liệu thật đang
   *     dùng (luật 24), và là lý do bản mới tự kiểm chứng với CẢ HAI định dạng.
   *
   *     Bài này nay chỉ giữ thứ đặc thù cho chân/đầu trang: hợp đồng "đọc từ cấu hình".
   */
  it('thanh đầu trang đọc số đường dây nóng từ cấu hình', () => {
    expect(NGUON_HEADER).toContain("'company.hotline'");
  });

  it('⛔ không dòng nào của chân trang dùng chuỗi cứng làm giá trị dự phòng cho `company.*`', () => {
    // Bài canh ở tầng CẤU TRÚC, phủ cả sáu khoá cùng lúc — ba bài trên bắt theo hình dạng từng
    // loại dữ liệu (điện thoại, email, địa chỉ) nên luôn có loại thứ tư lọt qua: giờ làm việc,
    // tên viết tắt, số fax nước ngoài… Ở đây khẳng định trực tiếp điều thật sự cần đúng: mọi khoá
    // `company.*` phải rơi về chuỗi RỖNG, không rơi về một giá trị bịa sẵn.
    // ⚠ Bản đầu chỉ soi tiền tố `company.` và chỉ toán tử `??`. Ba khoá
    //   `site.footer.social.*` rơi về `|| 'https://facebook.com'` — cùng một hình dạng "dự
    //   phòng bịa", khác tiền tố và khác toán tử, nên đi lọt trọn vẹn (§10.54). Nay phủ mọi
    //   khoá cấu hình và cả hai toán tử.
    const duPhongCung = [
      ...NGUON.matchAll(/\['((?:company|site)\.[\w.-]+)'\]\s*(?:\?\?|\|\|)\s*(.+)/g),
    ]
      // Cấm CHUỖI VIẾT CỨNG. Cho phép `''` và cho phép biểu thức không chứa chuỗi nào —
      // `SITE.name` (hằng số sản phẩm, một nguồn duy nhất) hay `` `© ${năm} ${tên}` `` là giá
      // trị TÍNH RA, không phải một bản sao dữ liệu sẽ mục đi khi ai đó sửa trên giao diện.
      .filter(([, , duPhong]) => {
        const v = duPhong.trimStart();
        return !v.startsWith("''") && /['"][^'"]{4,}['"]/.test(v);
      })
      .map(([, khoa]) => khoa);

    expect(
      duPhongCung,
      `những khoá này có giá trị dự phòng ghi cứng: ${duPhongCung.join(', ')} — dữ liệu đã seed ở ` +
        'V202608241255, nên dự phòng phải là chuỗi rỗng',
    ).toEqual([]);
  });
});
