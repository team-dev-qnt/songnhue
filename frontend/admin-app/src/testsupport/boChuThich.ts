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
 */
export function boChuThich(ma: string): string {
  return ma
    .replace(/\{\s*\/\*[\s\S]*?\*\/\s*\}/g, ' ') // {/* … */} của JSX
    .replace(/\/\*[\s\S]*?\*\//g, ' ') // /* … */
    .replace(/^\s*\/\/.*$/gm, ' '); // // … trọn dòng
}
