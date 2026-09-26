import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';
import { type SettingView, type UserView } from '@/shared/api-types';
import type * as ApiClientModule from '@/shared/apiClient';

/**
 * ⛔⛔ **Nhóm "Ban điều hành" phải CHỌN được, ⛔ phải gõ JSON UUID bằng tay** — T76.3.
 *
 * <h2>Khuyết tật, đo 20/09/2026</h2>
 *
 * `notification.alert-group.executive-board` là khoá `JSON`, `is_editable = TRUE`, nên **trên giấy**
 * nó có đường ghi. Đường ấy là ô `Input.TextArea` thô của {@link SettingsPage}, và giá trị phải là
 * một mảng **`publicId` tài khoản** — trong khi màn hình *Tài khoản* ⛔ hiện `publicId` ở đâu cả.
 * ⇒ Một đường ghi **tồn tại mà ⛔ đi được**: người quản trị ⛔ có cách nào biết UUID của ai.
 *
 * Hệ quả đo được: khoá ấy giữ nguyên `'[]'` từ 13/08/2026, nên `RecipientResolver.executiveBoard()`
 * trả **tập rỗng** ⇒ chốt G11 của khách (*"cảnh báo tới Ban điều hành"*) chưa từng chạy một lần.
 *
 * <h2>⚠⚠ Vì sao bài này đi SAU bản vá T74.7, ⛔ đi một mình</h2>
 *
 * Tới 20/09 `RecipientResolver` **cộng** nhóm này vào MỌI lượt gửi ⛔ nhắm đích theo quyền — gồm 17
 * hàng `notify_owner` của quy trình duyệt và sáu mã sự kiện an ninh **của một cá nhân** (*"tài khoản
 * của bạn đã bị khoá"*). Hôm nay ⛔ ai thấy vì nhóm rỗng. ⇒ Dựng widget này TRƯỚC khi vá T74.7 là
 * **bật một lỗi đang ngủ** đúng ngày Công ty điền danh sách. Bộ canh của vế kia:
 * `NhomCanhBaoKhongLanSangThuCaNhanTest`.
 *
 * <h2>Ba bài, ba việc khác nhau</h2>
 *
 * <ol>
 *   <li>**đọc** — giá trị đã lưu hiện ra thành TÊN NGƯỜI, ⛔ phải UUID;
 *   <li>**ghi** — chọn thêm một người ⇒ `PUT` một mảng JSON `publicId` **hợp lệ**;
 *   <li>**vế phân biệt** — một khoá `JSON` KHÁC vẫn là ô văn bản thô. ⛔ Có vế này thì một bản vá
 *       thay *mọi* ô JSON bằng ô chọn tài khoản cũng làm hai bài đầu xanh, và nó sẽ phá mọi khoá
 *       JSON còn lại (luật 9 · luật 28).
 * </ol>
 *
 * <h2>⭐ T85.15 — hằng khoá ĐO từ backend, ⛔ gõ tay</h2>
 *
 * Bản đầu của chính tệp này giữ một **bản chép** chuỗi khoá, và `SettingsPage.tsx` giữ một bản nữa
 * (`KHOA_NHOM_CANH_BAO`) — ⛔ bên nào đọc `RecipientResolver.java`. Hai bản chép khớp nhau thì bài
 * xanh, nên nó **canh chính nó với trang** chứ ⛔ canh cặp FE ↔ BE: backend đổi khoá là ô chọn lặng
 * lẽ ⛔ hiện ra và người quản trị tụt về đúng ô JSON thô mà T76.3 sinh ra để bỏ, ⛔ một dòng đỏ nào
 * (T51.15 — *một bài kiểm chép hằng số của phía bên kia thì nó canh CHÍNH NÓ*).
 *
 * ⇒ Mọi bài dưới dùng chuỗi **đọc từ `RecipientResolver.java`**, đúng khuôn
 * `oChonMaDiemDoLenCong.test.tsx`: hằng phía FE trôi một ký tự là cả ba bài đỏ ngay — ⛔ phải một
 * bài canh-văn-bản riêng, và ⛔ có cách nào im nó bằng cách sửa chú thích.
 *
 * ⚠ `CiPathFilterTest` **ĐO** mọi hằng chuỗi `'backend/…'` trong mã kiểm FE rồi đối chiếu với bộ lọc
 * `frontend` của `ci.yml` — nên đường dẫn dưới đây tự nó kéo job FE chạy khi tệp Java ấy đổi.
 */

