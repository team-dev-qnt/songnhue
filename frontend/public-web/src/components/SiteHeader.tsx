import Link from 'next/link';

import { PortalInfoStrip } from '@/components/PortalInfoStrip';
import { PortalNav } from '@/components/nav/PortalNav';
import type { MenuLink } from '@/lib/api';
import { getArticles, getMenu, getSiteConfig } from '@/lib/api';
import { buildMenuTree, fileUrl, ROUTES } from '@/lib/routes';
import { docSo } from '@/lib/settings';
import { SITE } from '@/lib/site';

/**
 * Đầu trang cổng thông tin — T16.1, dựng lại 29/08/2026 theo bố cục mới.
 *
 * Ba tầng:
 * <ol>
 *   <li>dải nhận diện — logo, <b>hai dòng tên</b>, ô tìm kiếm;
 *   <li>thanh điều hướng — {@link PortalNav};
 *   <li>dải thông tin — {@link PortalInfoStrip}: đồng hồ · chữ chạy · trực ban.
 * </ol>
 *
 * <h2>⭐ 29/08: ô Tìm kiếm rời thanh điều hướng lên dải nhận diện</h2>
 *
 * Không phải chuyện thẩm mỹ mà là <b>ngân sách bề rộng</b>. {@link PortalNav} đo tám nhãn cấp 1
 * rồi so với chỗ trống còn lại <i>sau khi trừ nút Tìm kiếm</i>; bỏ nút ấy ra khỏi thanh trả lại
 * ~110px, tức khung khả dụng 1232px thay vì 1082px. Cùng lượt này migration
 * {@code V202608291041} chuyển "Hoạt động Đảng, đoàn thể" xuống làm mục con nên cấp 1 còn bảy —
 * hai thay đổi cộng lại là thanh điều hướng thôi chật.
 *
 * <p>⛔ Nhưng <b>KHÔNG</b> vì thế mà gỡ {@code ResizeObserver} trong {@link PortalNav}: nhãn menu
 * nằm trong CSDL và Công ty sửa được, nên mọi con số cân sẵn đều có hạn dùng.
 *
 * <h2>⭐ 29/08: tên đầu trang hai dòng — và chữ hoa nằm ở DỮ LIỆU</h2>
 *
 * {@code site.header.parent-org} + {@code site.header.display-name}, dựng ở
 * {@code V202608291042}. Cả hai hiện <b>nguyên văn</b>; không có {@code uppercase} nào ở đây.
 * Công ty muốn đổi cách viết thì sửa ở màn hình cấu hình — CR-42, và cũng là điều kiện để
 * {@code noForcedUppercase} còn xanh.
 *
 * <p>{@code site.header.display-name} rỗng ⇒ rơi về {@code site.name}, lúc ấy tên Công ty chỉ
 * còn một nguồn duy nhất.
 *
 * <h2>⛔ Dự phòng khi API menu không trả lời</h2>
 *
 * Chỉ chứa tuyến đường mà bản thân ứng dụng bảo đảm có. Bản trước rơi về một menu 10 mục viết
 * cứng, trong đó BA mục trỏ tới chuyên mục không hề tồn tại — cổng quảng cáo những khu vực mà
 * bấm vào là 404, và chỉ hiện đúng lúc backend hỏng, tức lúc không ai soi (§10.54).
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
    logoId: null,
  },
];

/**
 * Trần của `site.home.marquee-count` — lấy đúng số trong ràng buộc `^(0|[1-9]|1[0-9]|20)$` của
 * `V202608291045`.
 *
 * ⚠ Đây KHÔNG phải "số bài hiển thị": số ấy nằm trong `settings` và người vận hành sửa được.
 * Đây là số bài **lấy về** để lượt gọi cấu hình và lượt gọi bài chạy song song — đọc `settings`
 * trước rồi mới gọi `/articles` là thêm một vòng khứ hồi vào TTFB của MỌI trang, vì đầu trang
 * dựng ở mọi trang. Lấy dư rồi cắt là rẻ hơn hẳn, và điểm cắt vẫn là giá trị Công ty đặt.
 */
const SO_TIN_TOI_DA = 20;

