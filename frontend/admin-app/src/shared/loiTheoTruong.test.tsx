import { App as AntdApp, Form, Input } from 'antd';
import type { FormInstance } from 'antd';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { boChuThich } from '@/testsupport/boChuThich';

import { ApiClientError } from './apiClient';
import { datLoiTheoTruong, moTaLuat } from './loiTheoTruong';

/**
 * **Một lỗi 422 không bao giờ được biến mất không dấu vết.**
 *
 * <h2>Sự cố 01/09/2026</h2>
 *
 * QuanTran: *"lúc tạo tài khoản, error 422 trong network F12 không hiển thị lên màn hình"*.
 * Backend trả `details[].field = "newPassword"`; biểu mẫu *Thêm tài khoản* khai
 * `temporaryPassword`. `Form.setFields` với một tên không tồn tại **không phải lỗi** với AntD —
 * nó lặng lẽ không làm gì. Nhánh xử lý `return` ngay sau đó, nên toast cũng không chạy.
 *
 * <h2>⭐ Vì sao bài này dựng `Form` THẬT của AntD</h2>
 *
 * Hành vi "bỏ qua tên lạ trong im lặng" là hành vi **của AntD**, không phải của mã ta viết. Một
 * bản `FormInstance` giả sẽ làm đúng thứ người viết bài kiểm *nghĩ* AntD làm — và người viết bài
 * kiểm chính là người vừa hiểu sai điều đó. Đây là luật 29: một bài kiểm chứng ngược có thể sai
 * theo đúng cách mà thứ nó kiểm đang sai. Nên phải là `Form` thật, `getFieldsError()` thật.
 */

/** Dựng một `AntdApp` + `Form` thật với đúng các trường được kê, trả về `FormInstance` sống. */
function dungForm(tenTruong: string[]): Promise<FormInstance> {
  return new Promise((resolve) => {
    function Man() {
      const [form] = Form.useForm();
      // `Form.useForm` chỉ gắn instance vào cây sau lượt vẽ đầu; trả ra qua ref lúc render là
      // trả ra một instance chưa đăng ký trường nào.
      return (
        <Form form={form} ref={() => resolve(form)}>
          {tenTruong.map((ten) => (
            <Form.Item key={ten} name={ten} label={ten}>
              <Input />
            </Form.Item>
          ))}
        </Form>
      );
    }
    render(
      <AntdApp>
        <Man />
      </AntdApp>,
    );
  });
}

function loi422(chiTiet: { field: string; rule: string }[]): ApiClientError {
  return new ApiClientError(
    'AUTH-0006',
    'Mật khẩu chưa đạt yêu cầu an toàn',
    'caller',
    'warning',
    422,
    'trace-kiem-thu',
    chiTiet.map((c) => ({ field: c.field, rule: c.rule, rejectedValue: null })),
  );
}

