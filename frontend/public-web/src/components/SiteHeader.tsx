import Link from 'next/link';

import type { MenuLink } from '@/lib/api';
import { getMenu, getSiteConfig } from '@/lib/api';
import { buildMenuTree, fileUrl, isExternal, menuHref, ROUTES } from '@/lib/routes';
import { SITE } from '@/lib/site';

/**
 * Đầu trang cổng thông tin — T16.1.
 *
 * Cấu trúc 2 tầng chuẩn cổng thông tin cơ quan/doanh nghiệp nhà nước:
 * 1. Dải nhận diện thương hiệu (Brand Masthead): Logo + Tên đầy đủ cơ quan + Slogan + Thông tin liên hệ
 * 2. Thanh điều hướng chính (Main Navbar): Xanh thương hiệu gradient, sticky top, menu đa cấp dropdown, tìm kiếm
 */
const DEFAULT_HEADER_MENU: MenuLink[] = [
  {
    label: 'Trang chủ',
    linkType: 'URL',
    url: '/',
    categorySlug: null,
    articleSlug: null,
    openNewTab: false,
    depth: 0,
    parentLabel: null,
  },
  {
    label: 'Giới thiệu',
    linkType: 'CATEGORY',
    url: null,
    categorySlug: 'gioi-thieu',
    articleSlug: null,
    openNewTab: false,
    depth: 0,
    parentLabel: null,
  },
  {
    label: 'Tin tức',
    linkType: 'CATEGORY',
    url: null,
    categorySlug: 'tin-tuc',
    articleSlug: null,
    openNewTab: false,
    depth: 0,
    parentLabel: null,
  },
  {
    label: 'Thông báo',
    linkType: 'CATEGORY',
    url: null,
    categorySlug: 'thong-bao',
    articleSlug: null,
    openNewTab: false,
    depth: 0,
    parentLabel: null,
  },
  {
    label: 'Văn bản điều hành',
    linkType: 'URL',
    url: 'http://songnhue.bhh40.net',
    categorySlug: null,
    articleSlug: null,
    openNewTab: true,
    depth: 0,
    parentLabel: null,
  },
  {
    label: 'Liên hệ',
    linkType: 'ARTICLE',
    url: null,
    categorySlug: null,
    articleSlug: 'lien-he',
    openNewTab: false,
    depth: 0,
    parentLabel: null,
  },
];

