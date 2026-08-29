import Link from 'next/link';

import { PortalImage } from '@/components/PortalImage';
import type { ArticleRow } from '@/lib/api';
import { fileUrl, formatDate, ROUTES } from '@/lib/routes';
import { EmptyBlock } from './EmptyBlock';
import { SectionTitle } from './SectionTitle';

/**
 * Cột 4/12 cạnh slider — danh sách tin có ảnh, cuộn trong khung.
 *
 * <h2>Khối này THAY hai khối cũ</h2>
 *
 * Trước 29/08 cột trái có {@code HomeHotNews} (3 thẻ ảnh) nằm dưới slider, cột phải có
 * {@code HomeLatestNewsFeed} (danh sách chữ trơn). Cùng một nguồn bài, chia hai chỗ, hai
 * kiểu trình bày — người đọc phải quét hai lần để biết có gì mới. Nay gộp: một danh sách,
 * mỗi dòng một ảnh, đúng cột phải của cổng tham chiếu.
 *
 * <h2>⭐ Cao bằng cột slider — bằng CẤU TRÚC, không bằng một con số</h2>
 *
 * Cổng tham chiếu chốt {@code max-h-[510px]}. Con số ấy đúng với đúng bộ nội dung của họ:
 * thêm một tiêu đề dài thêm một dòng là hai cột lệch nhau ngay. Ở đây thẻ nhận
 * {@code h-full} rồi vùng cuộn lấy {@code flex-1 min-h-0} — nó co giãn theo chiều cao mà
 * lưới cấp cho hàng, nên hai cột bằng nhau với MỌI bộ nội dung, kể cả khi một bên rỗng.
 *
 * <p>{@code min-h-0} là mảnh dễ quên nhất: mặc định {@code min-height} của một mục flex là
 * {@code auto}, nghĩa là nó từ chối co nhỏ hơn nội dung — vùng cuộn sẽ đẩy dài thẻ ra thay
 * vì cuộn, và thanh cuộn không bao giờ xuất hiện.
 */
interface HomeNewsColumnProps {
  articles: ArticleRow[];
  /** Số bài hiển thị — `site.home.news-count`. */
  soBai: number;
  tieuDe: string;
  /** Chuyên mục nguồn, cho liên kết "Xem tất cả". */
  categorySlug: string;
}

export function HomeNewsColumn({ articles, soBai, tieuDe, categorySlug }: HomeNewsColumnProps) {
  const hienThi = articles.slice(0, soBai);

  return (
    <div className="flex h-full flex-col rounded-lg border border-surface-border bg-white p-5 shadow-sm sm:p-6">
      <SectionTitle href={ROUTES.category(categorySlug)}>{tieuDe}</SectionTitle>

      {hienThi.length === 0 ? (
        <div className="mt-4 flex flex-1 items-center">
          <EmptyBlock>Chưa có tin tức nào được xuất bản.</EmptyBlock>
        </div>
      ) : (
        <div className="sn-scroll mt-4 min-h-0 flex-1 content-start space-y-4 overflow-y-auto pr-2">
          {hienThi.map((article) => (
            <article
              key={article.slug}
              className="group border-b border-surface-border/60 pb-4 last:border-b-0"
            >
              <Link href={ROUTES.article(article.slug)} className="flex gap-3">
                <PortalImage
                  src={fileUrl(article.coverAttachmentPublicId)}
                  alt=""
                  ratio="aspect-[16/9]"
                  className="w-[103px] shrink-0 rounded-lg lg:w-[120px]"
                />
                <div className="min-w-0">
                  <h3 className="line-clamp-3 text-[15px] leading-snug text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary">
                    {article.title}
                  </h3>
                  <time
                    dateTime={article.publishedAt ?? undefined}
                    className="mt-1.5 block text-[11px] text-surface-textSecondary"
                  >
                    {formatDate(article.publishedAt)}
                  </time>
                </div>
              </Link>
            </article>
          ))}
        </div>
      )}

      <div className="mt-4 flex shrink-0 items-center justify-end border-t border-surface-border/60 pt-4">
        <Link
          href={ROUTES.category(categorySlug)}
          className="flex items-center gap-1.5 text-[13px] font-semibold text-brand-primary hover:text-brand-primaryHover"
        >
          <span>Xem tất cả</span>
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2.5}
              d="m9 18 6-6-6-6"
            />
          </svg>
        </Link>
      </div>
    </div>
  );
}
