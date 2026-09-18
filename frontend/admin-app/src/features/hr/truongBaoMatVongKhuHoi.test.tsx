import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Mở hộp thoại 🔒 → bấm Lưu ⛔ sửa gì → payload phải mang lại ĐỦ 8 trường** — T47.17, và đây là
 * biểu mẫu **nguy hiểm nhất** trong nhóm còn nợ.
 *
 * <h2>Vì sao nguy hiểm nhất</h2>
 *
 * Tám trường này là dữ liệu cá nhân **đã mã hoá AES-256-GCM** trong bảng riêng
 * `employee_sensitive`: CCCD, ngày/nơi cấp, lương cơ bản, hệ số lương, số tài khoản, mã số thuế, số
 * sổ bảo hiểm. NĐ 13/2023 gọi tên đúng nhóm này, và quy tắc 10 của dự án dựng cả một bảng riêng cho
 * nó. Một trường rơi khỏi payload là **mất một mẩu dữ liệu cá nhân** mà ⛔ ai đếm — người nhập vào
 * ba tháng trước ⛔ mở lại ô ấy, còn người sửa hôm nay chỉ định đổi số tài khoản.
 *
 * <h2>Chỗ khe hở nằm</h2>
 *
 * `SensitiveModal.luu()` dựng payload bằng cách **liệt kê tay cả 8 khoá**. Đó đúng là hình dạng đã
 * gây ra `T63.8` ở biểu mẫu menu (`articleId: null` ghi cứng): một danh sách gõ tay ⛔ tự biết nó
 * thiếu gì. Bài này đọc danh sách trường từ **`HrDtos.SensitiveRequest`**, ⛔ chép tay — backend
 * thêm trường mà biểu mẫu ⛔ gửi thì nó đỏ ngay.
 *
 * <h2>⚠ Ô RỖNG ⛔ được đọc thành *"⛔ gửi"*</h2>
 *
 * `chu()` quy chuỗi rỗng về `null`, và `null` ở đây nghĩa là **xoá ô** — cố ý, vì người dùng phải
 * xoá được một số CCCD nhập nhầm. Nên bài này nạp **giá trị ở MỌI ô**: một ô để trống sẽ ⛔ phân
 * biệt được *"trường bị đánh rơi"* với *"trường cố ý rỗng"* (luật 9).
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTOS = readFileSync(
  join(GOC_KHO, 'backend/hr/src/main/java/com/songnhue/hr/api/HrDtos.java'),
  'utf8',
);

function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(DTOS);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong HrDtos.java`);
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .replace(/<[^<>]*>/g, '')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

/** Giá trị ở MỌI ô — xem javadoc: một ô rỗng làm bài kiểm mù trước chính thứ nó canh. */
const TRUONG_BAO_MAT = {
  nationalId: '001199012345',
  nationalIdIssuedOn: '2021-03-15',
  nationalIdIssuedPlace: 'Cục Cảnh sát QLHC về TTXH',
  baseSalary: '18500000',
  salaryCoefficient: '3.66',
  bankAccount: '19001234567890',
  taxCode: '8412345678',
  socialInsuranceNo: '0123456789',
};

let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async () => TRUONG_BAO_MAT),
    put: vi.fn(async (_duong: string, than: unknown) => {
      thanCuoi = than as Record<string, unknown>;
    }),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { SensitiveModal } = await import('./SensitiveModal');

function dung() {
  const chuaKhai = () => {
    throw new Error('AuthContext giả: bài kiểm này ⛔ dựng phần đó');
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
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>
          <SensitiveModal open publicId="nv-1" tenCanBo="Nguyễn Văn A" onClose={() => {}} />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  thanCuoi = {};
});

describe('Trường bảo mật 🔒 — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được đủ 8 trường từ SensitiveRequest', () => {
    const truong = truongCuaRecord('SensitiveRequest');
    expect(truong.length).toBeGreaterThanOrEqual(8);
    expect(truong).toEqual(
      expect.arrayContaining(['nationalId', 'bankAccount', 'baseSalary', 'socialInsuranceNo']),
    );
  });

  it('⭐⭐ mở hộp thoại → Lưu ⛔ sửa gì ⇒ cả 8 trường 🔒 quay lại nguyên vẹn', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    // Tiền đề: dữ liệu đã nạp thật vào ô. Thiếu vế này thì một hộp thoại RỖNG cũng "gửi đủ 8 khoá"
    // với toàn `null`, và bài kiểm xanh trong đúng tình huống nó sinh ra để bắt (luật 7).
    await screen.findByDisplayValue(TRUONG_BAO_MAT.bankAccount);

    await nguoiDung.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('SensitiveRequest')
      .map((t) => ({
        t,
        kyVong: (TRUONG_BAO_MAT as Record<string, unknown>)[t],
        thucTe: thanCuoi[t],
      }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường 🔒 bị đánh rơi sau một lượt Lưu ⛔ sửa gì. Đây là dữ liệu cá nhân ĐÃ MÃ HOÁ ' +
        '(CCCD, số tài khoản, lương) — `SensitiveService` ghi đè cả tám ô, nên một khoá thiếu ' +
        'trong payload là một ô bị XOÁ, im lặng. NĐ 13/2023 gọi tên đúng nhóm dữ liệu này.',
    ).toEqual([]);
  });
});
