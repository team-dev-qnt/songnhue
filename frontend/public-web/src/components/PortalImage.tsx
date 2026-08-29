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
 * <h2>⭐⭐ Bề rộng có đúng MỘT chủ: prop {@code rong}</h2>
 *
 * Khung ngoài luôn mang một lớp bề rộng, mặc định {@code w-full}. Muốn khác thì truyền
 * {@code rong}, <b>không</b> nhét vào {@code className}.
 *
 * <p>⛔ Vì sao đây là một luật chứ không phải sở thích — đo được ngày 29/08 trên CSS đang chạy:
 * {@code .w-\[103px\]} nằm ở byte <b>28977</b>, {@code .w-full} ở byte <b>29021</b>. Hai lớp cùng
 * độ ưu tiên, nên lớp đứng SAU thắng — và lớp đứng sau luôn là {@code w-full}, vì Tailwind xếp
 * giá trị tùy biến trước giá trị có sẵn. Kết quả: mọi nơi gọi truyền {@code w-[103px]} qua
 * {@code className} đều nhận một ảnh <b>kín bề rộng cột</b>.
 *
 * <p>Hậu quả đo được trên trang chủ hôm ấy — và đây mới là phần đắt: ảnh nở ra kín cả hàng
 * {@code flex}, ô chữ bên cạnh ({@code min-w-0}) co về <b>bề rộng 0</b>, nên <b>tiêu đề bài viết
 * biến mất</b> còn ngày tháng tràn sang cột bên. Ba nơi gọi cùng dính: danh sách tin theo chuyên
 * mục, cột tin cạnh slider, dải ảnh cạnh video. Không có gì đỏ ở bất kỳ đâu: lớp vẫn đúng tên,
 * vẫn được sinh ra trong CSS, chỉ là không thắng.
 *
 * <p>Cách chữa không phải {@code !w-[103px]} mà là <b>bỏ hẳn cuộc đua</b>: {@code w-full} không
 * còn được phát ra khi nơi gọi đã đặt {@code rong}, vì cả hai dùng chung một ô trong chuỗi lớp.
 * Hai lớp không bao giờ cùng xuất hiện thì không có thứ tự nào để mà phụ thuộc.
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
  /**
   * **Bề rộng khung** — mặc định `w-full`. Chỗ DUY NHẤT được đặt bề rộng cho ô ảnh.
   *
   * ⛔⛔ Đặt bề rộng trong {@link className} thì nó KHÔNG có tác dụng, và không có gì đỏ.
   *    Xem khối "Bề rộng có đúng MỘT chủ" ở đầu tệp — đây là chỗ chữa của lỗi đo được ngày
   *    29/08. `noPortalImageWidth.test.ts` canh cho không ai đặt nhầm chỗ nữa.
   */
  rong?: string;
  /**
   * `true` (mặc định) ⇒ ảnh **phủ** khung và bị cắt cho vừa (`object-cover`) — đúng cho ảnh minh
   * hoạ, nơi mất một dải mép không sao.
   *
   * <p>`false` ⇒ ảnh **vừa trọn** trong khung (`object-contain`), có thể chừa nền hai bên. Dùng
   * cho thứ mà cắt đi là mất thông tin: sơ đồ kỹ thuật, logo, bản vẽ. Cắt một sơ đồ hệ thống là
   * cắt mất một đoạn tuyến sông, và **không ai biết đoạn nào vừa mất**.
   *
   * ⛔ Đây là một PROP chứ không phải một lớp truyền qua {@link className}, và lý do là bài học
   * đo được ngày 29/08: `object-cover` nằm sẵn trên thẻ `<img>`, nên một lớp cạnh tranh phải
   * thắng nó bằng độ ưu tiên CSS — đúng cuộc đua đã làm biến mất tiêu đề bài viết trên trang
   * chủ. Hai lớp không bao giờ cùng được phát ra thì không có thứ tự nào để mà phụ thuộc.
   */
  phuKhung?: boolean;
  /** Lớp cho KHUNG ngoài — bo góc, viền, `shrink-0`, `h-full` khi khung phải giãn theo lưới. */
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
  rong = 'w-full',
  phuKhung = true,
  className = '',
  priority = false,
}: PortalImageProps) {
  return (
    <div className={`relative ${ratio} ${rong} overflow-hidden bg-surface-bgLayout ${className}`}>
      {src ? (
        <img
          src={src}
          alt={alt}
          loading={priority ? 'eager' : 'lazy'}
          decoding="async"
          fetchPriority={priority ? 'high' : 'auto'}
          className={`absolute inset-0 h-full w-full object-center ${
            phuKhung ? 'object-cover' : 'object-contain'
          }`}
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
