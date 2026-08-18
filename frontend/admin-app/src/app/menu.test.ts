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
    expect(leafLabels(visibleMenu(MENU, all))).toHaveLength(11);
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
