import Link from 'next/link';

import { DanhDauTuKhoa } from '@/components/DanhDauTuKhoa';
import { PortalImage } from '@/components/PortalImage';
import { ANH_BAI_VIET_MAC_DINH } from '@/lib/anhMacDinh';
import type { ArticleRow } from '@/lib/api';
import { fileUrl, formatDate, ROUTES } from '@/lib/routes';

/**
 * Thẻ một bài viết trong danh sách.
 *
 * ⚠ Ảnh dùng thẻ `<img>` thường, **không** phải `next/image` — quyết định ở
 * `architecture-review.md` §10.9: bộ tối ưu của Next đòi `sharp`, và ảnh của hệ này còn
 * phải hiển thị được ở `admin-app` (Vite) nên không đi qua Next. Bù lại bằng `loading="lazy"`
 * và khung kích thước cố định: thiếu hai thứ đó thì mở một trang 20 bài là tải về vài chục
 * MB ảnh gốc, và bố cục nhảy khi ảnh về (T12.7 hoãn → nợ #62).
 */
/**
 * ⚠ `tuKhoa` chỉ có ở trang Tìm kiếm — CN-01.8 / T36.10.
 *
 * ⛔ Mặc định `undefined` ⇒ `DanhDauTuKhoa` trả về nguyên văn một đoạn, ⛔ không đoạn nào tô. Nhờ
 * vậy mọi nơi gọi khác (trang chủ, trang danh mục) ⛔ không phải đổi một dòng nào, và ⛔ không có
 * nhánh "có tô / ⛔ không tô" nào để ai đó quên.
 */
export function ArticleCard({ article, tuKhoa }: { article: ArticleRow; tuKhoa?: string }) {
  const cover = fileUrl(article.coverAttachmentPublicId);

  return (
    <article className="group flex flex-col overflow-hidden rounded-xl border border-surface-border bg-white shadow-xs transition-all duration-300 ease-smooth hover:-translate-y-1 hover:border-brand-primary hover:shadow-md">
      <Link href={ROUTES.article(article.slug)} className="flex flex-1 flex-col">
        <PortalImage
          src={cover}
          alt={article.title}
          ratio="aspect-[16/10]"
          anhMacDinh={ANH_BAI_VIET_MAC_DINH}
        />
        <div className="flex flex-1 flex-col p-4 sm:p-5">
          <h3 className="line-clamp-2 text-justify text-sm font-bold leading-snug text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary sm:text-base">
            <DanhDauTuKhoa chu={article.title} tuKhoa={tuKhoa} />
          </h3>
          {article.summary ? (
            <p className="mt-2 line-clamp-2 text-justify text-xs text-surface-textSecondary leading-relaxed sm:text-sm">
              <DanhDauTuKhoa chu={article.summary} tuKhoa={tuKhoa} />
            </p>
          ) : null}
          <div className="mt-auto pt-3 flex items-center justify-between border-t border-surface-border/60 text-[11px] text-surface-textSecondary">
            <time dateTime={article.publishedAt ?? undefined}>
              {formatDate(article.publishedAt)}
            </time>
            {article.viewCount !== undefined && article.viewCount > 0 ? (
              <span>👁 {article.viewCount}</span>
            ) : null}
          </div>
        </div>
      </Link>
    </article>
  );
}
