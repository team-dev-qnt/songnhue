import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { RichTextEditor } from './RichTextEditor';
import {
  lyDoKichThuocSai,
  TRAN_COT_LUOI,
  TRAN_COT_SO,
  TRAN_HANG_LUOI,
  TRAN_HANG_SO,
} from './tableCommands';

/**
 * **Bảng phải chèn được với kích thước tuỳ ý** — WS-41 (T41.5, T41.12).
 *
 * <h2>Sự cố 04/09/2026</h2>
 *
 * QuanTran: *"chỉ có thể tạo được bảng chứ không sửa được. Ngoài ra, table đang chỉ cho phép
 * insert 3x3."* Đo được: toàn bộ `RichTextEditor.tsx` (743 dòng) có **đúng một** lệnh bảng —
 * `insertTable({rows:3, cols:3})` viết cứng — trong khi `TableKit` 3.31 đăng ký sẵn **19** lệnh.
 *
 * <h2>⚠⚠ Đây là tệp kiểm ĐẦU TIÊN dựng `RichTextEditor` thật</h2>
 *
 * Trước lượt này có **0** bài nào render nó. 71 bài của `components/business` đều là schema thuần
 * (dựng `new Editor(...)` headless). Nghĩa là toàn bộ thanh công cụ — 14 nút, ba chế độ, thanh
 * ảnh — chưa từng có một phép kiểm nào đi qua.
 *
 * <h2>⛔ Giới hạn của jsdom, và vì sao mọi bài dưới đây lái bằng BÀN PHÍM/CHUỘT trên nút</h2>
 *
 * jsdom 29.1.1 **không có** `elementFromPoint`, `caretRangeFromPoint`, `DataTransfer`,
 * `URL.createObjectURL` (grep toàn gói = 0 kết quả). Mà `prosemirror-view` gọi `view.posAtCoords`
 * trong `handlers.mousedown` ⇒ **bấm chuột vào vùng `.ProseMirror` ném `TypeError`**.
 *
 * Hệ quả bắt buộc: không bài nào ở đây được bấm vào ô bảng hay đặt con trỏ bằng chuột. Chúng chỉ
 * bấm vào **nút của thanh công cụ** (nút thường, ngoài vùng contenteditable) rồi đọc `value` mà
 * `onChange` trả ra. Đó là đúng cái vòng người dùng đi, và là phần jsdom kiểm được thật.
 *
 * ⚠ Ghi ra để cái xanh của tệp này không bị đọc rộng hơn nó (quy tắc 28): nó **không** chứng minh
 * "thả ảnh đâu thì ảnh nằm đó" (T41.16), và **không** chứng minh bảng trông đúng trên trình duyệt.
 */

/** Dựng trình soạn thảo và trả về hàm đọc HTML mới nhất mà nó phát ra. */
function dung() {
  const onChange = vi.fn();
  render(
    <AntdApp>
      <RichTextEditor value="" onChange={onChange} />
    </AntdApp>,
  );
  return {
    onChange,
    /** HTML của lượt `onChange` gần nhất; chuỗi rỗng nếu chưa lượt nào. */
    html: () => (onChange.mock.calls.at(-1)?.[0] as string | undefined) ?? '',
  };
}

async function moHopChenBang(nguoiDung: ReturnType<typeof userEvent.setup>) {
  await nguoiDung.click(screen.getByRole('button', { name: /Chèn bảng — chọn số hàng/ }));
  await screen.findByRole('grid', { name: 'Chọn nhanh kích thước bảng' });
}

/** Đếm thẻ mở — ⛔ không dùng `split().length` trên chuỗi rỗng, nó trả 1 chứ không phải 0. */
function dem(html: string, the: string): number {
  return (html.match(new RegExp(`<${the}[\\s>]`, 'g')) ?? []).length;
}

