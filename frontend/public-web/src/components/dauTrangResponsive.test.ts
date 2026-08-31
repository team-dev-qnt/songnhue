import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * **Dải nhận diện đầu trang phải chịu được bề rộng điện thoại.**
 *
 * <h2>Sự cố ngày 31/08/2026 — số đo, không phải cảm nhận</h2>
 *
 * Đo trên staging bằng Chrome, khung nhìn <b>390×844</b> (iPhone 14), bản trước đợt sửa:
 *
 * <table>
 *   <tr><th>Đo</th><th>Trước</th><th>Sau</th></tr>
 *   <tr><td>bề rộng khối chữ (logo + tên)</td><td><b>0px</b></td><td>298px</td></tr>
 *   <tr><td>dòng cơ quan chủ quản</td><td><b>8 dòng</b></td><td>1 dòng</td></tr>
 *   <tr><td>chữ vàng tràn khỏi hộp của nó</td><td><b>37px</b></td><td>0px</td></tr>
 *   <tr><td>giao nhau giữa khối chữ và ô tìm kiếm</td><td><b>23×40px</b></td><td>0</td></tr>
 *   <tr><td>chiều cao dải nhận diện</td><td>160px</td><td>129px</td></tr>
 *   <tr><td>{@code body.scrollWidth} vs khung nhìn</td><td colspan="2"><b>bằng nhau ở CẢ HAI</b></td></tr>
 * </table>
 *
 * <p>Dòng cuối là lý do bài kiểm này tồn tại: <b>không có tràn ngang nào để bắt</b>. Ô tìm kiếm
 * mang {@code max-w-[288px] shrink-0} nên nó không chịu co; khối chữ mang {@code min-w-0} nên nó
 * là thứ duy nhất co được — và nó co về <b>0px</b>, rồi chữ tràn ra ngoài một cái hộp rộng 0.
 * {@code overflow: visible} làm phần tràn ấy hiện lên trông như chữ bình thường, và
 * {@code document.body.scrollWidth} vẫn đúng bằng bề rộng khung nhìn.
 *
 * <p>Cùng hình dạng §10.62: <i>một cơ chế chịu lỗi làm đúng việc của nó thì lỗi không bao giờ nổi
 * lên</i>. Ở đó là {@code flex-wrap} che một thanh điều hướng tràn 22% ở mọi bề rộng.
 *
 * <h2>Bài này canh ba bất biến, không canh một bố cục cụ thể</h2>
 *
 * <ol>
 *   <li><b>Dải nhận diện xếp cột trước, thành hàng sau</b> — ở bề rộng điện thoại, logo + hai
 *       dòng tên + ô tìm kiếm không đủ chỗ trên một hàng. Đây là phép trừ, không phải thẩm mỹ:
 *       390 − 32 ({@code px-4}) − 16 ({@code gap}) − 288 (ô tìm kiếm) = <b>54px</b> cho cả logo
 *       lẫn tên, mà riêng logo đã 44px.
 *   <li><b>Ô tìm kiếm không được có bề rộng cố định ở bề rộng điện thoại</b> — bề rộng cố định
 *       chỉ được phép sau một tiền tố điểm dừng ({@code sm:} / {@code md:} / {@code lg:}).
 *   <li><b>Mọi dòng chữ trong dải nhận diện phải chặn số dòng</b> ({@code line-clamp-*} hoặc
 *       {@code truncate}) — dòng cơ quan chủ quản là dòng duy nhất chưa chặn, và nó chính là
 *       dòng nổ thành 8 dòng.
 * </ol>
 *
 * <h2>⚠ Giới hạn — nói ra thay vì để người đọc tự suy (luật 28)</h2>
 *
 * Bài này đọc <b>văn bản lớp CSS trong {@code SiteHeader.tsx}</b>. Nó <b>không</b> dựng bố cục và
 * <b>không</b> đo pixel — jsdom không có bộ dựng bố cục, và kho chưa có trình duyệt trong CI. Vì
 * thế nó bắt được đúng <b>ba hình dạng đã gây ra sự cố này</b>, không bắt được một cách vỡ bố cục
 * kiểu khác. Số đo thật ở bảng trên lấy bằng Chrome trên staging và phải đo lại bằng tay khi đổi
 * bố cục đầu trang.
 *
 * <p>⛔ Không đọc bài xanh này thành <i>"đầu trang đã responsive"</i>. Đọc nó đúng nghĩa:
 * <i>"ba nguyên nhân của sự cố 31/08 chưa quay lại"</i>.
 */
const NGUON = join(process.cwd(), 'src', 'components', 'SiteHeader.tsx');

/** Lấy mọi chuỗi `className="…"` trong tệp, theo thứ tự xuất hiện. */
function docClassName(ma: string): string[] {
  return [...ma.matchAll(/className="([^"]+)"/g)].map((m) => m[1].replace(/\s+/g, ' ').trim());
}

