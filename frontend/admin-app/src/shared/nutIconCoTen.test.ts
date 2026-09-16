import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * **Nút chỉ có icon phải có TÊN đọc được** — `ui-styles.md` §a11y, nợ **T63.9**.
 *
 * <h2>Vì sao</h2>
 *
 * Một `<Button icon={<EditOutlined />} />` ⛔ có chữ, ⛔ có `aria-label`, ⛔ có `Tooltip` là một nút
 * mà trình đọc màn hình đọc thành *"button"* — trống rỗng. Người khiếm thị ⛔ biết nó làm gì, và
 * người dùng bàn phím ⛔ có gì để phân biệt ba nút liền nhau trong một cột thao tác.
 *
 * <p>⚠ Nó còn là một khe hở **⛔ ai thấy khi nhìn màn hình**: giao diện trông hoàn toàn bình
 * thường. Lộ ra 16/09 khi một bài kiểm cần bấm nút Sửa của điểm đo và ⛔ có cách nào gọi tên nó —
 * đúng dấu hiệu *"⛔ chọn được bằng tên"* mà `@testing-library` sinh ra để ép.
 *
 * <h2>⭐ Trần CHỈ-ĐƯỢC-GIẢM, ⛔ đòi 0 ngay</h2>
 *
 * Cùng khuôn `noHardcodedColors.test.ts`. Đòi 0 ngay thì hoặc phải hoãn bộ canh tới khi dọn xong
 * 36 chỗ — và trong lúc hoãn nợ lại lớn lên, đúng vòng đã xảy ra với nợ màu (25 → 62 trong 10
 * ngày) — hoặc phải khai một danh sách miễn trừ, mà danh sách miễn trừ **biến *"tôi đã nghĩ tới"*
 * thành *"tôi được phép quên"***. Một con số thì ⛔ quên được: mỗi nút icon mới ⛔ nhãn là một lượt
 * CI đỏ.
 *
 * <h2>⛔⛔ Phép dò: quét CÂN NGOẶC, ⛔ một biểu thức chính quy</h2>
 *
 * Bản đầu của phép đo này dùng `/<Button\b([^>]*?)\/>/` và cho ra **112/129 nút thiếu nhãn** — 87%.
 * Cả ba mẫu tôi mở ra kiểm đều là **dương tính giả**: `[^>]*?` dừng ở dấu `>` **bên trong**
 * `icon={<EditOutlined />}`, nên một nút CÓ CHỮ bị đọc thành nút tự đóng chỉ-có-icon. Con số thật
 * là **37/63** — và ngay sau khi vá một nút (điểm đo) thì bài *"trần phải bám sát"* bắt tôi hạ xuống
 * **36**, đúng việc của nó.
 *
 * <p>⇒ Bài học đã ghi sẵn trong kho (javadoc `CotPhase2CoDocGhiTest`): *một kết quả "mọi thứ đều
 * hỏng" gần như luôn là một **phép đo hỏng***. 87% lẽ ra đã phải là dấu hiệu. Vế cứu được lượt này
 * là mở ba tệp ra đọc bằng mắt — và đó chính là {@link #phepDoPhanBietDuocHaiHinhDang} ở dạng tự
 * động.
 */

const GOC = join(dirname(new URL(import.meta.url).pathname), '..');

/**
 * Trần hiện tại — **chỉ được giảm**.
 *
 * ⚠ Con số này là một phép ĐO ngày 16/09/2026, ⛔ phải một hạn mức ai đó chọn. Giảm nó khi dọn
 * xong một nhóm; ⛔ bao giờ tăng.
 */
const NGUONG = 34;

/** Từ vị trí `<Button`, trả về `[thuộc tính, có-tự-đóng-⛔, vị trí sau thẻ]` — quét cân `{}` và nháy. */
function theButton(ma: string, tu: number): [string, boolean, number] {
  let i = tu + '<Button'.length;
  let sau = 0;
  let nhay: string | null = null;
  while (i < ma.length) {
    const c = ma[i];
    if (nhay) {
      if (c === nhay) nhay = null;
    } else if (c === '"' || c === "'") {
      nhay = c;
    } else if (c === '{') {
      sau += 1;
    } else if (c === '}') {
      sau -= 1;
    } else if (sau === 0 && c === '>') {
      const tuDong = ma[i - 1] === '/';
      return [ma.slice(tu + '<Button'.length, i - (tuDong ? 1 : 0)), tuDong, i + 1];
    }
    i += 1;
  }
  return ['', false, ma.length];
}

function tepTsx(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const p = join(thuMuc, ten);
    if (statSync(p).isDirectory()) return tepTsx(p);
    return ten.endsWith('.tsx') && !ten.includes('.test.') ? [p] : [];
  });
}

