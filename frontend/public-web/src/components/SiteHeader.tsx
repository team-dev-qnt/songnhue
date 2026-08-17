import Link from 'next/link';

import { NAV_ITEMS, SITE } from '@/lib/site';

/** Đầu trang công khai. Phase 1 thay `NAV_ITEMS` bằng danh mục lấy từ CMS. */
export function SiteHeader() {
  return (
    <header className="border-b border-surface-border bg-white">
      <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
        <Link href="/" className="flex items-center gap-3">
          <span
            aria-hidden
            className="flex h-10 w-10 items-center justify-center rounded bg-brand-primary text-sm font-bold text-white"
          >
            SN
          </span>
          <span className="flex flex-col leading-tight">
            <span className="text-base font-semibold text-surface-textBase">{SITE.shortName}</span>
            <span className="text-xs text-surface-textSecondary">Cổng thông tin điện tử</span>
          </span>
        </Link>

        <nav aria-label="Điều hướng chính">
          <ul className="flex flex-wrap gap-x-5 gap-y-2 text-sm">
            {NAV_ITEMS.map((item) => (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className="text-surface-textSecondary hover:text-brand-primary"
                >
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      </div>
    </header>
  );
}
