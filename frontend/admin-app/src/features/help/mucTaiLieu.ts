import { MENU, type MenuNode } from '@/app/menu';
import { boDau, tachTu } from '@/shared/boDau';

import { chuThuan, type Block } from './markdown';

/**
 * Cắt tài liệu thành **mục tra cứu được** và nối mỗi mục với màn hình thật của hệ thống.
 *
 * <h2>⛔⛔ Vế "vai trò nào dùng được mục này" KHÔNG được viết tay</h2>
 *
 * Tài liệu chỉ khai **đường dẫn màn hình** (`<!-- man-hinh: /van-hanh/cong-trinh -->`). Mã quyền
 * suy ra từ `MENU`, tức từ **cùng một nơi** mà thanh điều hướng và route guard đang đọc. Chép một
 * bảng vai-trò-↔-mục vào tài liệu là dựng bản sao thứ hai của ma trận phân quyền, mà ma trận ấy
 * **Công ty tự sửa được** (`RolesPage`, T27.31) ⇒ bản chép sai ngay ngày họ chỉnh ô đầu tiên, và
 * sai **im lặng**.
 *
 * <h2>⚠ Mục ⛔ khai `man-hinh` là mục CHUNG — và đó là trạng thái mặc định đúng</h2>
 *
 * Đăng nhập, vai trò, xử lý sự cố, những ô đang trống: chúng đúng với **mọi** vai trò. Bắt mọi mục
 * phải khai một màn hình sẽ đẩy người viết tới chỗ gắn bừa một đường dẫn cho có — rồi bộ lọc giấu
 * mất phần hướng dẫn đăng nhập của đúng người đang ⛔ đăng nhập được.
 */

export interface MucTaiLieu {
  /** Neo của tiêu đề — dùng cho mục lục, cho liên kết sâu và cho nút `?`. */
  id: string;
  tieuDe: string;
  /** 2 = phần lớn, 3 = mục con. */
  level: number;
  /** Gồm CẢ khối tiêu đề, để dựng lại nguyên vẹn khi hiển thị. */
  blocks: Block[];
  /** Đường dẫn màn hình mục này hướng dẫn. Rỗng = mục chung. */
  duongDan: string[];
  /** Mã quyền suy từ `MENU`. Rỗng = ⛔ đòi quyền nào (mục chung, hoặc màn hình ai cũng vào được). */
  quyen: string[];
  /** Toàn văn đã bỏ dấu + hạ chữ thường — chỉ mục tìm kiếm. */
  chiMuc: string;
}

/** Một phần lớn kèm các mục con của nó. */
export interface NhomMuc {
  muc: MucTaiLieu;
  con: MucTaiLieu[];
}

// ---------------------------------------------------------------------------
// Tra cứu MENU
// ---------------------------------------------------------------------------

function duyet(nodes: readonly MenuNode[], nhan: (n: MenuNode) => void): void {
  for (const n of nodes) {
    nhan(n);
    if (n.children) {
      duyet(n.children, nhan);
    }
  }
}

/** `đường dẫn → { nhãn, quyền }` của mọi màn hình bấm được trong `MENU`. */
export const MAN_HINH = (() => {
  const ra = new Map<string, { nhan: string; quyen: readonly string[] }>();
  duyet(MENU, (n) => {
    if (n.path) {
      ra.set(n.path, { nhan: n.label, quyen: n.permissions ?? [] });
    }
  });
  return ra;
})();

// ---------------------------------------------------------------------------
// Cắt mục
// ---------------------------------------------------------------------------

/**
 * Cắt danh sách khối thành các mục, mỗi mục bắt đầu ở một tiêu đề cấp 2 hoặc 3.
 *
 * ⚠ Khối nằm TRƯỚC tiêu đề cấp 2 đầu tiên (tiêu đề cấp 1 + lời mở) ⛔ thuộc mục nào — trả riêng
 * ở `dauTrang` để trang luôn dựng chúng, ⛔ kể bộ lọc đang bật gì.
 */
export function catMuc(khoi: readonly Block[]): { dauTrang: Block[]; nhom: NhomMuc[] } {
  const dauTrang: Block[] = [];
  const nhom: NhomMuc[] = [];
  let hienTai: MucTaiLieu | null = null;

  const chot = (): void => {
    if (!hienTai) {
      return;
    }
    if (hienTai.level === 2) {
      nhom.push({ muc: hienTai, con: [] });
    } else if (nhom.length > 0) {
      nhom[nhom.length - 1].con.push(hienTai);
    } else {
      // Tiêu đề cấp 3 trước cấp 2 nào — ⛔ có trong tài liệu, nhưng nuốt nó đi thì một mục biến
      // mất mà ⛔ ai biết (quy tắc 16). Nâng nó thành một nhóm riêng.
      nhom.push({ muc: hienTai, con: [] });
    }
    hienTai = null;
  };

  for (const b of khoi) {
    if (b.kind === 'heading' && (b.level === 2 || b.level === 3)) {
      chot();
      hienTai = {
        id: b.id,
        tieuDe: chuThuan(b.inline),
        level: b.level,
        blocks: [b],
        duongDan: [],
        quyen: [],
        chiMuc: '',
      };
      continue;
    }
    if (!hienTai) {
      dauTrang.push(b);
      continue;
    }
    if (b.kind === 'directive' && b.key === 'man-hinh') {
      hienTai.duongDan.push(...b.values);
      continue; // ⛔ đưa vào `blocks`: thẻ khai là dữ liệu, ⛔ phải nội dung đọc được
    }
    hienTai.blocks.push(b);
  }
  chot();

  for (const n of nhom) {
    hoanThien(n.muc);
    n.con.forEach(hoanThien);
    // ⭐ Phần lớn THỪA HƯỞNG màn hình của các mục con: ⛔ thì một phần mà mọi mục con đều thuộc
    //   vai trò khác vẫn hiện ra với mỗi cái tiêu đề trống trơn.
    const tuCon = n.con.flatMap((c) => c.duongDan);
    n.muc.duongDan = [...new Set([...n.muc.duongDan, ...tuCon])];
    n.muc.quyen = [...new Set([...n.muc.quyen, ...n.con.flatMap((c) => c.quyen)])];
  }

  return { dauTrang, nhom };
}

