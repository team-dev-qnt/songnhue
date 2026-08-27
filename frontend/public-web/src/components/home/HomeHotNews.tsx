import Link from 'next/link';

import type { ArticleRow } from '@/lib/api';
import { fileUrl, formatDate, ROUTES } from '@/lib/routes';
import { EmptyBlock } from './EmptyBlock';

interface HomeHotNewsProps {
  articles: ArticleRow[];
}

/** CR-11 nói rõ "3 tin Hot" — con số nằm trong chính yêu cầu, không phải một tham số vận hành. */
const SO_TIN_HOT = 3;

/**
 * Khối **3 tin Hot** ngay dưới slider — CR-11.
 *
 * <p>CR-11 là mục duy nhất trong bảng 43 mã mang ưu tiên <i>"Giữ nguyên"</i>: bố cục ba thẻ có
 * ảnh này đúng ý Công ty. Thứ đổi quanh nó là <b>chỗ nó đứng</b>: khối trên đầu trang không
 * còn là một bài viết đinh nữa mà là slider ảnh hoạt động (CR-10), nên bài đinh cũ đã bỏ và ba
 * thẻ này lên thành khối chính của cột trái.
 *
 * ⛔ Không lấp chỗ trống: có hai bài thì hiện hai thẻ. Bản trước ghép thêm bốn bài viết cứng
 * khi có dưới bốn bài, nên một mảng rỗng vẫn cho ra một khối tiêu điểm đầy đủ (§10.54).
 */
export function HomeHotNews({ articles }: HomeHotNewsProps) {
  const hienThi = articles.slice(0, SO_TIN_HOT);

  if (hienThi.length === 0) {
    return <EmptyBlock>Chưa có tin tức nào được xuất bản.</EmptyBlock>;
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
      {hienThi.map((article) => {
        const cover = fileUrl(article.coverAttachmentPublicId);
        return (
          <article
            key={article.slug}
            className="group flex flex-col overflow-hidden rounded-lg border border-surface-border bg-white p-3 shadow-xs transition-all duration-300 ease-smooth hover:-translate-y-0.5 hover:border-brand-primary hover:shadow-md"
          >
            <Link href={ROUTES.article(article.slug)} className="flex flex-col gap-2.5">
              <div className="aspect-[16/10] w-full overflow-hidden rounded-md bg-surface-bgLayout">
                {cover ? (
                  <img
                    src={cover}
                    alt=""
                    loading="lazy"
                    decoding="async"
                    className="h-full w-full object-cover transition-transform duration-500 ease-smooth group-hover:scale-105"
                  />
                ) : (
                  <div className="flex h-full w-full items-center justify-center bg-surface-bgLayout">
                    <svg
                      className="h-6 w-6 text-surface-border"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={1.5}
                        d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                      />
                    </svg>
                  </div>
                )}
              </div>
              <div>
                <h3 className="line-clamp-2 text-sm font-semibold text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary">
                  {article.title}
                </h3>
                <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-surface-textSecondary">
                  <time dateTime={article.publishedAt ?? undefined}>
                    {formatDate(article.publishedAt)}
                  </time>
                  {article.categories.slice(0, 1).map((c) => (
                    <span
                      key={c.slug}
                      className="rounded bg-brand-primaryLight px-1.5 py-0.5 text-[11px] font-semibold text-brand-primary"
                    >
                      {c.name}
                    </span>
                  ))}
                </div>
              </div>
            </Link>
          </article>
        );
      })}
    </div>
  );
}
