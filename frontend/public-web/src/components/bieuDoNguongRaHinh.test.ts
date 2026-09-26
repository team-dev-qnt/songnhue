import { SVGRenderer } from 'echarts/renderers';
import { describe, expect, it } from 'vitest';

import { optionBieuDo } from '@/components/charts/BieuDoDienBien';
import { echarts, THEME } from '@/components/charts/setup';
import type { BieuDoCongTrinh, DuongNguong, OLuoi } from '@/lib/api';

/**
 * **Đường ngưỡng có RA HÌNH hay ⛔ — T45.11.**
 *
 * ## ⛔⛔ Vì sao bài này cần tồn tại khi đã có hai phép so văn bản
 *
 * `bieuDoDienBien.test.ts` khẳng định `setup` chứa chuỗi `MarkLineComponent` và mã nguồn chứa
 * chuỗi `markLine`. Hai câu ấy chứng minh *hai chuỗi có mặt trong mã nguồn* — ⛔ phải *một đường
 * ngang có ra hình*. Giữa hai điều đó là đúng nhánh mà `setup.ts` tự cảnh báo: quên đăng ký
 * `MarkLineComponent` thì ECharts **⛔ ném, ⛔ cảnh báo**, nó chỉ ⛔ vẽ — và cái mất là thứ nói cho
 * người đọc biết mực nước đã vượt báo động hay chưa.
 *
 * ## ⭐ Tiền đề của dòng nợ gốc đã HẾT ĐÚNG
 *
 * `T45.11` viết 14/09: *"`alert_levels` đang 0 hàng (chờ G9-a) nên ⛔ dựng được trạng thái có
 * ngưỡng mà ⛔ seed CSDL từ trong Playwright"*. Lượt đo 19/09 bác nó: ECharts render **SSR ra SVG**
 * ngay trong vitest, ⛔ cần trình duyệt lẫn CSDL. Thứ chặn thật là option nằm trong thân
 * `useEffect` — nay đã tách thành `optionBieuDo`.
 *
 * ## ⚠ `SVGRenderer` chỉ đăng ký Ở ĐÂY
 *
 * `setup.ts` cố ý chỉ nạp `CanvasRenderer`: cổng công khai đo bằng NFR-02 (*trang chủ < 3 giây*),
 * và javadoc của nó nêu đích danh lý do đăng ký chọn lọc. Thêm `SVGRenderer` vào bản chạy thật là
 * bắt mọi người dùng tải một bộ vẽ chỉ bộ kiểm cần. ⇒ Bài này `use` nó cho riêng tiến trình kiểm;
 * **các component vẫn do `setup.ts` đăng ký**, nên một component bị quên vẫn đỏ ở đây.
 */
echarts.use([SVGRenderer]);

const NGUONG: DuongNguong[] = [
  { chiTieu: 'Thượng lưu', tenMuc: 'Báo động II', giaTri: '3.50', khoaMau: 'alert-level-2' },
];

const o = (giaTri: string | null): OLuoi => ({
  giaTri,
  chatLuong: giaTri ? 'HOP_LE' : null,
  lyDo: giaTri === null ? 'Không có dữ liệu tại mốc này' : null,
  khoaMauCanhBao: null,
  tenMucCanhBao: null,
});

function bieuDo(nguong: DuongNguong[]): BieuDoCongTrinh {
  return {
    meta: {
      lanLayCuoi: '2026-09-25T02:00:00Z',
      mocDoGanNhat: '2026-09-25T02:00:00Z',
      trangThaiNguon: 'OK',
      donVi: 'm',
      lyDoLuongMua: null,
    },
    moc: ['2026-09-25T01:00:00Z', '2026-09-25T02:00:00Z', '2026-09-25T03:00:00Z'],
    congTrinh: {
      maCongTrinh: 'CT-01',
      tenCongTrinh: 'Cống Vân Đình',
      lyTrinh: null,
      trucChinh: true,
      dong: [
        {
          chiTieu: 'Thượng lưu',
          loai: 'DO',
          o: [o('2.10'), o('2.90'), o('4.20')],
          trangThai: 'HOAT_DONG',
          mocGanNhat: '2026-09-25T03:00:00Z',
        },
      ],
    },
    nguong,
    lyDoTrong: null,
  };
}

