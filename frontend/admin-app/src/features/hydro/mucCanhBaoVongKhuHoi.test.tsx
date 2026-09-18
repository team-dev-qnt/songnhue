import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa một mức cảnh báo → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 6 trường** — T47.17.
 *
 * <h2>Hậu quả nghiệp vụ của từng trường bị đánh rơi</h2>
 *
 * <ul>
 *   <li><b>`active`</b> — `AlertLevelService.create/update` quy {@code active == null || active},
 *       tức {@code null ⇒ BẬT}. Đánh rơi trường này là **bật lại một mức vừa cố ý tắt**, và
 *       javadoc của chính ô ấy nói ra hậu quả: *"tắt một mức làm im MỌI ngưỡng đang trỏ vào nó"*.
 *       Người trực ban tắt cấp Báo động I trong lúc trạm đang sửa chữa; một lượt sửa ghi chú sau
 *       đó bật lại toàn bộ chuông, ⛔ ai bấm gì cả. Đây là vế **AN TOÀN**, ⛔ phải mất dữ liệu.
 *   <li><b>`severityRank`</b> — hạng nặng nhẹ, thứ quyết định *"vượt nhiều mức cùng lúc thì cảnh
 *       báo mang mức nào"*. `@NotNull` ⇒ đánh rơi là **400 ở mọi lượt sửa**, và hạng là trường
 *       {@code UNIQUE} nghiệp vụ (`HYD-1002` khi trùng) nên ⛔ có cách đoán lại.
 *   <li><b>`colorToken`</b> — khoá màu dùng chung cho **marker GIS, biểu tuyến sông và lịch sử
 *       cảnh báo**. `@NotBlank` ⇒ 400. ⛔ Nó ⛔ phải mã hex: một chuỗi lạ đi lọt tới màn hình rồi
 *       hiện lên đúng chữ ấy (T35.14).
 *   <li><b>`description`</b> — `muc.setDescription(...)` chạy **VÔ ĐIỀU KIỆN**, nên đánh rơi là
 *       **xoá trắng** ghi chú, ⛔ một dòng log, ⛔ một thông báo (§11.19).
 *   <li><b>`code`</b> — mã ngắn đi vào **báo cáo và bản kết xuất**. `@NotBlank` ⇒ 400.
 * </ul>
 *
 * <h2>Chỗ khe hở nằm</h2>
 *
 * <p>`moSua` gọi {@code form.setFieldsValue(row)} rồi {@code onFinish} gửi **nguyên giá trị biểu
 * mẫu**. Danh sách trường vì thế ⛔ nằm trong một mảng gõ tay — nó nằm trong **tập `Form.Item` đã
 * khai**, và một `Form.Item` bị xoá ⛔ để lại dấu vết nào ở tầng TypeScript (mọi trường của
 * {@code AlertLevelRequest} sau `code`/`name`/`colorToken`/`severityRank` đều **tuỳ chọn**).
 * ⇒ Bài này đọc danh sách trường thẳng từ {@code HydroAlertDtos.AlertLevelRequest}.
 *
 * <p>⚠ Dữ liệu thử dùng **`active: false`** có chủ đích: với `true` thì đánh rơi trường ấy cho ra
 * ĐÚNG giá trị cũ và bài kiểm ⛔ phân biệt được hai trạng thái (luật 9).
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
 * Hai mức cảnh báo, **mọi ô có giá trị và ⛔ ô nào trùng nhau**.
 *
 * ⚠ Ô rỗng làm bài mù trước vế *"trường bị đánh rơi"* (rỗng ↔ rơi ⛔ phân biệt được); giá trị
 * trùng làm bài mù trước vế *"trộn A vào B"*. Luật 9 ở cả hai chiều.
 */
const BD1 = {
  id: 'muc-bd1',
  code: 'BD1',
  name: 'Báo động I',
  colorToken: 'alert-level-1',
  severityRank: 10,
  // ⚠ ĐANG TẮT — đúng giá trị mà mặc định lặng `null ⇒ true` sẽ đảo nếu trường bị đánh rơi.
  active: false,
  description: 'Tạm tắt trong lúc trạm Lương Cổ sửa chữa — bật lại khi nghiệm thu xong',
};

