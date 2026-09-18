import { describe, expect, it } from 'vitest';

import { type BcnDongBang4View, type BcnDongBang5View, type BcnKhoiView } from '@/shared/api-types';

import {
  locBang2,
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
        ten: 'Đại Áng (tiêu)',
        nguonTuoiHuongTieu: null,
        nhom: [nhom('a1', 0), nhom('a2', 2)],
      },
      {
        constructionPublicId: 'c2',
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
