import Link from 'next/link';

import type { ArticleRow } from '@/lib/api';
import { fileUrl, formatDate, ROUTES } from '@/lib/routes';
import { EmptyBlock } from './EmptyBlock';

interface DocumentItem {
  id: string;
  code: string;
  title: string;
  issuedDate: string;
  signer: string;
  type: string;
  fileType: 'pdf' | 'doc';
  downloadUrl: string;
}

interface DirectiveDocumentsSectionProps {
  directiveArticles?: ArticleRow[];
  documents?: DocumentItem[];
}

/**
 * Khối Chỉ đạo Điều hành & Văn bản Quy phạm (Directives & Regulations Section).
 *
 * - Cột 1 (6/12): Hoạt động Chỉ đạo điều hành từ Ban Lãnh đạo Công ty.
 * - Cột 2 (6/12): Hệ thống Văn bản, Quyết định vận hành kèm liên kết tải file.
 */
export function DirectiveDocumentsSection({
  directiveArticles = [],
  documents = [],
}: DirectiveDocumentsSectionProps) {
  // ⛔ Bản trước ghép thêm ba bài viết cứng cho đủ 3 ô, và cột văn bản mặc định là bốn văn bản
  //    viết cứng — CÓ SỐ HIỆU và NGƯỜI KÝ (`158/QĐ-SN`, "Chủ tịch Công ty"). Đây là cổng của
  //    một doanh nghiệp nhà nước; bịa một quyết định không phải chuyện thẩm mỹ.
  const leadArticle = directiveArticles[0];
  const subArticles = directiveArticles.slice(1, 3);
  const cover = leadArticle ? fileUrl(leadArticle.coverAttachmentPublicId) : null;

  return (
    <section className="mt-10 sm:mt-14">
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-12 lg:gap-8">
        {/* ───── CỘT 1: CHỈ ĐẠO ĐIỀU HÀNH (6 Cột) ───── */}
        <div className="flex flex-col lg:col-span-6">
          <div className="flex items-center justify-between border-b-2 border-brand-primary pb-2.5">
            <div className="flex items-center gap-2">
              <span className="h-5 w-1.5 rounded-full bg-brand-primary"></span>
              <h2 className="text-base font-bold uppercase tracking-tight text-surface-textBase sm:text-lg">
                Chỉ đạo Điều hành
              </h2>
            </div>
            <Link
              href={ROUTES.search}
              className="text-xs font-semibold text-brand-primary hover:underline"
            >
              Xem thêm ➔
            </Link>
          </div>

          <div className="mt-4 flex flex-1 flex-col gap-4">
            {leadArticle ? (
              <article className="group flex flex-col overflow-hidden rounded-lg border border-surface-border bg-white p-3.5 shadow-xs transition-all duration-300 ease-smooth hover:border-brand-primary hover:shadow-md">
                <Link href={ROUTES.article(leadArticle.slug)} className="flex flex-col gap-3">
                  {cover ? (
                    <div className="aspect-[16/9] w-full overflow-hidden rounded-md bg-surface-bgLayout">
                      <img
                        src={cover}
                        alt=""
                        loading="lazy"
                        className="h-full w-full object-cover transition-transform duration-500 ease-smooth group-hover:scale-105"
                      />
                    </div>
                  ) : null}
                  <div>
                    <h3 className="text-sm font-bold text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary sm:text-base">
                      {leadArticle.title}
                    </h3>
                    {leadArticle.summary ? (
                      <p className="mt-1.5 line-clamp-2 text-xs text-surface-textSecondary">
                        {leadArticle.summary}
                      </p>
                    ) : null}
                    <time
                      dateTime={leadArticle.publishedAt ?? undefined}
                      className="mt-2 block text-[11px] text-surface-textSecondary"
                    >
                      {formatDate(leadArticle.publishedAt)}
                    </time>
                  </div>
                </Link>
              </article>
            ) : (
              <div className="rounded-lg border border-dashed border-surface-border p-6 text-center text-xs text-surface-textSecondary">
                Thông tin chỉ đạo điều hành đang được cập nhật.
              </div>
            )}

            {/* Các tin phụ ngắn */}
            {subArticles.map((art) => (
              <article
                key={art.slug}
                className="group rounded-lg border border-surface-border bg-white p-3 transition-all duration-200 hover:border-brand-primary"
              >
                <Link href={ROUTES.article(art.slug)} className="flex flex-col">
                  <h4 className="line-clamp-2 text-xs font-semibold text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary sm:text-sm">
                    {art.title}
                  </h4>
                  <time
                    dateTime={art.publishedAt ?? undefined}
                    className="mt-1 text-[11px] text-surface-textSecondary"
                  >
                    {formatDate(art.publishedAt)}
                  </time>
                </Link>
              </article>
            ))}
          </div>
        </div>

        {/* ───── CỘT 2: VĂN BẢN & QUYẾT ĐỊNH (6 Cột) ───── */}
        <div className="flex flex-col lg:col-span-6">
          <div className="flex items-center justify-between border-b-2 border-brand-primary pb-2.5">
            <div className="flex items-center gap-2">
              <span className="h-5 w-1.5 rounded-full bg-brand-primary"></span>
              <h2 className="text-base font-bold uppercase tracking-tight text-surface-textBase sm:text-lg">
                Văn bản & Quyết định
              </h2>
            </div>
            <a
              href="http://songnhue.bhh40.net"
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs font-semibold text-brand-primary hover:underline"
            >
              Hệ thống VBĐT ↗
            </a>
          </div>

          <div className="mt-4 flex flex-1 flex-col divide-y divide-surface-border rounded-xl border border-surface-border bg-white p-4 shadow-xs">
            {documents.length === 0 ? (
              <EmptyBlock>
                Chưa có văn bản nào. Hệ thống văn bản điều hành là hệ thống riêng — cổng chỉ
                liên kết sang, không đồng bộ dữ liệu (CN-01.7).
              </EmptyBlock>
            ) : null}
            {documents.map((doc) => (
              <div key={doc.id} className="group py-3 first:pt-0 last:pb-0">
                <a
                  href={doc.downloadUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-start gap-3"
                >
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-red-50 text-red-700 group-hover:bg-red-100">
                    <span className="text-[10px] font-extrabold uppercase">{doc.fileType}</span>
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="rounded bg-brand-primaryLight px-1.5 py-0.5 text-[10px] font-bold text-brand-primary">
                        {doc.code}
                      </span>
                      <span className="text-[11px] text-surface-textSecondary">
                        {doc.issuedDate}
                      </span>
                    </div>
                    <h3 className="mt-1 line-clamp-2 text-xs font-medium text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary sm:text-sm">
                      {doc.title}
                    </h3>
                  </div>
                </a>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
