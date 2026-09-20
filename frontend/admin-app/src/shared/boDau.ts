/**
 * Bỏ dấu + hạ chữ thường — **bản JS của `sn_khong_dau(text)`** phía CSDL.
 *
 * ## ⛔⛔ Cùng một luật chuẩn hoá sống ở HAI nơi ⛔ không dùng chung mã được
 *
 * `sn_khong_dau` (PL/pgSQL, `V202608191014`) quyết định **ai được tìm thấy**; hàm này quyết định
 * **phần nào được tô sáng** (`ToSang`). Lệch nhau thì backend trả **đúng người** mà màn hình **⛔
 * không tô gì** — và người dùng đọc cái đó thành *"hệ thống tìm sai"*. Đây là luật 14 ở dạng ⛔
 * không gỡ được bằng mã: bù bằng `toSang.test.tsx`.
 *
 * ⛔ `đ`/`Đ` phải xử lý **riêng**: chúng ⛔ không phải `d` + dấu tổ hợp mà là một ký tự Unicode độc
 * lập, nên `NFD` ⛔ không tách chúng ra. Thiếu hai dòng ấy thì gõ `dieu` ⛔ không tô được `Điều`,
 * trong khi `unaccent` của Postgres **có** xử lý — tức hai vế lệch nhau đúng ở chỗ tiếng Việt dùng
 * hằng ngày.
 *
 * ⚠ Hàm này ở một tệp `.ts` RIÊNG, ⛔ không nằm cạnh component: quy tắc `react-refresh/only-export-components`
 * (bật ở mức **lỗi** trong kho này) cấm một module vừa export component vừa export hàm thường.
 */
export function boDau(s: string): string {
  return s
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase();
}

/**
 * Tô sáng **MỌI** lần khớp trong một câu — ⛔ chỉ lần đầu như {@link ToSang} của danh bạ.
 *
 * <h3>⚠ Vì sao ⛔ dùng lại `ToSang`</h3>
 *
 * Danh bạ tô tên người: từ khoá xuất hiện một lần là đủ. Ô tìm kiếm của trang Hướng dẫn quét
 * **cả đoạn văn** — một từ như *"ngưỡng"* xuất hiện bốn lần trong cùng một đoạn, và tô đúng lần
 * đầu rồi bỏ ba lần sau đọc như *"hệ thống tìm sót"*.
 *
 * ⚠ Chuẩn hoá NFC TRƯỚC khi cắt, cùng lý do đã ghi ở `ToSang`: `boDau` giữ đúng độ dài so với bản
 * NFC, nên chuỗi vào ở dạng NFD (gõ trên macOS) sẽ làm chỉ số lệch và cắt giữa một ký tự.
 */
export function viTriKhop(van: string, tuKhoa: string): { tu: number; den: number }[] {
  const goc = boDau(van.normalize('NFC'));
  const ra: { tu: number; den: number }[] = [];
  for (const tu of tachTu(tuKhoa)) {
    let i = goc.indexOf(tu);
    while (i >= 0) {
      ra.push({ tu: i, den: i + tu.length });
      // ⚠ Nhảy qua HẾT đoạn vừa khớp, ⛔ phải `i + 1`: với từ khoá lặp ký tự (`aa` trong `aaa`)
      //   thì bước 1 sinh ra hai đoạn CHỒNG nhau, và lượt cắt sau đó ra chuỗi âm.
      i = goc.indexOf(tu, i + tu.length);
    }
  }
  // ⚠⚠ Sắp xếp + gộp đoạn chồng nhau. Nhiều từ khoá có thể khớp **đè lên nhau** (`nuoc` và
  //   `muc nuoc`), và dựng thẻ `<mark>` từ một danh sách đoạn chồng nhau sẽ cắt chuỗi âm rồi
  //   đổ ra chữ lộn xộn — hỏng theo kiểu ⛔ ném, ⛔ báo.
  ra.sort((a, b) => a.tu - b.tu);
  const gop: { tu: number; den: number }[] = [];
  for (const d of ra) {
    const cuoi = gop[gop.length - 1];
    if (cuoi && d.tu <= cuoi.den) {
      cuoi.den = Math.max(cuoi.den, d.den);
    } else {
      gop.push({ ...d });
    }
  }
  return gop;
}

/**
 * Cắt ô tìm kiếm thành các **từ** đã bỏ dấu.
 *
 * ⚠⚠ Đây ⛔ phải chuyện làm màu. Ô tìm kiếm của trang Hướng dẫn so **chuỗi con** ở bản đầu, nên
 * gõ `nguong canh bao` ⛔ ra §6.6 *"Ngưỡng **và** cảnh báo"* — một chữ *"và"* chen vào giữa là
 * trượt. Người dùng gõ vài từ nhớ được chứ ⛔ gõ đúng nguyên văn tiêu đề.
 */
export function tachTu(tuKhoa: string): string[] {
  return boDau(tuKhoa.normalize('NFC'))
    .toLowerCase()
    .split(/\s+/)
    .filter((t) => t.length > 0);
}

/** Văn bản có chứa **mọi** từ trong ô tìm kiếm ⛔? Ô rỗng ⇒ luôn đúng. */
export function khopMoiTu(van: string, tuKhoa: string): boolean {
  const chiMuc = boDau(van.normalize('NFC')).toLowerCase();
  return tachTu(tuKhoa).every((t) => chiMuc.includes(t));
}
