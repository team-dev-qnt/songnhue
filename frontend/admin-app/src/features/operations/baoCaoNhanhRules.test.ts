import { describe, expect, it } from 'vitest';

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  type BcnDongBang4View,
  type BcnDongBang5View,
  type BcnKhoiView,
  type BcnYenNghiaView,
} from '@/shared/api-types';
import { boChuThich } from '@/testsupport/boChuThich';

import {
  locBang2,
  moTaChoGhiChu,
  payloadLuongMua,
  payloadNgapUng,
  payloadVanHanh,
  tramDangHoatDong,
} from './baoCaoNhanhRules';

const nhom = (id: string, soMayVanHanh: number | null) => ({
  nhomMayPublicId: id,
  soMayThietKe: 10,
  qMotMayM3h: 1000,
  coMay: '1',
  soMayVanHanh,
});

const BANG2: BcnKhoiView[] = [
  {
    tenDonVi: 'XNTL Thanh Trì',
    tongMayThietKe: 30,
    tram: [
      {
        constructionPublicId: 'c1',
        ma: 'TB-DANG',
        ten: 'Đại Áng (tiêu)',
        nguonTuoiHuongTieu: null,
        nhom: [nhom('a1', 0), nhom('a2', 2)],
      },
      {
        constructionPublicId: 'c2',
        ma: 'TB-SQUAN',
        ten: 'Siêu Quần',
        nguonTuoiHuongTieu: null,
        nhom: [nhom('b1', null)],
      },
    ],
  },
];

describe('Báo cáo nhanh — luật màn hình', () => {
  it('⭐ trạm hoạt động ⇔ ÍT NHẤT một nhóm > 0; lọc hiện ĐỦ mọi nhóm của trạm ấy', () => {
    const loc = locBang2(BANG2, true, '', {});
    expect(loc[0]?.tram.map((t) => t.ten)).toEqual(['Đại Áng (tiêu)']);
    expect(loc[0]?.tram[0]?.nhom).toHaveLength(2);
  });

  it('bản nháp thắng số đã lưu khi xét "đang hoạt động"', () => {
    expect(tramDangHoatDong(BANG2[0]!.tram[1]!, { b1: 3 })).toBe(true);
    expect(tramDangHoatDong(BANG2[0]!.tram[0]!, { a2: 0 })).toBe(false);
  });

  it('tìm theo tên trạm ⛔ phân biệt dấu', () => {
    expect(locBang2(BANG2, false, 'sieu quan', {})[0]?.tram.map((t) => t.ten)).toEqual([
      'Siêu Quần',
    ]);
  });

  it('⭐ tìm được cả theo MÃ công trình — thứ DUY NHẤT phân biệt hai trạm trùng tên', () => {
    // T78.1: danh mục cho phép hai trạm bơm cùng tên (chỉ `code` là duy nhất). Một ô tìm chỉ soi
    // tên sẽ trả về CẢ HAI đúng lúc người nhập cần tách chúng ra — tức nó im lặng ở ca nó sinh ra
    // để phục vụ.
    expect(locBang2(BANG2, false, 'TB-SQUAN', {})[0]?.tram.map((t) => t.ten)).toEqual([
      'Siêu Quần',
    ]);
    expect(locBang2(BANG2, false, 'tb-dang', {})[0]?.tram.map((t) => t.ma)).toEqual(['TB-DANG']);
  });

  it('⚠ hai trạm TRÙNG TÊN: lọc theo tên ra cả hai, lọc theo mã ra đúng một', () => {
    // Vế chống-xanh-vì-lý-do-sai: nếu bộ lọc mã hỏng (luôn khớp) thì khẳng định trên vẫn xanh.
    const trungTen: BcnKhoiView[] = [
      {
        tenDonVi: 'XNTL Hà Đông',
        tongMayThietKe: 20,
        tram: [
          {
            constructionPublicId: 'y1',
            ma: 'TB-YNGHIA',
            ten: 'Trạm bơm Yên Nghĩa',
            nguonTuoiHuongTieu: null,
            nhom: [nhom('y1a', 5)],
          },
          {
            constructionPublicId: 'y2',
            ma: 'TB-YNGHIA-2',
            ten: 'Trạm bơm Yên Nghĩa',
            nguonTuoiHuongTieu: null,
            nhom: [nhom('y2a', 8)],
          },
        ],
      },
    ];
    expect(locBang2(trungTen, false, 'yen nghia', {})[0]?.tram).toHaveLength(2);
    expect(locBang2(trungTen, false, 'TB-YNGHIA-2', {})[0]?.tram.map((t) => t.ma)).toEqual([
      'TB-YNGHIA-2',
    ]);
  });

  it('⭐ payload Bảng 2 chỉ gồm ô ĐÃ ĐỔI; xoá về null là một thay đổi', () => {
    expect(payloadVanHanh(BANG2, { a1: 0, a2: 5, b1: null })).toEqual({
      o: [{ nhomMayPublicId: 'a2', soMayVanHanh: 5 }],
    });
    expect(payloadVanHanh(BANG2, { a2: null }).o).toEqual([
      { nhomMayPublicId: 'a2', soMayVanHanh: null },
    ]);
  });

  it('⛔⛔ payload Bảng 5 gửi ĐỦ bốn ô của xã đã đổi — thiếu một ô là XOÁ nó (§11.19)', () => {
    const o = {
      ngapTrangLua: null,
      ngapTrangRau: null,
      ngapTrangCong: null,
      sauNuocLua: 115,
      sauNuocRau: 20,
      sauNuocCong: 135,
      tongLua: 115,
      tongRau: 20,
      tongCong: 135,
    };
    const bang5: BcnDongBang5View[] = [{ xaPublicId: 'x1', ten: 'Thượng Phúc', thuTu: 55, o }];
    const p = payloadNgapUng(bang5, {
      x1: { ngapTrangLua: 3, ngapTrangRau: null, sauNuocLua: 115, sauNuocRau: 20 },
    });
    expect(p.dong).toEqual([
      { xaPublicId: 'x1', ngapTrangLua: 3, ngapTrangRau: null, sauNuocLua: 115, sauNuocRau: 20 },
    ]);
    expect(
      payloadNgapUng(bang5, {
        x1: { ngapTrangLua: null, ngapTrangRau: null, sauNuocLua: 115, sauNuocRau: 20 },
      }).dong,
    ).toEqual([]);
  });

  it('Bảng 4: chỉ gửi điểm ĐÃ ĐỔI; xoá trắng gửi `null` (⛔ 0), gõ lại đúng số cũ ⛔ gửi', () => {
    const bang4: BcnDongBang4View[] = [
      { diemMuaPublicId: 'lm', ten: 'Liên Mạc', thuTu: 5, luongMuaMm: 15 },
      { diemMuaPublicId: 'hd', ten: 'Hà Đông', thuTu: 6, luongMuaMm: null },
      { diemMuaPublicId: 'ds', ten: 'Điệp Sơn', thuTu: 12, luongMuaMm: 3 },
    ];
    expect(payloadLuongMua(bang4, {}).o).toEqual([]);
    expect(payloadLuongMua(bang4, { lm: null, hd: 0, ds: 3 }).o).toEqual([
      { diemMuaPublicId: 'lm', luongMuaMm: null },
      { diemMuaPublicId: 'hd', luongMuaMm: 0 },
    ]);
  });
});

