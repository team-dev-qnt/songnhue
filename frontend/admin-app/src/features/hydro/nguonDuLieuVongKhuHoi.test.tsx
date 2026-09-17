import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa một nguồn dữ liệu → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 8 trường** — T47.17, và biểu
 * mẫu này canh một loại hậu quả **⛔ màn hình nào khác có**: mất dữ liệu **VĨNH VIỄN**.
 *
 * <h2>Vì sao bốn ô tham số nhịp là chỗ nguy hiểm</h2>
 *
 * `cron` · `frameMinutes` · `timeoutSeconds` · `maxRetry` có `null` **MANG NGHĨA** — *dùng tham số
 * chung ở Cấu hình hệ thống* — chứ ⛔ phải *"chưa cấu hình"*. Nên đánh rơi một ô ⛔ sinh lỗi nào,
 * ⛔ dòng log nào: nguồn **lặng lẽ đổi nhịp** sang tham số chung. Với một nguồn cố ý chạy nhịp
 * riêng, đó là bỏ lỡ khung 10 phút — mà **quy tắc 18** nói nguồn ⛔ có API lịch sử ⇒ **mất dữ liệu
 * là vĩnh viễn**. T50.1 và T52.0 đã trả giá đúng ở màn hình này: 9 ngày với `hydro_raw_logs = 0`.
 *
 * <h2>⛔ Mã số truy cập ⛔ nằm trong biểu mẫu này — và đó là thiết kế</h2>
 *
 * `ApiSourceRequest` **⛔ có** trường credential: mã số đi bằng endpoint riêng
 * ({@code PUT /{id}/credential}) để mỗi lượt chạm vào nó đều sinh một sự kiện bảo mật. Nên bài này
 * đọc danh sách trường **từ chính record ấy** — nếu ai đó nhét credential vào biểu mẫu sửa hồ sơ,
 * vế chống-tập-rỗng dưới đây đỏ ngay.
 *
 * <h2>Vế thứ hai: ⛔ được trộn hai nguồn</h2>
 *
 * Xem `donViVongKhuHoi.test.tsx` (T63.12) — màn hình đơn vị đã hỏng đúng kiểu ấy. Ở đây `moSua()`
 * gọi `resetFields()` rồi `setFieldsValue()` **tường minh**, tức cơ chế đúng đã có; vế này **khoá
 * nó lại** để một lượt dọn dẹp sau ⛔ lặng lẽ tháo mất.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTOS = readFileSync(
  join(GOC_KHO, 'backend/hydro/src/main/java/com/songnhue/hydro/api/HydroCatalogDtos.java'),
  'utf8',
);

