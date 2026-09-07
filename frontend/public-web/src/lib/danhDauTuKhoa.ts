/**
 * Cắt một chuỗi thành các đoạn **khớp / không khớp** từ khoá — CN-01.8 "highlight từ khoá".
 *
 * <h2>⛔⛔ Vì sao ⛔ KHÔNG dùng `String.normalize('NFD').replace(...)` trên cả chuỗi</h2>
 *
 * Đây là chỗ duy nhất của cả tính năng có thể sai **âm thầm**. Muốn tìm không dấu mà tô đúng
 * chữ CÓ dấu, ta phải khớp trên bản bỏ dấu rồi **cắt trên bản gốc** — tức hai bản phải có
 * **cùng số ký tự**, một-đối-một.
 *
 * `'ế'.normalize('NFD')` cho **hai** code point (`e` + dấu), nên `.replace(/[̀-ͯ]/g,'')`
 * trên cả chuỗi cho ra một chuỗi **ngắn hơn** chuỗi gốc. Vị trí khớp tìm được trên nó rồi đem
 * cắt chuỗi gốc là **lệch dần** — và lệch bao nhiêu thì phụ thuộc số chữ có dấu đứng trước, nên
 * lỗi hiện ra như "tô trúng nửa từ, càng về cuối câu càng lệch". ⛔ Không có gì đỏ.
 *
 * ⇒ Chuẩn hoá **từng code point một** và giữ đúng một ký tự cho mỗi ký tự vào. `{@link boDauGiuViTri}`
 * bảo đảm điều đó, và `danhDauTuKhoa.test.ts` khẳng định bằng một phép **so độ dài** — vế ấy ⛔
 * không chia sẻ giả định nào với phép cắt (luật 29).
 *
 * <h2>⚠ Làm việc trên MẢNG code point, ⛔ không trên chỉ số UTF-16</h2>
 *
 * `'😀'.length === 2` trong JavaScript. Một chỉ số tính trên chuỗi bỏ dấu rồi đem `slice()` chuỗi
 * gốc sẽ cắt đôi một cặp thay thế và sinh ký tự hỏng. Cả hai bản đều là mảng code point, nên
 * chỉ số luôn cùng hệ quy chiếu.
 *
 * <h2>⛔ Đây là hàm THUẦN, ⛔ không dựng JSX</h2>
 *
 * Cùng lý lẽ với `lib/slider.ts`: một hàm thuần kiểm được bằng vài dòng, còn một component thì
 * phải dựng DOM mới khẳng định được. Nơi vẽ `<mark>` là `components/DanhDauTuKhoa.tsx`.
 */

/** `đ` ⛔ không phân rã được bằng NFD — phải khai tay. */
const RIENG: Record<string, string> = { đ: 'd', Đ: 'd' };

/**
 * Bỏ dấu và hạ chữ thường, **giữ nguyên số ký tự**.
 *
 * ⚠ Hậu điều kiện chịu lực: `Array.from(boDauGiuViTri(s)).length === Array.from(s).length` với
 * mọi `s`. Mất hậu điều kiện ấy là mọi phép tô sai vị trí — xem javadoc đầu tệp.
 */
export function boDauGiuViTri(s: string): string[] {
  return Array.from(s).map((ch) => {
    const rieng = RIENG[ch];
    if (rieng) return rieng;
    const phanRa = ch.normalize('NFD').replace(/[̀-ͯ]/g, '');
    // Ký tự chỉ gồm dấu (⛔ không xảy ra với chữ dựng sẵn, nhưng dữ liệu người dùng dán vào thì
    // có) ⇒ giữ nguyên ký tự gốc, ⛔ KHÔNG trả chuỗi rỗng: rỗng là mất một ô trong mảng.
    return (phanRa === '' ? ch : phanRa[0]).toLowerCase();
  });
}

export interface Doan {
  chu: string;
  khop: boolean;
}

/**
 * Cắt `chu` thành các đoạn theo `tuKhoa` — không dấu, ⛔ không phân biệt hoa thường.
 *
 * <p>⚠ Trả về **một đoạn duy nhất, `khop: false`** khi từ khoá rỗng hoặc ⛔ không khớp gì. Nơi gọi
 * vì thế ⛔ không cần một nhánh riêng, và một lượt tìm ⛔ không có kết quả vẫn hiện nguyên văn.
 *
 * <p>⛔ ⛔ Khớp **cả cụm** từ khoá chứ ⛔ không tách từng từ. Tách từ thì gõ "cống Liên Mạc" sẽ tô
 * mọi chữ "cống" trong mọi tiêu đề — nhiễu tới mức người đọc thôi nhìn màu, và lúc ấy phần tô
 * ⛔ không còn nói gì. Đây cũng đúng cách backend khớp (`LIKE '%…%'` trên cả cụm), nên phần tô
 * ⛔ không hứa nhiều hơn thứ đã dùng để lọc.
 */
export function chiaTheoTuKhoa(chu: string, tuKhoa: string | undefined | null): Doan[] {
  const nguyen = Array.from(chu ?? '');
  const khoa = (tuKhoa ?? '').trim();
  if (nguyen.length === 0 || khoa.length === 0) {
    return [{ chu: chu ?? '', khop: false }];
  }

  const chuThuong = boDauGiuViTri(chu).join('');
  const khoaThuong = boDauGiuViTri(khoa).join('');
  const doDaiKhoa = Array.from(khoaThuong).length;

  // ⚠ So trên MẢNG code point ở cả hai phía — xem javadoc đầu tệp.
  const a = Array.from(chuThuong);
  const b = Array.from(khoaThuong);

  const doan: Doan[] = [];
  let i = 0;
  let batDauDoanThuong = 0;

  while (i <= a.length - doDaiKhoa) {
    let trung = true;
    for (let j = 0; j < doDaiKhoa; j++) {
      if (a[i + j] !== b[j]) {
        trung = false;
        break;
      }
    }
    if (!trung) {
      i++;
      continue;
    }
    if (i > batDauDoanThuong) {
      doan.push({ chu: nguyen.slice(batDauDoanThuong, i).join(''), khop: false });
    }
    doan.push({ chu: nguyen.slice(i, i + doDaiKhoa).join(''), khop: true });
    i += doDaiKhoa;
    batDauDoanThuong = i;
  }

  if (batDauDoanThuong < nguyen.length) {
    doan.push({ chu: nguyen.slice(batDauDoanThuong).join(''), khop: false });
  }
  return doan.length === 0 ? [{ chu, khop: false }] : doan;
}
