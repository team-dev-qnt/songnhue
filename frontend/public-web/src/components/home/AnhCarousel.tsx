'use client';

import { useCallback, useEffect, useState, type ReactNode } from 'react';

import { coTuChay } from '@/lib/slider';

export interface MucCarousel {
  /** Khoá React — dùng id thật của ảnh, không dùng chỉ số mảng. */
  khoa: string;
  /** Địa chỉ ảnh đã giải; `null` ⇒ vẽ nền chuyển sắc thay vì một ô vỡ. */
  src: string | null;
  title: string;
  /** Mô tả ngắn dưới tiêu đề. Rỗng ⇒ không dựng dòng nào. */
  description?: string | null;
  /** Đích của liên kết. Rỗng ⇒ ảnh không bấm được, và KHÔNG dựng liên kết trỏ về `#`. */
  linkUrl?: string | null;
  openNewTab?: boolean;
}

interface AnhCarouselProps {
  muc: MucCarousel[];
  /** Giây mỗi ảnh — `site.slider.interval-seconds`. */
  intervalSeconds: number;
  autoplay: boolean;
  showArrows: boolean;
  showDots: boolean;
  /** `aria-label` của vùng trượt — hai slider trên cùng trang phải gọi tên khác nhau. */
  nhan: string;
  /**
   * Chiều cao TỐI THIỂU của khung ảnh, dạng lớp Tailwind viết nguyên văn ở nơi gọi.
   *
   * ⚠ Phải là một chuỗi hằng ở nơi gọi: bộ quét nguồn của Tailwind đọc mã, không chạy mã —
   * ghép chuỗi lúc chạy thì lớp không được sinh ra và khung tụt về chiều cao 0.
   */
  chieuCaoToiThieu: string;
  /**
   * `fetchpriority="high"` cho ảnh đầu. Chỉ MỘT slider mỗi trang được bật: ảnh đầu của slider
   * trang chủ là phần tử LCP, còn đặt `high` ở chỗ thứ hai là giành băng thông với chính nó.
   */
  uuTienAnhDau?: boolean;
  /**
   * Ảnh có được phép **cắt** cho kín khung không.
   *
   * `true` (mặc định, giữ hành vi cũ) ⇒ `object-cover`: ảnh phủ kín, phần thừa bị cắt.
   * `false` ⇒ `object-contain`: **toàn bộ nội dung ảnh** hiện ra, phần dôi của khung thành nền.
   *
   * ⛔ Đây không phải lựa chọn thẩm mỹ mà là lựa chọn *mất thông tin hay không*. Ảnh hoạt động
   * của Công ty có tỷ lệ bất kỳ (điện thoại chụp dọc, ảnh quét, ảnh sơ đồ); `object-cover` trên
   * một khung 16/9 cắt mất hai đầu của ảnh dọc, và **không ai biết đã mất phần nào** — cùng lý
   * do `PortalImage` có `phuKhung` và ảnh sơ đồ hệ thống dùng `phuKhung={false}`.
   */
  phuKhung?: boolean;
  /** Ô rỗng — phải nói vì sao rỗng và ai là người nhập, không phải một khung xám. */
  khiRong: ReactNode;
}

