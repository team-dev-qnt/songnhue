/**
 * Mục cấp 1 nào đang mở menu con — phần **quyết định**, tách khỏi phần vẽ.
 *
 * <h2>Vì sao tách ra một tệp riêng</h2>
 *
 * Cùng lý do đã tách {@link ./vuaThanhNgang}: `vitest.config.mts` của `public-web` **cố ý không
 * dựng môi trường DOM** ("dựng một tầng mock nửa vời chỉ tạo ra thứ xanh mà không chứng minh
 * gì"). Để logic đóng/mở nằm lẫn trong component là đặt nó ra ngoài tầm với của mọi bài kiểm —
 * và đó chính là chỗ sự cố 01/09 đã sống suốt bốn ngày.
 *
 * <h2>⭐⭐ Sự cố 01/09 — hai menu con cùng mở, chồng lên nhau</h2>
 *
 * QuanTran báo: *"click 1 item trên thanh navigation, dropdown list không disappear, hover vào
 * item khác thì 2 dropdown list sẽ cùng hiển thị chồng lên nhau"*.
 *
 * <p>Nguyên nhân không nằm trong logic React mà nằm ở **một lớp CSS**. Bản trước cho menu con
 * hiện lên nếu **bất kỳ** điều nào đúng:
 *
 * <pre>
 *   1. group-hover:visible          ← con trỏ đang trong mục
 *   2. group-focus-within:visible   ← có phần tử nào đó trong mục đang giữ focus
 *   3. moCap1 === nhãn              ← state React
 * </pre>
 *
 * Bấm một `<button>` thì trình duyệt **để lại focus trên nó** — không sự kiện nào thu về, không
 * dòng mã nào của ta chạy. Vế (2) bật vĩnh viễn cho mục vừa bấm; rê sang mục kế thì vế (1) bật
 * cho mục thứ hai. `datMoCap1(null)` chạy đúng như viết, nhưng nó chỉ tắt được vế (3).
 *
 * <p>⛔ **Quy tắc 14 ở dạng CSS ↔ state**: hai nơi cùng quyết định một điều và không nơi nào đọc
 * được nơi kia. Không đồng bộ được — CSS không có chỗ hỏi `moCap1`. Cách duy nhất đóng hẳn lớp
 * lỗi là bỏ hai vế CSS và để **một biến kiểu `string | null`** làm nguồn duy nhất: hai menu con
 * cùng mở khi ấy không phải "khó xảy ra" mà là **không biểu diễn được**.
 *
 * <p>Hàm này là chỗ vế (3) sống, và là chỗ ba hành vi mà CSS từng lo hộ nay được viết ra tay —
 * mỗi cái kèm một cái bẫy đã đo được, ghi ngay tại nhánh của nó.
 */

/** Nhãn của mục cấp 1 đang mở menu con. `null` = không mục nào. */
export type MucDangMo = string | null;

/**
 * Mọi cách một menu con có thể đổi trạng thái.
 *
 * ⛔ Danh sách này là **đóng**. Thêm một đường mở/đóng mới mà không thêm vào đây là dựng lại
 * đúng tình huống của sự cố: một nguồn quyết định thứ hai mà nguồn thứ nhất không biết.
 */
export type SuKienMenuCap1 =
  /** Con trỏ vào vùng mục. `loaiContro` lấy nguyên từ `PointerEvent.pointerType`. */
  | { loai: 'contro-vao'; nhan: string; loaiContro: string }
  | { loai: 'contro-ra'; nhan: string; loaiContro: string }
  /** Bấm vào mục `NONE` (mục chỉ để mở menu con). */
  | { loai: 'bam'; nhan: string }
  /** Focus đến **từ bàn phím** — đã lọc `:focus-visible` ở nơi gọi. */
  | { loai: 'focus-ban-phim'; nhan: string }
  /** Focus rời hẳn mục (không còn nằm trong mục ấy). */
  | { loai: 'roi-focus'; nhan: string }
  /** Mũi tên xuống — lối vào bàn phím không phụ thuộc `:focus-visible`. */
  | { loai: 'mui-ten-xuong'; nhan: string }
  /** Bấm một liên kết, hoặc bấm ra ngoài thanh, hoặc Esc. */
  | { loai: 'dong-het' };

export function menuCap1KeTiep(dangMo: MucDangMo, e: SuKienMenuCap1): MucDangMo {
  switch (e.loai) {
    case 'contro-vao':
      // ⚠ Chỉ chuột. Trên máy tính bảng một cú chạm bắn `pointerenter` TRƯỚC `click`; mở ở đây
      //   rồi để nhánh `bam` đảo trạng thái ngay sau là menu con loé lên rồi tắt — đúng lỗi
      //   "nút không phản hồi" mà bản 28/08 đã sửa một lần. Chạm đi hẳn qua đường `bam`.
      return e.loaiContro === 'mouse' ? e.nhan : dangMo;

    case 'contro-ra':
      // ⚠⚠ Phải SO NHÃN trước khi xoá. Bản trước truyền `datMoCap1(null)` trần, nên con trỏ rời
      //    mục A trong khi mục B mới là mục đang mở sẽ tắt oan B. Một lệnh ghi không hỏi mình
      //    đang ghi đè cái gì — cùng họ với lớp lỗi §10.70.
      return e.loaiContro === 'mouse' && dangMo === e.nhan ? null : dangMo;

    case 'bam':
      // Đảo trạng thái: bấm lần nữa thì đóng. Đây là đường DUY NHẤT của thiết bị cảm ứng.
      return dangMo === e.nhan ? null : e.nhan;

    case 'focus-ban-phim':
    case 'mui-ten-xuong':
      return e.nhan;

    case 'roi-focus':
      return dangMo === e.nhan ? null : dangMo;

    case 'dong-het':
      return null;
  }
}
