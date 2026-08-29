import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from '../lib/boChuThich';

/**
 * **Không component nào được chứa dữ liệu nghiệp vụ bịa.**
 *
 * <h2>Lỗi đã có thật — §10.54</h2>
 *
 * Bảy khối của trang chủ từng có một mảng viết cứng để lấp chỗ trống khi API trả về ít dữ
 * liệu, và cách lấp là `articles.length >= 4 ? articles : [...articles, ...BIA]`. Hệ quả:
 * **một mảng rỗng cho ra một trang chủ đầy**. Đường dữ liệu hỏng hoàn toàn và đường dữ liệu
 * chạy đúng trông giống hệt nhau, chỉ khác ở chỗ tên bài không có thật.
 *
 * Tổng cộng 14 bài viết, 4 văn bản **có số hiệu và người ký**, 5 trạm quan trắc **có mực nước
 * và một mức cảnh báo BĐ I trên tên cống có thật**, 8 xí nghiệp **có số điện thoại**, 4 ảnh
 * hotlink từ Unsplash, 1 video YouTube gắn tiêu đề "Phóng sự … Sông Nhuệ".
 *
 * Đo được ngày 25/8: staging phục vụ đúng trang ấy sau mỗi lượt triển khai, và log của
 * `public-web` còn ghi lại việc trình duyệt thật đi prefetch 14 liên kết không tồn tại.
 *
 * <h2>Vì sao bài kiểm ở tầng mã nguồn</h2>
 *
 * Dựng component rồi khẳng định "không có bài nào" thì phải truyền mảng rỗng vào — mà đó đúng
 * là thứ ta muốn kiểm, nên bài kiểm sẽ chỉ chứng minh lại chính nó. Thứ cần khẳng định ở đây
 * là *nguồn* của dữ liệu, và nguồn thì nhìn thấy ở mã (cùng lý lẽ với
 * `siteContactConfig.test.ts`).
 *
 * ⚠ Ba tầng, cố ý chồng nhau:
 *   1. theo **tên trường** — thông báo lỗi chỉ thẳng vào thứ sai;
 *   2. theo **cấu trúc** — mọi mảng ≥3 object literal, bắt cả loại trường chưa ai nghĩ ra
 *      (luật 24: bắt theo từng loại dữ liệu thì luôn có loại thứ tư lọt qua);
 *   3. theo **địa chỉ ngoài** — chặn hotlink quay lại.
 */

const GOC = join(process.cwd(), 'src');

/**
 * Hằng số dữ liệu tĩnh có thật, được phép tồn tại — kèm lý do, không phải danh sách để dài thêm.
 *
 * ⭐ Ngày 28/08/2026 danh sách này **rỗng trở lại**. Mục duy nhất của nó, `EXTERNAL_PORTALS`
 * (bốn cổng TTĐT cơ quan cấp trên), đã chuyển vào `menu_items` vị trí `LIEN_KET` — CR-21 yêu cầu
 * Công ty rà soát lại tên và địa chỉ bốn cơ quan ấy, và một hằng số trong mã thì rà xong cũng
 * không sửa được. Lý lẽ miễn trừ ghi ở đây ("liên kết điều hướng, không phải dữ liệu nghiệp vụ")
 * đúng về bản chất dữ liệu nhưng trả lời sai câu hỏi: vấn đề không phải nó có thật hay không, mà
 * là **ai sửa được nó**.
 */
const CHO_PHEP_MANG = new Set<string>([]);

/** Tên miền ngoài được phép xuất hiện trong mã component. */
const CHO_PHEP_TEN_MIEN = [
  'youtube-nocookie.com', // khung nhúng video, tuân thủ CSP — ID video đến từ props
  'google.com', // liên kết tra bản đồ, dựng từ địa chỉ trong `settings` — không phải dữ liệu bịa
  // ⭐ Ba tên miền cơ quan nhà nước (mard / hanoi / cucthuyloi) đã gỡ khỏi danh sách cùng lượt
  //   chuyển `EXTERNAL_PORTALS` sang `menu_items`: không còn địa chỉ nào trong mã cổng, nên để
  //   chúng ở đây là chừa sẵn một lối cho lần sau ai đó ghi cứng lại.
];