/**
 * Vùng ảnh trượt — **một** cơ chế dùng cho cả hai slider của trang chủ.
 *
 * <h2>Vì sao gộp</h2>
 *
 * Yêu cầu 29/08: dải ảnh cạnh video *"convert về slider hiển thị image và auto slide lấy đúng
 * theo config của slider tin tức, sự kiện"*. Hai slider đọc cùng bộ khoá {@code site.slider.*},
 * nên chép mã sang chỗ thứ hai là dựng đúng tình huống luật 14 cấm — người sau sửa một bên,
 * bên kia trôi lại, và không có gì đỏ. Điều kiện tự chạy còn được tách xuống {@link coTuChay}
 * để kiểm được bằng bốn dòng thay vì dựng cả một DOM.
 *
 * <h2>⭐ Chú thích nằm DƯỚI ảnh, và phần dôi ra rơi vào ẢNH</h2>
 *
 * Thẻ là {@code flex-col h-full}: thẻ chữ {@code shrink-0} chỉ cao bằng chữ nó mang, khung ảnh
 * lấy {@code flex-1} với một chiều cao tối thiểu. Khi lưới cấp cho hàng một chiều cao (bằng cột
 * cao hơn), phần dôi ra nở ảnh chứ không thành một ô trắng dưới tiêu đề — lỗi đo được sáng
 * 29/08: thẻ chữ nhận <b>216px</b> thừa cho đúng một dòng tiêu đề.
 *
 * <h2>Cả ảnh lẫn tiêu đề là MỘT liên kết — bằng vùng bấm kéo giãn</h2>
 *
 * Mũi tên và chấm chỉ mục là {@code <button>}, mà {@code <button>} lồng trong {@code <a>} là
 * HTML không hợp lệ — trình duyệt tự sửa bằng cách cắt cây DOM. Nên {@code <a>} chỉ bọc TIÊU
 * ĐỀ, rồi {@code after:absolute after:inset-0} kéo vùng bấm của nó phủ cả thẻ; mũi tên nhận
 * {@code z-20} nên vẫn nằm trên và vẫn bấm được. Tên gọi của liên kết vì thế là chính tiêu đề
 * — không phải một {@code aria-label} viết tay.
 *
 * <h2>⛔ Không có ảnh mặc định</h2>
 *
 * Chưa ai tải ảnh lên thì khối nói thẳng là chưa có. Bản trước của trang chủ có bốn ảnh hotlink
 * từ Unsplash làm nền — chúng khiến một cổng rỗng trông như một cổng đang chạy (§10.54).
 */
