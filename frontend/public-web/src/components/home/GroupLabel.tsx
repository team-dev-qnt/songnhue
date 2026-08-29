/**
 * Nhãn NHÓM khối trên trang chủ — bố cục 29/08/2026.
 *
 * <p>Trang chủ mới có mười một khối. Không có mốc phân nhóm thì người đọc gặp mười một tiêu đề
 * ngang hàng nhau và phải tự đoán khối nào liên quan khối nào. Nhãn này chia chúng thành năm
 * nhóm: tin tức · điều hành &amp; số liệu · văn bản · tổ chức &amp; đơn vị · truyền thông.
 *
 * <h2>Cố ý NHẠT hơn tiêu đề khối</h2>
 *
 * 11px, giãn chữ, màu xám phụ — đứng cạnh {@link SectionTitle} (20px, in hoa, màu thương hiệu)
 * thì mắt đọc tiêu đề khối trước, nhãn nhóm sau. Làm ngược lại là hai cấp tranh nhau và cả hai
 * cùng mất tác dụng.
 *
 * <p>⚠ Không dùng thẻ tiêu đề (`h2`/`h3`): đây là mốc phân nhóm cho MẮT, còn cây tiêu đề của
 * trang thì tiêu đề khối đã giữ đúng thứ bậc rồi. Chèn thêm một cấp `h` ở đây là trình đọc màn
 * hình đọc ra một mục lục hai tầng cho một trang vốn phẳng.
 */
export function GroupLabel({ children }: { children: React.ReactNode }) {
  return (
    <div className="mt-10 flex items-center gap-3.5 sm:mt-12" aria-hidden="true">
      <span className="h-[15px] w-[3px] shrink-0 rounded-sm bg-brand-primary" />
      <span className="whitespace-nowrap text-[11px] font-bold tracking-[0.16em] text-surface-textSecondary">
        {children}
      </span>
      <span className="h-px flex-1 bg-surface-border/70" />
    </div>
  );
}
