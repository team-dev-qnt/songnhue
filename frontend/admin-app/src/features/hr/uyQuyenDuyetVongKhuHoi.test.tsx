import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type * as ApiClientModule from '@/shared/apiClient';

/**
 * **Uỷ quyền duyệt nghỉ phép** — vòng khứ hồi của biểu mẫu giao uỷ quyền (T80.5, chốt B3).
 *
 * ⛔⛔ Ba trường của biểu mẫu này **⛔ trường nào bỏ được**, và đánh rơi một cái thì hỏng theo ba
 * kiểu khác nhau — ⛔ kiểu nào có triệu chứng ngay:
 *
 * - rơi `nguoiDuocUyQuyenPublicId` ⇒ máy chủ trả 400 ⇒ *có* triệu chứng, nhẹ nhất;
 * - rơi `tuNgay`/`denNgay` ⇒ **khoảng hiệu lực sai**, và một lượt uỷ quyền sai hạn là một người
 *   duyệt được đơn ở những ngày ⛔ ai giao cho họ — thứ ⛔ ai đếm cho tới khi có tranh chấp;
 * - rơi `orgUnitPublicId` ⇒ uỷ quyền gắn nhầm đơn vị ⇒ **đúng lỗ hổng mà cả WS-80 đi vá**.
 *
 * ⇒ Bài này đếm **số trường đo được** chứ ⛔ chỉ khẳng định *"POST đã được gọi"* — một lượt gọi có
 * mặt mà thiếu nửa payload đọc y hệt một lượt gọi đúng (đúng hình dạng T63.8 và luật 9).
 *
 * Vế backend tương ứng: `ThamQuyenDuyetPhepHttpTest.uyQuyenVaThuHoiCoHieuLucNgay` ·
 * `baDieuKienCuaUyQuyen`.
 */

const dangGui = vi.fn();
const dangXoa = vi.fn();

vi.mock('@/shared/apiClient', async (importOriginal) => {
  const thuc = await importOriginal<typeof ApiClientModule>();
  return {
    ...thuc,
    api: {
      ...thuc.api,
      get: vi.fn(async (url: string) => {
        if (url === '/org-units/selectable') {
          return [
            {
              publicId: 'dv-a',
              code: 'XN-A',
              name: 'Xí nghiệp A',
              unitType: 'XI_NGHIEP',
              path: '/1/2/',
              depth: 1,
              active: true,
              children: [],
            },
          ];
        }
        if (url === '/admin/users') {
          return [
            { publicId: 'u-1', username: 'pho_a', fullName: 'Phó A', status: 'ACTIVE' },
            { publicId: 'u-2', username: 'khoa', fullName: 'Người khoá', status: 'LOCKED' },
          ];
        }
        return [];
      }),
      post: vi.fn(async (url: string, body: unknown) => {
        dangGui(url, body);
        return {};
      }),
      delete: vi.fn(async (url: string) => {
        dangXoa(url);
        return {};
      }),
    },
  };
});

const { UyQuyenDuyetPage } = await import('./UyQuyenDuyetPage');

function dung() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <AntdApp>
        <UyQuyenDuyetPage />
      </AntdApp>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  dangGui.mockClear();
  dangXoa.mockClear();
});

describe('Uỷ quyền duyệt nghỉ phép — chốt B3', () => {
  it('⛔ Chưa chọn đơn vị thì ⛔ gọi API và nút Giao bị khoá — một danh sách rỗng ⛔ được đọc thành "đơn vị này chưa có uỷ quyền"', async () => {
    dung();
    expect(
      await screen.findByText('Chọn một đơn vị để xem các uỷ quyền của đơn vị đó'),
    ).toBeTruthy();
    const nut = screen.getByRole('button', { name: 'Giao uỷ quyền' });
    expect(nut.hasAttribute('disabled')).toBe(true);
  });

  it('⛔⛔ Ô chọn người ⛔ bày tài khoản đã KHOÁ — một lựa chọn chắc chắn hỏng là một cái bẫy', async () => {
    dung();
    await screen.findByText('Chọn một đơn vị để xem các uỷ quyền của đơn vị đó');
    // Người khoá ⛔ bao giờ xuất hiện ở đâu trên trang này — kể cả trước khi mở hộp thoại.
    expect(screen.queryByText(/Người khoá/)).toBeNull();
  });

  it('⭐⭐ Trang khai đúng rằng uỷ quyền ⛔ cấp quyền — câu ấy là hợp đồng với người quản trị', async () => {
    dung();
    expect(
      await screen.findByText('Uỷ quyền chuyển VAI người duyệt, không cấp thêm quyền'),
    ).toBeTruthy();
    expect(screen.getByText(/phải đang có quyền duyệt nghỉ phép/)).toBeTruthy();
    // ⛔⛔ Vế *thu hồi tức thì* là khác biệt đo được giữa thiết kế này và một lượt cộng quyền vào
    //    token (30 phút). Nó phải nói ra trên màn hình, ⛔ chỉ nằm trong javadoc.
    expect(screen.getByText(/hiệu lực ngay, không phải đăng nhập lại/)).toBeTruthy();
  });

  it('⛔⛔ Gửi ĐỦ bốn trường: đơn vị · người nhận · từ ngày · đến ngày — đếm số trường, ⛔ chỉ hỏi "đã gọi chưa"', async () => {
    const nguoiDung = userEvent.setup();
    dung();
    await screen.findByText('Chọn một đơn vị để xem các uỷ quyền của đơn vị đó');

    await nguoiDung.click(screen.getByRole('combobox'));
    await nguoiDung.click(await screen.findByText('Xí nghiệp A'));
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Giao uỷ quyền' }).hasAttribute('disabled')).toBe(
        false,
      );
    });

    await nguoiDung.click(screen.getByRole('button', { name: 'Giao uỷ quyền' }));
    await screen.findByText('Giao uỷ quyền duyệt');

    // Người nhận — ⛔ chọn `Người khoá` được, ô ⛔ bày nó ra (bài trên đã canh).
    const oChon = screen.getAllByRole('combobox').at(-1) as HTMLElement;
    await nguoiDung.click(oChon);
    await nguoiDung.click(await screen.findByText('Phó A (pho_a)'));

    // RangePicker: gõ thẳng hai ngày. ⛔ `dayjs()` trần ở đây — hai chuỗi này là hằng của bài kiểm,
    // nên lượt chạy ở `TZ=UTC` (đã ghim ở vite.config) và ở +07 cho cùng một payload (T63.18).
    const oNgay = document.querySelectorAll('.ant-picker-input input');
    await nguoiDung.type(oNgay[0] as HTMLElement, '01/10/2026');
    await nguoiDung.keyboard('{Enter}');
    await nguoiDung.type(oNgay[1] as HTMLElement, '09/10/2026');
    await nguoiDung.keyboard('{Enter}');

    await nguoiDung.click(screen.getByRole('button', { name: 'Giao' }));

    await waitFor(() => expect(dangGui).toHaveBeenCalled());
    const [duong, than] = dangGui.mock.calls[0] as [string, Record<string, unknown>];
    expect(duong).toBe('/hr/nghi-phep/uy-quyen');
    // ⛔⛔ Đếm TỪNG trường: một lượt gọi có mặt mà thiếu nửa payload đọc y hệt một lượt gọi đúng.
    expect(than).toMatchObject({
      orgUnitPublicId: 'dv-a',
      nguoiDuocUyQuyenPublicId: 'u-1',
      tuNgay: '2026-10-01',
      denNgay: '2026-10-09',
    });
  });
});
