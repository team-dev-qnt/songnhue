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
 * Slider ảnh hoạt động của Công ty — CR-10.
 *
 * <h2>Thứ nó thay thế</h2>
 *
 * Đầu trang cũ dùng "Giới thiệu chung" như một bài viết đinh. CR-10 đổi bản chất: đây là dải
 * ảnh hoạt động, 10–20 tấm xoay vòng, và <b>ảnh không cần gắn link bài viết</b>. Nên khối này
 * không đọc `articles` — nó chỉ đọc `banners`, thứ biên tập viên tự tải lên và tự xếp thứ tự.
 *
 * <h2>Năm tham số đều đến từ `settings`, không có số nào viết cứng</h2>
 *
 * §2 của tài liệu nói thẳng: *"số ảnh slider, thời gian chuyển ảnh phải cấu hình được, không
 * gán cứng trong mã nguồn"*. Năm khoá `site.slider.*` đã có từ `V202608191020`; đợt này thêm
 * `site.slider.max-items`. Đổi nhịp chạy từ 5 giây xuống 3 giây là một cú bấm trên màn hình
 * cấu hình, không phải một lượt dựng lại image.
 *
 * <h2>⛔ Không có ảnh mặc định</h2>
 *
 * Chưa ai tải ảnh lên thì khối nói thẳng là chưa có. Bản trước của trang chủ có bốn ảnh
 * hotlink từ Unsplash làm nền — chúng khiến một cổng rỗng trông như một cổng đang chạy
 * (§10.54), và `noFabricatedContent.test.ts` nay chặn mọi tên miền ngoài danh sách.
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

  return (
    <section
      aria-roledescription="carousel"
      aria-label="Ảnh hoạt động của Công ty"
      ref={vungRef}
      onMouseEnter={() => datTamDung(true)}
      onMouseLeave={() => datTamDung(false)}
      onFocus={() => datTamDung(true)}
      onBlur={() => datTamDung(false)}
      className="group relative overflow-hidden rounded-xl border border-surface-border bg-surface-bgLayout shadow-sm"
    >
      <div className="relative aspect-[16/9] w-full sm:aspect-[21/9]">
        {banners.map((anh, i) => {
          const src = fileUrl(anh.imageId);
          const dangHien = i === viTri;
          const noiDung = (
            <>
              {src ? (
                <img
                  src={src}
                  alt={anh.title}
                  // Ảnh đầu tải ngay (nó nằm trên màn hình đầu tiên), phần còn lại chờ.
                  loading={i === 0 ? 'eager' : 'lazy'}
                  decoding="async"
                  className="h-full w-full object-cover"
                />
              ) : (
                <div className="h-full w-full bg-gradient-to-br from-brand-primaryGradientFrom to-brand-primary" />
              )}
              {anh.title || anh.description ? (
                <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 via-black/35 to-transparent p-4 sm:p-6">
                  {anh.title ? (
                    <h2 className="text-base font-bold leading-snug text-white drop-shadow-sm sm:text-xl">
                      {anh.title}
                    </h2>
                  ) : null}
                  {anh.description ? (
                    <p className="mt-1 line-clamp-2 text-xs text-white/90 sm:text-sm">
                      {anh.description}
                    </p>
                  ) : null}
                </div>
              ) : null}
            </>
          );

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
              {/* CR-10: ảnh KHÔNG cần gắn link. Chỉ bọc thẻ <a> khi biên tập viên thật sự
                  đặt một đường dẫn — không dựng liên kết trỏ về `#` cho có. */}
              {anh.linkUrl ? (
                <a
                  href={anh.linkUrl}
                  target={anh.openNewTab ? '_blank' : undefined}
                  rel={anh.openNewTab ? 'noopener noreferrer' : undefined}
                  className="block h-full w-full"
                  tabIndex={dangHien ? 0 : -1}
                >
                  {noiDung}
                </a>
              ) : (
                noiDung
              )}
            </div>
          );
        })}
      </div>

      {showArrows && soAnh > 1 ? (
        <>
          <button
            type="button"
            onClick={() => di(-1)}
            aria-label="Ảnh trước"
            className="absolute left-2 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-black/40 text-white opacity-0 transition-opacity duration-200 hover:bg-black/60 focus-visible:opacity-100 group-hover:opacity-100 sm:left-3"
          >
            <span aria-hidden="true">‹</span>
          </button>
          <button
            type="button"
            onClick={() => di(1)}
            aria-label="Ảnh kế tiếp"
            className="absolute right-2 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-black/40 text-white opacity-0 transition-opacity duration-200 hover:bg-black/60 focus-visible:opacity-100 group-hover:opacity-100 sm:right-3"
          >
            <span aria-hidden="true">›</span>
          </button>
        </>
      ) : null}

      {showDots && soAnh > 1 ? (
        <div className="absolute inset-x-0 bottom-2 flex items-center justify-center gap-1.5">
          {banners.map((anh, i) => (
            <button
              key={`dot-${anh.imageId}-${i}`}
              type="button"
              onClick={() => datViTri(i)}
              aria-label={`Tới ảnh ${i + 1}`}
              aria-current={i === viTri}
              className={`h-1.5 rounded-full transition-all duration-300 ${
                i === viTri ? 'w-5 bg-white' : 'w-1.5 bg-white/55 hover:bg-white/80'
              }`}
            />
          ))}
        </div>
      ) : null}
    </section>
  );
}