function timTsx(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) return timTsx(duong);
    return ten.endsWith('.tsx') && !ten.includes('.test.') ? [duong] : [];
  });
}

const TEP = timTsx(GOC);
const MA = TEP.map((duong) => ({
  ten: duong.slice(GOC.length + 1),
  nguon: boChuThich(readFileSync(duong, 'utf8')),
}));

/** Từng hình dạng dữ liệu bịa, kèm một mẫu vi phạm để tự kiểm chứng bộ canh. */
const HINH_DANG: { ten: string; mau: RegExp; viPham: string }[] = [
  { ten: 'slug bài viết viết cứng', mau: /\bslug:\s*['"`]/, viPham: "  slug: 'bao-so-4'," },
  {
    ten: 'ngày đăng viết cứng',
    mau: /\bpublishedAt:\s*['"`]/,
    viPham: "  publishedAt: '2026-08-20T08:00:00Z',",
  },
  { ten: 'lượt xem viết cứng', mau: /\bviewCount:\s*\d/, viPham: '  viewCount: 1420,' },
  { ten: 'mực nước viết cứng', mau: /\bwaterLevel:\s*['"`]/, viPham: "  waterLevel: '+4.20 m'," },
  {
    ten: 'số hiệu văn bản viết cứng',
    mau: /['"`]\s*\d{1,4}\/[A-ZĐ]{2,3}-[A-ZĐ]{2,4}\s*['"`]/,
    viPham: "  code: '158/QĐ-SN',",
  },
  // ⚠⚠ `{1,2}` chứ KHÔNG phải hai nhóm bắt buộc. Bản của `siteContactConfig.test.ts` đòi đúng
  //    BỐN nhóm số vì nó được chỉnh cho `(024) 33.546.247`; nó KHÔNG khớp `(024) 3382 4580`,
  //    dạng ba nhóm — và đó là dạng của tám số bịa ở khối đơn vị trực thuộc. Tức bộ canh cũ
  //    không bắt được chúng kể cả khi đã soi đúng tệp. Luật 24, lần thứ ba trên cùng một regex.
  {
    ten: 'số điện thoại viết cứng',
    mau: /\(\d{3,4}\)[\s.-]*\d{2,4}(?:[\s.-]*\d{3,4}){1,2}/,
    viPham: "  phone: '(024) 3382 4580',",
  },
  {
    ten: 'email hoặc tên miền của Công ty viết cứng',
    mau: /['"`][\w.+-]+@[\w-]+\.[\w.]+['"`]/,
    viPham: "  email: 'vanthu@thuyloisongnhue.vn',",
  },
  {
    // ⚠ Cờ `i` là bắt buộc: địa chỉ trụ sở của Công ty được viết HOA toàn bộ, và bản đầu của
    //   bộ canh cũ phân biệt hoa thường nên đi lọt đúng dạng đang dùng.
    ten: 'địa chỉ trụ sở viết cứng',
    mau: /thanh bình|mộ lao|hà đông|xala|new house/i,
    viPham: '  address: "… QUẬN HÀ ĐÔNG - THÀNH PHỐ HÀ NỘI."',
  },
  {
    ten: 'số điện thoại viết cứng — dạng chấm phân nhóm',
    mau: /\(\d{3,4}\)[\s.-]*\d{2,4}(?:[\s.-]*\d{3,4}){1,2}/,
    viPham: "  phone: '(024) 33.546.247',",
  },
];

describe('Component không chứa dữ liệu nghiệp vụ bịa', () => {
  it('⚠ tìm được tệp để soi — bài kiểm chạy qua tập rỗng thì xanh mà không canh gì (luật 7)', () => {
    expect(TEP.length).toBeGreaterThan(10);
    // ⚠ Neo vào một tệp CÓ THẬT, và nó phải là tệp từng chứa dữ liệu bịa. `HomeHeroFeatured.tsx`
    //   là neo cũ; nó đã bị CR-10/CR-11 thay bằng `HomeHotNews.tsx` (bài đinh nhường chỗ cho
    //   slider ảnh). Neo chết là bài kiểm đỏ oan — nhưng bỏ neo hẳn thì bộ canh quét qua tập
    //   rỗng vẫn xanh trọn vẹn, đúng luật 7. Nên đổi neo, không gỡ neo.
    const ten = MA.map((m) => m.ten);
    expect(ten).toContain(join('components', 'home', 'HomeNewsColumn.tsx'));
    expect(ten).toContain(join('components', 'home', 'AffiliatedUnitsLinks.tsx'));
    // Tám trang dựng ở đợt 27/08/2026 nằm dưới `app/`, không phải `components/` — bộ canh phải
    // với tới chúng, vì bảng 7 cột và bảng 6 cột là đúng chỗ dữ liệu bịa dễ mọc lại nhất.
    expect(ten).toContain(join('app', 'gioi-thieu', 'xi-nghiep', 'page.tsx'));
    expect(ten).toContain(join('app', 'quan-ly-van-hanh', 'danh-muc-cong-trinh', 'page.tsx'));
  });

  describe.each(HINH_DANG)('$ten', ({ mau, viPham }) => {
    it('không tệp nào vi phạm', () => {
      const pham = MA.filter((m) => mau.test(m.nguon)).map((m) => m.ten);
      expect(pham, `những tệp này chứa dữ liệu viết cứng: ${pham.join(', ')}`).toEqual([]);
    });

    it('⛔ kiểm chứng ngược: bộ canh bắt được mẫu vi phạm', () => {
      expect(mau.test(viPham)).toBe(true);
    });
  });

  it('⭐ backstop cấu trúc: không mảng ≥3 object literal nào trong component', () => {
    // Bắt theo từng tên trường thì luôn có trường thứ bảy lọt qua. Dòng này hỏi câu tổng
    // quát: có ai đang gõ sẵn một bộ bản ghi vào mã không.
    const pham = MA.flatMap(({ ten, nguon }) =>
      [...nguon.matchAll(/const\s+([A-Za-z_$][\w$]*)[^=]*=\s*\[([\s\S]*?)\n\];/g)]
        .filter(
          ([, mang, than]) =>
            !CHO_PHEP_MANG.has(mang) && (than.match(/\n\s*\{/g) ?? []).length >= 3,
        )
        .map(([, mang]) => `${ten}:${mang}`),
    );
    expect(
      pham,
      `mảng bản ghi viết cứng: ${pham.join(', ')} — dữ liệu nghiệp vụ phải đến từ API, ` +
        'ô chưa có nguồn thì dùng `EmptyBlock` để nói là chưa có',
    ).toEqual([]);
  });

  it('⛔ kiểm chứng ngược: backstop bắt được một fixture mới thêm', () => {
    const viPham = 'const BIA: Row[] = [\n  { a: 1 },\n  { a: 2 },\n  { a: 3 },\n];';
    const bat = [
      ...viPham.matchAll(/const\s+([A-Za-z_$][\w$]*)[^=]*=\s*\[([\s\S]*?)\n\];/g),
    ].filter(([, , than]) => (than.match(/\n\s*\{/g) ?? []).length >= 3);
    expect(bat).toHaveLength(1);
  });

  it('⭐ không tên miền ngoài nào ngoài danh sách cho phép', () => {
    const pham = MA.flatMap(({ ten, nguon }) =>
      [...nguon.matchAll(/https?:\/\/([\w.-]+)/g)]
        .map(([, mien]) => mien)
        .filter((mien) => !CHO_PHEP_TEN_MIEN.some((ok) => mien === ok || mien.endsWith(`.${ok}`)))
        .map((mien) => `${ten} → ${mien}`),
    );
    expect(
      pham,
      `địa chỉ ngoài chưa được duyệt: ${pham.join(', ')} — ảnh của cổng phải đi qua ` +
        '`/api/v1/public/files/<id>`, không hotlink sang máy chủ người khác',
    ).toEqual([]);
  });

  it('⛔ kiểm chứng ngược: bộ lọc bỏ chú thích không cắt nhầm địa chỉ trong chuỗi', () => {
    expect(boChuThich("const a = 'https://images.unsplash.com/x'; // ghi chú")).toContain(
      'unsplash.com',
    );
    expect(boChuThich('const a = 1; // slug: ‑bịa‑')).not.toContain('slug:');
    expect(boChuThich('/* slug: “bịa” */ const a = 1;')).not.toContain('slug:');
  });
});