describe('datLoiTheoTruong — 422 không được biến mất', () => {
  afterEach(cleanup);

  it('⭐⭐ KỊCH BẢN SỰ CỐ: backend trỏ vào trường KHÔNG có trên biểu mẫu → trả false', async () => {
    // Đây là toàn bộ sự cố, cô lại thành một khẳng định. `false` nghĩa là "chưa ai thấy gì" —
    // và nơi gọi bắt buộc phải hiện toast. Bản trước không có giá trị trả về nào để hỏi.
    const form = await dungForm(['temporaryPassword', 'username']);

    const daDat = datLoiTheoTruong(form, loi422([{ field: 'newPassword', rule: 'MIN_LENGTH_12' }]));

    expect(daDat).toBe(false);
    // ⚠ Và khẳng định thêm rằng AntD THẬT SỰ im lặng — nếu một ngày AntD bắt đầu ném lỗi cho
    //   tên lạ thì bài này phải đỏ, vì lúc ấy cả bản vá lẫn lý do của nó đều đã đổi.
    expect(form.getFieldError('temporaryPassword')).toHaveLength(0);
  });

  it('tên trường KHỚP → đặt được lỗi lên đúng ô, và trả true', async () => {
    const form = await dungForm(['temporaryPassword', 'username']);

    const daDat = datLoiTheoTruong(
      form,
      loi422([{ field: 'temporaryPassword', rule: 'MIN_LENGTH_12' }]),
    );

    expect(daDat).toBe(true);
    expect(form.getFieldError('temporaryPassword')).toEqual(['Cần ít nhất 12 ký tự']);
  });

  it('nhiều luật trên cùng một trường gộp thành MỘT câu, không mất luật nào', async () => {
    const form = await dungForm(['temporaryPassword']);

    datLoiTheoTruong(
      form,
      loi422([
        { field: 'temporaryPassword', rule: 'MIN_LENGTH_12' },
        { field: 'temporaryPassword', rule: 'REQUIRE_LETTER_AND_DIGIT' },
        { field: 'temporaryPassword', rule: 'MUST_NOT_CONTAIN_USERNAME' },
      ]),
    );

    expect(form.getFieldError('temporaryPassword')).toEqual([
      'Cần ít nhất 12 ký tự · Phải có cả chữ và số · Không được chứa tên đăng nhập',
    ]);
  });

  it('một phần khớp, một phần không → vẫn trả true và đặt được phần khớp', async () => {
    const form = await dungForm(['username']);

    const daDat = datLoiTheoTruong(
      form,
      loi422([
        { field: 'username', rule: 'NotBlank' },
        { field: 'khongCoTruongNay', rule: 'NotBlank' },
      ]),
    );

    expect(daDat).toBe(true);
    expect(form.getFieldError('username')).toEqual(['Bắt buộc nhập']);
  });

  it('không có chi tiết nào → trả false, nơi gọi phải rơi về toast', async () => {
    const form = await dungForm(['username']);
    expect(datLoiTheoTruong(form, loi422([]))).toBe(false);
  });

  it('luật lạ → rơi về câu chung của mã lỗi, KHÔNG bịa chữ', async () => {
    const form = await dungForm(['username']);

    datLoiTheoTruong(form, loi422([{ field: 'username', rule: 'MOT_LUAT_CHUA_AI_DICH' }]));

    expect(form.getFieldError('username')).toEqual(['Mật khẩu chưa đạt yêu cầu an toàn']);
  });
});

describe('moTaLuat — con số phải là con số ĐÃ GIẢI từ settings', () => {
  it('MIN_LENGTH_<n> mang đúng n của backend, không phải một hằng số của giao diện', () => {
    // ⭐ Quy tắc 3 + §10.69. Nếu câu này ghi cứng "10 ký tự" thì nó nói dối ngay lần đầu Admin
    //    đổi tham số, và không cổng kiểm nào đỏ được.
    expect(moTaLuat('MIN_LENGTH_10')).toBe('Cần ít nhất 10 ký tự');
    expect(moTaLuat('MIN_LENGTH_14')).toBe('Cần ít nhất 14 ký tự');
  });

  it('luật chưa có bản dịch trả null — để nơi gọi biết mà rơi về câu chung', () => {
    expect(moTaLuat('LUAT_MOI_TINH')).toBeNull();
    expect(moTaLuat(null)).toBeNull();
    expect(moTaLuat(undefined)).toBeNull();
  });
});

/**
 * ⭐ Phần này canh thứ các bài trên KHÔNG canh được (luật 28): rằng **mọi** màn hình đều đi qua
 * `datLoiTheoTruong`. Không có nó thì sáu bài trên xanh trọn vẹn trong khi năm màn hình vẫn giữ
 * nguyên khuôn cũ — đúng hình dạng §10.62 (`SvgSanitizer` có 9 bài kiểm mà không nằm trên đường
 * chạy nào), và §10.70 (trả nợ ở ba điểm ghi, điểm ghi thứ tư mang lại đúng lỗi cũ).
 */