/** Điểm dừng đứng trước một lớp — `sm:w-60` → `sm`, `w-full` → `` (không có). */
const CO_TIEN_TO = /^(sm|md|lg|xl|2xl):/;

/** Bề rộng cố định: `w-60`, `w-[288px]`, `max-w-[288px]`, `min-w-[…]`. Không tính `w-full`/`w-auto`. */
function laBeRongCoDinh(lop: string): boolean {
  // ⚠ `min-w-0` khớp mẫu dưới đây vì `\d+` bắt cả số 0 — nhưng nó là lớp *cho phép* co, đúng
  //   ngược với thứ bài này đi tìm. Loại trừ tường minh; xem cùng cái bẫy ở
  //   `nav/thanhDieuHuongMotNguon.test.ts`.
  if (lop === 'min-w-0') return false;
  return /^(max-|min-)?w-(\[[^\]]+\]|\d+(\.\d+)?|px)$/.test(lop);
}

// ── Ba vị từ — mỗi cái nhận CHUỖI LỚP và trả lời đúng/sai, để bản hỏng dùng lại được ──

/**
 * ⭐ 01/09 — vị từ 1 THAY cho `hangNhanDienXepCotTruoc`, và lý do phải thay chứ không phải xoá.
 *
 * Bất biến cũ: *"hàng nhận diện phải xếp cột trước, thành hàng sau"*. Nó là **bản vá**, không
 * phải bất biến — nó tồn tại vì hàng ấy phải chứa ba khối (logo, tên, ô tìm kiếm) trong 375px.
 * Ô tìm kiếm rời đi ⇒ hàng chỉ còn một khối ⇒ xếp cột không còn nghĩa gì, và giữ lại nó là canh
 * một bố cục cụ thể thay vì canh nguyên nhân.
 *
 * <h2>Nguyên nhân thật, phát biểu chính xác</h2>
 *
 * Thứ làm vỡ dải nhận diện ở 375px **không phải** "có bề rộng cố định" — `max-w-*` chặn phần tử
 * *nở ra*, nó không hề chặn phần tử co lại, và `min-w-0` thì đúng nghĩa *cho phép* co. Thứ làm
 * vỡ là một mục flex vừa **từ chối co** (`shrink-0`) vừa **giữ chỗ theo một hạn mức bề rộng**
 * (`max-w-[288px]`): nó ăn 288px trong 375px và bỏ 39px lại cho hai khối còn lại.
 *
 * <p>⚠ Hai lượt viết trước của vị từ này đều bắt nhầm — lượt một bắt `min-w-0`, lượt hai bắt
 * `max-w-[1232px]` của chính khung chứa. Cả hai đều là lớp vô hại, và cả hai đều lọt qua vì
 * người viết đọc "bề rộng cố định" thành "có chữ w- và một con số". Ghi lại vì đây đúng là
 * **luật 25** ở quy mô nhỏ nhất: một vị từ canh hình dạng phải được thử với dữ liệu THẬT của cả
 * hai phía — thứ nó định bắt, và thứ nó không được bắt.
 */
function hangNhanDienKhongCoGiChanCo(cacLop: string[]): boolean {
  return !cacLop.some((chuoi) => {
    const lop = chuoi.split(' ');
    if (!lop.includes('shrink-0')) return false;
    // `w-auto` là bề rộng nội tại (logo) — không phải một hạn mức giành chỗ.
    return lop.filter((l) => !CO_TIEN_TO.test(l)).some(laBeRongCoDinh);
  });
}

/** ⭐ 01/09 — vị từ 2: dải nhận diện canh GIỮA (yêu cầu Công ty, sau khi ô tìm kiếm rời đi). */
function hangNhanDienCanhGiua(lopHang: string): boolean {
  return lopHang.split(' ').includes('justify-center');
}

function moiDongChuDeuChanSoDong(lopChu: string[]): boolean {
  return lopChu.every((l) => /(^|\s)(line-clamp-\d+|truncate)(\s|$)/.test(l));
}

/**
 * Bản đã gây ra sự cố — chép NGUYÊN VĂN từ `git show` của commit trước bản vá.
 *
 * ⚠ Đây là phần kiểm chứng ngược, và §10.62 đã cho thấy nó <b>cũng sai được theo đúng cách thứ
 * nó kiểm đang sai</b>: hai lượt kiểm chứng ngược trong một phiên đều hỏng, một cái vì mẫu không
 * biết SQL có chú thích, một cái vì nó <i>chép lại</i> hành vi sai thay vì bắt nó. Nên ngoài ba
 * khẳng định đúng/sai, bài này còn khẳng định <b>số lượng</b> — thứ không chia sẻ giả định nào
 * với các mẫu regex ở trên.
 */
