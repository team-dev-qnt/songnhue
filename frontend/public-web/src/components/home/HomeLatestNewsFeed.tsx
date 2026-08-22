import Link from 'next/link';

import type { ArticleRow } from '@/lib/api';
import { formatDate, ROUTES } from '@/lib/routes';

const DEFAULT_NEWS_FEED: ArticleRow[] = [
  {
    title: 'Phát động phong trào thi đua hoàn thành kế hoạch tưới tiêu phục vụ sản xuất',
    slug: 'phat-dong-thi-dua-hoan-thanh-ke-hoach-tuoi-tieu',
    summary: '',
    publishedAt: '2026-08-20T10:00:00Z',
    viewCount: 320,
    coverAttachmentPublicId: null,
  },
  {
    title: 'Công tác trực ban 24/24h phòng chống bão số 3 và ngập úng đô thị vùng ven',
    slug: 'truc-ban-phong-chong-bao-so-3',
    summary: '',
    publishedAt: '2026-08-19T15:30:00Z',
    viewCount: 450,
    coverAttachmentPublicId: null,
  },
  {
    title: 'Bảo dưỡng định kỳ hệ thống máy đóng mở cống và tổ máy bơm trạm Vân Đình',
    slug: 'bao-duong-dinh-ky-tram-bom-van-dinh',
    summary: '',
    publishedAt: '2026-08-18T08:45:00Z',
    viewCount: 210,
    coverAttachmentPublicId: null,
  },
  {
    title: 'Tăng cường tuần tra, xử lý vi phạm hành lang bảo vệ công trình thủy lợi',
    slug: 'tuan-tra-xu-ly-vi-pham-hanh-lang-thuy-loi',
    summary: '',
    publishedAt: '2026-08-17T11:20:00Z',
    viewCount: 190,
    coverAttachmentPublicId: null,
  },
  {
    title: 'Tập huấn kỹ thuật vận hành cửa van tự động cho cán bộ kỹ thuật các xí nghiệp',
    slug: 'tap-huan-ky-thuat-van-hanh-cua-van',
    summary: '',
    publishedAt: '2026-08-16T14:10:00Z',
    viewCount: 165,
    coverAttachmentPublicId: null,
  },
  {
    title: 'Kiểm tra chất lượng nguồn nước và các điểm xả thải dọc tuyến kênh chính',
    slug: 'kiem-tra-chat-luong-nguon-nuoc-song-nhue',
    summary: '',
    publishedAt: '2026-08-15T09:00:00Z',
    viewCount: 280,
    coverAttachmentPublicId: null,
  },
];

const DEFAULT_NOTICE_ITEM: ArticleRow = {
  title: 'Thông báo lịch vận hành điều tiết xả nước đệm hạ thấp mực nước Sông Nhuệ',
  slug: 'thong-bao-lich-van-hanh-dieu-tiet-xa-nuoc-dem',
  summary: '',
  publishedAt: '2026-08-20T07:00:00Z',
  viewCount: 880,
  coverAttachmentPublicId: null,
};

interface HomeLatestNewsFeedProps {
  latestArticles: ArticleRow[];
  noticeArticles?: ArticleRow[];
}

/**
 * Cột Tin Mới & Dòng Thời Sự Nóng (Cột 4/12).
 *
 * - Header khối dạng thẻ chuyên mục với thanh nhấn thương hiệu.
 * - Danh sách cuộn thời sự cô đọng, hiển thị ngày đăng và tiêu đề tinh gọn.
 * - Nút "Xem tất cả" dẫn tới trang tìm kiếm/danh mục.
 */
export function HomeLatestNewsFeed({
  latestArticles,
  noticeArticles = [],
}: HomeLatestNewsFeedProps) {
  // Lấy các bài viết từ API thật (từ bài thứ 4 trở đi sau Hero hoặc toàn bộ), nếu thiếu thì ghép thêm fallback
  const rawList = latestArticles.length > 4 ? latestArticles.slice(4, 10) : latestArticles;
  const displayArticles =
    rawList.length >= 6
      ? rawList.slice(0, 6)
      : [...rawList, ...DEFAULT_NEWS_FEED.slice(rawList.length)].slice(0, 6);

  const activeNotice = noticeArticles.length > 0 ? noticeArticles[0] : DEFAULT_NOTICE_ITEM;

  return (
    <aside className="flex flex-col rounded-xl border border-surface-border bg-white p-5 shadow-sm">
      {/* Header Khối */}
      <div className="flex items-center justify-between border-b border-surface-border pb-3">
        <div className="flex items-center gap-2">
          <span className="h-4 w-1 rounded-full bg-brand-primary"></span>
          <h2 className="text-base font-bold uppercase tracking-tight text-surface-textBase sm:text-lg">
            Dòng thời sự
          </h2>
        </div>
        <Link
          href={ROUTES.search}
          className="text-xs font-semibold text-brand-primary transition-colors hover:underline"
        >
          Xem tất cả ➔
        </Link>
      </div>

      {/* Danh sách tin mới cuộn gọn */}
      <div className="mt-3 divide-y divide-surface-border">
        {displayArticles.length > 0 ? (
          displayArticles.map((article, index) => (
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
                  <div className="mt-1 flex items-center gap-2 text-[11px] text-surface-textSecondary">
                    <time dateTime={article.publishedAt ?? undefined}>
                      {formatDate(article.publishedAt)}
                    </time>
                  </div>
                </div>
              </Link>
            </article>
          ))
        ) : (
          <p className="py-4 text-center text-xs text-surface-textSecondary">
            Chưa có tin thời sự mới.
          </p>
        )}
      </div>

      {/* Thông báo điều hành nổi bật nhanh */}
      {activeNotice ? (
        <div className="mt-4 rounded-lg bg-amber-50 p-3.5 border border-amber-200/70">
          <div className="flex items-center gap-1.5 text-xs font-bold text-amber-900">
            <svg
              className="h-4 w-4 text-amber-600"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
            Thông báo điều hành
          </div>
          <Link
            href={ROUTES.article(activeNotice.slug)}
            className="mt-1.5 block line-clamp-2 text-xs font-medium text-amber-950 hover:underline"
          >
            {activeNotice.title}
          </Link>
        </div>
      ) : null}
    </aside>
  );
}
