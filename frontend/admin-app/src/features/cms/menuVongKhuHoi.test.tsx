import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

/**
 * **Sửa một mục menu → bấm Lưu ⛔ đổi gì → payload phải mang lại ĐỦ mọi trường** — T47.17 (biểu mẫu
 * Menu), và nó là bài kiểm chứng cho bản vá **T63.8**.
 *
 * <h2>⛔⛔ Khuyết tật ĐANG SỐNG mà bài này bắt được</h2>
 *
 * `MenusTab.tsx` trước bản vá gửi `articleId: null` **ghi cứng** và `setFieldsValue` ⛔ nạp
 * `articlePublicId`, trong khi ô *Loại liên kết* vẫn bày đủ 5 giá trị của `MO_TA_LOAI` — gồm
 * *"Bài viết"* — mà ⛔ nhánh nào render ô chọn bài viết. Hai hậu quả **đo được**:
 *
 * 1. Chọn *"Bài viết"* ⇒ `MenuService.applyTarget` ném `CMS-2012` ⇒ một lựa chọn **CHẾT** trong ô
 *    Select (luật 15 — một công tắc ⛔ ai đọc là một lỗi).
 * 2. Nặng hơn: seed `V202608191021` **dựng sẵn** mục menu loại `ARTICLE` (*Liên hệ*, *Giới thiệu
 *    chung*, và một vòng mục con) ⇒ những mục ấy **⛔ sửa nổi** — kể cả chỉ đổi nhãn hay tắt/bật.
 *    Câu lỗi người dùng đọc được là *"Đích của mục menu ⛔ tồn tại hoặc đã bị xoá"*, dẫn họ đi tìm
 *    xem mình lỡ xoá bài nào.
 *
 * <p>⚠ Vế **⛔ mất dữ liệu**: backend CHẶN được, nên đây là lỗi *⛔ dùng được* chứ ⛔ phải lỗi xoá
 * trắng như §11.19. Một lượt rà trước đó kết luận *"đứt liên kết trên cổng, im lặng"* — <b>sai</b>,
 * và phép đo phân xử là đọc `applyTarget`: nhánh `ARTICLE` gọi `requireId(...)` rồi ném.
 *
 * <h2>Ngoại lệ có tên</h2>
 *
 * `parentId` — `MenuController:110` <b>⛔ truyền</b> nó xuống service ở đường `PUT` (vị trí cha đổi
 * bằng kéo–thả, một endpoint khác), nên biểu mẫu gửi gì cũng ⛔ đổi được cha. Bài này vẫn đòi nó có
 * mặt và đúng giá trị cũ, vì gửi **sai** một trường bị bỏ qua vẫn là một lượt gửi sai.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const CONTROLLER = readFileSync(
  join(GOC_KHO, 'backend/content/src/main/java/com/songnhue/content/api/MenuController.java'),
  'utf8',
);

/** Tên các thành phần của `record MenuRequest` — ĐỌC từ nguồn, ⛔ chép tay (luật 14). */
function truongCuaMenuRequest(): string[] {
  const m = /record\s+MenuRequest\s*\(([\s\S]*?)\)\s*\{/.exec(CONTROLLER);
  if (!m) throw new Error('Không tìm thấy record MenuRequest trong MenuController.java');
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .replace(/<[^<>]*>/g, '')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

/** Mục menu loại `ARTICLE` — đúng hình dạng seed dựng sẵn trên cổng. */
const MUC_BAI_VIET = {
  publicId: 'menu-1',
  label: 'Giới thiệu chung',
  linkType: 'ARTICLE' as const,
  parentPublicId: null,
  categoryPublicId: null,
  categorySlug: null,
  articlePublicId: 'bai-gioi-thieu',
  articleSlug: 'gioi-thieu-chung',
  url: null,
  openNewTab: false,
  depth: 0,
  sortOrder: 10,
  active: true,
  logoAttachmentId: null,
};

const capNhat = vi.fn(async (_id: string, _than: unknown) => MUC_BAI_VIET);

vi.mock('./api', () => ({
  cmsKeys: {
    menu: (v: string) => ['cms', 'menu', v] as const,
    categories: () => ['cms', 'categories'] as const,
    articles: (f: unknown) => ['cms', 'articles', f ?? null] as const,
  },
  cmsApi: {
    menu: vi.fn(async () => [MUC_BAI_VIET]),
    categories: vi.fn(async () => [{ publicId: 'dm-1', name: 'Tin tức', depth: 0 }]),
    searchArticles: vi.fn(async () => ({
      items: [
        { publicId: 'bai-gioi-thieu', title: 'Giới thiệu chung', slug: 'gioi-thieu-chung' },
        { publicId: 'bai-khac', title: 'Bài viết khác', slug: 'bai-khac' },
      ],
      meta: { page: 0, size: 200, totalItems: 2, totalPages: 1 },
    })),
    updateMenuItem: capNhat,
    createMenuItem: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
    deleteMenuItem: vi.fn(),
    reorderMenu: vi.fn(),
    uploadMenuLogo: vi.fn(),
  },
}));

const { MenusTab } = await import('./MenusTab');

function dung() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <AntdApp>
        <MenusTab />
      </AntdApp>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  capNhat.mockClear();
});

