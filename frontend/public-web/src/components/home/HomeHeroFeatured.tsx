import Link from 'next/link';

import type { ArticleRow, BannerItem } from '@/lib/api';
import { fileUrl, formatDate, ROUTES } from '@/lib/routes';

const DEFAULT_HERO_ARTICLES: ArticleRow[] = [
  {
    title: 'Hội nghị Triển khai Công tác Vận hành & Phòng chống Thiên tai năm 2026 Lưu vực Sông Nhuệ',
    slug: 'trien-khai-cong-tac-van-hanh-pctt-2026',
    summary:
      'Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ tổ chức hội nghị tổng kết và giao chỉ tiêu vận hành các cụm công trình đầu mối, bảo đảm an toàn hệ thống đê điều và tưới tiêu phục vụ sản xuất.',
    publishedAt: '2026-08-20T08:00:00Z',
    viewCount: 1420,
    coverAttachmentPublicId: null,
  },
  {
    title: 'Chủ động vận hành Trạm bơm Yên Nghĩa tiêu úng phục vụ sản xuất nông nghiệp vụ Mùa',
    slug: 'van-hanh-tram-bom-yen-nghia-vu-mua',
    summary:
      'Công tác trực ban 24/24h tại các tổ máy bơm Yên Nghĩa bảo đảm tiêu thoát nước nhanh chóng.',
    publishedAt: '2026-08-19T09:30:00Z',
    viewCount: 890,
    coverAttachmentPublicId: null,
  },
  {
    title: 'Kiểm tra an toàn hệ thống cống đầu mối Liên Mạc và Cầu Cung trước mùa mưa bão',
    slug: 'kiem-tra-an-toan-cong-dau-moi-lien-mac',
    summary:
      'Đoàn công tác Ban Lãnh đạo Công ty kiểm tra thực tế hiện trạng các cụm công trình thủy lợi trọng điểm.',
    publishedAt: '2026-08-18T14:15:00Z',
    viewCount: 650,
    coverAttachmentPublicId: null,
  },
  {
    title: 'Đẩy mạnh chuyển đổi số trong quan trắc thủy văn và giám sát mực nước tự động',
    slug: 'chuyen-doi-so-quan-trac-thuy-van-song-nhue',
    summary:
      'Ứng dụng hệ thống cảm biến SCADA giám sát mực nước và lưu lượng theo thời gian thực.',
    publishedAt: '2026-08-17T16:00:00Z',
    viewCount: 520,
    coverAttachmentPublicId: null,
  },
];

interface HomeHeroFeaturedProps {
  banner?: BannerItem | null;
  articles: ArticleRow[];
}

/**
 * Khối Tiêu điểm Đinh & Lưới Tin Nổi bật (Cột 8/12).
 *
 * - Bài viết đinh (Lead article) với tỷ lệ vàng 16:9, typography đậm nét, trích dẫn ngắn.
 * - Lưới 3 bài tiêu điểm phụ bên dưới với ảnh thumbnail 4:3 bo góc.
 * - Tự động ưu tiên bài từ API thật, bổ sung fallback khi dữ liệu chưa đủ để giao diện luôn sống động.
 */
export function HomeHeroFeatured({ banner, articles }: HomeHeroFeaturedProps) {
  const mergedArticles =
    articles.length >= 4 ? articles : [...articles, ...DEFAULT_HERO_ARTICLES.slice(articles.length)];
  const leadArticle = mergedArticles[0];
  const subArticles = mergedArticles.slice(1, 4);

  const leadCover = leadArticle ? fileUrl(leadArticle.coverAttachmentPublicId) : null;
  const bannerImage = banner ? fileUrl(banner.imageId) : null;

  return (
    <div className="flex flex-col gap-5">
      {/* ───── 1. Bài viết đinh (Lead Article) hoặc Banner Hero ───── */}
      {leadArticle ? (
        <article className="group relative overflow-hidden rounded-xl border border-surface-border bg-white shadow-sm transition-all duration-300 ease-smooth hover:border-brand-primary hover:shadow-md">
          <Link href={ROUTES.article(leadArticle.slug)} className="block">
            <div className="relative aspect-[16/9] w-full overflow-hidden bg-surface-bgLayout sm:aspect-[21/9] lg:aspect-[16/9]">
              {leadCover ? (
                <img
                  src={leadCover}
                  alt={leadArticle.title}
                  loading="eager"
                  decoding="async"
                  className="h-full w-full object-cover transition-transform duration-700 ease-smooth group-hover:scale-105"
                />
              ) : (
                <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-brand-primaryGradientFrom to-brand-primary text-white/30">
                  <svg className="h-16 w-16" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 20H5a2 2 0 01-2-2V6a2 2 0 012-2h10a2 2 0 012 2v1m2 13a2 2 0 01-2-2V7m2 13a2 2 0 002-2V9a2 2 0 00-2-2h-2m-4-3H9M7 16h6M7 8h6v4H7V8z" />
                  </svg>
                </div>
              )}

              {/* Gradient che phủ đáy ảnh */}
              <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 via-black/40 to-transparent p-4 sm:p-6">
                <span className="inline-block rounded bg-brand-primary px-2 py-0.5 text-[11px] font-bold uppercase tracking-wider text-white shadow-xs">
                  Tiêu điểm
                </span>
                <h3 className="mt-2 text-lg font-bold leading-snug text-white drop-shadow-sm transition-colors duration-200 group-hover:text-amber-300 sm:text-xl md:text-2xl">
                  {leadArticle.title}
                </h3>
                {leadArticle.summary ? (
                  <p className="mt-2 hidden line-clamp-2 text-sm text-white/90 drop-shadow-xs sm:block">
                    {leadArticle.summary}
                  </p>
                ) : null}
                <div className="mt-3 flex items-center gap-3 text-xs text-white/75">
                  <time dateTime={leadArticle.publishedAt ?? undefined}>
                    {formatDate(leadArticle.publishedAt)}
                  </time>
                  <span>•</span>
                  <span>{leadArticle.viewCount ?? 0} lượt xem</span>
                </div>
              </div>
            </div>
          </Link>
        </article>
      ) : banner ? (
        <div className="group relative overflow-hidden rounded-xl border border-surface-border bg-white shadow-sm">
          {bannerImage ? (
            <img
              src={bannerImage}
              alt={banner.title}
              className="aspect-[16/9] w-full object-cover transition-transform duration-700 ease-smooth group-hover:scale-105 sm:aspect-[21/9]"
            />
          ) : null}
          <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/80 via-black/30 to-transparent p-6 text-white">
            <h3 className="text-xl font-bold sm:text-2xl">{banner.title}</h3>
            {banner.description ? <p className="mt-1 text-sm text-white/90">{banner.description}</p> : null}
          </div>
        </div>
      ) : null}

      {/* ───── 2. Lưới 3 tin tiêu điểm phụ bên dưới ───── */}
      {subArticles.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          {subArticles.map((article) => {
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
                      <div className="flex h-full w-full items-center justify-center bg-surface-bgLayout text-surface-textSecondary">
                        <svg className="h-6 w-6 text-surface-border" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                      </div>
                    )}
                  </div>
                  <div>
                    <h4 className="line-clamp-2 text-sm font-semibold text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary">
                      {article.title}
                    </h4>
                    <time
                      dateTime={article.publishedAt ?? undefined}
                      className="mt-1.5 block text-xs text-surface-textSecondary"
                    >
                      {formatDate(article.publishedAt)}
                    </time>
                  </div>
                </Link>
              </article>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}
