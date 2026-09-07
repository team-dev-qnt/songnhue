import { describe, expect, it } from 'vitest';

import { boDauGiuViTri, chiaTheoTuKhoa } from './danhDauTuKhoa';

/**
 * **Highlight từ khoá — CN-01.8 / T36.10.**
 *
 * Bài chịu lực là {@link giuNguyenSoKyTu}: cả cơ chế tô dựa vào việc bản bỏ dấu và bản gốc có
 * **cùng số ký tự**. Mất bất biến ấy thì phần tô lệch dần theo số chữ có dấu đứng trước — một
 * lỗi hiện ra như *"tô trúng nửa từ, càng về cuối câu càng lệch"*, và ⛔ không có gì đỏ.
 */
describe('boDauGiuViTri — bất biến chịu lực', () => {
  it('⛔⛔ GIỮ NGUYÊN số ký tự — vế mà cả cơ chế tô dựa vào', () => {
    // ⚠ Vế này ⛔ KHÔNG chia sẻ giả định nào với phép cắt ở `chiaTheoTuKhoa` (luật 29): nó chỉ
    //   đếm. `'ế'.normalize('NFD')` cho HAI code point, nên một bản dùng `.replace()` trên cả
    //   chuỗi sẽ đỏ ngay ở đây.
    for (const mau of [
      'Cống Liên Mạc',
      'Điều tiết nước hệ thống sông Nhuệ',
      'Trạm bơm Đan Hoài — đợt tưới Đông Xuân',
      'ĐẬP TRÀN',
      'abc 123',
    ]) {
      expect(boDauGiuViTri(mau).length, `lệch ở "${mau}"`).toBe(Array.from(mau).length);
    }
  });

  it('⭐ Bỏ dấu đúng, kể cả `đ` — ký tự NFD ⛔ không phân rã được', () => {
    expect(boDauGiuViTri('Điều tiết').join('')).toBe('dieu tiet');
    expect(boDauGiuViTri('Cống').join('')).toBe('cong');
    // ⛔ Vế phân biệt: một hàm chỉ `toLowerCase()` cũng qua được phép đếm ở trên.
    expect(boDauGiuViTri('ế').join('')).toBe('e');
  });
});

describe('chiaTheoTuKhoa', () => {
  it('⭐⭐ Tìm KHÔNG DẤU nhưng cắt trên bản CÓ DẤU — đúng vị trí', () => {
    const doan = chiaTheoTuKhoa('Cống Liên Mạc đã đóng', 'lien mac');

    expect(doan.map((d) => d.chu).join('')).toBe('Cống Liên Mạc đã đóng');
    expect(doan.filter((d) => d.khop).map((d) => d.chu)).toEqual(['Liên Mạc']);
  });

  it('⭐ ⛔ Không phân biệt hoa thường, và chịu được từ khoá CÓ dấu', () => {
    expect(
      chiaTheoTuKhoa('Trạm bơm Đan Hoài', 'ĐAN hoài')
        .filter((d) => d.khop)
        .map((d) => d.chu),
    ).toEqual(['Đan Hoài']);
  });

  it('⭐ Nhiều lần khớp trong một chuỗi', () => {
    const doan = chiaTheoTuKhoa('Cống A và cống B', 'cong');
    expect(doan.filter((d) => d.khop).map((d) => d.chu)).toEqual(['Cống', 'cống']);
    expect(doan.map((d) => d.chu).join('')).toBe('Cống A và cống B');
  });

  it('⛔ Từ khoá rỗng / ⛔ không khớp ⇒ MỘT đoạn nguyên văn, ⛔ không đoạn nào `khop`', () => {
    // Nơi gọi ⛔ không cần một nhánh riêng cho hai trường hợp này.
    for (const khoa of ['', '   ', undefined, null, 'khong-co-o-day']) {
      const doan = chiaTheoTuKhoa('Cống Liên Mạc', khoa);
      expect(doan).toHaveLength(1);
      expect(doan[0]).toEqual({ chu: 'Cống Liên Mạc', khop: false });
    }
  });

  it('⛔⛔ MỌI đoạn ghép lại phải bằng ĐÚNG chuỗi gốc — vế chống mất chữ', () => {
    // ⛔ Đây là vế bắt được một phép cắt lệch: tô sai chỗ vẫn "trông có vẻ chạy", nhưng NUỐT một
    //    ký tự thì người đọc mất chữ trong chính kết quả tìm kiếm của họ.
    for (const [chu, khoa] of [
      ['Điều tiết nước hệ thống sông Nhuệ', 'nuoc'],
      ['ĐẬP TRÀN số 1', 'dap tran'],
      ['Cống Liên Mạc', 'cống liên mạc'],
      ['abc', 'abc'],
    ] as const) {
      expect(
        chiaTheoTuKhoa(chu, khoa)
          .map((d) => d.chu)
          .join(''),
        `ghép lại ⛔ không khớp với "${chu}"`,
      ).toBe(chu);
    }
  });

  it('⭐ Khớp CẢ CỤM, ⛔ không tách từng từ', () => {
    // ⛔ Tách từ thì gõ "cống Liên Mạc" sẽ tô mọi chữ "cống" ở mọi tiêu đề — nhiễu tới mức người
    //    đọc thôi nhìn màu, và lúc ấy phần tô ⛔ không còn nói gì. Backend cũng khớp cả cụm.
    const doan = chiaTheoTuKhoa('Cống Liên Mạc và cống Hà Đông', 'cong lien mac');
    expect(doan.filter((d) => d.khop).map((d) => d.chu)).toEqual(['Cống Liên Mạc']);
  });
});
