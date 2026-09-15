import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';
import {
  type BiMatTinhTrangView,
  type SettingView,
  type TongQuanCauHinhView,
} from '@/shared/api-types';
import type * as ApiClientModule from '@/shared/apiClient';

/**
 * **Nhập lại mã 2FA trước thao tác nhạy cảm** — T61.42 / T61.44 (QuanTran chốt 15/09/2026).
 *
 * Vế backend: `CauHinhHeThongHttpTest` (403 thay 401, đếm lượt sai, chỉ SUPER_ADMIN, ⛔ trả giá trị).
 * Vế ở đây: màn hình ⛔ gửi lượt ghi nhóm nhạy cảm khi CHƯA có mã, gửi KÈM mã khi có, và mã sai ⛔ đóng
 * hộp thoại (người dùng nhập lại được mà ⛔ phải bấm Lưu từ đầu).
 */

const goi = vi.fn();
let loiLuotGhi: unknown = null;

const THAM_SO = (nhom: string, nhayCam: boolean): SettingView => ({
  key: `${nhom.toLowerCase()}.thu`,
  value: 'cu',
  effectiveValue: 'cu',
  valueType: 'STRING',
  defaultValue: null,
  groupCode: nhom,
  label: `Tham số ${nhom}`,
  description: null,
  validation: null,
  editable: true,
  exportable: true,
  canXacThucLai: nhayCam,
});

let danhSachThamSo: SettingView[] = [];

const TONG_QUAN: TongQuanCauHinhView = {
  soChan: 1,
  soCanhBao: 0,
  muc: [
    {
      ma: 'PROMETHEUS',
      nhom: 'Giám sát & cảnh báo',
      ten: 'Prometheus đọc được chỉ số của ứng dụng',
      trangThai: 'THIEU',
      mucDo: 'CHAN',
      nguoiDoc: 'nginx → Prometheus',
      datO: '.env cả hai máy — METRICS_*',
      ghiChu: '5 phút qua ⛔ lượt đọc nào',
    },
  ],
};

const BI_MAT: BiMatTinhTrangView[] = [
  {
    loai: 'RECAPTCHA_SECRET_KEY',
    ten: 'Khoá bí mật reCAPTCHA',
    moTa: 'Google reCAPTCHA v3',
    nguon: 'CHUA_CO',
    giaiMaDuoc: true,
    coGiaTriMoi: false,
    capNhatLuc: null,
  },
];

vi.mock('@/shared/apiClient', async (importOriginal) => {
  const thuc = await importOriginal<typeof ApiClientModule>();
  const ghi = async (dongTu: string, url: string, than: unknown) => {
    goi(dongTu, url, than);
    if (loiLuotGhi) {
      const l = loiLuotGhi;
      loiLuotGhi = null;
      throw l;
    }
    return {};
  };
  return {
    ...thuc,
    api: {
      ...thuc.api,
      get: vi.fn(async (url: string) =>
        url === '/settings'
          ? danhSachThamSo
          : url === '/system/cau-hinh'
            ? TONG_QUAN
            : url === '/system/cau-hinh/bi-mat'
              ? BI_MAT
              : [],
      ),
      put: vi.fn((url: string, than: unknown) => ghi('PUT', url, than)),
      post: vi.fn((url: string, than: unknown) => ghi('POST', url, than)),
    },
  };
});

const { ApiClientError } = await import('@/shared/apiClient');
const { SettingsPage } = await import('./SettingsPage');
const { CauHinhHeThongPage } = await import('./CauHinhHeThongPage');

function dung(trang: React.ReactNode, quyen: string[]) {
  const khongDung = () => {
    throw new Error('AuthContext giả');
  };
  const auth = {
    status: 'authenticated' as const,
    user: { id: 'toi', username: 'sieuquantri' },
    hasPermission: (q: string) => quyen.includes(q),
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
        <AntdApp>{trang}</AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  goi.mockClear();
  loiLuotGhi = null;
});

async function doiGiaTriRoiLuu(nguoiDung: ReturnType<typeof userEvent.setup>) {
  const o = await screen.findByDisplayValue('cu');
  await nguoiDung.clear(o);
  await nguoiDung.type(o, 'moi');
  await nguoiDung.click(screen.getByRole('button', { name: 'Lưu' }));
}

