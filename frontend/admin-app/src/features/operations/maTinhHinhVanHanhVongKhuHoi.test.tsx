import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa một mã tình hình vận hành → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 8 trường** — T47.17.
 *
 * <h2>⛔⛔ DTO này là `class`, ⛔ phải `record` — và điều đó đổi hẳn hậu quả của một trường rơi</h2>
 *
 * <p>{@code OperationStatusCodeUpdateRequest} khai {@code boolean hasParameter} và
 * {@code boolean active} ở dạng **nguyên thuỷ**. Với một `record` thì trường thiếu ra {@code null}
 * và service còn cơ hội đọc nó là *"giữ nguyên"*; với `boolean` nguyên thuỷ thì Jackson dựng
 * {@code false}, và {@code apDung} ghi thẳng {@code entity.setActive(false)}. ⇒ **Một khoá JSON
 * vắng mặt ⛔ đọc như *chưa khai*, nó đọc như *TẮT*.**
 *
 * <h2>Hậu quả nghiệp vụ của từng trường bị đánh rơi</h2>
 *
 * <ul>
 *   <li><b>`active`</b> — {@code /operation-status-codes/active} là **nguồn DUY NHẤT** của ô chọn
 *       trên màn hình nhập tình hình vận hành (CN-02.11). Rơi ⇒ mã tắt ⇒ người trực ban **⛔ còn
 *       chọn được tình hình ấy**, trong khi màn hình quản trị vừa báo *"Cập nhật thành công"*.
 *   <li><b>`hasParameter`</b> — cờ bật ô nhập **trị số** đi kèm (lưu lượng, số giờ bơm). Rơi ⇒
 *       *"Chạy 2 máy bơm"* ⛔ còn chỗ ghi **m³/s**, và số liệu vận hành mất phần định lượng.
 *   <li><b>`mappedStatus`</b> — ánh xạ sang **trạng thái công trình dẫn xuất** (quy tắc 4). Rơi ⇒
 *       {@code null} ⇒ một cống *đang bảo trì* hiện **"Bình thường"** trên dashboard trực ban và
 *       trên bản đồ. {@code OperationStatusCodeService.update} so cờ {@code anhXaDoi} rồi
 *       {@code recomputeFor} **hàng loạt công trình** — nên một trường rơi ⛔ dừng ở một dòng.
 *   <li><b>`colorHex`</b> — {@code @NotBlank @Pattern} ⇒ **400 ở mọi lượt sửa**; màu này lên cả
 *       cổng công khai ({@code portalCache.constructionsChanged()} chạy vô điều kiện sau mỗi lượt).
 *   <li><b>`sortOrder`</b> — {@code @NotNull} ⇒ 400; nó là thứ tự ô chọn của người trực ban.
 *   <li><b>`parameterUnit`</b> — {@code setParameterUnit(...)} chạy **VÔ ĐIỀU KIỆN** ⇒ rơi = xoá
 *       trắng đơn vị đo.
 *   <li><b>`code`</b> — khoá nghiệp vụ, {@code @NotBlank}. Ô bị {@code disabled} khi sửa nhưng
 *       **vẫn phải có trong payload**: `disabled` chỉ khoá DOM, ⛔ gỡ trường khỏi kho biểu mẫu.
 * </ul>
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTO = readFileSync(
  join(
    GOC_KHO,
    'backend/operations/src/main/java/com/songnhue/operations/api/dto/OperationStatusCodeUpdateRequest.java',
  ),
  'utf8',
);

function boGhiChu(ma: string): string {
  return ma.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/.*$/gm, ' ');
}

/**
 * Bộ đọc trường cho một DTO dạng **`class`** — ⛔ dùng lại được bộ đọc `record` của sáu bài trước.
 *
 * <p>⚠ Nó bỏ **tường minh** mọi khai báo {@code static}: T48.4 đo được rằng một bộ đọc mã nguồn
 * bỏ sót hằng số {@code static} sẽ cho ra tập **NHỎ HƠN**, tức xanh vì lý do sai. Ở đây chiều
 * ngược lại cũng nguy hiểm — một hằng số lọt vào danh sách trường là một dòng đỏ giả vĩnh viễn.
 *
 * <p>⚠ Phạm vi tự khai: nó chỉ thấy trường khai **trực tiếp** trong tệp này. Lớp ⛔ kế thừa ai
 * (nó chỉ {@code implements OperationStatusCodeFields}), và bài dưới đối chiếu chéo số trường với
 * **số setter** — hai phép đọc độc lập trên cùng một tệp.
 */
