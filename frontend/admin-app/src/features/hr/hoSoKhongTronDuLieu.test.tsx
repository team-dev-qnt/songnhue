import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import type React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';
import type * as ApiClientModule from '@/shared/apiClient';

/**
 * ⛔⛔⛔ **Hai hồ sơ ⛔ KHÔNG được trộn dữ liệu vào nhau** — WS-51.
 *
 * <h2>Vì sao bài này tồn tại: một cơ chế ĐÚNG ở chỗ sai vẫn hỏng</h2>
 *
 * `EmployeeFormModal` đã đọc T41.9 và đặt `key={publicId}` lên biểu mẫu con kèm chú thích
 * *"`initialValues` của AntD chỉ đổ dữ liệu ở lượt mount ĐẦU"*. Lập luận ấy **đúng** — và **vẫn ⛔
 * không đủ**, vì `Form.useForm()` nằm ở component **NGOÀI**: `key` dựng lại phần hiển thị, nhưng
 * *kho giá trị* sống trên **thực thể form**, và thực thể ấy ⛔ không unmount theo.
 *
 * <p>`rc-field-form` áp `initialValues` bằng `setInitialValues(values, init)` với
 * `init = !this.initialized`. Lượt mở thứ hai có `init === false` ⇒ nó **ghi nhớ** giá trị ban đầu
 * mới nhưng ⛔ **không** ghi đè kho — và `preserve` mặc định `true` nên `destroyOnClose` cũng ⛔
 * không dọn.
 *
 * <h2>⛔⛔ Vì sao đây ⛔ KHÔNG phải một lỗi giao diện</h2>
 *
 * Hậu quả ⛔ không phải *"ô hiện sai"* mà là **`PUT` gửi họ tên, quê quán, ngày sinh của người A lên
 * hồ sơ của người B**, kèm một thông báo *"Đã cập nhật hồ sơ cán bộ"*. Đó là §11.19 ở dạng nặng
 * hơn — ⛔ không phải đánh rơi một trường, mà **trộn dữ liệu cá nhân giữa hai con người**
 * (NĐ 13/2023), im lặng, ⛔ không mã lỗi nào.
 *
 * <p>Với `SensitiveModal` thì nó là một lượt **rò CCCD / lương / số tài khoản** qua đúng cái cửa
 * dựng ra để bảo vệ chúng — và dòng `security_events` sẽ ghi *"đọc hồ sơ B"* trong khi màn hình
 * đang hiện số của A.
 *
 * <h2>⚠ Khẳng định phải là "giá trị của B XUẤT HIỆN", ⛔ không phải "giá trị của A biến mất"</h2>
 *
 * Luật 9. `not.toHaveValue(cuaA)` xanh trọn vẹn khi biểu mẫu ⛔ không dựng, khi component sập, hoặc
 * khi ô bị đổi tên — ba trạng thái tệ hơn hẳn lỗi đang sửa. Nên mỗi bài dưới đây khẳng định **giá
 * trị đúng của B**, và bài `PUT` khẳng định **thân yêu cầu thật sự gửi đi**.
 */

const A = {
  publicId: 'hs-a',
  code: 'WS51UI-A',
  fullName: 'Nguyễn Văn A',
  dateOfBirth: '1988-04-17',
  gender: 'NAM',
  ethnicity: 'Kinh',
  hometown: 'Xã Đại Áng, Thanh Trì',
  address: 'Số 1 phố A',
  phone: '0912345678',
  workEmail: 'a@songnhue.local',
  personalEmail: null,
  maritalStatus: 'DA_KET_HON',
  emergencyContactName: 'Người nhà A',
  emergencyContactPhone: '0900000001',
  orgUnitId: 'dv-1',
  orgUnitName: 'Xí nghiệp 1',
  positionId: null,
  positionName: null,
  jobTitle: 'Kỹ thuật viên',
  hiredAt: '2015-03-02',
  contractType: 'KHONG_XAC_DINH_THOI_HAN',
  contractSignedAt: '2015-03-02',
  contractExpiresAt: null,
  status: 'DANG_LAM',
  terminatedAt: null,
  terminationReason: null,
  sensitive: {
    cccdDaCo: true,
    luongDaCo: false,
    taiKhoanDaCo: true,
    mstDaCo: false,
    bhxhDaCo: false,
  },
};

