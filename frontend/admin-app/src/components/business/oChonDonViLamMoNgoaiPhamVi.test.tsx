import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';

/**
 * **Ô chọn đơn vị làm MỜ nút ngoài phạm vi ghi — T74.11.**
 *
 * ## Khuyết tật
 *
 * `/org-units/selectable` trả **toàn cây** cho mọi biểu mẫu. Sau T74.8 backend chặn đúng, nhưng
 * người dùng chỉ biết sau khi đã điền xong cả biểu mẫu và bấm Lưu — và câu họ nhận là `AUTH-3002`,
 * một mã ⛔ nói được ô nào sai.
 *
 * ## Ba vế, và vế thứ ba là thứ dễ làm hỏng nhất
 *
 * 1. Ngoài phạm vi ⇒ **⛔ chọn được**, mà **VẪN HIỆN RA**. Ẩn nó đi là nút con *trong* phạm vi mất
 *    đường hiển thị — cùng lý lẽ đã ghi sẵn trong component từ trước cho `onlyTypes`.
 * 2. **Vế phân biệt**: ⛔ bật `chiTrongPhamVi` thì **⛔ làm mờ gì** — ⛔ có vế này thì một bản luôn
 *    làm mờ cũng xanh ở vế 1, và nó sẽ khoá 6 ô chọn mà backend ⛔ hề chặn (luật 9).
 * 3. **Phản hồi CŨ còn trong đệm ⛔ có trường `trongPhamVi`.** `undefined` phải đọc là *chưa biết* và
 *    **⛔ làm mờ**. Viết `!node.trongPhamVi` là làm mờ **toàn bộ cây** cho tới khi đệm được thay —
 *    tức khoá người dùng ra khỏi chính đơn vị của họ, im lặng (quy tắc 16).
 */

const getGia = vi.fn();

vi.mock('@/shared/apiClient', () => ({
  api: { get: (url: string) => getGia(url) as unknown },
}));

/** Gốc ngoài phạm vi, XN-A trong, XN-B ngoài — người dùng đứng ở XN-A. */
function cay(coCo: boolean) {
  const nut = (publicId: string, code: string, name: string, trongPhamVi: boolean) => ({
    publicId,
    code,
    name,
    shortName: null,
    unitType: 'XI_NGHIEP',
    path: `/1/${publicId}/`,
    depth: 1,
    sortOrder: 0,
    active: true,
    address: null,
    phone: null,
    email: null,
    headUserPublicId: null,
    deputyUserPublicId: null,
    ...(coCo ? { trongPhamVi } : {}),
    children: [],
  });
  return [
    {
      ...nut('dv-goc', 'CTY', 'Công ty Sông Nhuệ', false),
      path: '/1/',
      depth: 0,
      children: [
        nut('dv-a', 'XN-A', 'Xí nghiệp A', true),
        nut('dv-b', 'XN-B', 'Xí nghiệp B', false),
      ],
    },
  ];
}

function dung(props: { chiTrongPhamVi?: boolean; onChange: (v: string | undefined) => void }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AntdApp>
      <QueryClientProvider client={queryClient}>
        <OrgUnitTreeSelect {...props} />
      </QueryClientProvider>
    </AntdApp>,
  );
}

describe('Ô chọn đơn vị và phạm vi ghi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getGia.mockResolvedValue(cay(true));
  });

  afterEach(cleanup);

  it('⭐⭐ ngoài phạm vi thì ⛔ chọn được, mà VẪN HIỆN RA — ẩn nó là con mất đường hiển thị', async () => {
    const nguoiDung = userEvent.setup();
    const onChange = vi.fn();
    dung({ chiTrongPhamVi: true, onChange });

    await nguoiDung.click(screen.getByRole('combobox'));

    // Tiền đề, ⛔ phải kết luận: cả ba nút phải có mặt trước khi hỏi nút nào bấm được.
    expect(await screen.findByText('Xí nghiệp A')).toBeTruthy();
    expect(screen.getByText('Xí nghiệp B')).toBeTruthy();
    expect(screen.getByText('Công ty Sông Nhuệ')).toBeTruthy();

    await nguoiDung.click(screen.getByText('Xí nghiệp B'));
    expect(onChange).not.toHaveBeenCalled();

    await nguoiDung.click(screen.getByText('Xí nghiệp A'));
    expect(onChange).toHaveBeenCalledWith('dv-a', expect.anything(), expect.anything());
  });

  it('⭐ vế phân biệt: ⛔ bật `chiTrongPhamVi` thì ⛔ làm mờ gì — 6 ô chọn kia backend ⛔ hề chặn', async () => {
    const nguoiDung = userEvent.setup();
    const onChange = vi.fn();
    dung({ onChange });

    await nguoiDung.click(screen.getByRole('combobox'));
    await nguoiDung.click(await screen.findByText('Xí nghiệp B'));

    expect(onChange).toHaveBeenCalledWith('dv-b', expect.anything(), expect.anything());
  });

  it('⛔⛔ phản hồi CŨ trong đệm ⛔ có trường `trongPhamVi` ⇒ ⛔ được làm mờ gì cả', async () => {
    // `!node.trongPhamVi` sẽ làm mờ TOÀN BỘ cây ở đây — kể cả đơn vị của chính người dùng — cho
    // tới khi đệm 10 phút được thay. Hỏng theo chiều khoá người dùng ra ngoài, và im lặng.
    getGia.mockResolvedValue(cay(false));
    const nguoiDung = userEvent.setup();
    const onChange = vi.fn();
    dung({ chiTrongPhamVi: true, onChange });

    await nguoiDung.click(screen.getByRole('combobox'));
    await nguoiDung.click(await screen.findByText('Xí nghiệp A'));

    expect(onChange).toHaveBeenCalledWith('dv-a', expect.anything(), expect.anything());
  });
});
