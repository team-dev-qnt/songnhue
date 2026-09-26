import { SVGRenderer } from 'echarts/renderers';
import { describe, expect, it } from 'vitest';

import { optionBieuDo } from '@/components/charts/BieuDoDienBien';
import { echarts, THEME } from '@/components/charts/setup';
import type { BieuDoCongTrinh, OLuoi } from '@/lib/api';

/**
 * **Bố cục biểu đồ diễn biến: chữ ⛔ được chồng chữ, thời gian chạy CŨ → MỚI — T87.7 · T87.9.**
 *
 * ## ⛔⛔ Hai khuyết tật QuanTran báo 26/09/2026, kèm ảnh chụp
 *
 * 1. **Tên trục đè lên chú giải.** `Mực nước (m)` nằm chồng lên hàng chú giải bên trái,
 *    `Chênh lệch` chồng lên đầu phải. Nguyên nhân đo được: `yAxis[].name` ⛔ khai
 *    `nameLocation`/`nameGap`, mà mặc định của ECharts cho trục giá trị là `'end'` + `15` ⇒ tên vẽ
 *    NGANG, phía trên lưới, ở `y ≈ grid.top − 15` = **đúng dải chú giải** (`legend.top: 4`).
 *    ⚠ `grid.containLabel` ⛔ cứu được: nó tính `axisLabel`, **⛔ bao giờ** tính `axisName`.
 * 2. **Trục thời gian chạy PHẢI → TRÁI** (`10:30 · 09:30 · 08:30 …`), nên một đường đang **lên**
 *    trông như đang **xuống** — trên đúng biểu đồ người ta đọc để biết nước đang lên hay xuống.
 *
 * ⭐ Ảnh PNG tải về dùng **đúng** option này (`getDataURL` trên thực thể đang hiển thị), nên vá một
 * chỗ là hết ở **cả hai** nơi người dùng thấy — ⛔ có đường kết xuất riêng để quên.
 *
 * ## ⛔ Vì sao đo trên HÌNH
 *
 * Khẳng định `option.yAxis[0].nameGap === 44` chỉ nói *"tôi vừa viết ra thứ tôi vừa viết"*. Câu
 * thật sự phải trả lời là *"hai khối chữ có chạm nhau ⛔"*, và nó phụ thuộc `grid.top`,
 * `legend.top`, `nameLocation`, `nameRotate` và cỡ chữ của theme **cùng lúc**. Chỉ hình mới biết.
 *
 * ## ⚠ `SVGRenderer` chỉ đăng ký cho tiến trình kiểm
 *
 * Cùng lý do đã ghi ở `bieuDoNguongRaHinh.test.ts`: bản chạy thật chỉ nạp `CanvasRenderer` (NFR-02).
 * Các **component** vẫn do `setup.ts` đăng ký, nên một component bị quên vẫn đỏ ở đây.
 */
echarts.use([SVGRenderer]);

const o = (giaTri: string | null): OLuoi => ({
  giaTri,
  chatLuong: giaTri ? 'HOP_LE' : null,
  lyDo: giaTri === null ? 'Không có dữ liệu tại mốc này' : null,
  khoaMauCanhBao: null,
  tenMucCanhBao: null,
});

/**
 * Dữ liệu dựng theo **đúng thứ tự backend gửi**: `CheDoXemLuoi.dungLuoi` trả
 * **mới-nhất-trước** (`api.ts` ghi rõ trong hợp đồng), và đó chính là thứ sinh ra khuyết tật 2.
 */
function bieuDoMoiTruoc(): BieuDoCongTrinh {
  const moc = [
    '2026-09-25T03:00:00Z',
    '2026-09-25T02:00:00Z',
    '2026-09-25T01:00:00Z',
    '2026-09-25T00:00:00Z',
  ];
  return {
    meta: {
      lanLayCuoi: '2026-09-25T03:00:00Z',
      mocDoGanNhat: '2026-09-25T03:00:00Z',
      trangThaiNguon: 'OK',
      donVi: 'm',
      lyDoLuongMua: null,
    },
    moc,
    congTrinh: {
      maCongTrinh: 'CT-01',
      tenCongTrinh: 'Cống Vân Đình',
      lyTrinh: null,
      trucChinh: true,
      dong: [
        {
          chiTieu: 'Thượng lưu',
          loai: 'DO',
          // Mới-nhất-trước ⇒ 4.20 là mốc 03:00, 2.10 là mốc 00:00. Một chuỗi TĂNG theo thời gian.
          o: [o('4.20'), o('3.50'), o('2.90'), o('2.10')],
          trangThai: 'HOAT_DONG',
          mocGanNhat: '2026-09-25T03:00:00Z',
        },
        {
          chiTieu: 'Hạ lưu',
          loai: 'DO',
          o: [o('3.90'), o('3.20'), o('2.60'), o('1.90')],
          trangThai: 'HOAT_DONG',
          mocGanNhat: '2026-09-25T03:00:00Z',
        },
      ],
    },
    nguong: [],
    lyDoTrong: null,
  };
}

