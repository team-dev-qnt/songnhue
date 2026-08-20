import Link from 'next/link';

import { getMenu, getSiteConfig } from '@/lib/api';
import { buildMenuTree, fileUrl, isExternal, menuHref, ROUTES } from '@/lib/routes';
import { SITE } from '@/lib/site';

/**
 * Đầu trang — điều hướng và logo lấy từ CMS (T16.1).
 *
 * Là Server Component nên dữ liệu được nạp lúc dựng trang tĩnh: người xem không phải chờ
 * một lượt gọi API để thấy menu, và menu không "nhảy vào" sau khi trang đã hiện.
 *
 * ⚠ Backend không gọi được thì **vẫn phải ra một đầu trang dùng được**. Cổng thông tin
 * trắng trang vì API hắt hơi là đổi một sự cố nhỏ lấy một sự cố lớn.
 */
export async function SiteHeader() {
  const [config, menu] = await Promise.all([getSiteConfig(), getMenu('HEADER')]);

  const siteName = config?.['site.name'] ?? SITE.name;
  const shortName = config?.['site.slogan'] || SITE.shortName;
  const logo = fileUrl(config?.['site.logo.attachment-id']);
  const tree = buildMenuTree(menu ?? []);

  return (
    <header className="border-b border-surface-border bg-white">
      <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
        <Link href={ROUTES.home} className="flex items-center gap-3">
          {logo ? (
            <img src={logo} alt="" className="h-10 w-auto" />
          ) : (
            <span
              aria-hidden
              className="flex h-10 w-10 items-center justify-center rounded bg-brand-primary text-sm font-bold text-white"
            >
              SN
            </span>
          )}
          <span className="flex flex-col leading-tight">
            <span className="text-base font-semibold text-surface-textBase">{siteName}</span>
            <span className="text-xs text-surface-textSecondary">{shortName}</span>
          </span>
        </Link>

        <nav aria-label="Điều hướng chính">
          <ul className="flex flex-wrap gap-x-5 gap-y-2 text-sm">
            {tree.map(({ item, children }) => {
              const href = menuHref(item);
              return (
                <li key={`${item.label}-${item.depth}`} className="relative group">
                  {href ? (
                    <Link
                      href={href}
                      target={item.openNewTab ? '_blank' : undefined}
                      /* noopener chặn trang đích chiếm quyền điều khiển tab gốc qua window.opener */
                      rel={isExternal(item) ? 'noopener noreferrer' : undefined}
                      className="text-surface-textSecondary hover:text-brand-primary"
                    >
                      {item.label}
                    </Link>
                  ) : (
                    <span className="cursor-default text-surface-textSecondary">{item.label}</span>
                  )}

                  {children.length > 0 ? (
                    <ul className="absolute left-0 top-full z-20 hidden min-w-48 rounded border border-surface-border bg-white py-1 shadow-lg group-hover:block group-focus-within:block">
                      {children.map((child) => {
                        const childHref = menuHref(child);
                        return (
                          <li key={child.label}>
                            {childHref ? (
                              <Link
                                href={childHref}
                                className="block px-3 py-1.5 text-surface-textSecondary hover:bg-surface-bgLayout hover:text-brand-primary"
                              >
                                {child.label}
                              </Link>
                            ) : null}
                          </li>
                        );
                      })}
                    </ul>
                  ) : null}
                </li>
              );
            })}
            <li>
              <Link
                href={ROUTES.search}
                className="text-surface-textSecondary hover:text-brand-primary"
              >
                Tìm kiếm
              </Link>
            </li>
          </ul>
        </nav>
      </div>
    </header>
  );
}