/**
 * Đặt giá trị cho một `InputNumber`.
 *
 * ⚠⚠ `fireEvent.change` chứ **không** `userEvent.type` — đã ĐO, không phải sở thích:
 *
 * <pre>
 *   giá trị ban đầu             = "3"
 *   sau `type('{selectall}12')` = "312"   ← `{selectall}` KHÔNG chọn được nội dung ô này
 *   sau `fireEvent.change(12)`  = "12"
 * </pre>
 *
 * `"312"` bị AntD kẹp về trần 15, nên một bài khẳng định "12 cột" sẽ đỏ với con số **15** —
 * một con số chẳng liên quan gì tới thứ đang kiểm, và người đọc lượt sau sẽ đi tìm lỗi ở
 * `insertTable`. `clear()` rồi gõ cũng hỏng cùng kiểu vì ô là **có kiểm soát**.
 */
function datSo(nhan: string, gia: number) {
  fireEvent.change(screen.getByRole('spinbutton', { name: nhan }), {
    target: { value: String(gia) },
  });
}

afterEach(cleanup);

describe('Chèn bảng kích thước tuỳ ý', () => {
  it('⚠ vế chống xanh-trên-tập-rỗng: hộp thoại dựng đủ ô lưới', async () => {
    // Luật 7 — nếu lưới không render thì mọi bài dưới đây "không tìm thấy nút" và tác giả sẽ
    // sửa selector cho tới khi xanh, thay vì phát hiện lưới hỏng.
    const nguoiDung = userEvent.setup();
    dung();
    await moHopChenBang(nguoiDung);

    const oLuoi = screen.getAllByRole('button', { name: /hàng × \d+ cột$/ });
    expect(oLuoi).toHaveLength(TRAN_HANG_LUOI * TRAN_COT_LUOI);
  });

  it('⭐⭐ chèn 4×7 bằng LƯỚI — đúng 7 cột, đúng 4 hàng', async () => {
    const nguoiDung = userEvent.setup();
    const { html } = dung();
    await moHopChenBang(nguoiDung);

    await nguoiDung.click(screen.getByRole('button', { name: '4 hàng × 7 cột' }));

    await waitFor(() => expect(html()).toContain('<table'));
    expect(dem(html(), 'tr'), 'số hàng').toBe(4);
    expect(dem(html(), 'th'), 'hàng đầu là tiêu đề ⇒ 7 ô <th>').toBe(7);
    expect(dem(html(), 'td'), '3 hàng thân × 7 cột').toBe(21);
  });

  it('⭐⭐ chèn 12 cột bằng Ô NHẬP SỐ — trần của LƯỚI không phải trần của tính năng', async () => {
    // Bài này là lý do ô nhập số tồn tại. Lưới dừng ở 10 cột; bảng tiến độ sản xuất theo tháng
    // cần 13 (CR-30). Không có bài này thì "đã sửa xong 3×3" nghe như đã xong, trong khi người
    // dùng vẫn không tới được cỡ họ cần — chỉ đổi trần từ 3 thành 10.
    const nguoiDung = userEvent.setup();
    const { html } = dung();
    await moHopChenBang(nguoiDung);

    datSo('Số cột', 12);
    await nguoiDung.click(screen.getByRole('button', { name: 'Chèn' }));

    await waitFor(() => expect(html()).toContain('<table'));
    expect(dem(html(), 'th'), '12 cột tiêu đề').toBe(12);
    expect(TRAN_COT_LUOI, 'lưới vẫn dừng ở 10 — đây chính là điểm của bài kiểm').toBeLessThan(12);
  });

  it('⭐⭐ TẮT công tắc rồi chèn bằng LƯỚI ⇒ KHÔNG có ô tiêu đề nào', async () => {
    // ⚠ Bài này bắt một lỗi CÓ THẬT trong bản đầu: lưới gọi thẳng lệnh chèn còn công tắc chỉ được
    // nút "Chèn" đọc. Người dùng tắt công tắc, bấm ô lưới, và bảng **vẫn có hàng tiêu đề** — lưu
    // thành công, không lỗi, không cách nào biết. Một bài chỉ kiểm đường nhập số sẽ xanh trọn vẹn.
    const nguoiDung = userEvent.setup();
    const { html } = dung();
    await moHopChenBang(nguoiDung);

    await nguoiDung.click(screen.getByRole('switch'));
    await nguoiDung.click(screen.getByRole('button', { name: '3 hàng × 4 cột' }));

    await waitFor(() => expect(html()).toContain('<table'));
    expect(dem(html(), 'th'), 'công tắc tắt ⇒ không ô tiêu đề nào').toBe(0);
    expect(dem(html(), 'td'), '3 hàng × 4 cột đều là ô thường').toBe(12);
  });

  it('⭐ chèn xong thì hộp thoại BẮT ĐẦU đóng — không bắt người dùng bấm Huỷ', async () => {
    const nguoiDung = userEvent.setup();
    dung();
    await moHopChenBang(nguoiDung);
    await nguoiDung.click(screen.getByRole('button', { name: '2 hàng × 2 cột' }));

    // ⚠⚠ Khẳng định vào lớp hiệu ứng của AntD, và đây là một nhượng bộ CÓ LÝ DO, không phải cẩu thả.
    // Đo được: sau lượt chèn, `.ant-modal` mang `ant-zoom-leave ant-zoom-leave-active` và **đứng
    // im ở đó** — rc-motion tháo phần tử khi nhận `transitionend`, mà jsdom không bao giờ bắn sự
    // kiện ấy. Nên `destroyOnHidden` không tháo, và `queryByRole('grid')` **vẫn thấy lưới mãi mãi**.
    // Một bài khẳng định `toBeNull()` ở đây sẽ đỏ vì môi trường, không vì sản phẩm.
    // `ant-zoom-leave` phân biệt được đúng hai trạng thái ta cần phân biệt: đang mở / đã bắt đầu
    // đóng (quy tắc 9). ⛔ Nó KHÔNG chứng minh hộp thoại biến mất trên trình duyệt — chuyện đó
    // thuộc lượt chạy tay ở §Nghiệm thu.
    await waitFor(() =>
      expect(document.querySelector('.ant-modal')?.className).toContain('ant-zoom-leave'),
    );
  });

  it('⭐ bàn phím: mũi tên di chuyển trên lưới, Enter chèn — đường duy nhất không cần chuột', async () => {
    const nguoiDung = userEvent.setup();
    const { html } = dung();
    await moHopChenBang(nguoiDung);

    // Ô (1,1) là ô duy nhất nhận Tab; từ đó đi bằng mũi tên (roving tabindex).
    screen.getByRole('button', { name: '1 hàng × 1 cột' }).focus();
    await nguoiDung.keyboard('{ArrowRight}{ArrowRight}{ArrowDown}');
    await nguoiDung.keyboard('{Enter}');

    await waitFor(() => expect(html()).toContain('<table'));
    expect(dem(html(), 'th'), '2 lần sang phải ⇒ 3 cột').toBe(3);
    expect(dem(html(), 'tr'), '1 lần xuống ⇒ 2 hàng').toBe(2);
  });

  it('⭐ nhập 99 cột KHÔNG bao giờ cho ra bảng quá trần — bất biến, không phụ thuộc AntD kẹp kiểu gì', async () => {
    // ⚠⚠ Bài này đã sai HAI lần trước khi đúng, và cả hai lần đều vì tôi khẳng định về một trạng
    // thái không tồn tại thay vì đo:
    //   ① "nút Chèn bị khoá"       → ĐỎ. `InputNumber` khai `max` nên trạng thái quá-trần không
    //                                 tới được từ giao diện.
    //   ② "kẹp về đúng 15 cột"     → ĐỎ, ra 3. Đo được: AntD **không truyền** giá trị ngoài dải ra
    //                                 `onChange`, nên `cot` giữ nguyên giá trị cũ (3).
    // Khẳng định đúng là **bất biến**: dù AntD chặn, kẹp, hay bỏ qua — bảng chèn ra không bao giờ
    // vượt trần. Nó không chia sẻ giả định nào với cách AntD cư xử (quy tắc 29).
    const nguoiDung = userEvent.setup();
    const { html } = dung();
    await moHopChenBang(nguoiDung);

    datSo('Số cột', 99);
    await nguoiDung.click(screen.getByRole('button', { name: 'Chèn' }));

    await waitFor(() => expect(html()).toContain('<table'));
    const soCot = dem(html(), 'th') + dem(html(), 'td') / Math.max(dem(html(), 'tr') - 1, 1);
    expect(dem(html(), 'th'), `không được vượt trần ${TRAN_COT_SO}`).toBeLessThanOrEqual(
      TRAN_COT_SO,
    );
    expect(soCot, 'và phải là một bảng thật, không phải bảng rỗng').toBeGreaterThan(0);
  });
});

