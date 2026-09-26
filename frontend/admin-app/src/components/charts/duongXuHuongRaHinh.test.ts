import { describe, expect, it } from 'vitest';

import { statusColors } from '@songnhue/design-tokens';

import { optionDuongXuHuong } from './chartOptions';
import { echarts, THEME_SANG } from './setup';

/**
 * Biểu đồ mực nước tô theo **chiều** — đo trên HÌNH, ⛔ trên `option` (WS-87 · T87.6).
 *
 * <h2>⛔⛔ Vì sao ⛔ đọc lại `option`</h2>
 *
 * `optionDuongXuHuong` là hàm thuần, nên một khẳng định kiểu `option.series[1].lineStyle.color`
 * bao giờ cũng xanh — nó chỉ nói *"tôi vừa viết ra thứ tôi vừa viết"*. Nó **⛔ phân biệt được**
 * những thứ thật sự làm hỏng hình:
 *
 * <ul>
 *   <li>một chuỗi phủ có đúng **một** mốc ⇒ ECharts ⛔ vẽ đoạn nào (một điểm ⛔ thành đoạn) —
 *       `option` vẫn "đúng" hoàn toàn;
 *   <li>cơ chế tô bị đổi sang một thứ ECharts **⛔ hỗ trợ cho nét**. Đo 26/09/2026: `visualMap`
 *       theo chiều trục thì **NÉM** ở `LineView.getVisualGradient`, theo chiều khác thì
 *       **⛔ hiệu lực gì** — nét vẫn mang màu mặc định. Cả hai lượt ấy `option` đều "đẹp".
 * </ul>
 *
 * ⇒ Dựng thật, kết xuất SVG, **đếm màu của những nét có vẽ ra thứ gì đó**. Đó là lý do
 * `SVGRenderer` có mặt trong `setup.ts`.
 */
function veRaSvg(moc: string[], giaTri: (number | null)[]): string {
  const doThi = echarts.init(null, THEME_SANG, {
    renderer: 'svg',
    ssr: true,
    width: 800,
    height: 360,
  });
  try {
    doThi.setOption({ ...optionDuongXuHuong(moc, 'Mực nước (m)', giaTri), animation: false });
    return doThi.renderToSVGString();
  } finally {
    doThi.dispose();
  }
}

/**
 * Màu của những nét **thật sự vẽ ra thứ gì đó**.
 *
 * <h2>⛔⛔ Bản đầu của hàm này quét mọi `stroke=` và nó ĐO SAI — hai nguồn dương tính giả</h2>
 *
 * Dump SVG thật (26/09/2026) cho thấy ECharts vẫn phát một thẻ `<path>` cho:
 *
 * <ul>
 *   <li>chuỗi **rỗng hoàn toàn** — `d=""` nhưng `stroke` vẫn mang màu của chuỗi ấy;
 *   <li>chuỗi **vô hình** (`lineStyle.opacity: 0`) và **ô chú giải** — `stroke-opacity="0"`.
 * </ul>
 *
 * ⇒ Một phép quét thô thấy đủ cả ba màu ở **mọi** chuỗi, kể cả chuỗi chỉ tăng — tức nó xanh ở CẢ
 * HAI trạng thái nó sinh ra để phân biệt (luật 9). Phải hỏi *"nét này có vẽ gì ⛔"*: `d` ⛔ rỗng
 * **và** ⛔ trong suốt.
 */
function mauNet(svg: string): Set<string> {
  const ra = new Set<string>();
  for (const [the] of svg.matchAll(/<path\b[^>]*>/g)) {
    const d = /\bd="([^"]*)"/.exec(the)?.[1] ?? '';
    if (d.trim() === '') continue;
    if (/stroke-opacity="0"/.test(the)) continue;
    const mau = /\bstroke="(#[0-9a-fA-F]{3,8})"/.exec(the)?.[1];
    if (mau) ra.add(mau.toLowerCase());
  }
  return ra;
}

const NHAN = ['00:00', '00:10', '00:20', '00:30', '00:40'];

