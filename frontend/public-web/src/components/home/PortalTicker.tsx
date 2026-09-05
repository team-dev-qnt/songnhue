import Link from 'next/link';

/**
 * Dải chữ chạy dưới thanh điều hướng — bố cục 29/08/2026.
 *
 * <p>Thay cho hai dòng tĩnh "giờ làm việc" và "email" trước đây: cùng chỗ ấy nay chở được
 * tiêu đề các bài mới nhất mà không tốn thêm chiều cao nào.
 *
 * <h2>Vì sao danh sách được vẽ hai lần</h2>
 *
 * Vòng lặp mượt cần bản sao thứ hai đứng sẵn ở chỗ bản thứ nhất vừa rời đi — xem chú thích
 * {@code sn-ticker} trong {@code globals.css}. Bản sao là ảnh trang trí thuần: nó mang
 * {@code aria-hidden} để trình đọc màn hình không đọc mọi tiêu đề hai lần.
 *
 * <h2>⛔ Không có mục nào viết trong tệp này</h2>
 *
 * Bài lấy từ API, giờ làm việc và thư điện tử lấy từ {@code settings}. Danh sách rỗng ⇒
 * <b>không vẽ dải</b>, chứ không rơi về một câu chào mặc định: một dải chữ chạy nói "Chào
 * mừng đến với cổng thông tin" là đúng thứ §10.54 gọi là lấp chỗ trống cho trang đỡ rỗng.
 */
export interface TickerItem {
  text: string;
  /** Có địa chỉ thì mục thành liên kết; mục thông tin (giờ làm việc, email) thì không. */
  href?: string;
}

interface PortalTickerProps {
  items: TickerItem[];
  /**
   * Số giây cho một vòng. Tính theo số mục để dải dài không chạy nhanh hơn dải ngắn —
   * tốc độ đọc của người xem không đổi theo số bài Công ty đăng.
   */
  giayMoiMuc?: number;
}

const GIAY_MOI_MUC_MAC_DINH = 6;

export function PortalTicker({ items, giayMoiMuc = GIAY_MOI_MUC_MAC_DINH }: PortalTickerProps) {
  if (items.length === 0) return null;

  const thoiLuong = `${items.length * giayMoiMuc}s`;

  const day = (aria: boolean) =>
    items.map((m, i) => {
      const noiDung = (
        <>
          <svg className="h-3 w-3 shrink-0 text-brand-primary" viewBox="0 0 7 7" fill="none">
            <path fill="currentColor" d="M6.469 3.5.445 6.978V.022z" />
          </svg>
          <span className="text-[15px]">{m.text}</span>
        </>
      );
      return (
        <div
          key={`${aria ? 'a' : 'b'}-${i}`}
          className="flex items-center gap-2.5 whitespace-nowrap pr-10"
        >
          {m.href ? (
            <Link
              href={m.href}
              tabIndex={aria ? 0 : -1}
              className="flex items-center gap-2.5 text-surface-textBase hover:text-brand-primary"
            >
              {noiDung}
            </Link>
          ) : (
            <span className="flex items-center gap-2.5 text-surface-textSecondary">{noiDung}</span>
          )}
        </div>
      );
    });

  return (
    <div
      className="sn-ticker min-w-0 flex-1 overflow-hidden py-3"
      style={{ ['--sn-ticker-duration' as string]: thoiLuong }}
    >
      <div className="sn-ticker-track">
        {day(true)}
        <div aria-hidden="true" className="flex">
          {day(false)}
        </div>
      </div>
    </div>
  );
}
