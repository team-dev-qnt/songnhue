import type { Metadata } from 'next';

import { ArticleList } from '@/components/ArticleList';
import { Breadcrumb } from '@/components/Breadcrumb';
import { PortalSidebar } from '@/components/PortalSidebar';
import { getArticles, getCategories, getSiteConfig } from '@/lib/api';
import { ROUTES } from '@/lib/routes';

/** Trang danh sách bài theo chuyên mục — T16.3. */
/**
 * ⚠ Số viết thẳng, KHÔNG import hằng số: Next đọc `export const revalidate` bằng phân tích
 * tĩnh và từ chối build nếu giá trị không phải literal ("Invalid segment configuration
 * export"). `REVALIDATE_SECONDS` ở `lib/api.ts` phải bằng đúng con số này —
 * `revalidate-config.test.ts` canh việc đó.
 */
export const revalidate = 300;

interface PageProps {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ page?: string }>;
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { slug } = await params;
  const categories = await getCategories();
  const category = categories?.find((c) => c.slug === slug);

  return {
    title: `${category?.name ?? 'Chuyên mục'} - Thủy lợi Sông Nhuệ`,
    description: category?.description ?? undefined,
    alternates: { canonical: ROUTES.category(slug) },
  };
}

export default async function CategoryPage({ params, searchParams }: PageProps) {
  const { slug } = await params;
  const { page } = await searchParams;

  const [categories, articles, latestNews, config] = await Promise.all([
    getCategories(),
    getArticles({ category: slug, page: Number(page ?? 0) }),
    getArticles({ size: 6 }),
    getSiteConfig(),
  ]);
  const category = categories?.find((c) => c.slug === slug);
  const categoryName = category?.name ?? 'Chuyên mục';

  return (
    <div className="mx-auto max-w-[1240px] px-4 py-6 sm:px-6 sm:py-8 animate-fade-in">
      {/* ───── Breadcrumbs Điều hướng ───── */}
      <Breadcrumb items={[{ label: 'Chuyên mục', href: ROUTES.home }, { label: categoryName }]} />

      {/* ───── Bố cục 2 Cột: Main (8 Cột) & Sidebar (4 Cột) ───── */}
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-12 lg:gap-8">
        {/* Cột chính 8/12 */}
        <main className="lg:col-span-8">
          {/* Header Chuyên mục */}
          <div className="mb-6 border-b-2 border-brand-primary pb-3">
            <div className="flex items-center gap-2.5">
              <span className="h-6 w-1.5 rounded-full bg-brand-primary"></span>
              <h1 className="text-xl font-bold uppercase tracking-tight text-surface-textBase sm:text-2xl">
                {categoryName}
              </h1>
            </div>
            {category?.description ? (
              <p className="mt-2 text-xs text-surface-textSecondary sm:text-sm leading-relaxed">
                {category.description}
              </p>
            ) : null}
          </div>

          {/* Danh sách bài viết */}
          <ArticleList
            page={articles}
            basePath={ROUTES.category(slug)}
            emptyText={`Chưa có bài viết nào trong chuyên mục "${categoryName}".`}
          />
        </main>

        {/* Cột Sidebar 4/12 */}
        <div className="lg:col-span-4">
          <PortalSidebar
            latestArticles={latestNews?.content ?? []}
            hotline={config?.['company.hotline']}
          />
        </div>
      </div>
    </div>
  );
}
