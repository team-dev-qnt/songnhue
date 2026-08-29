'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

import type { BannerItem } from '@/lib/api';
import { fileUrl } from '@/lib/routes';
import { EmptyBlock } from './EmptyBlock';

interface HomeBannerSliderProps {
  banners: BannerItem[];
  /** Giây mỗi ảnh — `site.slider.interval-seconds`. Tài liệu: 3–5 giây. */
  intervalSeconds: number;
  autoplay: boolean;
  showArrows: boolean;
  showDots: boolean;
}

/**
 * Slider ảnh hoạt động của Công ty — CR-10, dựng lại theo bản vẽ 29/08/2026.
 *
 * <h2>⭐ Chú thích nằm DƯỚI ảnh, không đè lên ảnh</h2>
 *
 * Bản trước phủ một dải gradient đen lên đáy ảnh rồi đặt tiêu đề trắng lên đó. Bản vẽ đổi sang
 * đúng hình dạng của cổng tham chiếu: khung ảnh chiều cao cố định, rồi một <b>thẻ trắng</b> nằm
 * dưới mang tiêu đề. Ba thứ được sửa cùng lúc, và không thứ nào là chuyện thẩm mỹ:
 *
 * <ul>
 *   <li><b>Đọc được.</b> Chữ trắng trên ảnh chỉ đọc được khi ảnh đủ tối ở đúng góc ấy — mà ảnh
 *       do biên tập viên tải lên, không ai kiểm soát được độ sáng góc dưới. Một tấm ảnh trời
 *       nắng là tiêu đề biến mất.
 *   <li><b>Không che mất ảnh.</b> Dải gradient ăn ~35% chiều cao khung — đúng phần thường có
 *       nội dung của một tấm ảnh công trình.
 *   <li><b>Cao bằng cột bên cạnh.</b> Xem mục dưới — đây là mảnh làm được việc ấy.
 * </ul>
 *
 * <h2>⭐ Cao bằng cột tin — bằng CẤU TRÚC, không bằng một con số</h2>
 *
 * Thẻ là {@code flex-col h-full}: khung ảnh {@code shrink-0} giữ đúng chiều cao của nó, còn thẻ
 * chú thích lấy {@code flex-1}. Nên khi lưới cấp cho hàng một chiều cao (bằng cột cao hơn),
 * phần dôi ra rơi vào thẻ trắng chứ không thành một dải nền trống dưới ảnh.
 *
 * <p>⛔ Bản trước dựng khung ảnh bằng {@code aspect-[21/9]}: chiều cao do TỈ LỆ quyết định, nên
 * thẻ không giãn được theo hàng. {@code h-full} trên thẻ ngoài vẫn xanh, vẫn không làm gì —
 * đúng thứ yêu cầu 29/08 gọi là *"bên cao bên thấp"*.
 *
 * <h2>Cả ảnh lẫn tiêu đề là MỘT liên kết — bằng vùng bấm kéo giãn</h2>
 *
 * Yêu cầu: *"phần ảnh slider và text phải convert thành link bài viết"*. Nhưng mũi tên và chấm
 * chỉ mục là {@code <button>}, mà {@code <button>} lồng trong {@code <a>} là HTML không hợp lệ —
 * trình duyệt tự sửa bằng cách cắt cây DOM, và cái bị cắt thường là thứ ta cần.
 *
 * <p>Nên thẻ {@code <a>} chỉ bọc TIÊU ĐỀ, rồi {@code after:absolute after:inset-0} kéo vùng bấm
 * của nó phủ cả thẻ. Mũi tên nhận {@code z-20} nên vẫn nằm trên và vẫn bấm được. Tên gọi của
 * liên kết vì thế là chính tiêu đề bài — không phải một {@code aria-label} viết tay.
 *
 * <h2>⛔ Không có ảnh mặc định</h2>
 *
 * Chưa ai tải ảnh lên thì khối nói thẳng là chưa có. Bản trước của trang chủ có bốn ảnh hotlink
 * từ Unsplash làm nền — chúng khiến một cổng rỗng trông như một cổng đang chạy (§10.54), và
 * `noFabricatedContent.test.ts` nay chặn mọi tên miền ngoài danh sách.
 */
