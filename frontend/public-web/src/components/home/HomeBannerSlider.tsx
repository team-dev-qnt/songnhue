import type { BannerItem } from '@/lib/api';
import { fileUrl } from '@/lib/routes';
import { AnhCarousel } from './AnhCarousel';
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
 * <h2>Khối này nay chỉ còn phần ÁNH XẠ DỮ LIỆU</h2>
 *
 * Toàn bộ cơ chế trượt (tự chạy, tạm dừng, mũi tên, chấm chỉ mục, vùng bấm kéo giãn) đã dời
 * xuống {@link AnhCarousel} vì trang chủ có <b>hai</b> slider đọc cùng bộ khoá
 * {@code site.slider.*} — cái thứ hai là dải ảnh cạnh video (yêu cầu 29/08). Giữ hai bản mã
 * trượt là dựng đúng tình huống luật 14 cấm.
 *
 * <p>⭐ {@code uuTienAnhDau} chỉ bật ở ĐÂY: ảnh đầu của slider trang chủ là phần tử LCP. Bật ở
 * cả hai slider là không ưu tiên gì cả, mà còn giành băng thông với chính ảnh LCP.
 *
 * <p>⚠ {@code linkUrl} và {@code description} là dữ liệu của biên tập viên, không bắt buộc.
 * Ảnh chưa có bài viết tương ứng thì thẻ chú thích <b>không</b> thành liên kết — không dựng một
 * liên kết trỏ về {@code #} cho đủ hình dạng.
 */
export function HomeBannerSlider({
  banners,
  intervalSeconds,
  autoplay,
  showArrows,
  showDots,
}: HomeBannerSliderProps) {
  return (
    <AnhCarousel
      muc={banners.map((b, i) => ({
        khoa: `${b.imageId}-${i}`,
        src: fileUrl(b.imageId),
        title: b.title,
        description: b.description,
        linkUrl: b.linkUrl,
        openNewTab: b.openNewTab,
      }))}
      intervalSeconds={intervalSeconds}
      autoplay={autoplay}
      showArrows={showArrows}
      showDots={showDots}
      nhan="Ảnh hoạt động của Công ty"
      // ⭐ 01/09: `lg:min-h-[444px]` → `lg:min-h-[300px]`. Con số 444 là SÀN, và một cái sàn cao
      //    thắng mọi cái trần: trang chủ nay chặn chiều cao Nhóm 1 theo khung nhìn
      //    (`src/app/page.tsx`), mà `min-height` luôn thắng `max-height` trong CSS. Giữ 444 là
      //    cái trần ấy không có hiệu lực trên màn hình thấp — đúng hình dạng "cơ chế canh gác
      //    tồn tại mà không có hiệu lực".
      chieuCaoToiThieu="min-h-[220px] lg:min-h-[300px]"
      // ⭐ 01/09: KHÔNG cắt ảnh. Yêu cầu QuanTran: *"các ảnh luôn hiển thị đủ 100% content ảnh"*.
      phuKhung={false}
      uuTienAnhDau
      khiRong={
        <EmptyBlock>
          Chưa có ảnh hoạt động nào được đăng. Ảnh của slider trang chủ do biên tập viên tải lên ở
          mục Banner của trang quản trị.
        </EmptyBlock>
      }
    />
  );
}