/** Vẽ ra SVG, đúng bộ component mà `setup.ts` đăng ký cho bản chạy thật. */
function veRaSvg(bd: BieuDoCongTrinh): string {
  const doThi = echarts.init(null, THEME, { renderer: 'svg', ssr: true, width: 800, height: 360 });
  try {
    doThi.setOption({ ...optionBieuDo(bd), animation: false });
    return doThi.renderToSVGString();
  } finally {
    doThi.dispose();
  }
}

/**
 * Các nét NGANG có gạch đứt trong SVG.
 *
 * ⚠ Đo theo **hình dạng**, ⛔ theo một thuộc tính ECharts tự đặt tên: hai đầu nét phải có cùng
 * toạ độ Y (⇒ nằm ngang) và nét phải mang `stroke-dasharray` (⇒ nét đứt, đúng §7.1). Hỏi một
 * `class` hay một `id` do thư viện sinh ra là neo bộ canh vào chi tiết nội bộ của ECharts.
 *
 * ⛔⛔ **Con số tuyệt đối ở đây ⛔ dùng được, và lượt chạy đầu của bài này đã đỏ vì đúng thế**: chủ
 * đề `songnhue` vẽ **6** đường lưới ngang cũng nét đứt, nên *"có đúng 1 nét"* là một khẳng định
 * sai ngay từ đầu. ⇒ Mọi bài dưới đây đo **HIỆU SỐ** so với chính biểu đồ ấy khi ⛔ có ngưỡng —
 * phép đo ấy nói đúng điều cần nói (*thêm một ngưỡng thì thêm một nét*) và ⛔ vỡ vào ngày ai đó
 * đổi số đường lưới của chủ đề.
 */
function netNgangDut(svg: string): { y: number; dai: number }[] {
  const ra: { y: number; dai: number }[] = [];
  for (const the of svg.match(/<path\b[^>]*>/g) ?? []) {
    if (!the.includes('stroke-dasharray')) continue;
    const d = /\sd="([^"]+)"/.exec(the)?.[1];
    const m = d && /^M\s*([\d.-]+)[\s,]+([\d.-]+)\s*L\s*([\d.-]+)[\s,]+([\d.-]+)\s*$/.exec(d);
    if (!m) continue;
    const [x1, y1, x2, y2] = [+m[1], +m[2], +m[3], +m[4]];
    if (Math.abs(y1 - y2) < 0.5 && Math.abs(x2 - x1) > 50)
      ra.push({ y: y1, dai: Math.abs(x2 - x1) });
  }
  return ra;
}

