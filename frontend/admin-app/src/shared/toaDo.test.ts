import { describe, expect, it } from 'vitest';

import { bocToaDo, trongVungSongNhue } from './toaDo';

/**
 * Toạ độ thật của **Cống Liên Mạc** (K0+390, sông Nhuệ) — dùng làm mốc cho mọi định dạng, để một
 * bài đỏ chỉ ra *định dạng nào* hỏng chứ ⛔ không phải *phép so nào* hỏng.
 *
 * ⚠ Đây là dữ liệu **kiểm thử**, ⛔ không phải dữ liệu sản phẩm — ⛔ đừng chép nó vào migration.
 * Bản chụp G8 của Công ty ⛔ không có cột toạ độ và cấm lệnh chống bịa vẫn đứng nguyên.
 */
const VI_DO = 21.083333;
const KINH_DO = 105.766667;

describe('bocToaDo', () => {
  describe('cặp thập phân — thứ Google Maps đưa vào clipboard', () => {
    it.each([
      ['dấu phẩy + khoảng trắng', '21.083333, 105.766667'],
      ['dấu phẩy sát', '21.083333,105.766667'],
      ['chỉ khoảng trắng', '21.083333 105.766667'],
      ['chấm phẩy', '21.083333;105.766667'],
      ['có khoảng trắng thừa hai đầu', '   21.083333, 105.766667  \n'],
    ])('%s', (_ten, chuoi) => {
      expect(bocToaDo(chuoi)).toEqual({ viDo: VI_DO, kinhDo: KINH_DO });
    });

    it('⛔ số âm vẫn nhận — Nam bán cầu là hợp lệ, dù Công ty ⛔ không có công trình ở đó', () => {
      expect(bocToaDo('-8.409518, 115.188919')).toEqual({ viDo: -8.409518, kinhDo: 115.188919 });
    });
  });

  describe('độ-phút-giây', () => {
    it('dạng Google Maps hiện ở thanh tìm kiếm', () => {
      const t = bocToaDo(`21°05'00.0"N 105°46'00.0"E`);
      expect(t?.viDo).toBeCloseTo(21.083333, 5);
      expect(t?.kinhDo).toBeCloseTo(105.766667, 5);
    });

    it('⭐ kinh độ đứng TRƯỚC vẫn ra đúng cột — hướng quyết định, ⛔ không phải thứ tự', () => {
      const t = bocToaDo(`105°46'00.0"E 21°05'00.0"N`);
      expect(t?.viDo).toBeCloseTo(21.083333, 5);
      expect(t?.kinhDo).toBeCloseTo(105.766667, 5);
    });

    it('S/W cho ra số âm', () => {
      const t = bocToaDo(`8°24'34.3"S 115°11'20.1"W`);
      expect(t?.viDo).toBeLessThan(0);
      expect(t?.kinhDo).toBeLessThan(0);
    });
  });

  describe('liên kết Google Maps', () => {
    it.each([
      ['dạng /@lat,lng,zoom', 'https://www.google.com/maps/@21.083333,105.766667,17z'],
      ['dạng ?q=', 'https://maps.google.com/?q=21.083333,105.766667'],
      [
        'dạng place kèm /@',
        'https://www.google.com/maps/place/C%E1%BB%91ng/@21.083333,105.766667,15z/data=!3m1',
      ],
    ])('%s', (_ten, url) => {
      expect(bocToaDo(url)).toEqual({ viDo: VI_DO, kinhDo: KINH_DO });
    });
  });

  describe('⛔ những thứ PHẢI trả null — bộ bóc ⛔ không được "cố hiểu"', () => {
    it.each([
      ['rỗng', ''],
      ['chỉ khoảng trắng', '   '],
      ['null', null],
      ['undefined', undefined],
      ['một số duy nhất', '21.083333'],
      ['chữ', 'Cống Liên Mạc'],
      // ⛔⛔ Bài chịu lực. `K72+000` là LÝ TRÌNH của Cống Vân Đình — nó sống cạnh ô toạ độ trên
      //    cùng một bước của biểu mẫu, nên dán nhầm ô là chuyện sẽ xảy ra. Một bộ bóc dễ dãi đọc
      //    nó thành `72, 0` — một điểm ngoài khơi Ấn Độ Dương, và ⛔ không gì báo.
      ['lý trình', 'K72+000'],
      ['ngày tháng', '09/09/2026'],
      ['ba số', '21.08, 105.76, 17'],
    ])('%s ⇒ null', (_ten, chuoi) => {
      expect(bocToaDo(chuoi as string)).toBeNull();
    });

    it('⛔⛔ vĩ độ vượt ±90 ⇒ null — đây là cách một cặp BỊ ĐẢO lộ ra', () => {
      // ⚠ Người dùng dán "kinh độ trước": với Việt Nam kinh độ ~105 LUÔN vượt biên vĩ độ, nên cặp
      //   đảo ⛔ không thể đi lọt. ⛔ Bộ bóc cố ý ⛔ KHÔNG tự đảo hộ — xem javadoc `toaDo.ts`: tự
      //   sửa thì cặp ấy được NHẬN và điểm rơi sai trong im lặng.
      expect(bocToaDo('105.766667, 21.083333')).toBeNull();
    });
  });
});

describe('trongVungSongNhue — NHẮC, ⛔ không CHẶN', () => {
  it('Cống Liên Mạc nằm trong vùng', () => {
    expect(trongVungSongNhue({ viDo: VI_DO, kinhDo: KINH_DO })).toBe(true);
  });

  it('⛔ TP Hồ Chí Minh ⛔ không nằm trong vùng — nhưng bộ bóc VẪN trả về cặp ấy', () => {
    const t = bocToaDo('10.762622, 106.660172');
    expect(t).not.toBeNull();
    expect(trongVungSongNhue(t!)).toBe(false);
  });
});
