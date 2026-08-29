'use client';

import { useEffect, useState } from 'react';

/**
 * Đồng hồ trên dải thông tin — chạy ở TRÌNH DUYỆT, cố ý.
 *
 * <h2>⛔ Vì sao không lấy giờ máy chủ như "Cập nhật lúc"</h2>
 *
 * {@code getServerTime()} khai {@code revalidate: 0}. Dải thông tin nằm trong
 * {@link SiteHeader}, tức trên MỌI trang — gọi nó ở đó là ép toàn bộ cổng dựng lại ở từng
 * lượt truy cập, mất sạch ISR và kéo theo DOD1.17 (trang chủ &lt; 3s, NFR-02). Cái giá quá
 * lớn cho một chiếc đồng hồ.
 *
 * <p>Phân biệt hai loại thời gian, và đây là chỗ hay lẫn: <b>mốc của một dữ liệu</b>
 * ("số liệu này đo lúc nào") phải là giờ máy chủ, vì nó là một sự kiện đã xảy ra; còn
 * <b>giờ treo tường</b> ("bây giờ là mấy giờ") thì không thuộc về dữ liệu nào cả. Khối
 * "Cập nhật lúc" của mực nước / vận hành vẫn dùng {@code getServerTime()} như cũ.
 *
 * <h2>Vẫn phải là giờ Việt Nam, không phải giờ máy người xem</h2>
 *
 * {@code timeZone: 'Asia/Ho_Chi_Minh'} khai tay. Đọc mặc định của trình duyệt thì một người
 * mở cổng từ nước ngoài sẽ thấy một giờ khác hẳn giờ trực ban — đúng thứ quy tắc 1 cấm
 * (lưu UTC, hiện UTC+7), chỉ khác là ở đây nó rơi vào phía hiển thị.
 *
 * <h2>Vì sao lượt vẽ đầu tiên trả rỗng</h2>
 *
 * Máy chủ và trình duyệt không thể cùng đọc ra một giây. Vẽ giờ ở phía máy chủ là chắc chắn
 * lệch khi React đối chiếu, và React sẽ vứt cả cây con đó. Nên lượt đầu để trống, hiện sau
 * khi gắn — một ô trống trong 50ms rẻ hơn một cảnh báo hydrate.
 */
const DINH_DANG = new Intl.DateTimeFormat('vi-VN', {
  weekday: 'long',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
  timeZone: 'Asia/Ho_Chi_Minh',
});

export function PortalClock() {
  const [nhan, setNhan] = useState<string>('');

  useEffect(() => {
    const ve = () => setNhan(DINH_DANG.format(new Date()));
    ve();
    const id = window.setInterval(ve, 1000);
    return () => window.clearInterval(id);
  }, []);

  return (
    <span suppressHydrationWarning className="whitespace-nowrap">
      {nhan ? `${nhan} GMT+7` : ' '}
    </span>
  );
}
