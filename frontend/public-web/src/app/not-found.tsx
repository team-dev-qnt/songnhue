import Link from 'next/link';

import { getSiteConfig } from '@/lib/api';
import { ROUTES } from '@/lib/routes';

/**
 * Trang 404 — nội dung lấy từ cấu hình giao diện (T15.4, T16.7).
 *
 * Công ty sửa được câu chữ qua màn hình cấu hình, không phải mở phiếu yêu cầu deploy. Thiếu
 * cấu hình thì rơi về câu mặc định — trang 404 trống rỗng còn tệ hơn một câu chung chung.
 */
export default async function NotFound() {
  const config = await getSiteConfig();

  const title = config?.['site.page.404.title'] || 'Không tìm thấy trang';
  const message =
    config?.['site.page.404.message'] ||
    'Trang bạn tìm không còn tồn tại hoặc đã được chuyển sang địa chỉ khác.';

  return (
    <div className="mx-auto max-w-2xl animate-fade-in px-4 py-20 text-center">
      <p className="bg-gradient-to-r from-brand-primaryGradientFrom to-brand-primaryGradientTo bg-clip-text text-6xl font-black text-transparent">
        404
      </p>
      <h1 className="mt-4 text-2xl font-bold text-surface-textBase">{title}</h1>
      <p className="mt-3 text-surface-textSecondary">{message}</p>
      <div className="mt-8 flex justify-center gap-3">
        <Link
          href={ROUTES.home}
          className="rounded-lg bg-brand-primary px-5 py-2.5 font-medium text-white shadow-sm transition-all duration-200 ease-smooth hover:-translate-y-0.5 hover:shadow-md"
        >
          Về trang chủ
        </Link>
        <Link
          href={ROUTES.search}
          className="rounded-lg border border-surface-border px-5 py-2.5 font-medium transition-all duration-200 ease-smooth hover:-translate-y-0.5 hover:border-brand-primary hover:shadow-sm"
        >
          Tìm kiếm
        </Link>
      </div>
    </div>
  );
}
