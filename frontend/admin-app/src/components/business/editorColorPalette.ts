import { editorColors } from 'design-tokens';
import { CELL_BG_CLASSES, TEXT_BG_CLASSES, TEXT_COLOR_CLASSES } from 'design-tokens/editor-schema';

/**
 * **Bảng màu của thanh công cụ** — T41.15.
 *
 * ⚠ Tệp `.ts` riêng chứ ⛔ không nằm trong `RichTextEditor.tsx`: ESLint
 * `react-refresh/only-export-components` ở mức **lỗi** với `--max-warnings=0`, nên một tệp `.tsx`
 * xuất component ⛔ không được xuất thêm hàm thường. Cùng lý do đã tách `editorExtensions.ts`,
 * `AlignClass.ts`, `tableCommands.ts`.
 *
 * <h3>Vì sao ánh xạ class → màu là một HÀM chứ không phải một bảng chép tay</h3>
 *
 * Bảng chép tay là nơi thứ 19 phải nhớ khi thêm một màu. Tên class đã mang đủ thông tin
 * (`sn-fg-den` → `editorColors.fgDen`), nên phép tra suy ra được — và `editorColorPalette.test.ts`
 * chứng minh **mọi** class trong ba nhóm tra ra một màu có thật. Không có phép kiểm ấy thì một
 * class gõ sai chỉ hiện ra dưới dạng ô màu trong suốt trên thanh công cụ: trông như lựa chọn
 * *"bỏ màu"*, ⛔ không như một lỗi.
 */

/**
 * Nhãn tiếng Việt theo **sắc**, ⛔ không theo class.
 *
 * Ba nhóm dùng chung một bộ sắc, nên bảng này 8 dòng thay vì 18 — và thêm một màu vào một nhóm
 * ⛔ không đòi sửa ở đây, miễn là sắc ấy đã có tên.
 */
const NHAN_SAC: Record<string, string> = {
  den: 'Đen',
  xam: 'Xám',
  do: 'Đỏ',
  cam: 'Cam',
  luc: 'Lục',
  lam: 'Lam',
  vang: 'Vàng',
  hong: 'Hồng',
};

/** `sn-fg-den` → `editorColors.fgDen`; `sn-cell-vang` → `editorColors.cellVang`. */
export function maMauCuaClass(lop: string): string | undefined {
  const khoa = lop.replace(/^sn-/, '').replace(/-(\w)/g, (_, c: string) => c.toUpperCase());
  return (editorColors as Record<string, string>)[khoa];
}

/** `sn-cell-vang` → `'Vàng'`. */
export function nhanSacCuaClass(lop: string): string | undefined {
  return NHAN_SAC[lop.slice(lop.lastIndexOf('-') + 1)];
}

export interface NhomMau {
  /** Khoá ổn định cho `key` của React và cho bài kiểm. */
  readonly ma: 'fg' | 'bg' | 'cell';
  readonly tieuDe: string;
  readonly goiY: string;
  readonly lop: readonly string[];
}

/**
 * Ba nhóm, đúng thứ tự hiện trên thanh công cụ.
 *
 * ⚠ Nhóm `cell` chỉ bật khi con trỏ đang ở trong bảng — nút câm không kèm lý do là thứ
 * `ToolbarButton` sinh ra để tránh, nên nơi gọi truyền `lyDoTat` thay vì chỉ `disabled`.
 */
export const NHOM_MAU: readonly NhomMau[] = [
  {
    ma: 'fg',
    tieuDe: 'Màu chữ',
    goiY: 'Màu chữ — chọn vùng chữ trước',
    lop: TEXT_COLOR_CLASSES,
  },
  {
    ma: 'bg',
    tieuDe: 'Màu nền chữ',
    goiY: 'Màu nền chữ (bôi vàng) — chọn vùng chữ trước',
    lop: TEXT_BG_CLASSES,
  },
  {
    ma: 'cell',
    tieuDe: 'Màu nền ô',
    goiY: 'Màu nền ô bảng — đặt con trỏ vào một ô, hoặc quét nhiều ô',
    lop: CELL_BG_CLASSES,
  },
];
