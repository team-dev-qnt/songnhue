import type { Metadata } from 'next';

import { ArticleList } from '@/components/ArticleList';
import { getArticles, getCategories } from '@/lib/api';
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
    title: category?.name ?? 'Chuyên mục',
    description: category?.description ?? undefined,
    alternates: { canonical: ROUTES.category(slug) },
  };
}

export default async function CategoryPage({ params, searchParams }: PageProps) {
  const { slug } = await params;
  const { page } = await searchParams;

  const [categories, articles] = await Promise.all([
    getCategories(),
    getArticles({ category: slug, page: Number(page ?? 0) }),
  ]);
  const category = categories?.find((c) => c.slug === slug);

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      {/*
        Danh mục ẩn hoặc không tồn tại: backend trả danh sách RỖNG chứ không trả toàn bộ bài
        (PublicPortalService). Ở đây chỉ cần hiện tên nếu biết, và một thông báo trống nếu
        không — không phải 404, vì một chuyên mục vừa được ẩn đi thì trang trống dễ hiểu hơn
        là trang lỗi.
      */}
      <h1 className="text-2xl font-bold text-surface-textBase">{category?.name ?? 'Chuyên mục'}</h1>
      {category?.description ? (
        <p className="mt-2 text-surface-textSecondary">{category.description}</p>
      ) : null}

      <div className="mt-6">
        <ArticleList page={articles} basePath={ROUTES.category(slug)} />
      </div>
    </div>
  );
}
