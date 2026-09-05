import type { MetadataRoute } from 'next';

import { getArticles, getCategories } from '@/lib/api';
import { ROUTES } from '@/lib/routes';
import { SITE_URL } from '@/lib/site';

/**
 * `sitemap.xml` — đọc từ CSDL (T16.4).
 *
 * <h3>⛔ Chỉ liệt kê thứ đã xuất bản</h3>
 *
 * Điều đó được bảo đảm ở tầng dưới, không phải ở đây: `GET /public/articles` đã lọc theo
 * `status = 'XUAT_BAN'` và `published_at <= now()`. Sitemap chỉ việc dùng đúng nguồn ấy —
 * lấy từ một truy vấn khác là mở đường cho bài chưa duyệt lọt vào một tệp mà Google đọc.
 *
 * <h3>Vì sao chỉ lấy 500 bài</h3>
 *
 * Chuẩn sitemap cho tối đa 50.000 URL mỗi tệp, nhưng cổng này sẽ có vài trăm bài trong nhiều
 * năm. Đặt trần để một lỗi phân trang không biến sitemap thành một lượt tải cả CSDL; khi
 * chạm trần thật thì việc phải làm là tách sitemap theo chỉ mục, không phải nâng số.
 */
const TRAN_BAI = 500;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date();

  const [articles, categories] = await Promise.all([
    getArticles({ size: 50, page: 0 }),
    getCategories(),
  ]);

  const entries: MetadataRoute.Sitemap = [
    { url: SITE_URL, lastModified: now, changeFrequency: 'daily', priority: 1 },
  ];

  for (const category of categories ?? []) {
    entries.push({
      url: `${SITE_URL}${ROUTES.category(category.slug)}`,
      lastModified: now,
      changeFrequency: 'weekly',
      priority: 0.7,
    });
  }

  // Lấy hết các trang bài viết, dừng ở trần.
  let page = articles;
  let index = 0;
  while (page && entries.length < TRAN_BAI) {
    for (const article of page.content) {
      entries.push({
        url: `${SITE_URL}${ROUTES.article(article.slug)}`,
        lastModified: article.publishedAt ? new Date(article.publishedAt) : now,
        changeFrequency: 'monthly',
        priority: 0.6,
      });
    }
    index += 1;
    page = index < page.totalPages ? await getArticles({ size: 50, page: index }) : null;
  }

  return entries;
}
