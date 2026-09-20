import { describe, expect, it } from 'vitest';

import { DEM_CHO_MS, DUONG_XIN_VE, laLoiVe, MA_LOI_VE, taoNguoiGiuVe, xinVe } from './veBieuMau';

/** T73.9 — logic vé biểu mẫu: xin, chờ đủ tuổi, bỏ khi máy chủ từ chối. */

const json = (than: unknown, status = 200) =>
  new Response(JSON.stringify(than), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });

const veHopLe = (ve: string, giay = 3) =>
  json({ success: true, data: { ve, tuoiToiThieuGiay: giay } });

/** Máy chủ giả: trả lần lượt từng phản hồi, ghi lại mọi lượt gọi. */
function mayChuGia(...phanHoi: Array<Response | Error>) {
  const luot: Array<{ duong: string; init?: RequestInit }> = [];
  const goi = async (duong: string, init?: RequestInit) => {
    luot.push({ duong, init });
    const tiep = phanHoi.shift();
    if (tiep === undefined) throw new Error('máy chủ giả hết phản hồi');
    if (tiep instanceof Error) throw tiep;
    return tiep;
  };
  return { goi, luot };
}

/** Đồng hồ giả: `cho(ms)` ghi lại số ms rồi TIẾN đồng hồ đúng chừng ấy. */
function dongHoGia(batDau = 1000) {
  let bayGio = batDau;
  const daCho: number[] = [];
  return {
    dongHo: () => bayGio,
    cho: async (ms: number) => {
      daCho.push(ms);
      bayGio += ms;
    },
    troiQua: (ms: number) => {
      bayGio += ms;
    },
    daCho,
  };
}

/**
 * Chờ lượt xin vé đang dở xử lý xong (mọi microtask chạy trước một macrotask).
 *
 * ⚠ Nếu chưa đủ thì lượt `lay()` sau đó tính mốc đủ-tuổi bằng đồng hồ ĐÃ tiến ⇒ nó chờ ĐỦ
 * 3,3 giây ⇒ bài ĐỎ — thiếu nhịp ở đây cho ra đỏ, ⛔ bao giờ cho ra xanh giả.
 */
async function xaHangDoi() {
  for (let i = 0; i < 3; i++) await new Promise((xong) => setTimeout(xong, 0));
}

describe('xinVe — T73.9', () => {
  it('xin đúng đường, ⛔ đệm, và đọc cả vé lẫn số giây phải chờ', async () => {
    const may = mayChuGia(veHopLe('v1.100.k:ab', 3));
    expect(await xinVe(may.goi)).toEqual({ ve: 'v1.100.k:ab', tuoiToiThieuMs: 3000 });
    expect(may.luot).toHaveLength(1);
    expect(may.luot[0].duong).toBe(DUONG_XIN_VE);
    expect(may.luot[0].init?.cache).toBe('no-store');
  });

  it('⛔ hỏng thì trả null chứ ⛔ ném — mọi hình dạng hỏng', async () => {
    const ca: Array<[string, Response | Error]> = [
      ['500', json({ success: false }, 500)],
      ['429', json({ success: false }, 429)],
      ['mất mạng', new Error('mất mạng')],
      ['thân ⛔ phải JSON', new Response('<html>', { status: 200 })],
      ['thiếu vé', json({ success: true, data: { tuoiToiThieuGiay: 3 } })],
      ['vé rỗng', veHopLe('')],
      [
        '⛔ thiếu số giây — đoán một con số là chờ sai',
        json({ success: true, data: { ve: 'v1.1.k:a' } }),
      ],
      [
        'số giây là CHUỖI',
        json({ success: true, data: { ve: 'v1.1.k:a', tuoiToiThieuGiay: '3' } }),
      ],
      ['số giây ÂM', veHopLe('v1.1.k:a', -1)],
    ];
    for (const [ten, phanHoi] of ca) {
      expect(await xinVe(mayChuGia(phanHoi).goi), ten).toBeNull();
    }
  });
});

