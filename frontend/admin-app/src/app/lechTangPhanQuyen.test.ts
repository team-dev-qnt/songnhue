import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * **Màn hình mở bằng quyền XEM thì nút GHI phải tự khoá** — nợ **T27.28**.
 *
 * <h3>⛔⛔ Hình dạng lỗi: tầng 1 và tầng 3 nói hai điều khác nhau, tầng 2 im lặng</h3>
 *
 * Dự án có **ba tầng** phân quyền: menu (tầng 1) · nút trên màn hình (tầng 2) · chốt chặn ở
 * endpoint (tầng 3). Tầng 3 là tầng duy nhất **thật sự chặn**; hai tầng kia tồn tại để người dùng
 * ⛔ không đâm vào một bức tường mà họ không nhìn thấy.
 *
 * Khi tuyến mở bằng một quyền **rộng hơn** quyền mà endpoint đòi, và màn hình ⛔ không có tầng 2,
 * thì người dùng: mở được màn hình → gõ xong việc → bấm Lưu → **403**. ⛔ Không màn hình nào giải
 * thích được, và triệu chứng họ báo lại là *"hệ thống lỗi"*.
 *
 * <h3>Hai chỗ đo được 04/09/2026, cả hai đã vá</h3>
 *
 * | màn hình | tuyến mở bằng | endpoint đòi | ai lọt qua |
 * |---|---|---|---|
 * | `ArticleEditorPage` | `cms:article:view` | `cms:article:create`/`update` | **EXECUTIVE · VIEWER** |
 * | `BannersTab` | `cms:layout:manage` | `cms:banner:manage` | ⛔ chưa ai — hai mã cùng một vai trò |
 *
 * ⚠ Chỗ thứ hai **hôm nay vô hại**, và đó chính là lý do phải canh: nó ⛔ không có triệu chứng cho
 * tới đúng ngày Công ty tách hai mã quyền ra — việc mà CN-05.2 sinh ra để làm.
 *
 * <h3>⚠ Phạm vi tự khai (luật 28)</h3>
 *
 * Bài này canh **hai màn hình có tên**, ⛔ không quét toàn cây. Một màn hình thứ ba mắc cùng lỗi ⛔
 * sẽ không bị bắt — muốn phủ hết thì phải đối chiếu `router.tsx` với `@RequirePermission` của
 * backend, và cặp ấy ⛔ không đọc được từ phía FE. Nợ đã ghi; ở đây khai giới hạn thay vì để cái
 * xanh đọc như một lời bảo đảm.
 */

const GOC = join(dirname(new URL(import.meta.url).pathname), '..');

function doc(tuongDoi: string): string {
  return readFileSync(join(GOC, tuongDoi), 'utf8');
}

/**
 * ⚠ Canh CẤU TRÚC: mã quyền phải nằm **bên trong đối số của một lời gọi `hasPermission(...)`**,
 * ⛔ không phải một lần nhắc tên trong chú thích (luật 2).
 *
 * <h3>⛔ Bản đầu của hàm này SAI, và lượt chạy đầu tiên bắt được</h3>
 *
 * Nó đòi đối số **trần**: {@code hasPermission('cms:article:create')}. Nhưng lời gọi thật là
 * {@code hasPermission(laBaiMoi ? 'cms:article:create' : 'cms:article:update')} — một biểu thức ba
 * ngôi, vì màn hình dùng `create` khi viết bài mới và `update` khi sửa. Bộ canh báo đỏ trên một
 * đoạn mã **đúng**.
 *
 * ⚠ Đó là cùng một sai lầm đã mắc sáng nay ở `bieuDoMucNuoc.test.ts`, chỉ đảo chiều: ở đó mẫu
 * **quá rộng** (một dòng chú thích đi lọt), ở đây mẫu **quá hẹp**. Cả hai đều là hệ quả của việc
 * canh hình dạng văn bản thay vì canh thứ mình thật sự muốn — <i>"mã quyền này có được hỏi tới
 * không"</i>.
 *
 * ⇒ Nay tách đối số của từng lời gọi rồi tìm mã quyền **bên trong** — biểu thức ba ngôi, biến tạm
 * hay hằng số đều đi qua được, còn văn xuôi thì ⛔ không.
 */
function goiHasPermission(ma: string, quyen: string): boolean {
  const doiSo = [...ma.matchAll(/hasPermission\(([^)]*)\)/g)].map((m) => m[1]);
  return doiSo.some((d) => d.includes(`'${quyen}'`) || d.includes(`"${quyen}"`));
}

