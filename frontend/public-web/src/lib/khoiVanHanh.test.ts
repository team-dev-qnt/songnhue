import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';
import {
  KHOA_HIEN_KHOI_VAN_HANH,
  TUYEN_BI_KHOA,
  khoiVanHanhBat,
  locMenuTheoCongTac,
} from './khoiVanHanh';
import type { MenuLink } from '@/lib/api';

/**
 * Công tắc khối "Điều hành & số liệu công trình" — **sáu bề mặt, một nguồn**.
 *
 * <h2>Bài này canh cái gì, và KHÔNG canh cái gì (luật 28)</h2>
 *
 * <b>Canh</b> ba thứ:
 * <ol>
 *   <li>hai hàm thuần ({@code khoiVanHanhBat}, {@code locMenuTheoCongTac}) xử lý đúng — kể cả
 *       trường hợp mục cha còn 0 con, thứ hôm nay chưa xảy ra;</li>
 *   <li><b>số</b> trang có chốt chặn bằng đúng <b>số</b> tuyến khai là bị khoá;</li>
 *   <li>bộ lọc menu nằm ở đúng MỘT chỗ, và số nơi tiêu thụ menu nhiều hơn hẳn một.</li>
 * </ol>
 *
 * <p><b>Không</b> canh: rằng trình duyệt thật trả 404, và rằng thanh điều hướng vẽ ra đúng. Kho
 * này cố ý không dựng DOM và <b>không có {@code @testing-library/react}</b> ở {@code public-web}
 * — cả 266 bài đều là hàm thuần hoặc đọc mã nguồn. Việc ấy thuộc bộ Playwright, mà bộ ấy
 * <b>chưa nằm trong CI</b> (nợ T38.10/T37.13).
 *
 * <h2>⭐ Vì sao khẳng định về SỐ LƯỢNG chứ không khớp một danh sách cứng</h2>
 *
 * Ngày 28/8 cả hai lượt kiểm chứng ngược của một phiên đều sai theo đúng cách mà thứ chúng kiểm
 * chứng đang sai — người viết cả hai mang cùng một giả định. Thứ cứu được không phải bài kiểm
 * chứng ngược mà là một khẳng định <b>về số lượng</b>: nó không chia sẻ giả định nào với mẫu khớp.
 * Nên ở đây đếm tệp, đếm nơi gọi, đếm chốt chặn — thay vì liệt kê tên.
 */

const goc = process.cwd();
const thuMucVanHanh = join(goc, 'src/app/quan-ly-van-hanh');

function doc(duongDanTuongDoi: string): string {
  return readFileSync(join(goc, duongDanTuongDoi), 'utf8');
}

/** Mọi tệp `.ts`/`.tsx` dưới `src/`, trừ chính các bài kiểm. */
function moiTepNguon(dir: string = join(goc, 'src')): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const full = join(dir, e.name);
    if (e.isDirectory()) return moiTepNguon(full);
    return /\.tsx?$/.test(e.name) && !/\.test\.tsx?$/.test(e.name) ? [full] : [];
  });
}

/** Khuôn một mục menu — chỉ đặt tên và đường dẫn, phần còn lại là giá trị trung tính. */
function muc(
  label: string,
  url: string | null,
  parentLabel: string | null = null,
  linkType: MenuLink['linkType'] = 'URL',
): MenuLink {
  return {
    label,
    linkType,
    categorySlug: null,
    articleSlug: null,
    url,
    openNewTab: false,
    depth: parentLabel === null ? 0 : 1,
    parentLabel,
    logoId: null,
  };
}

const CHA = 'Quản lý, vận hành';

/** Đúng hình dạng menu thật: một mục cha `NONE` với bốn mục con. */
function menuThat(): MenuLink[] {
  return [
    muc('Trang chủ', '/'),
    muc(CHA, null, null, 'NONE'),
    muc('Danh mục công trình', '/quan-ly-van-hanh/danh-muc-cong-trinh', CHA),
    muc('Tiến độ sản xuất', '/quan-ly-van-hanh/tien-do-san-xuat', CHA),
    muc('Mực nước, lượng mưa', '/quan-ly-van-hanh/muc-nuoc-luong-mua', CHA),
    muc('Vận hành công trình', '/quan-ly-van-hanh/van-hanh-cong-trinh', CHA),
  ];
}