function truongCuaClass(): string[] {
  const than = boGhiChu(DTO);
  const truong: string[] = [];
  const mau =
    /(?:^|\n)\s*(?:private|protected|public)\s+((?:static|final|transient|volatile)\s+)*([\w.]+(?:<[^<>]*>)?(?:\[\])?)\s+(\w+)\s*(?:=[^;]*)?;/g;
  let m: RegExpExecArray | null;
  while ((m = mau.exec(than)) !== null) {
    if (m[1]?.includes('static')) continue;
    truong.push(m[3]);
  }
  return truong;
}

/** Phép đọc ĐỘC LẬP thứ hai trên cùng tệp — xem javadoc `truongCuaClass`. */
function soSetter(): number {
  return (boGhiChu(DTO).match(/public\s+void\s+set\w+\s*\(/g) ?? []).length;
}

/**
 * Hai mã tình hình vận hành, **mọi ô của mã A đều có giá trị và ⛔ ô nào trùng mã B**.
 *
 * ⚠ `hasParameter: true` và `active: true` ở mã A là **cố ý**: hai trường ấy là `boolean` nguyên
 * thuỷ nên khoá JSON vắng mặt cho ra `false`. Đặt A ở `false` là để bài xanh trong đúng tình
 * huống nó sinh ra để bắt (luật 9).
 */
const MA_BOM = {
  publicId: 'ma-bom',
  code: 'MB2',
  name: 'Chạy 2 máy bơm tiêu',
  hasParameter: true,
  parameterUnit: 'm3/s',
  colorHex: '#1677ff',
  mappedStatus: 'BINH_THUONG' as const,
  sortOrder: 3,
  active: true,
};

const MA_BAO_TRI = {
  publicId: 'ma-bao-tri',
  code: 'BTC',
  name: 'Đang bảo trì cống',
  // ⚠ CŨNG `true`, và đó là cố ý: với `false` thì ô Đơn vị tham số UNMOUNT ở mã B, nên khẳng định
  //   *"payload của B ⛔ mang đơn vị của A"* sẽ XANH ở CẢ HAI trạng thái — một trường unmount rơi
  //   khỏi payload dù có rò hay ⛔ (luật 9). Đo được: payload khi tắt cờ là `{"hasParameter":false}`.
  hasParameter: true,
  parameterUnit: 'giờ chạy',
  colorHex: '#faad14',
  mappedStatus: 'BAO_TRI' as const,
  sortOrder: 7,
  active: false,
};

let duongCuoi = '';
let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong === '/ops/operation-status-codes') return [MA_BOM, MA_BAO_TRI];
      return [];
    }),
    put: vi.fn(async (duong: string, than: unknown) => {
      duongCuoi = duong;
      thanCuoi = than as Record<string, unknown>;
      return MA_BOM;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
    delete: vi.fn(),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { OperationStatusCodesPage } = await import('./OperationStatusCodesPage');

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
          <OperationStatusCodesPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

/** ⚠ Kiểu CẤU TRÚC, ⛔ `typeof MA_BOM`: mã B có `parameterUnit: null` nên `typeof` ghim `string`. */
async function moSua(
  nguoiDung: ReturnType<typeof userEvent.setup>,
  ma: { code: string; name: string },
) {
  await nguoiDung.click(
    await screen.findByRole('button', { name: `Sửa mã tình hình vận hành ${ma.code}` }),
  );
  await screen.findByText('Sửa Tình trạng Vận hành');
  await screen.findByDisplayValue(ma.name);
}

afterEach(() => {
  cleanup();
  duongCuoi = '';
  thanCuoi = {};
});

describe('Mã tình hình vận hành — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 8 trường của OperationStatusCodeUpdateRequest (DTO dạng class)', () => {
    const truong = truongCuaClass();
    expect(truong.length).toBeGreaterThanOrEqual(8);
    expect(truong).toEqual(
      expect.arrayContaining([
        'code',
        'name',
        'hasParameter',
        'parameterUnit',
        'colorHex',
        'mappedStatus',
        'sortOrder',
        'active',
      ]),
    );
    expect(
      truong.length,
      '⛔ Số trường đọc được ⛔ khớp số setter — bộ đọc `class` đang bỏ sót hoặc nhặt thừa (T48.4: ' +
        'một bộ đọc yếu hơn cho tập NHỎ HƠN, tức xanh vì lý do sai).',
    ).toBe(soSetter());
  });

  it('⭐⭐ sửa mã → Lưu ⛔ đổi gì ⇒ đủ 8 trường, mã ĐANG DÙNG ⛔ tự tắt', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, MA_BOM);
    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaClass()
      .map((t) => ({ t, kyVong: (MA_BOM as Record<string, unknown>)[t], thucTe: thanCuoi[t] }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `mappedStatus` rơi ⇒ cống đang bảo trì ' +
        'hiện "Bình thường" trên dashboard (quy tắc 4); `parameterUnit` bị ghi đè VÔ ĐIỀU KIỆN ' +
        'nên rơi = xoá trắng đơn vị đo.',
    ).toEqual([]);

    expect(
      thanCuoi.active,
      '⛔⛔ `active` là `boolean` NGUYÊN THUỶ: khoá JSON vắng mặt ⇒ `false` ⇒ mã biến khỏi ô chọn ' +
        'của người trực ban, trong khi màn hình vừa báo "Cập nhật thành công".',
    ).toBe(true);
    expect(
      thanCuoi.hasParameter,
      '⛔⛔ `hasParameter` cũng là `boolean` NGUYÊN THUỶ ⇒ rơi = mất ô nhập trị số đi kèm (m³/s).',
    ).toBe(true);
  });

  it('⭐⭐ mở mã A → đóng → mở mã B ⇒ ô mang dữ liệu của B, Lưu ghi vào ĐÚNG B', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, MA_BOM);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Cancel' }));

    await moSua(nguoiDung, MA_BAO_TRI);
    expect(
      screen.queryByDisplayValue(MA_BOM.name),
      '⛔ Ô Tên hiển thị đang bày dữ liệu của mã MỞ TRƯỚC (T63.12 · T51.12 · T53.7).',
    ).toBeNull();
    expect(
      screen.queryByDisplayValue(MA_BOM.code),
      '⛔ Ô Mã (khoá nghiệp vụ, `disabled` khi sửa) đang bày mã MỞ TRƯỚC ⇒ lượt Lưu đổi MÃ của B.',
    ).toBeNull();
    expect(
      screen.queryByDisplayValue(MA_BOM.parameterUnit),
      '⛔ Ô Đơn vị tham số đang bày đơn vị của mã MỞ TRƯỚC ⇒ "Đang bảo trì cống" nhận đơn vị m³/s.',
    ).toBeNull();

    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    expect(duongCuoi, 'lượt Lưu phải ghi vào mã ĐANG mở').toBe(
      `/ops/operation-status-codes/${MA_BAO_TRI.publicId}`,
    );
    expect(
      thanCuoi,
      '⛔ Payload mang dữ liệu của mã mở TRƯỚC ⇒ một lượt Lưu ghi hồ sơ A đè lên B kèm thông báo ' +
        '"Cập nhật thành công".',
    ).toMatchObject({
      code: MA_BAO_TRI.code,
      name: MA_BAO_TRI.name,
      hasParameter: MA_BAO_TRI.hasParameter,
      parameterUnit: MA_BAO_TRI.parameterUnit,
      colorHex: MA_BAO_TRI.colorHex,
      mappedStatus: MA_BAO_TRI.mappedStatus,
      sortOrder: MA_BAO_TRI.sortOrder,
      active: MA_BAO_TRI.active,
    });
    expect(
      thanCuoi.parameterUnit,
      '⛔⛔ Ô Đơn vị tham số của mã B ⛔ mang đúng giá trị của B. `openEditModal` hôm nay ghi đè ' +
        'bằng cách TRẢI cả bản ghi (`...record`) kèm `resetFields()`; ngày ai đó thay phép trải ấy ' +
        'bằng một danh sách khoá gõ tay — đúng hình dạng T63.8 — thì trường vắng mặt biến khỏi ' +
        'payload, và `setParameterUnit(...)` chạy VÔ ĐIỀU KIỆN nên đơn vị đo bị XOÁ TRẮNG. ' +
        '⚠ Lượt phá bản vá đo được đúng `undefined`, ⛔ phải giá trị của A: khẳng định này bắt CẢ ' +
        'hai kiểu hỏng (rơi và rò), nên ⛔ đọc nó là "bài canh rò rỉ".',
    ).toBe(MA_BAO_TRI.parameterUnit);
  });
});
