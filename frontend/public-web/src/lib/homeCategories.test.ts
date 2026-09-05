import { describe, expect, it } from 'vitest';

import type { CategoryNode, MenuLink } from '@/lib/api';
import { chonKhoiChuyenMuc, laNhanhCua, nhanNhanhTin } from '@/lib/homeCategories';
import { buildMenuTree } from '@/lib/routes';

function muc(partial: Partial<MenuLink>): MenuLink {
  return {
    label: 'Mục',
    linkType: 'NONE',
    categorySlug: null,
    articleSlug: null,
    url: null,
    openNewTab: false,
    depth: 0,
    parentLabel: null,
    logoId: null,
    ...partial,
  };
}

/** Cây menu HEADER đúng như CSDL đang chạy — đo 29/08/2026 bằng `psql` trên stack local. */
function cayThat() {
  return buildMenuTree([
    muc({ label: 'Trang chủ', linkType: 'URL', url: '/' }),
    muc({ label: 'Giới thiệu' }),
    muc({
      label: 'Cơ cấu tổ chức',
      depth: 1,
      parentLabel: 'Giới thiệu',
      linkType: 'URL',
      url: '/gioi-thieu/co-cau-to-chuc',
    }),
    muc({ label: 'Tin tức – Sự kiện', linkType: 'CATEGORY', categorySlug: 'tin-tuc' }),
    muc({
      label: 'Hoạt động Đảng, đoàn thể',
      depth: 1,
      parentLabel: 'Tin tức – Sự kiện',
      linkType: 'CATEGORY',
      categorySlug: 'hoat-dong-dang-doan-the',
    }),
    muc({
      label: 'Tin thủy lợi',
      depth: 1,
      parentLabel: 'Tin tức – Sự kiện',
      linkType: 'CATEGORY',
      categorySlug: 'tin-thuy-loi',
    }),
    muc({
      label: 'Tin Công ty',
      depth: 1,
      parentLabel: 'Tin tức – Sự kiện',
      linkType: 'CATEGORY',
      categorySlug: 'tin-cong-ty',
    }),
    muc({ label: 'Công bố thông tin', linkType: 'CATEGORY', categorySlug: 'cong-bo-thong-tin' }),
    muc({
      label: 'Văn bản pháp luật',
      depth: 1,
      parentLabel: 'Công bố thông tin',
      linkType: 'CATEGORY',
      categorySlug: 'van-ban-phap-luat',
    }),
  ]);
}

describe('chonKhoiChuyenMuc — hàng chuyên mục dưới slider', () => {
  it('lấy đúng các mục con CATEGORY của nhánh cấu hình, giữ thứ tự menu', () => {
    const khoi = chonKhoiChuyenMuc(cayThat(), 'tin-tuc');

    expect(khoi.map((k) => k.slug)).toEqual([
      'hoat-dong-dang-doan-the',
      'tin-thuy-loi',
      'tin-cong-ty',
    ]);
    expect(khoi.map((k) => k.label)).toEqual([
      'Hoạt động Đảng, đoàn thể',
      'Tin thủy lợi',
      'Tin Công ty',
    ]);
  });

  it('⭐ đổi khoá cấu hình sang nhánh khác thì lấy con của NHÁNH ẤY — không phải nhánh tin', () => {
    // Khẳng định này phân biệt được hai trạng thái: một cài đặt trả về "con của nhánh đầu tiên
    // tìm thấy" cũng qua được bài trên, nhưng đỏ ở đây (luật 9).
    expect(chonKhoiChuyenMuc(cayThat(), 'cong-bo-thong-tin').map((k) => k.slug)).toEqual([
      'van-ban-phap-luat',
    ]);
  });

  it('⛔ nhánh không có mục menu nào ⇒ mảng rỗng, KHÔNG rơi về danh sách mặc định', () => {
    expect(chonKhoiChuyenMuc(cayThat(), 'khong-ton-tai')).toEqual([]);
    expect(chonKhoiChuyenMuc(cayThat(), '')).toEqual([]);
    expect(chonKhoiChuyenMuc([], 'tin-tuc')).toEqual([]);
  });

  it('⛔ bỏ mục con không phải chuyên mục, và mục CATEGORY có slug rỗng', () => {
    const cay = buildMenuTree([
      muc({ label: 'Tin', linkType: 'CATEGORY', categorySlug: 'tin-tuc' }),
      muc({ label: 'Trang tĩnh', depth: 1, parentLabel: 'Tin', linkType: 'URL', url: '/x' }),
      muc({ label: 'Bài', depth: 1, parentLabel: 'Tin', linkType: 'ARTICLE', articleSlug: 'a' }),
      muc({ label: 'Chỉ mở menu', depth: 1, parentLabel: 'Tin', linkType: 'NONE' }),
      // Dữ liệu hỏng: CATEGORY mà không có slug. Lọt qua ⇒ gọi API `category=` rỗng ⇒ trả về
      // TOÀN BỘ bài của cổng dưới một cái tên chuyên mục.
      muc({
        label: 'Hỏng',
        depth: 1,
        parentLabel: 'Tin',
        linkType: 'CATEGORY',
        categorySlug: null,
      }),
      muc({
        label: 'Thật',
        depth: 1,
        parentLabel: 'Tin',
        linkType: 'CATEGORY',
        categorySlug: 'that',
      }),
    ]);

    expect(chonKhoiChuyenMuc(cay, 'tin-tuc')).toEqual([{ label: 'Thật', slug: 'that' }]);
  });
});

