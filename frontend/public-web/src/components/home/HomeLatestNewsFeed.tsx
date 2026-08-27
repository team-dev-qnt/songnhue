import Link from 'next/link';

import type { ArticleRow } from '@/lib/api';
import { formatDate, ROUTES } from '@/lib/routes';
import { EmptyBlock } from './EmptyBlock';

interface HomeLatestNewsFeedProps {
  latestArticles: ArticleRow[];
  /** `site.home.news-count` — CR-12 nói "khoảng 5 bài", con số thật do Công ty chỉnh. */
  soBai: number;
}

/**
 * Khối **TIN TỨC – SỰ KIỆN** bên phải banner — CR-12.
 *
 * <h2>Ba thay đổi so với bản dev</h2>
 *
 * <ol>
 *   <li><b>Tên khối.</b> "Dòng thời sự" không có mặt trong cây nội dung §3, nên nó là một hệ
 *       phân loại thứ hai tồn tại song song với menu — đúng thứ §2 cấm.
 *   <li><b>Số bài đọc từ `settings`</b> thay vì hằng số 6 viết trong mã (§2).
 *   <li><b>Nhãn chuyên mục</b> cạnh mỗi bài, phân biệt Tin thủy lợi / Tin Công ty. Nhãn đến từ
 *       `article.categories` — dữ liệu — chứ không phải một phép đoán theo tiêu đề.
 * </ol>
 *
 * <h2>⛔ Khối "Thông báo điều hành" đã bị gỡ</h2>
 *
 * Bản trước lọc bài theo `title.includes('thông báo')` rồi dựng một ô riêng cho bài đầu tiên
 * khớp. Đó là phân loại nội dung bằng chuỗi con của tiêu đề: một bài tên "Thông báo mời thầu"
 * và một bài tên "Kết quả xử lý thông báo của xã X" rơi vào cùng một rổ, còn một thông báo
 * thật không có chữ ấy trong tên thì không bao giờ hiện. CR-01 cũng vừa bỏ hẳn mục "Thông
 * báo" khỏi cây nội dung, nên cái rổ ấy không còn chỗ đứng nào.
 */
export function HomeLatestNewsFeed({ latestArticles, soBai }: HomeLatestNewsFeedProps) {
  // ⛔ KHÔNG lấp chỗ trống. Có bao nhiêu thì hiện bấy nhiêu — bản trước ghép thêm sáu bài viết
  //    cứng cho đủ ô, nên nhánh "chưa có tin" chưa từng chạy một lần nào (luật 7 · §10.54).
  const hienThi = latestArticles.slice(0, Math.max(soBai, 1));

  return (
    <aside className="flex flex-col rounded-xl border border-surface-border bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between border-b border-surface-border pb-3">
        <div className="flex items-center gap-2">
          <span className="h-4 w-1 rounded-full bg-brand-primary"></span>
          <h2 className="text-base font-bold tracking-tight text-surface-textBase sm:text-lg">
            Tin tức – Sự kiện
          </h2>
        </div>
        <Link
          href={ROUTES.search}
          className="text-xs font-semibold text-brand-primary transition-colors hover:underline"
        >
          Xem tất cả ➔
        </Link>
      </div>

      <div className="mt-3 divide-y divide-surface-border">
        {hienThi.length > 0 ? (
          hienThi.map((article, index) => (
            <article key={article.slug} className="group py-3 first:pt-1 last:pb-1">
              <Link href={ROUTES.article(article.slug)} className="flex items-start gap-3">
                <span
                  className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded text-xs transition-transform duration-200 group-hover:scale-110 ${
                    index === 0
                      ? 'bg-red-600 text-white font-black shadow-2xs'
                      : index < 3
                        ? 'bg-brand-primary text-white font-bold'
                        : 'bg-surface-bgLayout text-surface-textSecondary font-semibold'
                  }`}
                >
                  {index + 1}
                </span>
                <div className="flex-1">
                  <h3 className="line-clamp-2 text-sm font-medium text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary">
                    {article.title}
                  </h3>
                  <div className="mt-1 flex flex-wrap items-center gap-2 text-[11px] text-surface-textSecondary">
                    <time dateTime={article.publishedAt ?? undefined}>
                      {formatDate(article.publishedAt)}
                    </time>
                    {/* Nhãn chuyên mục — CR-12. Bài chưa xếp chuyên mục nào đang hiện thì
                        không có ô nào, chứ không mượn một nhãn mặc định. */}
                    {article.categories.map((c) => (
                      <span
                        key={c.slug}
                        className="rounded bg-brand-primaryLight px-1.5 py-0.5 font-semibold text-brand-primary"
                      >
                        {c.name}
                      </span>
                    ))}
                  </div>
                </div>
              </Link>
            </article>
          ))
        ) : (
          <div className="py-3">
            <EmptyBlock>Chưa có tin tức nào được xuất bản.</EmptyBlock>
          </div>
        )}
      </div>
    </aside>
  );
}
