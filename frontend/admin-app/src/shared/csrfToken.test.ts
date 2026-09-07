import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { __httpForTests, api, clearTokens, setAccessToken } from './apiClient';

/**
 * **Vé CSRF chỉ có MỘT nguồn: cookie.**
 *
 * Cơ chế double-submit đối chiếu header `X-CSRF-Token` với cookie `XSRF-TOKEN`. Máy chủ chỉ
 * tin cookie — nên khi cookie vắng mặt thì *không giá trị nào* gửi lên đi qua được.
 *
 * ## Lỗi đã xảy ra thật
 *
 * Bản đầu giữ thêm một bản trong bộ nhớ và cho nó **thắng** cookie
 * (`csrfToken ?? readCookie(...)`). Đổi mật khẩu thành công thì máy chủ thu hồi phiên và xoá
 * cookie, nhưng bản trong RAM vẫn còn, nên FE tiếp tục gửi một vé mà trình duyệt không còn
 * giữ. Nhật ký máy chủ ghi đúng triệu chứng đó:
 *
 * ```
 * Từ chối CSRF: POST /api/v1/auth/change-password — header có, cookie thiếu
 * ```
 *
 * ⭐ **Bản sửa đầu tiên của tôi chỉ đảo thứ tự ưu tiên, và bài kiểm này bắt được là chưa đủ.**
 * Đảo thứ tự chỉ chữa lúc hai bên *cùng có* mà lệch nhau; lúc cookie **bị xoá** thì vẫn rơi
 * về bộ nhớ và vẫn gửi vé chết. Bỏ hẳn bản sao mới là bản sửa đúng — hai nguồn cho một sự
 * thật chỉ tạo cơ hội để chúng lệch nhau.
 */

const COOKIE = 'XSRF-TOKEN';

function datCookie(giaTri: string | null): void {
  document.cookie =
    giaTri === null ? `${COOKIE}=; Max-Age=0; path=/` : `${COOKIE}=${giaTri}; path=/`;
}

/**
 * Chặn ngay ở tầng vận chuyển để đọc được header thật mà interceptor đã dựng.
 *
 * ⚠ Kiểu của tham số khai tại chỗ chứ không `import type` từ `axios`: ESLint cấm mọi tệp
 * ngoài `apiClient` import axios (conventions.md §2.5), và luật đó đáng giữ nguyên — nới ra
 * cho "chỉ là kiểu thôi" là mở đúng cái khe mà lần sau có người dựng instance riêng chui qua.
 */
function batHeader(): { doc: () => Record<string, unknown> } {
  let batDuoc: Record<string, unknown> = {};
  __httpForTests.defaults.adapter = ((config: { headers: unknown }) => {
    batDuoc = { ...(config.headers as Record<string, unknown>) };
    return Promise.resolve({
      data: { success: true, data: null },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    });
  }) as typeof __httpForTests.defaults.adapter;
  return { doc: () => batDuoc };
}

describe('Vé CSRF gửi kèm request', () => {
  beforeEach(() => {
    clearTokens();
    datCookie(null);
    setAccessToken('access-token-gia');
  });

  afterEach(() => {
    clearTokens();
    datCookie(null);
    __httpForTests.defaults.adapter = undefined;
  });

  it('⭐ vé gửi lên lấy đúng từ cookie', async () => {
    const bat = batHeader();
    datCookie('ve-that-trong-cookie');

    await api.post('/thu-nghiem', {});

    expect(bat.doc()['X-CSRF-Token']).toBe('ve-that-trong-cookie');
  });

  it('⭐⭐ máy chủ xoá cookie thì KHÔNG còn vé nào để gửi — đây là lỗi đã xảy ra thật', async () => {
    const bat = batHeader();
    datCookie('ve-cua-phien-sap-bi-thu-hoi');
    await api.post('/thu-nghiem', {});
    expect(bat.doc()['X-CSRF-Token']).toBe('ve-cua-phien-sap-bi-thu-hoi');

    // Máy chủ thu hồi phiên (đổi mật khẩu / đăng xuất) → cookie biến mất.
    datCookie(null);
    await api.post('/thu-nghiem', {});

    expect(bat.doc()['X-CSRF-Token']).toBeUndefined();
  });

  it('⭐ vé sống sót qua F5 — bộ nhớ trống nhưng cookie còn', async () => {
    const bat = batHeader();
    datCookie('ve-tu-cookie');
    // `clearTokens()` mô phỏng bộ nhớ sạch sau khi tải lại trang.
    clearTokens();
    setAccessToken('access-token-gia');

    await api.post('/thu-nghiem', {});

    expect(bat.doc()['X-CSRF-Token']).toBe('ve-tu-cookie');
  });
});
