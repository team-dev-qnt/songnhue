import { describe, expect, it } from 'vitest';

import { docNguonBaiViet } from './nguonBaiViet';

describe('docNguonBaiViet — ô "Nguồn tin" của bài viết (nợ T26.63)', () => {
  it('rỗng / chỉ khoảng trắng / null → null, để nơi gọi BỎ HẲN dòng', () => {
    // ⛔ Không nhãn mặc định. Nhãn mặc định là thứ đã tạo ra câu sai sự thật trên cổng.
    expect(docNguonBaiViet(null)).toBeNull();
    expect(docNguonBaiViet(undefined)).toBeNull();
    expect(docNguonBaiViet('')).toBeNull();
    expect(docNguonBaiViet('   ')).toBeNull();
  });

  it('URL thật trong CSDL → nhãn là tên miền, href là địa chỉ đầy đủ', () => {
    // Giá trị NGUYÊN VĂN từ bộ seed `V202608251100__seed_portal_content.sql` — bộ canh theo hình
    // dạng phải được thử với dữ liệu ĐANG DÙNG (luật 25).
    const that =
      'https://hanoimoi.vn/xa-phu-xuyen-tang-cuong-ung-truc-chu-dong-phong-ngua-ngap-ung-1238587.html';
    expect(docNguonBaiViet(that)).toEqual({ nhan: 'hanoimoi.vn', href: that });

    const that2 =
      'https://vneconomy.vn/ha-noi-du-an-cai-tao-song-nhue-duoc-de-xuat-chia-thanh-hai-giai-doan.htm';
    expect(docNguonBaiViet(that2)).toEqual({ nhan: 'vneconomy.vn', href: that2 });
  });

  it('bỏ tiền tố www. khỏi nhãn, giữ nguyên href', () => {
    expect(docNguonBaiViet('http://www.baochinhphu.vn/tin.htm')).toEqual({
      nhan: 'baochinhphu.vn',
      href: 'http://www.baochinhphu.vn/tin.htm',
    });
  });

  it('không phải URL → hiện nguyên văn, KHÔNG thành liên kết', () => {
    expect(docNguonBaiViet('Báo Hà Nội Mới')).toEqual({ nhan: 'Báo Hà Nội Mới', href: null });
    expect(docNguonBaiViet('  Tổng cục Thủy lợi  ')).toEqual({
      nhan: 'Tổng cục Thủy lợi',
      href: null,
    });
  });

  describe('⛔ giao thức ngoài danh sách cho phép KHÔNG BAO GIỜ thành href', () => {
    // Ô này là chữ tự do 255 ký tự do người dùng quản trị nhập. Danh sách CHO PHÉP, không phải
    // danh sách cấm — §10.52 đã trả giá cho đúng bài học ấy ở tầng converter.
    const doc = [
      'javascript:alert(1)',
      'JavaScript:alert(1)',
      'data:text/html,<script>alert(1)</script>',
      'file:///etc/passwd',
      'vbscript:msgbox(1)',
    ];

    for (const gt of doc) {
      it(`"${gt}" → href null`, () => {
        const kq = docNguonBaiViet(gt);
        expect(kq).not.toBeNull();
        expect(kq?.href).toBeNull();
        expect(kq?.nhan).toBe(gt.trim());
      });
    }

    it('kiểm chứng ngược: một URL https HỢP LỆ vẫn phải ra href — bài trên không "luôn null"', () => {
      // Nếu vị từ chỉ luôn trả null thì năm bài kiểm trên xanh mà chẳng canh gì.
      expect(docNguonBaiViet('https://hanoimoi.vn/a.html')?.href).toBe(
        'https://hanoimoi.vn/a.html',
      );
    });
  });
});
