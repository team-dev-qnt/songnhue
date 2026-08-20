import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';

import { ViewTracker } from '@/components/ViewTracker';
import { getArticle } from '@/lib/api';
import { fileUrl, formatDate, ROUTES } from '@/lib/routes';

/**
 * Trang chi tiết bài viết — T16.3, T16.4, T16.7.
 *
 * <h3>404 cho mọi thứ chưa được phép xem</h3>
 *
 * Backend trả 404 cho bài Nháp, Chờ duyệt, Gỡ bài và bài hẹn giờ chưa tới hạn — cùng một câu
 * trả lời với slug không tồn tại. Trang này chỉ việc chuyển tiếp: phân biệt ở đây là làm hỏng
 * đúng thứ backend vừa cẩn thận giữ.
 */
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
    return { title: 'Không tìm thấy bài viết' };
  }

  const cover = fileUrl(article.coverAttachmentPublicId);

  return {
    title: article.metaTitle || article.title,
    description: article.metaDescription || article.summary || undefined,
    keywords: article.metaKeywords || undefined,
    alternates: { canonical: ROUTES.article(article.slug) },
    // ⛔ Bài Lưu trữ đã rút khỏi luồng tin nhưng địa chỉ vẫn sống. Không gắn `noindex` thì
    // công cụ tìm kiếm giữ nó trong kết quả mãi, và người dân đọc phải một thông báo cũ
    // tưởng là mới.
    robots: article.archived ? { index: false, follow: true } : undefined,
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
  const article = await getArticle(slug);

  if (!article) {
    notFound();
  }

  const cover = fileUrl(article.coverAttachmentPublicId);

  return (
    <article className="mx-auto max-w-3xl px-4 py-8">
      {article.categories.length > 0 ? (
        <nav aria-label="Chuyên mục" className="mb-3 flex flex-wrap gap-2 text-sm">
          {article.categories.map((c) => (
            <Link
              key={c.slug}
              href={ROUTES.category(c.slug)}
              className="text-brand-primary hover:underline"
            >
              {c.name}
            </Link>
          ))}
        </nav>
      ) : null}

      <h1 className="text-2xl font-bold text-surface-textBase sm:text-3xl">{article.title}</h1>

      <p className="mt-2 text-sm text-surface-textSecondary">
        <time dateTime={article.publishedAt ?? undefined}>{formatDate(article.publishedAt)}</time>
        {article.archived ? (
          <span className="ml-3 rounded bg-surface-bgLayout px-2 py-0.5 text-xs">
            Nội dung lưu trữ
          </span>
        ) : null}
      </p>

      {article.summary ? (
        <p className="mt-4 border-l-4 border-brand-primary pl-4 text-surface-textSecondary">
          {article.summary}
        </p>
      ) : null}

      {cover ? (
        <img src={cover} alt="" className="mt-6 w-full rounded object-cover" loading="lazy" />
      ) : null}

      {/*
        Nội dung là HTML đã được `HtmlSanitizer` của backend lọc **lúc ghi** (danh sách CHO
        PHÉP, jsoup): script, iframe, thuộc tính `on*` và `javascript:` đều bị gỡ trước khi
        vào CSDL. Lọc lúc ghi chứ không lúc đọc, nên mọi nơi hiển thị — cổng công khai lẫn
        màn hình xem trước của admin-app — đều nhận nội dung đã sạch.

        Quy trình duyệt KHÔNG phải lớp bảo vệ ở đây: người duyệt nhìn nội dung hiển thị, không
        nhìn mã nguồn HTML.
      */}
      {/*
        ⚠ `sn-article` chứ không phải `prose`: class `prose` ở bản trước là **class rỗng** —
        gói `@tailwindcss/typography` chưa từng được cài, nên bài lên cổng mất hết dấu đầu
        dòng, viền bảng, cỡ chữ tiêu đề và cả căn lề. Xem `article-content.css`.
      */}
      <div
        className="sn-article mt-6 max-w-none"
        // eslint-disable-next-line react/no-danger -- HtmlSanitizer (BE) đã lọc lúc GHI
        dangerouslySetInnerHTML={{ __html: article.content }}
      />

      <ViewTracker slug={article.slug} />
    </article>
  );
}