describe('Cấu hình hệ thống — nhập lại mã 2FA (T61.42)', () => {
  it('⭐⭐ tham số nhóm nhạy cảm: bấm Lưu ⛔ gửi gì, mở hộp thoại; nhập mã ⇒ PUT KÈM maXacThuc', async () => {
    danhSachThamSo = [THAM_SO('SECURITY', true)];
    const nguoiDung = userEvent.setup();
    dung(<SettingsPage />, ['adm:setting:view', 'adm:setting:update']);

    await doiGiaTriRoiLuu(nguoiDung);
    expect(goi).not.toHaveBeenCalled();

    const o = await screen.findByLabelText('Mã xác thực hai bước');
    const xacNhan = screen.getByRole('button', { name: 'Xác nhận' });
    await nguoiDung.type(o, '12a34');
    expect(xacNhan).toBeDisabled(); // chữ bị lọc, còn 4 số — ⛔ gửi được
    await nguoiDung.type(o, '56');
    await nguoiDung.click(xacNhan);

    await waitFor(() =>
      expect(goi).toHaveBeenCalledWith('PUT', '/settings/security.thu', {
        value: 'moi',
        maXacThuc: '123456',
      }),
    );
  });

  it('⭐ đối chứng: tham số nhóm thường gửi NGAY, ⛔ hộp thoại', async () => {
    danhSachThamSo = [THAM_SO('SYSTEM', false)];
    const nguoiDung = userEvent.setup();
    dung(<SettingsPage />, ['adm:setting:view', 'adm:setting:update']);

    await doiGiaTriRoiLuu(nguoiDung);
    await waitFor(() =>
      expect(goi).toHaveBeenCalledWith('PUT', '/settings/system.thu', { value: 'moi' }),
    );
    expect(screen.queryByLabelText('Mã xác thực hai bước')).toBeNull();
  });

  it('⛔ mã sai (ADM-2024) ⇒ hộp thoại VẪN MỞ và hiện câu lỗi', async () => {
    danhSachThamSo = [THAM_SO('SECURITY', true)];
    const nguoiDung = userEvent.setup();
    dung(<SettingsPage />, ['adm:setting:view', 'adm:setting:update']);

    await doiGiaTriRoiLuu(nguoiDung);
    loiLuotGhi = new ApiClientError(
      'ADM-2024',
      'Mã xác thực hai bước không đúng',
      'caller',
      'warning',
      403,
      null,
      [],
    );
    await nguoiDung.type(await screen.findByLabelText('Mã xác thực hai bước'), '000000');
    await nguoiDung.click(screen.getByRole('button', { name: 'Xác nhận' }));

    expect(await screen.findByText('Mã xác thực hai bước không đúng')).toBeInTheDocument();
    expect(screen.getByLabelText('Mã xác thực hai bước')).toHaveValue('');
  });
});

describe('Tình trạng cấu hình + bí mật tích hợp (T61.41 / T61.44)', () => {
  it('⭐⭐ đặt bí mật: nhập giá trị ⇒ nhập mã ⇒ PUT mang CẢ HAI', async () => {
    const nguoiDung = userEvent.setup();
    dung(<CauHinhHeThongPage />, ['adm:system-config:view', 'adm:system-config:secret']);

    expect(await screen.findByText('Prometheus đọc được chỉ số của ứng dụng')).toBeInTheDocument();
    expect(screen.getByText('1 mục CHẶN cần xử lý')).toBeInTheDocument();

    await nguoiDung.click(await screen.findByRole('button', { name: 'Đặt' }));
    await nguoiDung.type(await screen.findByLabelText('Giá trị bí mật'), '  khoa-bi-mat-thu  ');
    await nguoiDung.click(screen.getByRole('button', { name: 'Tiếp tục' }));
    await nguoiDung.type(await screen.findByLabelText('Mã xác thực hai bước'), '654321');
    await nguoiDung.click(screen.getByRole('button', { name: 'Xác nhận' }));

    await waitFor(() =>
      expect(goi).toHaveBeenCalledWith('PUT', '/system/cau-hinh/bi-mat/RECAPTCHA_SECRET_KEY', {
        giaTri: 'khoa-bi-mat-thu',
        maXacThuc: '654321',
      }),
    );
  });

  it('⛔ chỉ có quyền xem ⇒ ⛔ nút Đặt/Xoá nào', async () => {
    dung(<CauHinhHeThongPage />, ['adm:system-config:view']);
    expect(await screen.findByText('Khoá bí mật reCAPTCHA')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Đặt' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Xoá' })).toBeNull();
  });
});
