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
    expect(findMenuKey(MENU, '/thuy-van/nhat-ky-dong-bo')).toBe('nhat-ky-dong-bo');
    expect(findMenuKey(MENU, '/thuy-van/ma-la')).toBe('ma-la');
  });

  /**
   * ⭐⭐ Hai màn hình chẩn đoán (T31.13) đứng sau `hyd:measurement:view`, ⛔ KHÔNG sau
   * `hyd:api-source:manage`.
   *
   * Đo trên ma trận seed: `hyd:api-source:manage` chỉ SUPER_ADMIN và ADMIN có. Gác bằng nó thì
   * TECHNICIAN — vai trò duy nhất ngoài quản trị có `hyd:station:manage`, tức đúng người sẽ đi
   * khai một mã lạ — không đọc nổi lý do vì sao số liệu không về. Đó là hình dạng T27.20 lần thứ
   * ba trong hai tuần.
   */
  it('người XEM SỐ LIỆU thấy hai màn hình chẩn đoán, KHÔNG thấy Nguồn dữ liệu', () => {
    const visible = leafLabels(visibleMenu(MENU, checker('hyd:measurement:view')));

    expect(visible).toContain('Nhật ký đồng bộ');
    expect(visible).toContain('Mã lạ từ nguồn');
    expect(visible).not.toContain('Nguồn dữ liệu');
    expect(visible).not.toContain('Danh mục điểm đo');
  });

  it('người CẤU HÌNH NGUỒN cũng thấy hai màn hình ấy — hai quyền ở chế độ HOẶC', () => {
    const visible = leafLabels(visibleMenu(MENU, checker('hyd:api-source:manage')));

    expect(visible).toContain('Nhật ký đồng bộ');
    expect(visible).toContain('Mã lạ từ nguồn');
  });

  it('⛔ Chỉ xem điểm đo thì KHÔNG thấy hai màn hình chẩn đoán — vế phân biệt', () => {
    // Thiếu vế này thì hai bài trên xanh cả khi ai đó gộp cả nhóm về một quyền duy nhất.
    const visible = leafLabels(visibleMenu(MENU, checker('hyd:station:view')));

    expect(visible).not.toContain('Nhật ký đồng bộ');
    expect(visible).not.toContain('Mã lạ từ nguồn');
  });
});

/**
 * Nhóm "Nhân sự" (WS-51) — và một điều mà bài `toHaveLength(11)` ở trên **⛔ không** nói.
 *
 * ⚠⚠ Con số 11 ấy đo trên một `checker` chỉ cấp quyền `adm:*`, nên nó **⛔ không đổi** khi thêm
 * nhóm HR — và cái xanh của nó đọc như *"menu đã được canh"*. Đúng luật 28: một bộ canh phải nói
 * ra phạm vi của chính nó, và phạm vi của bài ấy là *tám màn hình quản trị*, ⛔ không phải *toàn
 * bộ menu*. Vế thật sự canh nhóm mới là bốn bài dưới đây.
 */
describe('nhóm Nhân sự hiện theo đúng quyền của từng màn hình — WS-51', () => {
  it('người xem hồ sơ CBNV thấy CẢ danh mục chức vụ', () => {
    const visible = leafLabels(visibleMenu(MENU, checker('hr:employee:view')));

    expect(visible).toContain('Hồ sơ cán bộ');
    // ⛔ Danh mục chức vụ KHÔNG được gác hẹp hơn: nó là nguồn dữ liệu cho ô "Chức vụ" của biểu mẫu
    //    hồ sơ. Người dựng hồ sơ mà không mở được nó thì không kiểm được mã mình đang chọn (WS-28).
    expect(visible).toContain('Danh mục chức vụ');
  });

  it('⛔ quyền xem trường 🔒 MỘT MÌNH ⛔ không mở được menu nào — vế phân biệt', () => {
    // `hr:employee:view-sensitive` gác một hộp thoại BÊN TRONG trang, ⛔ không gác trang. Thiếu vế
    // này thì bài trên xanh cả khi ai đó gộp cả nhóm về mã quyền ấy — và `V202608131007:169` cấp
    // cho ADMIN mọi quyền TRỪ đúng nó, nên ADMIN sẽ mất cả nhóm menu nhân sự.
    const visible = visibleMenu(MENU, checker('hr:employee:view-sensitive'));
    expect(visible.map((node) => node.label)).not.toContain('Nhân sự');
  });

  it('không có quyền nhân sự nào thì cả nhóm biến mất, không để lại mục trống', () => {
    const visible = visibleMenu(MENU, checker('adm:user:view'));
    expect(visible.map((node) => node.label)).not.toContain('Nhân sự');
  });

  it('đường dẫn của hai màn hình nhân sự tô sáng đúng mục menu', () => {
    expect(findMenuKey(MENU, '/nhan-su/ho-so')).toBe('ho-so-cbnv');
    expect(findMenuKey(MENU, '/nhan-su/chuc-vu')).toBe('chuc-vu');
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
