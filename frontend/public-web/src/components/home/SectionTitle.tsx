import Link from 'next/link';

/**
 * Tiêu đề một khối trên trang chủ — bố cục 29/08/2026.
 *
 * <p>Số đo lấy từ HTML thật của cổng tham chiếu: chữ 20px đậm, IN HOA, đệm dưới 17px, kẻ
 * chân 1px. Khai **một lần** ở đây rồi dùng lại: mười một khối của trang chủ mà mỗi khối tự
 * viết lớp riêng thì chỉ cần một chỗ lệch cỡ chữ là cả trang mất nhịp, và lệch cỡ chữ là thứ
 * không cổng kiểm nào bắt được.
 *
 * <h2>⚠ Đây là chỗ ép chữ hoa THỨ HAI của cổng, và nó có ngưỡng đếm</h2>
 *
 * {@code noForcedUppercase} vốn chỉ tha cho {@code PortalNav}. Công ty duyệt bố cục mới với
 * tiêu đề khối in hoa, nên bài kiểm được nới thêm ĐÚNG tệp này với ĐÚNG một chỗ ép — thêm chỗ
 * thứ hai vào đây cũng làm bài kiểm đỏ. Ép hoa bằng CSS chỉ được phép ở nhãn do CHÍNH GIAO
 * DIỆN đặt ra; giá trị người dùng nhập (tên Công ty, nhãn menu cấp 2, tiêu đề bài) vẫn hiện
 * nguyên văn — CR-42.
 */
interface SectionTitleProps {
  children: React.ReactNode;
  /** Có địa chỉ thì tiêu đề thành liên kết, như cổng tham chiếu. */
  href?: string | null;
  /** Nút/nhãn phụ dồn về phải trên cùng đường kẻ chân. */
  phu?: React.ReactNode;
  className?: string;
}

export function SectionTitle({ children, href, phu, className = '' }: SectionTitleProps) {
  const chu = (
    <span className="text-lg font-bold uppercase text-brand-primary transition-colors duration-200 lg:text-xl">
      {children}
    </span>
  );

  return (
    <div
      className={`flex flex-wrap items-end justify-between gap-3 border-b border-surface-border pb-[17px] ${className}`}
    >
      {href ? (
        <Link href={href} className="hover:opacity-80">
          {chu}
        </Link>
      ) : (
        chu
      )}
      {phu}
    </div>
  );
}
