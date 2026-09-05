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
    return (
      <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-surface-border bg-white p-12 text-center shadow-2xs">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-surface-bgLayout text-surface-textSecondary">
          <svg className="h-7 w-7" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M19 20H5a2 2 0 01-2-2V6a2 2 0 012-2h10a2 2 0 012 2v1m2 13a2 2 0 01-2-2V7m2 13a2 2 0 002-2V9a2 2 0 00-2-2h-2m-4-3H9M7 16h6M7 8h6v4H7V8z"
            />
          </svg>
        </div>
        <p className="mt-3 text-sm font-medium text-surface-textSecondary">{emptyText}</p>
      </div>
    );
  }

  const link = (target: number) =>
    `${basePath}?${extraQuery ? `${extraQuery}&` : ''}page=${target}`;

  return (
    <>
      <div className="grid gap-6 sm:grid-cols-2">
        {page.content.map((article) => (
          <ArticleCard key={article.slug} article={article} />
        ))}
      </div>

      {page.totalPages > 1 ? (
        <nav aria-label="Phân trang" className="mt-10 flex items-center justify-center gap-2">
          {page.number > 0 ? (
            <Link
              href={link(page.number - 1)}
              className="flex items-center gap-1 rounded-lg border border-surface-border bg-white px-3.5 py-2 text-xs font-semibold text-surface-textBase shadow-2xs transition-colors hover:border-brand-primary hover:text-brand-primary"
            >
              ← Trang trước
            </Link>
          ) : null}

          <div className="flex items-center gap-1 px-2">
            <span className="rounded-md bg-brand-primary px-3 py-1.5 text-xs font-bold text-white shadow-2xs">
              {page.number + 1}
            </span>
            <span className="text-xs text-surface-textSecondary">/ {page.totalPages}</span>
          </div>

          {page.number + 1 < page.totalPages ? (
            <Link
              href={link(page.number + 1)}
              className="flex items-center gap-1 rounded-lg border border-surface-border bg-white px-3.5 py-2 text-xs font-semibold text-surface-textBase shadow-2xs transition-colors hover:border-brand-primary hover:text-brand-primary"
            >
              Trang sau →
            </Link>
          ) : null}
        </nav>
      ) : null}
    </>
  );
}