/** ⚠ B cố ý để TRỐNG những ô A có: ô trống là chỗ dữ liệu của A chảy sang mà ⛔ không ai thấy. */
const B = {
  ...A,
  publicId: 'hs-b',
  code: 'WS51UI-B',
  fullName: 'Trần Thị B',
  dateOfBirth: '1995-11-08',
  gender: 'NU',
  ethnicity: null,
  hometown: null,
  address: null,
  phone: null,
  workEmail: null,
  maritalStatus: 'DOC_THAN',
  emergencyContactName: null,
  emergencyContactPhone: null,
  jobTitle: null,
  hiredAt: '2021-01-04',
  contractType: 'XAC_DINH_THOI_HAN',
  contractSignedAt: '2021-01-04',
  contractExpiresAt: '2027-01-03',
  sensitive: {
    cccdDaCo: false,
    luongDaCo: false,
    taiKhoanDaCo: false,
    mstDaCo: false,
    bhxhDaCo: false,
  },
};

const KIN_A = {
  nationalId: '001111111111',
  nationalIdIssuedOn: '2020-01-01',
  nationalIdIssuedPlace: 'Cục CSQLHC',
  baseSalary: '9000000',
  salaryCoefficient: '3.66',
  bankAccount: '1900AAAA',
  taxCode: '8000000001',
  socialInsuranceNo: '0100000001',
};

/** B chưa nhập gì — mọi ô rỗng, đúng trạng thái mà dữ liệu của A dễ chảy vào nhất. */
const KIN_B = {
  nationalId: null,
  nationalIdIssuedOn: null,
  nationalIdIssuedPlace: null,
  baseSalary: null,
  salaryCoefficient: null,
  bankAccount: null,
  taxCode: null,
  socialInsuranceNo: null,
};

const put = vi.fn(async () => undefined);
const post = vi.fn(async () => A);

/**
 * ⛔ Giữ NGUYÊN `ApiClientError` thật.
 *
 * Thay cả module thì `caught instanceof ApiClientError` trong hai hộp thoại luôn `false`, và bài
 * kiểm khi ấy nói về một màn hình khác với màn hình thật (bài học của
 * `soanBaiKhongMatDuLieu.test.tsx`).
 */
vi.mock('@/shared/apiClient', async () => {
  const that = await vi.importActual<typeof ApiClientModule>('@/shared/apiClient');
  return {
    ...that,
    api: {
      get: vi.fn(async (duongDan: string) => {
        if (duongDan === '/org-units/selectable') {
          return [
            {
              publicId: 'dv-1',
              name: 'Xí nghiệp 1',
              code: 'XN1',
              unitType: 'XI_NGHIEP',
              active: true,
              children: [],
            },
          ];
        }
        if (duongDan === '/hr/positions') return [];
        if (duongDan === '/hr/employees/hs-a') return A;
        if (duongDan === '/hr/employees/hs-b') return B;
        if (duongDan === '/hr/employees/hs-a/sensitive') return KIN_A;
        if (duongDan === '/hr/employees/hs-b/sensitive') return KIN_B;
        throw new Error('Bài kiểm ⛔ không dựng đường dẫn này: ' + duongDan);
      }),
      post,
      put,
      delete: vi.fn(),
      getPage: vi.fn(),
    },
  };
});

const { EmployeeFormModal } = await import('./EmployeeFormModal');
const { SensitiveModal } = await import('./SensitiveModal');

