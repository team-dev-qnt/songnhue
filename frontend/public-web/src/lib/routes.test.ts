import { describe, expect, it } from 'vitest';

import type { MenuLink } from '@/lib/api';
import {
  buildMenuTree,
  constructionDocUrl,
  fileUrl,
  formatDate,
  isExternal,
  menuHref,
  ROUTES,
} from '@/lib/routes';

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

describe('menuHref — ghép đường dẫn từ loại liên kết', () => {
  it('danh mục và bài viết dùng tiền tố riêng, không đặt ở gốc', () => {
    expect(menuHref(muc({ linkType: 'CATEGORY', categorySlug: 'tin-tuc' }))).toBe(
      '/danh-muc/tin-tuc',
    );
    expect(menuHref(muc({ linkType: 'ARTICLE', articleSlug: 'lien-he' }))).toBe(
      '/bai-viet/lien-he',
    );
  });

  it('mục NONE không dẫn đi đâu — nơi gọi render thành nhãn, không phải liên kết trỏ về #', () => {
    expect(menuHref(muc({ linkType: 'NONE' }))).toBeNull();
  });

  it('URL và hệ thống văn bản trả nguyên đường dẫn đã nhập', () => {
    expect(menuHref(muc({ linkType: 'URL', url: '/' }))).toBe('/');
    expect(menuHref(muc({ linkType: 'EXTERNAL_DOC', url: 'http://songnhue.bhh40.net' }))).toBe(
      'http://songnhue.bhh40.net',
    );
  });

  it('⛔ đích thiếu dữ liệu trả null thay vì ghép ra "/danh-muc/null"', () => {
    expect(menuHref(muc({ linkType: 'CATEGORY', categorySlug: null }))).toBeNull();
    expect(menuHref(muc({ linkType: 'ARTICLE', articleSlug: null }))).toBeNull();
  });
});

describe('isExternal — quyết định rel="noopener"', () => {
  it('chỉ đường dẫn tuyệt đối mới là ra ngoài', () => {
    expect(isExternal(muc({ linkType: 'EXTERNAL_DOC', url: 'http://songnhue.bhh40.net' }))).toBe(
      true,
    );
    expect(isExternal(muc({ linkType: 'CATEGORY', categorySlug: 'tin-tuc' }))).toBe(false);
    expect(isExternal(muc({ linkType: 'URL', url: '/gioi-thieu' }))).toBe(false);
  });
});

describe('buildMenuTree — dựng cây hai cấp từ danh sách phẳng', () => {
  it('gắn mục con vào đúng cha, giữ nguyên thứ tự backend đã sắp', () => {
    const tree = buildMenuTree([
      muc({ label: 'Trang chủ', linkType: 'URL', url: '/' }),
      muc({ label: 'Giới thiệu' }),
      muc({ label: 'Giới thiệu chung', depth: 1, parentLabel: 'Giới thiệu' }),
      muc({ label: 'Cơ cấu tổ chức', depth: 1, parentLabel: 'Giới thiệu' }),
      muc({ label: 'Tin tức', linkType: 'CATEGORY', categorySlug: 'tin-tuc' }),
    ]);

    expect(tree.map((t) => t.item.label)).toEqual(['Trang chủ', 'Giới thiệu', 'Tin tức']);
    expect(tree[1].children.map((c) => c.label)).toEqual(['Giới thiệu chung', 'Cơ cấu tổ chức']);
  });

  it('⭐ mục con mất cha được nâng lên cấp gốc, KHÔNG bị bỏ đi', () => {
    // Xảy ra thật khi quản trị viên tắt mục cha mà quên các mục con: backend lọc `active`
    // nên cha biến mất khỏi danh sách. Mất một mục menu là thứ không ai phát hiện; thừa
    // một mục thì thấy ngay và sửa được.
    const tree = buildMenuTree([muc({ label: 'Mồ côi', depth: 1, parentLabel: 'Cha đã tắt' })]);

    expect(tree).toHaveLength(1);
    expect(tree[0].item.label).toBe('Mồ côi');
  });
});

describe('fileUrl — địa chỉ tệp công khai', () => {
  it('⛔ KHÔNG phải presigned URL — địa chỉ phải ổn định vì trang tĩnh sống lâu hơn 10 phút', () => {
    const url = fileUrl('11111111-2222-3333-4444-555555555555');

    expect(url).toContain('/public/files/11111111-2222-3333-4444-555555555555');
    expect(url).not.toContain('X-Amz-Signature');
    expect(url).not.toContain('?');
  });

  it('không có tệp thì trả null, để nơi gọi khỏi render <img src="null">', () => {
    expect(fileUrl(null)).toBeNull();
    expect(fileUrl(undefined)).toBeNull();
  });
});

describe('constructionDocUrl — tệp tài liệu công trình đi đường RIÊNG (404 câm, 31/08)', () => {
  it('rỗng → null, không dựng liên kết chết', () => {
    expect(constructionDocUrl(null)).toBeNull();
    expect(constructionDocUrl(undefined)).toBeNull();
    expect(constructionDocUrl('')).toBeNull();
  });

  it('KHÁC đường tệp của cổng — đây chính là cả nội dung của bản vá', () => {
    const id = '7c9e6679-7425-40de-944b-e07fc1f90ae7';
    // ⛔ `/public/files/{id}` chỉ phục vụ MEDIA_FOLDER · BANNER · SITE_CONFIG · MENU_ITEM;
    //    CONSTRUCTION cố ý nằm ngoài (backend có bài kiểm đóng đinh). Hai hàm trả về CÙNG một
    //    chuỗi nghĩa là bản vá đã bị hoàn tác.
    expect(constructionDocUrl(id)).not.toBe(fileUrl(id));
    expect(constructionDocUrl(id)).toBe(`/api/v1/public/constructions/documents/${id}`);
  });
});

describe('formatDate — hiển thị UTC+7 (quy tắc 1 của dự án)', () => {
  it('mốc UTC 17:30 ngày 19/8 là ngày 20/8 theo giờ Việt Nam', () => {
    // Lưu UTC, hiển thị UTC+7. Không đổi múi giờ thì bài đăng buổi tối hiện sai ngày.
    expect(formatDate('2026-08-19T17:30:00Z')).toBe('20/08/2026');
  });

  it('không có mốc thì trả chuỗi rỗng, không phải "Invalid Date"', () => {
    expect(formatDate(null)).toBe('');
    expect(formatDate(undefined)).toBe('');
  });
});

describe('ROUTES', () => {
  it('trang tìm kiếm có đường dẫn riêng — bài viết không tranh chỗ với nó', () => {
    expect(ROUTES.search).toBe('/tim-kiem');
    expect(ROUTES.article('tim-kiem')).toBe('/bai-viet/tim-kiem');
  });
});
