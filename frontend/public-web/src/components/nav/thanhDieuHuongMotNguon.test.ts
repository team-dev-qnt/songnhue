import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * **Thanh điều hướng: một nguồn sự thật, và ô tìm kiếm không được biến mất ở điện thoại.**
 *
 * <h2>Vì sao bài này tồn tại bên cạnh {@code lib/menuCap1.test.ts}</h2>
 *
 * Bài kia kiểm <b>máy trạng thái</b>. Nó xanh trọn vẹn ngay cả khi {@code PortalNav.tsx} không
 * gọi máy trạng thái ấy một lần nào — đúng hình dạng đã trả giá ở §10.62: <i>{@code SvgSanitizer}
 * có 9 bài kiểm mà không nằm trên đường chạy nào</i>. Bài này canh phần còn lại: <b>mã thật có
 * đi qua đó không</b>, và <b>hai lớp CSS đã gây ra sự cố có quay lại không</b>.
 *
 * <h2>⚠ Giới hạn — nói ra thay vì để người đọc tự suy (luật 28)</h2>
 *
 * Bài này <b>đọc văn bản mã nguồn</b>. Kho {@code public-web} cố ý không dựng DOM
 * ({@code vitest.config.mts}: <i>"dựng một tầng mock nửa vời chỉ tạo ra thứ xanh mà không chứng
 * minh gì"</i>), nên không bài nào ở đây bấm được một menu thật. Nó bắt được đúng <b>hai hình
 * dạng đã gây ra sự cố 01/09</b> — không bắt được một cách vỡ kiểu khác.
 */
const THU_MUC = join(process.cwd(), 'src', 'components');
const NGUON_NAV = join(THU_MUC, 'nav', 'PortalNav.tsx');
const NGUON_DAU_TRANG = join(THU_MUC, 'SiteHeader.tsx');

const maNav = readFileSync(NGUON_NAV, 'utf8');
const maDauTrang = readFileSync(NGUON_DAU_TRANG, 'utf8');

/** Mọi chuỗi `className="…"` — chỉ lớp CSS thật, KHÔNG lấy chú thích nhắc tới tên lớp. */
function docClassNameTinh(ma: string): string[] {
  return [...ma.matchAll(/className="([^"]+)"/g)].map((m) => m[1]);
}

/** Mọi biểu thức `className={`…`}` (template literal) — nơi lớp động được ghép. */
function docClassNameDong(ma: string): string[] {
  return [...ma.matchAll(/className=\{`([^`]+)`\}/g)].map((m) => m[1]);
}

function moiLopCss(ma: string): string[] {
  return [...docClassNameTinh(ma), ...docClassNameDong(ma)];
}

/**
 * Lớp này có **chặn phần tử co lại** không?
 *
 * ⛔ Không phải "có phải bề rộng cố định không". `max-w-*` chặn phần tử *nở ra*, không chặn nó
 * co; `min-w-0` là lớp <b>cho phép</b> co và là thứ bắt buộc phải có ở đây. Chỉ `w-<số>` và
 * `min-w-<khác 0>` mới là thứ giữ ô nhập không nhỏ lại được — đúng nguyên nhân sự cố 31/08.
 */
function chanCoLai(lop: string): boolean {
  if (lop === 'min-w-0') return false;
  return /^(min-)?w-(\[[^\]]+\]|\d+(\.\d+)?)$/.test(lop);
}

/** Vị từ tách riêng để bản hỏng dùng lại được — nhận TẬP LỚP, trả `true` nếu sạch. */
function khongConNguonHienThiCss(cacLop: string[]): boolean {
  return !cacLop.some((lop) => /group-(hover|focus-within):(visible|opacity-100)/.test(lop));
}

describe('Menu con cấp 1 — CSS thôi làm nguồn hiển thị thứ hai', () => {
  const cacLop = moiLopCss(maNav);

  it('đọc được đúng đối tượng cần soi — chống xanh trên tập rỗng (luật 7)', () => {
    // Không có dòng này thì một regex hỏng làm `cacLop` rỗng, và mọi khẳng định dưới đây xanh
    // trọn vẹn trong khi chưa soi một lớp CSS nào.
    expect(cacLop.length).toBeGreaterThanOrEqual(15);
    expect(cacLop.some((l) => l.includes('absolute left-0 top-full'))).toBe(true);
  });

  it('không lớp CSS nào của thanh điều hướng còn tự bật menu con', () => {
    const viPham = cacLop.filter((l) => /group-(hover|focus-within):(visible|opacity-100)/.test(l));
    expect(viPham, `còn nguồn hiển thị CSS ở: ${viPham.join(' | ')}`).toHaveLength(0);
  });

  it('kiểm chứng ngược: vị từ phải BẮT ĐƯỢC đúng chuỗi lớp đã gây ra sự cố', () => {
    // Chép nguyên văn từ bản trước bản vá (`git show` của commit 9832f65).
    const BAN_HONG =
      'absolute left-0 top-full z-50 min-w-64 divide-y divide-surface-border/40 rounded-lg ' +
      'border border-surface-border bg-white py-1.5 shadow-lg transition-all duration-200 ' +
      'ease-smooth before:absolute before:-top-2 before:left-0 before:right-0 before:h-2 ' +
      'group-hover:visible group-hover:translate-y-0 group-hover:opacity-100 ' +
      'group-focus-within:visible group-focus-within:translate-y-0 group-focus-within:opacity-100';
    expect(khongConNguonHienThiCss([BAN_HONG])).toBe(false);
    // …và phải KHÔNG bắt nhầm một lớp `group-hover` vô hại (logo phóng to khi rê chuột).
    expect(khongConNguonHienThiCss(['group-hover:scale-105'])).toBe(true);
  });

  it('mọi lượt ghi trạng thái mở đều đi qua máy trạng thái, hoặc là lượt đóng tường minh', () => {
    // ⭐ Đây là khẳng định giữ cho `menuCap1.test.ts` còn ý nghĩa. Một `datMoCap1('X')` viết
    //    thẳng ở đâu đó là một đường quyết định thứ hai — đúng lớp lỗi mà cả đợt vá này đóng.
    const luotGhi = [...maNav.matchAll(/datMoCap1\(([^\n]*)/g)].map((m) => m[1]);
    expect(luotGhi.length).toBeGreaterThanOrEqual(4);

    const laHopLe = (bieuThuc: string) =>
      bieuThuc.startsWith('null)') || bieuThuc.includes('menuCap1KeTiep');
    const viPham = luotGhi.filter((b) => !laHopLe(b));
    expect(viPham, `lượt ghi không qua máy trạng thái: ${viPham.join(' | ')}`).toHaveLength(0);

    // Và máy trạng thái phải thật sự được nhập vào tệp — không chỉ nhắc tên trong chú thích.
    expect(maNav).toContain("from '@/lib/menuCap1'");
  });
});

describe('Ô tìm kiếm — đúng MỘT lối vào, và nó không được mất ở điện thoại', () => {
  it('dải nhận diện KHÔNG còn biểu mẫu tìm kiếm nào', () => {
    // Hai ô tìm kiếm trên cùng một trang là hai nơi phải nhớ sửa (luật 14). Từ 01/09 lối vào
    // duy nhất nằm ở thanh điều hướng.
    expect(maDauTrang).not.toContain('<form');
  });

  it('thanh điều hướng có đúng một biểu mẫu tìm kiếm, trỏ vào tuyến tìm kiếm thật', () => {
    const soForm = [...maNav.matchAll(/<form\b/g)].length;
    expect(soForm).toBe(1);
    expect(maNav).toContain('action={ROUTES.search}');
    expect(maNav).toContain('name="q"');
  });

  it('⭐ nút tìm kiếm nằm NGOÀI thanh ngang — mất nó ở ngăn kéo là mất hẳn chức năng', () => {
    // `lopThanhNgang` là biến quyết định thanh ngang hiện hay ẩn khi rơi về ngăn kéo. Nút tìm
    // kiếm mang lớp ấy thì ở điện thoại cổng KHÔNG CÒN đường tìm kiếm nào — và không cổng kiểm
    // nào đỏ, vì trang vẫn dựng bình thường.
    const khoiNut = maNav.slice(maNav.indexOf('ref={timKiemRef}'));
    const ketNut = khoiNut.indexOf('</button>');
    expect(ketNut).toBeGreaterThan(0);
    expect(khoiNut.slice(0, ketNut)).not.toContain('lopThanhNgang');
  });

  it('ô nhập tìm kiếm không có bề rộng cố định — nó phải co được xuống 320px', () => {
    const lopONhap = moiLopCss(maNav).filter((l) => l.includes('flex-1') && l.includes('min-w-0'));
    expect(lopONhap.length).toBeGreaterThanOrEqual(1);
    for (const lop of lopONhap) {
      const viPham = lop.split(/\s+/).filter(chanCoLai);
      expect(viPham, `ô nhập có bề rộng chặn co: ${viPham.join(' ')}`).toHaveLength(0);
    }
  });

  it('kiểm chứng ngược: `chanCoLai` phân biệt được lớp CHẶN co và lớp CHO PHÉP co', () => {
    // ⚠ Lượt viết đầu của vị từ này bắt nhầm `min-w-0` — lớp *cho phép* co — là "bề rộng cố
    //    định", vì `\d+` khớp cả số 0. Bài kiểm đỏ ngay và đó là lý do có mục này: một vị từ
    //    canh hình dạng phải được thử với CẢ hai phía, không chỉ phía nó định bắt (luật 25).
    expect(['w-60', 'w-[288px]', 'min-w-64', 'min-w-[12rem]'].every(chanCoLai)).toBe(true);
    expect(['min-w-0', 'w-full', 'w-auto', 'flex-1', 'max-w-[1232px]'].some(chanCoLai)).toBe(false);
  });
});
