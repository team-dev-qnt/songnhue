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
    label: 'Tổng quan & Lịch sử hình thành',
    linkType: 'ARTICLE',
    url: null,
    categorySlug: null,
    articleSlug: 'gioi-thieu-chung',
    openNewTab: false,
    depth: 1,
    parentLabel: 'Giới thiệu',
  },
  {
    label: 'Chức năng & Nhiệm vụ',
    linkType: 'ARTICLE',
    url: null,
    categorySlug: null,
    articleSlug: 'chuc-nang-nhiem-vu',
    openNewTab: false,
    depth: 1,
    parentLabel: 'Giới thiệu',
  },
  {
    label: 'Cơ cấu tổ chức & Ban Lãnh đạo',
    linkType: 'ARTICLE',
    url: null,
    categorySlug: null,
    articleSlug: 'co-cau-to-chuc',
    openNewTab: false,
    depth: 1,
    parentLabel: 'Giới thiệu',
  },
  {
    label: 'Hệ thống Công trình Thủy lợi',
    linkType: 'ARTICLE',
    url: null,
    categorySlug: null,
    articleSlug: 'he-thong-cong-trinh',
    openNewTab: false,
    depth: 1,
    parentLabel: 'Giới thiệu',
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
    label: 'Tin hoạt động Công ty',
    linkType: 'CATEGORY',
    url: null,
    categorySlug: 'tin-tuc',
    articleSlug: null,
    openNewTab: false,
    depth: 1,
    parentLabel: 'Tin tức',
  },
  {
    label: 'Công tác PCTT & Vận hành bão lũ',
    linkType: 'CATEGORY',
    url: null,
    categorySlug: 'pctt',
    articleSlug: null,
    openNewTab: false,
    depth: 1,
    parentLabel: 'Tin tức',
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
    label: 'Thông báo điều hành xả nước',
    linkType: 'CATEGORY',
    url: null,
    categorySlug: 'thong-bao',
    articleSlug: null,
    openNewTab: false,
    depth: 1,
    parentLabel: 'Thông báo',
  },
  {
    label: 'Lịch vận hành cống & trạm bơm',
    linkType: 'CATEGORY',
    url: null,
    categorySlug: 'lich-van-hanh',
    articleSlug: null,
    openNewTab: false,
    depth: 1,
    parentLabel: 'Thông báo',
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
  const logo = fileUrl(config?.['site.logo.attachment-id']) || '/logo-song-nhue.png';
  // Số trực ban 24/7 — đọc từ nhóm `company.*` trong bảng settings
  const hotline = config?.['company.hotline'] ?? '';
  const activeMenu = menu && menu.length > 0 ? menu : DEFAULT_HEADER_MENU;
  const tree = buildMenuTree(activeMenu);

  return (
    <>
      {/* ───── Tầng 1: Dải nhận diện thương hiệu (Nền Gradient Xanh Sông Nước Rõ Nét, Logo Nổi Bật) ───── */}
      <div className="w-full border-b border-[#165bb6]/25 bg-gradient-to-r from-[#bfd9f8] via-[#d5e7fb] to-[#b4d3f6] shadow-xs">
        <div className="mx-auto flex max-w-[1240px] items-center justify-between px-4 py-3 sm:px-6 sm:py-3.5">
          {/* Logo & Tên cơ quan */}
          <Link href={ROUTES.home} className="group flex items-center gap-3.5 sm:gap-4">
            <img
              src={logo}
              alt={siteName}
              className="h-14 w-auto object-contain transition-transform duration-300 ease-smooth group-hover:scale-105 sm:h-16"
            />
            <div className="flex flex-col">
              <span className="text-sm font-black uppercase tracking-tight text-[#06244f] drop-shadow-2xs transition-colors duration-200 sm:text-base md:text-lg">
                {siteName}
              </span>
            </div>
          </Link>

          {/* Hotline / Thông tin nhanh bên phải */}
          {hotline ? (
            <div className="flex items-center gap-2 text-xs">
              {/* Phiên bản Desktop đầy đủ */}
              <div className="hidden items-center gap-2 rounded-xl border border-red-200/80 bg-white/95 px-3.5 py-1.5 text-red-900 shadow-xs backdrop-blur-xs md:flex">
                <span className="flex h-2.5 w-2.5 rounded-full bg-red-600 animate-pulse" />
                <div className="flex flex-col text-left leading-tight">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-red-700">
                    Trực ban PCTT 24/7
                  </span>
                  <a
                    href={`tel:${hotline.replace(/\D/g, '')}`}
                    className="text-xs font-extrabold text-red-800 hover:underline"
                  >
                    {hotline}
                  </a>
                </div>
              </div>

              {/* Phiên bản Mobile gọn gàng */}
              <a
                href={`tel:${hotline.replace(/\D/g, '')}`}
                className="flex items-center gap-1.5 rounded-lg border border-red-200/80 bg-white/95 px-2.5 py-1 text-[11px] font-bold text-red-800 shadow-xs md:hidden"
                aria-label="Gọi Trực ban PCTT 24/7"
              >
                <span className="flex h-2 w-2 rounded-full bg-red-600 animate-pulse" />
                <span>PCTT 24/7</span>
              </a>
            </div>
          ) : null}
        </div>
      </div>

      {/* ───── Tầng 2: Thanh điều hướng chính (Sticky Navigation Bar - Capslock) ───── */}
      <nav
        aria-label="Điều hướng chính"
        className="sticky top-0 z-40 w-full border-b border-black/20 bg-gradient-to-r from-[#061b37] via-[#0b2d5b] to-[#061b37] text-white shadow-md backdrop-blur-md"
      >
        <div className="mx-auto flex max-w-[1240px] items-center justify-between px-4 sm:px-6">
          <ul className="flex flex-wrap items-center gap-1 text-xs font-bold uppercase tracking-wider sm:text-[13px]">
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
                      className="flex items-center gap-1 px-3 py-2.5 text-xs font-bold uppercase tracking-wider text-white transition-all duration-200 ease-smooth hover:bg-white/10 hover:text-[#dbc373] sm:px-3.5 sm:py-3 sm:text-[13px]"
                    >
                      <span className="uppercase">{item.label}</span>
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
                      className="flex items-center gap-1 px-3 py-2.5 text-xs font-bold uppercase tracking-wider text-white transition-all duration-200 ease-smooth hover:bg-white/10 hover:text-[#dbc373] sm:px-3.5 sm:py-3 sm:text-[13px]"
                    >
                      <span className="uppercase">{item.label}</span>
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
                    <ul className="invisible absolute left-0 top-full z-50 min-w-64 translate-y-1 rounded-lg border border-surface-border bg-white py-1.5 opacity-0 shadow-xl transition-all duration-200 ease-smooth before:absolute before:-top-2 before:left-0 before:right-0 before:h-2 group-hover:visible group-hover:translate-y-0 group-hover:opacity-100 group-focus-within:visible group-focus-within:translate-y-0 group-focus-within:opacity-100 divide-y divide-surface-border/40">
                      {children.map((child) => {
                        const childHref = menuHref(child);
                        return (
                          <li key={child.label}>
                            {childHref ? (
                              <Link
                                href={childHref}
                                target={child.openNewTab ? '_blank' : undefined}
                                rel={isExternal(child) ? 'noopener noreferrer' : undefined}
                                className="block px-4 py-2.5 text-xs font-bold uppercase tracking-wider text-surface-textBase transition-all duration-150 ease-smooth hover:bg-brand-primaryLight hover:text-brand-primary hover:pl-5"
                              >
                                <span className="uppercase">{child.label}</span>
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
            className="flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-bold uppercase tracking-wider text-white transition-colors duration-200 ease-smooth hover:bg-white/20 hover:text-[#dbc373]"
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
            <span className="hidden sm:inline uppercase">Tìm kiếm</span>
          </Link>
        </div>
      </nav>
    </>
  );
}
