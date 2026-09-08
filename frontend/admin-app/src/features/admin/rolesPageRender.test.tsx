import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { App as AntdApp } from 'antd';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const get = vi.fn((..._args: unknown[]) => Promise.resolve([] as unknown));
const put = vi.fn((..._args: unknown[]) => Promise.resolve(undefined as unknown));
const hasPermission = vi.fn((_code: string) => true);

vi.mock('@/shared/apiClient', () => ({
  api: {
    get: (...args: unknown[]) => get(...args),
    put: (...args: unknown[]) => put(...args),
  },
  ApiClientError: class extends Error {},
}));

vi.mock('@/app/auth/useAuth', () => ({
  useAuth: () => ({ hasPermission: (code: string) => hasPermission(code) }),
}));

const { RolesPage } = await import('./RolesPage');

/**
 * **Bài kiểm ĐẦU TIÊN render `RolesPage`** — T27.31.
 *
 * ## ⛔ Vì sao một màn hình vừa chuyển từ "chỉ xem" sang "sửa được" cần bài render
 *
 * Trước T27.31 màn hình này ⛔ không có gì để sai: ba lời gọi `GET`, một bảng, một danh sách thẻ.
 * Nay nó mang **một trạng thái nháp** và **hai điều kiện khoá** — và cả ba thứ ấy đều là loại
 * khuyết tật mà `tsc` ⛔ không thấy được:
 *
 * - **ô nháp ⛔ không đồng bộ** khi đổi vai trò ⇒ tick của vai trò A dính sang vai trò B, người
 *   dùng bấm Lưu và ghi nhầm ma trận của một vai trò khác;
 * - **vai trò hệ thống ⛔ không bị khoá ở FE** ⇒ người dùng tick xong, bấm Lưu, nhận 403 và mất
 *   toàn bộ việc vừa làm (backend vẫn chặn — nhưng đó là một ràng buộc *ẩn*);
 * - **thân yêu cầu sai hình dạng** ⇒ `@NotNull List<String> permissionCodes` từ chối, và triệu
 *   chứng là *"lưu không được"* mà ⛔ không nói vì sao.
 *
 * ⚠ Cả ba đều là **luật 27 ở phía trình duyệt**: nửa ghi chạy hoàn hảo, nửa còn lại lệch.
 */