describe('Không màn hình nào còn giữ khuôn mã đã gây ra sự cố', () => {
  const MAN_HINH = [
    'features/admin/UsersPage.tsx',
    'features/admin/OrgUnitsPage.tsx',
    'features/admin/OrgUnitLeadersPanel.tsx',
    'features/operations/ConstructionFormPage.tsx',
    'features/operations/components/MaintenanceFormModal.tsx',
    'features/cms/ArticleEditorPage.tsx',
  ];

  const ma = MAN_HINH.map((duong) => ({
    duong,
    noiDung: readFileSync(join(process.cwd(), 'src', duong), 'utf8'),
  }));

  it('đọc được đủ sáu tệp — chống xanh trên tập rỗng (luật 7)', () => {
    expect(ma).toHaveLength(6);
    expect(ma.every((t) => t.noiDung.length > 500)).toBe(true);
  });

  it('không tệp nào còn gọi thẳng `form.setFields(...fieldErrors...)`', () => {
    // Khuôn cũ: `form.setFields(caught.fieldErrors())` rồi `return` vô điều kiện. Chính cú
    // `return` ấy là thứ nuốt mất toast — nên bắt ở lượt gọi `setFields`, không bắt ở `return`.
    const viPham = ma.filter((t) => /setFields\(\s*[^)]*fieldErrors/.test(t.noiDung));
    expect(
      viPham.map((t) => t.duong),
      'còn dùng khuôn cũ',
    ).toHaveLength(0);
  });

  it('mọi tệp đều nhập và gọi `datLoiTheoTruong`', () => {
    const thieu = ma.filter(
      (t) =>
        !t.noiDung.includes("from '@/shared/loiTheoTruong'") ||
        !t.noiDung.includes('datLoiTheoTruong('),
    );
    expect(
      thieu.map((t) => t.duong),
      'chưa đi qua bộ chặn dùng chung',
    ).toHaveLength(0);
  });

  it('kiểm chứng ngược: mẫu bắt khuôn cũ phải BẮT ĐƯỢC bản đã gây ra sự cố', () => {
    // Chép nguyên văn từ `git show 9832f65 -- .../UsersPage.tsx`.
    const BAN_HONG =
      '        form.setFields(caught.fieldErrors<keyof CreateUserRequest & string>());';
    expect(/setFields\(\s*[^)]*fieldErrors/.test(BAN_HONG)).toBe(true);
    // …và không bắt nhầm lượt gọi hợp lệ bên trong chính bộ chặn.
    expect(/setFields\(\s*[^)]*fieldErrors/.test('  form.setFields(nhanDuoc);')).toBe(false);
  });
});

describe('Hướng dẫn mật khẩu hiện ra ở màn hình đặt mật khẩu', () => {
  afterEach(cleanup);

  it('không màn hình nào còn hứa "hệ thống sẽ báo cụ thể" mà không có cơ chế đứng sau', () => {
    // Câu cũ ở `ChangePasswordPage` hứa hai điều: yêu cầu nằm ở màn hình cấu hình (người dùng
    // đang bị bắt buộc đổi mật khẩu thì không mở được), và hệ thống sẽ báo cụ thể (không đúng —
    // ba `rule` bị `fieldErrors()` vứt hết). Cả hai vế nay đã có cơ chế thật đứng sau.
    const doi = boChuThich(
      readFileSync(join(process.cwd(), 'src/features/auth/ChangePasswordPage.tsx'), 'utf8'),
    );
    expect(doi).not.toContain('hệ thống sẽ báo cụ thể');
    expect(doi).toContain('<HuongDanMatKhau />');

    const them = boChuThich(
      readFileSync(join(process.cwd(), 'src/features/admin/UsersPage.tsx'), 'utf8'),
    );
    expect(them).toContain('<HuongDanMatKhau />');
  });

  it('thành phần hướng dẫn KHÔNG ghi cứng con số nào', () => {
    // ⛔ §10.69: một tham số cấu hình *nói dối* khó phát hiện hơn một tham số không ai đọc.
    //    Mọi con số ở đây phải đến từ `GET /auth/password-policy`.
    const nguon = boChuThich(
      readFileSync(join(process.cwd(), 'src/shared/HuongDanMatKhau.tsx'), 'utf8'),
    );
    const than = nguon.slice(nguon.indexOf('export function HuongDanMatKhau'));
    expect(than).not.toMatch(/ít nhất \d+ ký tự/);
    expect(than).toContain('chinhSach.minLength');
  });

  it('chưa lấy được chính sách thì không vẽ gì — không có con số bịa nào lọt ra', async () => {
    const { HuongDanMatKhau } = await import('./HuongDanMatKhau');
    const { QueryClient, QueryClientProvider } = await import('@tanstack/react-query');
    // `queryFn` không bao giờ giải quyết ⇒ `data` là `undefined` suốt — đúng trạng thái "chưa
    // lấy được", và cũng là trạng thái của người dùng không có mạng.
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { container } = render(
      <QueryClientProvider client={qc}>
        <AntdApp>
          <HuongDanMatKhau />
        </AntdApp>
      </QueryClientProvider>,
    );
    expect(container.textContent).toBe('');
    expect(screen.queryByText(/ít nhất/)).toBeNull();
  });
});