describe('nhanNhanhTin — tiêu đề cột tin cạnh slider', () => {
  it('trả nhãn của chính mục menu, không phải tên bản ghi danh mục', () => {
    expect(nhanNhanhTin(cayThat(), 'tin-tuc')).toBe('Tin tức – Sự kiện');
  });

  it('⛔ không có mục menu ⇒ null, nơi gọi bỏ khối thay vì viết cứng một tiêu đề', () => {
    expect(nhanNhanhTin(cayThat(), 'khong-ton-tai')).toBeNull();
    expect(nhanNhanhTin(cayThat(), '')).toBeNull();
  });
});

/**
 * `laNhanhCua` — trang `/danh-muc/[slug]` dùng nó để chọn giữa BẢNG văn bản và danh sách tin.
 *
 * ⚠ Bài quan trọng nhất ở đây không phải "trả đúng true/false" mà là **cây có vòng phải dừng**:
 * `parentSlug` là dữ liệu, và một chu trình A→B→A biến một `while` leo-tới-null thành một lượt
 * dựng trang không bao giờ trả lời. Trên máy chủ Next đó là một tab quay mãi, không phải một lỗi
 * có thông báo — nên nó phải được kiểm, chứ không phải được tin là "sẽ không xảy ra".
 */
describe('laNhanhCua', () => {
  const cay: CategoryNode[] = [
    { slug: 'cong-bo-thong-tin', name: 'Công bố thông tin', description: null, parentSlug: null },
    {
      slug: 'van-ban-phap-luat',
      name: 'Văn bản pháp luật',
      description: null,
      parentSlug: 'cong-bo-thong-tin',
    },
    {
      slug: 'quyet-dinh-2026',
      name: 'Quyết định 2026',
      description: null,
      parentSlug: 'van-ban-phap-luat',
    },
    { slug: 'tin-tuc', name: 'Tin tức', description: null, parentSlug: null },
  ] as CategoryNode[];

  it('chính nó ⇒ true', () => {
    expect(laNhanhCua(cay, 'cong-bo-thong-tin', 'cong-bo-thong-tin')).toBe(true);
  });

  it('con trực tiếp ⇒ true', () => {
    expect(laNhanhCua(cay, 'van-ban-phap-luat', 'cong-bo-thong-tin')).toBe(true);
  });

  it('⭐ CHÁU cũng phải true — hàng nhánh con của trang chủ dẫn tới cấp 3 (CR-31/CR-32)', () => {
    expect(laNhanhCua(cay, 'quyet-dinh-2026', 'cong-bo-thong-tin')).toBe(true);
  });

  it('nhánh khác ⇒ false', () => {
    expect(laNhanhCua(cay, 'tin-tuc', 'cong-bo-thong-tin')).toBe(false);
  });

  it('slug không có trong cây ⇒ false, không nổ', () => {
    expect(laNhanhCua(cay, 'khong-ton-tai', 'cong-bo-thong-tin')).toBe(false);
  });

  it('⛔ gốc RỖNG ⇒ false — chuỗi rỗng KHÔNG được khớp mọi thứ', () => {
    // Một khoá `settings` chưa đặt mà khớp tất cả sẽ biến MỌI trang danh mục thành bảng văn bản.
    expect(laNhanhCua(cay, 'tin-tuc', '')).toBe(false);
    expect(laNhanhCua(cay, '', 'cong-bo-thong-tin')).toBe(false);
  });

  it('⭐⭐ cây có VÒNG thì dừng, không treo tiến trình dựng trang', () => {
    const cayVong: CategoryNode[] = [
      { slug: 'a', name: 'A', description: null, parentSlug: 'b' },
      { slug: 'b', name: 'B', description: null, parentSlug: 'a' },
    ] as CategoryNode[];
    // Nếu hàm treo thì bài kiểm này không "đỏ" — nó chạy mãi và cả bộ test hết giờ. Đó chính là
    // lý do phải chặn số bước leo bằng độ dài mảng thay vì tin vào `null` ở đâu đó.
    expect(laNhanhCua(cayVong, 'a', 'cong-bo-thong-tin')).toBe(false);
  });
});
