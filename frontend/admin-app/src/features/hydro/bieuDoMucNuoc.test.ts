import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * **`optionDuong` đã có nơi gọi THẬT** — đóng §10.33 bằng một con số. T35.4.
 *
 * <h3>⛔ Vì sao đây là một bài kiểm chứ không phải một dòng ghi chú</h3>
 *
 * `optionDuong` sống trong `chartOptions.ts` từ Phase 1 với **0 nơi gọi** ngoài bài kiểm của chính
 * nó, và javadoc của nó tự đặt hạn: *"⛔ Nếu Phase 2 đến mà vẫn không ai gọi thì phải XOÁ, không
 * phải giữ"*. Một hàm thuần **có bài kiểm riêng** là dạng nợ khó thấy nhất: bộ test xanh, độ phủ
 * đẹp, và không dòng nào của nó từng chạy ở production.
 *
 * ⚠ Nay nó có nơi gọi. Bài này giữ điều đó — nếu ai gỡ trang biểu đồ mà quên gỡ hàm, nợ cũ quay
 * lại **im lặng**, đúng như nó đã im lặng suốt Phase 1.
 *
 * <h3>⚠ Phạm vi tự khai (luật 28)</h3>
 *
 * Quét toàn bộ `src/`, bỏ qua `*.test.*` và chính `chartOptions.ts`. ⛔ Đếm ở đâu khác thì "được
 * kiểm bởi bài kiểm của chính nó" lại tính là "có người gọi" — đúng cái nhầm đã kéo dài một phase.
 *
 * <h3>⛔⛔ Canh CẤU TRÚC, ⛔ không canh VĂN BẢN — luật 2, và bản đầu của bài này đã sai đúng thế</h3>
 *
 * Bản đầu chỉ hỏi `noiDung.includes('optionDuong')`. Lượt kiểm chứng ngược (đổi tên hàm ở trang
 * biểu đồ) **vẫn xanh**, và thủ phạm là một dòng **javadoc** ở `api-types.ts` nhắc tên hàm — do
 * chính tôi viết, cùng đợt. Một lời nhắc trong chú thích được đếm thành một nơi gọi, nên bộ canh
 * sẽ **vĩnh viễn** xanh kể cả sau khi hàm bị bỏ rơi thật.
 *
 * ⇒ Nay đòi **hai tín hiệu độc lập**, và ⛔ không tín hiệu nào văn xuôi tạo ra được: một **ràng buộc
 * import** từ đúng module, và một **lời gọi** `optionDuong(`. Cùng khuôn luật 29 — hai vế ⛔ không
 * chia sẻ giả định.
 */

const GOC = join(dirname(new URL(import.meta.url).pathname), '..', '..');

function moiTepNguon(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) return moiTepNguon(duong);
    if (!/\.tsx?$/.test(ten)) return [];
    // ⛔ Bỏ bài kiểm VÀ bỏ chính tệp định nghĩa: cả hai đều "nhắc tên" hàm mà ⛔ không phải nơi gọi.
    if (/\.test\.tsx?$/.test(ten) || ten === 'chartOptions.ts') return [];
    return [duong];
  });
}

/** Ràng buộc import thật từ đúng module — ⛔ một dòng chú thích ⛔ không tạo ra được cái này. */
const IMPORT_THAT = /import\s*\{[^}]*\boptionDuong\b[^}]*\}\s*from\s*['"][^'"]*chartOptions['"]/;

