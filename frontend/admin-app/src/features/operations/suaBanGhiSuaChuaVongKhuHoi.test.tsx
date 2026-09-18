import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';
import { type MaintenanceRow } from '@/shared/api-types';
import type * as ApiClientModule from '@/shared/apiClient';

/**
 * **Sửa bản ghi sửa chữa: mở → Lưu ⛔ sửa gì ⇒ `PUT` mang đủ mọi trường** — T61.18 (T47.17 bài 3/17).
 *
 * Trước T61.18, `PUT /ops/maintenance-logs/{id}` có 0 nơi gọi. Lối sửa dựng lại biểu mẫu TẠO MỚI, mà
 * biểu mẫu ấy ⛔ có ô cho `acceptanceResult` · `acceptanceNote` · `assigneeUserId` · `alertEventId` —
 * trong khi `MaintenanceLogService.apDung` ghi đè cả bốn. Dùng lại payload tạo mới là một lượt Lưu xoá
 * kết quả nghiệm thu và cắt liên kết cảnh báo ngưỡng.
 *
 * ⛔ Trường phải có đọc từ `MaintenanceDtos.SaveRequest`. Ngoại lệ có tên: `initialState` — chỉ đường
 * TẠO đọc (`resolveInitialState`); `chiDuongTaoDocInitialState` canh chiều ấy trên mã service.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const OPS = join(GOC_KHO, 'backend/operations/src/main/java/com/songnhue/operations');
const DTOS = readFileSync(join(OPS, 'api/MaintenanceDtos.java'), 'utf8');
const SERVICE = readFileSync(join(OPS, 'application/MaintenanceLogService.java'), 'utf8');

function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(DTOS);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong MaintenanceDtos.java`);
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

const CHI_KHI_TAO = new Set(['initialState']);

function banGhi(ghiDe: Partial<MaintenanceRow> = {}): MaintenanceRow {
  return {
    id: 'bg-1',
    code: 'BT-2026-0001',
    constructionId: 'ct-1',
    constructionCode: 'CT-01',
    constructionName: 'Trạm bơm kiểm thử',
    workType: 'KHAC_PHUC_SU_CO',
    severity: 'CAO',
    status: 'DA_XU_LY',
    startedOn: '2026-08-01',
    completedOn: '2026-08-05',
    content: 'Thay gioăng cửa van số 2',
    itemOrEquipment: 'Cửa van số 2',
    performer: 'Xí nghiệp A',
    performerIsInternal: true,
    performerOrgUnitId: '11111111-1111-4111-8111-111111111111',
    cost: '1500000',
    fundingSource: 'Sự nghiệp',
    acceptanceResult: 'DANG_THEO_DOI',
    acceptanceNote: 'Theo dõi 30 ngày',
    assigneeUserId: '22222222-2222-4222-8222-222222222222',
    alertEventId: '33333333-3333-4333-8333-333333333333',
    createdAt: '2026-08-01T02:00:00Z',
    ...ghiDe,
  };
}

const putDaGui = vi.fn();

vi.mock('@/shared/apiClient', async (importOriginal) => {
  const thuc = await importOriginal<typeof ApiClientModule>();
  return {
    ...thuc,
    api: {
      ...thuc.api,
      // Cây đơn vị rỗng: ô chọn vẫn GIỮ giá trị đã nạp — thứ bài này đo.
      get: vi.fn(async () => []),
      put: vi.fn(async (url: string, than: unknown) => {
        putDaGui(url, than);
        return {};
      }),
      post: vi.fn(async () => {
        throw new Error('Lối SỬA ⛔ được đi đường TẠO MỚI');
      }),
    },
  };
});

const { MaintenanceFormModal } = await import('./components/MaintenanceFormModal');

function boc(children: React.ReactNode) {
  const khongDung = () => {
    throw new Error('AuthContext giả: bài kiểm này ⛔ dựng phần đó');
  };
  const auth = {
    status: 'authenticated' as const,
    user: null,
    hasPermission: () => true,
    hasRole: () => true,
    maintenance: false,
    login: khongDung,
    verifyTwoFactor: khongDung,
    confirmEnrollment: khongDung,
    logout: khongDung,
    endSession: khongDung,
    reloadProfile: khongDung,
  } as unknown as AuthContextValue;
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>{children}</AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}

function modal(row: MaintenanceRow) {
  return (
    <MaintenanceFormModal
      key={row.id}
      constructionPublicId={row.constructionId}
      banGhi={row}
      open
      onClose={() => {}}
      onSaved={() => {}}
    />
  );
}

/** Kỳ vọng tra theo luật chung trên dòng đã nạp — ⛔ chép phép ánh xạ của biểu mẫu. */
function kyVong(t: string, row: MaintenanceRow): unknown {
  if (t === 'performerName') return row.performerIsInternal ? null : row.performer;
  return (row as unknown as Record<string, unknown>)[t] ?? null;
}

