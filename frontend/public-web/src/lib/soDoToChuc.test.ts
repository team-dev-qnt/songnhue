import { describe, expect, it } from 'vitest';

import type { LeaderRow, OrgChartNode } from '@/lib/api';
import {
  NHAN_LOAI,
  beRongToiDa,
  doSau,
  tenGoc,
  tuBanLanhDao,
  tuCoCauToChuc,
} from '@/lib/soDoToChuc';

/**
 * Hình dạng **thật** trên staging, đo 10/09/2026: một Công ty, chín Xí nghiệp, sâu hai tầng,
 * ⛔ chưa có phòng ban nào.
 *
 * <p>⚠ Dùng đúng dữ liệu thật thay vì một cây tự nghĩ: luật 25 — *một bộ canh theo hình dạng phải
 * được thử với dữ liệu THẬT đang dùng*. Chín anh em trên một hàng là con số quyết định trang có
 * cần khung cuộn ngang hay ⛔ không; một fixture ba nút sẽ ⛔ không lộ ra điều đó.
 */
const CHART: OrgChartNode[] = [
  {
    code: 'CTY',
    name: 'Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ',
    shortName: 'Thủy lợi Sông Nhuệ',
    unitType: 'CONG_TY',
    children: Array.from({ length: 9 }, (_, i) => ({
      code: `XN0${i + 1}`,
      name: `Xí nghiệp Thủy lợi số ${i + 1}`,
      shortName: null,
      unitType: 'XI_NGHIEP',
      children: [],
    })),
  },
];

const LANH_DAO: LeaderRow[] = [
  { fullName: 'VŨ MẠNH HÙNG', title: 'Chủ tịch công ty' },
  { fullName: 'NGUYỄN HUY HƯNG', title: 'Tổng Giám đốc công ty' },
  { fullName: 'NGÔ THANH SƠN', title: 'Phó Tổng Giám đốc công ty' },
];

describe('tuCoCauToChuc — cây đơn vị, lãnh đạo gắn vào hộp gốc', () => {
  it('giữ nguyên hình dạng cây: 1 gốc, 9 con, sâu 2 tầng', () => {
    const cay = tuCoCauToChuc(CHART, LANH_DAO);

    expect(cay).toHaveLength(1);
    expect(cay[0].con).toHaveLength(9);
    expect(doSau(cay)).toBe(2);
    expect(beRongToiDa(cay)).toBe(9);
  });

  it('⛔ lãnh đạo CHỈ ở hộp gốc — nút con phải RỖNG', () => {
    const cay = tuCoCauToChuc(CHART, LANH_DAO);

    expect(cay[0].phu.map((d) => d.ten)).toEqual([
      'VŨ MẠNH HÙNG',
      'NGUYỄN HUY HƯNG',
      'NGÔ THANH SƠN',
    ]);
    // Đối chứng: `LeaderRow` ⛔ không mang mã đơn vị, nên rải danh sách này xuống Xí nghiệp là bịa
    // ra một khẳng định dữ liệu ⛔ không nói. Bài này đỏ đúng ngày ai đó "tiện tay" làm việc ấy.
    expect(cay[0].con.every((c) => c.phu.length === 0)).toBe(true);
  });

  it('⛔ nhiều nút gốc thì chỉ nút ĐẦU TIÊN nhận lãnh đạo — khớp `findFirstByParentIdIsNull`', () => {
    const haiGoc: OrgChartNode[] = [
      CHART[0],
      { code: 'KHAC', name: 'Đơn vị khác', shortName: null, unitType: 'KHAC', children: [] },
    ];
    const cay = tuCoCauToChuc(haiGoc, LANH_DAO);

    expect(cay[0].phu).toHaveLength(3);
    expect(cay[1].phu).toHaveLength(0);
  });

  it('loại đơn vị lạ thì hiện nguyên mã, ⛔ không nuốt mất', () => {
    const la: OrgChartNode[] = [
      { code: 'X', name: 'X', shortName: null, unitType: 'LOAI_MOI', children: [] },
    ];
    expect(tuCoCauToChuc(la, [])[0].nhan).toBe('LOAI_MOI');
    expect(NHAN_LOAI.XI_NGHIEP).toBe('Xí nghiệp');
  });
});