function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(DTOS);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong HydroCatalogDtos.java`);
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .replace(/<[^<>]*>/g, '')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

/**
 * Hai nguồn, **mọi ô đều có giá trị riêng**.
 *
 * ⚠ Bốn ô nhịp của nguồn A cố ý **khác** tham số chung: một nguồn để trống cả bốn ⛔ phân biệt được
 * *"đánh rơi ô"* với *"vốn dùng tham số chung"* (luật 9) — đúng chỗ khuyết tật này ẩn được.
 */
const NGUON_A = {
  id: 'ng-a',
  code: 'BHH40',
  name: 'Trạm đo tự động bhh40',
  adapterType: 'BHH40' as const,
  baseUrl: 'http://songnhue.bhh40.net/songnhue',
  credentialDaCauHinh: true,
  status: 'ACTIVE' as const,
  cron: '45 1/2 * * * *',
  frameMinutes: 10,
  timeoutSeconds: 20,
  maxRetry: 4,
  cronHieuLuc: '45 1/2 * * * *',
  cronDungChung: false,
  khungNguonPhutHieuLuc: 10,
  khungDungChung: false,
  timeoutGiayHieuLuc: 20,
  timeoutDungChung: false,
  soLanThuLaiHieuLuc: 4,
  thuLaiDungChung: false,
  lastSuccessAt: null,
  lastFailureAt: null,
  lastFailureReason: null,
  consecutiveFailures: 0,
  soDiemDo: 19,
  description: 'Nguồn chính — nhịp riêng vì khung phát của trạm lệch với tham số chung',
};

const NGUON_B = {
  ...NGUON_A,
  id: 'ng-b',
  code: 'DUPHONG',
  name: 'Nguồn dự phòng thủ công',
  baseUrl: 'http://duphong.example.vn/api',
  status: 'PAUSED' as const,
  cron: '0 0/15 * * * *',
  frameMinutes: 15,
  timeoutSeconds: 45,
  maxRetry: 2,
  cronHieuLuc: '0 0/15 * * * *',
  khungNguonPhutHieuLuc: 15,
  timeoutGiayHieuLuc: 45,
  soLanThuLaiHieuLuc: 2,
  soDiemDo: 0,
  description: 'Chỉ bật khi nguồn chính hỏng',
};

let duongCuoi = '';
let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong === '/hyd/api-sources') return [NGUON_A, NGUON_B];
      return [];
    }),
    put: vi.fn(async (duong: string, than: unknown) => {
      duongCuoi = duong;
      thanCuoi = than as Record<string, unknown>;
      return NGUON_A;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI hay GỌI THỬ');
    }),
    delete: vi.fn(),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { ApiSourcesPage } = await import('./ApiSourcesPage');

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
          <ApiSourcesPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

async function moSua(nguoiDung: ReturnType<typeof userEvent.setup>, nguon: { code: string }) {
  await nguoiDung.click(await screen.findByRole('button', { name: `Sửa nguồn ${nguon.code}` }));
  await screen.findByText(`Sửa nguồn ${nguon.code}`);
}

afterEach(() => {
  cleanup();
  duongCuoi = '';
  thanCuoi = {};
});

describe('Nguồn dữ liệu — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 8 trường, và ⛔ trường nào mang mã số', () => {
    const truong = truongCuaRecord('ApiSourceRequest');
    expect(truong.length).toBeGreaterThanOrEqual(8);
    expect(truong).toEqual(
      expect.arrayContaining(['cron', 'frameMinutes', 'timeoutSeconds', 'maxRetry', 'status']),
    );
    expect(
      truong.filter((t) => /maSo|credential|key|secret/i.test(t)),
      '⛔ Mã số truy cập ⛔ được nằm trong biểu mẫu sửa hồ sơ — nó đi bằng `PUT /{id}/credential` ' +
        'để mỗi lượt chạm đều sinh một sự kiện bảo mật (quy tắc 13).',
    ).toEqual([]);
  });

  it('⭐⭐ sửa nguồn → Lưu ⛔ đổi gì ⇒ đủ 8 trường, bốn ô nhịp riêng còn nguyên', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, NGUON_A);
    await screen.findByDisplayValue(NGUON_A.cron);

    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('ApiSourceRequest')
      .map((t) => ({ t, kyVong: (NGUON_A as Record<string, unknown>)[t], thucTe: thanCuoi[t] }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `cron`/`frameMinutes`/`timeoutSeconds`/' +
        '`maxRetry` có `null` MANG NGHĨA *dùng tham số chung*, nên đánh rơi ⛔ sinh lỗi nào — nguồn ' +
        'lặng lẽ đổi nhịp, bỏ lỡ khung 10 phút, và quy tắc 18 nói mất dữ liệu là VĨNH VIỄN.',
    ).toEqual([]);
  });

  it('⭐⭐ mở nguồn A → đóng → mở nguồn B ⇒ ô mang nhịp của B, Lưu ghi vào ĐÚNG B', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, NGUON_A);
    await screen.findByDisplayValue(NGUON_A.cron);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Cancel' }));

    await moSua(nguoiDung, NGUON_B);
    expect(
      screen.queryByDisplayValue(NGUON_A.cron),
      '⛔ Ô Cron đang bày nhịp của nguồn MỞ TRƯỚC ⇒ một lượt Lưu ép nguồn B chạy theo nhịp của A ' +
        '(T63.12 · T51.12). Với nguồn thuỷ văn, sai nhịp là mất khung đo — vĩnh viễn.',
    ).toBeNull();
    await screen.findByDisplayValue(NGUON_B.cron);

    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    expect(duongCuoi).toBe(`/hyd/api-sources/${NGUON_B.id}`);
    expect(thanCuoi).toMatchObject({
      name: NGUON_B.name,
      baseUrl: NGUON_B.baseUrl,
      cron: NGUON_B.cron,
      frameMinutes: NGUON_B.frameMinutes,
      timeoutSeconds: NGUON_B.timeoutSeconds,
      maxRetry: NGUON_B.maxRetry,
      status: NGUON_B.status,
    });
  });
});