function hoanThien(m: MucTaiLieu): void {
  m.quyen = [...new Set(m.duongDan.flatMap((d) => [...(MAN_HINH.get(d)?.quyen ?? [])]))];
  // Chỉ mục gồm cả nhãn màn hình và mã quyền: gõ `ops:report` hay `Báo cáo vận hành` đều ra.
  const nhanManHinh = m.duongDan.map((d) => MAN_HINH.get(d)?.nhan ?? '').join(' ');
  m.chiMuc = boDau(
    [m.tieuDe, vanBanCuaKhoi(m.blocks), nhanManHinh, m.duongDan.join(' '), m.quyen.join(' ')]
      .join(' ')
      .normalize('NFC'),
  ).toLowerCase();
}

/** Toàn bộ chữ người dùng đọc được trong một dãy khối — ⛔ gồm thẻ khai. */
export function vanBanCuaKhoi(khoi: readonly Block[]): string {
  const phan: string[] = [];
  for (const b of khoi) {
    switch (b.kind) {
      case 'heading':
      case 'paragraph':
        phan.push(chuThuan(b.inline));
        break;
      case 'quote':
        b.paragraphs.forEach((p) => phan.push(chuThuan(p)));
        break;
      case 'list':
        b.items.forEach((it) => phan.push(chuThuan(it)));
        break;
      case 'table':
        b.head.forEach((o) => phan.push(chuThuan(o)));
        b.rows.forEach((h) => h.forEach((o) => phan.push(chuThuan(o))));
        break;
      case 'code':
        phan.push(b.text);
        break;
      default:
        break;
    }
  }
  return phan.join(' ');
}

// ---------------------------------------------------------------------------
// Lọc
// ---------------------------------------------------------------------------

/**
 * Mục này có thuộc bộ quyền đang chọn ⛔?
 *
 * ⚠ Ba trạng thái, ⛔ phải hai: **mục chung** (⛔ khai màn hình) luôn hợp · **màn hình ⛔ đòi
 * quyền** (Tổng quan, Hộp thư) luôn hợp · còn lại đòi **ít nhất một** mã quyền giao nhau, đúng
 * phép `.some` mà `visibleMenu` và `RequirePermission` dùng.
 */
export function hopVaiTro(m: MucTaiLieu, quyen: ReadonlySet<string>): boolean {
  if (m.duongDan.length === 0) {
    return true;
  }
  return m.duongDan.some((d) => {
    const can = MAN_HINH.get(d)?.quyen ?? [];
    return can.length === 0 || can.some((c) => quyen.has(c));
  });
}

/**
 * Mục có khớp ô tìm kiếm ⛔ — đòi **mọi từ** đều có mặt, ⛔ đòi nguyên cụm.
 *
 * ⚠ Bản đầu so chuỗi con và trượt ngay ở ca thường gặp nhất: `nguong canh bao` ⛔ ra
 * *"Ngưỡng **và** cảnh báo"*. `chiMuc` đã bỏ dấu và hạ chữ thường sẵn từ {@link catMuc}.
 */
export function hopTuKhoa(m: MucTaiLieu, tuKhoa: string): boolean {
  return tachTu(tuKhoa).every((t) => m.chiMuc.includes(t));
}

/**
 * Mục hướng dẫn cho một đường dẫn màn hình — nguồn của nút `?` trên thanh tiêu đề.
 *
 * ⚠ So theo **tiền tố có phân đoạn**: `/van-hanh/cong-trinh/abc-123` (trang chi tiết) phải tra ra
 * mục của `/van-hanh/cong-trinh`. So bằng `startsWith` trần thì `/van-hanh/cong-trinh-khac` cũng
 * khớp — chọn đoạn khớp **dài nhất**, cùng luật `findMenuKey` đang dùng.
 */
export function mucChoDuongDan(nhom: readonly NhomMuc[], duongDan: string): MucTaiLieu | undefined {
  let tot: { m: MucTaiLieu; dai: number } | undefined;
  for (const n of nhom) {
    for (const m of [n.muc, ...n.con]) {
      for (const d of m.duongDan) {
        if ((duongDan === d || duongDan.startsWith(`${d}/`)) && (!tot || d.length > tot.dai)) {
          tot = { m, dai: d.length };
        }
      }
    }
  }
  return tot?.m;
}
