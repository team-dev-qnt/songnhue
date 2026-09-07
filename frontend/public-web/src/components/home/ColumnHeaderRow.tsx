interface ColumnHeaderRowProps {
  cot: readonly string[];
  /**
   * Lớp `grid-cols-[…]` của riêng khối — tỉ lệ cột KHÔNG chia đều: "Công trình / điểm đo" cần
   * gấp đôi "Lý trình". Truyền từ nơi gọi để Tailwind nhìn thấy chuỗi lúc biên dịch.
   */
  luoi: string;
  /** Bề rộng tối thiểu của hàng trước khi nó chuyển sang cuộn ngang — `min-w-[…]`. */
  beRongToiThieu: string;
}

/**
 * Hàng tiêu đề cột của một bảng số liệu trên trang chủ — bố cục 29/08/2026.
 *
 * <h2>⛔ Không ép chữ hoa, dù bản vẽ có</h2>
 *
 * Bản vẽ chép {@code text-transform: uppercase} từ cổng tham chiếu. Ở đây thì không, và lý do
 * đã được trả giá: chữ hoa tiếng Việt chồng dấu — "MỰC NƯỚC THƯỢNG LƯU" khó đọc hơn hẳn bản
 * chữ thường, và {@code noForcedUppercase} chỉ tha cho {@code PortalNav} với
 * {@code SectionTitle}, mỗi tệp một ngưỡng đếm. Thêm một chỗ ép hoa ở đây là làm bài kiểm ấy
 * đỏ, đúng như nó được dựng ra để làm.
 *
 * <h2>Cuộn ngang, không xuống dòng</h2>
 *
 * Tám cột trên màn hình điện thoại là tám cột rộng ~45px — mỗi tiêu đề vỡ thành bốn dòng và
 * bảng cao hơn màn hình trước khi có dòng dữ liệu nào. Nên hàng giữ bề rộng tối thiểu rồi cuộn
 * ngang trong khung của chính nó; thân trang không bao giờ cuộn ngang theo.
 */
export function ColumnHeaderRow({ cot, luoi, beRongToiThieu }: ColumnHeaderRowProps) {
  return (
    <div className="overflow-x-auto border-y border-surface-border bg-surface-bgLayout">
      <div className={`grid ${luoi} ${beRongToiThieu}`}>
        {cot.map((ten) => (
          <div
            key={ten}
            className="px-3.5 py-2.5 text-[11px] font-bold leading-tight text-surface-textSecondary"
          >
            {ten}
          </div>
        ))}
      </div>
    </div>
  );
}