/** Lời gọi thật. ⚠ Tín hiệu thứ hai, độc lập với tín hiệu trên (luật 29). */
const GOI_THAT = /\boptionDuong\s*\(/;

describe('optionDuong — chuỗi thời gian đầu tiên của hệ (T35.4)', () => {
  it('⭐⭐ có ÍT NHẤT một nơi gọi thật ngoài bài kiểm — §10.33 đóng bằng con số', () => {
    const noiGoi = moiTepNguon(GOC)
      .map((t) => [t, readFileSync(t, 'utf8')] as const)
      .filter(([, ma]) => IMPORT_THAT.test(ma) && GOI_THAT.test(ma));

    expect(
      noiGoi.length,
      '⛔ `optionDuong` không còn nơi gọi nào ngoài bài kiểm của chính nó. Javadoc của hàm ghi rõ: ' +
        'Phase 2 đến mà vẫn không ai gọi thì XOÁ, không phải giữ. Một hàm thuần CÓ bài kiểm riêng là ' +
        'dạng nợ khó thấy nhất — bộ test xanh, độ phủ đẹp, và không dòng nào từng chạy ở production.',
    ).toBeGreaterThanOrEqual(1);
  });

  /**
   * ⚠ Vế phân biệt của luật 9 — bộ canh phải **phân biệt được** một lần nhắc tên với một nơi gọi.
   *
   * Không có vế này thì bản "canh văn bản" cũ xanh y hệt bản "canh cấu trúc" mới, và ⛔ không có gì
   * chỉ ra rằng nó đã mù.
   */
  it('⛔ một dòng CHÚ THÍCH nhắc tên hàm ⛔ KHÔNG được tính là nơi gọi', () => {
    const chiNhacTen = ' *   `optionDuong` đặt `connectNulls: false` để chỗ ấy nhìn thấy được;';

    expect(IMPORT_THAT.test(chiNhacTen), 'văn xuôi ⛔ không tạo ra được một ràng buộc import').toBe(
      false,
    );
    expect(
      GOI_THAT.test(chiNhacTen),
      'văn xuôi ⛔ không tạo ra được một lời gọi có dấu mở ngoặc',
    ).toBe(false);
  });

  /**
   * ⛔⛔ Ba quyết định chịu lực của trang biểu đồ, canh ở tầng **cấu trúc** chứ ⛔ không ở tầng văn
   * bản hiển thị (luật 2: `includes('.sn-align-center')` vẫn xanh sau khi thuộc tính đã bị xoá hẳn).
   */
  it('⛔ trang biểu đồ giữ ba quyết định: empty tường minh · lý do từ backend · nhịp 2 phút', () => {
    const trang = readFileSync(join(GOC, 'features/hydro/WaterLevelChartPage.tsx'), 'utf8');

    expect(trang, '`empty` phải khai TƯỜNG MINH — BaseChart ⛔ không suy từ `option`').toContain(
      'empty={diem.length === 0}',
    );
    expect(
      trang,
      '⛔ Lý do biểu đồ rỗng đến TỪ BACKEND — ⛔ không được viết cứng một câu ở đây, vì backend là nơi ' +
        'duy nhất phân biệt được "trạm chưa gửi số" với "mọi bản ghi đều nghi ngờ" (quy tắc 16).',
    ).toContain('bieu.data?.lyDoTrong');
    expect(
      trang,
      '⛔ Nhịp nội bộ 2 phút bám chu kỳ poller (chốt G3) — ⛔ KHÔNG gộp với nhịp 5 phút của cổng (OI-09)',
    ).toMatch(/const NHIP_LAM_MOI_MS = 2 \* 60 \* 1000/);
  });

  it('⚠ tự kiểm: bộ quét ĐỌC ĐƯỢC tệp thật và ⛔ không khớp một tên bịa', () => {
    const tep = moiTepNguon(GOC);

    expect(
      tep.length,
      'quét ra 0 tệp nguồn ⇒ mọi khẳng định trên xanh trên tập rỗng',
    ).toBeGreaterThan(50);
    expect(
      tep.filter((t) => readFileSync(t, 'utf8').includes('optionKhongBaoGioTonTai')),
      'một tên hàm bịa ⛔ không được khớp — nếu nó khớp thì phép lọc đang trả về mọi tệp',
    ).toHaveLength(0);
  });
});
