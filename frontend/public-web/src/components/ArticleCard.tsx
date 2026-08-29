import Link from 'next/link';

import { PortalImage } from '@/components/PortalImage';
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
    <article className="group flex flex-col overflow-hidden rounded-xl border border-surface-border bg-white shadow-xs transition-all duration-300 ease-smooth hover:-translate-y-1 hover:border-brand-primary hover:shadow-md">
      <Link href={ROUTES.article(article.slug)} className="flex flex-1 flex-col">
        <PortalImage src={cover} alt={article.title} ratio="aspect-[16/10]" />
        <div className="flex flex-1 flex-col p-4 sm:p-5">
          <h3 className="line-clamp-2 text-sm font-bold leading-snug text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary sm:text-base">
            {article.title}
          </h3>
          {article.summary ? (
            <p className="mt-2 line-clamp-2 text-xs text-surface-textSecondary leading-relaxed sm:text-sm">
              {article.summary}
            </p>
          ) : null}
          <div className="mt-auto pt-3 flex items-center justify-between border-t border-surface-border/60 text-[11px] text-surface-textSecondary">
            <time dateTime={article.publishedAt ?? undefined}>
              {formatDate(article.publishedAt)}
            </time>
            {article.viewCount !== undefined && article.viewCount > 0 ? (
              <span>👁 {article.viewCount}</span>
            ) : null}
          </div>
        </div>
      </Link>
    </article>
  );
}
