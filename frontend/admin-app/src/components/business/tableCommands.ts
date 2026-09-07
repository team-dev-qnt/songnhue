/**
 * **Ràng buộc và trạng thái của bảng trong trình soạn thảo** — WS-41 (T41.5, T41.6).
 *
 * <h3>Vì sao là tệp `.ts` riêng</h3>
 *
 * Hai lý do, và cả hai đều bắt buộc:
 *
 * <ul>
 *   <li>ESLint `react-refresh/only-export-components` ở mức <b>lỗi</b> với `--max-warnings=0`, và
 *       `allowConstantExport` không cho `ArrayExpression` — nên một tệp `.tsx` xuất component
 *       <b>không được</b> xuất kèm hằng mảng.
 *   <li>Phần quyết định phải <b>kiểm được headless</b>. jsdom 29.1.1 không có `elementFromPoint`
 *       nên không đặt được con trỏ vào ô bảng bằng chuột; mọi bài kiểm phải lái bằng <b>lệnh</b>.
 *       Để phép so nằm lẫn trong component thì không bài kiểm nào chạm tới được nó — cùng lý do
 *       đã tách `lib/vuaThanhNgang.ts` ở public-web (T25.31).
 * </ul>
 */

/**
 * Trần của **lưới rê chuột**.
 *
 * ⚠ Đây là trần của một cách chọn, **không** phải trần của tính năng: ô nhập số đi tới
 * {@link TRAN_HANG_SO}×{@link TRAN_COT_SO}. Lẫn hai thứ này là dựng lại đúng khiếu nại của
 * QuanTran — *"chỉ cho phép insert 3x3"* — chỉ đổi con số từ 3 thành 10.
 */
export const TRAN_HANG_LUOI = 8;
export const TRAN_COT_LUOI = 10;

/**
 * Trần của **ô nhập số**.
 *
 * ⛔ Đây là ràng buộc **giao diện**, không phải tham số nghiệp vụ ⇒ **không** seed vào bảng
 * `settings`. Luật 12 của `CLAUDE.md` nêu đích danh *"giới hạn số lượng"* là thứ phải để trong
 * `settings` — nhưng nó nói về giới hạn **nghiệp vụ** (số tệp đính kèm, dung lượng, retention).
 * Con số ở đây trả lời một câu hỏi khác: *bao nhiêu cột thì bảng còn dùng được trên màn hình
 * này*. Nó phái sinh từ `TABLE_CELL_MIN_WIDTH_PX` và bề ngang khung soạn thảo, không phải từ
 * một quyết định của Công ty; đưa vào `settings` là mời người vận hành đặt một con số họ không
 * có thông tin để đặt đúng (cùng lý lẽ với `TRAN_PHUC_VU_CONG_KHAI_MB` ở T40.19).
 *
 * ⚠ 15 cột × 80px = 1200px — đã vượt khung soạn thảo, nên bảng sẽ cuộn ngang. Đó là chủ ý:
 * bảng tiến độ sản xuất theo tháng cần **13 cột** (CR-30), và chặn ở 10 là chặn đúng việc thật.
 */
export const TRAN_HANG_SO = 30;
export const TRAN_COT_SO = 15;

export interface KichThuocBang {
  hang: number;
  cot: number;
}

/**
 * Các lệnh sửa cấu trúc bảng mà thanh ngữ cảnh lộ ra.
 *
 * ⭐ Tên phải là tên **THẬT** trong `editor.commands` — `tableCommands.test.ts` đối chiếu từng cái
 * với schema thật. Đây là bài học `AlignClass` đã trả giá: bản đầu của nó khai `'image'`/`'figure'`
 * (hai tên không tồn tại), TipTap **bỏ qua lặng lẽ**, và nút sáng lên như đã làm xong việc.
 *
 * <h3>⛔ Ba lệnh CÓ THẬT nhưng cố ý KHÔNG lộ ra</h3>
 *
 * <ul>
 *   <li>{@code fixTables} — **no-op thật** ở 3.31.0: `prosemirror-tables` trả về một Transaction
 *       và TipTap **vứt nó đi**. Một nút gọi nó là một nút không bao giờ làm gì.
 *   <li>{@code setCellSelection} — {@code return true} vô điều kiện, không dùng để bật/tắt nút được,
 *       và người dùng chọn ô bằng chuột chứ không bằng nút.
 *   <li>{@code setCellAttribute('align', …)} — phát {@code style="text-align:…"}, mà `HtmlSanitizer`
 *       gỡ {@code style}. Nút bấm được, thấy tác dụng ngay, lưu thành công, rồi **mất**. Muốn có
 *       căn lề trong ô thì phải đi bằng **class** như `ALIGN_CLASSES` — xem T41.15.
 * </ul>
 */