describe('Menu — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được các trường của MenuRequest từ MenuController.java', () => {
    const truong = truongCuaMenuRequest();
    expect(truong.length).toBeGreaterThanOrEqual(8);
    expect(truong).toEqual(
      expect.arrayContaining([
        'label',
        'linkType',
        'parentId',
        'categoryId',
        'articleId',
        'url',
        'openNewTab',
        'active',
      ]),
    );
  });

  it('⭐⭐ sửa mục loại "Bài viết" → Lưu ⛔ đổi gì ⇒ articleId GIỮ NGUYÊN, ⛔ về null', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await screen.findByText(MUC_BAI_VIET.label);
    await nguoiDung.click((await screen.findAllByRole('button', { name: 'Sửa' }))[0]);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));

    await waitFor(() => expect(capNhat).toHaveBeenCalled());
    const payload = capNhat.mock.calls.at(-1)?.[1] as Record<string, unknown>;

    expect(
      payload.articleId,
      '⛔ Trước T63.8 ô này là `null` GHI CỨNG ⇒ mọi lượt Lưu một mục ARTICLE đều bị backend ' +
        'từ chối bằng CMS-2012, tức mục menu ấy ⛔ sửa nổi bằng giao diện.',
    ).toBe(MUC_BAI_VIET.articlePublicId);
  });

  it('⭐ mọi trường của MenuRequest đều có mặt trong payload, đúng giá trị đã nạp', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await screen.findByText(MUC_BAI_VIET.label);
    await nguoiDung.click((await screen.findAllByRole('button', { name: 'Sửa' }))[0]);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(capNhat).toHaveBeenCalled());
    const payload = capNhat.mock.calls.at(-1)?.[1] as Record<string, unknown>;

    // Hình dạng dây khác hình dạng đọc ở đúng ba chỗ — khai ra thay vì chép phép ánh xạ.
    const kyVong: Record<string, unknown> = {
      label: MUC_BAI_VIET.label,
      linkType: MUC_BAI_VIET.linkType,
      parentId: MUC_BAI_VIET.parentPublicId,
      categoryId: MUC_BAI_VIET.categoryPublicId,
      articleId: MUC_BAI_VIET.articlePublicId,
      url: MUC_BAI_VIET.url,
      openNewTab: MUC_BAI_VIET.openNewTab,
      active: MUC_BAI_VIET.active,
    };

    const lech = truongCuaMenuRequest()
      .map((t) => ({ t, kyVong: kyVong[t], thucTe: payload[t] }))
      .filter((x) => JSON.stringify(x.thucTe) !== JSON.stringify(x.kyVong));

    expect(lech, 'trường bị đánh rơi hoặc đổi giá trị sau một lượt Lưu ⛔ sửa gì').toEqual([]);
  });
});
