import { docTaiLieu } from './markdown';
import { catMuc, mucChoDuongDan } from './mucTaiLieu';
import nguon from './huong-dan-su-dung.md?raw';

/**
 * Tra **đường dẫn màn hình → neo mục hướng dẫn** — nguồn của nút `?` trên thanh tiêu đề.
 *
 * <h2>⭐⭐ Đảo chiều câu hỏi, và đó là toàn bộ giá trị của nó</h2>
 *
 * Trang Hướng dẫn trả lời *"tôi muốn tra, nó nằm đâu"*. Nút `?` trả lời câu ngược lại —
 * *"tôi **đang** ở đây và ⛔ biết làm gì tiếp"* — vốn là câu người dùng thật sự hỏi, đúng lúc họ
 * đang bí. Bắt họ mở tài liệu rồi tự dò 11 phần là bỏ rơi đúng khoảnh khắc ấy.
 *
 * <h2>⚠ Tách khỏi `AdminLayout` vì KHUNG được nạp ở MỌI lượt tải trang</h2>
 *
 * `AdminLayout` nằm trong bó mã chính; tệp markdown thì **60 KB**. Nhập thẳng nó vào khung là
 * cộng chừng ấy vào lượt tải **đầu tiên** của mọi người, để phục vụ một cái nút. Tệp này ⛔ được
 * import tĩnh từ khung — `AdminLayout` gọi nó qua `import()` động, nên bó mã hướng dẫn chỉ về máy
 * khi có người thật sự bấm.
 */

let bang: Map<string, string> | null = null;

/**
 * Bảng tra, dựng **một lần** rồi giữ lại.
 *
 * ⚠ Dựng lười (⛔ phải hằng số ở tầng module): phân tích 700 dòng markdown là việc ⛔ đáng làm cho
 * tới khi có người bấm nút.
 */
function dungBang(): Map<string, string> {
  if (bang) {
    return bang;
  }
  const { nhom } = catMuc(docTaiLieu(nguon));
  const ra = new Map<string, string>();
  for (const n of nhom) {
    for (const m of [n.muc, ...n.con]) {
      for (const d of m.duongDan) {
        // ⚠ Mục CON thắng mục cha: cả hai cùng khai một đường dẫn (phần cha thừa hưởng của con),
        //   mà thứ người dùng cần là mục cụ thể chứ ⛔ phải cả phần lớn.
        if (!ra.has(d) || m.level === 3) {
          ra.set(d, m.id);
        }
      }
    }
  }
  bang = ra;
  return ra;
}

/**
 * Neo mục hướng dẫn cho một đường dẫn đang mở, hoặc `undefined` nếu ⛔ có mục nào.
 *
 * ⛔ Trả về một neo bịa khi ⛔ tra ra: nút sẽ dẫn tới giữa trang hướng dẫn một cách ngẫu nhiên,
 * mà người dùng thì đang tin rằng mình vừa được đưa tới đúng chỗ. Thà ẩn nút (T23.8).
 */
export function neoChoDuongDan(duongDan: string): string | undefined {
  const { nhom } = catMuc(docTaiLieu(nguon));
  const m = mucChoDuongDan(nhom, duongDan);
  if (!m) {
    return undefined;
  }
  // Ưu tiên bảng tra (mục con thắng mục cha), rơi về kết quả khớp tiền tố dài nhất.
  return dungBang().get(m.duongDan.find((d) => duongDan.startsWith(d)) ?? '') ?? m.id;
}