async function luuVaLayPayload() {
  const nguoiDung = userEvent.setup();
  await nguoiDung.click(screen.getByRole('button', { name: /OK/ }));
  await waitFor(() => expect(putDaGui).toHaveBeenCalled());
  return putDaGui.mock.calls.at(-1) as [string, Record<string, unknown>];
}

afterEach(() => {
  cleanup();
  putDaGui.mockClear();
});

describe('Sửa bản ghi sửa chữa — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: SaveRequest có ≥ 15 trường, gồm bốn trường biểu mẫu ⛔ có ô', () => {
    expect(truongCuaRecord('SaveRequest')).toEqual(
      expect.arrayContaining([
        'acceptanceResult',
        'acceptanceNote',
        'assigneeUserId',
        'alertEventId',
      ]),
    );
    expect(truongCuaRecord('SaveRequest').length).toBeGreaterThanOrEqual(15);
  });

  it('⛔ ngoại lệ initialState vẫn đúng: thân hàm update ⛔ đọc nó', () => {
    const than = /public MaintenanceLog update\(([\s\S]*?)\n {4}\}/.exec(SERVICE)?.[1] ?? '';
    expect(than.length, 'tìm được thân hàm update').toBeGreaterThan(200);
    expect(than).not.toContain('initialState');
  });

  it.each([
    ['sự cố · đơn vị nội bộ', banGhi()],
    [
      'bảo trì · nhà thầu ngoài',
      banGhi({
        id: 'bg-2',
        workType: 'BAO_TRI_DINH_KY',
        severity: null,
        status: 'DANG_XU_LY',
        completedOn: null,
        performer: 'Công ty Cơ điện Hà Nội',
        performerIsInternal: false,
        performerOrgUnitId: null,
        acceptanceResult: 'CHUA_DAT',
      }),
    ],
  ])('⭐⭐ %s: Lưu ⛔ sửa gì ⇒ PUT đúng đường, mang đủ mọi trường đã nạp', async (_ten, row) => {
    render(boc(modal(row)));
    await screen.findByDisplayValue(row.content);

    const [url, payload] = await luuVaLayPayload();
    expect(url).toBe(`/ops/maintenance-logs/${row.id}`);

    const lech = truongCuaRecord('SaveRequest')
      .filter((t) => !CHI_KHI_TAO.has(t))
      .map((t) => ({ t, kyVong: kyVong(t, row), thucTe: payload[t] }))
      .filter((x) => JSON.stringify(x.thucTe ?? null) !== JSON.stringify(x.kyVong));
    expect(lech, 'trường bị đánh rơi / đổi giá trị sau một lượt Lưu ⛔ sửa gì').toEqual([]);
  });

  it('⛔⛔ mở bản ghi A rồi B ⇒ PUT gửi dữ liệu của B lên B (T51.12)', async () => {
    const a = banGhi();
    const b = banGhi({ id: 'bg-9', content: 'Nội dung của bản ghi B', cost: '2750000' });
    const { rerender } = render(boc(modal(a)));
    await screen.findByDisplayValue(a.content);
    rerender(boc(modal(b)));
    await screen.findByDisplayValue(b.content);

    const [url, payload] = await luuVaLayPayload();
    expect(url).toBe('/ops/maintenance-logs/bg-9');
    expect(payload.content).toBe('Nội dung của bản ghi B');
    expect(payload.cost).toBe('2750000');
  });
});