describe('Chốt chặn: không chèn bảng vào chỗ sẽ phá thứ khác', () => {
  it('⭐⭐ đang ở TRONG một bảng ⇒ nút Chèn bảng bị khoá, và nói RA lý do', async () => {
    // Bảng lồng bảng dựng được thật (`tableCell.content = 'block+'`) và `can().insertTable()`
    // **luôn trả `true`** — không có chốt chặn nào ở tầng lệnh. Một bảng lồng trong bảng thì nút
    // "Xoá bảng" ở lớp ngoài không với tới, và trên cổng nó thừa hưởng `display:block` của bảng cha.
    const nguoiDung = userEvent.setup();
    dung();
    await moHopChenBang(nguoiDung);
    // `insertTable` đặt con trỏ vào ô đầu tiên ⇒ sau lượt này ta ĐANG ở trong bảng, không cần
    // bấm chuột vào ô (thứ jsdom không làm được).
    await nguoiDung.click(screen.getByRole('button', { name: '2 hàng × 2 cột' }));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /Con trỏ đang ở trong một bảng/ })).toBeDisabled(),
    );
  });

  it('⛔ kiểm chứng ngược: khi KHÔNG ở trong bảng thì nút mở bình thường', async () => {
    // Không có bài này thì một nút khoá cứng vĩnh viễn cũng cho bài trên xanh trọn vẹn — và
    // người dùng mất hẳn đường chèn bảng. Hai bài cùng nhau mới phân biệt được hai trạng thái.
    const nguoiDung = userEvent.setup();
    dung();
    const nut = screen.getByRole('button', { name: /Chèn bảng — chọn số hàng/ });
    expect(nut).not.toBeDisabled();
    await nguoiDung.click(nut);
    expect(await screen.findByRole('grid')).toBeInTheDocument();
  });
});

