import Link from 'next/link';

import type { ArticleRow } from '@/lib/api';
import { formatDate, ROUTES } from '@/lib/routes';
import { EmptyBlock } from './EmptyBlock';
import { SectionTitle } from './SectionTitle';

interface PublishedDocumentsSectionProps {
  documents: ArticleRow[];
  /** Slug danh mục nguồn — `site.home.documents-category`. Dùng cho liên kết "Xem tất cả". */
  categorySlug: string;
  /**
   * Địa chỉ hệ thống văn bản điều hành của Thành phố — `site.external.doc-system-url`.
   *
   * ⛔ Rỗng ⇒ KHÔNG vẽ cột phải, chứ không vẽ một thẻ có nút bấm đi tới `#`. Cổng không đồng bộ
   *   dữ liệu từ hệ thống ấy; đây chỉ là một cánh cửa mở tab mới (CN-01.7).
   */
  docSystemUrl: string;
}

/**
 * Khối **CÔNG BỐ THÔNG TIN** trên trang chủ — CR-16 + CR-17.
 *
 * <h2>Hai việc trong một lượt sửa</h2>
 *
 * <ul>
 *   <li><b>CR-16 — bỏ hẳn cột "Chỉ đạo điều hành".</b> Khối cũ có tên ấy nhưng bên trong là ba
 *       trang tĩnh (Cơ cấu tổ chức, Chức năng nhiệm vụ, Giới thiệu chung); tên khối và nội dung
 *       không nói cùng một chuyện. Nó cũng lấy bài bằng `allArticles.slice(1, 4)` — tức "ba bài
 *       bất kỳ sau bài đầu", một phép chọn không mang nghĩa nghiệp vụ nào.
 *   <li><b>CR-17 — đổi "Văn bản &amp; Quyết định" thành "Công bố thông tin"</b>, đúng tên mục
 *       cấp 1 trong cây nội dung §3, và nay có mục tương ứng trên menu chính.
 * </ul>
 *
 * <h2>⛔ Nội dung đến từ CMS, không từ hệ thống văn bản điều hành</h2>
 *
 * CR-07 chốt: cổng <b>không</b> dựng module văn bản điều hành nội bộ và <b>không</b> đồng bộ
 * dữ liệu từ hệ thống của Thành phố (CN-01.7). Nên khối này liệt kê bài viết thuộc nhánh danh
 * mục "Công bố thông tin" — nội dung do chính biên tập viên của Công ty đăng.
 *
 * Bản trước có bốn văn bản viết cứng <b>kèm số hiệu và người ký</b> (`158/QĐ-SN`, "Chủ tịch
 * Công ty"). Đây là cổng của một doanh nghiệp nhà nước; bịa một quyết định không phải chuyện
 * thẩm mỹ (§10.54).
 */
export function PublishedDocumentsSection({
  documents,
  categorySlug,
  docSystemUrl,
}: PublishedDocumentsSectionProps) {
  return (
    <section className="mt-5">
      <div className="grid grid-cols-1 items-stretch gap-6 lg:grid-cols-12 lg:gap-9">
        <div className="flex flex-col lg:col-span-8">
          <SectionTitle
            href={ROUTES.category(categorySlug)}
            phu={
              <Link
                href={ROUTES.category(categorySlug)}
                className="text-xs font-semibold text-brand-primary hover:underline"
              >
                Xem tất cả ➔
              </Link>
            }
          >
            Công bố thông tin
          </SectionTitle>

          <div className="mt-5 flex-1">
            {documents.length === 0 ? (
              <EmptyBlock>
                Chưa có văn bản nào được công bố. Mục này do biên tập viên của Công ty đăng trong
                nhánh danh mục &ldquo;Công bố thông tin&rdquo;; cổng không đồng bộ dữ liệu từ hệ
                thống văn bản điều hành (CN-01.7).
              </EmptyBlock>
            ) : (
              <div className="grid grid-cols-1 gap-x-8 gap-y-0 sm:grid-cols-2">
                {documents.map((doc) => (
                  <article
                    key={doc.slug}
                    className="group border-b border-surface-border py-3 last:border-b-0"
                  >
                    <Link href={ROUTES.article(doc.slug)} className="flex items-start gap-3">
                      <span
                        aria-hidden="true"
                        className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-primaryLight text-brand-primary transition-colors group-hover:bg-brand-primary group-hover:text-white"
                      >
                        <svg
                          className="h-4 w-4"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={1.75}
                            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                          />
                        </svg>
                      </span>
                      <div className="flex-1">
                        <h3 className="line-clamp-2 text-sm font-medium text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary">
                          {doc.title}
                        </h3>
                        <div className="mt-1 flex flex-wrap items-center gap-2 text-[11px] text-surface-textSecondary">
                          <time dateTime={doc.publishedAt ?? undefined}>
                            {formatDate(doc.publishedAt)}
                          </time>
                          {doc.categories.map((c) => (
                            <span
                              key={c.slug}
                              className="rounded bg-surface-bgLayout px-1.5 py-0.5 font-semibold"
                            >
                              {c.name}
                            </span>
                          ))}
                        </div>
                      </div>
                    </Link>
                  </article>
                ))}
              </div>
            )}
          </div>
        </div>

        {docSystemUrl ? (
          <div className="flex flex-col lg:col-span-4">
            <SectionTitle>Hệ thống văn bản điều hành</SectionTitle>
            <div className="mt-5 flex flex-1 flex-col rounded-lg border border-surface-border bg-surface-bgLayout/60 p-5">
              <svg
                className="h-8 w-8 text-brand-primary"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.8}
                  d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8zM14 2v6h6M9 13h6M9 17h6"
                />
              </svg>
              <p className="mt-3.5 text-[15px] font-semibold leading-snug text-surface-textBase">
                Hệ thống quản lý văn bản điều hành thành phố Hà Nội
              </p>
              <p className="mt-2 text-xs leading-relaxed text-surface-textSecondary">
                Mở ở tab mới. Cổng <b>không</b> đồng bộ dữ liệu — chỉ lưu mã số và tự đăng nhập
                (CN-01.7).
              </p>
              <a
                href={docSystemUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="mt-auto inline-flex items-center gap-2 self-start rounded-lg bg-brand-primary px-4 py-3 text-[13px] font-bold text-white transition-colors hover:bg-brand-primaryHover"
              >
                <span>Truy cập hệ thống</span>
                <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2.5}
                    d="M7 17 17 7M9 7h8v8"
                  />
                </svg>
              </a>
            </div>
          </div>
        ) : null}
      </div>
    </section>
  );
}