export const LENH_BANG = [
  'addRowBefore',
  'addRowAfter',
  'deleteRow',
  'addColumnBefore',
  'addColumnAfter',
  'deleteColumn',
  'mergeCells',
  'splitCell',
  'toggleHeaderRow',
  'toggleHeaderColumn',
  'deleteTable',
] as const;

export type LenhBang = (typeof LENH_BANG)[number];

export interface TrangThaiBang {
  /** Lệnh nào đang chạy được — đọc từ `editor.can()`. */
  chayDuoc: Record<LenhBang, boolean>;
  /** Hàng đầu tiên có phải hàng tiêu đề không — tính từ HÌNH HỌC, xem `trangThaiBang`. */
  hangTieuDe: boolean;
  /** Cột đầu tiên có phải cột tiêu đề không. */
  cotTieuDe: boolean;
}

/**
 * ⛔⛔ Hai lệnh mà `can()` **NÓI DỐI** — giữ nút BẬT và đọc kết quả `run()` thay vì hỏi trước.
 *
 * Đã đọc `prosemirror-tables`: chốt chặn của `deleteRow`/`deleteColumn` nằm **bên trong**
 * `if (dispatch) { … }`:
 *
 * <pre>
 *   deleteRow: if (rect.top === 0 && rect.bottom === map.height) return false
 * </pre>
 *
 * `can()` chạy với `dispatch` rỗng ⇒ nhánh ấy không bao giờ được vào ⇒ **luôn trả `true`**.
 *
 * Hệ quả nếu tin `can()`: bảng còn một hàng (rất thường — xoá dần thân bảng, hoặc bảng chỉ có
 * hàng tiêu đề) thì nút "Xoá hàng" **sáng**, bấm, **không có gì xảy ra**, không thông báo.
 *
 * Cách đúng: chạy lệnh, và nếu `run()` trả `false` thì nói ra **việc phải làm**.
 */
export const LENH_CAN_NOI_DOI: readonly LenhBang[] = ['deleteRow', 'deleteColumn'];

/**
 * Kích thước có hợp lệ không.
 *
 * ⚠ Trả **câu lý do** chứ không trả `boolean`: nơi gọi cần nói cho người dùng biết *phải làm gì*,
 * và một `false` trần buộc mỗi nơi gọi tự bịa lại câu ấy. `null` = hợp lệ.
 */
export function lyDoKichThuocSai({ hang, cot }: KichThuocBang): string | null {
  if (!Number.isInteger(hang) || !Number.isInteger(cot)) {
    return 'Số hàng và số cột phải là số nguyên';
  }
  if (hang < 1 || cot < 1) {
    return 'Bảng phải có ít nhất 1 hàng và 1 cột';
  }
  if (hang > TRAN_HANG_SO) {
    return `Tối đa ${TRAN_HANG_SO} hàng — bảng dài hơn thì nên tách thành nhiều bảng`;
  }
  if (cot > TRAN_COT_SO) {
    return `Tối đa ${TRAN_COT_SO} cột — bảng rộng hơn sẽ phải cuộn ngang rất nhiều trên cổng`;
  }
  return null;
}

