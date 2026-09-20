import { describe, expect, it } from 'vitest';

import { bocInline, chuThuan, docTaiLieu, taoNeo, type Block, type InlineNode } from './markdown';
import nguon from './huong-dan-su-dung.md?raw';

/**
 * **Bộ đọc markdown hẹp phải TỰ KHAI khoảng trống của nó** — luật 28.
 *
 * <h2>⛔⛔ Hình dạng lỗi bài này sinh ra để chặn</h2>
 *
 * `markdown.ts` cố ý chỉ đỡ tập cú pháp mà `huong-dan-su-dung.md` đang dùng. Cái hẹp ấy an toàn
 * **đúng chừng nào có người canh** — ngày ai đó sửa hướng dẫn và viết một cú pháp mới (một danh
 * sách lồng, một ảnh, một thẻ HTML), bộ đọc ⛔ ném, ⛔ báo, nó chỉ **in nguyên văn dấu markdown ra
 * màn hình cho cán bộ Công ty đọc**. Người viết tài liệu xem diff thấy markdown đúng chuẩn; người
 * đọc trang thấy `**chữ đậm**` đầy dấu sao. ⛔ Ai nối được hai đầu ấy lại.
 *
 * ⇒ Vế trái của bài này **ĐO từ tài liệu thật**, ⛔ phải một danh sách gõ tay (cùng khuôn với
 * `CotPhase2CoDocGhiTest` · `VongKhuHoiDuPhamViTest`): phân tích cả tệp rồi đòi **0 khối ⛔ đọc
 * được** và **0 dấu markdown còn sót**.
 */

const KHOI = docTaiLieu(nguon);

