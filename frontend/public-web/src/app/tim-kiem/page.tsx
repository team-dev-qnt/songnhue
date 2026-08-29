import type { Metadata } from 'next';

import { ArticleList } from '@/components/ArticleList';
import { Breadcrumb } from '@/components/Breadcrumb';
import { PortalSidebar } from '@/components/PortalSidebar';
import { getArticles, getSiteConfig } from '@/lib/api';
import { ROUTES } from '@/lib/routes';

/**
 * Tìm kiếm bài viết — CN-01.8 phần công khai.
 *
 * ⚠ `revalidate` vẫn có tác dụng vì mỗi bộ tham số truy vấn là một bản cache riêng: người
 * dùng gõ đi gõ lại cùng một từ khoá thì lượt sau lấy từ bộ đệm.
 */
export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Tìm kiếm - Thủy lợi Sông Nhuệ',
  // Trang kết quả tìm kiếm không nên nằm trong chỉ mục: nó sinh vô số URL cùng nội dung.
  robots: { index: false, follow: true },
};

export default async function SearchPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; page?: string }>;
}) {
  const { q, page } = await searchParams;
  const tuKhoa = (q ?? '').trim();

  const [ketQua, latestNews, config] = await Promise.all([
    getArticles({ q: tuKhoa || undefined, page: Number(page ?? 0) }),
    getArticles({ size: 6 }),
    getSiteConfig(),
  ]);

  return (
    <div className="mx-auto max-w-[1232px] px-4 py-6 sm:px-6 sm:py-8 animate-fade-in">
      {/* ───── Breadcrumbs Điều hướng ───── */}
      <Breadcrumb
        items={[{ label: 'Tìm kiếm' }, ...(tuKhoa ? [{ label: `Từ khóa: "${tuKhoa}"` }] : [])]}
      />

      {/* ───── Bố cục 2 Cột: Main (8 Cột) & Sidebar (4 Cột) ───── */}
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-12 lg:gap-8">
        {/* Cột chính 8/12 */}
        <main className="lg:col-span-8">
          <div className="rounded-xl border border-surface-border bg-white p-5 shadow-xs sm:p-6 mb-6">
            <div className="flex items-center gap-2.5 border-b border-surface-border pb-3">
              <span className="h-6 w-1.5 rounded-full bg-brand-primary"></span>
              <h1 className="text-xl font-bold tracking-tight text-surface-textBase sm:text-2xl">
                {tuKhoa ? `Kết quả tìm kiếm cho "${tuKhoa}"` : 'Tìm kiếm bài viết & tin tức'}
              </h1>
            </div>

            {/* Biểu mẫu GET thật với icon */}
            <form action={ROUTES.search} method="get" className="mt-5 flex gap-2">
              <div className="relative flex-1">
                <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-surface-textSecondary pointer-events-none">
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                    />
                  </svg>
                </span>
                <label htmlFor="q" className="sr-only">
                  Từ khoá tìm kiếm
                </label>
                <input
                  id="q"
                  name="q"
                  type="search"
                  defaultValue={tuKhoa}
                  placeholder="Nhập từ khóa tìm kiếm (gõ không dấu vẫn tìm được)..."
                  className="w-full rounded-lg border border-surface-border py-2.5 pl-9 pr-4 text-xs sm:text-sm text-surface-textBase transition-colors focus:border-brand-primary focus:outline-none focus:ring-1 focus:ring-brand-primary"
                />
              </div>
              <button
                type="submit"
                className="flex items-center gap-1.5 rounded-lg bg-brand-primary px-5 py-2.5 text-xs sm:text-sm font-bold text-white shadow-xs transition-colors hover:bg-brand-primaryHover"
              >
                <span>Tìm</span>
              </button>
            </form>

            {ketQua ? (
              <div className="mt-3 flex items-center justify-between text-xs text-surface-textSecondary">
                <span>
                  Tìm thấy{' '}
                  <strong className="text-brand-primary font-bold">{ketQua.totalElements}</strong>{' '}
                  kết quả phù hợp
                </span>
              </div>
            ) : null}
          </div>

          <div className="mt-6">
            <ArticleList
              page={ketQua}
              basePath={ROUTES.search}
              extraQuery={tuKhoa ? `q=${encodeURIComponent(tuKhoa)}` : ''}
              emptyText="Không tìm thấy bài viết nào khớp với từ khoá tìm kiếm."
            />
          </div>
        </main>

        {/* Cột Sidebar 4/12 */}
        <div className="lg:col-span-4">
          <PortalSidebar
            latestArticles={latestNews?.content ?? []}
            hotline={config?.['company.hotline']}
            docSystemUrl={config?.['site.external.doc-system-url']}
          />
        </div>
      </div>
    </div>
  );
}
