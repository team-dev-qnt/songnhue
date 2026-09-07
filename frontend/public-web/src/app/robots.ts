import type { MetadataRoute } from 'next';

import { SITE_URL } from '@/lib/site';

/**
 * `robots.txt` sinh động theo `NEXT_PUBLIC_SITE_URL` (T9.3).
 *
 * ⚠ Chặn `/api/` là bắt buộc: dưới đó có `/api/revalidate` (nhận webhook có bí mật) và
 * `/api/health`. Không có gì để lập chỉ mục, mà lại là đường dẫn không nên quảng bá.
 *
 * ⚠ Staging và production **dùng chung mã nguồn này**. Nếu staging cũng cho lập chỉ mục
 * thì Google sẽ có hai bản của cùng nội dung và có thể xếp bản staging lên trước — nên
 * chặn theo `NEXT_PUBLIC_SITE_URL`, chứ không hard-code.
 */
export default function robots(): MetadataRoute.Robots {
  const isProduction = !/localhost|staging|127\.0\.0\.1/i.test(SITE_URL);

  if (!isProduction) {
    return {
      rules: [{ userAgent: '*', disallow: '/' }],
    };
  }

  return {
    rules: [
      {
        userAgent: '*',
        allow: '/',
        disallow: ['/api/'],
      },
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
