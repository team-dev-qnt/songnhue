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
 * Trang Liên hệ — nơi thứ hai công bố thông tin liên hệ, dựng ở đợt chỉnh sửa 27/08/2026 (CR-22).
 *
 * ⚠⚠ Bài kiểm này TỪNG đỏ oan vì chỉ soi chân trang. CR-40 và CR-41 yêu cầu bỏ **email** và
 * **giờ làm việc** khỏi chân trang, nên hai khoá ấy rời khỏi `SiteFooter.tsx` — và một bài canh
 * đóng đinh vào một tệp sẽ đọc chuyện đó thành "cổng lại ghi cứng thông tin liên hệ", tức là
 * đúng ngược với sự thật.
 *
 * Bất biến thật không bao giờ là *"tệp X phải chứa chuỗi Y"* mà là *"thông tin liên hệ của Công
 * ty phải đến từ `settings`, ở mọi nơi cổng công bố nó"*. Nên danh sách dưới đây là **các nơi
 * công bố**, và mỗi khoá chỉ cần được đọc ở ít nhất một nơi.
 */
const NGUON_LIEN_HE = readFileSync(join(process.cwd(), 'src/app/lien-he/page.tsx'), 'utf8');

/** Mọi nơi cổng công bố thông tin liên hệ. Thêm nơi thứ tư thì thêm vào đây. */
const NOI_CONG_BO: { ten: string; nguon: string }[] = [
  { ten: 'SiteFooter.tsx', nguon: NGUON },
  { ten: 'app/lien-he/page.tsx', nguon: NGUON_LIEN_HE },
];

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
  it.each(KHOA_BAT_BUOC)('đọc khoá %s ở ít nhất một nơi công bố', (khoa) => {
    const doc = NOI_CONG_BO.filter((n) => n.nguon.includes(`'${khoa}'`)).map((n) => n.ten);
    expect(
      doc,
      `không nơi nào đọc '${khoa}' từ settings — nó đang bị ghi cứng, hoặc đã biến mất khỏi cổng`,
    ).not.toEqual([]);
  });

  /*
    ⛔ CR-40 và CR-41: email và giờ làm việc phải BỎ khỏi chân trang.

    Hai khẳng định dưới đây là vế còn thiếu của bài kiểm trên. Không có chúng thì "đã bỏ khỏi
    chân trang" chỉ là một dòng trong tài liệu nghiệm thu — và lượt sửa giao diện kế tiếp đặt
    lại hai dòng ấy sẽ không làm đỏ gì cả. Đây đúng hình dạng đã trả giá ngày 24/8, khi một bản
    vá giao diện lặng lẽ khôi phục nguyên trạng lỗi cũ vì màn hình vẫn trông đúng.

    ⚠ Canh trên `NGUON` (mã nguồn) chứ không trên DOM: hai khoá vẫn phải TỒN TẠI trong
    `settings` (OI-04 còn chờ Công ty chốt bỏ hẳn email hay thay bằng email công vụ), nên thứ
    cần khẳng định là chân trang không ĐỌC chúng nữa.
  */
  it.each(['company.email', 'company.working-hours'])(
    '⛔ chân trang KHÔNG còn đọc %s (CR-40, CR-41)',
    (khoa) => {
      expect(
        NGUON.includes(`'${khoa}'`),
        `'${khoa}' đã quay lại chân trang — CR-40/CR-41 yêu cầu bỏ khỏi chân trang, ` +
          'giá trị vẫn nằm trong settings và vẫn hiện ở trang Liên hệ',
      ).toBe(false);
    },
  );

  it('⚠ tìm được tệp để soi — bài kiểm chạy qua tập rỗng thì xanh mà không canh gì (luật 7)', () => {
    for (const { ten, nguon } of NOI_CONG_BO) {
      expect(nguon.length, `${ten} rỗng hoặc không đọc được`).toBeGreaterThan(200);
    }
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
