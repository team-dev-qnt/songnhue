import Link from 'next/link';

import { getMenu, getSiteConfig } from '@/lib/api';
import { fileUrl, isExternal, menuHref, ROUTES } from '@/lib/routes';
import { SITE } from '@/lib/site';

/**
 * Chân trang cổng thông tin điện tử — CN-01.5 (T15.3).
 *
 * Màu sắc đồng bộ 100% với SiteHeader và `design-tokens`:
 * - Dải hotline tiếp nhận thông tin: Xanh thương hiệu gradient (đồng bộ thanh Navbar)
 * - Khối nội dung 4 cột: nền navy theo `portalChrome`, tiêu đề trắng, text tương phản cao
 *
 * ⛔ Bảy mã navy từng ghi thẳng vào class Tailwind ở tệp này và ở `SiteHeader`. Nay đọc từ
 *   `design-tokens.portalChrome` — cùng giá trị đang hiện, chỉ khác nơi khai (`ui-styles.md`
 *   §2.1 cấm khai màu tại chỗ). `noHardcodedColors.test.ts` canh.
 * - Dải bản quyền đáy trang: Nền trang nhã, phân cách rõ ràng
 */
export async function SiteFooter() {
  const [config, menu] = await Promise.all([getSiteConfig(), getMenu('FOOTER')]);

  const siteName = config?.['site.name'] ?? SITE.name;
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
  const hotline = config?.['company.hotline'] ?? '';
  // CR-07 · đóng nợ T11.28. Địa chỉ hệ thống văn bản điều hành từng ghi cứng ở BA tệp giao
  // diện, nên đổi địa chỉ của khách là sửa mã nguồn và dựng lại image. Nay là cấu hình.
  //
  // ⛔ Rỗng thì KHÔNG render nút — không rơi về một địa chỉ mặc định. Một nút mở sang sai hệ
  //    thống tệ hơn hẳn không có nút (luật 16).
  const heThongVanBan = config?.['site.external.doc-system-url'] ?? '';
  const companyInfo = config?.['site.footer.company-info'] ?? '';
  const mapEmbed = config?.['site.footer.map-embed'] ?? '';
  const logo = fileUrl(config?.['site.logo.attachment-id']) || '/logo-song-nhue.png';
  const copyright =
    config?.['site.footer.copyright'] || `© ${new Date().getFullYear()} ${siteName}`;

  const social = [
    {
      label: 'Facebook Fanpage',
      url: config?.['site.footer.social.facebook'] ?? '',
      icon: (
        <svg className="h-4 w-4 text-social-facebook" fill="currentColor" viewBox="0 0 24 24">
          <path d="M22 12c0-5.523-4.477-10-10-10S2 6.477 2 12c0 4.991 3.657 9.128 8.438 9.878v-6.987h-2.54V12h2.54V9.797c0-2.506 1.492-3.89 3.777-3.89 1.094 0 2.238.195 2.238.195v2.46h-1.26c-1.243 0-1.63.771-1.63 1.562V12h2.773l-.443 2.89h-2.33v6.988C18.343 21.128 22 16.991 22 12z" />
        </svg>
      ),
    },
    {
      label: 'Zalo Official Account',
      url: config?.['site.footer.social.zalo'] ?? '',
      icon: (
        <span className="flex h-4 w-4 items-center justify-center rounded bg-social-zalo font-bold text-[9px] text-white leading-none">
          Z
        </span>
      ),
    },
    {
      label: 'Kênh YouTube',
      url: config?.['site.footer.social.youtube'] ?? '',
      icon: (
        <svg className="h-4 w-4 text-social-youtube" fill="currentColor" viewBox="0 0 24 24">
          <path d="M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z" />
        </svg>
      ),
    },
  ];

  return (
    <footer className="mt-16 w-full border-t border-white/10 bg-gradient-to-b from-chrome-navy700 via-chrome-navy600 to-chrome-navy900 text-white">
      {/* ───── 1. Dải tiếp nhận thông tin trực ban / Hotline bão lũ ───── */}
      <div className="border-b border-white/10 bg-chrome-navy500/80 py-3 text-xs text-white sm:text-sm">
        <div className="mx-auto flex max-w-[1232px] flex-col items-center justify-between gap-2 px-4 sm:flex-row sm:px-6">
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
          {/* ⚠ OI-08 còn mở: Công ty chưa chốt giữ / đổi tên / bỏ hai mục này, và liệu
              "Tra cứu văn bản" có trùng chức năng với "Công bố thông tin" không. Giữ nguyên
              tới khi có trả lời — bỏ trước là tự quyết thay khách. Đường dẫn Liên hệ đã đổi
              sang trang riêng `/lien-he` (CR-22), không còn là một bài viết. */}
          <div className="flex items-center gap-4 text-xs font-medium text-white/90">
            <Link
              href={ROUTES.search}
              className="transition-colors hover:text-white hover:underline"
            >
              Tra cứu văn bản
            </Link>
            <span className="text-white/30">|</span>
            <Link
              href={ROUTES.lienHe}
              className="rounded-full bg-white/15 px-3 py-1 text-white backdrop-blur-xs transition-all hover:bg-white hover:text-brand-primary"
            >
              Gửi phản ánh kiến nghị →
            </Link>
          </div>
        </div>
      </div>

      {/* ───── 2. Khối nội dung chính (4 cột trên nền Full Blue Gradient) ───── */}
      <div className="mx-auto grid max-w-[1232px] gap-8 px-4 py-10 text-sm sm:grid-cols-2 sm:px-6 lg:grid-cols-12">
        <div className="space-y-3.5 lg:col-span-4">
          <div className="flex items-center gap-3">
            <div className="flex h-12 shrink-0 items-center justify-center">
              <img src={logo} alt={siteName} className="h-full w-auto object-contain" />
            </div>
            {/* CR-39: đã bỏ dòng "Doanh nghiệp 100% vốn Nhà nước" theo yêu cầu của Công ty. */}
            <div className="flex flex-col">
              <span className="font-bold tracking-tight text-white drop-shadow-xs">{siteName}</span>
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
              {/* CR-42: hiện nguyên văn giá trị trong `settings`, KHÔNG ép `uppercase`.
                  Địa chỉ mới Công ty gửi viết thường ("Tầng 4-5 Tòa nhà Newhouse – Phường
                  Hà Đông"); ép hoa ở đây là giao diện tự quyết định thay người nhập. */}
              <p className="flex items-start gap-2">
                <span className="shrink-0 font-semibold text-white">Trụ sở:</span>
                <span>{diaChi}</span>
              </p>
              <p className="flex items-center gap-2">
                <span className="shrink-0 font-semibold text-white">Liên hệ:</span>
                <span>
                  {dienThoai}
                  {fax ? ` — Fax: ${fax}` : ''}
                </span>
              </p>
              {/* ⛔ CR-40 (email) và CR-41 (giờ làm việc) đã BỎ khỏi chân trang.
                  Hai khoá `company.email` / `company.working-hours` vẫn còn trong `settings`
                  và vẫn hiện ở trang Liên hệ — tài liệu yêu cầu bỏ khỏi *chân trang*, không
                  yêu cầu xoá dữ liệu. OI-04 còn chờ Công ty chốt bỏ hẳn email hay thay bằng
                  email công vụ; xoá dữ liệu bây giờ là quyết định thay họ. */}
            </div>
          )}
        </div>

        {/*
          ⛔ CR-09 — cột "Nghiệp vụ thủy lợi" đã bị gỡ.

          Nó là năm liên kết viết cứng tạo ra một hệ phân loại THỨ HAI ngay cạnh menu:
          "Vận hành cống & điều tiết nước", "Thông báo cảnh báo xả lũ", "Quản lý công trình &
          hồ đập"… không mục nào có mặt trong cây nội dung §3, và ba trong số đó trỏ vào
          chuyên mục/bài mà Công ty vừa đổi hoặc bỏ. §2 nói thẳng: *"Tránh để menu trên và
          footer dùng hai hệ phân loại khác nhau."*

          Chân trang nay chỉ đọc menu vị trí FOOTER — cùng bảng, cùng màn hình quản trị với
          menu chính, nên hai nơi không lệch được (quy tắc 12).
        */}
        {/* Cột 2: Liên kết nhanh — ĐỌC MENU FOOTER, không có mục nào viết cứng */}
        <div className="lg:col-span-5">
          <p className="relative pb-2 font-bold text-xs text-white after:absolute after:bottom-0 after:left-0 after:h-0.5 after:w-8 after:rounded after:bg-sky-300">
            Liên kết nhanh
          </p>
          <ul className="mt-3.5 grid grid-cols-1 gap-x-6 gap-y-2 text-xs text-white/85 sm:grid-cols-2">
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
              /*
                ⛔ Dự phòng CHỈ chứa tuyến đường mà bản thân ứng dụng bảo đảm có.

                Bản trước rơi về bốn liên kết viết cứng, và sau đợt chỉnh sửa này thì HAI
                trong số đó đã chết: `/bai-viet/gioi-thieu-chung` nay là `tong-quan` (CR-23),
                `/bai-viet/lien-he` nay là trang riêng `/lien-he` (CR-22). Một dự phòng như vậy
                chỉ hiện đúng lúc backend hỏng — tức lúc không ai soi — và nó quảng cáo những
                địa chỉ trả 404. Đây là cùng cái bẫy đã gỡ khỏi `SiteHeader` ở §10.54.
              */
              <li>
                <Link
                  href={ROUTES.home}
                  className="transition-colors hover:text-white hover:underline"
                >
                  Trang chủ
                </Link>
              </li>
            )}
          </ul>

          {/* CR-07 — cánh cửa sang hệ thống văn bản điều hành của Thành phố. Cổng KHÔNG dựng
              module văn bản nội bộ và KHÔNG đồng bộ dữ liệu (CN-01.7); đây chỉ là một liên kết
              mở tab mới. Địa chỉ đọc từ `settings`, rỗng thì không render gì. */}
          {heThongVanBan ? (
            <a
              href={heThongVanBan}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-4 inline-flex items-center gap-1.5 rounded-lg border border-white/25 bg-white/10 px-3 py-2 text-xs font-semibold text-sky-100 transition-all hover:bg-white hover:text-brand-primary"
            >
              <span>Hệ thống văn bản điều hành</span>
              <span aria-hidden="true">↗</span>
            </a>
          ) : null}
        </div>

        {/* Cột 4: Kênh kết nối truyền thông (3/12 cột).

            ⛔ Bản đồ ĐÃ RỜI khỏi cột này — xem dải bản đồ kín bề rộng ngay dưới lưới. */}
        <div className="space-y-3.5 lg:col-span-3">
          <p className="relative pb-2 font-bold text-xs text-white after:absolute after:bottom-0 after:left-0 after:h-0.5 after:w-8 after:rounded after:bg-sky-300">
            Kênh kết nối
          </p>

          {/* Mạng xã hội */}
          <div className="flex flex-col gap-2">
            {social
              .filter((s) => s.url)
              .map((s) => (
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
        </div>
      </div>

      {/* ───── 2b. Dải BẢN ĐỒ TRỤ SỞ — kín bề rộng chân trang ─────

          ⭐ Yêu cầu Công ty 29/08: *"mở rộng hết sức có thể phần diện tích bản đồ hiển thị"*.

          Trước lượt này bản đồ nằm trong cột "Kênh kết nối" rộng 3/12 với `[&_iframe]:h-28` —
          112px cao, ~250px rộng trên màn hình 1232px. Ở cỡ đó Google Map không đọc được tên
          đường nào: nó là một hình trang trí có hình dạng của một bản đồ. Nay nó là một dải
          riêng chiếm trọn bề rộng, cao 320px (384px từ `sm`), tức diện tích tăng khoảng **13
          lần**.

          ⚠ Chiều cao đặt ở KHUNG chứ không ở `<iframe>` gốc: mã nhúng do Công ty dán vào ô cấu
          hình mang `width`/`height` riêng của Google, và `HtmlSanitizer.cleanMapEmbed()` giữ
          nguyên chúng. Selector `[&_iframe]:h-full` ghi đè bằng CSS — sửa chuỗi HTML lúc hiển
          thị là sửa thứ đã qua khâu làm sạch, tức mở lại đúng cửa mà khâu ấy vừa đóng. */}
      {mapEmbed ? (
        <div className="mx-auto max-w-[1232px] px-4 pb-10 sm:px-6">
          <p className="relative mb-3.5 pb-2 font-bold text-xs text-white after:absolute after:bottom-0 after:left-0 after:h-0.5 after:w-8 after:rounded after:bg-sky-300">
            Bản đồ trụ sở
          </p>
          <div
            className="h-[320px] overflow-hidden rounded-xl border border-white/20 shadow-xs sm:h-[384px] [&_iframe]:h-full [&_iframe]:w-full [&_iframe]:border-0"
            // eslint-disable-next-line react/no-danger -- HtmlSanitizer.cleanMapEmbed() lúc ghi
            dangerouslySetInnerHTML={{ __html: mapEmbed }}
          />
        </div>
      ) : (
        /* ⛔ Chưa cấu hình mã nhúng thì KHÔNG dựng một khung xám cao 320px cho có: một ô trống
           to bằng cả bề rộng trang là thứ trông như hỏng. Thẻ chỉ đường nhỏ, nói thẳng, và có
           một đường đi thật tới Google Maps. */
        <div className="mx-auto max-w-[1232px] px-4 pb-10 sm:px-6">
          <div className="flex flex-col gap-2 rounded-xl border border-white/20 bg-white/10 p-4 text-xs text-white/90 backdrop-blur-xs sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="flex items-center gap-1.5 font-bold text-white">
                <span aria-hidden="true">📍</span>
                <span>Chỉ đường tới trụ sở</span>
              </div>
              {diaChi ? <p className="mt-1 text-[11px] text-white/80">{diaChi}</p> : null}
            </div>
            <a
              href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
                `${siteName} ${diaChi}`,
              )}`}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex shrink-0 items-center gap-1 rounded-lg border border-white/25 bg-white/10 px-3 py-2 text-[11px] font-semibold text-sky-100 transition-all hover:bg-white hover:text-brand-primary"
            >
              <span>Xem trên Google Maps</span>
              <span aria-hidden="true">↗</span>
            </a>
          </div>
        </div>
      )}

      {/* ───── 3. Dải bản quyền đáy trang ───── */}
      <div className="border-t border-white/10 bg-chrome-navy700 py-3.5 text-xs text-white/70">
        <div className="mx-auto flex max-w-[1232px] flex-col items-center justify-between gap-2 px-4 text-center sm:flex-row sm:px-6 sm:text-left">
          <div>
            <p className="font-medium text-white">{copyright}</p>
            {/* ⚠ Tên trong câu này ĐỌC `site.name`, không viết lại lần nữa. Bản trước ghi cứng
                "Cổng thông tin Thủy lợi Sông Nhuệ" ngay dưới một dòng {siteName} lấy từ
                `settings` — hai nguồn cho cùng một cái tên, và đổi tên trên màn hình cấu hình
                chỉ đổi được một nửa (luật 14). */}
            <p className="mt-0.5 text-[11px] text-white/60">
              Ghi rõ nguồn &ldquo;{siteName}&rdquo; khi phát hành lại thông tin từ website này.
            </p>
          </div>
          {/*
            ⛔ Ba mục cũ ở đây đều đã gỡ, mỗi mục một lý do — cùng một họ với §10.54:

            • "Phiên bản 1.0" — một con số viết cứng, không nơi nào đọc, không đổi khi cổng đổi.
              Nó KHẲNG ĐỊNH một điều với người đọc mà không gì bảo đảm nó còn đúng;
            • "Sơ đồ cổng" — nhãn hứa một trang sơ đồ, `href` trỏ về **trang chủ**. Không có
              trang sơ đồ nào tồn tại. Một liên kết mà nhãn và đích nói hai chuyện khác nhau;
            • "Hỗ trợ kỹ thuật" — trỏ `/lien-he`, nơi chỉ có liên hệ của Công ty, không có đầu
              mối kỹ thuật nào. Đổi nhãn cho khớp đích thay vì giữ một lời hứa không ai nhận.
          */}
          <div className="flex items-center gap-4 text-[11px] text-white/70">
            <Link href={ROUTES.lienHe} className="hover:text-white">
              Liên hệ
            </Link>
            <span>•</span>
            <Link href={ROUTES.search} className="hover:text-white">
              Tìm kiếm
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