/**
 * Kết quả `hasPermission` có thật sự **khoá được một nút** không.
 *
 * <h3>⛔⛔ Bản đầu SAI, và lượt kiểm chứng ngược bắt được</h3>
 *
 * Bản đầu chỉ hỏi: có `!coQuyenGhi` ở đâu đó, và có `disabled={` ở đâu đó. Lượt phá thử — gỡ
 * `!coQuyenGhi` khỏi biểu thức khoá nút Lưu — **vẫn xanh**, vì tệp còn một `!coQuyenGhi` khác nằm
 * trong `title={…}`, tức là một **tooltip**. Bộ canh khi ấy chấp nhận một màn hình mà nút vẫn bấm
 * được, chỉ là có chú thích giải thích vì sao lẽ ra không nên bấm.
 *
 * ⚠ Đúng luật 9: hai trạng thái *"đã khoá"* và *"có chữ nói về việc khoá"* ⛔ không phân biệt được.
 *
 * ⇒ Nay đi hai bước: (1) tìm mọi **biến** được gán một biểu thức chứa `!coQuyenGhi`; (2) đòi
 * `coQuyenGhi` **hoặc** một trong những biến ấy xuất hiện **bên trong** một `disabled={…}`.
 *
 * ⛔ Giới hạn tự khai (luật 28): nó theo được **một** mắt xích biến trung gian, ⛔ không theo được
 * hai. Và nó ⛔ không chứng minh được `disabled` ấy thuộc đúng nút Lưu — bắt được lỗi *quên nối*,
 * ⛔ không bắt được lỗi *nối vào nhầm nút*. Muốn chặt hơn phải render component và bấm thử.
 */
function khoaDuocNoiVaoNut(ma: string): boolean {
  const bienKhoa = [...ma.matchAll(/const\s+(\w+)\s*=[^;]*!coQuyenGhi[^;]*;/g)].map((m) => m[1]);
  const ungVien = ['coQuyenGhi', ...bienKhoa];
  return [...ma.matchAll(/disabled=\{([^}]*)\}/g)].some((m) =>
    ungVien.some((ten) => new RegExp(String.raw`\b${ten}\b`).test(m[1])),
  );
}

describe('T27.28 — màn hình mở bằng quyền XEM phải khoá nút GHI', () => {
  it('⭐⭐ Trình soạn thảo bài viết khoá nút Lưu theo `cms:article:create`/`update`', () => {
    const ma = doc('features/cms/ArticleEditorPage.tsx');

    expect(
      goiHasPermission(ma, 'cms:article:create') && goiHasPermission(ma, 'cms:article:update'),
      '⛔ Tuyến mở bằng `cms:article:view`, mà EXECUTIVE và VIEWER CÓ quyền ấy và ⛔ KHÔNG có ' +
        'create/update. Họ mở được trình soạn thảo, gõ xong cả bài, bấm Lưu và nhận 403 — hai vai ' +
        'trò ấy là lãnh đạo và người xem, đúng những người ⛔ không tự đoán được rằng mình chỉ ' +
        'được đọc, vì màn hình mở ra y hệt người soạn bài.',
    ).toBe(true);

    expect(
      khoaDuocNoiVaoNut(ma),
      '⛔ Có `hasPermission` mà ⛔ không nối vào `disabled` là nửa cặp đọc–ghi ở tầng giao diện: ' +
        'biến được tính, nút vẫn bấm được (luật 27).',
    ).toBe(true);
  });

  it('⭐ Tab banner khoá nút ghi theo `cms:banner:manage`, ⛔ không theo quyền của tuyến', () => {
    const ma = doc('features/cms/BannersTab.tsx');

    expect(
      goiHasPermission(ma, 'cms:banner:manage'),
      '⛔ Bảy endpoint ghi của `BannerController` đòi `cms:banner:manage`, còn tuyến ' +
        '`/noi-dung/giao-dien` mở bằng `cms:layout:manage`. Hôm nay hai mã thuộc cùng một vai trò ' +
        'nên ⛔ chưa ai gặp — ngày Công ty tách chúng thì mọi nút ở tab này 403 trong im lặng.',
    ).toBe(true);

    expect(
      khoaDuocNoiVaoNut(ma),
      '⛔ `hasPermission` phải nối vào `disabled` của nút, ⛔ không chỉ được tính ra rồi bỏ đó.',
    ).toBe(true);
  });

  /**
   * ⚠ Vế chống xanh-trên-tập-rỗng + vế phân biệt (luật 7 · 9): bộ đọc tệp và mẫu regex phải
   * **phân biệt được** hai trạng thái, ⛔ không chỉ "có khớp gì đó".
   */
  it('⚠ Tự kiểm: bộ canh đọc được tệp thật và ⛔ không khớp một mã quyền bịa', () => {
    const ma = doc('features/cms/ArticleEditorPage.tsx');

    expect(ma.length, 'đọc ra tệp rỗng ⇒ mọi khẳng định trên vô nghĩa').toBeGreaterThan(1000);
    expect(
      goiHasPermission(ma, 'cms:khong-bao-gio-ton-tai'),
      'một mã quyền bịa ⛔ không được khớp — nếu nó khớp thì mẫu đang bắt bừa',
    ).toBe(false);
  });
});
