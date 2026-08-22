import Link from 'next/link';

import type { ArticleRow } from '@/lib/api';
import { formatDate, ROUTES } from '@/lib/routes';

interface PortalSidebarProps {
  latestArticles?: ArticleRow[];
  hotline?: string;
}

const DEFAULT_SIDEBAR_NEWS: ArticleRow[] = [
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
];

/**
 * Sidebar dùng chung cho tất cả các trang con (Danh mục, Bài viết, Tìm kiếm).
 *
 * - Khối Tin mới nhất với phân cấp trực quan.
 * - Khối Trực ban PCTT 24/7.
 * - Khối Liên kết dịch vụ công và văn bản quy phạm.
 */
export function PortalSidebar({ latestArticles = [], hotline = '' }: PortalSidebarProps) {
  const displayNews =
    latestArticles.length >= 5
      ? latestArticles.slice(0, 5)
      : [...latestArticles, ...DEFAULT_SIDEBAR_NEWS.slice(latestArticles.length)].slice(0, 5);

  return (
    <aside className="flex flex-col gap-6">
      {/* ───── 1. Khối Trực ban PCTT & Khẩn cấp ───── */}
      <div className="overflow-hidden rounded-xl border border-red-200 bg-gradient-to-br from-red-50 via-white to-red-50/50 p-4 shadow-xs">
        <div className="flex items-center gap-2">
          <span className="flex h-3 w-3 rounded-full bg-red-600 animate-pulse" />
          <h3 className="text-xs font-black uppercase tracking-wider text-red-900">
            Trực ban PCTT 24/7
          </h3>
        </div>
        <p className="mt-1.5 text-xs text-red-800/90 leading-relaxed">
          Tiếp nhận tin báo sự cố ngập úng, mực nước và thiên tai thủy lợi lưu vực Sông Nhuệ:
        </p>
        {hotline ? (
          <a
            href={`tel:${hotline.replace(/\D/g, '')}`}
            className="mt-3 flex items-center justify-center gap-2 rounded-lg bg-red-600 px-4 py-2 text-xs font-bold text-white shadow-xs transition-colors hover:bg-red-700"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"
              />
            </svg>
            <span>{hotline}</span>
          </a>
        ) : (
          <div className="mt-2 text-xs font-bold text-red-800">Hotline: (024) 3382 4586</div>
        )}
      </div>

      {/* ───── 2. Khối Tin mới nhận / Dòng thời sự ───── */}
      <div className="rounded-xl border border-surface-border bg-white p-5 shadow-xs">
        <div className="flex items-center justify-between border-b border-surface-border pb-3">
          <div className="flex items-center gap-2">
            <span className="h-4 w-1.5 rounded-full bg-brand-primary" />
            <h3 className="text-sm font-bold uppercase tracking-tight text-surface-textBase">
              Tin mới nhận
            </h3>
          </div>
          <Link
            href={ROUTES.search}
            className="text-xs font-semibold text-brand-primary hover:underline"
          >
            Tất cả ➔
          </Link>
        </div>

        <div className="mt-3 divide-y divide-surface-border">
          {displayNews.map((art, idx) => (
            <article key={art.slug} className="group py-3 first:pt-1 last:pb-1">
              <Link href={ROUTES.article(art.slug)} className="flex items-start gap-2.5">
                <span
                  className={`mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded text-[10px] font-bold ${
                    idx === 0
                      ? 'bg-red-600 text-white'
                      : idx < 3
                        ? 'bg-brand-primary text-white'
                        : 'bg-surface-bgLayout text-surface-textSecondary'
                  }`}
                >
                  {idx + 1}
                </span>
                <div className="flex-1">
                  <h4 className="line-clamp-2 text-xs font-medium text-surface-textBase transition-colors duration-150 group-hover:text-brand-primary">
                    {art.title}
                  </h4>
                  <time
                    dateTime={art.publishedAt ?? undefined}
                    className="mt-1 block text-[10px] text-surface-textSecondary"
                  >
                    {formatDate(art.publishedAt)}
                  </time>
                </div>
              </Link>
            </article>
          ))}
        </div>
      </div>

      {/* ───── 3. Khối Truy cập nhanh Dịch vụ & Văn bản ───── */}
      <div className="rounded-xl border border-surface-border bg-white p-5 shadow-xs">
        <div className="flex items-center gap-2 border-b border-surface-border pb-3">
          <span className="h-4 w-1.5 rounded-full bg-brand-primary" />
          <h3 className="text-sm font-bold uppercase tracking-tight text-surface-textBase">
            Tra cứu & Dịch vụ
          </h3>
        </div>

        <ul className="mt-3.5 space-y-2 text-xs">
          <li>
            <Link
              href="http://songnhue.bhh40.net"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center justify-between rounded-lg border border-surface-border p-2.5 font-semibold text-surface-textBase transition-colors hover:border-brand-primary hover:bg-brand-primaryLight hover:text-brand-primary"
            >
              <span>Hệ thống Văn bản Điều hành</span>
              <span>↗</span>
            </Link>
          </li>
          <li>
            <Link
              href="/bai-viet/lien-he"
              className="flex items-center justify-between rounded-lg border border-surface-border p-2.5 font-semibold text-surface-textBase transition-colors hover:border-brand-primary hover:bg-brand-primaryLight hover:text-brand-primary"
            >
              <span>Gửi Phản ánh & Kiến nghị</span>
              <span>→</span>
            </Link>
          </li>
          <li>
            <Link
              href="/danh-muc/thong-bao"
              className="flex items-center justify-between rounded-lg border border-surface-border p-2.5 font-semibold text-surface-textBase transition-colors hover:border-brand-primary hover:bg-brand-primaryLight hover:text-brand-primary"
            >
              <span>Lịch Vận hành Cống & Xả lũ</span>
              <span>→</span>
            </Link>
          </li>
        </ul>
      </div>
    </aside>
  );
}
