import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * Bỏ chú thích khối và chú thích **cả dòng** trước khi soi mã.
 *
 * ⚠ Bỏ ÍT chứ ⛔ không bỏ NHIỀU: `//` nằm **giữa** dòng ⛔ không bị cắt, vì một chuỗi
 * `'https://…'` trong mã thật sẽ mất phần đuôi — và mất mã thật thì sinh **đỏ giả**, hỏng theo
 * chiều tệ hơn hẳn. Còn sót đúng một khe: nhắc tên trong chú thích `//` **cuối dòng mã**. Ghi ra
 * đây thay vì để người sau tưởng phép này kín (luật 28).
 *
 * ⛔ Bản sao của `CotPhase2CoDocGhiTest.boChuThich` ở backend — hai kho, ⛔ không dùng chung mã
 * được. Sửa một bên thì đọc lại bên kia.
 */
function boChuThich(ma: string): string {
  return ma.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/^\s*\/\/.*$/gm, ' ');
}

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
    // ⛔⛔ BỎ CHÚ THÍCH TRƯỚC KHI SOI — và đây là bài học phải trả giá HAI LẦN trong cùng một đợt.
    //
    //   Lần một: `CotPhase2CoDocGhiTest` (backend) đỏ vì một javadoc ở `toaDo.ts` nhắc tên cột
    //   `geom` — ⛔ không dòng mã nào đọc cột ấy, chỉ có một câu văn.
    //   Lần hai: chính khẳng định PHỦ ĐỊNH ngay bên dưới đỏ vì chú thích trong
    //   `WaterLevelChartPage.tsx` **giải thích** vì sao ⛔ không được dùng `diem.length === 0` —
    //   tức nó đỏ vì tài liệu mô tả đúng cấm lệnh mà nó canh.
    //
    //   ⇒ Một khẳng định phủ định trên **văn bản thô** ⛔ không phân biệt được *mã vi phạm* với
    //   *chú thích nói về vi phạm*, và nó phạt đúng người viết tài liệu tử tế (luật 2).
    const trang = boChuThich(
      readFileSync(join(GOC, 'features/hydro/WaterLevelChartPage.tsx'), 'utf8'),
    );

    // ⭐ Khai TƯỜNG MINH — `BaseChart` ⛔ không suy `empty` từ `option`.
    expect(trang, '`empty` phải khai TƯỜNG MINH — BaseChart ⛔ không suy từ `option`').toMatch(
      /empty=\{/,
    );
    // ⛔⛔ T43.13 — VẾ CHỊU LỰC, và nó là một khẳng định PHỦ ĐỊNH có chủ đích.
    //
    //   Bản trước ghim nguyên văn `empty={diem.length === 0}` — tức nó ghim CHÍNH BIỂU THỨC HỎNG.
    //   Từ khi backend trả **trục đủ 144 mốc** (dựng độc lập với dữ liệu), `diem` ⛔ không bao giờ
    //   rỗng, nên `diem.length === 0` là một điều kiện **KHÔNG BAO GIỜ ĐÚNG**: trạm chưa có số sẽ
    //   vẽ ra một khung trục trắng thay vì hiện câu giải thích — hỏng theo chiều im lặng (luật 9).
    //
    //   ⇒ Ghim điều kiện ĐÚNG (`soMocCoSo`) và **cấm** điều kiện cũ quay lại. Cấm lệnh phủ định là
    //   thứ duy nhất chặn được một lượt "dọn dẹp" đưa `diem.length` trở lại — nó đọc rất tự nhiên.
    expect(
      trang,
      '⛔ `empty` phải hỏi `soMocCoSo` — số MỐC CÓ SỐ, ⛔ không phải độ dài mảng `diem` (nay là TRỤC)',
    ).toContain('soMocCoSo');
    expect(
      trang,
      '⛔⛔ `diem.length === 0` là điều kiện KHÔNG BAO GIỜ ĐÚNG kể từ T43.13 — trục dựng độc lập với ' +
        'dữ liệu nên nó luôn đủ 144 phần tử. Dùng lại nó là tắt nhánh `empty` trong im lặng.',
    ).not.toContain('diem.length === 0');
    // ⛔ Và ô trống phải ra dây là `null`, ⛔ không được `Number(null)` → 0: mực nước 0 m là một
    //    khẳng định về mực nước (quy tắc 16).
    expect(
      trang,
      '⛔ Mốc ⛔ không có số phải vào ECharts dưới dạng `null` để `connectNulls:false` NGẮT được đường',
    ).toContain('d.giaTri === null ? null');
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

  it('⛔ bằng chứng cho phép BỎ CHÚ THÍCH — nếu không, khẳng định phủ định ở trên vô nghĩa', () => {
    const ma = [
      '/** javadoc nhắc diem.length === 0 nhưng ⛔ không dùng nó */',
      '// dong_chu_thich_rieng cũng vậy',
      'const x = maThatSuChay();',
      "const u = 'https://vi.du/khong-duoc-cat';",
    ].join('\n');
    const sach = boChuThich(ma);

    expect(sach, '⛔ khối javadoc phải biến mất').not.toContain('diem.length === 0');
    expect(sach, '⛔ dòng bắt đầu bằng `//` phải biến mất').not.toContain('dong_chu_thich_rieng');
    expect(sach, '⭐ ĐỐI CHỨNG: mã thật PHẢI còn — bỏ quá tay là sinh ĐỎ GIẢ (luật 10)').toContain(
      'maThatSuChay',
    );
    expect(
      sach,
      '⚠ GHIM ranh giới: `//` GIỮA dòng ⛔ không bị cắt, nếu không mọi `https://` mất đuôi',
    ).toContain('khong-duoc-cat');
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