const BD3 = {
  id: 'muc-bd3',
  code: 'BD3',
  name: 'Báo động III',
  colorToken: 'alert-level-3',
  severityRank: 30,
  active: true,
  description: 'Mức cao nhất — chuyển cả nhóm trực ban và lãnh đạo Xí nghiệp',
};

let duongCuoi = '';
let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong === '/hyd/alert-levels') return [BD1, BD3];
      return [];
    }),
    put: vi.fn(async (duong: string, than: unknown) => {
      duongCuoi = duong;
      thanCuoi = than as Record<string, unknown>;
      return BD1;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
    delete: vi.fn(),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { AlertLevelsPage } = await import('./AlertLevelsPage');

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
          <AlertLevelsPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

async function moSua(nguoiDung: ReturnType<typeof userEvent.setup>, muc: typeof BD1) {
  await nguoiDung.click(
    await screen.findByRole('button', { name: `Sửa mức cảnh báo ${muc.code}` }),
  );
  await screen.findByText('Sửa mức cảnh báo');
  await screen.findByDisplayValue(muc.name);
}

afterEach(() => {
  cleanup();
  duongCuoi = '';
  thanCuoi = {};
});

describe('Mức cảnh báo — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 6 trường của AlertLevelRequest', () => {
    const truong = truongCuaRecord('AlertLevelRequest');
    expect(truong.length).toBeGreaterThanOrEqual(6);
    expect(truong).toEqual(
      expect.arrayContaining([
        'code',
        'name',
        'colorToken',
        'severityRank',
        'active',
        'description',
      ]),
    );
  });

  it('⭐⭐ sửa mức → Lưu ⛔ đổi gì ⇒ đủ 6 trường, mức ĐANG TẮT ⛔ tự bật lại', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, BD1);
    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('AlertLevelRequest')
      .map((t) => ({ t, kyVong: (BD1 as Record<string, unknown>)[t], thucTe: thanCuoi[t] }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `description` bị ghi đè VÔ ĐIỀU KIỆN nên ' +
        'rơi = xoá trắng; `colorToken` nuôi marker GIS lẫn biểu tuyến sông; `severityRank` là thứ ' +
        'quyết định "vượt nhiều mức thì cảnh báo mang mức nào".',
    ).toEqual([]);

    expect(
      thanCuoi.active,
      '⛔⛔ `AlertLevelService` quy `active == null ⇒ TRUE`. Đánh rơi trường này là BẬT LẠI một ' +
        'mức vừa cố ý tắt ⇒ mọi ngưỡng trỏ vào nó phát chuông trở lại, ⛔ ai bấm gì cả.',
    ).toBe(false);
  });

  it('⭐⭐ mở mức A → đóng → mở mức B ⇒ ô mang dữ liệu của B, Lưu ghi vào ĐÚNG B', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, BD1);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Cancel' }));

    await moSua(nguoiDung, BD3);
    expect(
      screen.queryByDisplayValue(BD1.name),
      '⛔ Ô Tên mức đang bày dữ liệu của mức MỞ TRƯỚC (T63.12 · T51.12 · T53.7).',
    ).toBeNull();
    expect(
      screen.queryByDisplayValue(BD1.description),
      '⛔ Ô Ghi chú đang bày dữ liệu của mức MỞ TRƯỚC ⇒ một lượt Lưu ghi ghi chú của A đè lên B.',
    ).toBeNull();

    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    expect(duongCuoi, 'lượt Lưu phải ghi vào mức ĐANG mở').toBe(`/hyd/alert-levels/${BD3.id}`);
    expect(
      thanCuoi,
      '⛔ Payload mang dữ liệu của mức mở TRƯỚC ⇒ một lượt Lưu ghi hồ sơ A đè lên B kèm thông báo ' +
        '"Đã cập nhật mức cảnh báo".',
    ).toMatchObject({
      code: BD3.code,
      name: BD3.name,
      colorToken: BD3.colorToken,
      severityRank: BD3.severityRank,
      active: BD3.active,
      description: BD3.description,
    });
  });
});