/** Nút chỉ-có-icon thiếu tên khả truy cập, dạng `đường-dẫn:dòng`. */
function nutThieuTen(): string[] {
  const ket: string[] = [];
  for (const p of tepTsx(GOC)) {
    const ma = readFileSync(p, 'utf8');
    let i = 0;
    for (;;) {
      i = ma.indexOf('<Button', i);
      if (i < 0) break;
      const [thuocTinh, tuDong, het] = theButton(ma, i);
      const dau = i;
      i = het;
      // Có children ⇒ nút có CHỮ ⇒ ⛔ phải nút chỉ-có-icon.
      if (!tuDong || !thuocTinh.includes('icon=')) continue;
      if (thuocTinh.includes('aria-label') || thuocTinh.includes('title=')) continue;
      const truoc = ma.slice(Math.max(0, dau - 400), dau);
      const mo = truoc.lastIndexOf('<Tooltip');
      if (mo >= 0 && !truoc.slice(mo).includes('</Tooltip>')) continue;
      ket.push(`${p.slice(GOC.length + 1)}:${ma.slice(0, dau).split('\n').length}`);
    }
  }
  return ket;
}

describe('Nút chỉ có icon phải có tên đọc được', () => {
  it('⭐⭐ số nút thiếu tên khả truy cập ⛔ được TĂNG', () => {
    const thieu = nutThieuTen();
    expect(
      thieu.length,
      `Số nút chỉ-có-icon ⛔ có tên khả truy cập: ${thieu.length} (trần ${NGUONG}).\n\n` +
        `${thieu.join('\n')}\n\n` +
        'Trình đọc màn hình đọc một nút như vậy thành "button" — trống rỗng. Thêm MỘT trong ba: ' +
        'bọc <Tooltip title="…">, đặt aria-label="…", hoặc cho nút một nhãn chữ.\n\n' +
        '⛔ ĐỪNG nâng NGUONG cho hết đỏ — con số ấy là một phép ĐO, ⛔ phải một hạn mức. Nâng nó ' +
        'là tự tay tháo bộ canh (§11.17).',
    ).toBeLessThanOrEqual(NGUONG);
  });

  it('⚠ trần phải BÁM SÁT số thật — ⛔ để nó nới rộng ra rồi ⛔ ai biết', () => {
    // Dọn xong một nhóm mà quên hạ trần thì bộ canh lại có chỗ trống cho nợ mới chui vào,
    // và nó xanh suốt thời gian ấy. Đây là vế ép hạ trần ngay khi dọn.
    expect(
      nutThieuTen().length,
      `Đã dọn bớt rồi — hãy hạ NGUONG xuống đúng số thật (${nutThieuTen().length}).`,
    ).toBeGreaterThanOrEqual(NGUONG);
  });

  it('⭐ phép đo PHÂN BIỆT được nút-có-chữ với nút-chỉ-icon (vế đã bắt bản đầu sai 3 lần)', () => {
    // ⛔⛔ Vế cứu cả lớp này. Bản đầu dùng regex `[^>]*?` và đếm ra 112/129 vì dấu `>` bên trong
    //    `icon={<EditOutlined />}` cắt sớm — một nút CÓ CHỮ bị đọc thành nút tự đóng.
    const coChu = '<Button icon={<EditOutlined />} onClick={x}>Sửa thông tin</Button>';
    const chiIcon = '<Button type="text" icon={<EditOutlined />} onClick={x} />';

    const [, tuDongA] = theButton(coChu, 0);
    const [thuocTinhB, tuDongB] = theButton(chiIcon, 0);

    expect(tuDongA, 'nút CÓ CHỮ ⛔ được đọc thành nút tự đóng — đây là lỗi của bản đầu').toBe(
      false,
    );
    expect(tuDongB, 'nút chỉ-có-icon phải được nhận ra là tự đóng').toBe(true);
    expect(thuocTinhB).toContain('icon=');
  });

  it('⚠ chống tập rỗng: phải quét ra ít nhất 40 nút chỉ-có-icon', () => {
    // ⛔ quét được tệp nào (đổi cấu trúc thư mục, đổi tên component) thì bài chính xanh trên tập
    // RỖNG và trần 37 trở thành một lời bảo đảm cho một phép đo đã chết (luật 7).
    let tong = 0;
    for (const p of tepTsx(GOC)) {
      const ma = readFileSync(p, 'utf8');
      let i = 0;
      for (;;) {
        i = ma.indexOf('<Button', i);
        if (i < 0) break;
        const [thuocTinh, tuDong, het] = theButton(ma, i);
        i = het;
        if (tuDong && thuocTinh.includes('icon=')) tong += 1;
      }
    }
    expect(tong).toBeGreaterThanOrEqual(40);
  });
});