describe('optionDuongXuHuong — tô theo CHIỀU, nước lên là ĐỎ (WS-87)', () => {
  it('⭐⭐ chuỗi LÊN rồi XUỐNG ⇒ hình có CẢ hai màu: đỏ cho đoạn lên, xanh cho đoạn xuống', () => {
    const mau = mauNet(veRaSvg(NHAN, [4.0, 4.2, 4.5, 4.3, 4.1]));

    expect(
      mau,
      '⛔ Thiếu màu ĐỎ nghĩa là đoạn đi LÊN ⛔ được vẽ — hoặc chuỗi phủ chỉ giữ MỘT đầu của ' +
        'đoạn (một điểm ⛔ thành một đoạn), hoặc cơ chế tô đã đổi sang thứ ECharts ⛔ tô nét được',
    ).toContain(statusColors.danger.toLowerCase());
    expect(mau, '⛔ Thiếu màu XANH nghĩa là đoạn đi XUỐNG ⛔ được tô').toContain(
      statusColors.normal.toLowerCase(),
    );
  });

  /**
   * ⭐⭐ **Vế phân biệt *chiều* với *giá trị*.**
   *
   * Cách tô "hiển nhiên" khác là theo **giá trị** (cao/thấp). Với một chuỗi lên-rồi-xuống, cách ấy
   * cũng cho ra hai màu ⇒ bài ở trên vẫn XANH. Chuỗi **chỉ tăng** là thứ tách được hai trạng thái:
   * theo *chiều* thì nó chỉ có một màu, theo *giá trị* thì nó vẫn ra hai.
   */
  it('⭐⭐ chuỗi CHỈ TĂNG ⇒ ⛔ có nét XANH nào — nếu có, màu đang đi theo GIÁ TRỊ ⛔ theo CHIỀU', () => {
    const mau = mauNet(veRaSvg(NHAN, [4.0, 4.2, 4.5, 4.9, 5.4]));

    expect(mau).toContain(statusColors.danger.toLowerCase());
    expect(
      mau,
      '⛔⛔ Một chuỗi CHỈ TĂNG mà có nét xanh nghĩa là màu đang đi theo GIÁ TRỊ (cao/thấp) chứ ⛔ ' +
        'phải theo CHIỀU (lên/xuống) — hai câu hỏi khác hẳn nhau, và câu sai là câu nghe hợp lý hơn',
    ).not.toContain(statusColors.normal.toLowerCase());
  });

  it('⛔ chuỗi CHỈ GIẢM ⇒ ⛔ có nét ĐỎ nào — vế đối xứng, để bài trên ⛔ xanh vì lý do sai', () => {
    const mau = mauNet(veRaSvg(NHAN, [5.4, 4.9, 4.5, 4.2, 4.0]));

    expect(mau).toContain(statusColors.normal.toLowerCase());
    expect(mau).not.toContain(statusColors.danger.toLowerCase());
  });

  /**
   * ⚠ Quãng mất tín hiệu phải NGẮT đường, và đoạn nối lại ⛔ được mang một "chiều" bịa ra.
   *
   * `connectNulls: false` lo vế ngắt. Vế thứ hai là **chỗ đặt giá trị trong chuỗi phủ**: một mốc
   * chỉ được vào chuỗi *Đang lên* / *Đang xuống* khi nó có một đoạn **kề thật sự** đi đúng chiều —
   * mốc đứng cạnh khoảng trống thì ⛔ có đoạn nào để xét.
   */
  it('⛔ mốc cạnh khoảng MẤT TÍN HIỆU ⛔ vào chuỗi chiều nào — ⛔ bịa một lượt lên/xuống', () => {
    const opt = optionDuongXuHuong(['a', 'b', 'c', 'd', 'e'], 'Mực nước (m)', [
      4.0,
      null,
      null,
      4.9,
      5.0,
    ]) as { series: { name: string; data: (number | null)[] }[] };
    const chuoi = (ten: string) => opt.series.find((s) => s.name === ten)!.data;

    expect(
      chuoi('Đang lên')[0],
      '⛔⛔ Mốc 4.0 đứng một mình giữa hai khoảng trống — cho nó vào "Đang lên" là dựng một lượt ' +
        'tăng từ hai số cách nhau 30 phút mà ⛔ ai đo',
    ).toBeNull();
    expect(chuoi('Đang lên')[1], 'mốc mất tín hiệu ⇒ ⛔ có giá trị').toBeNull();
    expect(
      chuoi('Đang lên')[3],
      '4.9 → 5.0 là một đoạn tăng THẬT ⇒ cả hai đầu phải có mặt để vẽ được nó',
    ).toBeCloseTo(4.9, 6);
    expect(chuoi('Đang lên')[4]).toBeCloseTo(5.0, 6);
    expect(
      chuoi('Đang xuống').every((v) => v === null),
      'chuỗi này ⛔ có đoạn giảm nào',
    ).toBe(true);
  });

  /**
   * ⚠ Đoạn **PHẲNG** phải được vẽ, bằng màu xám.
   *
   * Bỏ nó đi là để một quãng mực nước đứng yên **biến mất khỏi hình** — và một quãng trắng trên
   * biểu đồ trực ban đọc y hệt *mất tín hiệu* (quy tắc 16 · luật 9).
   */
  it('⛔ quãng PHẲNG ⛔ biến mất — nó vào chuỗi "Không đổi", màu xám', () => {
    const mau = mauNet(veRaSvg(NHAN, [4.2, 4.2, 4.2, 4.2, 4.2]));

    expect(
      mau,
      '⛔⛔ Một quãng đứng yên mà ⛔ vẽ gì thì trông y hệt mất tín hiệu — hai trạng thái khác hẳn nhau',
    ).toContain(statusColors.unknown.toLowerCase());
    expect(mau).not.toContain(statusColors.danger.toLowerCase());
    expect(mau).not.toContain(statusColors.normal.toLowerCase());
  });
});
