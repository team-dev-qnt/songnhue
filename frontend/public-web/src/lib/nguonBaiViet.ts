/**
 * Đọc ô "Nguồn tin" của một bài viết thành thứ hiển thị được — nợ **T26.63**.
 *
 * <h2>Vì sao có tệp này</h2>
 *
 * Tới 31/08/2026, chân **mọi** bài trên cổng in một chuỗi ghi cứng:
 * `Nguồn: Cổng TTĐT Thủy lợi Sông Nhuệ`. Cột `articles.source` có dữ liệu thật — năm bài trong bộ
 * seed mang URL `hanoimoi.vn` / `vneconomy.vn` — nhưng nó **không nằm trong DTO công khai nào**.
 * Nghĩa là cổng đang nói một câu **sai sự thật** về năm bài dẫn lại từ báo ngoài, và nói bằng giọng
 * chắc chắn. Đây đúng hình dạng "nửa cặp đọc–ghi" của luật 27: ô nhập có, đường ghi có, đường đọc
 * không có, và triệu chứng thì im lặng.
 *
 * <h2>Ba quyết định, và cả ba đều nhỏ hơn cái bẫy chúng tránh</h2>
 *
 * <ol>
 *   <li><b>Rỗng thì trả `null`, nơi gọi bỏ hẳn dòng.</b> ⛔ Không có nhãn mặc định. Nhãn mặc định
 *       chính là thứ vừa tạo ra câu sai sự thật ở trên — và "chưa ai điền nguồn" khác hẳn "nguồn là
 *       Công ty" (cùng họ với §10.34: `SUM` trả `null` không được biến thành số 0).
 *   <li><b>Chỉ `http:` và `https:` mới thành liên kết — danh sách CHO PHÉP, không phải danh sách
 *       cấm.</b> Ô này là chữ tự do dài 255 ký tự do người dùng quản trị nhập; `javascript:alert(1)`
 *       lọt vào `href` là một lỗ XSS. §10.52 đã trả giá cho bài học "đổi danh sách cấm thành danh
 *       sách cho phép" ở tầng converter — cùng một lý lẽ, rẻ hơn nhiều khi làm từ đầu.
 *   <li><b>Nhãn hiển thị là TÊN MIỀN, không phải URL đầy đủ.</b> Giá trị thật dài tới 96 ký tự
 *       (`hanoimoi.vn/xa-phu-xuyen-tang-cuong-ung-truc-…-1238587.html`); in nguyên vào chân bài thì
 *       nó tràn dòng trên điện thoại. Địa chỉ đầy đủ vẫn nằm ở `href`.
 * </ol>
 *
 * ⚠ Hàm này <b>không</b> sửa dữ liệu và <b>không</b> đoán: giá trị không phải URL được trả về
 * nguyên văn để hiện thành chữ ("Báo Hà Nội Mới" là một giá trị hợp lệ của ô này).
 */
export interface NguonBaiViet {
  /** Nhãn hiển thị: tên miền nếu là URL, nguyên văn nếu không. */
  nhan: string;
  /** Địa chỉ mở ra, hoặc `null` khi giá trị không phải một URL http(s) — lúc ấy chỉ hiện chữ. */
  href: string | null;
}

/** Giao thức được phép thành liên kết. Danh sách CHO PHÉP — thêm vào đây là một quyết định. */
const GIAO_THUC_CHO_PHEP = ['http:', 'https:'];

export function docNguonBaiViet(source: string | null | undefined): NguonBaiViet | null {
  const chu = (source ?? '').trim();
  if (chu === '') {
    return null;
  }

  let url: URL;
  try {
    url = new URL(chu);
  } catch {
    // Không phải URL tuyệt đối — tên báo, tên cơ quan… Hiện nguyên văn.
    return { nhan: chu, href: null };
  }

  if (!GIAO_THUC_CHO_PHEP.includes(url.protocol)) {
    // ⛔ `javascript:`, `data:`, `file:`… Hiện thành CHỮ, tuyệt đối không thành `href`.
    return { nhan: chu, href: null };
  }

  const tenMien = url.hostname.replace(/^www\./, '');
  return { nhan: tenMien === '' ? chu : tenMien, href: chu };
}