const goi = vi.fn();

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');

const NGUON_RECIPIENT_RESOLVER = readFileSync(
  join(
    GOC_KHO,
    'backend/core/src/main/java/com/songnhue/core/application/notification/RecipientResolver.java',
  ),
  'utf8',
);

/** `public static final String KEY_EXECUTIVE_BOARD = "notification.alert-group.executive-board";` */
const KHOA_BAN_DIEU_HANH = (() => {
  const khop = /KEY_EXECUTIVE_BOARD\s*=\s*"([^"]+)"/.exec(NGUON_RECIPIENT_RESOLVER);
  // Tiền đề (luật 7): ⛔ bóc được thì mọi bài dưới chạy trên một khoá BỊA và đều xanh vô nghĩa.
  if (!khop) {
    throw new Error('⛔ đọc được KEY_EXECUTIVE_BOARD trong RecipientResolver.java');
  }
  return khop[1];
})();

const NGUOI: UserView[] = [
  nguoi('u-an-0000-0000-0000-000000000001', 'an.nv', 'Nguyễn Văn An', 'ACTIVE'),
  nguoi('u-binh-0000-0000-0000-00000000002', 'binh.tt', 'Trần Thị Bình', 'ACTIVE'),
  nguoi('u-cuong-0000-0000-0000-00000000003', 'cuong.le', 'Lê Văn Cường', 'ACTIVE'),
  nguoi('u-khoa-0000-0000-0000-000000000004', 'khoa.pv', 'Phạm Văn Khoá', 'LOCKED'),
];

function nguoi(publicId: string, username: string, fullName: string, status: string): UserView {
  return {
    publicId,
    username,
    fullName,
    email: null,
    phone: null,
    status,
    mustChangePassword: false,
    twoFactorRequired: false,
    lastLoginAt: null,
    hoSoNhanSu: null,
  };
}

function thamSoJson(key: string, value: string): SettingView {
  return {
    key,
    value,
    effectiveValue: value,
    valueType: 'JSON',
    defaultValue: '[]',
    groupCode: 'NOTIFICATION',
    label: key === KHOA_BAN_DIEU_HANH ? 'Nhóm Ban điều hành' : 'Khoá JSON khác',
    description: null,
    validation: null,
    editable: true,
    exportable: true,
    canXacThucLai: false,
  };
}

let danhSachThamSo: SettingView[] = [];

vi.mock('@/shared/apiClient', async (importOriginal) => {
  const thuc = await importOriginal<typeof ApiClientModule>();
  return {
    ...thuc,
    api: {
      ...thuc.api,
      get: vi.fn(async (url: string) =>
        url === '/settings' ? danhSachThamSo : url === '/admin/users' ? NGUOI : [],
      ),
      put: vi.fn(async (url: string, than: unknown) => {
        goi('PUT', url, than);
        return {};
      }),
      post: vi.fn(async (url: string, than: unknown) => {
        goi('POST', url, than);
        return {};
      }),
    },
  };
});

const { SettingsPage } = await import('./SettingsPage');

function dung() {
  const khongDung = () => {
    throw new Error('AuthContext giả');
  };
  const auth = {
    status: 'authenticated' as const,
    user: { id: 'toi', username: 'sieuquantri' },
    hasPermission: (q: string) => ['adm:setting:view', 'adm:setting:update'].includes(q),
    hasRole: () => false,
    maintenance: false,
    login: khongDung,
    verifyTwoFactor: khongDung,
    confirmEnrollment: khongDung,
    logout: khongDung,
    endSession: khongDung,
    reloadProfile: khongDung,
  } as unknown as AuthContextValue;
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>
          <SettingsPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  goi.mockClear();
});

