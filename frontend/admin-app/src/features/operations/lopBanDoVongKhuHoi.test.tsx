import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa một lớp bản đồ → Lưu ⛔ đổi gì → payload phải mang lại đủ trường** — T47.17.
 *
 * <h2>Biểu mẫu này hỏng theo HAI kiểu khác nhau, và ⛔ kiểu nào thay thế được kiểu kia</h2>
 *
 * `GisLayerForm` khai bốn trường trình bày nhận {@code null} = **giữ nguyên**. Nên hậu quả của một
 * trường bị đánh rơi ⛔ giống nhau giữa các trường:
 *
 * <ul>
 *   <li><b>`description`</b> — {@code apDung} gọi {@code layer.setDescription(...)} **VÔ ĐIỀU
 *       KIỆN**, ⛔ có chốt `null`. Rơi = **xoá trắng**. Ô này là nơi duy nhất ghi *nguồn số liệu và
 *       năm số hoá* của một lớp; mất nó là một lớp ranh giới lưu vực ⛔ ai biết vẽ từ bản đồ nào,
 *       và ⛔ có API lịch sử nào dựng lại được.
 *   <li><b>`color` · `opacity` · `active`</b> — {@code null ⇒ giữ nguyên}, nên rơi ⛔ mất dữ liệu
 *       mà làm **chính cái núm ấy thành đồ trang trí**: người vận hành tắt một lớp, bấm Lưu, nhận
 *       *"Đã cập nhật lớp"*, và lớp **vẫn nằm trên bản đồ**. Một lượt lưu báo thành công mà ⛔ đổi
 *       gì là triệu chứng im lặng nhất của luật 27.
 *   <li><b>`name`</b> — {@code @NotBlank} ⇒ **400 ở mọi lượt sửa**, và tên lớp là khoá chống trùng
 *       (`OPS-2024`).
 * </ul>
 *
 * <h2>⚠ NGOẠI LỆ CÓ TÊN: `sortOrder` ⛔ nằm trong biểu mẫu, và đó là đúng</h2>
 *
 * <p>Thứ tự chồng lớp đi bằng đường riêng {@code PATCH /thu-tu}, nhận **toàn bộ danh sách đã sắp**
 * (hai nơi cùng phải biết thứ tự hiện tại là hai nơi sẽ lệch). Hộp thoại sửa gửi
 * {@code sortOrder = null}, và {@code apDung} đọc null = *giữ nguyên* ⇒ một lượt sửa màu ⛔ kéo
 * lớp về đáy chồng.
 *
 * <p>⛔ Ngoại lệ ấy phải được **khai ra và ĐẾM**, ⛔ phải bỏ lặng: bài dưới khẳng định tập miễn trừ
 * đúng bằng {@code ['sortOrder']}, nên một trường thứ hai lặng lẽ rời biểu mẫu ⛔ núp được vào đây
 * (luật 28).
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const CONTROLLER = readFileSync(
  join(
    GOC_KHO,
    'backend/operations/src/main/java/com/songnhue/operations/api/GisLayerController.java',
  ),
  'utf8',
);