interface KhoiChu {
  chu: string;
  x: number;
  y: number;
}

/**
 * Mọi khối chữ trong SVG kèm toạ độ.
 *
 * ⚠⚠ ECharts đặt chữ bằng **HAI** dạng transform khác nhau, và đọc thiếu một dạng là bỏ sót đúng
 * những khối cần so (bản đầu của hàm này bỏ sót, nên bài đỏ ở vế *chống tập rỗng* — luật 7):
 *
 * <ul>
 *   <li>chữ ngang ⇒ `transform="translate(83.8 292)"`;
 *   <li>chữ **XOAY** — tức đúng hai tên trục sau bản vá — ⇒ `transform="matrix(0,-1,1,0,47.8,185)"`,
 *       trong đó hai số cuối là phần tịnh tiến. Chúng ⛔ có thuộc tính `x` nào.
 * </ul>
 */
function khoiChu(svg: string): KhoiChu[] {
  const ra: KhoiChu[] = [];
  // ⚠ `matchAll` trả mảng mà phần tử 0 là TOÀN BỘ khớp — phải bỏ qua nó khi bóc nhóm.
  for (const [, thuocTinh, chu] of svg.matchAll(/<text\b([^>]*)>([^<]*)<\/text>/g)) {
    if (!chu?.trim()) continue;
    const mt = /transform="matrix\(([^)]*)\)"/.exec(thuocTinh ?? '');
    const so = mt?.[1]?.split(/[ ,]+/).map(Number);
    const tr = /transform="translate\((-?[\d.]+)[ ,]+(-?[\d.]+)\)"/.exec(thuocTinh ?? '');
    const x = Number(so?.[4] ?? tr?.[1] ?? /\bx="(-?[\d.]+)"/.exec(thuocTinh ?? '')?.[1] ?? NaN);
    const y = Number(so?.[5] ?? tr?.[2] ?? /\by="(-?[\d.]+)"/.exec(thuocTinh ?? '')?.[1] ?? NaN);
    if (Number.isFinite(x) && Number.isFinite(y)) ra.push({ chu: chu.trim(), x, y });
  }
  return ra;
}

function veRaSvg(bd: BieuDoCongTrinh): string {
  const doThi = echarts.init(null, THEME, { renderer: 'svg', ssr: true, width: 800, height: 360 });
  try {
    doThi.setOption({ ...optionBieuDo(bd), animation: false });
    return doThi.renderToSVGString();
  } finally {
    doThi.dispose();
  }
}

