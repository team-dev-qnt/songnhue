import type { MetadataRoute } from 'next';

import { NAV_ITEMS, SITE_URL } from '@/lib/site';

/**
 * `sitemap.xml` (T9.3).
 *
 * Phase 0 chỉ liệt kê các trang tĩnh đã có. **Phase 1 nối thêm bài viết và văn bản** bằng
 * cách gọi API danh sách rồi `concat` vào mảng dưới đây — hàm là `async` sẵn cho việc đó.
 *
 * ⚠ Sitemap **không** được liệt kê trang chưa xuất bản. Bài viết ở trạng thái chờ duyệt
 * mà lọt vào đây là công bố nội dung trước khi nó được duyệt — một lỗi quy trình, không
 * phải lỗi kỹ thuật, nên khi Phase 1 nối API vào thì phải lọc theo trạng thái đã xuất bản.
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date();

  return NAV_ITEMS.map((item) => ({
    url: `${SITE_URL}${item.href === '/' ? '' : item.href}`,
    lastModified: now,
    changeFrequency: item.href === '/' ? ('daily' as const) : ('weekly' as const),
    priority: item.href === '/' ? 1 : 0.7,
  }));
}