describe('khoiVanHanhBat — đọc công tắc', () => {
  it("chỉ đúng chuỗi 'false' mới tắt", () => {
    expect(khoiVanHanhBat({ [KHOA_HIEN_KHOI_VAN_HANH]: 'false' })).toBe(false);
    expect(khoiVanHanhBat({ [KHOA_HIEN_KHOI_VAN_HANH]: 'true' })).toBe(true);
  });

  it('⭐ FAIL-OPEN CÓ TÊN: backend hỏng (`null`) hoặc khoá chưa có ⇒ vẫn BẬT', () => {
    // `apiGet` nuốt lỗi và trả `null`. Nếu chỗ này fail-CLOSED thì một lượt backend hắt hơi làm
    // hai trang thật trả 404 — tệ hơn hẳn việc hiện thừa một khối. Ghi thành bài kiểm để lần sau
    // ai đó "sửa cho chặt hơn" thì thấy ngay đây là lựa chọn, không phải sơ suất.
    expect(khoiVanHanhBat(null)).toBe(true);
    expect(khoiVanHanhBat(undefined)).toBe(true);
    expect(khoiVanHanhBat({})).toBe(true);
  });

  it('giá trị rác hay chuỗi rỗng cũng rơi về BẬT, không rơi về TẮT', () => {
    // "Rỗng" khác "chưa đặt" (luật 3): người quản trị xoá trắng ô thì `settings` giữ chuỗi rỗng.
    expect(khoiVanHanhBat({ [KHOA_HIEN_KHOI_VAN_HANH]: '' })).toBe(true);
    expect(khoiVanHanhBat({ [KHOA_HIEN_KHOI_VAN_HANH]: 'FALSE' })).toBe(true);
    expect(khoiVanHanhBat({ [KHOA_HIEN_KHOI_VAN_HANH]: '0' })).toBe(true);
  });
});

describe('locMenuTheoCongTac — lọc danh sách phẳng', () => {
  it('BẬT ⇒ không mục nào bị bỏ', () => {
    const truoc = menuThat();
    expect(locMenuTheoCongTac(truoc, true)).toHaveLength(truoc.length);
  });

  it('⭐ TẮT ⇒ đúng hai mục biến mất, và đúng hai mục ấy', () => {
    const sau = locMenuTheoCongTac(menuThat(), false);
    const duongDan = sau.map((m) => m.url);

    // Khẳng định về SỐ LƯỢNG trước — nó không chia sẻ giả định nào với danh sách tên bên dưới.
    expect(sau).toHaveLength(menuThat().length - TUYEN_BI_KHOA.length);
    for (const tuyen of TUYEN_BI_KHOA) {
      expect(duongDan).not.toContain(tuyen);
    }
    // Hai trang KHÔNG thuộc Nhóm 2 phải còn nguyên — đây là vế dễ hỏng nếu ai lọc theo tiền tố
    // `/quan-ly-van-hanh/` thay vì theo danh sách tuyến.
    expect(duongDan).toContain('/quan-ly-van-hanh/danh-muc-cong-trinh');
    expect(duongDan).toContain('/quan-ly-van-hanh/tien-do-san-xuat');
  });

  it('mục cha còn con thì GIỮ — nhánh thật hôm nay còn 2/4 con', () => {
    const sau = locMenuTheoCongTac(menuThat(), false);
    expect(sau.map((m) => m.label)).toContain(CHA);
  });

  it('⭐ mục cha còn 0 con thì BỎ — trường hợp hôm nay chưa xảy ra, nên phải đóng đinh', () => {
    // An toàn hiện tại là do BIÊN ĐỘ (nhánh còn 2 con), không do thiết kế. Nếu Công ty chuyển hai
    // mục kia đi nơi khác thì `PortalNav` sẽ vẽ mục cha `NONE` thành một <button> không có menu
    // con — một điều khiển bày ra mà không ai đọc được thao tác của nó (đúng lỗi WS-25).
    const menu = [
      muc('Trang chủ', '/'),
      muc(CHA, null, null, 'NONE'),
      muc('Mực nước, lượng mưa', '/quan-ly-van-hanh/muc-nuoc-luong-mua', CHA),
      muc('Vận hành công trình', '/quan-ly-van-hanh/van-hanh-cong-trinh', CHA),
    ];
    const sau = locMenuTheoCongTac(menu, false);
    expect(sau.map((m) => m.label)).toEqual(['Trang chủ']);
  });

  it('mục cấp 1 vốn KHÔNG có con thì không bị bước hai đụng tới', () => {
    const menu = [muc('Trang chủ', '/'), muc('Liên hệ', '/lien-he')];
    expect(locMenuTheoCongTac(menu, false)).toHaveLength(2);
  });

  it('⭐ dải "Liên kết website" đi qua CÙNG hàm này — tắt công tắc không được đụng tới nó', () => {
    // `getMenu('LIEN_KET')` dùng chung bộ lọc. Bộ lọc so theo đường dẫn nên liên kết ngoài không
    // khớp gì; bài này khẳng định điều đó thay vì tin vào lập luận.
    const lienKet = [
      muc('Sở NN&PTNT Hà Nội', 'https://sonnptnt.hanoi.gov.vn'),
      muc('Cổng TTĐT Chính phủ', 'https://chinhphu.vn'),
    ];
    expect(locMenuTheoCongTac(lienKet, false)).toHaveLength(2);
  });

  it('mục kiểu CATEGORY được lấy đường dẫn qua `menuHref`, không qua `url`', () => {
    // ⛔ Tự đọc `item.url` là bỏ sót mục CATEGORY (url của nó là null). Bài này bắt đúng chỗ ấy:
    // một mục CATEGORY trỏ vào slug bình thường phải sống sót, không bị nhầm thành "không có href".
    const menu = [
      { ...muc('Tin tức', null, null, 'CATEGORY'), categorySlug: 'tin-tuc' },
      muc('Vận hành công trình', '/quan-ly-van-hanh/van-hanh-cong-trinh'),
    ];
    const sau = locMenuTheoCongTac(menu, false);
    expect(sau.map((m) => m.label)).toEqual(['Tin tức']);
  });

  it('đường dẫn có dấu / cuối vẫn khớp', () => {
    const menu = [muc('Vận hành công trình', '/quan-ly-van-hanh/van-hanh-cong-trinh/')];
    expect(locMenuTheoCongTac(menu, false)).toHaveLength(0);
  });
});

