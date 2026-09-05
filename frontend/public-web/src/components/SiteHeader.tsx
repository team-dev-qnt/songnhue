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
 * <h2>⭐⭐ 01/09: ô tìm kiếm RỜI HẲN dải nhận diện — và dải này thôi phải chia bề rộng</h2>
 *
 * Đường đi của một ô nhập, ghi lại vì mỗi chặng đổi một ràng buộc khác:
 *
 * <pre>
 *   28/08  nút Tìm kiếm ở thanh điều hướng    → ngân sách bề rộng thanh mất ~110px
 *   29/08  dời lên dải nhận diện              → trả 110px cho thanh, dải phải chia chỗ 3 phần
 *   31/08  dải vỡ ở 375px vì đúng việc chia   → vá bằng xếp hai hàng (xem mục dưới)
 *   01/09  về lại thanh, THU VỀ MỘT BIỂU TƯỢNG → dải chỉ còn logo + tên, thanh chỉ mất 44px
 * </pre>
 *
 * Chặng cuối là chặng duy nhất không phải đánh đổi: ô nhập bung ra ở <b>một hàng riêng</b> dưới
 * thanh điều hướng (xem {@link PortalNav}), nên nó không giành bề ngang với ai — không với tám
 * nhãn cấp 1, không với logo và tên.
 *
 * <p>Hệ quả cho tệp này, và là lý do nó được sửa: dải nhận diện <b>không còn ba khối phải xếp
 * cạnh nhau</b>, chỉ còn một. Nên {@code justify-between} đổi thành {@code justify-center} —
 * logo và tên canh giữa khung, đúng yêu cầu Công ty. Và toàn bộ máy móc xếp-hai-hàng của bản
 * 31/08 thôi cần: nguyên nhân của sự cố ấy là <i>ô tìm kiếm 288px không chịu co</i>, mà nay nó
 * không nằm ở đây nữa.
 *
 * <p>⛔ Phép trừ 31/08 vẫn đúng và vẫn phải nhớ: 375 − 32 − 16 − 288 = <b>39px</b> cho logo + tên,
 * trong khi riêng logo đã 44px. Ai định đưa một ô nhập bề rộng cố định trở lại hàng này thì đọc
 * lại con số đó trước.
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
 * <h2>📌 31/08 — LƯU TRỮ: vì sao dải này từng phải xếp hai hàng ở điện thoại</h2>
 *
 * <p>⚠ Mục này <b>đã hết hiệu lực</b> từ 01/09 (ô tìm kiếm rời đi ⇒ hàng chỉ còn một khối, không
 * còn gì để xếp cột). Giữ lại vì <b>bài học</b> thì không hết hiệu lực, và vì nó là bằng chứng
 * cho một lớp lỗi mà bộ canh của kho vẫn chưa bắt được.
 *
 * <p>Đo trên staging bằng Chrome ở <b>375×812, DPR 2</b>: bề rộng khối chữ (logo + tên) =
 * <b>0px</b>; dòng cơ quan chủ quản nổ thành <b>8 dòng / cao 100px</b> trong một cái hộp rộng 0;
 * ô tìm kiếm <b>288px cố định</b> = 77% bề rộng màn hình; và {@code body.scrollWidth} = 375 —
 * <b>không</b> tràn ngang.
 *
 * <p>Dòng cuối là toàn bộ vấn đề: <b>không có tràn ngang nào để bắt</b>. Ô tìm kiếm mang
 * {@code shrink-0} nên nó không chịu co; khối chữ mang {@code min-w-0} nên nó là thứ duy nhất co
 * được — và nó co về 0, rồi chữ tràn ra ngoài một cái hộp rộng 0 mà {@code overflow: visible}
 * hiển thị như chữ bình thường. Cùng hình dạng §10.62: <i>một cơ chế chịu lỗi làm đúng việc của
 * nó thì lỗi không bao giờ nổi lên</i> — ở đó là {@code flex-wrap} che một thanh tràn 22%.
 *
 * <p>⛔ {@code line-clamp-2} ở dòng cơ quan chủ quản thì <b>GIỮ</b>. Nó là lớp phòng thủ theo chiều
 * sâu chứ không phải bản vá: bề rộng khung chứa còn phụ thuộc dữ liệu Công ty nhập, mà chuỗi
 * trong CSDL thì dài ra được bất cứ lúc nào.
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
        <div className="mx-auto flex max-w-[1232px] items-center justify-center px-4 py-3 sm:px-6 sm:py-4">
          <Link
            href={ROUTES.home}
            className="group flex min-w-0 items-center justify-center gap-3 text-center sm:gap-4"
          >
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
              className="h-12 w-auto shrink-0 object-contain transition-transform duration-300 ease-smooth group-hover:scale-105 sm:h-16"
            />
            <div className="min-w-0">
              {coQuanChuQuan ? (
                <div className="line-clamp-2 text-[11px] font-semibold leading-tight tracking-wide text-brand-gold sm:text-[13px]">
                  {coQuanChuQuan}
                </div>
              ) : null}
              {/* ⚠ `line-clamp-2` giữ tên Công ty (53 ký tự) trong hai dòng.

                  📌 Hai lượt chú thích trước ở đúng chỗ này đều nói về `min-w-0` và ô tìm kiếm,
                     và lượt đầu nói SAI CHIỀU nguyên nhân (§10.42). Từ 01/09 ô tìm kiếm không
                     còn ở hàng này, nên `min-w-0` không còn ai để tranh chỗ — nó chỉ còn giữ
                     một việc: cho phép `line-clamp` cắt chữ thay vì đẩy hàng rộng ra. Giữ lại,
                     vì tên Công ty đến từ CSDL và dài ra được. */}
              <div className="mt-0.5 line-clamp-2 text-[13px] font-black leading-tight tracking-tight text-white drop-shadow-2xs sm:text-base md:text-lg">
                {tenDauTrang}
              </div>
            </div>
          </Link>
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
