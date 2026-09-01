import Link from 'next/link';

import { PortalImage } from '@/components/PortalImage';
import { ANH_BAI_VIET_MAC_DINH } from '@/lib/anhMacDinh';
import type { ArticleRow } from '@/lib/api';
import { fileUrl, formatDate, ROUTES } from '@/lib/routes';
import { EmptyBlock } from './EmptyBlock';
import { SectionTitle } from './SectionTitle';

export interface KhoiTinChuyenMuc {
  label: string;
  slug: string;
  articles: ArticleRow[];
}

/**
 * Hàng **CHUYÊN MỤC TIN** ngay dưới slider — khối `news-category` của cổng tham chiếu, dựng
 * lại theo bố cục Công ty duyệt 29/08/2026.
 *
 * <h2>⭐ Ba cột BẰNG NHAU, không phải 8/4 như bản vẽ</h2>
 *
 * Bản vẽ đặt một chuyên mục làm khối lớn 8/12 và dồn phần còn lại vào cột 4/12. Cách ấy đòi
 * trả lời được câu <i>chuyên mục nào là chuyên mục chính</i> — và hôm nay không có nguồn nào
 * trả lời được: {@code menu_items.sort_order} <b>không</b> quyết định thứ tự các mục anh em
 * (nợ <b>T26.25</b> — câu lệnh sắp theo {@code path} trước, mà {@code path} là chuỗi id nên
 * thứ tự thật là thứ tự id). Lấy "mục con đầu tiên" làm khối lớn nghĩa là để một id chạy số
 * quyết định bố cục trang chủ, và Công ty kéo thả trên màn hình Menu thì <b>không có gì đổi</b>.
 *
 * <p>Nên ở đây ba ô ngang hàng. Ngày T26.25 được trả, đổi sang 8/4 là một quyết định có cơ sở;
 * làm trước là dựng một thứ trông như cấu hình được mà không cấu hình được (quy tắc 15).
 *
 * <h2>Ô rỗng vẫn là một ô, và nó nói vì sao</h2>
 *
 * Đo trên stack thật ngày 29/08: cả 19 bài đang gắn thẳng vào nhánh cha {@code tin-tuc}, ba
 * chuyên mục con <b>chưa bài nào</b>. Câu lọc của backend so <b>đúng một id danh mục</b>, không
 * gộp nhánh con — nên ba ô này rỗng thật, và chúng nói đúng lý do ấy: bài chưa được gắn chuyên
 * mục con. Mượn bài của ô khác cho "trông có nội dung" là đúng cái bẫy §10.54.
 */
export function HomeCategoryNews({ blocks }: { blocks: KhoiTinChuyenMuc[] }) {
  if (blocks.length === 0) {
    return null;
  }

  return (
    <section className="mt-9" aria-label="Tin theo chuyên mục">
      <div className="grid grid-cols-1 items-stretch gap-6 md:grid-cols-2 lg:grid-cols-3 lg:gap-9">
        {blocks.map((khoi) => (
          <div key={khoi.slug} className="flex h-full flex-col">
            {/* ⚠ KHÔNG truyền `className="text-base"` để thu nhỏ: `SectionTitle` đặt cỡ chữ ở
                thẻ <span> bên trong, nên một lớp cỡ chữ ở thẻ bọc là lớp CHẾT — trông như đã
                chỉnh mà không chỉnh gì (cùng họ với quy tắc 15). Muốn hai cỡ thì thêm một
                prop có thật ở `SectionTitle`; ở đây giữ đúng một nhịp cho mọi tiêu đề khối. */}
            <SectionTitle href={ROUTES.category(khoi.slug)}>{khoi.label}</SectionTitle>

            {khoi.articles.length === 0 ? (
              <div className="mt-5 flex flex-1 items-center">
                <EmptyBlock>
                  Chưa có bài viết nào thuộc chuyên mục này. Bài đã đăng đang gắn ở nhánh cha; ô này
                  hiện lên khi biên tập viên gắn bài vào đúng chuyên mục con.
                </EmptyBlock>
              </div>
            ) : (
              <div className="mt-5 flex flex-1 flex-col">
                {/* Bài đầu: ảnh lớn + tiêu đề — điểm neo mắt của mỗi cột. */}
                <Link href={ROUTES.article(khoi.articles[0].slug)} className="group block">
                  <PortalImage
                    src={fileUrl(khoi.articles[0].coverAttachmentPublicId)}
                    alt=""
                    ratio="aspect-[380/240]"
                    className="rounded-lg"
                    anhMacDinh={ANH_BAI_VIET_MAC_DINH}
                  />
                  <h3 className="mt-3.5 line-clamp-3 text-justify text-[17px] font-bold leading-snug text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary">
                    {khoi.articles[0].title}
                  </h3>
                  <time
                    dateTime={khoi.articles[0].publishedAt ?? undefined}
                    className="mt-2 block text-[11px] text-surface-textSecondary"
                  >
                    {formatDate(khoi.articles[0].publishedAt)}
                  </time>
                </Link>

                {/* Các bài còn lại: ảnh nhỏ 103×68 + chữ, đúng dòng danh sách của cổng tham chiếu. */}
                {khoi.articles.length > 1 ? (
                  <div className="mt-4 flex-1 border-t border-surface-border/60">
                    {khoi.articles.slice(1).map((bai) => (
                      <article key={bai.slug} className="group border-b border-surface-border/60">
                        <Link
                          href={ROUTES.article(bai.slug)}
                          className="flex items-center gap-3.5 py-3"
                        >
                          <PortalImage
                            src={fileUrl(bai.coverAttachmentPublicId)}
                            alt=""
                            ratio="aspect-[103/68]"
                            rong="w-[103px]"
                            className="shrink-0 rounded-md"
                            anhMacDinh={ANH_BAI_VIET_MAC_DINH}
                          />
                          <div className="min-w-0">
                            <h4 className="line-clamp-2 text-justify text-[15px] leading-snug text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary">
                              {bai.title}
                            </h4>
                            <time
                              dateTime={bai.publishedAt ?? undefined}
                              className="mt-1 block text-[11px] text-surface-textSecondary"
                            >
                              {formatDate(bai.publishedAt)}
                            </time>
                          </div>
                        </Link>
                      </article>
                    ))}
                  </div>
                ) : null}
              </div>
            )}

            <div className="mt-4 flex shrink-0 items-center justify-end">
              <Link
                href={ROUTES.category(khoi.slug)}
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
        ))}
      </div>
    </section>
  );
}
