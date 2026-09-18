import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa một danh mục → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ trường `PUT` dùng tới** — T47.17
 * (CN-01.2).
 *
 * <h2>⛔⛔ `slug` rơi ⇒ ĐỔI ĐỊA CHỈ CÔNG KHAI của cả một chuyên mục, im lặng hoàn toàn</h2>
 *
 * Đây là biểu mẫu **nhỏ nhất** của cả nhóm (hai ô) và là biểu mẫu có hậu quả **rộng nhất**, vì một
 * trong hai ô là một **URL đang được dùng**. {@code CategoryService.requireUniqueSlug} khai:
 *
 * <pre>slug == null || slug.isBlank() ? VietnameseUtils.toSlug(name) : VietnameseUtils.toSlug(slug)</pre>
 *
 * ⇒ đánh rơi `slug` ⛔ để lại một ô rỗng đáng ngờ nào: hệ **tự sinh lại** slug từ TÊN. Một danh mục
 * mang địa chỉ cũ {@code /danh-muc/dieu-hanh-tuoi-tieu} đổi tên hiển thị thành *"Thông báo điều
 * hành"* sẽ lặng lẽ chuyển sang {@code /danh-muc/thong-bao-dieu-hanh} ⇒ **mọi liên kết đã phát ra
 * ngoài đều 404**, và màn hình quản trị báo *"Đã cập nhật danh mục"*.
 *
 * <h2>⚠ Vì sao dữ liệu thử để `slug ≠ toSlug(name)`</h2>
 *
 * Đó là **vế phân biệt** (luật 9). Một danh mục mà slug vốn đã bằng {@code toSlug(name)} cho ra
 * **cùng một kết quả ở cả hai trạng thái** — gửi slug hay đánh rơi slug đều ra một chuỗi — nên bài
 * sẽ xanh y hệt khi trường đã rơi. Và cặp *"slug cũ, tên mới"* ⛔ phải một ca dựng cho khó: nó là ca
 * **thường gặp nhất**, vì slug đặt một lần lúc lập danh mục còn tên thì đổi theo cách gọi của Công ty.
 *
 * <h2>Ngoại lệ CÓ TÊN: `parentId` ⛔ thuộc vòng khứ hồi của `PUT`</h2>
 *
 * {@code SaveRequest} có **ba** thành phần, nhưng {@code CategoryController.rename} chỉ chuyển
 * {@code name} và {@code slug} xuống service — đổi cha là một endpoint khác
 * ({@code PUT /{id}/parent}, đi bằng kéo–thả trên cây). Bài (a) **ĐO** điều đó từ chính mã nguồn
 * thay vì khai bằng một câu chú thích: ngày nào `rename` nhận thêm `parentId` thì bài đỏ, và người
 * sửa buộc phải nối ô ấy vào biểu mẫu — thay vì để một trường mới lặng lẽ bị ghi đè bằng `null`.
 *
 * <h2>Vế A → đóng → B</h2>
 *
 * Nút *Sửa* của màn hình này đặt giá trị **tường minh** ({@code form.setFieldsValue}) chứ ⛔ dựa vào
 * {@code initialValues}, nên nó ⛔ có hình dạng T51.12. Vế này vì thế là một **bánh cóc**: nó khoá
 * lại cách làm đúng, để lượt sửa sau ⛔ đổi sang `initialValues` mà ⛔ ai biết.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const CONTROLLER = readFileSync(
  join(GOC_KHO, 'backend/content/src/main/java/com/songnhue/content/api/CategoryController.java'),
  'utf8',
);