describe('Ghi chú — câu chữ đi theo TRẠM ĐÃ GẮN, ⛔ theo một cái tên viết sẵn (T78.1)', () => {
  const yn = (v: Partial<BcnYenNghiaView>): BcnYenNghiaView => ({
    trangThai: 'CHUA_NHAP',
    cau: null,
    soMay: null,
    luuLuongM3s: null,
    tenTram: 'Trạm bơm Hồng Vân',
    maTram: 'TB-HVAN',
    ...v,
  });

  it('có câu thật ⇒ ⛔ chen thêm câu cảnh báo nào', () => {
    expect(
      moTaChoGhiChu(yn({ trangThai: 'VAN_HANH', cau: 'Trạm bơm Hồng Vân vận hành 3 máy bơm.' })),
    ).toBeNull();
  });

  it('⭐ ba trạng thái CHƯA đều gọi TÊN trạm đang gắn', () => {
    expect(moTaChoGhiChu(yn({}))).toContain('Trạm bơm Hồng Vân');
    expect(moTaChoGhiChu(yn({ trangThai: 'CHUA_CO_TRONG_DANH_MUC' }))).toContain(
      'Trạm bơm Hồng Vân',
    );
    // Chưa gắn thì ⛔ có tên nào để gọi — và câu phải chỉ đúng việc cần làm.
    expect(
      moTaChoGhiChu(yn({ trangThai: 'CHUA_GAN_TRAM', tenTram: null, maTram: null })),
    ).toContain('Chưa chọn trạm bơm');
  });

  it('⛔ BỊA tên khi backend ⛔ trả tên', () => {
    expect(moTaChoGhiChu(yn({ tenTram: null }))).toContain('trạm đã chọn');
  });

  it('⛔⛔ màn hình ⛔ còn ghi cứng "Yên Nghĩa" ở bất kỳ đâu', () => {
    // Bộ canh CẤU TRÚC cho đúng khuyết tật T78.1: một cái tên viết sẵn trong JSX sẽ sống sót qua
    // mọi lượt đổi ô chọn. Quét trên mã ĐÃ BỎ CHÚ THÍCH — chú thích *giải thích* khuyết tật phải
    // được phép gọi tên nó (T46.7 · T54.8).
    const doc = (ten: string) =>
      boChuThich(readFileSync(join(dirname(fileURLToPath(import.meta.url)), ten), 'utf8'));
    expect(doc('BaoCaoNhanhChiTietPage.tsx')).not.toContain('Yên Nghĩa');
    expect(doc('baoCaoNhanhRules.ts')).not.toContain('Yên Nghĩa');
    // Vế tự kiểm: `boChuThich` phải GIỮ chuỗi ký tự, ⛔ thì khẳng định trên xanh vì tệp rỗng.
    expect(doc('BaoCaoNhanhChiTietPage.tsx')).toContain('Ghi chú:');
  });
});
