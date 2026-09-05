/**
 * Bỏ chú thích khỏi mã nguồn trước khi soi nội dung **hiển thị hoặc thi hành**.
 *
 * <h2>Vì sao có tệp này</h2>
 *
 * Trong cùng một phiên, **hai** bộ canh đọc-mã-nguồn đã đỏ oan vì cùng một lý do: chú thích giải
 * thích *vì sao* một đoạn mã bị gỡ có chứa nguyên văn đoạn mã ấy, nên phép tìm chuỗi bắt trúng
 * lời giải thích thay vì lời thi hành.
 *
 * <ul>
 *   <li>`loiTheoTruong.test.tsx` — tìm câu *"hệ thống sẽ báo cụ thể"* và bắt trúng chú thích nói
 *       vì sao câu ấy bị gỡ;
 *   <li>`taiLogoMoDuocHopThoai.test.tsx` — `indexOf('&lt;Upload')` bắt trúng chú thích nhắc tới
 *       `&lt;Upload&gt;`, nên khối cắt ra ôm luôn cả phần nằm *trước* thẻ thật.
 * </ul>
 *
 * <p>Đây là §10.62 ở chiều ngược lại: ở đó một bộ canh **mù trước** SQL đã chú thích; ở đây bộ
 * canh **chỉ thấy** chú thích. Cùng một gốc — *canh văn bản thì phải biết văn bản nào đang chạy*.
 *
 * <p>⛔ Đặt ở một tệp dùng chung, không chép sang từng bài kiểm: hai bản của cùng một phép cắt là
 * hai nơi phải nhớ sửa (luật 14), và bản thứ hai đã suýt ra đời thật.
 *
 * <h2>⚠ Giới hạn — nói ra thay vì để người đọc tự suy (luật 28)</h2>
 *
 * Đây là phép cắt theo ký tự, **không** phải bộ phân tích cú pháp. Giới hạn đã ĐO được, kèm bài
 * kiểm cho từng vế ở `boChuThich.test.ts`:
 *
 * <ul>
 *   <li>chuỗi ký tự chứa `/*` <b>bị cắt nhầm</b> — mẫu khối không biết mình đang ở trong chuỗi;
 *   <li>chuỗi ký tự chứa `//` (URL chẳng hạn) thì <b>KHÔNG</b> — mẫu dòng đòi `//` đứng ở đầu
 *       dòng. Lượt viết đầu của tệp này khai ngược vế thứ hai, và bài kiểm đỏ ngay: một giới hạn
 *       khai sai còn tốn thời gian hơn không khai (§10.42).
 * </ul>
 *
 * Đủ dùng để soi câu chữ và cấu trúc JSX của một tệp TSX; đừng dùng nó cho việc gì cần độ chính
 * xác cao hơn thế.
 *
 * <h2>⚠⚠ 01/09 — THỨ TỰ ba phép thay là bắt buộc, và bản trước sai</h2>
 *
 * Bản trước chạy mẫu chú thích JSX TRƯỚC. Mẫu ấy có thể **quay lui qua nhiều chú thích**: gặp
 * `interface X &#123;` theo sau là xuống dòng rồi một javadoc, nó thấy sau dấu đóng không phải
 * `&#125;` nên kéo dài phần `[\s\S]*?` mãi tới dấu đóng của một chú thích JSX cách đó hàng trăm
 * dòng — và **nuốt trọn mọi thứ ở giữa**.
 *
 * <p>Đo được ở `public-web/src/components/home/AnhCarousel.tsx`: bản cũ cắt mất **8.174 ký tự**,
 * gồm cả `export function AnhCarousel`. Ở `admin-app/src/features/cms/MenusTab.tsx` bản cũ tình
 * cờ ra **đúng cùng kết quả** với bản mới (lệch 0 ký tự) — tức hai bộ canh của admin-app đang
 * xanh **nhờ bố cục tệp**, không nhờ phép cắt đúng. Đó đúng là hình dạng "cơ chế canh gác xanh
 * mà không chạy": một bộ cắt quá tay không làm bài nào đỏ, nó chỉ lặng lẽ xoá thứ cần soi.
 *
 * <p>Cắt khối `/* … *&#47;` TRƯỚC thì mỗi chú thích được xử lý riêng lẻ (`*?` dừng ở `*&#47;`
 * gần nhất, không có chỗ quay lui); `{/* … *&#47;}` khi ấy còn lại `{ }`, dọn bằng phép thứ ba.
 */
export function boChuThich(ma: string): string {
  return ma
    .replace(/\/\*[\s\S]*?\*\//g, ' ') // /* … */ và /** … */ — PHẢI chạy trước
    .replace(/^\s*\/\/.*$/gm, ' ') // // … trọn dòng
    .replace(/\{\s*\}/g, ' '); // cặp ngoặc rỗng còn lại của {/* … */}
}
