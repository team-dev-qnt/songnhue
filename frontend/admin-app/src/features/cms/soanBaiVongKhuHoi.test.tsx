import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Mở bài viết đủ mọi ô → bấm Lưu ⛔ sửa gì → payload phải mang lại ĐỦ mọi trường** — T47.17 (bài 2/17).
 *
 * Cùng khuôn `hoSoCongTrinhVongKhuHoi.test.tsx` (T61.12). `PUT /cms/articles/{id}` là phép **thay toàn
 * phần** (`ArticleService.update` ghi từng setter từ thân yêu cầu), nên một ô biểu mẫu quên nối là một
 * lượt Lưu xoá trường ấy khỏi bài đang đăng trên cổng — ⛔ một dòng lỗi (§11.19).
 *
 * ⛔ Danh sách trường ĐỌC từ `ArticleDtos.SaveRequest`, ⛔ chép tay: backend thêm trường mà biểu mẫu
 * ⛔ gửi thì bài này đỏ ngay.
 *
 * ⚠ Ngoại lệ có tên: `authorPublicId` — `ArticleService.update` chỉ ghi khi KHÁC null (giữ nguyên tác
 * giả khi thân thiếu trường), và `ArticleDetail` ⛔ trả trường ấy ⇒ biểu mẫu ⛔ có gì để gửi lại. Nếu
 * backend đổi sang ghi đè cả khi null thì ngoại lệ này sai — `ngoaiLeTacGiaVanDung` canh chiều ấy.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTOS = readFileSync(
  join(GOC_KHO, 'backend/content/src/main/java/com/songnhue/content/api/ArticleDtos.java'),
  'utf8',
);
const SERVICE = readFileSync(
  join(
    GOC_KHO,
    'backend/content/src/main/java/com/songnhue/content/application/ArticleService.java',
  ),
  'utf8',
);

function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(DTOS);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong ArticleDtos.java`);
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .replace(/<[^<>]*>/g, '')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

const GIU_NGUYEN_KHI_NULL = new Set(['authorPublicId']);

/** Bài mang GIÁ TRỊ Ở MỌI Ô — một ô `null` là một trường bài này ⛔ thấy nếu bị đánh rơi. */
const BAI = {
  publicId: 'bai-1',
  title: 'Thông báo lịch vận hành trạm bơm',
  slug: 'thong-bao-lich-van-hanh',
  summary: 'Tóm tắt đầy đủ để bài kiểm thấy nếu nó biến mất',
  content: '<p>Nội dung bài viết kiểm thử khứ hồi</p>',
  coverAttachmentPublicId: '55555555-5555-4555-8555-555555555555',
  source: 'Phòng Kỹ thuật',
  status: 'NHAP',
  publishedAt: '2026-09-10T01:30:00Z',
  reviewNote: null,
  metaTitle: 'Tiêu đề SEO',
  metaDescription: 'Mô tả SEO của bài viết',
  metaKeywords: 'trạm bơm, vận hành',
  docNumber: '123/TB-SN',
  docIssuedDate: '2026-09-09',
  viewCount: 0,
  publiclyVisible: false,
  categoryPublicIds: ['dm-1'],
  documents: [
    {
      publicId: '66666666-6666-4666-8666-666666666666',
      label: 'Quyết định kèm theo',
      originalName: 'qd-123.pdf',
      contentType: 'application/pdf',
      sizeBytes: 1024,
      downloadable: true,
    },
  ],
  allowedActions: [{ action: 'SUBMIT', label: 'Gửi duyệt' }],
};

const capNhat = vi.fn(async (_id: string, _than: unknown) => BAI);

vi.mock('./api', () => ({
  cmsKeys: {
    article: (id: string) => ['cms', 'article', id] as const,
    categories: () => ['cms', 'categories'] as const,
    folders: () => ['cms', 'folders'] as const,
    files: (f: string | null) => ['cms', 'files', f] as const,
    versions: (id: string) => ['cms', 'article', id, 'versions'] as const,
    versionContent: (id: string, v: string) => ['cms', 'article', id, 'version', v] as const,
  },
  cmsApi: {
    getArticle: vi.fn(async () => BAI),
    categories: vi.fn(async () => [{ publicId: 'dm-1', name: 'Tin tức', depth: 0 }]),
    folders: vi.fn(async () => []),
    versions: vi.fn(async () => []),
    updateArticle: capNhat,
    createArticle: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
  },
}));

const { ArticleEditorPage } = await import('./ArticleEditorPage');

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
  const router = createMemoryRouter(
    [
      { path: '/noi-dung/bai-viet/:publicId', element: <ArticleEditorPage /> },
      { path: '/noi-dung/bai-viet', element: <div>Danh sách bài viết</div> },
    ],
    { initialEntries: ['/noi-dung/bai-viet/bai-1'] },
  );
  render(
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>
          <RouterProvider router={router} />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

/** Giá trị kỳ vọng — chuẩn hoá đúng HAI chỗ mà hình dạng dây khác hình dạng đọc (⛔ chép phép ánh xạ). */
function kyVong(truong: string): unknown {
  if (truong === 'documents') {
    return BAI.documents.map((d) => ({ publicId: d.publicId, label: d.label }));
  }
  return (BAI as Record<string, unknown>)[truong];
}

function thucTe(truong: string, payload: Record<string, unknown>): unknown {
  const v = payload[truong];
  // Thời điểm: `toISOString()` thêm `.000` — cùng một khoảnh khắc, khác chuỗi.
  if (truong === 'publishedAt' && typeof v === 'string') return new Date(v).toISOString();
  return v;
}

afterEach(() => {
  cleanup();
  capNhat.mockClear();
});

describe('Soạn bài — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được ≥ 14 trường từ SaveRequest, có documents và categoryPublicIds', () => {
    const truong = truongCuaRecord('SaveRequest');
    expect(truong.length).toBeGreaterThanOrEqual(14);
    expect(truong).toEqual(
      expect.arrayContaining(['documents', 'categoryPublicIds', 'docIssuedDate']),
    );
  });

  it('⛔ ngoại lệ tác giả vẫn đúng: update chỉ ghi tác giả khi khác null', () => {
    expect(SERVICE).toMatch(
      /if\s*\(\s*draft\.authorUserId\(\)\s*!=\s*null\s*\)\s*\{\s*article\.setAuthorUserId/,
    );
  });

  it('⭐⭐ mở bài → Lưu ⛔ sửa gì ⇒ payload mang đủ mọi trường, đúng giá trị đã nạp', async () => {
    const nguoiDung = userEvent.setup();
    dung();
    await screen.findByDisplayValue(BAI.title);

    await nguoiDung.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(capNhat).toHaveBeenCalled());
    const payload = capNhat.mock.calls.at(-1)?.[1] as Record<string, unknown>;

    const lech = truongCuaRecord('SaveRequest')
      .filter((t) => !GIU_NGUYEN_KHI_NULL.has(t))
      .map((t) => ({ t, kyVong: kyVong(t), thucTe: thucTe(t, payload) }))
      .filter(
        (x) =>
          JSON.stringify(x.thucTe) !==
          JSON.stringify(
            x.t === 'publishedAt' ? new Date(String(x.kyVong)).toISOString() : x.kyVong,
          ),
      );
    expect(lech, 'trường bị đánh rơi hoặc đổi giá trị sau một lượt Lưu ⛔ sửa gì').toEqual([]);
  });
});