/** {@code LayerRequest} là record **lồng trong chính controller**, ⛔ nằm ở tệp `*Dtos.java`. */
function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(CONTROLLER);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong GisLayerController.java`);
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .replace(/<[^<>]*>/g, '')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

/** ⚠ Trường đi bằng đường KHÁC — xem javadoc đầu tệp. */
const MIEN_TRU = ['sortOrder'];

/** Hai lớp bản đồ, **mọi ô có giá trị và ⛔ ô nào trùng nhau** (luật 9 ở cả hai chiều). */
const LOP_KENH = {
  publicId: 'lop-kenh',
  name: 'Kênh mương cấp 1',
  description: 'Số hoá 2024 từ bản đồ địa hình 1:10.000 của Xí nghiệp Hà Đông',
  geometryType: 'LINE' as const,
  color: '#ff4d4f',
  // ⚠ ⛔ phải 70 (giá trị hộp thoại TẠO MỚI tự điền) — trùng nó là bài mù trước một lượt rơi.
  opacity: 45,
  sortOrder: 0,
  // ⚠ ĐANG TẮT: với `true` thì rơi `active` cho ra đúng giá trị cũ ở tầng service.
  active: false,
  coTep: true,
  soDoiTuong: 128,
};

const LOP_LUU_VUC = {
  publicId: 'lop-luu-vuc',
  name: 'Ranh giới lưu vực sông Nhuệ',
  description: 'Nguồn: Viện Quy hoạch Thuỷ lợi, bản 2019 — chưa cập nhật sau điều chỉnh 2023',
  geometryType: 'POLYGON' as const,
  color: '#1677ff',
  opacity: 80,
  sortOrder: 1,
  active: true,
  coTep: true,
  soDoiTuong: 7,
};

let duongCuoi = '';
let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong === '/ops/gis-layers') return [LOP_KENH, LOP_LUU_VUC];
      return [];
    }),
    put: vi.fn(async (duong: string, than: unknown) => {
      duongCuoi = duong;
      thanCuoi = than as Record<string, unknown>;
      return LOP_KENH;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
    patch: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được chạm đường SẮP THỨ TỰ');
    }),
    upload: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { GisLayersPage } = await import('./GisLayersPage');

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
          <GisLayersPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

/** ⚠ Kiểu CẤU TRÚC, ⛔ `typeof LOP_KENH`: hai lớp khác `geometryType` nên `typeof` ghim literal. */
async function moSua(
  nguoiDung: ReturnType<typeof userEvent.setup>,
  lop: { name: string; description: string },
) {
  await nguoiDung.click(await screen.findByRole('button', { name: `Sửa lớp ${lop.name}` }));
  await screen.findByDisplayValue(lop.description);
}

afterEach(() => {
  cleanup();
  duongCuoi = '';
  thanCuoi = {};
});

describe('Lớp bản đồ GIS — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 6 trường của LayerRequest', () => {
    const truong = truongCuaRecord('LayerRequest');
    expect(truong.length).toBeGreaterThanOrEqual(6);
    expect(truong).toEqual(
      expect.arrayContaining(['name', 'description', 'color', 'opacity', 'sortOrder', 'active']),
    );
    expect(
      MIEN_TRU.filter((t) => !truong.includes(t)),
      '⛔ Một trường được MIỄN TRỪ mà ⛔ còn tồn tại trong DTO ⇒ lời miễn trừ đang canh một thứ đã ' +
        'chết, và trường thật sự thiếu ⛔ ai đếm.',
    ).toEqual([]);
  });

  it('⭐⭐ sửa lớp → Lưu ⛔ đổi gì ⇒ đủ trường, lớp ĐANG TẮT ⛔ tự bật lại', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, LOP_KENH);
    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('LayerRequest')
      .filter((t) => !MIEN_TRU.includes(t))
      .map((t) => ({ t, kyVong: (LOP_KENH as Record<string, unknown>)[t], thucTe: thanCuoi[t] }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `description` bị ghi đè VÔ ĐIỀU KIỆN nên ' +
        'rơi = xoá trắng nguồn số liệu của lớp; `color`/`opacity`/`active` rơi thì service đọc ' +
        'null = GIỮ NGUYÊN ⇒ chính cái núm ấy thành đồ trang trí, bấm Lưu báo thành công mà bản ' +
        'đồ ⛔ đổi gì.',
    ).toEqual([]);

    expect(
      MIEN_TRU,
      '⛔ Tập miễn trừ phải đúng bằng ["sortOrder"] — thứ tự chồng lớp đi bằng PATCH /thu-tu. Một ' +
        'trường thứ hai lặng lẽ rời biểu mẫu ⛔ được núp vào đây (luật 28).',
    ).toEqual(['sortOrder']);
    expect(
      thanCuoi.sortOrder ?? null,
      '⛔ Hộp thoại sửa ⛔ được gửi `sortOrder`: gửi một con số cũ là kéo lớp về sai chỗ trong chồng.',
    ).toBeNull();
  });

  it('⭐⭐ mở lớp A → đóng → mở lớp B ⇒ ô mang dữ liệu của B, Lưu ghi vào ĐÚNG B', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, LOP_KENH);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Cancel' }));

    await moSua(nguoiDung, LOP_LUU_VUC);
    expect(
      screen.queryByDisplayValue(LOP_KENH.description),
      '⛔ Ô Mô tả đang bày dữ liệu của lớp MỞ TRƯỚC. `Form.useForm()` sống ở component NGOÀI Modal ' +
        'nên nó ⛔ unmount theo `destroyOnHidden` (T51.12 · T53.7 · T63.12).',
    ).toBeNull();
    expect(
      screen.queryByDisplayValue(LOP_KENH.name),
      '⛔ Ô Tên lớp đang bày dữ liệu của lớp MỞ TRƯỚC ⇒ lượt Lưu đổi TÊN của B thành tên của A, ' +
        'và tên lớp là khoá chống trùng (OPS-2024).',
    ).toBeNull();

    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    expect(duongCuoi, 'lượt Lưu phải ghi vào lớp ĐANG mở').toBe(
      `/ops/gis-layers/${LOP_LUU_VUC.publicId}`,
    );
    expect(
      thanCuoi,
      '⛔ Payload mang dữ liệu của lớp mở TRƯỚC ⇒ một lượt Lưu ghi hồ sơ A đè lên B kèm thông báo ' +
        '"Đã cập nhật lớp".',
    ).toMatchObject({
      name: LOP_LUU_VUC.name,
      description: LOP_LUU_VUC.description,
      color: LOP_LUU_VUC.color,
      opacity: LOP_LUU_VUC.opacity,
      active: LOP_LUU_VUC.active,
    });
  });
});