/** Dấu markdown ⛔ được phép còn nằm trong chữ đã bóc — mỗi cái là một cú pháp bộ đọc ⛔ hiểu. */
const DAU_CON_SOT: readonly { ten: string; re: RegExp }[] = [
  { ten: 'chữ đậm `**…**`', re: /\*\*/ },
  { ten: 'chữ nghiêng `*…*`', re: /(?<!\*)\*(?!\*)/ },
  /**
   * ⭐⭐ Dòng này thêm ngày 20/09 sau khi bộ canh **XANH trong đúng tình huống nó sinh ra để bắt**:
   * Prettier chuẩn hoá `*nghiêng*` → `_nghiêng_`, bộ đọc ⛔ hiểu dạng ấy, 58 câu in ra nguyên văn
   * gạch dưới — và bài này ⛔ thấy gì vì danh sách ⛔ có `_`. Chỉ bài đếm thẻ `<em>` bắt được.
   */
  { ten: 'chữ nghiêng `_…_` (dạng Prettier)', re: /(?<![\w])_[^_\n]+_(?![\w])/ },
  { ten: 'mã `` `…` ``', re: /`/ },
  { ten: 'liên kết `[…](…)`', re: /\]\(/ },
  { ten: 'thẻ HTML thô', re: /<\/?[a-zA-Z][^>]*>/ },
  { ten: 'ảnh `![…](…)`', re: /!\[/ },
  { ten: 'gạch ngang `~~…~~`', re: /~~/ },
];

/** Mọi dãy inline trong tài liệu, kèm đường đi để thông điệp đỏ chỉ được đúng chỗ. */
function moiInline(khoi: readonly Block[]): { duong: string; nodes: InlineNode[] }[] {
  const ra: { duong: string; nodes: InlineNode[] }[] = [];
  khoi.forEach((b, i) => {
    switch (b.kind) {
      case 'heading':
        ra.push({ duong: `khối ${i} tiêu đề`, nodes: b.inline });
        break;
      case 'paragraph':
        ra.push({ duong: `khối ${i} đoạn văn`, nodes: b.inline });
        break;
      case 'quote':
        b.paragraphs.forEach((p, j) => ra.push({ duong: `khối ${i} trích dẫn §${j}`, nodes: p }));
        break;
      case 'list':
        b.items.forEach((it, j) => ra.push({ duong: `khối ${i} danh sách §${j}`, nodes: it }));
        break;
      case 'table':
        b.head.forEach((o, j) => ra.push({ duong: `khối ${i} bảng, đầu cột ${j}`, nodes: o }));
        b.rows.forEach((h, j) =>
          h.forEach((o, k) => ra.push({ duong: `khối ${i} bảng, hàng ${j} cột ${k}`, nodes: o })),
        );
        break;
      default:
        break;
    }
  });
  return ra;
}

describe('phạm vi bộ đọc markdown — đo trên chính tài liệu đang phục vụ', () => {
  it('⚠ đọc được tài liệu thật — chống xanh trên tập rỗng (luật 7)', () => {
    expect(nguon.length, 'tệp .md rỗng ⇒ mọi khẳng định dưới đây vô nghĩa').toBeGreaterThan(10_000);
    expect(KHOI.length).toBeGreaterThan(100);
    // Vế phân biệt: phải có đủ MỌI loại khối, ⛔ phải "phân tích ra cái gì đó".
    const loai = new Set(KHOI.map((b) => b.kind));
    expect([...loai].sort()).toEqual(
      ['code', 'directive', 'heading', 'list', 'paragraph', 'quote', 'rule', 'table'].sort(),
    );
  });

  it('⭐⭐ ⛔ dòng nào của tài liệu rơi ra ngoài tầm bộ đọc', () => {
    const la = KHOI.filter((b) => b.kind === 'unknown');
    expect(
      la,
      '⛔ Có dòng bộ đọc ⛔ hiểu. Hoặc sửa `huong-dan-su-dung.md` về tập cú pháp đã đỡ, hoặc mở ' +
        'rộng `markdown.ts` — ⛔ bao giờ bỏ qua: một dòng ⛔ đọc được sẽ hiện nguyên văn dấu ' +
        'markdown ra cho người dùng, hoặc biến mất khỏi tài liệu mà ⛔ ai biết.\n' +
        la.map((b) => (b.kind === 'unknown' ? `  dòng ${b.lineNumber}: ${b.line}` : '')).join('\n'),
    ).toHaveLength(0);
  });

  it('⭐⭐ ⛔ dấu markdown nào còn sót lại trong chữ đã bóc', () => {
    const viPham: string[] = [];
    /**
     * ⚠⚠ Phải ĐỆ QUY xuống `children`. Bản đầu chỉ soi tầng ngoài, và từ lúc `strong`/`em`/`link`
     * mang con thì tầng ngoài của chúng ⛔ còn chữ nào — bộ canh sẽ **xanh trong đúng tình huống
     * nó sinh ra để bắt** (luật 7): một dấu markdown lọt vào bên trong một chữ đậm là vô hình.
     */
    const soi = (duong: string, nodes: readonly InlineNode[]): void => {
      for (const n of nodes) {
        // `code` giữ nguyên văn bên trong dấu nháy ngược — đó LÀ nội dung của nó.
        if (n.kind === 'code') {
          continue;
        }
        if (n.kind !== 'plain') {
          soi(duong, n.children);
          continue;
        }
        for (const { ten, re } of DAU_CON_SOT) {
          if (re.test(n.text)) {
            viPham.push(`  ${duong}: còn ${ten} trong "${n.text.slice(0, 70)}"`);
          }
        }
      }
    };
    for (const { duong, nodes } of moiInline(KHOI)) {
      soi(duong, nodes);
    }
    expect(
      viPham,
      '⛔ Dấu markdown lọt xuống chữ hiển thị. Người đọc trang sẽ thấy nguyên văn dấu sao / dấu ' +
        'nháy, trong khi người sửa tài liệu xem diff thì thấy markdown hoàn toàn đúng chuẩn.\n' +
        viPham.join('\n'),
    ).toHaveLength(0);
  });

  it('⭐ mọi bảng có số ô khớp dòng đầu — ô lệch cột là dữ liệu trôi sang cột bên cạnh', () => {
    const lech: string[] = [];
    KHOI.forEach((b, i) => {
      if (b.kind !== 'table') {
        return;
      }
      b.rows.forEach((h, j) => {
        if (h.length !== b.head.length) {
          lech.push(`  khối ${i} hàng ${j}: ${h.length} ô / đầu bảng ${b.head.length} ô`);
        }
      });
    });
    expect(lech, `⛔ Bảng lệch cột:\n${lech.join('\n')}`).toHaveLength(0);
  });

  /**
   * ⭐⭐ Mục lục của tài liệu là **liên kết**, và một liên kết gãy ⛔ có triệu chứng nào ngoài
   * việc bấm vào ⛔ đi đâu cả. Đổi tên một đề mục mà quên sửa mục lục là đúng ca ấy.
   */
  it('⭐⭐ mọi neo trong tài liệu trỏ tới một tiêu đề CÓ THẬT', () => {
    const neoCo = new Set(
      KHOI.filter((b) => b.kind === 'heading').map((b) => (b.kind === 'heading' ? b.id : '')),
    );
    const treo: string[] = [];
    const soiNeo = (duong: string, nodes: readonly InlineNode[]): void => {
      for (const n of nodes) {
        if (n.kind === 'plain' || n.kind === 'code') {
          continue;
        }
        if (n.kind === 'link' && n.href.startsWith('#') && !neoCo.has(n.href.slice(1))) {
          treo.push(`  ${duong}: "${chuThuan(n.children)}" → ${n.href}`);
        }
        soiNeo(duong, n.children);
      }
    };
    for (const { duong, nodes } of moiInline(KHOI)) {
      soiNeo(duong, nodes);
    }
    expect(
      treo,
      `⛔ Neo trỏ vào hư không — bấm vào ⛔ đi đâu, và ⛔ có gì báo:\n${treo.join('\n')}`,
    ).toHaveLength(0);
    // Vế chống tập rỗng: tài liệu PHẢI có mục lục, ⛔ thì bài trên xanh mà ⛔ canh gì.
    expect(neoCo.size).toBeGreaterThan(10);
  });

  it('⭐ neo ⛔ trùng nhau — hai tiêu đề cùng neo thì mục lục luôn nhảy về cái đầu', () => {
    const ids = KHOI.filter((b) => b.kind === 'heading').map((b) =>
      b.kind === 'heading' ? b.id : '',
    );
    expect(ids.length - new Set(ids).size).toBe(0);
  });

  /**
   * ⚠⚠ **Vế kiểm chứng ngược** — luật 7 · luật 29.
   *
   * Ba bài trên đều khẳng định *"⛔ có gì sai"*, tức chúng xanh cả khi bộ đọc **hỏng theo hướng
   * dễ dãi** (nuốt sạch mọi thứ nó ⛔ hiểu). Bài này ép chúng phân biệt được hai trạng thái: đưa
   * vào đúng những cú pháp `markdown.ts` ⛔ đỡ và đòi bộ canh **BẮT ĐƯỢC**.
   */
  describe('⚠ tự kiểm — bộ canh phải bắt được cú pháp ngoài phạm vi', () => {
    it('thẻ HTML thô bị bắt, ⛔ đi lọt', () => {
      expect(
        /<\/?[a-zA-Z][^>]*>/.test(chuThuan(bocInline('Xem <b>chỗ này</b> nhé'))),
        'bộ đọc ⛔ hiểu HTML thô ⇒ dấu vết PHẢI còn lại để bộ canh thấy',
      ).toBe(true);
    });

    it('⭐⭐ chữ đậm CHỨA chữ nghiêng — ca thứ hai làm bộ canh đỏ, 20/09', () => {
      const nodes = bocInline('**Chỉ tính trên số liệu *Hợp lệ*.**');
      expect(nodes).toHaveLength(1);
      expect(
        nodes[0].kind,
        '⛔ Mẫu `strong` trượt ⇒ `em` khớp lệch một ký tự ⇒ rơi ra một dấu sao',
      ).toBe('strong');
      expect(chuThuan(nodes)).toBe('Chỉ tính trên số liệu Hợp lệ.');
    });

    it('⭐ định dạng LỒNG NHAU bóc tới tận cùng — ca đã làm bộ canh đỏ lượt chạy đầu', () => {
      const nodes = bocInline('**`Ghi mới = 0` là chuyện bình thường**');
      expect(nodes).toHaveLength(1);
      expect(nodes[0].kind).toBe('strong');
      // Vế phân biệt: ⛔ đủ khi "⛔ còn dấu nháy" — đoạn mã phải thật sự thành một nút `code`.
      const con = nodes[0].kind === 'strong' ? nodes[0].children : [];
      expect(con.some((n) => n.kind === 'code' && n.text === 'Ghi mới = 0')).toBe(true);
      expect(chuThuan(nodes)).toBe('Ghi mới = 0 là chuyện bình thường');
    });

    /**
     * ⭐⭐ Ca này lộ ra ở lượt **kiểm chứng ngược** 20/09 và bản đầu của bộ canh ⛔ THẤY.
     *
     * `![mô tả](a.png)` là cú pháp **ảnh**; mẫu liên kết nuốt phần `[…](…)` rồi bỏ lại mỗi dấu
     * `!`, nên tấm ảnh lặng lẽ thành một liên kết gãy và ⛔ dấu vết nào còn đủ nguyên cho bộ canh
     * nhận ra. Vá bằng lookbehind trong `markdown.ts`; bài này là chốt giữ.
     */
    it('⭐⭐ ảnh ⛔ bị đọc nhầm thành liên kết — dấu vết phải còn nguyên', () => {
      const nodes = bocInline('Xem ![sơ đồ](a.png) nhé');
      expect(
        nodes.some((n) => n.kind === 'link'),
        '⛔ `![…](…)` là ẢNH, ⛔ phải liên kết. Đọc nhầm là một tấm ảnh biến thành liên kết gãy ' +
          'trong im lặng, và bộ canh ⛔ còn dấu vết nào để thấy.',
      ).toBe(false);
      expect(/!\[/.test(chuThuan(nodes))).toBe(true);
    });

    it('⛔ và một liên kết THẬT vẫn bóc bình thường — vế phân biệt của lookbehind', () => {
      const nodes = bocInline('Xem [mục 4](#4-vai-tro) nhé');
      expect(nodes.some((n) => n.kind === 'link')).toBe(true);
    });

    /**
     * ⭐⭐ Ca **Prettier** — xem javadoc `MAU_INLINE` trong `markdown.ts`. Bộ đọc phải hiểu dạng mà
     * bộ định dạng mã sinh ra, ⛔ phải dạng người viết gõ vào.
     */
    it('⭐⭐ `_nghiêng_` (đầu ra chính tắc của Prettier) bóc được', () => {
      const nodes = bocInline('Mở _Cấu hình hệ thống_ nhé');
      expect(nodes.some((n) => n.kind === 'em')).toBe(true);
      expect(chuThuan(nodes)).toBe('Mở Cấu hình hệ thống nhé');
    });

    it('⛔ `snake_case` ⛔ bị biến thành chữ nghiêng — vế phân biệt của lookbehind', () => {
      const nodes = bocInline('Bảng hydro_raw_logs và cột org_unit_id giữ nguyên');
      expect(
        nodes.some((n) => n.kind === 'em'),
        '⛔ Gạch dưới giữa hai chữ cái là tên cột, ⛔ phải dấu nhấn mạnh. Nghiêng nó đi là nuốt ' +
          'mất dấu gạch dưới khỏi một cái tên mà người đọc sẽ đi gõ lại.',
      ).toBe(false);
      expect(chuThuan(nodes)).toBe('Bảng hydro_raw_logs và cột org_unit_id giữ nguyên');
    });

    it('một dòng bảng lẻ loi (⛔ dòng phân cách) thành khối ⛔ đọc được', () => {
      const ra = docTaiLieu('Câu mở đầu.\n| a | b |\n');
      expect(ra.some((b) => b.kind === 'unknown')).toBe(true);
    });

    it('cú pháp ĐÃ đỡ thì ⛔ bị báo nhầm — vế phân biệt', () => {
      const ra = docTaiLieu('| a | b |\n|---|---|\n| 1 | 2 |\n');
      expect(ra.filter((b) => b.kind === 'unknown')).toHaveLength(0);
      expect(ra[0].kind).toBe('table');
    });

    it('neo sinh ra khớp kiểu GitHub — mục lục phải bấm được ở CẢ hai nơi', () => {
      expect(taoNeo('1. Hệ thống gồm những gì')).toBe('1-hệ-thống-gồm-những-gì');
      expect(taoNeo('10. Những ô đang trống và vì sao')).toBe('10-những-ô-đang-trống-và-vì-sao');
    });

    it('chuThuan ghép lại đúng câu gốc — mục lục ⛔ được rơi mất chữ', () => {
      expect(chuThuan(bocInline('Bấm **Lưu** rồi xem `log`'))).toBe('Bấm Lưu rồi xem log');
    });
  });
});