export function HomeBannerSlider({
  banners,
  intervalSeconds,
  autoplay,
  showArrows,
  showDots,
}: HomeBannerSliderProps) {
  const [viTri, datViTri] = useState(0);
  // Tạm dừng khi con trỏ nằm trên ảnh: người đang đọc chú thích mà ảnh tự nhảy là mất nội
  // dung họ đang xem. Cũng là lối thoát cho người dùng bàn phím đang tab qua các nút.
  const [tamDung, datTamDung] = useState(false);
  const soAnh = banners.length;
  const vungRef = useRef<HTMLDivElement>(null);

  const di = useCallback(
    (buoc: number) => {
      if (soAnh === 0) return;
      datViTri((cu) => (cu + buoc + soAnh) % soAnh);
    },
    [soAnh],
  );

  useEffect(() => {
    // ⚠ `intervalSeconds <= 0` phải dừng hẳn, không rơi về một mặc định. Đặt 0 ở màn hình cấu
    //   hình là ý định tắt tự chạy; "thấy 0 thì dùng 5" biến một ô cấu hình thành ô vô nghĩa.
    if (!autoplay || tamDung || soAnh <= 1 || intervalSeconds <= 0) {
      return;
    }
    const dinhKy = setInterval(() => di(1), intervalSeconds * 1000);
    return () => clearInterval(dinhKy);
  }, [autoplay, tamDung, soAnh, intervalSeconds, di]);

  if (soAnh === 0) {
    return (
      <EmptyBlock>
        Chưa có ảnh hoạt động nào được đăng. Ảnh của slider trang chủ do biên tập viên tải lên ở mục
        Banner của trang quản trị.
      </EmptyBlock>
    );
  }

  // ⚠ `viTri` có thể trỏ ra ngoài mảng trong đúng một khung hình: biên tập viên gỡ ảnh cuối
  //   trong lúc trang đang mở, `banners` ngắn lại trước khi `useEffect` kịp chạy. `?? banners[0]`
  //   là lưới an toàn, không phải phòng xa — đọc `.title` của `undefined` là cả trang chủ trắng.
  const hienTai = banners[viTri] ?? banners[0];

  return (
    <section
      aria-roledescription="carousel"
      aria-label="Ảnh hoạt động của Công ty"
      ref={vungRef}
      onMouseEnter={() => datTamDung(true)}
      onMouseLeave={() => datTamDung(false)}
      onFocus={() => datTamDung(true)}
      onBlur={() => datTamDung(false)}
      className="group relative flex h-full flex-col overflow-hidden rounded-lg bg-white shadow-card"
    >
      {/* Khung ảnh — chiều cao CỐ ĐỊNH (220px, 444px từ `lg`) như cổng tham chiếu, và
          `shrink-0` để phần dôi ra của hàng rơi xuống thẻ chú thích bên dưới. */}
      <div className="relative h-[220px] shrink-0 overflow-hidden bg-surface-bgLayout lg:h-[444px]">
        {banners.map((anh, i) => {
          const src = fileUrl(anh.imageId);
          const dangHien = i === viTri;
          return (
            <div
              key={`${anh.imageId}-${i}`}
              role="group"
              aria-roledescription="slide"
              aria-label={`Ảnh ${i + 1} trên ${soAnh}`}
              aria-hidden={!dangHien}
              className={`absolute inset-0 transition-opacity duration-700 ease-smooth ${
                dangHien ? 'opacity-100' : 'pointer-events-none opacity-0'
              }`}
            >
              {src ? (
                <img
                  src={src}
                  alt={anh.title}
                  // Ảnh đầu tải ngay (nó nằm trên màn hình đầu tiên), phần còn lại chờ.
                  loading={i === 0 ? 'eager' : 'lazy'}
                  // ⭐ Ảnh đầu của slider LÀ phần tử LCP của trang chủ — đo 28/08: 381 KB trên
                  //   đường tới hạn. `eager` chỉ nói "đừng hoãn"; `fetchpriority="high"` mới nói
                  //   "xếp trước các tài nguyên khác". DOD1.17 ghi rõ `fetchpriority` chưa từng
                  //   xuất hiện lần nào trong HTML của cổng — đây là chỗ nó phải có.
                  //
                  //   ⛔ Chỉ ảnh ĐẦU. Đặt `high` cho cả năm ảnh là không ưu tiên gì cả, mà còn
                  //      giành băng thông với chính ảnh LCP.
                  fetchPriority={i === 0 ? 'high' : 'auto'}
                  decoding="async"
                  className="h-full w-full object-cover"
                />
              ) : (
                <div className="h-full w-full bg-gradient-to-br from-brand-primaryGradientFrom to-brand-primary" />
              )}
            </div>
          );
        })}

        {/* ⭐ Mũi tên HIỆN SẴN, không chờ rê chuột. Bản trước dùng `opacity-0 group-hover:…`:
            trên màn hình cảm ứng không có sự kiện rê chuột nào, nên hai nút ấy chưa từng
            hiện ra ở đúng nhóm thiết bị mà vuốt-để-chuyển-ảnh cũng chưa có. */}
        {showArrows && soAnh > 1 ? (
          <>
            <button
              type="button"
              onClick={() => di(-1)}
              aria-label="Ảnh trước"
              className="absolute left-3 top-1/2 z-20 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full bg-chrome-navy800/55 text-xl text-white transition-colors duration-200 hover:bg-chrome-navy800/80 sm:left-4"
            >
              <span aria-hidden="true">‹</span>
            </button>
            <button
              type="button"
              onClick={() => di(1)}
              aria-label="Ảnh kế tiếp"
              className="absolute right-3 top-1/2 z-20 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full bg-chrome-navy800/55 text-xl text-white transition-colors duration-200 hover:bg-chrome-navy800/80 sm:right-4"
            >
              <span aria-hidden="true">›</span>
            </button>
          </>
        ) : null}

        {showDots && soAnh > 1 ? (
          <div className="absolute inset-x-0 bottom-4 z-20 flex items-center justify-center gap-2">
            {banners.map((anh, i) => (
              <button
                key={`dot-${anh.imageId}-${i}`}
                type="button"
                onClick={() => datViTri(i)}
                aria-label={`Tới ảnh ${i + 1}`}
                aria-current={i === viTri}
                className={`h-2 rounded-full transition-all duration-300 ${
                  i === viTri ? 'w-6 bg-white' : 'w-2 bg-white/55 hover:bg-white/80'
                }`}
              />
            ))}
          </div>
        ) : null}
      </div>

      {/* Thẻ chú thích — nền trắng, giãn theo chiều cao hàng.
          ⚠ KHÔNG đặt `relative` ở đây: `after:inset-0` của liên kết bên dưới phải neo vào
            THẺ NGOÀI để vùng bấm phủ cả ảnh. Thêm `relative` vào div này là vùng bấm co lại
            còn đúng phần chữ — và không có gì đỏ để ai nhìn thấy. */}
      <div className="flex flex-1 flex-col justify-center gap-2 p-5 sm:p-6">
        {hienTai.title ? (
          <h2 className="text-lg font-bold leading-snug text-brand-primary lg:text-xl">
            {/* CR-10: ảnh KHÔNG bắt buộc có liên kết. Chỉ bọc thẻ <a> khi biên tập viên thật
                sự đặt một đường dẫn — không dựng liên kết trỏ về `#` cho có. */}
            {hienTai.linkUrl ? (
              <a
                href={hienTai.linkUrl}
                target={hienTai.openNewTab ? '_blank' : undefined}
                rel={hienTai.openNewTab ? 'noopener noreferrer' : undefined}
                className="transition-opacity duration-200 after:absolute after:inset-0 hover:opacity-80"
              >
                {hienTai.title}
              </a>
            ) : (
              hienTai.title
            )}
          </h2>
        ) : hienTai.linkUrl ? (
          // Ảnh có liên kết mà biên tập viên chưa nhập tiêu đề: vùng bấm vẫn phải phủ cả thẻ,
          // nhưng KHÔNG bịa một nhãn ("Xem chi tiết") — trình đọc màn hình đọc đúng vị trí ảnh.
          <a
            href={hienTai.linkUrl}
            target={hienTai.openNewTab ? '_blank' : undefined}
            rel={hienTai.openNewTab ? 'noopener noreferrer' : undefined}
            className="after:absolute after:inset-0"
          >
            <span className="sr-only">{`Mở liên kết của ảnh ${viTri + 1} trên ${soAnh}`}</span>
          </a>
        ) : null}
        {hienTai.description ? (
          <p className="line-clamp-2 text-[15px] leading-relaxed text-surface-textSecondary">
            {hienTai.description}
          </p>
        ) : null}
      </div>
    </section>
  );
}
