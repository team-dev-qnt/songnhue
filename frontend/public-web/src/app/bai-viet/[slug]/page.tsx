import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';

import { Breadcrumb } from '@/components/Breadcrumb';
import { PortalImage } from '@/components/PortalImage';
import { PortalSidebar } from '@/components/PortalSidebar';
import { ViewTracker } from '@/components/ViewTracker';
import { getArticle, getArticles, getSiteConfig } from '@/lib/api';
import { fileUrl, formatDate, ROUTES } from '@/lib/routes';

/** Trang chi tiết một bài viết — T16.2. */
/**
 * ⚠ Số viết thẳng, KHÔNG import hằng số: Next đọc `export const revalidate` bằng phân tích
 * tĩnh và từ chối build nếu giá trị không phải literal ("Invalid segment configuration
 * export"). `REVALIDATE_SECONDS` ở `lib/api.ts` phải bằng đúng con số này —
 * `revalidate-config.test.ts` canh việc đó.
 */
export const revalidate = 300;

interface PageProps {
  params: Promise<{ slug: string }>;
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { slug } = await params;
  const article = await getArticle(slug);
  if (!article) {
    return { title: 'Bài viết không tồn tại - Thủy lợi Sông Nhuệ' };
  }

  const cover = fileUrl(article.coverAttachmentPublicId);

  return {
    title: `${article.metaTitle || article.title} - Thủy lợi Sông Nhuệ`,
    description: article.metaDescription || article.summary || undefined,
    keywords: article.metaKeywords ? article.metaKeywords.split(',') : undefined,
    robots: article.archived ? { index: false, follow: true } : undefined,
    alternates: { canonical: ROUTES.article(article.slug) },
    openGraph: {
      type: 'article',
      title: article.metaTitle || article.title,
      description: article.metaDescription || article.summary || undefined,
      publishedTime: article.publishedAt ?? undefined,
      images: cover ? [{ url: cover }] : undefined,
    },
  };
}

export default async function ArticlePage({ params }: PageProps) {
  const { slug } = await params;
  const [article, latestNews, config] = await Promise.all([
    getArticle(slug),
    getArticles({ size: 6 }),
    getSiteConfig(),
  ]);

  if (!article) {
    notFound();
  }

  const cover = fileUrl(article.coverAttachmentPublicId);
  const primaryCategory = article.categories.length > 0 ? article.categories[0] : null;

  return (
    <div className="mx-auto max-w-[1240px] px-4 py-6 sm:px-6 sm:py-8 animate-fade-in">
      {/* ───── Breadcrumbs Điều hướng ───── */}
      <Breadcrumb
        items={[
          ...(primaryCategory
            ? [{ label: primaryCategory.name, href: ROUTES.category(primaryCategory.slug) }]
            : [{ label: 'Tin tức', href: ROUTES.search }]),
          { label: article.title },
        ]}
      />

      {/* ───── Bố cục 2 Cột: Nội dung Bài viết (8 Cột) & Sidebar (4 Cột) ───── */}
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-12 lg:gap-8">
        {/* Cột chính 8/12 */}
        <main className="lg:col-span-8">
          <article className="rounded-xl border border-surface-border bg-white p-5 shadow-xs sm:p-8">
            {/* Category Tags */}
            {article.categories.length > 0 ? (
              <div className="mb-3 flex flex-wrap gap-2">
                {article.categories.map((c) => (
                  <Link
                    key={c.slug}
                    href={ROUTES.category(c.slug)}
                    className="rounded bg-brand-primaryLight px-2.5 py-1 text-xs font-bold text-brand-primary transition-colors hover:bg-brand-primary hover:text-white"
                  >
                    {c.name}
                  </Link>
                ))}
              </div>
            ) : null}

            {/* Tiêu đề Bài viết */}
            <h1 className="text-xl font-black leading-tight text-surface-textBase sm:text-2xl md:text-3xl">
              {article.title}
            </h1>

            {/* Dải Metadata thông tin bài viết */}
            <div className="mt-3.5 flex flex-wrap items-center justify-between gap-3 border-b border-surface-border/70 pb-3.5 text-xs text-surface-textSecondary">
              <div className="flex items-center gap-3">
                <time
                  dateTime={article.publishedAt ?? undefined}
                  className="flex items-center gap-1"
                >
                  <span>📅</span>
                  <span>{formatDate(article.publishedAt)}</span>
                </time>
                {article.viewCount !== undefined && article.viewCount > 0 ? (
                  <span className="flex items-center gap-1">
                    <span>👁</span>
                    <span>{article.viewCount} lượt xem</span>
                  </span>
                ) : null}
              </div>

              {article.archived ? (
                <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-900">
                  Nội dung lưu trữ
                </span>
              ) : null}
            </div>

            {/* Khối Sapo / Tóm tắt */}
            {article.summary ? (
              <div className="mt-5 rounded-r-lg border-l-4 border-brand-primary bg-sky-50/70 p-4 text-sm font-semibold leading-relaxed text-surface-textBase sm:text-base">
                {article.summary}
              </div>
            ) : null}

            {/* Ảnh minh họa bài viết nếu có */}
            {cover ? (
              // ⚠ Bản trước: `<img className="w-full object-cover">` trong một div không có
              //   chiều cao. `object-cover` chỉ có tác dụng khi khung ĐÃ có kích thước — không
              //   có thì nó là một khai báo chết, ảnh vẫn hiện theo tỉ lệ gốc. Nặng hơn: khung
              //   cao 0 cho tới lúc ảnh về rồi bung ra, đẩy toàn bộ bài viết xuống (CLS).
              <PortalImage
                src={cover}
                alt={article.title}
                ratio="aspect-[16/9]"
                priority
                className="mt-6 rounded-xl shadow-xs"
              />
            ) : null}

            {/* Nội dung bài viết chuẩn sn-article đã khử độc HTML */}
            <div
              className="sn-article mt-6 max-w-none"
              // eslint-disable-next-line react/no-danger -- HtmlSanitizer (BE) đã lọc lúc GHI
              dangerouslySetInnerHTML={{ __html: article.content }}
            />

            {/* Dải chân bài viết: Chia sẻ & Quay lại */}
            <div className="mt-8 flex flex-wrap items-center justify-between gap-3 border-t border-surface-border/80 pt-4 text-xs">
              <Link
                href={primaryCategory ? ROUTES.category(primaryCategory.slug) : ROUTES.home}
                className="font-bold text-brand-primary hover:underline"
              >
                ← Quay lại danh sách
              </Link>
              <span className="font-semibold text-surface-textSecondary">
                Nguồn: Cổng TTĐT Thủy lợi Sông Nhuệ
              </span>
            </div>

            <ViewTracker slug={article.slug} />
          </article>
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