const BAN_HONG = {
  hang: 'mx-auto flex max-w-[1232px] items-center justify-between gap-4 px-4 py-3 sm:gap-8 sm:px-6 sm:py-4',
  /** Ô tìm kiếm của bản 31/08 — nay không còn ở tệp này, nhưng vị từ 1 vẫn phải bắt được nó. */
  form: 'w-full max-w-[288px] shrink-0',
  chu: [
    'text-[10px] font-semibold leading-tight tracking-wide text-brand-gold sm:text-[13px]',
    'mt-0.5 line-clamp-2 text-[13px] font-black leading-tight tracking-tight text-white drop-shadow-2xs sm:text-base md:text-lg',
  ],
};

describe('Dải nhận diện đầu trang — bất biến sau khi ô tìm kiếm rời đi (01/09)', () => {
  const ma = readFileSync(NGUON, 'utf8');
  const cacLop = docClassName(ma);

  const lopHang = cacLop.find((l) => l.includes('max-w-[1232px]'));
  const lopChu = cacLop.filter(
    (l) => /text-brand-gold|text-white/.test(l) && l.includes('leading-tight'),
  );

  it('đọc được đúng các khối cần soi — chống xanh trên tập rỗng', () => {
    // ⛔ Nếu đổi cấu trúc đầu trang mà quên bài này, các khẳng định dưới sẽ chạy trên `undefined`
    //    và xanh trọn vẹn. Khẳng định về SỐ LƯỢNG là thứ duy nhất ở đây không dùng chung giả định
    //    với các mẫu regex — đúng thứ đã cứu lượt kiểm chứng ngược ở §10.62.
    expect(cacLop.length).toBeGreaterThanOrEqual(5);
    expect(lopHang, 'không tìm thấy hàng nhận diện (max-w-[1232px])').toBeTruthy();
    expect(lopChu.length, 'phải soi đủ hai dòng chữ: cơ quan chủ quản và tên Công ty').toBe(2);
  });

  it('⭐ không khối nào trong dải nhận diện từ chối co lại ở bề rộng điện thoại', () => {
    const viPham = cacLop.filter((chuoi) => !hangNhanDienKhongCoGiChanCo([chuoi]));
    expect(
      viPham,
      `bề rộng cố định không tiền tố điểm dừng ở: ${viPham.join(' | ')}. ` +
        `Phép trừ 31/08: 375 − 32 (px-4) − 16 (gap) − 288 = 39px cho logo + tên, riêng logo 44px.`,
    ).toHaveLength(0);
  });

  it('⭐ dải nhận diện canh GIỮA — logo và tên đứng giữa khung (yêu cầu Công ty 01/09)', () => {
    expect(
      hangNhanDienCanhGiua(lopHang as string),
      `hàng nhận diện phải có "justify-center". Đang là: ${lopHang}`,
    ).toBe(true);
  });

  it('mọi dòng chữ trong dải nhận diện đều chặn số dòng', () => {
    expect(
      moiDongChuDeuChanSoDong(lopChu),
      `thiếu line-clamp/truncate ở: ${lopChu.filter((l) => !/(line-clamp-\d+|truncate)/.test(l)).join(' | ')}`,
    ).toBe(true);
  });

  describe('kiểm chứng ngược — mỗi vị từ phải BẮT ĐƯỢC bản đã gây ra sự cố', () => {
    it('bản hỏng: ô tìm kiếm max-w-[288px] shrink-0 → vị từ 1 phải trả false', () => {
      expect(hangNhanDienKhongCoGiChanCo([BAN_HONG.form])).toBe(false);
    });

    it('bản hỏng: hàng nhận diện justify-between → vị từ 2 phải trả false', () => {
      expect(hangNhanDienCanhGiua(BAN_HONG.hang)).toBe(false);
    });

    it('bản hỏng: dòng cơ quan chủ quản không chặn số dòng → vị từ 3 phải trả false', () => {
      expect(moiDongChuDeuChanSoDong(BAN_HONG.chu)).toBe(false);
    });

    it('ba vị từ chỉ đỏ ĐÚNG chỗ chúng canh, không đỏ với mọi đầu vào', () => {
      // Nếu một vị từ trả false cho MỌI chuỗi thì nó không canh gì cả, chỉ đang luôn đỏ.
      expect(hangNhanDienKhongCoGiChanCo(['h-12 w-auto shrink-0 sm:h-16'])).toBe(true);
      expect(hangNhanDienKhongCoGiChanCo(['mx-auto flex max-w-[1232px] px-4'])).toBe(true);
      expect(hangNhanDienKhongCoGiChanCo(['min-w-0'])).toBe(true);
      expect(hangNhanDienCanhGiua('mx-auto flex items-center justify-center px-4')).toBe(true);
      expect(moiDongChuDeuChanSoDong([BAN_HONG.chu[1]])).toBe(true);
    });
  });
});