export function AnhCarousel({
  muc,
  intervalSeconds,
  autoplay,
  showArrows,
  showDots,
  nhan,
  chieuCaoToiThieu,
  phuKhung = true,
  uuTienAnhDau = false,
  khiRong,
}: AnhCarouselProps) {
  const [viTri, datViTri] = useState(0);
  // Tạm dừng khi con trỏ nằm trên ảnh: người đang đọc chú thích mà ảnh tự nhảy là mất nội
  // dung họ đang xem. Cũng là lối thoát cho người dùng bàn phím đang tab qua các nút.
  const [tamDung, datTamDung] = useState(false);
  const soAnh = muc.length;

  const di = useCallback(
    (buoc: number) => {
      if (soAnh === 0) return;
      datViTri((cu) => (cu + buoc + soAnh) % soAnh);
    },
    [soAnh],
  );

  useEffect(() => {
    if (!coTuChay({ autoplay, tamDung, soAnh, intervalSeconds })) return;
    const dinhKy = setInterval(() => di(1), intervalSeconds * 1000);
    return () => clearInterval(dinhKy);
  }, [autoplay, tamDung, soAnh, intervalSeconds, di]);

  if (soAnh === 0) {
    return <>{khiRong}</>;
  }

  // ⚠ `viTri` có thể trỏ ra ngoài mảng trong đúng một khung hình: biên tập viên gỡ ảnh cuối
  //   trong lúc trang đang mở, mảng ngắn lại trước khi `useEffect` kịp chạy. `?? muc[0]` là
  //   lưới an toàn, không phải phòng xa — đọc `.title` của `undefined` là cả trang chủ trắng.
  const hienTai = muc[viTri] ?? muc[0];

  return (
    <section
      aria-roledescription="carousel"
      aria-label={nhan}
      onMouseEnter={() => datTamDung(true)}
      onMouseLeave={() => datTamDung(false)}
      onFocus={() => datTamDung(true)}
      onBlur={() => datTamDung(false)}
      className="group relative flex h-full flex-col overflow-hidden rounded-lg bg-white shadow-card"
    >
      <div className={`relative flex-1 overflow-hidden bg-surface-bgLayout ${chieuCaoToiThieu}`}>
        {muc.map((anh, i) => {
          const dangHien = i === viTri;
          return (
            <div
              key={anh.khoa}
              role="group"
              aria-roledescription="slide"
              aria-label={`Ảnh ${i + 1} trên ${soAnh}`}
              aria-hidden={!dangHien}
              className={`absolute inset-0 transition-opacity duration-700 ease-smooth ${
                dangHien ? 'opacity-100' : 'pointer-events-none opacity-0'
              }`}
            >
              {anh.src ? (
                <img
                  src={anh.src}
                  alt={anh.title}
                  // Ảnh đầu tải ngay (nó nằm trên màn hình đầu tiên), phần còn lại chờ.
                  loading={i === 0 && uuTienAnhDau ? 'eager' : 'lazy'}
                  // ⭐ Ảnh đầu của slider trang chủ LÀ phần tử LCP — đo 28/08: 381 KB trên đường
                  //   tới hạn. `eager` chỉ nói "đừng hoãn"; `fetchpriority="high"` mới nói "xếp
                  //   trước các tài nguyên khác". DOD1.17 ghi rõ thuộc tính này chưa từng xuất
                  //   hiện lần nào trong HTML của cổng — đây là chỗ nó phải có.
                  fetchPriority={i === 0 && uuTienAnhDau ? 'high' : 'auto'}
                  decoding="async"
                  className={`h-full w-full ${phuKhung ? 'object-cover' : 'object-contain'}`}
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
          <div className="absolute inset-x-0 bottom-4 z-20 flex flex-wrap items-center justify-center gap-2 px-4">
            {muc.map((anh, i) => (
              <button
                key={`dot-${anh.khoa}`}
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

      {/* Thẻ chú thích — nền trắng, cao đúng bằng chữ nó mang.
          ⚠ KHÔNG đặt `relative` ở đây: `after:inset-0` của liên kết bên dưới phải neo vào
            THẺ NGOÀI để vùng bấm phủ cả ảnh. Thêm `relative` vào div này là vùng bấm co lại
            còn đúng phần chữ — và không có gì đỏ để ai nhìn thấy. */}
      <div className="flex shrink-0 flex-col gap-2 p-5 sm:p-6">
        {hienTai.title ? (
          // ⚠ Màu xanh thương hiệu chỉ dùng khi tiêu đề THẬT SỰ là liên kết. Ảnh không có bài
          //   tương ứng mà vẫn tô xanh là hứa một cú bấm không tồn tại — cùng họ với việc dựng
          //   một liên kết trỏ về `#` cho đủ hình dạng.
          <h2
            className={`text-lg font-bold leading-snug lg:text-xl ${
              hienTai.linkUrl ? 'text-brand-primary' : 'text-surface-textBase'
            }`}
          >
            {/* Ảnh KHÔNG bắt buộc có liên kết. Chỉ bọc thẻ <a> khi có đường dẫn thật. */}
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
          // Ảnh có liên kết mà chưa có tiêu đề: vùng bấm vẫn phải phủ cả thẻ, nhưng KHÔNG bịa
          // một nhãn ("Xem chi tiết") — trình đọc màn hình đọc đúng vị trí ảnh.
          <a
            href={hienTai.linkUrl}
            target={hienTai.openNewTab ? '_blank' : undefined}
            rel={hienTai.openNewTab ? 'noopener noreferrer' : undefined}
            className="after:absolute after:inset-0"
          >
            <span className="sr-only">{`Mở liên kết của ảnh ${viTri + 1} trên ${soAnh}`}</span>
          </a>
        ) : (
          // Ảnh Công ty gửi mang tên do máy sinh ⇒ backend trả chú thích rỗng (#57). Nói thẳng,
          // không hiện tên tệp và không bịa một câu.
          <p className="text-sm italic text-surface-textSecondary">Ảnh chưa có chú thích</p>
        )}
        {hienTai.description ? (
          <p className="line-clamp-3 text-[15px] leading-relaxed text-surface-textSecondary">
            {hienTai.description}
          </p>
        ) : null}
      </div>
    </section>
  );
}
