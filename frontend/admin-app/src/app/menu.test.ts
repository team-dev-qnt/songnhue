import { describe, expect, it } from 'vitest';

import { isRestoreVisible } from '@/features/admin/restoreAccess';

import { MENU, findMenuKey, visibleMenu } from './menu';

/** Người dùng chỉ có đúng những quyền liệt kê. */
function checker(...codes: string[]) {
  const owned = new Set(codes);
  return (code: string) => owned.has(code);
}

function labels(nodes: ReturnType<typeof visibleMenu>): string[] {
  return nodes.flatMap((node) => [node.label, ...(node.children ? labels(node.children) : [])]);
}

/** Chỉ mục bấm được — nhóm cha không dẫn tới màn hình nào nên không tính vào đây. */
function leafLabels(nodes: ReturnType<typeof visibleMenu>): string[] {
  return nodes.flatMap((node) => (node.children ? leafLabels(node.children) : [node.label]));
}

describe('menu ẩn/hiện theo quyền — điều kiện nghiệm thu WS-8', () => {
  it('tài khoản chỉ xem tài khoản thì không thấy các màn hình quản trị khác', () => {
    const visible = labels(visibleMenu(MENU, checker('adm:user:view')));

    expect(visible).toContain('Tài khoản');
    expect(visible).not.toContain('Sao lưu & khôi phục');
    expect(visible).not.toContain('Nhật ký kiểm toán');
    expect(visible).not.toContain('Cấu hình hệ thống');
  });

  it('nhóm cha rỗng thì biến mất luôn, không để lại mục bấm vào trống rỗng', () => {
    const visible = visibleMenu(MENU, checker());
    expect(visible.map((node) => node.label)).not.toContain('Quản trị hệ thống');
  });

  it('mục không đòi quyền vẫn hiện với mọi tài khoản đã đăng nhập', () => {
    const visible = labels(visibleMenu(MENU, checker()));
    expect(visible).toContain('Tổng quan');
    expect(visible).toContain('Hộp thư');
  });

  it('có đủ quyền thì thấy toàn bộ màn hình quản trị', () => {
    const all = checker(
      'adm:user:view',
      'adm:role:view',
      'adm:org-unit:view',
      'adm:setting:view',
      'adm:audit:view',
      'adm:backup:view',
      'adm:health:view',
      'adm:notification:broadcast',
    );
    // 8 màn hình quản trị + Tổng quan + Hộp thư + Phiên đăng nhập.
    // ⚠ `all` chỉ cấp quyền `adm:*` nên nhóm "Dữ liệu thuỷ văn" (WS-28) không nằm trong số này —
    //    đó chính là điều bài kiểm ngay dưới khẳng định.
    expect(leafLabels(visibleMenu(MENU, all))).toHaveLength(11);
  });
});

/**
 * Nhóm "Dữ liệu thuỷ văn" (WS-28) — canh đúng hình dạng lỗi §10.36.
 *
 * `Nguồn dữ liệu` đứng sau `hyd:api-source:manage`, một quyền mà **chỉ SUPER_ADMIN có**. Nếu ai đó
 * gộp cả nhóm về `hyd:station:view` cho gọn thì màn hình cấu hình mã số nguồn hiện ra với cán bộ Xí
 * nghiệp — bấm vào là 403, và triệu chứng đọc như "hệ thống lỗi" chứ không như "bạn không có quyền".
 * Chiều ngược lại cũng phải đúng: gộp cả nhóm về `hyd:api-source:manage` thì danh mục điểm đo biến
 * mất với đúng những người dùng nó hằng ngày.
 */
describe('nhóm Dữ liệu thuỷ văn hiện theo đúng quyền của từng màn hình — WS-28', () => {
  it('người xem điểm đo thấy danh mục và loại chỉ số, KHÔNG thấy Nguồn dữ liệu', () => {
    const visible = leafLabels(visibleMenu(MENU, checker('hyd:station:view')));

    expect(visible).toContain('Danh mục điểm đo');
    expect(visible).toContain('Loại chỉ số quan trắc');
    expect(visible).not.toContain('Nguồn dữ liệu');
  });

  it('người cấu hình nguồn thấy Nguồn dữ liệu', () => {
    const visible = leafLabels(visibleMenu(MENU, checker('hyd:api-source:manage')));

    expect(visible).toContain('Nguồn dữ liệu');
    expect(visible).not.toContain('Danh mục điểm đo');
  });

  it('không có quyền thuỷ văn nào thì cả nhóm biến mất, không để lại mục trống', () => {
    const visible = visibleMenu(MENU, checker('adm:user:view'));
    expect(visible.map((node) => node.label)).not.toContain('Dữ liệu thuỷ văn');
  });

  it('đường dẫn con của màn hình điểm đo vẫn tô sáng đúng mục menu', () => {
    expect(findMenuKey(MENU, '/thuy-van/diem-do')).toBe('diem-do');
    expect(findMenuKey(MENU, '/thuy-van/nguon-du-lieu')).toBe('nguon-du-lieu');
  });
});

describe('findMenuKey', () => {
  it('chọn đường dẫn khớp dài nhất, không để "Tổng quan" sáng ở mọi màn hình', () => {
    expect(findMenuKey(MENU, '/quan-tri/sao-luu')).toBe('sao-luu');
    expect(findMenuKey(MENU, '/')).toBe('tong-quan');
  });

  it('màn hình con vẫn tô sáng mục cha của nó', () => {
    expect(findMenuKey(MENU, '/quan-tri/tai-khoan/abc')).toBe('tai-khoan');
  });
});

describe('hiện chức năng khôi phục — điều kiện nghiệm thu WS-8', () => {
  it('không phải Super Admin thì không thấy, dù môi trường có bật', () => {
    expect(isRestoreVisible(false, { restoreAvailable: true })).toBe(false);
  });

  it('là Super Admin nhưng môi trường không bật khôi phục thì cũng không thấy', () => {
    expect(isRestoreVisible(true, { restoreAvailable: false })).toBe(false);
  });

  it('chưa tải xong trạng thái thì mặc định là ẩn', () => {
    expect(isRestoreVisible(true, undefined)).toBe(false);
  });

  it('đủ cả hai vế mới hiện', () => {
    expect(isRestoreVisible(true, { restoreAvailable: true })).toBe(true);
  });
});
