import type { Metadata } from 'next';

import { ArticleList } from '@/components/ArticleList';
import { Breadcrumb } from '@/components/Breadcrumb';
import { DocumentListing } from '@/components/DocumentListing';
import { PortalSidebar } from '@/components/PortalSidebar';
import { getArticles, getCategories, getSiteConfig } from '@/lib/api';
import { laNhanhCua } from '@/lib/homeCategories';
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
  searchParams: Promise<{ page?: string; q?: string }>;
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
  const { page, q } = await searchParams;
  const tuKhoa = (q ?? '').trim();

  const [categories, articles, latestNews, config] = await Promise.all([
    getCategories(),
    getArticles({ category: slug, page: Number(page ?? 0), q: tuKhoa || undefined }),
    getArticles({ size: 6 }),
    getSiteConfig(),
  ]);
  const category = categories?.find((c) => c.slug === slug);
  const categoryName = category?.name ?? 'Chuyên mục';

  /*
   * ⭐⭐ Nhánh VĂN BẢN trình bày bằng BẢNG, nhánh tin tức bằng danh sách bài có ảnh.
   *
   * Nhánh gốc đọc từ `site.home.documents-category` — cùng khoá `settings` mà trang chủ dùng, nên
   * đổi nhánh ở màn hình Cấu hình giao diện là cả hai nơi đổi theo. Viết cứng `'cong-bo-thong-tin'`
   * ở đây là dựng nguồn sự thật thứ hai (luật 14).
   *
   * ⚠ `laNhanhCua` leo `parentSlug` nên nó phủ CẢ nhánh con ("Văn bản pháp luật", "Văn bản Công
   *   ty") chứ không chỉ nhánh gốc — đúng chỗ người dùng bấm vào từ hàng nhánh con ở trang chủ.
   */
  const danhMucVanBan = config?.['site.home.documents-category'] ?? 'cong-bo-thong-tin';
  const laVanBan = laNhanhCua(categories ?? [], slug, danhMucVanBan);

  return (
    <div className="mx-auto max-w-[1232px] px-4 py-4 sm:px-6 animate-fade-in">
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
              <h1 className="text-xl font-bold tracking-tight text-surface-textBase sm:text-2xl">
                {categoryName}
              </h1>
            </div>
            {category?.description ? (
              <p className="mt-2 text-xs text-surface-textSecondary sm:text-sm leading-relaxed">
                {category.description}
              </p>
            ) : null}
          </div>

          {/* Danh sách bài viết — hoặc BẢNG VĂN BẢN nếu đây là nhánh Công bố thông tin */}
          {laVanBan ? (
            <DocumentListing
              page={articles}
              basePath={ROUTES.category(slug)}
              tuKhoa={tuKhoa}
              tenChuyenMuc={`chuyên mục "${categoryName}"`}
            />
          ) : (
            <ArticleList
              page={articles}
              basePath={ROUTES.category(slug)}
              emptyText={`Chưa có bài viết nào trong chuyên mục "${categoryName}".`}
            />
          )}
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
