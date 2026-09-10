import { boDau } from './boDau';

/**
 * Tô sáng phần khớp từ khoá trên danh bạ — CN-04.6 đòi *"highlight"*.
 *
 * ## ⛔⛔ Phép so ở đây phải BỎ DẤU giống hệt backend, ⛔ không được so chuỗi thẳng
 *
 * Backend tìm bằng `sn_khong_dau(...) LIKE sn_khong_dau(...)`, tức người dùng gõ `hoa` **có** ra
 * `Nguyễn Văn Hoà`. Nếu giao diện tô sáng bằng một phép so chuỗi thẳng thì kết quả trả về **đúng
 * người** mà **⛔ không tô gì** — và người dùng đọc cái đó thành *"hệ thống tìm sai"*.
 *
 * ⇒ Đây là **luật 14** ở dạng khó thấy: một quy tắc chuẩn hoá văn bản sống ở **hai** nơi (một hàm
 * PL/pgSQL và một hàm TypeScript) mà ⛔ không có cách nào bắt chúng dùng chung mã. Bù bằng bài kiểm
 * {@code toSang.test.tsx} liệt đúng những cặp mà `sn_khong_dau` xử lý.
 *
 * ⚠ Tách khỏi `DanhBaPage.tsx` ⛔ không phải cho gọn: `lazyPage` đòi module trang chỉ export
 * `ComponentType` ⛔ không có prop bắt buộc, nên một hàm trợ giúp export cùng chỗ làm `tsc` đỏ.
 */
export function ToSang({ van, tuKhoa }: { van: string; tuKhoa: string }) {
  const khoa = boDau(tuKhoa).trim();
  if (!khoa) {
    return <>{van}</>;
  }
  // ⚠⚠ Chuẩn hoá về NFC TRƯỚC khi cắt. `boDau` giữ nguyên độ dài **so với bản NFC** (mỗi ký tự
  //   tiếng Việt tổ hợp lại thành đúng một ký tự rồi mới bỏ dấu). Nếu chuỗi vào đã ở dạng NFD —
  //   chuyện xảy ra thật với dữ liệu gõ trên macOS — thì chỉ số tìm được sẽ lệch và ô tô sáng cắt
  //   giữa một ký tự. Một lỗi hiển thị chỉ xuất hiện với một số máy nhập liệu là loại lỗi ⛔ không
  //   ai tái lập được.
  const goc = van.normalize('NFC');
  const vi = boDau(goc).indexOf(khoa);
  if (vi < 0) {
    return <>{van}</>;
  }
  return (
    <>
      {goc.slice(0, vi)}
      <mark>{goc.slice(vi, vi + khoa.length)}</mark>
      {goc.slice(vi + khoa.length)}
    </>
  );
}