describe('⭐ Bất biến cấu trúc — hai nơi phải nhớ, một phép kiểm nhớ hộ', () => {
  const trangVanHanh = readdirSync(thuMucVanHanh, { withFileTypes: true })
    .filter((e) => e.isDirectory())
    .map((e) => ({
      slug: e.name,
      duongDan: `/quan-ly-van-hanh/${e.name}`,
      nguon: boChuThich(readFileSync(join(thuMucVanHanh, e.name, 'page.tsx'), 'utf8')),
    }));

  it('⛔ TIỀN ĐỀ: thư mục đọc được và bộ cắt chú thích không cắt quá tay', () => {
    // Một bộ cắt quá tay không làm bài nào đỏ — nó biến mọi khẳng định dưới thành xanh vĩnh viễn.
    expect(trangVanHanh.length).toBeGreaterThanOrEqual(4);
    for (const t of trangVanHanh) {
      expect(t.nguon).toContain('export default async function');
    }
  });

  it('mỗi tuyến khai là bị khoá đều có một thư mục trang thật', () => {
    // Đổi tên thư mục mà quên sửa `TUYEN_BI_KHOA` ⇒ công tắc lọc một tuyến không tồn tại, và
    // trang thật thì không ai khoá. Bộ canh phải bắt được, không phải chờ người dùng bấm.
    expect(TUYEN_BI_KHOA.length).toBeGreaterThanOrEqual(2);
    for (const tuyen of TUYEN_BI_KHOA) {
      expect(trangVanHanh.map((t) => t.duongDan)).toContain(tuyen);
    }
  });

  it('⭐⭐ SỐ trang gọi `notFound()` bằng đúng SỐ tuyến bị khoá', () => {
    // Đây là bất biến chính của hạng mục. Thêm một tuyến vào `TUYEN_BI_KHOA` mà quên đặt chốt
    // chặn ở trang tương ứng ⇒ hai con số lệch ⇒ đỏ. Và ngược lại: đặt chốt chặn ở một trang
    // không nằm trong danh sách cũng đỏ, vì đó là một trang bị khoá mà menu vẫn quảng cáo.
    const coChotChan = trangVanHanh.filter((t) => t.nguon.includes('notFound()'));
    expect(coChotChan).toHaveLength(TUYEN_BI_KHOA.length);
    expect(coChotChan.map((t) => t.duongDan).sort()).toEqual([...TUYEN_BI_KHOA].sort());
  });

  it('mỗi chốt chặn phải đọc công tắc, không tự viết lại chuỗi khoá', () => {
    for (const t of trangVanHanh.filter((x) => x.nguon.includes('notFound()'))) {
      expect(t.nguon).toContain('khoiVanHanhBat(config)');
      expect(t.nguon).not.toContain(KHOA_HIEN_KHOI_VAN_HANH);
    }
  });
});