function boc(children: React.ReactNode, qc: QueryClient) {
  const chuaKhai = () => {
    throw new Error('AuthContext giả: bài kiểm này ⛔ không dựng phần đó — khai thêm nếu cần');
  };
  const auth = {
    status: 'authenticated' as const,
    user: null,
    hasPermission: () => true,
    hasRole: () => true,
    maintenance: false,
    login: chuaKhai,
    verifyTwoFactor: chuaKhai,
    confirmEnrollment: chuaKhai,
    logout: chuaKhai,
    endSession: chuaKhai,
    reloadProfile: chuaKhai,
  } as unknown as AuthContextValue;

  return (
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>{children}</AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}

let qc: QueryClient;

beforeEach(() => {
  put.mockClear();
  post.mockClear();
  qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
});

afterEach(() => {
  cleanup();
  qc.clear();
});

describe('hộp thoại hồ sơ ⛔ không mang dữ liệu của hồ sơ trước sang hồ sơ sau', () => {
  it('⛔⛔ mở A → đóng → mở B: mọi ô mang giá trị của B', async () => {
    const { rerender } = render(
      boc(<EmployeeFormModal open publicId="hs-a" onClose={() => {}} onSaved={() => {}} />, qc),
    );
    await waitFor(() => expect(screen.getByLabelText('Họ và tên')).toHaveValue(A.fullName));

    rerender(
      boc(
        <EmployeeFormModal open={false} publicId="hs-a" onClose={() => {}} onSaved={() => {}} />,
        qc,
      ),
    );
    rerender(
      boc(<EmployeeFormModal open publicId="hs-b" onClose={() => {}} onSaved={() => {}} />, qc),
    );

    // ⚠ Khẳng định giá trị ĐÚNG của B, ⛔ không phải "⛔ không còn giá trị của A" — luật 9.
    await waitFor(() => expect(screen.getByLabelText('Họ và tên')).toHaveValue(B.fullName));
    expect(screen.getByLabelText('Mã cán bộ')).toHaveValue(B.code);
    // Ô B để TRỐNG: đây là chỗ dữ liệu của A chảy sang mà ⛔ không ai thấy.
    expect(screen.getByLabelText('Quê quán')).toHaveValue('');
    expect(screen.getByLabelText('Điện thoại')).toHaveValue('');
  });

  it('⛔⛔ và thân PUT gửi đi phải là dữ liệu của B', async () => {
    const nguoiDung = userEvent.setup();
    const { rerender } = render(
      boc(<EmployeeFormModal open publicId="hs-a" onClose={() => {}} onSaved={() => {}} />, qc),
    );
    await waitFor(() => expect(screen.getByLabelText('Họ và tên')).toHaveValue(A.fullName));

    rerender(
      boc(
        <EmployeeFormModal open={false} publicId="hs-a" onClose={() => {}} onSaved={() => {}} />,
        qc,
      ),
    );
    rerender(
      boc(<EmployeeFormModal open publicId="hs-b" onClose={() => {}} onSaved={() => {}} />, qc),
    );
    await waitFor(() => expect(screen.getByLabelText('Họ và tên')).toHaveValue(B.fullName));

    await nguoiDung.click(screen.getByRole('button', { name: 'Lưu' }));

    await waitFor(() => expect(put).toHaveBeenCalled());
    const [duongDan, than] = put.mock.calls[0] as unknown as [string, Record<string, unknown>];
    expect(duongDan).toBe('/hr/employees/hs-b');
    expect(than.fullName).toBe(B.fullName);
    // ⛔ Ô B để trống phải đi ra là rỗng/null — ⛔ không phải giá trị của A.
    expect(than.hometown ?? null).toBeNull();
    expect(than.phone ?? null).toBeNull();
  });

  it('⛔⛔⛔ hộp thoại 🔒: CCCD và số tài khoản của A ⛔ không được hiện dưới tên B', async () => {
    const { rerender } = render(
      boc(<SensitiveModal open publicId="hs-a" tenCanBo="Nguyễn Văn A" onClose={() => {}} />, qc),
    );
    await waitFor(() => expect(screen.getByLabelText('Số CCCD')).toHaveValue(KIN_A.nationalId));

    rerender(
      boc(
        <SensitiveModal open={false} publicId="hs-a" tenCanBo="Nguyễn Văn A" onClose={() => {}} />,
        qc,
      ),
    );
    rerender(
      boc(<SensitiveModal open publicId="hs-b" tenCanBo="Trần Thị B" onClose={() => {}} />, qc),
    );

    // Đối chứng phải-tìm-thấy: hộp thoại của B ĐÃ dựng (⛔ không phải "⛔ không tìm thấy ô nào").
    await waitFor(() => expect(screen.getByLabelText('Số CCCD')).toBeInTheDocument());
    expect(screen.getByLabelText('Số CCCD')).toHaveValue('');
    expect(screen.getByLabelText('Số tài khoản')).toHaveValue('');
  });
});
