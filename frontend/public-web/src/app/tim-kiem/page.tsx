import type { Metadata } from 'next';

import { ArticleList } from '@/components/ArticleList';
import { getArticles } from '@/lib/api';
import { ROUTES } from '@/lib/routes';

/**
 * Tìm kiếm bài viết — CN-01.8 phần công khai.
 *
 * ⚠ `revalidate` vẫn có tác dụng vì mỗi bộ tham số truy vấn là một bản cache riêng: người
 * dùng gõ đi gõ lại cùng một từ khoá thì lượt sau lấy từ bộ đệm. Từ khoá lạ thì dựng mới,
 * và hạn mức của nhóm công khai chặn việc dùng ô tìm kiếm để quét cả CSDL.
 */
/**
 * ⚠ Số viết thẳng, KHÔNG import hằng số: Next đọc `export const revalidate` bằng phân tích
 * tĩnh và từ chối build nếu giá trị không phải literal ("Invalid segment configuration
 * export"). `REVALIDATE_SECONDS` ở `lib/api.ts` phải bằng đúng con số này —
 * `revalidate-config.test.ts` canh việc đó.
 */
export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Tìm kiếm',
  // Trang kết quả tìm kiếm không nên nằm trong chỉ mục: nó sinh vô số URL cùng nội dung.
  robots: { index: false, follow: true },
};

export default async function SearchPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; page?: string }>;
}) {
  const { q, page } = await searchParams;
  const tuKhoa = (q ?? '').trim();
  const ketQua = await getArticles({ q: tuKhoa || undefined, page: Number(page ?? 0) });

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="text-2xl font-bold text-surface-textBase">
        {tuKhoa ? `Kết quả cho "${tuKhoa}"` : 'Tất cả bài viết'}
      </h1>

      {/* Biểu mẫu GET thật: kết quả có URL riêng, chia sẻ được, quay lại được bằng nút Back. */}
      <form action={ROUTES.search} method="get" className="mt-4 flex max-w-xl gap-2">
        <label htmlFor="q" className="sr-only">
          Từ khoá
        </label>
        <input
          id="q"
          name="q"
          type="search"
          defaultValue={tuKhoa}
          placeholder="Nhập từ khoá — gõ không dấu vẫn tìm được"
          className="flex-1 rounded border border-surface-border px-3 py-2"
        />
        <button
          type="submit"
          className="rounded bg-brand-primary px-4 py-2 text-white hover:opacity-90"
        >
          Tìm
        </button>
      </form>

      <p className="mt-3 text-sm text-surface-textSecondary">
        {ketQua ? `${ketQua.totalElements} bài viết` : ''}
      </p>

      <div className="mt-6">
        <ArticleList
          page={ketQua}
          basePath={ROUTES.search}
          extraQuery={tuKhoa ? `q=${encodeURIComponent(tuKhoa)}` : ''}
          emptyText="Không tìm thấy bài viết nào khớp với từ khoá."
        />
      </div>
    </div>
  );
}
