import Link from 'next/link';

import { getMenu, getSiteConfig } from '@/lib/api';
import { fileUrl, isExternal, menuHref, ROUTES } from '@/lib/routes';

/**
 * Chân trang cổng thông tin điện tử — CN-01.5 (T15.3).
 *
 * Màu sắc đồng bộ 100% với SiteHeader và `design-tokens`:
 * - Dải hotline tiếp nhận thông tin: Xanh thương hiệu gradient (đồng bộ thanh Navbar)
 * - Khối nội dung 4 cột: Nền sáng nhẹ (slate-50 / white / blue-50), tiêu đề xanh đậm `#003eb3`, text tương phản cao
 * - Dải bản quyền đáy trang: Nền trang nhã, phân cách rõ ràng
 */
export async function SiteFooter() {
  const [config, menu] = await Promise.all([getSiteConfig(), getMenu('FOOTER')]);

  const siteName =
    config?.['site.name'] ?? 'CÔNG TY TNHH MỘT THÀNH VIÊN ĐẦU TƯ PHÁT TRIỂN THỦY LỢI SÔNG NHUỆ';
  // ⚠ Nhóm `company.*` là nhận diện pháp nhân, sửa được trên màn hình cấu hình hệ thống.
  // Trước đây địa chỉ, điện thoại, fax, email và số đường dây nóng ghi cứng ngay trong tệp này —
  // đổi số điện thoại của một doanh nghiệp nhà nước phải sửa mã nguồn và dựng lại image.
  //
  // ⛔⛔ RỖNG là giá trị dự phòng ĐÚNG, không phải chỗ bỏ trống vì lười. Ngày 24/8 một bản vá giao
  //     diện đã đặt lại đúng bộ giá trị thật vào đây làm `??` dự phòng, và nó khôi phục nguyên
  //     trạng lỗi cũ theo một hình dạng khó thấy hơn: màn hình vẫn đúng, nên không ai biết rằng số
  //     điện thoại người dân gọi khi có sự cố lại đang nằm trong mã nguồn. Sáu khoá dưới đây đã
  //     được seed đủ ở `V202608241255`, nên rỗng ở đây KHÔNG làm mất nội dung — nó chỉ bắt buộc
  //     nội dung phải đi ra từ `settings`.
  //
  //     Và khi cấu hình thật sự chưa có, ô rỗng mới là câu trả lời trung thực (luật 16): một số
  //     điện thoại cũ hiện ra như thể còn hiệu lực nguy hiểm hơn hẳn một ô trống.
  const diaChi = config?.['company.address'] ?? '';
  const dienThoai = config?.['company.phone'] ?? '';
  const fax = config?.['company.fax'] ?? '';
  const email = config?.['company.email'] ?? '';
  const hotline = config?.['company.hotline'] ?? '';
  const gioLamViec = config?.['company.working-hours'] ?? '';
  const companyInfo = config?.['site.footer.company-info'] ?? '';
  const mapEmbed = config?.['site.footer.map-embed'] ?? '';
  const logo = fileUrl(config?.['site.logo.attachment-id']) || '/logo-song-nhue.png';
  const copyright =
    config?.['site.footer.copyright'] || `© ${new Date().getFullYear()} ${siteName}`;

  const social = [
    {
      label: 'Facebook Fanpage',
      url: config?.['site.footer.social.facebook'] || 'https://facebook.com',
      icon: (
        <svg className="h-4 w-4 text-[#1877F2]" fill="currentColor" viewBox="0 0 24 24">
          <path d="M22 12c0-5.523-4.477-10-10-10S2 6.477 2 12c0 4.991 3.657 9.128 8.438 9.878v-6.987h-2.54V12h2.54V9.797c0-2.506 1.492-3.89 3.777-3.89 1.094 0 2.238.195 2.238.195v2.46h-1.26c-1.243 0-1.63.771-1.63 1.562V12h2.773l-.443 2.89h-2.33v6.988C18.343 21.128 22 16.991 22 12z" />
        </svg>
      ),
    },
    {
      label: 'Zalo Official Account',
      url: config?.['site.footer.social.zalo'] || 'https://zalo.me',
      icon: (
        <span className="flex h-4 w-4 items-center justify-center rounded bg-[#0068FF] font-bold text-[9px] text-white leading-none">
          Z
        </span>
      ),
    },
    {
      label: 'Kênh YouTube',
      url: config?.['site.footer.social.youtube'] || 'https://youtube.com',
      icon: (
        <svg className="h-4 w-4 text-[#FF0000]" fill="currentColor" viewBox="0 0 24 24">
          <path d="M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z" />
        </svg>
      ),
    },
  ];

  return (
    <footer className="mt-16 w-full border-t border-white/10 bg-gradient-to-b from-[#081e3a] via-[#0c294e] to-[#05172c] text-white">
      {/* ───── 1. Dải tiếp nhận thông tin trực ban / Hotline bão lũ ───── */}
      <div className="border-b border-white/10 bg-[#0b2e59]/80 py-3 text-xs text-white sm:text-sm">
        <div className="mx-auto flex max-w-[1240px] flex-col items-center justify-between gap-2 px-4 sm:flex-row sm:px-6">
          {hotline ? (
            <div className="flex items-center gap-2">
              <span className="flex h-2.5 w-2.5 rounded-full bg-emerald-400 animate-pulse" />
              <span className="font-semibold text-white">
                Đường dây nóng phòng chống thiên tai & TKCN:
              </span>
              <span className="font-bold text-amber-300 drop-shadow-xs">{hotline}</span>
              <span className="hidden text-white/70 md:inline">(Trực ban 24/7)</span>
            </div>
          ) : (
            <span />
          )}
          <div className="flex items-center gap-4 text-xs font-medium text-white/90">
            <Link
              href={ROUTES.search}
              className="transition-colors hover:text-white hover:underline"
            >
              Tra cứu văn bản
            </Link>
            <span className="text-white/30">|</span>
            <Link
              href="/bai-viet/lien-he"
              className="rounded-full bg-white/15 px-3 py-1 text-white backdrop-blur-xs transition-all hover:bg-white hover:text-brand-primary"
            >
              Gửi phản ánh kiến nghị →
            </Link>
          </div>
        </div>
      </div>

      {/* ───── 2. Khối nội dung chính (4 cột trên nền Full Blue Gradient) ───── */}
      <div className="mx-auto grid max-w-[1240px] gap-8 px-4 py-10 text-sm sm:grid-cols-2 sm:px-6 lg:grid-cols-12">
        <div className="space-y-3.5 lg:col-span-4">
          <div className="flex items-center gap-3">
            <div className="flex h-12 shrink-0 items-center justify-center">
              <img src={logo} alt={siteName} className="h-full w-auto object-contain" />
            </div>
            <div className="flex flex-col">
              <span className="font-bold uppercase tracking-tight text-white drop-shadow-xs">
                {siteName}
              </span>
              <span className="text-xs font-semibold text-sky-200">
                Doanh nghiệp 100% vốn Nhà nước
              </span>
            </div>
          </div>

          {companyInfo ? (
            <div
              className="space-y-1.5 text-xs text-white/85 [&_a]:text-sky-200 [&_a]:underline"
              // eslint-disable-next-line react/no-danger -- Đã qua HtmlSanitizer lúc ghi
              dangerouslySetInnerHTML={{ __html: companyInfo }}
            />
          ) : (
            <div className="space-y-2 text-xs text-white/85">
              <p className="flex items-start gap-2">
                <span className="shrink-0 font-semibold text-white">Trụ sở:</span>
                <span className="uppercase">{diaChi}</span>
              </p>
              <p className="flex items-center gap-2">
                <span className="shrink-0 font-semibold text-white">LIÊN HỆ:</span>
                <span>
                  {dienThoai}
                  {fax ? ` FAX: ${fax}` : ''}
                </span>
              </p>
              <p className="flex items-center gap-2">
                <span className="shrink-0 font-semibold text-white">EMAIL:</span>
                <span className="font-medium text-sky-200">{email}</span>
              </p>
              <p className="flex items-start gap-2">
                <span className="shrink-0 font-semibold text-white">Giờ làm việc:</span>
                <span>{gioLamViec}</span>
              </p>
            </div>
          )}
        </div>

        {/* Cột 2: Nghiệp vụ & Dịch vụ (3/12 cột) */}
        <div className="lg:col-span-3">
          <p className="relative pb-2 font-bold uppercase tracking-wider text-xs text-white after:absolute after:bottom-0 after:left-0 after:h-0.5 after:w-8 after:rounded after:bg-sky-300">
            Nghiệp vụ thủy lợi
          </p>
          <ul className="mt-3.5 space-y-2 text-xs text-white/85">
            <li>
              <Link
                href="/danh-muc/tin-tuc"
                className="transition-colors hover:text-white hover:underline"
              >
                Vận hành cống & điều tiết nước
              </Link>
            </li>
            <li>
              <Link
                href="/danh-muc/thong-bao"
                className="transition-colors hover:text-white hover:underline"
              >
                Thông báo cảnh báo xả lũ
              </Link>
            </li>
            <li>
              <Link
                href="/bai-viet/chuc-nang-nhiem-vu"
                className="transition-colors hover:text-white hover:underline"
              >
                Quản lý công trình & hồ đập
              </Link>
            </li>
            <li>
              <Link
                href="/bai-viet/co-cau-to-chuc"
                className="transition-colors hover:text-white hover:underline"
              >
                Cơ cấu các Xí nghiệp thủy lợi
              </Link>
            </li>
            <li>
              <Link
                href="http://songnhue.bhh40.net"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 font-semibold text-sky-200 hover:text-white hover:underline"
              >
                Hệ thống văn bản điều hành ↗
              </Link>
            </li>
          </ul>
        </div>

        {/* Cột 3: Liên kết nhanh & Cơ quan (2/12 cột) */}
        <div className="lg:col-span-2">
          <p className="relative pb-2 font-bold uppercase tracking-wider text-xs text-white after:absolute after:bottom-0 after:left-0 after:h-0.5 after:w-8 after:rounded after:bg-sky-300">
            Liên kết nhanh
          </p>
          <ul className="mt-3.5 space-y-2 text-xs text-white/85">
            {menu && menu.length > 0 ? (
              menu.map((item) => {
                const href = menuHref(item);
                return href ? (
                  <li key={item.label}>
                    <Link
                      href={href}
                      target={item.openNewTab ? '_blank' : undefined}
                      rel={isExternal(item) ? 'noopener noreferrer' : undefined}
                      className="transition-colors hover:text-white hover:underline"
                    >
                      {item.label}
                    </Link>
                  </li>
                ) : null;
              })
            ) : (
              <>
                <li>
                  <Link href="/" className="transition-colors hover:text-white hover:underline">
                    Trang chủ
                  </Link>
                </li>
                <li>
                  <Link
                    href="/bai-viet/gioi-thieu-chung"
                    className="transition-colors hover:text-white hover:underline"
                  >
                    Giới thiệu chung
                  </Link>
                </li>
                <li>
                  <Link
                    href="/danh-muc/tin-tuc"
                    className="transition-colors hover:text-white hover:underline"
                  >
                    Tin tức & Sự kiện
                  </Link>
                </li>
                <li>
                  <Link
                    href="/bai-viet/lien-he"
                    className="transition-colors hover:text-white hover:underline"
                  >
                    Thông tin liên hệ
                  </Link>
                </li>
              </>
            )}
          </ul>
        </div>

        {/* Cột 4: Kênh kết nối truyền thông & Bản đồ (3/12 cột) */}
        <div className="space-y-3.5 lg:col-span-3">
          <p className="relative pb-2 font-bold uppercase tracking-wider text-xs text-white after:absolute after:bottom-0 after:left-0 after:h-0.5 after:w-8 after:rounded after:bg-sky-300">
            Kênh kết nối
          </p>

          {/* Mạng xã hội */}
          <div className="flex flex-col gap-2">
            {social.map((s) => (
              <a
                key={s.label}
                href={s.url}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-2.5 rounded-lg border border-white/20 bg-white/10 px-3 py-2 text-xs font-medium text-white shadow-xs backdrop-blur-xs transition-all duration-200 hover:-translate-y-0.5 hover:bg-white hover:text-brand-primary"
              >
                <span>{s.icon}</span>
                <span>{s.label}</span>
              </a>
            ))}
          </div>

          {/* Bản đồ nếu có cấu hình hoặc thẻ chỉ đường mặc định */}
          {mapEmbed ? (
            <div
              className="overflow-hidden rounded-lg border border-white/20 shadow-xs [&_iframe]:h-28 [&_iframe]:w-full"
              // eslint-disable-next-line react/no-danger -- HtmlSanitizer.cleanMapEmbed() lúc ghi
              dangerouslySetInnerHTML={{ __html: mapEmbed }}
            />
          ) : (
            <div className="rounded-lg border border-white/20 bg-white/10 p-3 text-xs text-white/90 backdrop-blur-xs">
              <div className="flex items-center gap-1.5 font-bold text-white">
                <span>📍</span>
                <span>Chỉ đường tới Trụ sở</span>
              </div>
              {diaChi ? (
                <p className="mt-1 text-[11px] text-white/80 line-clamp-1">{diaChi}</p>
              ) : null}
              <a
                href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
                  `${siteName} ${diaChi}`,
                )}`}
                target="_blank"
                rel="noopener noreferrer"
                className="mt-2 inline-flex items-center gap-1 text-[11px] font-semibold text-sky-200 hover:text-white hover:underline"
              >
                <span>Xem trên Google Maps</span>
                <span>↗</span>
              </a>
            </div>
          )}
        </div>
      </div>

      {/* ───── 3. Dải bản quyền đáy trang ───── */}
      <div className="border-t border-white/10 bg-[#082242] py-3.5 text-xs text-white/70">
        <div className="mx-auto flex max-w-[1240px] flex-col items-center justify-between gap-2 px-4 text-center sm:flex-row sm:px-6 sm:text-left">
          <div>
            <p className="font-medium text-white">{copyright}</p>
            <p className="mt-0.5 text-[11px] text-white/60">
              Ghi rõ nguồn &ldquo;Cổng thông tin Thủy lợi Sông Nhuệ&rdquo; khi phát hành lại thông
              tin từ website này.
            </p>
          </div>
          <div className="flex items-center gap-4 text-[11px] text-white/70">
            <span>Phiên bản 1.0</span>
            <span>•</span>
            <Link href="/" className="hover:text-white">
              Sơ đồ cổng
            </Link>
            <span>•</span>
            <Link href="/bai-viet/lien-he" className="hover:text-white">
              Hỗ trợ kỹ thuật
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