describe('tuBanLanhDao — cây lãnh đạo, và thứ nó cố ý KHÔNG khẳng định', () => {
  it('dựng gốc Công ty + mỗi lãnh đạo một hộp, giữ nguyên thứ tự sort_order', () => {
    const cay = tuBanLanhDao('Công ty Thủy lợi Sông Nhuệ', LANH_DAO);

    expect(cay).toHaveLength(1);
    expect(cay[0].ten).toBe('Công ty Thủy lợi Sông Nhuệ');
    expect(cay[0].con.map((c) => c.ten)).toEqual([
      'VŨ MẠNH HÙNG',
      'NGUYỄN HUY HƯNG',
      'NGÔ THANH SƠN',
    ]);
    expect(cay[0].con.map((c) => c.nhan)).toEqual([
      'Chủ tịch công ty',
      'Tổng Giám đốc công ty',
      'Phó Tổng Giám đốc công ty',
    ]);
  });

  it('⛔⛔ Ban lãnh đạo phải PHẲNG — cấm suy ra tầng từ chuỗi chức danh', () => {
    const cay = tuBanLanhDao('Công ty', LANH_DAO);

    // `org_unit_leaders` ⛔ không có cột cha–con, và `title` là ô văn bản tự do. Suy ra
    // "Chủ tịch > Tổng Giám đốc > Phó Tổng Giám đốc" bằng cách so chuỗi là dựng một sơ đồ
    // TRÔNG như dữ liệu trong khi nó là phỏng đoán — và nó sai lặng lẽ ngay lần Công ty đổi
    // cách viết chức danh. Bài này là bánh cóc giữ quyết định ấy.
    expect(doSau(cay)).toBe(2);
    expect(cay[0].con.every((c) => c.con.length === 0)).toBe(true);
  });

  it('⛔ thiếu gốc hoặc thiếu người thì trả RỖNG — hai trạng thái, ⛔ không hộp trống', () => {
    expect(tuBanLanhDao(null, LANH_DAO)).toEqual([]);
    expect(tuBanLanhDao('Công ty', [])).toEqual([]);
    // Đối chứng phải-CÓ: đủ hai vế thì phải dựng được, ⛔ không phải hàm luôn trả rỗng (luật 7).
    expect(tuBanLanhDao('Công ty', LANH_DAO)).toHaveLength(1);
  });
});

describe('phép đo hình dạng', () => {
  it('cây rỗng ⇒ sâu 0, rộng 0 — và tenGoc trả null', () => {
    expect(doSau([])).toBe(0);
    expect(beRongToiDa([])).toBe(0);
    expect(tenGoc([])).toBeNull();
  });

  it('beRongToiDa lấy hàng RỘNG NHẤT, ⛔ không phải hàng cuối', () => {
    const cay = tuCoCauToChuc(
      [
        {
          code: 'A',
          name: 'A',
          shortName: null,
          unitType: 'CONG_TY',
          children: [
            {
              code: 'B',
              name: 'B',
              shortName: null,
              unitType: 'PHONG_BAN',
              children: [
                { code: 'C', name: 'C', shortName: null, unitType: 'TO_DOI', children: [] },
              ],
            },
            { code: 'D', name: 'D', shortName: null, unitType: 'PHONG_BAN', children: [] },
            { code: 'E', name: 'E', shortName: null, unitType: 'PHONG_BAN', children: [] },
          ],
        },
      ],
      [],
    );
    // Hàng: 1 → 3 → 1. Rộng nhất là 3, và hàng CUỐI chỉ có 1 — hai số khác nhau thì phép đo mới
    // phân biệt được hai trạng thái (luật 9).
    expect(beRongToiDa(cay)).toBe(3);
    expect(doSau(cay)).toBe(3);
    expect(tenGoc(CHART)).toContain('Sông Nhuệ');
  });
});
