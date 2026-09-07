/**
 * Kiểm mã màu của một mã tình hình vận hành trước khi đưa vào thuộc tính `style`.
 *
 * <h2>Vì sao phải kiểm</h2>
 *
 * Màu badge do **Công ty tự đặt** trong danh mục mã (CRUD đầy đủ — chốt G4, quy tắc 16: danh mục
 * khách vận hành là dữ liệu, không phải enum trong mã). Nghĩa là giá trị này đi từ một ô nhập ở
 * màn hình quản trị thẳng ra cổng công khai, và mọi giá trị đi qua một đường như thế đều phải được
 * kiểm ở **chỗ dữ liệu đi qua**, không ở từng nơi gọi (quy tắc 12, §10.31).
 *
 * <p>Cột `color_hex` khai `length = 7` ở backend, nhưng ràng buộc độ dài không nói gì về hình dạng:
 * một chuỗi bảy ký tự vẫn có thể là `red;x:` hay `url(a`. React tự bỏ giá trị không hợp lệ, nên đây
 * không phải một lỗ đang chảy máu — nó là lớp chặn để nó không bao giờ thành một lỗ khi ai đó
 * chuyển từ `style` sang chuỗi CSS ghép tay.
 *
 * ⛔ Danh sách **CHO PHÉP** (đúng `#rrggbb`), không phải danh sách cấm — cùng bài học §10.52.
 */
const MA_MAU = /^#[0-9a-fA-F]{6}$/;

/**
 * @returns chính mã màu khi nó đúng dạng `#rrggbb`; `null` cho mọi giá trị khác — nơi gọi rơi về
 *   màu trung tính của hệ thiết kế thay vì vẽ một badge không màu.
 */
export function mauTrangThaiHopLe(hex: string | null | undefined): string | null {
  const chu = (hex ?? '').trim();
  return MA_MAU.test(chu) ? chu : null;
}