describe('laLoiVe — đọc MÃ trong envelope, ⛔ đoán theo mã HTTP', () => {
  it('chỉ 422 + CMS-2025 là lỗi vé', async () => {
    expect(await laLoiVe(json({ success: false, error: { code: MA_LOI_VE } }, 422))).toBe(true);
    expect(
      await laLoiVe(json({ success: false, error: { code: 'CMS-2019' } }, 422)),
      '422 của một luật nghiệp vụ KHÁC ⛔ phải lỗi vé',
    ).toBe(false);
    expect(
      await laLoiVe(json({ success: false, error: { code: 'SYS-0003' } }, 400)),
      'lỗi nhập liệu',
    ).toBe(false);
    expect(await laLoiVe(new Response('rác', { status: 422 })), 'thân hỏng').toBe(false);
  });
});

describe('taoNguoiGiuVe — xin một lần, chờ đủ tuổi, bỏ khi bị từ chối', () => {
  it('batDau gọi lặp (mỗi lần focus một ô) chỉ xin ĐÚNG MỘT vé', async () => {
    const may = mayChuGia(veHopLe('v1.1.k:a'));
    const dh = dongHoGia();
    const giu = taoNguoiGiuVe({ goi: may.goi, cho: dh.cho, dongHo: dh.dongHo });
    giu.batDau();
    giu.batDau();
    dh.troiQua(10_000);
    expect(await giu.lay()).toBe('v1.1.k:a');
    giu.batDau();
    expect(may.luot).toHaveLength(1);
  });

  it('⛔⛔ bấm Gửi NGAY sau khi nhận vé ⇒ CHỜ đủ tuổi + đệm rồi mới trả vé', async () => {
    const dh = dongHoGia();
    const giu = taoNguoiGiuVe({
      goi: mayChuGia(veHopLe('v1.1.k:a', 3)).goi,
      cho: dh.cho,
      dongHo: dh.dongHo,
    });
    expect(await giu.lay()).toBe('v1.1.k:a');
    expect(dh.daCho).toEqual([3000 + DEM_CHO_MS]);
  });

  it('người đã điền đủ lâu ⇒ ⛔ chờ thêm', async () => {
    const dh = dongHoGia();
    const giu = taoNguoiGiuVe({
      goi: mayChuGia(veHopLe('v1.1.k:a', 3)).goi,
      cho: dh.cho,
      dongHo: dh.dongHo,
    });
    giu.batDau();
    await xaHangDoi();
    dh.troiQua(20_000);
    expect(await giu.lay()).toBe('v1.1.k:a');
    expect(dh.daCho).toEqual([]);
  });

  it('chờ đúng PHẦN CÒN THIẾU — ⛔ chờ lại từ đầu', async () => {
    const dh = dongHoGia();
    const giu = taoNguoiGiuVe({
      goi: mayChuGia(veHopLe('v1.1.k:a', 3)).goi,
      cho: dh.cho,
      dongHo: dh.dongHo,
    });
    giu.batDau();
    await xaHangDoi();
    dh.troiQua(1000);
    await giu.lay();
    expect(dh.daCho).toEqual([2000 + DEM_CHO_MS]);
  });

  it('xin hỏng ⇒ lay() trả null; lượt sau XIN LẠI (⛔ kẹt ở null)', async () => {
    const may = mayChuGia(new Error('mất mạng'), veHopLe('v1.2.k:b'));
    const dh = dongHoGia();
    const giu = taoNguoiGiuVe({ goi: may.goi, cho: dh.cho, dongHo: dh.dongHo });
    expect(await giu.lay()).toBeNull();
    expect(await giu.lay()).toBe('v1.2.k:b');
    expect(may.luot).toHaveLength(2);
  });

  it('⛔ bo() sau khi máy chủ từ chối ⇒ lượt sau mang vé MỚI, ⛔ vé cũ', async () => {
    const may = mayChuGia(veHopLe('v1.1.k:cu', 0), veHopLe('v1.2.k:moi', 0));
    const dh = dongHoGia();
    const giu = taoNguoiGiuVe({ goi: may.goi, cho: dh.cho, dongHo: dh.dongHo });
    expect(await giu.lay()).toBe('v1.1.k:cu');
    giu.bo();
    expect(await giu.lay()).toBe('v1.2.k:moi');
    expect(may.luot).toHaveLength(2);
  });
});