export async function SiteHeader() {
  const [config, menu] = await Promise.all([getSiteConfig(), getMenu('HEADER')]);

  const siteName = config?.['site.name'] ?? SITE.name;
  const shortName = config?.['site.slogan'] || SITE.shortName;
  const logo = fileUrl(config?.['site.logo.attachment-id']);
  const activeMenu = menu && menu.length > 0 ? menu : DEFAULT_HEADER_MENU;
  const tree = buildMenuTree(activeMenu);

  return (
    <>
      {/* ───── Tầng 1: Dải nhận diện thương hiệu (Full Brand Blue Masthead) ───── */}
      <div className="w-full border-b border-white/10 bg-gradient-to-r from-brand-primaryGradientFrom via-brand-primary to-brand-primaryGradientFrom text-white shadow-xs">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3.5 sm:py-4">
          {/* Logo & Tên cơ quan */}
          <Link href={ROUTES.home} className="group flex items-center gap-3.5 sm:gap-4">
            {logo ? (
              <img
                src={logo}
                alt={siteName}
                className="h-12 w-auto object-contain transition-transform duration-300 ease-smooth group-hover:scale-105 sm:h-14"
              />
            ) : (
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-white text-base font-extrabold text-brand-primary shadow-md transition-all duration-300 ease-smooth group-hover:scale-105 sm:h-13 sm:w-13 sm:text-lg">
                SN
              </div>
            )}
            <div className="flex flex-col">
              <span className="text-sm font-bold uppercase tracking-tight text-white drop-shadow-xs transition-colors duration-200 sm:text-base md:text-lg">
                {siteName}
              </span>
              <span className="text-xs font-medium text-white/80 sm:text-xs">{shortName}</span>
            </div>
          </Link>

          {/* Hotline / Thông tin nhanh bên phải (ẩn trên màn hình nhỏ) */}
          <div className="hidden items-center gap-4 text-xs text-white md:flex">
            <div className="flex items-center gap-1.5 rounded-full border border-white/20 bg-white/10 px-3.5 py-1.5 backdrop-blur-xs">
              <svg
                className="h-3.5 w-3.5 text-amber-300"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"
                />
              </svg>
              <span>
                Hotline: <strong className="font-bold text-amber-300">(024) 3382 4586</strong>
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* ───── Tầng 2: Thanh điều hướng chính (Sticky Navigation Bar) ───── */}
      <nav
        aria-label="Điều hướng chính"
        className="sticky top-0 z-40 w-full border-b border-black/10 bg-[#0038a8]/95 text-white shadow-md backdrop-blur-md"
      >
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4">
          <ul className="flex flex-wrap items-center gap-1 text-sm font-medium">
            {tree.map(({ item, children }) => {
              const href = menuHref(item);
              const hasSubmenu = children.length > 0;

              return (
                <li key={`${item.label}-${item.depth}`} className="relative group">
                  {href ? (
                    <Link
                      href={href}
                      target={item.openNewTab ? '_blank' : undefined}
                      rel={isExternal(item) ? 'noopener noreferrer' : undefined}
                      className="flex items-center gap-1 px-3 py-2.5 transition-all duration-200 ease-smooth hover:bg-white/20 hover:text-white sm:px-3.5 sm:py-3"
                    >
                      <span>{item.label}</span>
                      {hasSubmenu && (
                        <svg
                          className="h-3 w-3 opacity-80 transition-transform duration-200 group-hover:rotate-180"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2.5}
                            d="M19 9l-7 7-7-7"
                          />
                        </svg>
                      )}
                    </Link>
                  ) : (
                    <button
                      type="button"
                      className="flex items-center gap-1 px-3 py-2.5 transition-all duration-200 ease-smooth hover:bg-white/20 hover:text-white sm:px-3.5 sm:py-3"
                    >
                      <span>{item.label}</span>
                      {hasSubmenu && (
                        <svg
                          className="h-3 w-3 opacity-80 transition-transform duration-200 group-hover:rotate-180"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2.5}
                            d="M19 9l-7 7-7-7"
                          />
                        </svg>
                      )}
                    </button>
                  )}

                  {/* Dropdown submenu */}
                  {hasSubmenu && (
                    <ul className="invisible absolute left-0 top-full z-50 min-w-56 translate-y-2 rounded-lg border border-surface-border bg-white py-1.5 opacity-0 shadow-xl transition-all duration-200 ease-smooth group-hover:visible group-hover:translate-y-0 group-hover:opacity-100 group-focus-within:visible group-focus-within:translate-y-0 group-focus-within:opacity-100">
                      {children.map((child) => {
                        const childHref = menuHref(child);
                        return (
                          <li key={child.label}>
                            {childHref ? (
                              <Link
                                href={childHref}
                                className="block px-4 py-2.5 text-sm text-surface-textBase transition-colors duration-150 ease-smooth hover:bg-brand-primaryLight hover:font-medium hover:text-brand-primary"
                              >
                                {child.label}
                              </Link>
                            ) : null}
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </li>
              );
            })}
          </ul>

          {/* Nút tìm kiếm bên phải navbar */}
          <Link
            href={ROUTES.search}
            className="flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold text-white transition-colors duration-200 ease-smooth hover:bg-white/25"
            aria-label="Tìm kiếm"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
              />
            </svg>
            <span className="hidden sm:inline">Tìm kiếm</span>
          </Link>
        </div>
      </nav>
    </>
  );
}
