/**
 * Bộ đọc Markdown **hẹp có chủ đích** — chỉ phục vụ `huong-dan-su-dung.md`.
 *
 * <h2>⛔⛔ Vì sao ⛔ dùng `react-markdown`</h2>
 *
 * ⛔ Phải vì "tự viết cho vui": vế đánh đổi đo được. Thứ cần render là **một tài liệu duy
 * nhất nằm trong kho**, dùng một tập cú pháp **đóng** (đo 20/09/2026: heading · bảng · trích
 * dẫn · danh sách · đường kẻ · khối mã · đậm/nghiêng/mã/liên kết — **0** HTML thô, **0** ảnh,
 * **0** gạch ngang, **0** ký tự thoát). Đổi lấy hai gói npm mới, một luật `dangerouslySetInnerHTML`
 * phải rào, và một mặt phẳng phụ thuộc phải theo dõi — trong khi `T63.1` vừa cho thấy một
 * lượt bump phụ thuộc frontend có thể kéo theo những gì.
 *
 * <h2>⭐ Và cái hẹp ấy phải TỰ NÓI RA, ⛔ để im (luật 28)</h2>
 *
 * Một bộ đọc hẹp mà im lặng là bộ đọc sẽ in `**chữ đậm**` **nguyên văn dấu sao** ra màn hình
 * cho người dùng đọc, vào đúng ngày ai đó viết một cú pháp mới. Nên ở đây ⛔ có nhánh "bỏ qua
 * cái ⛔ hiểu": mọi dòng ⛔ khớp khối nào thành khối {@link UnknownBlock}, mọi dấu markdown còn
 * sót sau khi bóc inline nằm lại trong chuỗi — và `phamViMarkdown.test.ts` **ĐO tài liệu thật**
 * rồi đỏ khi gặp một trong hai. Khoảng trống được canh, ⛔ phải được giấu.
 */

// ---------------------------------------------------------------------------
// Mô hình inline
// ---------------------------------------------------------------------------

/**
 * Một mẩu chữ trong câu.
 *
 * <h3>⚠⚠ `strong`/`em`/`link` mang `children`, ⛔ phải `text` — và đó là một bản SỬA</h3>
 *
 * Bản đầu để cả năm loại cùng mang một `text: string` phẳng. `phamViMarkdown.test.ts` đỏ ngay
 * **lượt chạy đầu tiên** và chỉ đúng chỗ: `` **`Ghi mới = 0` là chuyện bình thường** `` — chữ đậm
 * **chứa** một đoạn mã. Mô hình phẳng nuốt cặp dấu nháy ngược vào trong `text`, nên trang sẽ in
 * ra nguyên văn `` `Ghi mới = 0` `` kèm hai dấu nháy cho cán bộ Công ty đọc.
 *
 * ⛔ Cách chữa rẻ hơn — sửa đúng câu ấy trong tài liệu — là **để nguyên cái bẫy**: lồng mã trong
 * chữ đậm là markdown hoàn toàn đúng chuẩn, nên lần sau bộ canh sẽ đỏ vào mặt người viết tài liệu
 * trong khi thứ sai là bộ đọc. Đúng hình dạng T46.7: *bộ canh phạt đúng người viết tài liệu tử tế*.
 *
 * ⚠ `code` là **lá**: nội dung giữa hai dấu nháy ngược là chữ nguyên văn theo định nghĩa, đệ quy
 * vào đó là bóc mất đúng thứ người viết đang muốn hiện ra.
 */
export type InlineNode =
  | { kind: 'plain'; text: string }
  | { kind: 'code'; text: string }
  | { kind: 'strong'; children: InlineNode[] }
  | { kind: 'em'; children: InlineNode[] }
  | { kind: 'link'; children: InlineNode[]; href: string };

