import Link from 'next/link';

export interface BreadcrumbItem {
  label: string;
  href?: string;
}

interface BreadcrumbProps {
  items: BreadcrumbItem[];
}

/**
 * Thanh điều hướng đường dẫn (Breadcrumbs) chuẩn Cổng thông tin.
 *
 * - Giúp người dùng luôn biết vị trí hiện tại trong cấu trúc cổng.
 * - Tuân thủ accessibility với `aria-label="Đường dẫn trang"`.
 * - Tự động liên kết về Trang chủ ở đầu tiên.
 */
export function Breadcrumb({ items }: BreadcrumbProps) {
  return (
    <nav
      aria-label="Đường dẫn trang"
      className="mb-5 flex items-center text-xs text-surface-textSecondary"
    >
      <ol className="flex flex-wrap items-center gap-1.5 sm:gap-2">
        <li className="flex items-center">
          <Link
            href="/"
            className="flex items-center gap-1 text-surface-textSecondary transition-colors duration-150 hover:text-brand-primary font-medium"
          >
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
              />
            </svg>
            <span>Trang chủ</span>
          </Link>
        </li>

        {items.map((item, index) => {
          const isLast = index === items.length - 1;

          return (
            <li key={item.label} className="flex items-center gap-1.5 sm:gap-2">
              <span className="text-surface-border">/</span>
              {item.href && !isLast ? (
                <Link
                  href={item.href}
                  className="font-medium text-surface-textSecondary transition-colors duration-150 hover:text-brand-primary"
                >
                  {item.label}
                </Link>
              ) : (
                <span className="font-semibold text-brand-primary line-clamp-1 max-w-[200px] sm:max-w-[400px]">
                  {item.label}
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
