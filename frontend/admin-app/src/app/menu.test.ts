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

  /**
   * ⭐⭐ **Hướng dẫn sử dụng phải sống sót qua một tài khoản KHÔNG có quyền nào.**
   *
   * Đây ⛔ phải một bài kiểm cho vui: một cán bộ vai trò `VIEWER` mở hệ thống lần đầu thấy menu
   * ngắn hơn hẳn đồng nghiệp, và câu hỏi đầu tiên của họ — *"vì sao tôi ⛔ thấy mục kia"* — được
   * trả lời ở §4.2 của chính tài liệu ấy. Gác nó bằng một mã quyền là đóng cửa đúng vào nhóm cần
   * nó nhất, mà triệu chứng thì **im lặng hoàn toàn**: menu vẫn dựng, chỉ thiếu một dòng.
   */
  it('⭐ Hướng dẫn sử dụng hiện cả với tài khoản ⛔ có một quyền nào', () => {
    const visible = visibleMenu(MENU, checker());

    expect(
      visible.map((node) => node.label),
      '⛔ Mục này phải ở **cấp 1**: một mục cứu hộ nằm trong nhóm con thì người đang bối rối phải ' +
        'biết mở đúng nhóm mới thấy — mà biết mở nhóm nào thì họ đã ⛔ cần tới nó.',
    ).toContain('Hướng dẫn sử dụng');
    expect(findMenuKey(MENU, '/huong-dan')).toBe('huong-dan');
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
    // 8 màn hình quản trị + Tổng quan + Hộp thư + Phiên đăng nhập + Hướng dẫn sử dụng.
    // ⚠ `all` chỉ cấp quyền `adm:*` nên nhóm "Dữ liệu thuỷ văn" (WS-28) không nằm trong số này —
    //    đó chính là điều bài kiểm ngay dưới khẳng định.
    expect(leafLabels(visibleMenu(MENU, all))).toHaveLength(12);
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

/**
 * ⛔⛔ Danh bạ (CN-04.6) và Hồ sơ CBNV (CN-04.7) gác bằng HAI quyền khác nhau — WS-55.
 *
 * Đo trên ma trận seed: `hr:directory:view` cấp cho **11/12** vai trò (gồm VIEWER, CLERK,
 * XN_OPERATOR — những người ⛔ không có một quyền `hr:employee:*` nào), còn `hr:employee:view`
 * chỉ 3. Gộp hai mục về một quyền là hỏng theo **cả hai** chiều: gác chặt thì cả Công ty mất danh
 * bạ, gác lỏng thì hồ sơ nhân sự lộ cho mọi người.
 */
describe('Danh bạ nội bộ gác bằng hr:directory:view, ⛔ không phải hr:employee:view — CN-04.6', () => {
  it('⭐ người CHỈ có hr:directory:view thấy Danh bạ và ⛔ không thấy gì khác của nhóm Nhân sự', () => {
    const visible = leafLabels(visibleMenu(MENU, checker('hr:directory:view')));

    expect(visible).toContain('Danh bạ nội bộ');
    expect(visible).not.toContain('Hồ sơ cán bộ');
    expect(visible).not.toContain('Danh mục chức vụ');
  });

  it('⛔ người chỉ có hr:employee:view ⛔ KHÔNG thấy Danh bạ — vế phân biệt', () => {
    // Thiếu vế này thì bài trên xanh cả khi ai đó gác danh bạ bằng `hr:employee:view` "cho gọn",
    // và cả Công ty mất danh bạ mà menu vẫn trông đúng với người đi rà.
    const visible = leafLabels(visibleMenu(MENU, checker('hr:employee:view')));

    expect(visible).not.toContain('Danh bạ nội bộ');
    expect(visible).toContain('Hồ sơ cán bộ');
  });

  it('đường dẫn tô sáng đúng mục', () => {
    expect(findMenuKey(MENU, '/nhan-su/danh-ba')).toBe('danh-ba');
  });
});

/**
 * ⛔⛔ "Hồ sơ của tôi" gác bằng **LIÊN KẾT**, ⛔ không bằng quyền — T51.8, CN-04.7 vế hai.
 *
 * Vế *"chính nhân viên đó"* ⛔ không biểu diễn được bằng một mã quyền: quyền gán theo **vai trò**,
 * còn đây là quan hệ giữa **một tài khoản** và **một hàng**. Bốn bài dưới đây canh đúng chỗ dễ
 * hỏng nhất — ai đó "cho gọn" bằng cách thêm `permissions: ['hr:employee:view']` sẽ khoá đúng
 * những người mục này sinh ra để phục vụ (một cán bộ vai trò VIEWER ⛔ không có quyền ấy).
 */
describe('mục "Hồ sơ của tôi" hiện theo LIÊN KẾT hồ sơ, ⛔ không theo mã quyền — T51.8', () => {
  it('⛔ ⛔ Không quyền nào + CHƯA liên kết ⇒ ⛔ không thấy', () => {
    expect(leafLabels(visibleMenu(MENU, checker(), { coHoSoNhanSu: false }))).not.toContain(
      'Hồ sơ của tôi',
    );
  });

  it('⭐ ⛔ Không quyền nào + ĐÃ liên kết ⇒ THẤY — đây là toàn bộ điểm của T51.8', () => {
    expect(leafLabels(visibleMenu(MENU, checker(), { coHoSoNhanSu: true }))).toContain(
      'Hồ sơ của tôi',
    );
  });

  it('⛔ Có ĐỦ quyền nhân sự mà CHƯA liên kết ⇒ vẫn ⛔ không thấy — vế phân biệt', () => {
    // Thiếu vế này thì hai bài trên xanh cả khi ai đó gác mục bằng `hr:employee:view` như hai mục
    // anh em của nó — và cái xanh ấy đọc như "đã canh".
    const visible = leafLabels(
      visibleMenu(MENU, checker('hr:employee:view', 'hr:employee:view-sensitive'), {
        coHoSoNhanSu: false,
      }),
    );
    expect(visible).not.toContain('Hồ sơ của tôi');
    expect(visible).toContain('Hồ sơ cán bộ');
  });

  it('⛔ Bỏ trống tham số hồ sơ ⇒ ẨN (fail-closed), ⛔ không phải hiện', () => {
    expect(leafLabels(visibleMenu(MENU, checker()))).not.toContain('Hồ sơ của tôi');
  });

  it('đường dẫn tô sáng đúng mục', () => {
    expect(findMenuKey(MENU, '/nhan-su/ho-so-cua-toi')).toBe('ho-so-cua-toi');
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

/**
 * Ba mục nghỉ phép — CN-04.9 (WS-57).
 *
 * ⛔⛔ Mỗi mục gác bằng **một loại điều kiện khác nhau**, và đó ⛔ không phải tuỳ hứng:
 *
 * - *Nghỉ phép của tôi* — **liên kết hồ sơ**. `hr:leave:request` do chốt C3 cấp cho gần như mọi vai
 *   trò nên nó ⛔ không phân biệt được ai; điều kiện thật là tài khoản có hồ sơ CBNV (T51.8).
 * - *Duyệt nghỉ phép* — **mã quyền** `hr:leave:approve`, 2/12 vai trò.
 * - *Ngày nghỉ lễ* — **chỉ cần đăng nhập**, đúng như đường đọc của backend; nút ghi tự ẩn trong
 *   trang theo `hr:contract:manage`.
 */
describe('ba mục nghỉ phép gác bằng ba loại điều kiện khác nhau — CN-04.9', () => {
  it('⭐ chưa liên kết hồ sơ ⇒ ⛔ KHÔNG thấy "Nghỉ phép của tôi", dù có hr:leave:request', () => {
    const visible = leafLabels(
      visibleMenu(MENU, checker('hr:leave:request'), { coHoSoNhanSu: false }),
    );
    expect(visible).not.toContain('Nghỉ phép của tôi');
  });

  it('⭐ đã liên kết hồ sơ ⇒ THẤY, kể cả khi ⛔ không có một mã quyền nào', () => {
    // ⛔⛔ Đây là toàn bộ điểm của T51.8 áp cho nghỉ phép: điều kiện là một QUAN HỆ giữa tài khoản
    //    và một hàng, ⛔ không phải một thuộc tính của vai trò.
    const visible = leafLabels(visibleMenu(MENU, checker(), { coHoSoNhanSu: true }));
    expect(visible).toContain('Nghỉ phép của tôi');
    expect(visible).not.toContain('Duyệt nghỉ phép');
  });

  it('⛔ ⛔ Không có hr:leave:approve ⇒ ⛔ KHÔNG thấy "Duyệt nghỉ phép" — vế phân biệt', () => {
    expect(
      leafLabels(visibleMenu(MENU, checker('hr:leave:request'), { coHoSoNhanSu: true })),
    ).not.toContain('Duyệt nghỉ phép');
    expect(
      leafLabels(visibleMenu(MENU, checker('hr:leave:approve'), { coHoSoNhanSu: true })),
    ).toContain('Duyệt nghỉ phép');
  });

  it('⭐ "Ngày nghỉ lễ" gác bằng quyền của NGƯỜI NỘP ĐƠN, ⛔ không bằng quyền người sửa danh mục', () => {
    // ⛔ Gác bằng `hr:contract:manage` (2/12 vai trò) sẽ khoá đường đọc của những người mà lịch lễ
    //   quyết định số ngày công — và dựng lại đúng một endpoint ⛔ không màn hình nào gọi (§11.15).
    expect(leafLabels(visibleMenu(MENU, checker('hr:leave:request')))).toContain('Ngày nghỉ lễ');
    expect(leafLabels(visibleMenu(MENU, checker('hr:contract:manage')))).not.toContain(
      'Ngày nghỉ lễ',
    );
  });

  it('⭐ "Lịch nghỉ đơn vị" gác bằng `hr:leave:view-all`, ⛔ bằng `hr:leave:approve` — T57.18(b)', () => {
    // Lịch đơn vị là để BỐ TRÍ CA TRỰC, nên người phụ trách nhân sự cần xem dù ⛔ phải cấp duyệt.
    // Và `hr:leave:view-all` đúng là mã quyền backend đang gác `GET /hr/nghi-phep/lich` — hai tầng
    // trả lời cùng một câu thì phải cùng một điều kiện.
    expect(leafLabels(visibleMenu(MENU, checker('hr:leave:view-all')))).toContain(
      'Lịch nghỉ đơn vị',
    );
    expect(leafLabels(visibleMenu(MENU, checker('hr:leave:approve')))).not.toContain(
      'Lịch nghỉ đơn vị',
    );
  });

  it('đường dẫn tô sáng đúng mục', () => {
    expect(findMenuKey(MENU, '/nhan-su/nghi-phep')).toBe('nghi-phep-cua-toi');
    expect(findMenuKey(MENU, '/nhan-su/duyet-nghi-phep')).toBe('duyet-nghi-phep');
    expect(findMenuKey(MENU, '/nhan-su/ngay-le')).toBe('ngay-le');
    expect(findMenuKey(MENU, '/nhan-su/lich-nghi-don-vi')).toBe('lich-nghi-don-vi');
  });
});

/**
 * Sơ đồ tổ chức — CN-04.1 (WS-58).
 *
 * ⛔⛔ Mục này gác bằng `hr:org-chart:view` (3/12 vai trò), ⛔ **không** bằng `adm:org-unit:view`.
 * Hai màn hình đọc cùng một cây với hai mục đích ngược nhau: *Quản trị › Sơ đồ đơn vị* để **SỬA**
 * (`adm:org-unit:manage`), còn đây để **XEM** kèm quân số. Gộp chúng về một quyền hỏng theo CẢ HAI
 * chiều — cùng hình dạng CN-04.6 vs CN-04.7 (§11.22).
 */
describe('mục "Sơ đồ tổ chức" gác bằng hr:org-chart:view — CN-04.1', () => {
  it('⭐ có hr:org-chart:view ⇒ THẤY', () => {
    expect(leafLabels(visibleMenu(MENU, checker('hr:org-chart:view')))).toContain('Sơ đồ tổ chức');
  });

  it('⛔ chỉ có adm:org-unit:view ⇒ ⛔ KHÔNG thấy — vế phân biệt', () => {
    const visible = leafLabels(visibleMenu(MENU, checker('adm:org-unit:view')));
    expect(visible).not.toContain('Sơ đồ tổ chức');
    expect(visible).toContain('Sơ đồ đơn vị');
  });

  it('đường dẫn tô sáng đúng mục', () => {
    expect(findMenuKey(MENU, '/nhan-su/so-do-to-chuc')).toBe('so-do-to-chuc');
  });
});

/**
 * Báo cáo nhân sự — CN-04.8 (WS-58).
 *
 * ⛔⛔ Mục gác bằng `hr:report:view`; nút **tải tệp** bên trong trang gác riêng bằng
 * `hr:report:export`. Gộp hai quyền *"cho gọn"* là xoá một ranh giới khách đã vẽ: **xem** số tổng
 * hợp và **mang cả danh sách cán bộ ra khỏi hệ thống** là hai việc khác nhau.
 */
describe('mục "Báo cáo nhân sự" gác bằng hr:report:view — CN-04.8', () => {
  it('⭐ có hr:report:view ⇒ THẤY, và ⛔ không cần hr:report:export', () => {
    expect(leafLabels(visibleMenu(MENU, checker('hr:report:view')))).toContain('Báo cáo nhân sự');
  });

  it('⛔ chỉ có hr:report:export ⇒ ⛔ KHÔNG thấy mục — vế phân biệt', () => {
    // Quyền xuất một mình ⛔ không mở màn hình: nó gác NÚT, ⛔ không gác TRANG.
    expect(leafLabels(visibleMenu(MENU, checker('hr:report:export')))).not.toContain(
      'Báo cáo nhân sự',
    );
  });

  it('đường dẫn tô sáng đúng mục', () => {
    expect(findMenuKey(MENU, '/nhan-su/bao-cao')).toBe('bao-cao-nhan-su');
  });
});

/**
 * Lớp bản đồ GIS + Báo cáo vận hành — C3 (WS-59).
 *
 * ⛔⛔ Bốn mã quyền `ops:*` này là **bốn dòng miễn kiểm *Phase 3* CUỐI CÙNG** của `RbacMatrixTest`.
 * Sau lượt này, mọi mã quyền trong danh mục đều có ít nhất một đầu nhận.
 */
describe('hai mục C3 gác bằng quyền ops — CN-02.4 / CN-02.10', () => {
  it('⭐ `ops:gis-layer:view` mở mục Lớp bản đồ, ⛔ không cần `:manage`', () => {
    // ⛔ Gác bằng `:manage` sẽ khoá người chỉ được XEM ra khỏi một màn hình họ cần đọc — ba nút
    //   ghi trong trang đã tự ẩn theo `:manage` rồi.
    expect(leafLabels(visibleMenu(MENU, checker('ops:gis-layer:view')))).toContain(
      'Lớp bản đồ GIS',
    );
    expect(leafLabels(visibleMenu(MENU, checker('ops:gis-layer:manage')))).not.toContain(
      'Lớp bản đồ GIS',
    );
  });

  it('⭐ `ops:report:view` mở mục Báo cáo vận hành; `:export` một mình thì ⛔ KHÔNG', () => {
    // Quyền xuất gác NÚT, ⛔ không gác TRANG — cùng luật với báo cáo nhân sự.
    expect(leafLabels(visibleMenu(MENU, checker('ops:report:view')))).toContain('Báo cáo vận hành');
    expect(leafLabels(visibleMenu(MENU, checker('ops:report:export')))).not.toContain(
      'Báo cáo vận hành',
    );
  });

  it('đường dẫn tô sáng đúng mục', () => {
    expect(findMenuKey(MENU, '/van-hanh/lop-ban-do')).toBe('lop-ban-do');
    expect(findMenuKey(MENU, '/van-hanh/bao-cao')).toBe('bao-cao-van-hanh');
  });
});

describe('Báo cáo nhanh + Danh mục máy bơm — 18/09/2026', () => {
  it('⭐ `ops:report:view` mở Báo cáo nhanh; `ops:quick-report:manage` một mình thì ⛔ KHÔNG', () => {
    // Quyền nhập/chốt gác NÚT (tầng 2), ⛔ gác TRANG — người chỉ được xem vẫn đọc được văn bản.
    expect(leafLabels(visibleMenu(MENU, checker('ops:report:view')))).toContain('Báo cáo nhanh');
    expect(leafLabels(visibleMenu(MENU, checker('ops:quick-report:manage')))).not.toContain(
      'Báo cáo nhanh',
    );
  });

  it('`ops:construction:view` mở Danh mục máy bơm', () => {
    expect(leafLabels(visibleMenu(MENU, checker('ops:construction:view')))).toContain(
      'Danh mục máy bơm',
    );
    expect(leafLabels(visibleMenu(MENU, checker('ops:report:view')))).not.toContain(
      'Danh mục máy bơm',
    );
  });

  it('⛔ `/van-hanh/bao-cao-nhanh/…` tô sáng Báo cáo nhanh, ⛔ Báo cáo vận hành (tiền tố chung)', () => {
    expect(findMenuKey(MENU, '/van-hanh/bao-cao-nhanh')).toBe('bao-cao-nhanh');
    expect(findMenuKey(MENU, '/van-hanh/bao-cao-nhanh/3f2a')).toBe('bao-cao-nhanh');
    expect(findMenuKey(MENU, '/van-hanh/bao-cao-nhanh/cau-hinh')).toBe('bao-cao-nhanh');
    expect(findMenuKey(MENU, '/van-hanh/bao-cao')).toBe('bao-cao-van-hanh');
    expect(findMenuKey(MENU, '/van-hanh/may-bom')).toBe('may-bom');
  });
});
