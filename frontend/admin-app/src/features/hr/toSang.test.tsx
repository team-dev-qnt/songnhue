import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { boDau } from './boDau';
import { ToSang } from './toSang';

/**
 * ⛔⛔ Phép chuẩn hoá văn bản của danh bạ sống ở **HAI** nơi ⛔ không dùng chung mã được:
 * `sn_khong_dau(text)` (PL/pgSQL, `V202608191014`) quyết định **ai được tìm thấy**, còn
 * {@link boDau} (TypeScript) quyết định **phần nào được tô sáng**.
 *
 * Lệch nhau thì triệu chứng rất dễ đọc nhầm: backend trả **đúng người**, màn hình **⛔ không tô
 * gì**, và người dùng kết luận *"hệ thống tìm sai"*. Đó là luật 14 — hai nơi con người phải nhớ
 * cùng một điều thì cần một phép kiểm nhớ hộ.
 *
 * ⚠ Bài này ⛔ **không** chứng minh hai bên khớp nhau tuyệt đối (⛔ không có cách nào chạy
 * `unaccent` của Postgres ở đây). Nó ghim đúng **những cặp mà tiếng Việt dùng hằng ngày** — và
 * quan trọng nhất là cặp `đ → d`, thứ mà `NFD` một mình ⛔ không xử lý được.
 */
describe('boDau — bản JS của sn_khong_dau', () => {
  it('bỏ dấu thanh và dấu mũ, hạ chữ thường', () => {
    expect(boDau('Nguyễn Văn Hoà')).toBe('nguyen van hoa');
    expect(boDau('TRẦN THỊ BÍCH')).toBe('tran thi bich');
  });

  it('⛔⛔ `đ` và `Đ` phải thành `d` — NFD KHÔNG tách chúng ra', () => {
    // Thiếu hai dòng xử lý riêng thì gõ `dieu` ⛔ không tô được `Điều`, trong khi backend VẪN trả
    // về nó (`unaccent` của Postgres có xử lý). Hai vế lệch nhau đúng ở đây.
    expect(boDau('Đê Điều')).toBe('de dieu');
    expect(boDau('đường')).toBe('duong');
  });

  it('⛔ Giữ nguyên độ dài so với bản NFC — chỉ số tìm được dùng thẳng để cắt chuỗi', () => {
    // ⛔⛔ Đây là tiền đề của `ToSang`: nó cắt chuỗi GỐC bằng chỉ số tìm trên chuỗi ĐÃ BỎ DẤU.
    //    Sai tiền đề này thì ô tô sáng cắt giữa một ký tự.
    for (const chu of ['Nguyễn Văn Hoà', 'Đỗ Thị Ước', 'Lê Quốc Huy', 'Phạm Thị Lan']) {
      expect(boDau(chu)).toHaveLength(chu.normalize('NFC').length);
    }
  });
});

describe('ToSang — tô sáng bỏ dấu, giống hệt phép tìm của backend', () => {
  // ⚠ Khẳng định trên `querySelector('mark')`, ⛔ KHÔNG `getByText`: chuỗi `Hoà` là hậu tố của cả
  //   họ tên, nên `getByText` khớp CẢ thẻ cha lẫn `<mark>` và ném *"found multiple elements"* —
  //   một lỗi của đồ gá đọc y hệt một lỗi của mã. Bản đầu của bài này đỏ vì đúng chuyện ấy.
  it('⭐ gõ KHÔNG dấu vẫn tô được phần CÓ dấu', () => {
    // Backend trả về người này (`sn_khong_dau` khớp) — màn hình phải tô, ⛔ không được im lặng.
    const { container } = render(<ToSang van="Nguyễn Văn Hoà" tuKhoa="hoa" />);
    expect(container.querySelector('mark')?.textContent).toBe('Hoà');
  });

  it('⭐ gõ CÓ dấu cũng tô — hai chiều', () => {
    const { container } = render(<ToSang van="Nguyễn Văn Hoà" tuKhoa="Hoà" />);
    expect(container.querySelector('mark')?.textContent).toBe('Hoà');
  });

  it('⛔ từ khoá rỗng thì ⛔ không tô gì — vế phân biệt', () => {
    // Thiếu vế này thì một cài đặt tô SẠCH mọi thứ cũng qua ba bài trên.
    const { container } = render(<ToSang van="Nguyễn Văn Hoà" tuKhoa="   " />);
    expect(container.querySelector('mark')).toBeNull();
    expect(container.textContent).toBe('Nguyễn Văn Hoà');
  });

  it('⛔ từ khoá ⛔ không khớp thì trả nguyên văn, ⛔ không mất chữ nào', () => {
    const { container } = render(<ToSang van="Nguyễn Văn Hoà" tuKhoa="xyz" />);
    expect(container.querySelector('mark')).toBeNull();
    expect(container.textContent).toBe('Nguyễn Văn Hoà');
  });

  it('⛔⛔ Chuỗi vào dạng NFD vẫn cắt ĐÚNG ký tự — lỗi chỉ hiện ở một số máy nhập liệu', () => {
    const nfd = 'Nguyễn Văn Hoà'.normalize('NFD');
    const { container } = render(<ToSang van={nfd} tuKhoa="hoa" />);
    expect(container.textContent).toBe('Nguyễn Văn Hoà');
    expect(container.querySelector('mark')?.textContent).toBe('Hoà');
  });
});