describe('Đường ngưỡng §7.1 — đo trên HÌNH, ⛔ trên chuỗi trong mã nguồn', () => {
  /** Số nét ngang nét đứt của CHÍNH biểu đồ ấy khi ⛔ có ngưỡng nào — đường lưới của chủ đề. */
  const NEN = netNgangDut(veRaSvg(bieuDo([]))).length;

  it('⚠ Vế chống tập rỗng — bộ dựng phải ra một SVG THẬT, ⛔ một chuỗi rỗng', () => {
    // Thiếu vế này thì mọi khẳng định dưới đây xanh khi `renderToSVGString` trả '' (luật 7).
    const svg = veRaSvg(bieuDo(NGUONG));
    expect(svg.startsWith('<svg')).toBe(true);
    expect(svg.length).toBeGreaterThan(2000);
    expect(svg, 'tên trục Y của §7.1 phải ra chữ — ⛔ thì SVG này ⛔ phải biểu đồ').toContain(
      'Mực nước',
    );
    expect(NEN, 'nền phải là một con số ĐO ĐƯỢC, ⛔ phải 0 vì bộ dò hỏng').toBeGreaterThan(0);
  });

  it('⭐⭐ Có ngưỡng ⇒ SVG có ĐÚNG một nét NGANG nét đứt, và mang tên mức', () => {
    const svg = veRaSvg(bieuDo(NGUONG));
    expect(
      netNgangDut(svg).length - NEN,
      'Quên `MarkLineComponent` thì ECharts ⛔ ném, ⛔ cảnh báo — nó chỉ ⛔ vẽ. Đây là phép đo ' +
        'duy nhất phân biệt được "có đăng ký" với "⛔ đăng ký".',
    ).toBe(1);
    expect(svg, 'nhãn `{b}` của §7.1 phải hiện tên mức, ⛔ phải con số trần').toContain(
      'Báo động II',
    );
  });

  it('⭐⭐ Vế PHÂN BIỆT — ⛔ ngưỡng thì ⛔ nét ngang nào, và ⛔ tên mức nào', () => {
    // ⛔ Có vế này thì bài trên xanh cả khi biểu đồ vẽ một nét ngang vì lý do khác (đường lưới,
    //   trục, dải nền) — tức nó ⛔ khẳng định gì về `markLine` (luật 9).
    const svg = veRaSvg(bieuDo([]));
    expect(netNgangDut(svg).length - NEN).toBe(0);
    expect(svg).not.toContain('Báo động II');
  });

  it('⭐ Ngưỡng của chỉ tiêu KHÁC ⛔ lọt sang — `nguongCua` lọc theo `chiTieu`', () => {
    // Một công trình có cả Thượng lưu và Hạ lưu; vẽ ngưỡng hạ lưu lên đường thượng lưu là công bố
    // một mức báo động sai cho đúng chỗ người ta đọc để quyết định vận hành.
    const svg = veRaSvg(
      bieuDo([{ chiTieu: 'Hạ lưu', tenMuc: 'BĐ-HL', giaTri: '1.20', khoaMau: 'alert-level-1' }]),
    );
    expect(netNgangDut(svg).length - NEN).toBe(0);
    expect(svg).not.toContain('BĐ-HL');
  });

  it('⭐ Hai mức ⇒ hai nét ngang ở HAI độ cao khác nhau', () => {
    // Khẳng định về SỐ LƯỢNG và về VỊ TRÍ — nó ⛔ chia sẻ giả định nào với phép so tên mức ở trên
    // (luật 29), và nó bắt được một bản vá vẽ đè hai ngưỡng lên cùng một chỗ.
    //
    // ⚠⚠ Hai giá trị `3.33`/`3.77` là **chọn có tính toán**, ⛔ phải cho đẹp. Cặp tự nhiên nhất —
    //    `3.50` và `4.00` — rơi TRÚNG hai đường lưới của trục, nên hai nét ngưỡng chồng khít lên
    //    hai nét lưới và phép đếm *độ cao phân biệt* ⛔ khẳng định được gì. Lượt chạy đầu của bài
    //    này đỏ đúng ở đó (`expected 6 to be 8`), và đó là T48.7 lặp lại: *cặp giá trị tự nhiên
    //    nhất lại là cặp ⛔ khẳng định gì*.
    const svg = veRaSvg(
      bieuDo([
        { chiTieu: 'Thượng lưu', tenMuc: 'Báo động II', giaTri: '3.33', khoaMau: 'alert-level-2' },
        { chiTieu: 'Thượng lưu', tenMuc: 'Báo động III', giaTri: '3.77', khoaMau: 'alert-level-3' },
      ]),
    );
    const net = netNgangDut(svg);
    expect(net.length - NEN).toBe(2);
    expect(new Set(net.map((n) => Math.round(n.y))).size).toBe(NEN + 2);
  });
});
