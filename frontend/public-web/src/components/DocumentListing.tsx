import Link from 'next/link';

import { DocumentTable } from '@/components/DocumentTable';
import { EmptyBlock } from '@/components/home/EmptyBlock';
import type { PagedArticles } from '@/lib/api';

interface DocumentListingProps {
  page: PagedArticles | null;
  /** Đường dẫn gốc để dựng liên kết phân trang — `ROUTES.category(slug)`. */
  basePath: string;
  /** Từ khoá đang lọc, để ô nhập giữ lại thứ người dùng vừa gõ. */
  tuKhoa: string;
  /** Tên chuyên mục — dùng trong câu nói khi rỗng. */
  tenChuyenMuc: string;
}

/**
 * Trang danh sách văn bản đầy đủ — ô tìm kiếm + bảng + phân trang.
 *
 * <h2>Nguồn thiết kế: `thuyloisongday.vn/van-ban` (đo 01/09/2026)</h2>
 *
 * <pre>
 *   .search-box   padding 20px · viền 1px #E2E7ED · nền #F3F6FB
 *   .textbox      width calc(100% - 140px) · ô nhập cao 38px · bo 4px · padding 5px 15px
 *   .search-btn   nút 120×38 · nền #2A58A0 · chữ trắng · bo 4px
 *   .tr-pager     ô 40×40 · bo 3px · viền 1px #E2E7ED · trang hiện tại nền #2A58A0 chữ trắng
 * </pre>
 *
 * Mã màu ánh xạ sang `design-tokens` — xem javadoc {@link DocumentTable}.
 *
 * <h2>⭐ Tìm kiếm bằng BIỂU MẪU GET, không bằng JavaScript</h2>
 *
 * Cổng tham chiếu gán `window.location.href = ...` trong một `onclick`. Ở đây là một
 * `&lt;form method="get"&gt;` thật, cùng lý do `ArticleList` phân trang bằng liên kết thật: trang này
 * dựng ở máy chủ (ISR), nên tìm kiếm phải chạy được **khi JavaScript chưa tải xong hoặc bị chặn**
 * — và kết quả tìm kiếm có địa chỉ riêng để chia sẻ, đánh dấu và cho công cụ tìm kiếm lần theo.
 *
 * <p>⚠ Tham số là `q`, đúng tên mà `getArticles` và `/api/v1/public/articles` đã nhận sẵn. Đặt một
 * tên khác (kiểu `tu-khoa` của cổng tham chiếu) là dựng một chỗ thứ hai phải nhớ ánh xạ.
 */
export function DocumentListing({ page, basePath, tuKhoa, tenChuyenMuc }: DocumentListingProps) {
  const trang = page?.number ?? 0;
  const tongTrang = page?.totalPages ?? 0;
  const lienKetTrang = (dich: number) =>
    `${basePath}?${tuKhoa ? `q=${encodeURIComponent(tuKhoa)}&` : ''}page=${dich}`;

  return (
    <div className="space-y-5">
      <form
        action={basePath}
        method="get"
        role="search"
        className="rounded-lg border border-surface-border bg-surface-bgLayout p-5 shadow-2xs"
      >
        <h2 className="text-[15px] font-bold text-surface-textBase">Tìm kiếm</h2>
        <div className="mt-3 flex flex-col gap-3 sm:flex-row">
          <label htmlFor="tim-van-ban" className="sr-only">
            Tìm kiếm văn bản theo từ khoá
          </label>
          <input
            id="tim-van-ban"
            name="q"
            type="search"
            defaultValue={tuKhoa}
            placeholder="Tìm kiếm với từ khoá"
            className="h-[38px] flex-1 rounded border border-surface-border bg-white px-[15px] text-sm text-surface-textBase outline-none placeholder:text-surface-textSecondary focus-visible:border-brand-primary"
          />
          <button
            type="submit"
            className="h-[38px] w-full shrink-0 rounded bg-brand-primary text-sm font-medium text-white transition-colors hover:bg-brand-primaryHover sm:w-[120px]"
          >
            Tìm kiếm
          </button>
        </div>
      </form>

      <DocumentTable
        documents={page?.content ?? []}
        khiRong={
          <EmptyBlock>
            {tuKhoa
              ? `Không có văn bản nào khớp từ khoá “${tuKhoa}” trong ${tenChuyenMuc}.`
              : `Chưa có văn bản nào trong ${tenChuyenMuc}. Mục này do biên tập viên của Công ty đăng; cổng không đồng bộ dữ liệu từ hệ thống văn bản điều hành (CN-01.7).`}
          </EmptyBlock>
        }
      />

      {/* Phân trang — ô 40×40 của cổng tham chiếu. Liên kết thật, không nút JavaScript. */}
      {tongTrang > 1 ? (
        <nav
          aria-label="Phân trang văn bản"
          className="flex flex-wrap items-center justify-center gap-2"
        >
          {trang > 0 ? (
            <Link href={lienKetTrang(trang - 1)} className={LOP_O_TRANG} aria-label="Trang trước">
              ‹
            </Link>
          ) : null}
          <span
            className={`${LOP_O_TRANG} border-brand-primary bg-brand-primary font-semibold text-white`}
          >
            {trang + 1}
          </span>
          <span className="text-sm text-surface-textSecondary">/ {tongTrang}</span>
          {trang + 1 < tongTrang ? (
            <Link href={lienKetTrang(trang + 1)} className={LOP_O_TRANG} aria-label="Trang sau">
              ›
            </Link>
          ) : null}
        </nav>
      ) : null}
    </div>
  );
}

/** Ô phân trang 40×40 — một chuỗi, dùng cho cả liên kết lẫn ô trang hiện tại. */
const LOP_O_TRANG =
  'flex h-10 w-10 items-center justify-center rounded border border-surface-border bg-surface-bgLayout text-sm text-surface-textSecondary transition-colors hover:border-brand-primary hover:text-brand-primary';
