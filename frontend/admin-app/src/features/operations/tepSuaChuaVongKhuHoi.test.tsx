import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';
import { type MaintenanceAttachment } from '@/shared/api-types';
import type * as ApiClientModule from '@/shared/apiClient';

/**
 * **Tệp của bản ghi sửa chữa: danh sách · tải lên · tải về · xoá** — T61.19 (CN-02.2).
 *
 * Năm endpoint có 0 nơi gọi trước đợt này (`EndpointCoNoiGoiTest`). Bài này canh ĐƯỜNG DẪN + ĐỘNG TỪ
 * mà panel gọi, ⛔ chỉ canh chữ hiện ra — một nút đúng chữ mà gọi sai đường là T61.18 lần nữa.
 * Vế backend của đúng các đường ấy: `MaintenanceAttachmentHttpTest`.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTOS = readFileSync(
  join(
    GOC_KHO,
    'backend/operations/src/main/java/com/songnhue/operations/api/MaintenanceDtos.java',
  ),
  'utf8',
);
const API_TYPES = readFileSync(join(GOC_KHO, 'frontend/admin-app/src/shared/api-types.ts'), 'utf8');

const TEP: MaintenanceAttachment[] = [
  {
    id: 't-1',
    originalName: 'bien-ban.pdf',
    purpose: 'Biên bản nghiệm thu',
    contentType: 'application/pdf',
    sizeBytes: 2048,
    fileVersion: 2,
    downloadable: true,
    createdAt: '2026-09-14T02:00:00Z',
  },
  {
    id: 't-2',
    originalName: 'anh-sau.jpg',
    purpose: 'Ảnh sau',
    contentType: 'image/jpeg',
    sizeBytes: 4096,
    fileVersion: 1,
    downloadable: false,
    createdAt: '2026-09-14T03:00:00Z',
  },
];

const goi = vi.fn();

vi.mock('@/shared/apiClient', async (importOriginal) => {
  const thuc = await importOriginal<typeof ApiClientModule>();
  return {
    ...thuc,
    api: {
      ...thuc.api,
      get: vi.fn(async (url: string) => {
        goi('GET', url);
        return url.endsWith('/download-url') ? { url: 'https://tep.example/ky-san' } : TEP;
      }),
      upload: vi.fn(async (url: string) => {
        goi('POST', url);
        return TEP[0];
      }),
      delete: vi.fn(async (url: string) => {
        goi('DELETE', url);
      }),
    },
  };
});

const { MaintenanceAttachmentsPanel } = await import('./components/MaintenanceAttachmentsPanel');

function dung(quyen: string[]) {
  const khongDung = () => {
    throw new Error('AuthContext giả: bài kiểm này ⛔ dựng phần đó');
  };
  const auth = {
    status: 'authenticated' as const,
    user: null,
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
        <AntdApp>
          <MaintenanceAttachmentsPanel logId="bg-1" />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

const DU_QUYEN = ['ops:maintenance:view', 'ops:document:upload', 'ops:document:delete'];

afterEach(() => {
  cleanup();
  goi.mockClear();
});

describe('Tệp của bản ghi sửa chữa — T61.19', () => {
  it('⛔ kiểu TS khớp tên trường của `MaintenanceDtos.AttachmentView` (luật 14)', () => {
    const java = /record\s+AttachmentView\s*\(([\s\S]*?)\)\s*\{/.exec(DTOS)?.[1] ?? '';
    const truongJava = java
      .split(',')
      .map((p) => p.trim().split(/\s+/).pop() ?? '')
      .filter((t) => /^[a-z]\w*$/.test(t))
      .sort();
    const ts = /export interface MaintenanceAttachment \{([\s\S]*?)\n\}/.exec(API_TYPES)?.[1] ?? '';
    const truongTs = [...ts.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/^\s+(\w+)\??:/gm)]
      .map((m) => m[1])
      .sort();
    expect(truongJava.length, 'chống tập rỗng — record đổi tên?').toBeGreaterThanOrEqual(8);
    expect(truongTs).toEqual(truongJava);
  });

  it('⭐ danh sách gọi đúng đường; tệp chưa quét NÓI RA thay vì hiện nút tải', async () => {
    dung(DU_QUYEN);
    await screen.findByText('bien-ban.pdf');
    expect(goi).toHaveBeenCalledWith('GET', '/ops/maintenance-logs/bg-1/attachments');
    expect(screen.getByText('Phiên bản 2')).toBeTruthy();

    const hangChuaQuet = screen.getByText('anh-sau.jpg').closest('tr') as HTMLElement;
    expect(within(hangChuaQuet).getByText('Đang quét virus')).toBeTruthy();
    expect(within(hangChuaQuet).queryByRole('button', { name: /Tải về/ })).toBeNull();
  });

  it('⭐⭐ tải lên đi POST multipart với `docType` = NHÃN đang chọn ở query', async () => {
    const nguoiDung = userEvent.setup();
    dung(DU_QUYEN);
    await screen.findByText('bien-ban.pdf');

    const o = document.querySelector('input[type=file]') as HTMLInputElement;
    await nguoiDung.upload(o, new File(['%PDF-1.4'], 'moi.pdf', { type: 'application/pdf' }));

    await waitFor(() => expect(goi).toHaveBeenCalledWith('POST', expect.any(String)));
    const url = goi.mock.calls.find((c) => c[0] === 'POST')?.[1] as string;
    const u = new URL(url, 'http://x');
    expect(u.pathname).toBe('/ops/maintenance-logs/bg-1/attachments');
    expect(u.searchParams.get('docType')).toBe('Biên bản nghiệm thu');
  });

  it('⭐ tải về xin đường có hạn của ĐÚNG tệp rồi mở', async () => {
    const mo = vi.spyOn(window, 'open').mockImplementation(() => null);
    const nguoiDung = userEvent.setup();
    dung(DU_QUYEN);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Tải về bien-ban.pdf' }));
    await waitFor(() =>
      expect(goi).toHaveBeenCalledWith(
        'GET',
        '/ops/maintenance-logs/bg-1/attachments/t-1/download-url',
      ),
    );
    expect(mo).toHaveBeenCalledWith('https://tep.example/ky-san', '_blank', 'noopener');
    mo.mockRestore();
  });

  it('⭐ xoá gọi DELETE đúng tệp sau khi xác nhận', async () => {
    const nguoiDung = userEvent.setup();
    dung(DU_QUYEN);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Xoá anh-sau.jpg' }));
    await nguoiDung.click(await screen.findByRole('button', { name: /OK/ }));
    await waitFor(() =>
      expect(goi).toHaveBeenCalledWith('DELETE', '/ops/maintenance-logs/bg-1/attachments/t-2'),
    );
  });

  it('⛔ thiếu quyền tải lên / xoá thì ⛔ có nút — chỉ xem được', async () => {
    dung(['ops:maintenance:view']);
    await screen.findByText('bien-ban.pdf');
    expect(screen.queryByRole('button', { name: /Tải tệp/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /^Xoá / })).toBeNull();
  });
});
