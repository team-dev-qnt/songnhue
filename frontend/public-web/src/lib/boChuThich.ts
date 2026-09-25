/**
 * Bỏ chú thích khỏi mã nguồn trước khi soi nó bằng phép tìm chuỗi hay biểu thức chính quy.
 *
 * <h2>Vì sao mọi bộ canh đọc-mã-nguồn đều phải đi qua đây</h2>
 *
 * Chú thích *giải thích* một vi phạm phải được phép **nhắc tới** nó: ghi chú lịch sử của
 * `SiteFooter` cần gọi tên `#061b37`, ghi chú §10.54 cần gọi tên bộ dữ liệu bịa đã gỡ. Cấm cả
 * trong chú thích là buộc người sau mô tả lịch sử mà ⛔ được gọi tên nó — và hệ quả thực tế là
 * người ta **xoá ghi chú** chứ ⛔ xoá vi phạm.
 *
 * Ở chiều ngược lại, một bộ canh **chỉ thấy** chú thích cũng sai y hệt: `loiTheoTruong.test.tsx`
 * từng bắt trúng lời giải thích thay vì lời thi hành, và `taiLogoMoDuocHopThoai.test.tsx` cắt
 * khối ôm luôn phần nằm *trước* thẻ thật. Cùng một gốc — *canh văn bản thì phải biết văn bản nào
 * đang chạy*.
 *
 * <h2>⛔⛔ T28.41 — trước lượt này có TÁM bản, và SÁU thuật toán khác nhau</h2>
 *
 * ⚠⚠ Đây là lượt đo **thứ NĂM** của cùng một mẫu số, và **bốn lượt trước đều sai** — dòng nợ đi
 * từ *"⛔ một bản"* (bản gốc) → *"3 bản cắt ký tự + 2 lexer"* (19/09) → *"hai bản dùng chung ⛔
 * cùng thuật toán"* (20/09) → và đo 25/09 ra **8 định nghĩa / 6 thuật toán**. Ba bản ⛔ lượt nào
 * kể tên: `hieuUngVaoTrang.test.ts` (⛔ xử lý `//` một chút nào), `noBuildTimePrerender.test.ts`
 * (nối lại bằng chuỗi rỗng ⇒ **số dòng xê dịch**), `bieuDoMucNuoc.test.ts`. Cùng hình dạng T63.10:
 * một mẫu số đo bằng `grep` thì mỗi lượt hụt một kiểu khác nhau.
 *
 * Sáu thuật toán ấy khác nhau ở ba trục đo được: `//` tính **ở đầu dòng** hay **ở bất kỳ đâu ngoài
 * chuỗi**; bỏ **trọn dòng** hay chỉ **phần đuôi**; có dọn `{ }` hay ⛔.
 *
 * ⇒ Chuyển một bộ canh từ app này sang app kia — hay chỉ chép một khẳng định giữa hai tệp trong
 * cùng app — **đổi nghĩa của nó trong im lặng**, và ⛔ một dòng đỏ nào. Đó ⛔ phải rủi ro lý
 * thuyết: 31 tệp kiểm đang gọi hàm này, và hai câu trả lời khác nhau cho cùng một tệp nguồn nghĩa
 * là hai bộ canh cùng tên canh hai thứ khác nhau.
 *
 * ⭐ Bản đúng **đã nằm sẵn trong kho** từ T49.6 (lexer trong `apiKhongMoCoi.test.ts`) — lượt này
 * chỉ nâng nó lên bản chính và xoá sáu bản kia. Hai tệp `boChuThich.ts` của hai workspace nay
 * **giống nhau tới từng byte**, và `boChuThich.test.ts` canh cả hai điều đó: byte-đối-byte giữa
 * hai workspace, **và** toàn kho chỉ được có đúng hai định nghĩa (hai workspace ⛔ nhập khẩu chéo
 * được nên ⛔ rút về một bản được).
 *
 * <h2>⚠ Giới hạn — nói ra thay vì để người đọc tự suy (luật 28)</h2>
 *
 * <ul>
 *   <li>⛔ hiểu **biểu thức chính quy dạng literal**: `/a//b/` bị đọc thành một chú thích `//`.
 *       Giới hạn này có sẵn từ bản lexer gốc và ⛔ có nạn nhân nào trong 31 tệp đang gọi — nhưng
 *       nó là giới hạn thật, ⛔ phải một chi tiết bỏ qua được.
 *   <li>Chuỗi ký tự được giữ **nguyên văn**, kể cả ký tự thoát. Bản lexer gốc thay `\x` bằng hai
 *       dấu cách; lượt này giữ lại cả hai ký tự — vẫn ⛔ để `\'` kết thúc chuỗi nhầm, mà ⛔ băm
 *       mất nội dung chuỗi, thứ nhiều bộ canh đang soi.
 *   <li>Chú thích khối được thay bằng **dấu cách, giữ nguyên số dòng** — để chẩn đoán nào in số
 *       dòng vẫn trỏ đúng chỗ.
 * </ul>
 */
export function boChuThich(nguon: string): string {
  const ket: string[] = [];
  let trangThai: 'ma' | 'khoi' | 'dong' | '"' | "'" | '`' = 'ma';
  for (let i = 0; i < nguon.length; i += 1) {
    const c = nguon[i];
    const ke = nguon[i + 1] ?? '';
    if (trangThai === 'ma') {
      if (c === '/' && ke === '*') {
        trangThai = 'khoi';
        i += 1;
      } else if (c === '/' && ke === '/') {
        trangThai = 'dong';
        i += 1;
      } else if (c === '"' || c === "'" || c === '`') {
        trangThai = c;
        ket.push(c);
      } else {
        ket.push(c);
      }
    } else if (trangThai === 'khoi') {
      if (c === '*' && ke === '/') {
        trangThai = 'ma';
        i += 1;
      } else {
        // Giữ nguyên dòng, thay phần còn lại bằng dấu cách: số dòng ⛔ xê dịch.
        ket.push(c === '\n' ? '\n' : ' ');
      }
    } else if (trangThai === 'dong') {
      if (c === '\n') {
        trangThai = 'ma';
        ket.push('\n');
      }
    } else if (c === '\\') {
      // ⛔ Thay bằng dấu cách: nội dung chuỗi là thứ nhiều bộ canh đang soi. Nhảy qua ký tự sau
      //   nó để một `\'` ⛔ kết thúc chuỗi nhầm.
      ket.push(c, ke);
      i += 1;
    } else {
      if (c === trangThai) {
        trangThai = 'ma';
      }
      ket.push(c);
    }
  }
  // Cặp ngoặc rỗng còn lại của `{/* … */}` trong JSX. ⛔ Gộp vào vòng lặp trên: nó là một phép
  // dọn của JSX, ⛔ phải một phần của việc bóc chú thích khỏi JavaScript.
  return ket.join('').replace(/\{\s*\}/g, ' ');
}
