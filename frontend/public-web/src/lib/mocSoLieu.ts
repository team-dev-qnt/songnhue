/**
 * Kiểu **có nhãn** cho mốc thời gian của SỐ LIỆU — T43.9.
 *
 * ## Vì sao cần một kiểu riêng cho một chuỗi ISO
 *
 * `RealtimeFrame` khai hợp đồng của nó ngay ở prop từ WS-24:
 *
 * > `updatedAt`: *Mốc thời gian của **số liệu**, không phải của lượt dựng trang.*
 *
 * Câu ấy đúng, viết rõ, và **2 trong 4 nơi gọi vẫn vi phạm nó** suốt từ WS-24 tới 09/09/2026:
 * khối *Vận hành công trình* trên trang chủ và trang *Vận hành công trình* đều truyền
 * `getServerTime()` — đồng hồ máy chủ lúc dựng trang. Hệ quả đo được: trực ban ba ngày không
 * ghi bản ghi nào, dòng "Cập nhật lúc" vẫn **nhảy sang giờ mới mỗi lượt F5**.
 *
 * ⛔⛔ Thứ làm vi phạm ấy sống lâu là một **chú thích bênh vực nó**: ngay trong `RealtimeFrame`
 * có đoạn *"Mốc hiển thị đến từ MÁY CHỦ, không phải `new Date()` phía máy khách"*. Câu đó trả
 * lời câu hỏi **đồng hồ máy khách vs máy chủ** — một câu hỏi khác hẳn **mốc số liệu vs mốc dựng
 * trang**. Hai lập luận bị trộn, và chính chỗ trộn khiến vi phạm đọc như cố ý.
 *
 * ⇒ Một lời dặn ⛔ không phải một cổng kiểm (§11.19). Nhãn dưới đây biến vi phạm thành **lỗi
 * biên dịch**: `getServerTime()` trả `string | null` trần, và `string` ⛔ không gán được vào
 * `MocSoLieu`. `npm run typecheck` chạy trong job **bắt buộc** `Frontend — lint` (`ci.yml:377`),
 * nên bộ canh này ⛔ không im được bằng cách sửa một câu chú thích (luật 2).
 *
 * ## Phạm vi — luật 28
 *
 * Nhãn chỉ canh **đường vào `RealtimeFrame`** của `public-web`. Nó ⛔ **không** canh:
 * - `admin-app` (`WallFrame.capNhatLuc`) — kho khác, hợp đồng khác;
 * - mọi chỗ hiển thị thời gian ⛔ không đi qua `RealtimeFrame`;
 * - việc chuỗi truyền vào `mocSoLieu()` có **thật sự** là mốc số liệu hay không — hàm này là chỗ
 *   người viết **ký tên** vào lời khẳng định ấy, ⛔ không phải chỗ kiểm chứng nó. Vì vậy mỗi lời
 *   gọi phải đi kèm một nguồn đọc được: `meta.capNhatLuc`, `meta.lanLayCuoi` — ⛔ không phải một
 *   biểu thức tính ra `now`.
 */

declare const NHAN_MOC_SO_LIEU: unique symbol;

/**
 * Chuỗi ISO-8601 là mốc **của số liệu**: thời điểm bản ghi mới nhất được ghi xuống, hoặc lượt
 * đồng bộ cuối cùng lấy được số từ nguồn.
 *
 * ⛔ **KHÔNG** phải: giờ máy chủ lúc trả lời, giờ máy khách, mốc dựng trang, mốc hiệu lực nghiệp
 * vụ (`effectiveAt` — ghi lùi và ghi trước đều hợp lệ nên nó có thể nằm ở **tương lai**).
 */
export type MocSoLieu = string & { readonly [NHAN_MOC_SO_LIEU]: 'moc-so-lieu' };

/**
 * Gắn nhãn cho một mốc lấy **từ backend cùng lượt gọi với chính số liệu ấy**.
 *
 * ⚠ Đây là điểm **ký tên**, không phải điểm kiểm chứng — xem mục *Phạm vi* ở đầu tệp. Chỉ truyền
 * vào đây giá trị đọc thẳng từ một trường `meta` của phản hồi; ⛔ đừng truyền kết quả của một
 * phép tính phía cổng.
 *
 * @param iso mốc ISO-8601 từ backend; `null`/`undefined` ⇒ `null` (chưa có số liệu nào —
 *     `RealtimeFrame` hiện "chưa rõ", ⛔ không bịa một mốc, quy tắc 16)
 */
export function mocSoLieu(iso: string | null | undefined): MocSoLieu | null {
  return (iso ?? null) as MocSoLieu | null;
}
