import Link from 'next/link';

import type { ArticleRow } from '@/lib/api';
import { fileUrl, formatDate, ROUTES } from '@/lib/routes';

/**
 * Thẻ một bài viết trong danh sách.
 *
 * ⚠ Ảnh dùng thẻ `<img>` thường, **không** phải `next/image` — quyết định ở
 * `architecture-review.md` §10.9: bộ tối ưu của Next đòi `sharp`, và ảnh của hệ này còn
 * phải hiển thị được ở `admin-app` (Vite) nên không đi qua Next. Bù lại bằng `loading="lazy"`
 * và khung kích thước cố định: thiếu hai thứ đó thì mở một trang 20 bài là tải về vài chục
 * MB ảnh gốc, và bố cục nhảy khi ảnh về (T12.7 hoãn → nợ #62).
 */
export function ArticleCard({ article }: { article: ArticleRow }) {
  const cover = fileUrl(article.coverAttachmentPublicId);

  return (
    <article className="group flex flex-col overflow-hidden rounded-lg border border-surface-border bg-white shadow-sm transition-all duration-300 ease-smooth hover:-translate-y-1 hover:shadow-md hover:border-brand-primary">
      <Link href={ROUTES.article(article.slug)} className="flex flex-1 flex-col">
        <div className="aspect-[16/9] w-full overflow-hidden bg-surface-bgLayout">
          {cover ? (
            <img
              src={cover}
              alt=""
              loading="lazy"
              decoding="async"
              className="h-full w-full object-cover transition-transform duration-500 ease-smooth group-hover:scale-105"
            />
          ) : null}
        </div>
        <div className="flex flex-1 flex-col p-4">
          <h3 className="line-clamp-2 font-semibold text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary">
            {article.title}
          </h3>
          {article.summary ? (
            <p className="mt-2 line-clamp-3 text-sm text-surface-textSecondary">
              {article.summary}
            </p>
          ) : null}
          <time
            dateTime={article.publishedAt ?? undefined}
            className="mt-auto pt-3 text-xs text-surface-textSecondary"
          >
            {formatDate(article.publishedAt)}
          </time>
        </div>
      </Link>
    </article>
  );
}
