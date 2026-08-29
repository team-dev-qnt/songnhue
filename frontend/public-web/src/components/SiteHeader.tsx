import Link from 'next/link';

import { PortalInfoStrip } from '@/components/PortalInfoStrip';
import { PortalNav } from '@/components/nav/PortalNav';
import type { MenuLink } from '@/lib/api';
import { getArticles, getMenu, getSiteConfig } from '@/lib/api';
import { buildMenuTree, fileUrl, ROUTES } from '@/lib/routes';
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
  },
];

/** Số bài chở trên dải chữ chạy. Nhiều hơn thì một vòng dài quá sức kiên nhẫn của người đọc. */
const SO_TIN_CHAY = 10;

export async function SiteHeader() {
  const [config, menu, tinMoi] = await Promise.all([
    getSiteConfig(),
    getMenu('HEADER'),
    getArticles({ size: SO_TIN_CHAY }),
  ]);

  const siteName = config?.['site.name'] ?? SITE.name;
  // Ghi đè chỉ khi Công ty thật sự đặt — rỗng thì một nguồn là đủ.
  const tenDauTrang = config?.['site.header.display-name'] || siteName;
  const coQuanChuQuan = config?.['site.header.parent-org'] ?? '';
  const logo = fileUrl(config?.['site.logo.attachment-id']) || '/logo-song-nhue.png';

  // Số trực ban 24/7 — đọc từ nhóm `company.*`.
  //
  // ⛔ Dự phòng phải RỖNG. Cùng số này còn hiện ở chân trang; ghi cứng ở hai tệp thì sửa số trên
  //    giao diện chỉ đổi được một nơi, và hai con số khác nhau trên cùng một trang tệ hơn hẳn một
  //    con số cũ.
  const hotline = config?.['company.hotline'] ?? '';
  const gioLamViec = config?.['company.working-hours'] ?? '';
  const email = config?.['company.email'] ?? '';
  const activeMenu = menu && menu.length > 0 ? menu : MENU_TOI_THIEU;

  return (
    <>
      {/* ───── Tầng 1: Dải nhận diện thương hiệu ───── */}
      <div className="w-full border-b border-white/10 bg-gradient-to-r from-chrome-navy800 via-chrome-navy500 to-chrome-navy800 shadow-xs">
        <div className="mx-auto flex max-w-[1232px] items-center justify-between gap-4 px-4 py-3 sm:gap-8 sm:px-6 sm:py-4">
          <Link href={ROUTES.home} className="group flex min-w-0 items-center gap-3 sm:gap-4">
            {/* ⚠ `translate-y-[11.2%]` bù KHOẢNG TRỐNG BAKED-IN của tệp logo, không phải một
                tinh chỉnh thẩm mỹ.

                Logo Công ty tải lên từ màn hình quản trị là PNG 612×792, nhưng phần vẽ chỉ nằm ở
                hàng 88→525: lề trên 88px, **lề dưới 266px**. Tức 33,6% đáy khung là trong suốt,
                nên tâm phần nhìn thấy nằm CAO HƠN tâm khung 89px = 11,24% chiều cao. `items-center`
                canh giữa cái KHUNG, nên mắt thấy logo lệch lên.

                Dùng `%` chứ không dùng `px`: đơn vị ấy tính theo chiều cao của chính ảnh, nên một
                giá trị đúng cho cả `h-11` (≈4,9px) lẫn `sm:h-16` (≈7,2px).

                ⛔ Con số này gắn với TỆP LOGO HIỆN TẠI. Nếu Công ty tải lên bản đã cắt sát viền thì
                   PHẢI bỏ dòng này, nếu không nó lại lệch xuống. */}
            <img
              src={logo}
              alt={siteName}
              className="h-11 w-auto shrink-0 translate-y-[11.2%] object-contain transition-transform duration-300 ease-smooth group-hover:scale-105 sm:h-16"
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
        tinMoi={(tinMoi?.content ?? []).map((b) => ({ slug: b.slug, title: b.title }))}
        hotline={hotline}
        gioLamViec={gioLamViec}
        email={email}
      />
    </>
  );
}
