/**
 * `max-h-*` đặt trên một khung `grid` mà không có gì cắt phần tràn — lỗi 01/09.
 *
 * <h2>Cơ chế</h2>
 *
 * Hàng lưới ngầm được định cỡ bằng `grid-auto-rows: auto`, tức `minmax(auto, max-content)`.
 * Thuật toán định cỡ track **không có bước nào** ép một track `auto` co lại cho vừa
 * `max-height` của khung bao — chia phần thừa chỉ tồn tại với track `fr`. Nên `max-height`
 * kẹp **cái hộp**, còn **hàng** vẫn cao bằng mục cao nhất và con vẽ tràn ra ngoài
 * (`overflow: visible` là mặc định).
 *
 * <p>Số đo 01/09 trên trình duyệt thật, `page.tsx` bản 31/08, bề rộng 1366×768:
 * hộp Nhóm 1 đáy **756,0px** · mực vẽ thật **992,4px** · đỉnh khối kế **792,0px**
 * ⇒ **chồng lấn 200,4px**. Đúng ảnh chụp màn hình QuanTran gửi.
 *
 * <h2>Vì sao cần một bộ canh riêng thay vì chỉ sửa một dòng</h2>
 *
 * Chuỗi lớp `grid ... lg:max-h-[calc(100svh-17rem)]` **đọc rất hợp lý**. Người viết nó tin
 * rằng trần sẽ chặn hàng, và không có gì trong mã nói ngược lại — 204 bài kiểm của kho đều
 * đọc chuỗi class, mà chuỗi class thì đúng. Cái sai nằm ở tầng CSS, chỗ không ai kiểm.
 * Đây là một luật CSS, nên nó cần một phép kiểm về LUẬT, không phải một lượt sửa.
 */

/** Một khai báo `max-h-*` trên khung lưới, kèm lý do vì sao nó không có hiệu lực. */
export interface ViPhamTranLuoi {
  lop: string;
  ly_do: string;
}

const CO_GRID = /(?:^|\s|:)grid(?:$|\s)/;
const CO_MAX_H = /(?:^|\s)(?:[a-z]+:)?max-h-/;
/** `overflow-hidden`, `overflow-y-auto`, `overflow-clip`, kèm cả biến thể theo điểm ngắt. */
const CO_OVERFLOW = /(?:^|\s)(?:[a-z]+:)?overflow(?:-[xy])?-(?:hidden|auto|scroll|clip)/;
/** Khai báo tường minh chiều cao HÀNG — khi đó trần mới thật sự chặn được. */
const CO_ROW_TRACK = /(?:^|\s)(?:[a-z]+:)?(?:grid-rows-|auto-rows-)/;

/**
 * `null` = không vi phạm. Ngược lại trả lý do đọc được.
 *
 * Chỉ soi chuỗi có **cả** `grid` lẫn `max-h-`: `max-h` trên một khối thường (ngăn kéo của
 * `PortalNav` chẳng hạn) là hoàn toàn hợp lệ và không được báo.
 */
export function viPhamTranLuoi(lop: string): ViPhamTranLuoi | null {
  if (!CO_GRID.test(lop) || !CO_MAX_H.test(lop)) return null;
  if (CO_OVERFLOW.test(lop)) return null;
  if (CO_ROW_TRACK.test(lop)) return null;
  return {
    lop,
    ly_do:
      'max-h-* trên khung `grid` không chặn được HÀNG (grid-auto-rows: auto = max-content). ' +
      'Thêm `overflow-*` để cắt, hoặc khai `grid-rows-*`/`auto-rows-*`, hoặc bỏ trần và để ' +
      'một cột định chiều cao hàng.',
  };
}
