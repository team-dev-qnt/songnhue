import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa một ngưỡng cảnh báo → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 6 trường** — T47.17.
 *
 * <h2>Vì sao biểu mẫu này có vế AN TOÀN, ⛔ chỉ là mất dữ liệu</h2>
 *
 * `AlertRuleService.apDung` có hai mặc định **lặng**:
 *
 * <ul>
 *   <li>{@code active == null ⇒ true} — đánh rơi `active` là <b>BẬT LẠI một ngưỡng vừa cố ý tắt</b>.
 *       Người trực ban tắt một ngưỡng vì trạm đang sửa chữa; một lượt Lưu sau đó bật nó lên lại và
 *       ⛔ ai được báo.
 *   <li>{@code delayMinutes == null ⇒ 0} — đánh rơi là cảnh báo <b>bắn ngay</b> mỗi lượt vượt ngưỡng
 *       thay vì chờ đủ số phút; đúng thứ sinh ra chuông kêu liên tục rồi ⛔ ai đọc nữa.
 * </ul>
 *
 * <h2>Chỗ khe hở nằm</h2>
 *
 * `updateMutation` dựng payload bằng cách **liệt kê tay 6 khoá** — cùng hình dạng đã gây ra
 * `T63.8` (menu) và được bài 🔒 bắt lại lần nữa. Một danh sách gõ tay ⛔ tự biết nó thiếu gì, nên
 * bài này đọc danh sách trường từ **`HydroAlertDtos.AlertRuleUpdateRequest`**.
 *
 * <p>⚠ Ba trường **bất biến** (`stationId` · `measurementTypeCode` · `alertLevelId`) ⛔ nằm trong
 * DTO sửa — giao diện vẫn render chúng nhưng `disabled` và ⛔ gửi. Đó là ngoại lệ **có tên**, ⛔
 * phải một khoảng trống.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTOS = readFileSync(
  join(GOC_KHO, 'backend/hydro/src/main/java/com/songnhue/hydro/api/HydroAlertDtos.java'),
  'utf8',
);

function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(DTOS);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong HydroAlertDtos.java`);
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
 * Ngưỡng ĐANG TẮT, có độ trễ, dạng khoảng — ba giá trị mà mặc định lặng sẽ đổi nếu bị đánh rơi.
 *
 * ⚠ `active: false` là cố ý: với `active: true` thì đánh rơi trường ấy cho ra ĐÚNG giá trị cũ
 * (mặc định là `true`), và bài kiểm ⛔ phân biệt được hai trạng thái (luật 9).
 */
const NGUONG = {
  id: 'ng-1',
  stationId: 'tram-1',
  stationCode: 'TL-001',
  stationName: 'Trạm Lương Cổ',
  measurementTypeCode: 'MN',
  measurementTypeName: 'Mực nước',
  unit: 'm',
  alertLevelId: 'muc-1',
  alertLevelCode: 'BD2',
  alertLevelName: 'Báo động 2',
  colorToken: 'warning',
  conditionType: 'OUT_OF_RANGE',
  thresholdValue: '1.50',
  thresholdValueHigh: '3.20',
  delayMinutes: 30,
  active: false,
  note: 'Tạm tắt trong thời gian sửa chữa cống',
};

let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong === '/hyd/alert-rules') return [NGUONG];
      if (duong === '/hyd/alert-levels') {
        return [{ id: 'muc-1', code: 'BD2', name: 'Báo động 2', colorToken: 'warning' }];
      }
      if (duong === '/hyd/stations')
        return [{ id: 'tram-1', code: 'TL-001', name: 'Trạm Lương Cổ' }];
      if (duong === '/hyd/measurement-types') return [{ id: 'ct-1', code: 'MN', name: 'Mực nước' }];
      return [];
    }),
    put: vi.fn(async (_duong: string, than: unknown) => {
      thanCuoi = than as Record<string, unknown>;
      return NGUONG;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { AlertRulesPage } = await import('./AlertRulesPage');

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
          <AlertRulesPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  thanCuoi = {};
});

describe('Ngưỡng cảnh báo — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 6 trường của AlertRuleUpdateRequest', () => {
    const truong = truongCuaRecord('AlertRuleUpdateRequest');
    expect(truong.length).toBeGreaterThanOrEqual(6);
    expect(truong).toEqual(
      expect.arrayContaining(['conditionType', 'thresholdValue', 'delayMinutes', 'active', 'note']),
    );
  });

  it('⭐⭐ sửa ngưỡng ĐANG TẮT → Lưu ⛔ đổi gì ⇒ ⛔ trường nào rơi, `active` vẫn false', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    // Cột "Điểm đo" render `${stationCode} — ${stationName}`, ⛔ phải tên trần.
    await screen.findByText(`${NGUONG.stationCode} — ${NGUONG.stationName}`);
    await nguoiDung.click((await screen.findAllByRole('button', { name: 'Sửa' }))[0]);
    await screen.findByDisplayValue(NGUONG.note);

    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('AlertRuleUpdateRequest')
      .map((t) => ({ t, kyVong: (NGUONG as Record<string, unknown>)[t], thucTe: thanCuoi[t] }))
      .filter((x) => JSON.stringify(x.thucTe) !== JSON.stringify(x.kyVong));

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. Đây là biểu mẫu có vế AN TOÀN: ' +
        '`active == null ⇒ true` ở service, nên đánh rơi `active` là BẬT LẠI một ngưỡng vừa cố ý ' +
        'tắt; `delayMinutes == null ⇒ 0` làm cảnh báo bắn ngay mỗi lượt vượt.',
    ).toEqual([]);

    expect(thanCuoi.active, 'ngưỡng đang TẮT ⛔ được tự bật lại sau một lượt Lưu').toBe(false);
  });
});