/**
 * Trạng thái bảng tại vị trí con trỏ — `null` khi con trỏ **không** ở trong bảng nào.
 *
 * ⚠ Hàm **thuần và xuất khẩu**, không nằm lẫn trong component: jsdom không đặt được con trỏ vào ô
 * bảng bằng chuột (`elementFromPoint` không tồn tại), nên bài kiểm phải lái bằng **lệnh** và gọi
 * thẳng hàm này. Để phép quyết định nằm trong JSX thì **không bài kiểm nào chạm tới được nó**, và
 * một dấu `!` viết nhầm sẽ sống mãi — cùng lý do đã tách `lib/vuaThanhNgang.ts` ở public-web.
 *
 * ⛔ **Không** có trường `trongBang`: nó sẽ luôn `true` ở mọi thể hiện khác `null` — một trường có
 * người ghi mà không thể có người đọc (quy tắc 15). `null` đã mang trọn thông tin ấy.
 */
export function trangThaiBang(editor: EditorNhu | null): TrangThaiBang | null {
  if (!editor || !editor.isActive('table')) {
    return null;
  }

  // ⚠ Gọi `can()` **một lần** rồi lấy từng lệnh: mỗi lượt `editor.can()` dựng một chuỗi lệnh mới
  //   trên một bản sao state. Gọi 11 lần cho 11 nút là 11 lượt dựng cho một lượt vẽ.
  const can = editor.can() as unknown as Record<LenhBang, () => boolean>;
  const chayDuoc = Object.fromEntries(
    LENH_BANG.map((lenh) => [lenh, Boolean(can[lenh]?.())]),
  ) as Record<LenhBang, boolean>;

  return { chayDuoc, ...tieuDeTheoHinhHoc(editor) };
}

/**
 * Hàng/cột đầu có phải tiêu đề không — tính từ **hình học của bảng**.
 *
 * ⛔⛔ **Không** dùng `editor.isActive('tableHeader')`: nó trả lời *"con trỏ có đang ở trong một ô
 * tiêu đề không"*, một câu hỏi khác hẳn. Con trỏ ở hàng 5 của một bảng **có** hàng tiêu đề sẽ cho
 * `false`, nút hiện là "tắt", và cú bấm kế tiếp **xoá mất hàng tiêu đề đang có** — người dùng bấm
 * một nút họ tưởng là "bật", và nó tắt.
 */
function tieuDeTheoHinhHoc(editor: EditorNhu): { hangTieuDe: boolean; cotTieuDe: boolean } {
  const bang = timBangChua(editor);
  if (!bang) {
    return { hangTieuDe: false, cotTieuDe: false };
  }

  const hangDau = bang.firstChild;
  const hangTieuDe = hangDau !== null && hangDau.childCount > 0 && moiOLaTieuDe(hangDau);

  let cotTieuDe = bang.childCount > 0;
  bang.forEach((hang) => {
    const oDau = hang.firstChild;
    if (!oDau || oDau.type.name !== 'tableHeader') {
      cotTieuDe = false;
    }
  });

  return { hangTieuDe, cotTieuDe };
}

function moiOLaTieuDe(hang: NutNhu): boolean {
  let du = true;
  hang.forEach((o) => {
    if (o.type.name !== 'tableHeader') {
      du = false;
    }
  });
  return du;
}

/** Nút `table` gần nhất bao con trỏ. */
function timBangChua(editor: EditorNhu): NutNhu | null {
  const { $from } = editor.state.selection;
  for (let d = $from.depth; d > 0; d -= 1) {
    const nut = $from.node(d);
    if (nut.type.name === 'table') {
      return nut;
    }
  }
  return null;
}

/**
 * Bề mặt tối thiểu của `Editor` mà tệp này cần.
 *
 * ⚠ Khai hẹp thay vì nhận `Editor` đầy đủ để bài kiểm dựng được một bản giả — nhưng bài kiểm thật
 * vẫn truyền `Editor` thật (một bản giả tự viết sẽ **chép lại giả định của tôi** thay vì kiểm nó,
 * đúng bẫy quy tắc 29). Kiểu hẹp ở đây chỉ để tệp `.ts` này không phải kéo cả cây kiểu của TipTap.
 */
interface EditorNhu {
  isActive(ten: string): boolean;
  can(): unknown;
  state: { selection: { $from: { depth: number; node(d: number): NutNhu } } };
}

interface NutNhu {
  type: { name: string };
  childCount: number;
  firstChild: NutNhu | null;
  forEach(f: (con: NutNhu) => void): void;
}
