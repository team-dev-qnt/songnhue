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
      // ⭐⭐ 01/09 (lượt hai): TỈ LỆ thay cho chiều cao. Lượt một sáng nay hạ
      //    `lg:min-h-[444px]` → `300px` để cái trần theo khung nhìn có hiệu lực; cả trần lẫn
      //    sàn nay đều bỏ, vì trần ấy đặt sai tầng và chưa từng chặn được gì (xem javadoc
      //    Nhóm 1 ở `app/page.tsx`).
      //    16/9 không phải con số chọn cho vừa mắt: `docs/ui-styles.md:212` ghi "Cột 8: Tin
      //    đinh 16:9", và cổng tham chiếu ghi cứng `lg:h-[444px]` ở cột 8/12 khung 1232px —
      //    785/444 = 1,77, tức họ cũng đang làm 16:9, chỉ viết bằng pixel.
      tiLeKhung="aspect-[16/9]"
      // ⭐ 01/09 lượt hai: TRỞ LẠI `object-cover` (bỏ `phuKhung={false}`).
      //    Lượt một đặt `contain` theo yêu cầu *"ảnh luôn hiển thị đủ 100% content ảnh"*, nhưng
      //    trên một khung có tỉ lệ tai nạn (đo được 1,357 / 3,273 / 1,559) thì `contain` sinh ra
      //    đúng "diện tích thừa" QuanTran báo ở lượt sau. QuanTran đã chốt 01/09: chấp nhận cắt
      //    mép để hết thừa. Cổng tham chiếu cùng hướng — `object-cover` 90 lần / `contain` 2 lần.
      //    ⛔ Ảnh SƠ ĐỒ hệ thống ở Danh mục công trình vẫn giữ `contain`: bản vẽ kỹ thuật bị cắt
      //       mép là mất thông tin, không phải mất thẩm mỹ.
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