describe('RolesPage — màn hình ma trận phân quyền sửa được', () => {
  beforeEach(() => {
    get.mockReset();
    put.mockReset();
    hasPermission.mockReset();
    hasPermission.mockImplementation(() => true);
    put.mockImplementation(() => Promise.resolve(undefined));
    get.mockImplementation((duong: unknown) => {
      const url = String(duong);
      if (url.endsWith('/roles/catalog')) {
        return Promise.resolve(VAI_TRO);
      }
      if (url.endsWith('/permissions/catalog')) {
        return Promise.resolve(DANH_MUC);
      }
      if (url.includes('/roles/BIEN_TAP/permissions')) {
        return Promise.resolve(['cms:article:create']);
      }
      if (url.includes('/roles/SUPER_ADMIN/permissions')) {
        return Promise.resolve(['adm:role:manage', 'cms:article:create']);
      }
      return Promise.resolve([]);
    });
  });

  afterEach(() => cleanup());

  it('⭐ Vòng khép kín: chọn vai trò → tick thêm một quyền → Lưu gửi ĐÚNG cả tập lên PUT', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await nguoiDung.click(await screen.findByText('BIEN_TAP'));
    // Ô của quyền vai trò ĐANG có phải được tick sẵn — nếu ⛔ không, người dùng bấm Lưu là gỡ mất nó.
    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: /cms:article:create/ })).toBeChecked(),
    );
    expect(screen.getByRole('checkbox', { name: /hyd:station:view/ })).not.toBeChecked();

    await nguoiDung.click(screen.getByRole('checkbox', { name: /hyd:station:view/ }));
    await nguoiDung.click(screen.getByRole('button', { name: 'Lưu' }));

    await waitFor(() => expect(put).toHaveBeenCalledTimes(1));
    const [duong, than] = put.mock.calls[0] as [string, { permissionCodes: string[] }];
    expect(duong).toBe('/admin/users/roles/BIEN_TAP/permissions');
    // ⛔ Gửi CẢ TẬP, ⛔ không phải phần chênh lệch: hai người sửa cùng lúc ⛔ không được ra một kết
    //   quả lai mà ⛔ không ai chọn. Khẳng định đúng nội dung, ⛔ không chỉ "đã gọi".
    expect(than.permissionCodes).toEqual(['cms:article:create', 'hyd:station:view']);
  });

  it('⛔ Vai trò hệ thống: ⛔ không có nút Lưu, và mọi ô đều bị khoá', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await nguoiDung.click(await screen.findByText('SUPER_ADMIN'));
    await waitFor(() => expect(screen.getByText(/lối thoát cuối cùng/)).toBeInTheDocument());

    expect(screen.queryByRole('button', { name: 'Lưu' })).not.toBeInTheDocument();
    // ⚠ Khẳng định TỪNG ô, ⛔ không chỉ ô đầu: một điều kiện `disabled` viết hụt ở nhánh nào đó là
    //   đúng kiểu lỗi lọt qua một phép kiểm chỉ nhìn phần tử thứ nhất.
    for (const o of screen.getAllByRole('checkbox')) {
      expect(o).toBeDisabled();
    }
  });

  it('⛔ Đổi vai trò thì ô nháp phải ĐƯỢC XOÁ — tick của vai trò trước ⛔ không dính sang', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await nguoiDung.click(await screen.findByText('BIEN_TAP'));
    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: /hyd:station:view/ })).not.toBeChecked(),
    );
    await nguoiDung.click(screen.getByRole('checkbox', { name: /hyd:station:view/ }));
    expect(screen.getByRole('checkbox', { name: /hyd:station:view/ })).toBeChecked();

    // Sang vai trò khác: nháp phải rơi về dữ liệu của vai trò MỚI.
    await nguoiDung.click(screen.getByText('SUPER_ADMIN'));
    await waitFor(() =>
      expect(screen.getByRole('checkbox', { name: /adm:role:manage/ })).toBeChecked(),
    );
    expect(screen.getByRole('checkbox', { name: /hyd:station:view/ })).not.toBeChecked();
  });

  it('⛔ ⛔ Không có adm:role:manage → danh mục VẪN hiện đủ, chỉ ⛔ không tick được', async () => {
    // Ẩn hẳn quyền đi thì người dùng ⛔ không phân biệt được "⛔ không có quyền ấy" với "quyền ấy
    // ⛔ không tồn tại" — hai câu trả lời khác nhau cho cùng một màn hình trống (luật 9).
    hasPermission.mockImplementation((code: string) => code !== 'adm:role:manage');
    const nguoiDung = userEvent.setup();
    dung();

    await nguoiDung.click(await screen.findByText('BIEN_TAP'));
    await waitFor(() => expect(screen.getAllByRole('checkbox')).toHaveLength(DANH_MUC.length));

    expect(screen.queryByRole('button', { name: 'Lưu' })).not.toBeInTheDocument();
    expect(screen.getByText(/Bạn đang xem ma trận phân quyền/)).toBeInTheDocument();
  });

  it('Nút Lưu tắt khi chưa đổi gì — và Hoàn tác trả về đúng tập ban đầu', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await nguoiDung.click(await screen.findByText('BIEN_TAP'));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Lưu' })).toBeDisabled());

    await nguoiDung.click(screen.getByRole('checkbox', { name: /hyd:station:view/ }));
    expect(screen.getByRole('button', { name: 'Lưu' })).toBeEnabled();

    await nguoiDung.click(screen.getByRole('button', { name: 'Hoàn tác' }));
    expect(screen.getByRole('checkbox', { name: /hyd:station:view/ })).not.toBeChecked();
    expect(screen.getByRole('button', { name: 'Lưu' })).toBeDisabled();
    expect(put).not.toHaveBeenCalled();
  });

  it('⭐ Danh mục là nguồn ô đánh dấu — quyền vai trò CHƯA có vẫn phải hiện ra', async () => {
    // Chống tập rỗng (luật 7) và chống một khuyết tật cụ thể: dựng ô từ `permissionsOfRole` thay vì
    // từ danh mục thì màn hình *chỉ hiện quyền đã có* ⇒ ⛔ không bao giờ THÊM được quyền nào, mà
    // trông vẫn hoàn toàn bình thường.
    const nguoiDung = userEvent.setup();
    dung();

    await nguoiDung.click(await screen.findByText('BIEN_TAP'));
    await waitFor(() => expect(screen.getAllByRole('checkbox')).toHaveLength(DANH_MUC.length));
    expect(DANH_MUC.length).toBeGreaterThan(1);
  });
});

const VAI_TRO = [
  { code: 'BIEN_TAP', name: 'Biên tập viên', description: null, permissionCount: 1, isSystem: false },
  {
    code: 'SUPER_ADMIN',
    name: 'Quản trị tối cao',
    description: null,
    permissionCount: 2,
    isSystem: true,
  },
];

const DANH_MUC = [
  { code: 'adm:role:manage', module: 'adm', name: 'Sửa ma trận phân quyền', description: null },
  { code: 'cms:article:create', module: 'cms', name: 'Tạo bài viết', description: null },
  { code: 'hyd:station:view', module: 'hyd', name: 'Xem điểm đo', description: null },
];

/**
 * ⚠⚠ Bọc `<AntdApp>` là BẮT BUỘC, ⛔ không phải một chi tiết trang trí.
 *
 * `RolesPage` gọi `App.useApp()`. Thiếu provider thì hook ấy vẫn trả về một object — nhưng
 * `message.error` là `undefined`. Hậu quả đo được ở lượt chạy đầu của tệp này: `onSuccess` ném
 * *"message.success is not a function"*, react-query bắt lấy rồi rơi sang `onError`, `onError` ném
 * tiếp, và Vitest báo **6/6 xanh kèm 1 unhandled rejection** — mã thoát 1 mà bảng kết quả toàn màu
 * xanh.
 *
 * ⇒ Chính là luật 9 ở phía khung dựng bài kiểm: một bài kiểm ⛔ không phân biệt được *"mutation
 * thành công"* với *"mutation thành công rồi ném ở nhánh báo tin"* thì ⛔ không khẳng định gì về vế
 * thứ hai.
 */
function dung() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <AntdApp>
        <RolesPage />
      </AntdApp>
    </QueryClientProvider>,
  );
}