describe('lyDoKichThuocSai — chốt chặn ở tầng hàm', () => {
  // Giao diện kẹp giá trị nên trạng thái quá-trần không tới được bằng chuột. Nhưng hàm này là
  // chốt chặn cho MỌI nơi gọi (kể cả nơi gọi thứ hai chưa tồn tại), nên nó phải được kiểm thẳng.
  it('nhận cỡ hợp lệ', () => {
    expect(lyDoKichThuocSai({ hang: 3, cot: 7 })).toBeNull();
    expect(lyDoKichThuocSai({ hang: TRAN_HANG_SO, cot: TRAN_COT_SO })).toBeNull();
  });

  it('⛔ từ chối cỡ sai, và nói RA VIỆC PHẢI LÀM chứ không chỉ nói sai', () => {
    expect(lyDoKichThuocSai({ hang: 0, cot: 3 })).toContain('ít nhất');
    expect(lyDoKichThuocSai({ hang: 3, cot: 0 })).toContain('ít nhất');
    expect(lyDoKichThuocSai({ hang: TRAN_HANG_SO + 1, cot: 3 })).toContain('tách thành nhiều bảng');
    expect(lyDoKichThuocSai({ hang: 3, cot: TRAN_COT_SO + 1 })).toContain('cuộn ngang');
    expect(lyDoKichThuocSai({ hang: 2.5, cot: 3 })).toContain('số nguyên');
  });
});