describe('Nhóm Ban điều hành chọn được bằng TÊN (T76.3)', () => {
  it('⛔⛔ giá trị đã lưu hiện ra thành TÊN NGƯỜI — ⛔ phải một mảng UUID', async () => {
    danhSachThamSo = [
      thamSoJson(KHOA_BAN_DIEU_HANH, JSON.stringify([NGUOI[0].publicId, NGUOI[1].publicId])),
    ];
    dung();

    expect(await screen.findByText(/Nguyễn Văn An/)).toBeTruthy();
    expect(screen.getByText(/Trần Thị Bình/)).toBeTruthy();

    // ⚠ Vế này mới là thứ phân biệt "đã vá" với "vẫn là ô JSON thô": ô cũ hiện nguyên văn UUID.
    expect(screen.queryByDisplayValue(new RegExp(NGUOI[0].publicId))).toBeNull();
  });

  it('⛔⛔ chọn thêm một người rồi Lưu ⇒ PUT một mảng publicId HỢP LỆ', async () => {
    danhSachThamSo = [thamSoJson(KHOA_BAN_DIEU_HANH, JSON.stringify([NGUOI[0].publicId]))];
    const nguoiDung = userEvent.setup();
    dung();

    const o = await screen.findByLabelText('Nhóm Ban điều hành');
    await nguoiDung.click(o);
    await nguoiDung.click(await screen.findByTitle('Lê Văn Cường (cuong.le)'));
    await nguoiDung.click(screen.getByRole('button', { name: 'Lưu' }));

    await waitFor(() =>
      expect(goi).toHaveBeenCalledWith('PUT', `/settings/${KHOA_BAN_DIEU_HANH}`, {
        value: JSON.stringify([NGUOI[0].publicId, NGUOI[2].publicId]),
      }),
    );
  });

  it('⚠ tài khoản đã KHOÁ ⛔ bày ra — máy chủ lọc `findActiveIdsIn`, bày ra là dựng một lựa chọn chắc chắn hỏng', async () => {
    danhSachThamSo = [thamSoJson(KHOA_BAN_DIEU_HANH, '[]')];
    const nguoiDung = userEvent.setup();
    dung();

    await nguoiDung.click(await screen.findByLabelText('Nhóm Ban điều hành'));

    expect(await screen.findByTitle('Nguyễn Văn An (an.nv)')).toBeTruthy();
    expect(screen.queryByTitle('Phạm Văn Khoá (khoa.pv)')).toBeNull();
  });

  /**
   * ⛔⛔ **Bản đầu của bài này là một XANH GIẢ, và lượt phá bắt được.**
   *
   * Nó dùng giá trị `'{"a":1}'` — đọc được nhưng ⛔ phải MẢNG, nên `docMangPublicId` trả `null` và
   * ô thô hiện ra **bất kể khoá nào**. Nới phạm vi widget ra `valueType === 'JSON'` (đúng bản hỏng
   * nó sinh ra để bắt) ⇒ vẫn **4/4 xanh**. Vế phân biệt phải là một mảng chuỗi **HỢP LỆ**: khi ấy
   * hai trạng thái mới đọc khác nhau (luật 9).
   */
  it('⚠ VẾ PHÂN BIỆT (luật 28) — khoá JSON KHÁC mang mảng HỢP LỆ vẫn phải là ô văn bản thô', async () => {
    const mangHopLe = JSON.stringify(['x', 'y']);
    danhSachThamSo = [thamSoJson('notification.mot.khoa.json.khac', mangHopLe)];
    dung();

    expect(await screen.findByDisplayValue(mangHopLe)).toBeTruthy();
    expect(screen.queryByLabelText('Khoá JSON khác')).toBeNull();
  });

  it('⚠ giá trị JSON HỎNG rơi xuống ô thô — một mảng ⛔ đọc được phải NHÌN THẤY được để sửa', async () => {
    danhSachThamSo = [thamSoJson(KHOA_BAN_DIEU_HANH, '["u-an", thiếu ngoặc')];
    dung();

    expect(await screen.findByDisplayValue('["u-an", thiếu ngoặc')).toBeTruthy();
    expect(screen.queryByLabelText('Nhóm Ban điều hành')).toBeNull();
  });
});