/** ⛔ Đọc từ NGUỒN — ⛔ chép tay danh sách trường (luật 14/29). */
function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(CONTROLLER);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong CategoryController.java`);
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .replace(/<[^<>]*>/g, '')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

/** Những thành phần của `SaveRequest` mà đường `PUT` (rename) THẬT SỰ đọc — đo ở lời gọi service. */
function truongRenameDungToi(): string[] {
  const m = /categories\.rename\(([\s\S]*?)\);/.exec(CONTROLLER);
  if (!m) throw new Error('Không tìm thấy lời gọi categories.rename(...) trong CategoryController');
  return truongCuaRecord('SaveRequest').filter((t) => m[1].includes(`request.${t}()`));
}

/**
 * Một chuyên mục **có slug cũ** — xem javadoc: `slug !== toSlug(name)` là vế phân biệt của cả bài.
 */
const DM_A = {
  publicId: 'dm-a',
  name: 'Thông báo điều hành',
  slug: 'dieu-hanh-tuoi-tieu',
  parentPublicId: null,
  depth: 0,
  sortOrder: 10,
  visible: true,
};

/** Chuyên mục thứ hai — ⛔ ô nào trùng A, và cũng mang slug cũ. */
const DM_B = {
  publicId: 'dm-b',
  name: 'Tin tức hoạt động',
  slug: 'hoat-dong-cong-ty',
  parentPublicId: null,
  depth: 0,
  sortOrder: 20,
  visible: false,
};

const doiTen = vi.fn(async (_id: string, _than: unknown) => DM_A);

vi.mock('./api', () => ({
  cmsKeys: { categories: () => ['cms', 'categories'] as const },
  cmsApi: {
    categories: vi.fn(async () => [DM_A, DM_B]),
    renameCategory: doiTen,
    createCategory: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
    moveCategory: vi.fn(),
    setCategoryVisibility: vi.fn(),
    deleteCategory: vi.fn(),
  },
}));

const { CategoriesPage } = await import('./CategoriesPage');

/** ⚠ Một `QueryClient` duy nhất — dựng provider mới giữa chừng làm vế A → B xanh giả. */
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
          <CategoriesPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

/** Bấm *Sửa* trên ĐÚNG nút của danh mục ấy — cây có nhiều dòng, mỗi dòng một nút cùng tên. */
async function moSua(nguoiDung: ReturnType<typeof userEvent.setup>, dm: typeof DM_A) {
  const nhan = await screen.findByText(dm.name);
  const hang = nhan.closest('.ant-space');
  if (!hang) throw new Error('⛔ tìm được hàng của danh mục trên cây');
  await nguoiDung.click(within(hang as HTMLElement).getByRole('button', { name: 'Sửa' }));
  await screen.findByText(`Sửa danh mục "${dm.name}"`);
}

beforeEach(() => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
});

afterEach(() => {
  cleanup();
  qc.clear();
  doiTen.mockClear();
});

describe('Danh mục nội dung — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được SaveRequest, và ĐO được đường PUT dùng tới trường nào', () => {
    const truong = truongCuaRecord('SaveRequest');
    expect(truong.length).toBeGreaterThanOrEqual(3);
    expect(truong).toEqual(expect.arrayContaining(['name', 'slug', 'parentId']));

    // ⛔ Ngoại lệ CÓ TÊN, ĐO chứ ⛔ khai bằng chú thích: `rename` ⛔ đụng `parentId`.
    //    Ngày nào nó đụng thì dòng này đỏ và biểu mẫu phải nối thêm ô — ⛔ phải sửa bài cho hết đỏ.
    expect(
      truongRenameDungToi(),
      'CategoryController.rename đã nhận thêm một trường của SaveRequest — biểu mẫu CategoriesPage ' +
        'phải nạp và gửi trường ấy, ⛔ thì mỗi lượt Lưu ghi đè nó bằng null.',
    ).toEqual(['name', 'slug']);
  });

  it('⭐⭐ sửa danh mục → Lưu ⛔ đổi gì ⇒ slug CŨ còn nguyên, ⛔ bị sinh lại từ tên', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, DM_A);
    await screen.findByDisplayValue(DM_A.name);

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(doiTen).toHaveBeenCalled());

    const [id, than] = doiTen.mock.calls.at(-1) as [string, Record<string, unknown>];
    expect(id, 'lượt Lưu phải ghi vào danh mục ĐANG mở').toBe(DM_A.publicId);

    const lech = truongRenameDungToi()
      .map((t) => ({ t, kyVong: (DM_A as Record<string, unknown>)[t], thucTe: than[t] }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `slug` rơi ⛔ để lại ô rỗng nào — service ' +
        'SINH LẠI nó từ tên (`requireUniqueSlug`) ⇒ địa chỉ công khai của cả chuyên mục đổi, mọi ' +
        'liên kết đã phát ra ngoài thành 404, và màn hình báo "Đã cập nhật danh mục".',
    ).toEqual([]);
  });

  it('⭐ mở A → đóng → mở B ⇒ ô mang dữ liệu của B, và lượt Lưu ghi vào ĐÚNG B', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, DM_A);
    await screen.findByDisplayValue(DM_A.slug);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Huỷ' }));

    await moSua(nguoiDung, DM_B);

    expect(
      screen.queryByDisplayValue(DM_A.slug),
      '⛔ Ô "Đường dẫn" đang bày slug của danh mục MỞ TRƯỚC ⇒ một lượt Lưu gán địa chỉ công khai ' +
        'của A cho B, và backend chặn bằng CMS trùng slug hoặc — tệ hơn — nhận nếu A vừa bị xoá.',
    ).toBeNull();
    await screen.findByDisplayValue(DM_B.name);
    expect(screen.getByLabelText('Đường dẫn')).toHaveValue(DM_B.slug);

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(doiTen).toHaveBeenCalled());

    const [id, than] = doiTen.mock.calls.at(-1) as [string, Record<string, unknown>];
    expect(id).toBe(DM_B.publicId);
    expect(than).toMatchObject({ name: DM_B.name, slug: DM_B.slug });
  });
});