/**
 * Thứ tự QUAN TRỌNG và ⛔ đổi được tuỳ ý.
 *
 * `code` đứng **đầu**: trong tài liệu có `` `**` `` xuất hiện bên trong dấu nháy ngược, và nếu
 * `strong` chạy trước thì nó ăn mất dấu nháy. `strong` (`**`) phải đứng trước `em` (`*`) vì
 * `*` là **tiền tố** của `**` — để `em` chạy trước thì mọi chữ đậm biến thành một chữ nghiêng
 * rỗng kẹp giữa hai dấu sao thừa.
 */
const MAU_INLINE: readonly { kind: InlineNode['kind']; re: RegExp }[] = [
  { kind: 'code', re: /`([^`\n]+)`/ },
  /**
   * ⚠⚠ `(?<!!)` — ⛔ phải trang trí. Markdown của **ảnh** là `![mô tả](đường-dẫn)`, tức đúng cú
   * pháp liên kết kèm một dấu `!` đứng trước. ⛔ Có lookbehind này thì mẫu liên kết nuốt phần
   * `[…](…)`, bỏ lại dấu `!` lạc lõng, và một tấm **ảnh lặng lẽ biến thành một liên kết gãy**.
   *
   * ⭐ Lộ ra ở lượt **kiểm chứng ngược** ngày 20/09: chèn `![sơ đồ](a.png)` vào tài liệu thật thì
   * `phamViMarkdown` bắt được cái thẻ HTML đi kèm mà **⛔ thấy tấm ảnh** — vì tới lúc nó soi thì
   * `![` đã bị tách làm đôi, `!` nằm ở một nút, `[…]` nằm trong một nút `link`. Luật 9: hai trạng
   * thái *"ảnh"* và *"liên kết"* ⛔ phân biệt được nữa.
   *
   * ⇒ Cách chữa là **để nguyên dấu vết**: bộ đọc ⛔ đỡ ảnh thì ⛔ được đụng vào, để `![` còn lại
   * trong chữ cho bộ canh nhìn thấy và gọi tên. Một cú pháp ⛔ đỡ phải HỎNG TO, ⛔ hỏng khẽ.
   */
  { kind: 'link', re: /(?<!!)\[([^\]]+)\]\(([^)\s]+)\)/ },
  /**
   * ⚠⚠ `(?:[^*\n]|\*(?!\*))+` chứ ⛔ phải `[^*\n]+` — thân chữ đậm phải cho phép **một** dấu sao
   * lẻ đi qua, để `**câu có *chữ nghiêng* bên trong**` khớp được.
   *
   * Với `[^*\n]+` thì mẫu `strong` trượt, rồi mẫu `em` khớp **lệch một ký tự**: nó ăn từ dấu sao
   * thứ hai, bỏ lại dấu sao thứ nhất nằm trơ ra màn hình và biến cả câu đậm thành câu nghiêng.
   * ⇒ Hỏng theo kiểu **vừa mất định dạng vừa thêm rác**, và ⛔ gì ném.
   *
   * ⭐ Lộ ra 20/09 ngay khi viết §6.8 — câu `**… số liệu *Hợp lệ*.**` là văn phong bình thường của
   * chính tài liệu này. Bắt được nhờ bộ canh dấu-còn-sót, ⛔ phải nhờ đọc lại.
   */
  { kind: 'strong', re: /\*\*((?:[^*\n]|\*(?!\*))+)\*\*/ },
  { kind: 'em', re: /\*([^*\n]+)\*/ },
  /**
   * ⚠⚠ **Dạng gạch dưới ⛔ phải một lựa chọn phong cách — nó là đầu ra CHÍNH TẮC của Prettier.**
   *
   * `frontend/package.json` chạy `prettier --check .`, và cổng `Frontend — lint` bắt buộc nó. Mà
   * Prettier **chuẩn hoá** `*nghiêng*` thành `_nghiêng_` khi định dạng tệp `.md`. Nghĩa là dù người
   * viết tài liệu gõ dấu sao, thứ **thật sự nằm trong kho** sau lượt commit luôn là dấu gạch dưới.
   *
   * ⭐ Đo được 20/09: lượt `prettier --write` đầu tiên đổi **58** chỗ nghiêng sang `_…_`, và bộ đọc
   * khi ấy ⛔ hiểu dạng ấy ⇒ 58 câu in ra kèm gạch dưới nguyên văn cho người dùng đọc.
   *
   * ⛔⛔ Và `phamViMarkdown` khi ấy **XANH** — danh sách dấu còn sót của nó ⛔ có `_`. Thứ bắt được
   * là bài render đếm **số thẻ `<em>`** (`huongDanPage.test.tsx`): một bộ canh hỏi *"còn dấu ⛔"* và
   * một bộ canh hỏi *"có thành thẻ ⛔"* trả lời hai câu khác nhau, và ở đây chỉ câu thứ hai thấy.
   *
   * ⇒ Bài học ghi lại: bộ đọc phải hiểu dạng mà **bộ định dạng mã** sinh ra, ⛔ phải dạng mà người
   * viết gõ vào. Cùng họ §11.13 — *một bộ canh mà Spotless làm cho sai là bộ canh đang canh văn bản*.
   *
   * ⚠ `(?<![\w])` / `(?![\w])` để ⛔ cắn vào `snake_case`: `hydro_raw_logs` ⛔ được thành chữ nghiêng.
   */
  { kind: 'strong', re: /(?<![\w])__((?:[^_\n]|_(?!_))+)__(?![\w])/ },
  { kind: 'em', re: /(?<![\w])_([^_\n]+)_(?![\w])/ },
];

/**
 * Bóc định dạng trong một dòng chữ.
 *
 * ⚠ Quét **trái sang phải theo vị trí khớp SỚM NHẤT**, ⛔ theo thứ tự trong {@link MAU_INLINE}.
 * Chạy lần lượt từng mẫu trên cả chuỗi sẽ cho kết quả phụ thuộc thứ tự mẫu chứ ⛔ phụ thuộc
 * thứ tự chữ: câu `` Bấm **Lưu** rồi xem `log` `` sẽ ra `log` trước `Lưu`.
 */
export function bocInline(dong: string): InlineNode[] {
  const ra: InlineNode[] = [];
  let con = dong;

  while (con.length > 0) {
    let somNhat: { kind: InlineNode['kind']; m: RegExpExecArray } | null = null;

    for (const { kind, re } of MAU_INLINE) {
      const m = re.exec(con);
      if (m && (somNhat === null || m.index < somNhat.m.index)) {
        somNhat = { kind, m };
      }
    }

    if (somNhat === null) {
      ra.push({ kind: 'plain', text: con });
      break;
    }

    const { kind, m } = somNhat;
    if (m.index > 0) {
      ra.push({ kind: 'plain', text: con.slice(0, m.index) });
    }
    // ⚠ Đệ quy vào thân của `strong`/`em`/`link`; `code` giữ nguyên văn (xem `InlineNode`).
    switch (kind) {
      case 'code':
        ra.push({ kind: 'code', text: m[1] });
        break;
      case 'link':
        ra.push({ kind: 'link', children: bocInline(m[1]), href: m[2] });
        break;
      case 'strong':
      case 'em':
        ra.push({ kind, children: bocInline(m[1]) });
        break;
      default:
        break;
    }
    con = con.slice(m.index + m[0].length);
  }

  return ra.filter((n) => n.kind !== 'plain' || n.text.length > 0);
}

// ---------------------------------------------------------------------------
// Mô hình khối
// ---------------------------------------------------------------------------

export interface HeadingBlock {
  kind: 'heading';
  level: number;
  /** Neo để mục lục nhảy tới — sinh bằng {@link taoNeo}. */
  id: string;
  inline: InlineNode[];
}
export interface ParagraphBlock {
  kind: 'paragraph';
  inline: InlineNode[];
}
export interface QuoteBlock {
  kind: 'quote';
  /** Mỗi phần tử là một đoạn trong khối trích dẫn. */
  paragraphs: InlineNode[][];
}
export interface ListBlock {
  kind: 'list';
  ordered: boolean;
  items: InlineNode[][];
}
export interface TableBlock {
  kind: 'table';
  head: InlineNode[][];
  rows: InlineNode[][][];
}
export interface CodeBlock {
  kind: 'code';
  text: string;
}
export interface RuleBlock {
  kind: 'rule';
}
/**
 * Thẻ khai **màn hình mà mục này hướng dẫn** — `<!-- man-hinh: /van-hanh/cong-trinh -->`.
 *
 * <h3>⭐ Một thẻ phục vụ BA việc, và đó là lý do nó khai ĐƯỜNG DẪN chứ ⛔ khai mã quyền</h3>
 *
 * 1. **Lọc theo vai trò** — đường dẫn tra ra mã quyền qua `MENU`, mã quyền tra ra vai trò.
 * 2. **Nút `?` trên thanh tiêu đề** — đường dẫn hiện tại tra ngược ra mục hướng dẫn.
 * 3. **Bộ canh phủ** — mọi màn hình trong `MENU` phải được ít nhất một mục nhắc tới.
 *
 * Khai mã quyền thì việc (2) ⛔ làm được, và hai màn hình dùng chung một mã quyền sẽ trỏ về cùng
 * một mục — `MENU` có **51** màn hình trên **33** mã quyền, tức chuyện ấy xảy ra thật.
 *
 * <h3>⚠ Vì sao là chú thích HTML</h3>
 *
 * Nó **vô hình ở cả hai nơi**: GitHub ⛔ hiện chú thích, và bộ đọc này nuốt nó thay vì in ra. Một
 * thẻ nhìn thấy được (kiểu *"Quyền: ops:construction:view"*) là rác trên mặt một tài liệu viết cho
 * người ⛔ rành kỹ thuật — chính đối tượng của trang này.
 */
export interface DirectiveBlock {
  kind: 'directive';
  key: string;
  values: string[];
}

/** Dòng bộ đọc ⛔ hiểu. Tồn tại để `phamViMarkdown.test.ts` bắt được, ⛔ phải để hiển thị. */
export interface UnknownBlock {
  kind: 'unknown';
  line: string;
  lineNumber: number;
}

export type Block =
  | HeadingBlock
  | ParagraphBlock
  | QuoteBlock
  | ListBlock
  | TableBlock
  | CodeBlock
  | RuleBlock
  | DirectiveBlock
  | UnknownBlock;

/**
 * Neo của một tiêu đề — phải khớp **đúng** cách GitHub sinh neo, vì mục lục trong chính tài
 * liệu viết theo kiểu ấy (`[1](#1-hệ-thống-gồm-những-gì)`) và tài liệu còn được đọc trên
 * GitHub. Hai cách sinh neo khác nhau là mục lục bấm ⛔ ra gì ở đúng một trong hai nơi.
 *
 * ⚠ Giữ nguyên chữ có dấu: neo tiếng Việt hoạt động bình thường trên `id` của HTML.
 */
export function taoNeo(chu: string): string {
  return (
    chu
      .toLowerCase()
      .replace(/[—–·`*[\]()]/g, '')
      .replace(/[^\p{L}\p{N}\s-]/gu, '')
      .trim()
      // ⚠⚠ `\s` MỘT ký tự, ⛔ phải `\s+`. GitHub bỏ dấu câu rồi đổi **từng** khoảng trắng thành một
      //   gạch, nên `Sao lưu & khôi phục` ra `sao-lưu--khôi-phục` — **hai** gạch. Gộp `\s+` cho ra
      //   một gạch, tức neo của trang và neo của GitHub lệch nhau ở đúng những tiêu đề mang `&` hoặc
      //   `—`, và mục lục bấm được ở một nơi thì gãy ở nơi kia. Lộ ra 20/09 khi một bài kiểm đoán
      //   `9-6-sao-lưu--khôi-phục` còn tài liệu sinh ra `96-sao-lưu-khôi-phục`.
      .replace(/\s/g, '-')
  );
}

/** `<!-- khoa: a, b -->` — chú thích HTML một dòng mang dữ liệu. */
const RE_DIRECTIVE = /^<!--\s*([a-z-]+)\s*:\s*(.*?)\s*-->$/;
const RE_HEADING = /^(#{1,6})\s+(.*)$/;
const RE_UL = /^-\s+(.*)$/;
const RE_OL = /^\d+\.\s+(.*)$/;
const RE_QUOTE = /^>\s?(.*)$/;
const RE_RULE = /^-{3,}$/;
const RE_TABLE_ROW = /^\|(.*)\|\s*$/;
/** Dòng phân cách đầu bảng: `|---|---|` hoặc `|:--|--:|`. */
const RE_TABLE_SEP = /^\|[\s:|-]+\|\s*$/;

function oBang(dong: string): string[] {
  const m = RE_TABLE_ROW.exec(dong);
  // `split('|')` trên phần thân — tài liệu ⛔ dùng ký tự thoát `\|` (đo 20/09), và nếu một
  // ngày nó dùng thì ô sẽ vỡ đôi, `phamViMarkdown` bắt bằng vế "số ô mỗi dòng phải khớp đầu bảng".
  return (m ? m[1] : dong).split('|').map((o) => o.trim());
}

/**
 * Phân tích cả tài liệu thành danh sách khối.
 *
 * ⚠ Đoạn văn nối **nhiều dòng liền nhau** thành một đoạn — tài liệu ngắt dòng thủ công ở cột
 * ~95 để diff dễ đọc, nên render từng dòng thành một `<p>` sẽ cho ra một trang toàn đoạn cụt.
 */
export function docTaiLieu(nguon: string): Block[] {
  const dongs = nguon.replace(/\r\n/g, '\n').split('\n');
  const khoi: Block[] = [];
  let i = 0;

  while (i < dongs.length) {
    const dong = dongs[i];

    if (dong.trim() === '') {
      i += 1;
      continue;
    }

    // --- khối mã ```
    if (dong.startsWith('```')) {
      const than: string[] = [];
      i += 1;
      while (i < dongs.length && !dongs[i].startsWith('```')) {
        than.push(dongs[i]);
        i += 1;
      }
      i += 1; // bỏ dấu đóng
      khoi.push({ kind: 'code', text: than.join('\n') });
      continue;
    }

    // --- thẻ khai dữ liệu. Xét TRƯỚC mọi thứ khác: ⛔ thì nó rơi xuống nhánh đoạn văn và
    //     **in nguyên văn ra màn hình** cho người dùng đọc.
    const chiThi = RE_DIRECTIVE.exec(dong.trim());
    if (chiThi) {
      khoi.push({
        kind: 'directive',
        key: chiThi[1],
        // ⚠ Lọc rỗng: `man-hinh:` bỏ trống cho mảng `['']` chứ ⛔ phải mảng rỗng, và một đường
        //   dẫn rỗng sẽ khớp với mọi thứ ở bước tra ngược.
        values: chiThi[2]
          .split(',')
          .map((v) => v.trim())
          .filter((v) => v.length > 0),
      });
      i += 1;
      continue;
    }

    // --- đường kẻ ngang (xét TRƯỚC danh sách: `---` cũng bắt đầu bằng `-`)
    if (RE_RULE.test(dong.trim())) {
      khoi.push({ kind: 'rule' });
      i += 1;
      continue;
    }

    // --- tiêu đề
    const heading = RE_HEADING.exec(dong);
    if (heading) {
      khoi.push({
        kind: 'heading',
        level: heading[1].length,
        id: taoNeo(heading[2]),
        inline: bocInline(heading[2]),
      });
      i += 1;
      continue;
    }

    // --- bảng: dòng `|…|` kèm dòng phân cách ngay dưới
    if (RE_TABLE_ROW.test(dong) && i + 1 < dongs.length && RE_TABLE_SEP.test(dongs[i + 1])) {
      const head = oBang(dong).map(bocInline);
      i += 2;
      const rows: InlineNode[][][] = [];
      while (i < dongs.length && RE_TABLE_ROW.test(dongs[i])) {
        rows.push(oBang(dongs[i]).map(bocInline));
        i += 1;
      }
      khoi.push({ kind: 'table', head, rows });
      continue;
    }

    // --- trích dẫn: gom các dòng `>` liền nhau, dòng `>` rỗng ngắt đoạn
    if (RE_QUOTE.test(dong)) {
      const doan: string[][] = [[]];
      while (i < dongs.length && RE_QUOTE.test(dongs[i])) {
        const noi = RE_QUOTE.exec(dongs[i])![1];
        if (noi.trim() === '') {
          doan.push([]);
        } else {
          doan[doan.length - 1].push(noi.trim());
        }
        i += 1;
      }
      khoi.push({
        kind: 'quote',
        paragraphs: doan.filter((d) => d.length > 0).map((d) => bocInline(d.join(' '))),
      });
      continue;
    }

    // --- danh sách: mục mới bắt đầu bằng `- ` / `1. `, dòng thụt lề là phần nối tiếp
    if (RE_UL.test(dong) || RE_OL.test(dong)) {
      const ordered = RE_OL.test(dong);
      const items: string[][] = [];
      while (i < dongs.length) {
        const d = dongs[i];
        const m = ordered ? RE_OL.exec(d) : RE_UL.exec(d);
        if (m) {
          items.push([m[1]]);
        } else if (/^\s+\S/.test(d) && items.length > 0) {
          items[items.length - 1].push(d.trim());
        } else {
          break;
        }
        i += 1;
      }
      khoi.push({ kind: 'list', ordered, items: items.map((d) => bocInline(d.join(' '))) });
      continue;
    }

    // --- đoạn văn: nối tới khi gặp dòng trống hoặc một khối khác mở ra
    const than: string[] = [];
    let la: UnknownBlock | null = null;
    while (i < dongs.length) {
      const d = dongs[i];
      if (
        d.trim() === '' ||
        d.startsWith('```') ||
        RE_DIRECTIVE.test(d.trim()) ||
        RE_HEADING.test(d) ||
        RE_QUOTE.test(d) ||
        RE_UL.test(d) ||
        RE_OL.test(d) ||
        RE_RULE.test(d.trim()) ||
        RE_TABLE_ROW.test(d)
      ) {
        // ⚠ Một dòng `|…|` LẺ LOI (⛔ có dòng phân cách) ⛔ phải một đoạn văn — nó là một bảng
        //   viết hỏng. Khai thành `unknown` để bộ canh gọi tên, thay vì in ra đầy dấu `|`.
        if (RE_TABLE_ROW.test(d)) {
          la = { kind: 'unknown', line: d, lineNumber: i + 1 };
          i += 1;
        }
        break;
      }
      than.push(d.trim());
      i += 1;
    }
    if (than.length > 0) {
      khoi.push({ kind: 'paragraph', inline: bocInline(than.join(' ')) });
    }
    if (la) {
      khoi.push(la);
    }
  }

  return khoi;
}

/** Mục lục sinh từ chính các tiêu đề — dùng cho thanh điều hướng bên phải. */
export interface MucLuc {
  id: string;
  title: string;
  level: number;
}

/** Chữ thuần của một dãy inline — dùng cho mục lục và cho bộ canh. Đệ quy xuống `children`. */
export function chuThuan(nodes: readonly InlineNode[]): string {
  return nodes
    .map((n) => (n.kind === 'plain' || n.kind === 'code' ? n.text : chuThuan(n.children)))
    .join('');
}

export function lapMucLuc(khoi: readonly Block[], levels: readonly number[] = [2]): MucLuc[] {
  return khoi
    .filter((b): b is HeadingBlock => b.kind === 'heading' && levels.includes(b.level))
    .map((b) => ({ id: b.id, title: chuThuan(b.inline), level: b.level }));
}
