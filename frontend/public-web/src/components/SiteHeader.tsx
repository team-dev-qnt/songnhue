import Link from 'next/link';

import { PortalNav } from '@/components/nav/PortalNav';
import type { MenuLink } from '@/lib/api';
import { getMenu, getSiteConfig } from '@/lib/api';
import { buildMenuTree, fileUrl, ROUTES } from '@/lib/routes';
import { SITE } from '@/lib/site';

/**
 * Đầu trang cổng thông tin — T16.1, dựng lại 28/08/2026 (WS-25).
 *
 * Hai tầng, giữ nguyên như bản cũ:
 * <ol>
 *   <li>dải nhận diện — logo, tên Công ty, số trực ban;
 *   <li>thanh điều hướng — {@link PortalNav}.
 * </ol>
 *
 * <h2>Phần nào ở máy chủ, phần nào ở trình duyệt</h2>
 *
 * Tệp này <b>không</b> có {@code 'use client'}: nó là nơi duy nhất gọi API, và cây menu đi
 * xuống {@link PortalNav} dưới dạng props đã dựng sẵn. Trình duyệt không gọi lượt nào để vẽ
 * đầu trang.
 *
 * <h2>⛔ Dự phòng khi API menu không trả lời</h2>
 *
 * Chỉ chứa tuyến đường mà bản thân ứng dụng bảo đảm có. Bản trước rơi về một menu 10 mục viết
 * cứng, trong đó BA mục trỏ tới chuyên mục không hề tồn tại ({@code he-thong-cong-trinh},
 * {@code lich-van-hanh}, {@code pctt}) — cổng quảng cáo những khu vực mà bấm vào là 404, và chỉ
 * hiện đúng lúc backend hỏng, tức lúc không ai soi (§10.54).
 */
const MENU_TOI_THIEU: MenuLink[] = [
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
];

export async function SiteHeader() {
  const [config, menu] = await Promise.all([getSiteConfig(), getMenu('HEADER')]);

  const siteName = config?.['site.name'] ?? SITE.name;
  const logo = fileUrl(config?.['site.logo.attachment-id']) || '/logo-song-nhue.png';
  // Số trực ban 24/7 — đọc từ nhóm `company.*` trong bảng settings.
  //
  // ⛔ Dự phòng phải RỖNG. Cùng số này còn hiện ở chân trang; ghi cứng ở hai tệp thì sửa số trên
  //    giao diện chỉ đổi được một nơi, và hai con số khác nhau trên cùng một trang tệ hơn hẳn một
  //    con số cũ. Khoá `company.hotline` đã seed ở `V202608241255`.
  const hotline = config?.['company.hotline'] ?? '';
  const activeMenu = menu && menu.length > 0 ? menu : MENU_TOI_THIEU;

  return (
    <>
      {/* ───── Tầng 1: Dải nhận diện thương hiệu ───── */}
      <div className="w-full border-b border-white/10 bg-gradient-to-r from-chrome-navy800 via-chrome-navy500 to-chrome-navy800 shadow-xs">
        <div className="mx-auto flex max-w-[1240px] items-center justify-between gap-3 px-4 py-3 sm:px-6 sm:py-3.5">
          <Link href={ROUTES.home} className="group flex min-w-0 items-center gap-3 sm:gap-4">
            <img
              src={logo}
              alt={siteName}
              className="h-11 w-auto shrink-0 object-contain transition-transform duration-300 ease-smooth group-hover:scale-105 sm:h-16"
            />
            {/* ⚠ `line-clamp-2` + `min-w-0`: tên Công ty dài 56 ký tự. Không có hai lớp này thì
                trên điện thoại nó đẩy khối số trực ban ra khỏi màn hình — flex mặc định không
                cho con co lại dưới bề rộng nội dung của nó. */}
            <span className="line-clamp-2 text-[13px] font-black leading-tight tracking-tight text-white drop-shadow-2xs sm:text-base md:text-lg">
              {siteName}
            </span>
          </Link>

          {hotline ? (
            <div className="flex shrink-0 items-center gap-2 text-xs">
              <div className="hidden items-center gap-2 rounded-xl border border-red-200/80 bg-white/95 px-3.5 py-1.5 text-red-900 shadow-xs backdrop-blur-xs md:flex">
                <span className="flex h-2.5 w-2.5 rounded-full bg-red-600 animate-pulse" />
                <div className="flex flex-col text-left leading-tight">
                  <span className="text-[10px] font-bold text-red-700">Trực ban PCTT 24/7</span>
                  <a
                    href={`tel:${hotline.replace(/\D/g, '')}`}
                    className="text-xs font-extrabold text-red-800 hover:underline"
                  >
                    {hotline}
                  </a>
                </div>
              </div>

              <a
                href={`tel:${hotline.replace(/\D/g, '')}`}
                className="flex items-center gap-1.5 rounded-lg border border-red-200/80 bg-white/95 px-2.5 py-1 text-[11px] font-bold text-red-800 shadow-xs md:hidden"
                aria-label={`Gọi trực ban phòng chống thiên tai 24/7: ${hotline}`}
              >
                <span className="flex h-2 w-2 rounded-full bg-red-600 animate-pulse" />
                <span>PCTT 24/7</span>
              </a>
            </div>
          ) : null}
        </div>
      </div>

      {/* ───── Tầng 2: Thanh điều hướng ───── */}
      <PortalNav tree={buildMenuTree(activeMenu)} />
    </>
  );
}
