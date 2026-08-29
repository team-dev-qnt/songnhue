/**
 * Ô ảnh của cổng — **một** nơi giữ luật hiển thị ảnh cho toàn bộ trang công khai.
 *
 * <h2>Vì sao phải gom về một component</h2>
 *
 * Bố cục mới của trang chủ dùng ảnh ở <b>bảy</b> cỡ khác nhau trong cùng một trang: ảnh
 * slider 800×444, thẻ bài 380×240, ảnh nhỏ trong danh sách 120×67, ô ảnh chuyên mục
 * 386×220, dải logo liên kết 377×90, ảnh video 700×360, ô ảnh trên điện thoại 103×68.
 * Cùng một tệp gốc phải vừa cả bảy khung mà không méo, không bị cắt mất phần chính, và
 * không làm nhảy bố cục lúc tải.
 *
 * <p>Ba luật dưới đây trước nay chép tay ở từng component, nên chỉ cần một chỗ quên là ở
 * đó ảnh méo hoặc trang nhảy — mà quên thì không bộ kiểm nào thấy:
 *
 * <ol>
 *   <li><b>Khung mang tỉ lệ, ảnh phủ khung.</b> Khung ngoài giữ {@code aspect-*}, ảnh bên
 *       trong {@code h-full w-full object-cover}. Kích thước thật của tệp không còn quyết
 *       định chiều cao ô ⇒ đổi ô to nhỏ thế nào ảnh cũng vừa.
 *   <li><b>Khung tồn tại TRƯỚC khi ảnh về.</b> {@code aspect-*} đặt chỗ ngay từ lượt vẽ
 *       đầu; không có nó thì ô cao 0 rồi bung ra khi ảnh tải xong — bố cục nhảy, và đó là
 *       một trong ba chỉ số Core Web Vitals mà NFR-02 phải giữ.
 *   <li><b>Ô rỗng vẫn là một ô.</b> Không có ảnh thì vẽ khung xám cùng tỉ lệ, KHÔNG trả
 *       {@code null} — trả null là lưới co lại và các thẻ cạnh nhau cao thấp so le.
 * </ol>
 *
 * <h2>⚠ Vì sao là {@code <img>} chứ không phải {@code next/image}</h2>
 *
 * Quyết định cũ ở {@code architecture-review.md} §10.9: bộ tối ưu của Next đòi {@code sharp},
 * và cùng những ảnh này còn phải hiện được ở {@code admin-app} (Vite) nên không đi qua Next.
 * Bù lại bằng {@code loading}/{@code decoding}/{@code fetchpriority} khai tay ở đây.
 */
interface PortalImageProps {
  /** Địa chỉ ảnh đã giải; `null` ⇒ vẽ ô rỗng cùng tỉ lệ. */
  src: string | null;
  /**
   * Chữ thay ảnh. Chuỗi rỗng là **có chủ đích** cho ảnh trang trí đi kèm một tiêu đề đã
   * nói đúng nội dung đó — đọc lại tiêu đề lần thứ hai làm phiền người dùng đọc màn hình.
   */
  alt: string;
  /** Lớp tỉ lệ khung của Tailwind. Đổi tỉ lệ là đổi ở đây, không đổi bằng chiều cao cố định. */
  ratio?: string;
  /** Lớp cho KHUNG ngoài — bo góc, viền, `h-full` khi khung phải giãn theo lưới. */
  className?: string;
  /**
   * Ảnh nằm trong màn hình đầu tiên (slider trang chủ). Chỉ bật cho **một** ảnh mỗi trang:
   * `fetchpriority="high"` mà rải khắp nơi thì không còn ưu tiên gì nữa.
   */
  priority?: boolean;
}

export function PortalImage({
  src,
  alt,
  ratio = 'aspect-[16/10]',
  className = '',
  priority = false,
}: PortalImageProps) {
  return (
    <div className={`relative ${ratio} w-full overflow-hidden bg-surface-bgLayout ${className}`}>
      {src ? (
        <img
          src={src}
          alt={alt}
          loading={priority ? 'eager' : 'lazy'}
          decoding="async"
          fetchPriority={priority ? 'high' : 'auto'}
          className="absolute inset-0 h-full w-full object-cover object-center"
        />
      ) : (
        <div
          aria-hidden="true"
          className="absolute inset-0 flex items-center justify-center bg-surface-bgLayout text-surface-border"
        >
          <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
            />
          </svg>
        </div>
      )}
    </div>
  );
}
