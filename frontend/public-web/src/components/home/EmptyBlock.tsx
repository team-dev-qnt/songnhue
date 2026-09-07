/**
 * Ô nội dung chưa có nguồn — nói thẳng là chưa có, kèm lý do.
 *
 * <h3>Vì sao cần một component riêng cho việc "không hiển thị gì"</h3>
 *
 * Trước bản vá này, bảy khối của trang chủ đều có một mảng `DEFAULT_*` viết cứng để lấp chỗ
 * trống: 14 bài viết, 4 văn bản có số hiệu và người ký, 5 trạm quan trắc có mực nước, 8 xí
 * nghiệp có số điện thoại. Chúng làm cho **đường dữ liệu hỏng hoàn toàn trông y hệt đường dữ
 * liệu chạy đúng** — chỉ khác ở chỗ tên bài không có thật.
 *
 * Hậu quả đo được ngày 25/8: cổng staging phục vụ một trang chủ đầy nội dung bịa sau mỗi lượt
 * triển khai, và không ai nhìn ra là nó rỗng. Xem `architecture-review.md` §10.54.
 *
 * ⛔ Đây là luật 16 của `CLAUDE.md` ở dạng cụ thể: *ô số liệu chưa có nguồn phải trả rỗng kèm
 *    lý do*. Ràng buộc ép ở component này, không ép bằng lời dặn — nơi gọi chỉ cần truyền
 *    mảng rỗng là tự đi đúng đường.
 */
export function EmptyBlock({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-center rounded-lg border border-dashed border-surface-border bg-surface-bgLayout/60 px-4 py-8 text-center">
      <p className="text-xs text-surface-textSecondary">{children}</p>
    </div>
  );
}
