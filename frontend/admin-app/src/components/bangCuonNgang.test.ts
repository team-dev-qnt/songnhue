import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * **Mọi bảng quản trị phải khai bề ngang tối thiểu.**
 *
 * <h2>Lỗi đã có thật — đo ngày 01/09/2026</h2>
 *
 * QuanTran báo trang *Nguồn dữ liệu* vỡ bố cục: cột "Địa chỉ" bóp còn ~29px và URL
 * `http://songnhue.bhh40.net` xuống dòng **từng ký tự**. Rà ra thì đó không phải lỗi của riêng
 * trang ấy — **21 tệp dùng `<Table>` và chỉ 4 khai cuộn ngang**.
 *
 * <p>Ba thứ cộng lại, không cái nào tự nó đủ:
 *
 * <ol>
 *   <li>không cột nào khai `ellipsis` và bảng không khai `scroll` ⇒ `rc-table` chọn
 *       `tableLayout: 'auto'`, và dưới `auto` thì `<col width>` chỉ là **gợi ý**;
 *   <li>các cột cố định cộng lại đã gần hết bề ngang khả dụng (`viewport − 336`);
 *   <li>`.ant-table-cell{overflow-wrap:break-word}` ⇒ `min-content` của một URL bằng **một ký tự**.
 * </ol>
 *
 * ⚠ Lỗi có ở **mọi** bề ngang, chỉ **thấy được** dưới ~1600px. Cùng hình dạng §10.62, nơi
 * `flex-wrap` che một thanh điều hướng tràn 22% ở *mọi* màn hình.
 *
 * <h2>⚠⚠ Phạm vi tự khai (luật 28) — đọc trước khi tin cái xanh của bài này</h2>
 *
 * Bài này đọc **văn bản nguồn**: nó khẳng định mỗi tệp có `<Table` đều khai `scroll={{ x`
 * (hoặc truyền `scrollX` cho `DataTable`). Nó **KHÔNG** chứng minh:
 *
 * <ul>
 *   <li>con số ấy **đủ lớn** — đặt `scroll={{ x: 1 }}` vẫn qua bài này;
 *   <li>bảng **thật sự** không bóp chữ trên trình duyệt.
 * </ul>
 *
 * Hai điều đó chỉ đo được bằng hộp thật (`getBoundingClientRect`) trên một stack đang chạy.
 * `public-web` đã có bộ đo Playwright cho việc ấy; `admin-app` thì **chưa** — nợ đang mở.
 * Nên hãy đọc bài này đúng như nó là: một cái lưới chặn *quên khai*, không phải một bảo đảm
 * về bố cục.
 */

const GOC = join(process.cwd(), 'src');

/**
 * Tệp được miễn — mỗi mục kèm **lý do**, không phải danh sách để dài thêm.
 *
 * ⛔ Thêm một dòng vào đây là một quyết định phải giải thích được, không phải cách làm hết đỏ.
 */
const MIEN: Record<string, string> = {
  'components/DataTable.tsx':
    'chính là bộ bọc phát ra `scroll` — nó nhận `scrollX` từ nơi gọi, nên không tự khai',
  'components/business/RichTextEditor.tsx':
    'không có bảng nào; chuỗi khớp là biểu tượng `<TableOutlined />` trên thanh công cụ',
};

function moiTepTsx(thuMuc: string, ket: string[] = []): string[] {
  for (const ten of readdirSync(thuMuc)) {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) {
      moiTepTsx(duong, ket);
    } else if (ten.endsWith('.tsx') && !ten.endsWith('.test.tsx')) {
      ket.push(duong);
    }
  }
  return ket;
}

describe('bảng quản trị luôn cuộn ngang thay vì bóp chữ', () => {
  const tepCoBang = moiTepTsx(GOC)
    .map((duong) => ({
      duong,
      tuongDoi: duong.slice(GOC.length + 1),
      nguon: readFileSync(duong, 'utf8'),
    }))
    .filter((t) => t.nguon.includes('<Table') || t.nguon.includes('<DataTable'));

  it('⛔ vế chống xanh-trên-tập-rỗng: phải quét được một số lượng bảng đáng kể', () => {
    // Luật 7: nếu bộ quét hỏng (đổi thư mục, đổi cách khai) thì danh sách rỗng và bài dưới
    // xanh trọn vẹn mà không canh gì. Con số 15 là sàn đo được ngày 01/09 (21 tệp), cố ý đặt
    // thấp hơn để việc xoá vài màn hình không làm đỏ, nhưng đủ cao để bắt bộ quét chết hẳn.
    expect(tepCoBang.length).toBeGreaterThanOrEqual(15);
  });

  it('⭐ mọi tệp có bảng đều khai bề ngang tối thiểu', () => {
    const thieu = tepCoBang
      .filter((t) => !(t.tuongDoi in MIEN))
      .filter((t) => !/scroll=\{\{\s*x[:\s]/.test(t.nguon) && !/scrollX=/.test(t.nguon))
      .map((t) => t.tuongDoi);

    expect(
      thieu,
      'Bảng không khai `scroll={{ x: … }}` (hoặc `scrollX` nếu dùng `DataTable`) sẽ BÓP CHỮ ở ' +
        'màn hình hẹp thay vì cuộn ngang. Chuỗi dài nhất trong bảng — URL, đường dẫn tệp, mã ' +
        'công trình — co về một ký tự mỗi dòng, và không có gì báo lỗi.',
    ).toEqual([]);
  });

  it('⛔ danh sách miễn không được phình ra trong im lặng', () => {
    // Mỗi mục miễn là một chỗ bài kiểm này KHÔNG canh. Danh sách càng dài thì cái xanh của nó
    // càng nói ít đi — nên chính độ dài ấy phải là một khẳng định.
    expect(Object.keys(MIEN)).toHaveLength(2);

    // Và mục miễn phải trỏ tới một tệp CÓ THẬT: một mục trỏ vào tệp đã xoá là một lỗ hổng câm.
    const duongDaQuet = new Set(tepCoBang.map((t) => t.tuongDoi));
    for (const tep of Object.keys(MIEN)) {
      expect(
        duongDaQuet.has(tep),
        `mục miễn "${tep}" không còn tồn tại — gỡ nó khỏi danh sách`,
      ).toBe(true);
    }
  });
});
