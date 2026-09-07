import { chiaTheoTuKhoa } from '@/lib/danhDauTuKhoa';

/**
 * Tô phần khớp từ khoá trong một đoạn chữ — CN-01.8 "highlight từ khoá", T36.10.
 *
 * <h2>⛔⛔ Dựng bằng `<mark>` và mảng đoạn, tuyệt đối ⛔ KHÔNG bằng `dangerouslySetInnerHTML`</h2>
 *
 * Cách "rẻ" là `text.replace(re, '<mark>$1</mark>')` rồi bơm vào `dangerouslySetInnerHTML`. Nó
 * là **XSS phản chiếu**: `text` là tiêu đề bài viết (dữ liệu), nhưng từ khoá đến thẳng từ thanh
 * địa chỉ — một liên kết `?q=<img onerror=…>` gửi cho người dùng là mã chạy trên cổng. Ở đây
 * mọi đoạn là **chuỗi**, React escape, và ⛔ không có đường nào để một thẻ ra đời.
 *
 * <h2>⚠ `<mark>` chứ ⛔ không phải một `<span>` màu vàng</h2>
 *
 * `<mark>` mang **ngữ nghĩa** "phần liên quan tới ngữ cảnh hiện tại"; trình đọc màn hình nói ra
 * điều đó. Một `<span className="bg-yellow-200">` trông y hệt trên màn hình và ⛔ không nói gì
 * cho người dùng khiếm thị — đúng loại khác biệt ⛔ không ai thấy khi kiểm bằng mắt.
 *
 * <h2>⚠ Ô rỗng ⇒ ⛔ không dựng gì</h2>
 *
 * Nơi gọi truyền thẳng `summary` (có thể `null`). Trả `null` ở đây để nơi gọi ⛔ không phải nhớ
 * kiểm — quy tắc 12 ở dạng nhỏ nhất.
 */
export function DanhDauTuKhoa({
  chu,
  tuKhoa,
}: {
  chu: string | null | undefined;
  tuKhoa?: string;
}) {
  if (!chu) {
    return null;
  }
  const doan = chiaTheoTuKhoa(chu, tuKhoa);
  return (
    <>
      {doan.map((d, i) =>
        d.khop ? (
          <mark key={i} className="rounded-sm bg-brand-primaryLight px-0.5 text-brand-primary">
            {d.chu}
          </mark>
        ) : (
          <span key={i}>{d.chu}</span>
        ),
      )}
    </>
  );
}
