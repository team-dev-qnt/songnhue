import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa một chức vụ → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 6 trường** — T47.17 (CN-04.2).
 *
 * <h2>Trường nào rơi thì hỏng ra sao</h2>
 *
 * <ul>
 *   <li><b>`code`</b> — nặng nhất, và nó là trường **DUY NHẤT của biểu mẫu này bị `disabled`** khi
 *       sửa. Mã chức vụ là khoá nối duy nhất giữa danh mục và hồ sơ CBNV đang giữ chức vụ ấy; ô bị
 *       khoá rất dễ bị đọc thành *"ô này ⛔ cần gửi lên"*, mà `PositionService.update` gọi
 *       {@code batBuoc(form.code())} ⇒ rơi nó là **`SYS-0003`** ở mọi lượt Lưu, tức chức vụ ấy
 *       ⛔ sửa nổi bằng giao diện (cùng hậu quả với `T63.8`).
 *   <li><b>`active`</b> — <b>im lặng theo chiều NGUY HIỂM</b>. `apDung` khai
 *       {@code setActive(form.active() == null || form.active())} ⇒ trường rơi ⇒ **BẬT LẠI** một
 *       chức vụ Công ty vừa cố ý ngừng dùng, và nó lập tức quay lại ô chọn của mọi hồ sơ mới.
 *   <li><b>`sortOrder`</b> — cùng hình dạng: {@code == null ? 0} ⇒ chức vụ nhảy lên **đầu** danh
 *       mục, đảo thứ tự Công ty tự sắp (cùng họ §11.12).
 *   <li><b>`positionGroup`</b> · <b>`description`</b> — {@code rutGon} biến rỗng thành `NULL`, nên
 *       rơi mất là **xoá trắng** ô ấy ⛔ một dòng báo nào (§11.19).
 * </ul>
 *
 * <h2>⚠ Vì sao dữ liệu thử để `active = false` và `sortOrder ≠ 0`</h2>
 *
 * Hai mặc định lặng ở trên nghĩa là một hồ sơ thử *"đang dùng, thứ tự 0"* cho ra **cùng một payload
 * ở cả hai trạng thái** — bài sẽ xanh y hệt khi trường đã rơi (luật 9). Giá trị thử phải **ngược**
 * với mặc định lặng thì phép so mới phân biệt được.
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

/** Một chức vụ **đã ngừng dùng**, thứ tự ⛔ phải 0 — xem javadoc: hai giá trị ấy là vế phân biệt. */
const CV_A = {
  publicId: 'cv-a',
  code: 'TRUONG_PHONG_KT',
  name: 'Trưởng phòng Kỹ thuật',
  positionGroup: 'Lãnh đạo cấp phòng',
  description: 'Phụ trách kỹ thuật vận hành và an toàn công trình đầu mối',
  sortOrder: 20,
  active: false,
};

/** Chức vụ thứ hai — ⛔ ô nào trùng A, kể cả `active` và `sortOrder`. */
const CV_B = {
  publicId: 'cv-b',
  code: 'CN_VAN_HANH',
  name: 'Công nhân vận hành cống',
  positionGroup: 'Trực tiếp sản xuất',
  description: 'Trực ban đóng mở cống theo lệnh điều hành',
  sortOrder: 55,
  active: true,
};

let duongCuoi = '';
let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => (duong === '/hr/positions' ? [CV_A, CV_B] : [])),
    put: vi.fn(async (duong: string, than: unknown) => {
      duongCuoi = duong;
      thanCuoi = than as Record<string, unknown>;
      return CV_A;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
    delete: vi.fn(),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { PositionsPage } = await import('./PositionsPage');

/** ⚠ Dùng lại NGUYÊN một `QueryClient` — dựng provider mới giữa chừng làm vế A → B xanh giả. */
let qc: QueryClient;

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
  render(
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>
          <PositionsPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

async function moSua(nguoiDung: ReturnType<typeof userEvent.setup>, cv: typeof CV_A) {
  await screen.findByText(cv.name);
  await nguoiDung.click(await screen.findByRole('button', { name: `Sửa chức vụ ${cv.code}` }));
  await screen.findByText(`Sửa chức vụ ${cv.code}`);
}

beforeEach(() => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
});

afterEach(() => {
  cleanup();
  qc.clear();
  duongCuoi = '';
  thanCuoi = {};
});

describe('Chức vụ — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 6 trường của HrDtos.PositionRequest', () => {
    const truong = truongCuaRecord('PositionRequest');
    expect(truong.length).toBeGreaterThanOrEqual(6);
    expect(truong).toEqual(
      expect.arrayContaining([
        'code',
        'name',
        'positionGroup',
        'description',
        'sortOrder',
        'active',
      ]),
    );
  });

  it('⭐⭐ sửa chức vụ → Lưu ⛔ đổi gì ⇒ đủ 6 trường, mã bị khoá và cờ "ngừng dùng" còn nguyên', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, CV_A);
    await screen.findByDisplayValue(CV_A.description);

    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('PositionRequest')
      .map((t) => ({ t, kyVong: (CV_A as Record<string, unknown>)[t], thucTe: thanCuoi[t] }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `code` rơi ⇒ SYS-0003 ở MỌI lượt Lưu ⇒ ' +
        'chức vụ ⛔ sửa nổi; `active` rơi ⇒ mặc định lặng BẬT LẠI một chức vụ vừa cố ý ngừng dùng; ' +
        '`sortOrder` rơi ⇒ về 0 ⇒ nhảy lên đầu danh mục; `positionGroup`/`description` rơi ⇒ xoá ' +
        'trắng, ⛔ một dòng báo nào.',
    ).toEqual([]);

    expect(duongCuoi).toBe(`/hr/positions/${CV_A.publicId}`);
  });

  it('⭐⭐ mở A → đóng → mở B ⇒ ô mang dữ liệu của B, và lượt Lưu ghi vào ĐÚNG B', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, CV_A);
    await screen.findByDisplayValue(CV_A.description);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Cancel' }));

    await moSua(nguoiDung, CV_B);

    expect(
      screen.queryByDisplayValue(CV_A.description),
      '⛔ Ô "Mô tả" đang bày dữ liệu của chức vụ MỞ TRƯỚC — `Form.useForm()` sống ở tầng trang, ' +
        'nên kho giá trị ⛔ tự dọn giữa hai lượt mở (T51.12 · T53.7).',
    ).toBeNull();
    await screen.findByDisplayValue(CV_B.description);
    expect(screen.getByLabelText('Mã chức vụ')).toHaveValue(CV_B.code);

    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    expect(duongCuoi, 'lượt Lưu phải ghi vào chức vụ ĐANG mở').toBe(
      `/hr/positions/${CV_B.publicId}`,
    );
    expect(
      thanCuoi,
      '⛔ Payload mang dữ liệu của chức vụ mở TRƯỚC ⇒ một lượt Lưu ghi đè bản ghi B bằng nội dung ' +
        'của A kèm thông báo "Đã cập nhật chức vụ" — và vì `code` là khoá nối sang hồ sơ CBNV, nó ' +
        'còn có thể va vào HR-1002 hoặc đổi mã của một chức vụ đang có người giữ.',
    ).toMatchObject({
      code: CV_B.code,
      name: CV_B.name,
      positionGroup: CV_B.positionGroup,
      description: CV_B.description,
      sortOrder: CV_B.sortOrder,
      active: CV_B.active,
    });
  });
});
