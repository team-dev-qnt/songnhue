import type { AxiosAdapter } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { __httpForTests, api } from '@/shared/apiClient';

/**
 * ⭐⭐ **204 No Content là THÀNH CÔNG, không phải envelope hỏng.**
 *
 * Đây là lỗi đã biến việc đã-commit-xong thành lỗi hiện trên màn hình, ở **24 endpoint** cùng
 * lúc — xoá, sắp xếp lại, đánh dấu đã đọc, gỡ đăng, khoá tài khoản, và đổi mật khẩu.
 *
 * `ResponseEnvelopeAdvice` là `ResponseBodyAdvice`, mà Spring **không gọi advice khi handler trả
 * `void`** — không thân thì không converter nào chạy. 24 endpoint ấy vì thế trả 204 trần. Phía
 * này axios đặt `response.data = ''`; `''.success` là `undefined`, `!undefined` là `true`, và
 * `unwrap` ném `SYS-0001` **sau khi máy chủ đã ghi xong**.
 *
 * Bài kiểm đặt ở tầng `api.*` chứ không ở màn hình, vì 24 đường vào cùng đi qua đúng một hàm
 * (CLAUDE.md luật 12). Kiểm ở `ChangePasswordPage` thì 23 cái còn lại vẫn hỏng trong im lặng —
 * và đó chính là chuyện đã xảy ra: lượt sửa trước chữa triệu chứng thứ hai ở màn hình đó mà
 * không tìm ra mắt xích đầu tiên nằm ở đây.
 */
describe('204 No Content không được biến thành lỗi', () => {
  const banGoc = __httpForTests.defaults.adapter;

  afterEach(() => {
    __httpForTests.defaults.adapter = banGoc;
  });

  /** Đúng hình dạng axios dựng cho một phản hồi 204: `data` là chuỗi RỖNG, không phải object. */
  function traVe204(): void {
    const adapter: AxiosAdapter = async (config) => ({
      data: '',
      status: 204,
      statusText: 'No Content',
      headers: {},
      config,
      request: {},
    });
    __httpForTests.defaults.adapter = adapter;
  }

  // Bốn động từ này phủ hết 24 endpoint 204 đang có: DELETE (xoá), POST (đổi mật khẩu, đăng
  // xuất, thu hồi phiên, đánh dấu đã đọc), PUT/PATCH (sắp xếp lại, gỡ đăng).
  const dongTu = [
    { ten: 'post', goi: () => api.post<void>('/auth/change-password', {}) },
    { ten: 'put', goi: () => api.put<void>('/cms/menus/HEADER/reorder', {}) },
    { ten: 'patch', goi: () => api.patch<void>('/cms/banners/1', {}) },
    { ten: 'delete', goi: () => api.delete<void>('/cms/articles/1') },
  ] as const;

  it.each(dongTu)('api.$ten không ném khi máy chủ trả 204', async ({ goi }) => {
    traVe204();
    await expect(goi()).resolves.toBeUndefined();
  });

  it('getPage trên 204 trả danh sách rỗng chứ không nổ ở items.length', async () => {
    traVe204();
    const trang = await api.getPage<{ id: number }>('/cms/articles');
    expect(trang.items).toEqual([]);
    expect(trang.meta.totalElements).toBe(0);
  });

  it('⛔ và envelope success:false kèm 200 thì VẪN phải ném — đừng nới quá tay', async () => {
    // Nếu bản vá chỉ là "thân nào không phải object thì coi như xong" thì bài này sẽ đỏ.
    // Ranh giới phải là "KHÔNG CÓ thân", không phải "thân không đọc được".
    const adapter: AxiosAdapter = async (config) => ({
      data: { success: false, data: null, error: { code: 'CMS-1001', message: 'x' }, traceId: 't' },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
      request: {},
    });
    __httpForTests.defaults.adapter = adapter;
    await expect(api.post<void>('/cms/articles/1/publish', {})).rejects.toThrow();
  });
});