export async function SiteHeader() {
  const [config, menu, tinMoi] = await Promise.all([
    getSiteConfig(),
    getMenu('HEADER'),
    getArticles({ size: SO_TIN_TOI_DA }),
  ]);

  const siteName = config?.['site.name'] ?? SITE.name;
  // Ghi đè chỉ khi Công ty thật sự đặt — rỗng thì một nguồn là đủ.
  const tenDauTrang = config?.['site.header.display-name'] || siteName;
  const coQuanChuQuan = config?.['site.header.parent-org'] ?? '';
  const logo = fileUrl(config?.['site.logo.attachment-id']) || '/logo.png';

  // Số trực ban 24/7 — đọc từ nhóm `company.*`.
  //
  // ⛔ Dự phòng phải RỖNG. Cùng số này còn hiện ở chân trang; ghi cứng ở hai tệp thì sửa số trên
  //    giao diện chỉ đổi được một nơi, và hai con số khác nhau trên cùng một trang tệ hơn hẳn một
  //    con số cũ.
  const hotline = config?.['company.hotline'] ?? '';
  const gioLamViec = config?.['company.working-hours'] ?? '';
  const email = config?.['company.email'] ?? '';
  const activeMenu = menu && menu.length > 0 ? menu : MENU_TOI_THIEU;

  // ⭐ Số bài trên dải chữ chạy — `site.home.marquee-count` (`V202608291045`). Trước lượt này
  //    con số 10 nằm trong chính tệp này: một tham số nghiệp vụ viết trong mã, đúng thứ quy
  //    tắc 12 cấm. Đặt 0 ở màn hình cấu hình ⇒ `PortalTicker` không vẽ dải nào — không cần
  //    thêm một công tắc bật/tắt thứ hai.
  const soTinChay = docSo(config?.['site.home.marquee-count'], 10);

  return (
    <>
      {/* ───── Tầng 1: Dải nhận diện thương hiệu ───── */}
      <div className="w-full border-b border-white/10 bg-gradient-to-r from-chrome-navy800 via-chrome-navy500 to-chrome-navy800 shadow-xs">
        <div className="mx-auto flex max-w-[1232px] items-center justify-between gap-4 px-4 py-3 sm:gap-8 sm:px-6 sm:py-4">
          <Link href={ROUTES.home} className="group flex min-w-0 items-center gap-3 sm:gap-4">
            {/* ⭐ 29/08: ĐÃ GỠ `translate-y-[11.2%]` — và việc gỡ là bắt buộc, không phải dọn dẹp.

                Bản trước bù KHOẢNG TRỐNG BAKED-IN của `logo-song-nhue.png`: PNG 612×792 mà phần
                vẽ chỉ nằm ở hàng 88→525 (lề dưới 266px = 33,6% khung), nên tâm phần nhìn thấy cao
                hơn tâm khung 11,24% và `items-center` canh giữa cái KHUNG chứ không canh phần vẽ.
                Chú thích cũ nói thẳng: *"nếu Công ty tải lên bản đã cắt sát viền thì PHẢI bỏ dòng
                này, nếu không nó lại lệch xuống"*.

                Đo `logo.png` (tệp Công ty gửi 29/08) bằng cách đọc kênh alpha từng hàng: 354×353,
                phần vẽ nằm ở hàng **0→352** — lề trên 0px, lề dưới 0px, tâm vẽ trùng tâm khung.
                Giữ lại phép bù là đẩy logo xuống 11,2% mà không có gì để bù.

                ⛔ Con số bù luôn gắn với MỘT tệp cụ thể. Đổi logo thì đo lại biên alpha trước, đừng
                   chép lại giá trị cũ. */}
            <img
              src={logo}
              alt={siteName}
              className="h-11 w-auto shrink-0 object-contain transition-transform duration-300 ease-smooth group-hover:scale-105 sm:h-16"
            />
            <div className="min-w-0">
              {coQuanChuQuan ? (
                <div className="text-[10px] font-semibold leading-tight tracking-wide text-brand-gold sm:text-[13px]">
                  {coQuanChuQuan}
                </div>
              ) : null}
              {/* ⚠ `line-clamp-2` + `min-w-0`: tên Công ty dài 53 ký tự. Không có hai lớp này thì
                  trên điện thoại nó đẩy ô tìm kiếm ra khỏi màn hình — flex mặc định không cho con
                  co lại dưới bề rộng nội dung của nó. */}
              <div className="mt-0.5 line-clamp-2 text-[13px] font-black leading-tight tracking-tight text-white drop-shadow-2xs sm:text-base md:text-lg">
                {tenDauTrang}
              </div>
            </div>
          </Link>

          {/* Biểu mẫu GET thuần — chạy được cả khi JavaScript chưa tải xong. */}
          <form
            action={ROUTES.search}
            method="get"
            className="w-full max-w-[288px] shrink-0"
            role="search"
          >
            <label htmlFor="tim-kiem-dau-trang" className="sr-only">
              Tìm kiếm trên cổng thông tin
            </label>
            <div className="flex h-10 items-center gap-2 rounded-full bg-white pl-3.5 pr-1.5">
              <svg
                className="h-4 w-4 shrink-0 text-surface-textSecondary"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M21 21l-4.35-4.35M11 19a8 8 0 110-16 8 8 0 010 16z"
                />
              </svg>
              <input
                id="tim-kiem-dau-trang"
                name="q"
                type="search"
                placeholder="Nhập từ khoá tìm kiếm…"
                className="min-w-0 flex-1 bg-transparent text-[15px] text-surface-textBase outline-none placeholder:text-surface-textSecondary"
              />
              <button
                type="submit"
                className="hidden h-7 shrink-0 rounded-full bg-brand-primary px-3 text-xs font-bold text-white transition-colors hover:bg-brand-primaryHover sm:block"
              >
                Tìm
              </button>
            </div>
          </form>
        </div>
      </div>

      {/* ───── Tầng 2: Thanh điều hướng ───── */}
      <PortalNav tree={buildMenuTree(activeMenu)} />

      {/* ───── Tầng 3: Dải thông tin ───── */}
      <PortalInfoStrip
        tinMoi={(tinMoi?.content ?? [])
          .slice(0, soTinChay)
          .map((b) => ({ slug: b.slug, title: b.title }))}
        hotline={hotline}
        gioLamViec={gioLamViec}
        email={email}
      />
    </>
  );
}
