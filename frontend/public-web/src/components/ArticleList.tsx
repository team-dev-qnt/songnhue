import Link from 'next/link';

import type { PagedArticles } from '@/lib/api';
import { ArticleCard } from '@/components/ArticleCard';

/**
 * Lưới bài viết + phân trang.
 *
 * Phân trang bằng **liên kết thật** (`<a href="?page=2">`) chứ không bằng nút gọi JavaScript:
 * trang này được dựng tĩnh, và liên kết thật thì công cụ tìm kiếm đi được vào trang 2, người
 * dùng bookmark được, nút Back của trình duyệt hoạt động đúng.
 */
export function ArticleList({
  page,
  basePath,
  extraQuery = '',
  emptyText = 'Chưa có bài viết nào trong mục này.',
}: {
  page: PagedArticles | null;
  basePath: string;
  extraQuery?: string;
  emptyText?: string;
}) {
  if (!page || page.content.length === 0) {
    return <p className="py-10 text-center text-surface-textSecondary">{emptyText}</p>;
  }

  const link = (target: number) =>
    `${basePath}?${extraQuery ? `${extraQuery}&` : ''}page=${target}`;

  return (
    <>
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {page.content.map((article) => (
          <ArticleCard key={article.slug} article={article} />
        ))}
      </div>

      {page.totalPages > 1 ? (
        <nav aria-label="Phân trang" className="mt-8 flex items-center justify-center gap-2">
          {page.number > 0 ? (
            <Link
              href={link(page.number - 1)}
              className="rounded border border-surface-border px-3 py-1.5 text-sm hover:border-brand-primary"
            >
              Trang trước
            </Link>
          ) : null}
          <span className="px-2 text-sm text-surface-textSecondary">
            Trang {page.number + 1} / {page.totalPages}
          </span>
          {page.number + 1 < page.totalPages ? (
            <Link
              href={link(page.number + 1)}
              className="rounded border border-surface-border px-3 py-1.5 text-sm hover:border-brand-primary"
            >
              Trang sau
            </Link>
          ) : null}
        </nav>
      ) : null}
    </>
  );
}
