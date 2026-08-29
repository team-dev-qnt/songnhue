import { describe, expect, it } from 'vitest';

import type { MenuLink } from '@/lib/api';
import { chonKhoiChuyenMuc, nhanNhanhTin } from '@/lib/homeCategories';
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