describe('⭐ Bộ lọc menu nằm ở đúng MỘT chỗ — chỗ dữ liệu đi qua', () => {
  const nguonTheoTep = moiTepNguon().map((f) => ({
    ten: f.slice(goc.length + 1),
    ma: boChuThich(readFileSync(f, 'utf8')),
  }));

  it('có ÍT NHẤT bốn tệp tiêu thụ menu — nên đếm tay các nơi gọi là việc sẽ sai', () => {
    // T27.7 đã trả nợ xoá đệm cổng ở ba điểm ghi, rồi điểm ghi THỨ TƯ ra đời cùng đợt mang lại
    // đúng lỗi cũ. Con số dưới đây là lý lẽ cho việc đặt bộ lọc trong `getMenu()`; nó dùng
    // `toBeGreaterThanOrEqual` để một nơi gọi mới KHÔNG làm bài này đỏ vô cớ.
    const tieuThu = nguonTheoTep.filter(
      (t) => t.ma.includes('getMenu(') && !t.ten.endsWith('lib/api.ts'),
    );
    expect(tieuThu.length).toBeGreaterThanOrEqual(4);
  });

  it('⛔ chỉ `lib/api.ts` được gọi `locMenuTheoCongTac` — không nơi nào tự lọc lại', () => {
    // Hai chỗ lọc là hai luật sẽ lệch nhau theo thời gian; và một nơi gọi tự lọc là bằng chứng
    // rằng ai đó tưởng `getMenu()` chưa lọc — tức chú thích ở đó đang nói dối.
    //
    // ⚠ `khoiVanHanh.ts` bị trừ ra vì nó là nơi ĐỊNH NGHĨA hàm, không phải nơi gọi. Đây là một
    //   ngoại lệ hẹp và có tên — ⛔ đừng nới nó thành một danh sách bỏ qua.
    const noiGoi = nguonTheoTep
      .filter((t) => t.ten !== 'src/lib/khoiVanHanh.ts')
      .filter((t) => t.ma.includes('locMenuTheoCongTac('))
      .map((t) => t.ten);
    expect(noiGoi).toEqual(['src/lib/api.ts']);
  });

  it('`getMenu` thật sự áp bộ lọc — không chỉ import nó', () => {
    const api = boChuThich(doc('src/lib/api.ts'));
    const than = api.slice(api.indexOf('export async function getMenu'));
    expect(than).toContain('locMenuTheoCongTac(');
    expect(than).toContain('khoiVanHanhBat(');
  });

  it('sidebar viết cứng liên kết Mực nước ⇒ phải nhận cờ từ nơi gọi', () => {
    // Bề mặt này KHÔNG đi qua `getMenu()`, nên bộ lọc trung tâm không với tới. Ba nơi gọi đều
    // phải truyền cờ; thiếu một nơi là một trang vẫn quảng cáo lối vào đã bị khoá.
    const sidebar = boChuThich(doc('src/components/PortalSidebar.tsx'));
    expect(sidebar).toContain('hienKhoiVanHanh');

    const noiGoiSidebar = nguonTheoTep.filter((t) => t.ma.includes('<PortalSidebar'));
    expect(noiGoiSidebar.length).toBeGreaterThanOrEqual(3);
    for (const t of noiGoiSidebar) {
      expect(t.ma).toContain('hienKhoiVanHanh={khoiVanHanhBat(config)}');
    }
  });
});