describe('Bố cục biểu đồ diễn biến (T87.7 · T87.9)', () => {
  /**
   * ⭐⭐ Vế chịu lực của khuyết tật 1 — và **ngưỡng của nó là một phép ĐO, ⛔ một con số cho đẹp**.
   *
   * <h3>⛔⛔ Bản đầu của bài này XANH TRÊN CẢ MÃ CHƯA VÁ</h3>
   *
   * Nó hỏi `|y(tên trục) − y(chú giải)| > 12`. Đo toạ độ thật ở **trạng thái cũ** (800×360):
   * tên trục `y = 31`, chú giải `y = 9.5` ⇒ cách nhau **21,5** ⇒ **lọt**. Tức nó ⛔ phân biệt được
   * hai trạng thái nó sinh ra để phân biệt (luật 9), và nếu giữ nguyên thì nó là một dòng mã đã
   * **mục** trông như một bộ canh đang sống.
   *
   * <h3>⭐ Bất biến ĐÚNG, và nó nói thẳng điều bản vá làm</h3>
   *
   * Tên trục phải **ra hẳn khỏi dải phía trên lưới** — nơi chú giải ở. Đo được:
   * cũ `y = 31` (⛔ nằm trong dải, `grid.top` cũ = 40) · mới `y = 185` (xoay dọc, giữa lưới).
   * ⇒ Hỏi *"tên trục có nằm DƯỚI `grid.top` ⛔"*. Cũ đỏ, mới xanh, và ngưỡng ⛔ phải đoán.
   *
   * ⚠ `grid.containLabel` ⛔ cứu được lớp lỗi này: nó tính `axisLabel`, **⛔ bao giờ** tính
   * `axisName` — nên đừng đi tìm ở đó.
   */
  it('⛔⛔ Tên trục nằm DƯỚI mép trên của lưới — ⛔ lọt vào dải chú giải ("chữ chồng chữ" trong ảnh)', () => {
    const opt = optionBieuDo(bieuDoMoiTruoc()) as { grid: { top: number } };
    const mepTrenLuoi = opt.grid.top;
    const khoi = khoiChu(veRaSvg(bieuDoMoiTruoc()));

    const tenTruc = khoi.filter((k) => k.chu === 'Mực nước (m)' || k.chu === 'Chênh lệch');
    const chuGiai = khoi.filter((k) => k.chu === 'Thượng lưu' || k.chu === 'Hạ lưu');

    expect(tenTruc.length, '⛔ Tập rỗng ⇒ khẳng định dưới xanh vì ⛔ có gì để so (luật 7)').toBe(2);
    expect(chuGiai.length, '⛔ Tập rỗng ⇒ khẳng định dưới ⛔ nói gì').toBeGreaterThanOrEqual(2);

    for (const c of chuGiai) {
      expect(c.y, 'chú giải phải ở dải TRÊN lưới — nếu ⛔, bài này đang đo nhầm thứ').toBeLessThan(
        mepTrenLuoi,
      );
    }
    for (const t of tenTruc) {
      expect(
        t.y,
        `⛔⛔ "${t.chu}" vẽ ở y=${t.y}, tức NẰM TRONG dải phía trên lưới (grid.top=${mepTrenLuoi}) — ` +
          'đúng chỗ chú giải đang ở, và đúng thứ chồng chữ trong ảnh. Mặc định của ECharts cho trục ' +
          "giá trị là nameLocation:'end' + nameGap:15 ⇒ tên vẽ NGANG, ngay trên lưới.",
      ).toBeGreaterThan(mepTrenLuoi);
    }
  });

  /**
   * ⭐⭐ Vế chịu lực của khuyết tật 2 — và nó phải đo **chiều**, ⛔ phải sự có mặt của nhãn.
   *
   * Một khẳng định kiểu *"trục có 4 nhãn"* xanh ở **cả hai** chiều (luật 9). Thứ phân biệt được là
   * **nhãn trái phải CŨ HƠN nhãn phải**.
   */
  it('⛔⛔ Thời gian chạy CŨ → MỚI: nhãn trái cùng là mốc sớm nhất, nhãn phải cùng là muộn nhất', () => {
    const khoi = khoiChu(veRaSvg(bieuDoMoiTruoc()));
    const gio = khoi.filter((k) => /^\d{2}:\d{2}$/.test(k.chu)).sort((a, b) => a.x - b.x);

    expect(gio.length, '⛔ Tập rỗng ⇒ ⛔ có gì để so').toBeGreaterThanOrEqual(2);
    // 00:00Z = 07:00 giờ VN · 03:00Z = 10:00 giờ VN.
    expect(
      gio[0]!.chu,
      '⛔⛔ Mốc SỚM NHẤT phải nằm bên TRÁI. Backend trả mới-nhất-trước (đúng cho BẢNG, §6.1.2) và ' +
        'biểu đồ dùng lại nguyên mảng ấy ⇒ thời gian chạy phải → trái, đường đang LÊN trông như ' +
        'đang XUỐNG — trên đúng biểu đồ người ta đọc để quyết định vận hành cống.',
    ).toBe('07:00');
    expect(gio[gio.length - 1]!.chu, 'mốc MUỘN NHẤT phải nằm bên PHẢI').toBe('10:00');
  });

  /**
   * ⚠ Sắp theo **mốc**, ⛔ `reverse()` mù — nên dữ liệu đã đúng chiều phải đi qua ⛔ suy suyển.
   *
   * Thiếu vế này thì một bản vá dùng `reverse()` vẫn xanh ở hai bài trên, và nó sẽ **đảo ngược lần
   * thứ hai** vào ngày backend đổi thứ tự.
   */
  it('⛔ Dữ liệu ĐÃ đúng chiều (cũ → mới) đi qua ⛔ bị đảo lần nữa', () => {
    const goc = bieuDoMoiTruoc();
    const daDungChieu: BieuDoCongTrinh = {
      ...goc,
      moc: [...goc.moc].reverse(),
      congTrinh: {
        ...goc.congTrinh!,
        dong: goc.congTrinh!.dong.map((d) => ({ ...d, o: [...d.o].reverse() })),
      },
    };

    const gio = khoiChu(veRaSvg(daDungChieu))
      .filter((k) => /^\d{2}:\d{2}$/.test(k.chu))
      .sort((a, b) => a.x - b.x);

    expect(
      gio[0]!.chu,
      '⛔⛔ Một bản vá `reverse()` mù sẽ đảo ngược lần THỨ HAI ở đây và bài này đỏ — đó là điều nó ' +
        'sinh ra để bắt. Thứ tự đúng phải suy từ CHÍNH MỐC, ⛔ từ một hợp đồng phải nhớ (luật 14).',
    ).toBe('07:00');
    expect(gio[gio.length - 1]!.chu).toBe('10:00');
  });

  /**
   * ⚠ Nhãn phải phân biệt được hai ngày.
   *
   * Cửa sổ mặc định 24 giờ ⇒ `HH:mm` in **hai lần** mỗi mốc đồng hồ và ⛔ gì nói cái nào là hôm
   * nay. Một biểu đồ trực ban ⛔ được để người đọc đoán.
   */
  it('⛔ Hai mốc CÙNG GIỜ khác NGÀY phải ra hai nhãn KHÁC NHAU', () => {
    const goc = bieuDoMoiTruoc();
    // ⚠⚠ Hai mốc cách nhau đúng 24 giờ ⇒ **cùng giờ đồng hồ VN (10:00)**, khác ngày. Đây chính là
    //    ca mà cửa sổ 24 giờ sinh ra ở mọi lượt tải trang, và là thứ nhãn `HH:mm` ⛔ phân biệt được.
    // ⚠ Mốc phải vắt qua hai ngày **theo GIỜ VN**, ⛔ theo UTC — bản đầu của bài này sai đúng chỗ
    //   ấy (`24T18:00Z … 25T03:00Z` cộng 7 giờ thì cả bốn rơi vào 25/09), và thứ sai khi đó là
    //   FIXTURE chứ ⛔ phải bản vá. Cùng lớp lỗi `format.ts` đã trả giá ở T63.18.
    const haiNgay: BieuDoCongTrinh = {
      ...goc,
      moc: [
        '2026-09-25T03:00:00Z', // 10:00 VN ngày 25/09
        '2026-09-25T00:00:00Z',
        '2026-09-24T06:00:00Z',
        '2026-09-24T03:00:00Z', // 10:00 VN ngày 24/09 — CÙNG GIỜ, khác ngày
      ],
    };

    const nhan = khoiChu(veRaSvg(haiNgay))
      .filter((k) => /\d{2}:\d{2}/.test(k.chu))
      .map((k) => k.chu);

    expect(nhan.length, '⛔ Tập rỗng ⇒ ⛔ có gì để so (luật 7)').toBeGreaterThanOrEqual(2);
    expect(
      new Set(nhan).size,
      `⛔⛔ Hai mốc cách nhau đúng 24 giờ ra CÙNG một nhãn (${nhan.join(' · ')}) — người đọc ⛔ biết ` +
        '"10:00" là sáng nay hay sáng qua. ⚠ Khẳng định này cố ý ⛔ ghim ĐỊNH DẠNG: bản đầu đòi ' +
        '`dd/MM` và đỏ vì `Intl` của vi-VN in ra `10:00 25-09` — dấu GẠCH NGANG. Thứ đáng canh là ' +
        'hai nhãn PHÂN BIỆT ĐƯỢC, ⛔ phải cách viết ngày.',
    ).toBe(nhan.length);
  });
});
