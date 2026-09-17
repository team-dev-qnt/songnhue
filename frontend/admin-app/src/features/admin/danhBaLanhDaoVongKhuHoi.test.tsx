import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa một dòng danh bạ lãnh đạo → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 5 trường** — T47.17.
 *
 * <h2>⛔⛔ Vì sao bảng này là chỗ nguy hiểm nhất của cả nhóm</h2>
 *
 * `org_unit_leaders` là bảng **đọc ra CỔNG CÔNG KHAI**: trang *Lãnh đạo Công ty*
 * ({@code /gioi-thieu/lanh-dao}, CR-25) và cột *"Giám đốc XN"* của bảng 6 cột (CR-26) ⛔ có nguồn
 * nào khác. Một trường rơi ở đây ⛔ dừng trong nội bộ — nó **hiện ra cho người ngoài đọc**, và
 * người nhập liệu ⛔ thấy gì vì màn hình quản trị báo *"Đã cập nhật"*.
 *
 * <p>Và bảng này **có tiền sử đúng ở chiều ghi**: dựng 27/08/2026 kèm repository + endpoint công
 * khai, rồi chạy suốt một đợt với **đường đọc mà ⛔ có đường ghi nào** — ⛔ controller, ⛔ màn hình
 * (§10.62, javadoc `OrgUnitLeaderService`). Đó là vụ thứ ba cùng hình dạng trong một tuần.
 *
 * <h2>Trường nào rơi thì hỏng ra sao</h2>
 *
 * <ul>
 *   <li><b>`phone`</b> — một trong **ba cột** bảng công khai in ra (tên · chức danh · điện thoại).
 *       {@code ganTruong} quy rỗng về `NULL`, nên rơi mất là ô điện thoại **biến khỏi cổng**, và
 *       luật 16 đúng ở chiều ngược: người đọc sẽ hiểu là *"đơn vị này chưa công bố số"*.
 *   <li><b>`title`</b> — hiện <b>nguyên văn</b> ở cột 2. Rơi mất thì backend chặn bằng
 *       {@code @NotBlank}, nhưng một lượt Lưu **trộn** dòng khác vào thì ⛔ ai chặn: cổng đăng sai
 *       chức danh của một người có thật, dưới tên Công ty.
 *   <li><b>`sortOrder`</b> — <b>mặc định lặng</b>: {@code setSortOrder(sortOrder == null ? 0 : …)}
 *       ⇒ rơi mất là dòng ấy **nhảy lên đầu** trang Lãnh đạo Công ty, tức đảo thứ tự cấp bậc mà
 *       Công ty tự sắp. ⇒ Dữ liệu thử để `sortOrder ≠ 0`, nếu ⛔ thì hai trạng thái ra cùng một
 *       payload và bài ⛔ khẳng định gì (luật 9).
 *   <li><b>`email`</b> — ⛔ hiện trên cổng (CR-25 chốt bảng 3 cột), nhưng là đường liên hệ nội bộ
 *       duy nhất lưu cho người ấy.
 * </ul>
 *
 * <h2>Ngoại lệ CÓ TÊN: `active` ⛔ nằm trong `LeaderRequest`</h2>
 *
 * Công tắc *"Hiện trên cổng"* đi bằng endpoint riêng {@code PUT …/{id}/active}, và
 * {@code OrgUnitLeaderController} khai nguyên do: bắt một thao tác một-cú-bấm gửi kèm cả họ tên và
 * chức danh nghĩa là *"mỗi lần gửi lại là một cơ hội ghi đè nhầm trường khác"*. Nên bài này đọc
 * danh sách trường từ **`LeaderRequest`**, ⛔ từ `LeaderRow` — hai record khác nhau **cố ý**.
 *
 * <h2>Vế 2 — ⛔ được trộn hai con người (T51.12 · T53.7)</h2>
 *
 * {@code LeaderModal} giữ {@code Form.useForm()} ở component **NGOÀI** {@code Modal} và được
 * {@code OrgUnitLeadersPanel} render **vô điều kiện**. Đúng hình dạng T51.12. Ở đây có hai biện
 * pháp phòng ({@code destroyOnHidden} + {@code preserve={false}}), mà T53.7 đo được rằng **biện pháp
 * phòng chồng nhau ⛔ cộng lại thành an toàn** ⇒ vế này phải được **ĐO**, ⛔ suy từ việc đọc mã.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const CONTROLLER = readFileSync(
  join(
    GOC_KHO,
    'backend/core/src/main/java/com/songnhue/core/api/org/OrgUnitLeaderController.java',
  ),
  'utf8',
);

function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(CONTROLLER);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong OrgUnitLeaderController.java`);
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .replace(/<[^<>]*>/g, '')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

/** Giám đốc Xí nghiệp — dòng ĐẦU trang Lãnh đạo Công ty. `sortOrder` cố ý ⛔ phải 0. */
const LD_A = {
  publicId: 'ld-a',
  fullName: 'Nguyễn Văn Thắng',
  title: 'Giám đốc Xí nghiệp',
  phone: '024.3355.1188',
  email: 'thang.nv@thuyloisongnhue.vn',
  sortOrder: 3,
  active: true,
};

/** Người thứ hai — ⛔ ô nào trùng A, kể cả `active`, để vế "trộn A vào B" phân biệt được. */
const LD_B = {
  publicId: 'ld-b',
  fullName: 'Trần Thị Hoà',
  title: 'Phó Giám đốc phụ trách kỹ thuật',
  phone: '024.3688.2299',
  email: 'hoa.tt@thuyloisongnhue.vn',
  sortOrder: 7,
  active: false,
};

const DON_VI = 'xn-ha-dong';
const GOC = `/org-units/${DON_VI}/leaders`;

let duongCuoi = '';
let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => (duong === GOC ? [LD_A, LD_B] : [])),
    put: vi.fn(async (duong: string, than: unknown) => {
      duongCuoi = duong;
      thanCuoi = than as Record<string, unknown>;
      return LD_A;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
    delete: vi.fn(),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { OrgUnitLeadersPanel } = await import('./OrgUnitLeadersPanel');

/** ⚠ Một `QueryClient` duy nhất cho cả bài — provider mới giữa chừng làm vế A → B xanh giả. */
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
          <OrgUnitLeadersPanel orgUnitPublicId={DON_VI} />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

async function moSua(nguoiDung: ReturnType<typeof userEvent.setup>, ld: typeof LD_A) {
  await screen.findByText(ld.fullName);
  await nguoiDung.click(
    await screen.findByRole('button', { name: `Sửa dòng danh bạ ${ld.fullName}` }),
  );
  await screen.findByText('Sửa dòng danh bạ');
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

describe('Danh bạ lãnh đạo — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 5 trường của LeaderRequest từ OrgUnitLeaderController.java', () => {
    const truong = truongCuaRecord('LeaderRequest');
    expect(truong.length).toBeGreaterThanOrEqual(5);
    expect(truong).toEqual(
      expect.arrayContaining(['fullName', 'title', 'phone', 'email', 'sortOrder']),
    );
    // ⛔ Ngoại lệ CÓ TÊN: `active` đi bằng endpoint riêng — nếu ngày nào nó lọt vào `LeaderRequest`
    //    thì lời khai ở javadoc hết đúng và bài này phải được đọc lại, ⛔ phải sửa cho hết đỏ.
    expect(
      truong,
      '`active` lọt vào LeaderRequest ⇒ quyết định tách endpoint đã bị đảo; đọc lại javadoc ' +
        'OrgUnitLeaderController trước khi sửa bài kiểm.',
    ).not.toContain('active');
  });

  it('⭐⭐ sửa dòng danh bạ → Lưu ⛔ đổi gì ⇒ đủ 5 trường, số điện thoại lên cổng còn nguyên', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, LD_A);
    // ⚠ Mốc chờ là trường BẮT BUỘC, ⛔ phải `phone`: một lượt phá thử gỡ `phone` sẽ làm bài đỏ ở
    //   BƯỚC DỰNG và thông điệp chẩn đoán thật ⛔ bao giờ in ra (luật 10 · §11.20).
    await screen.findByDisplayValue(LD_A.fullName);

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('LeaderRequest')
      .map((t) => ({ t, kyVong: (LD_A as Record<string, unknown>)[t], thucTe: thanCuoi[t] }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì — và bảng này ĐỌC RA CỔNG CÔNG KHAI. ' +
        '`phone` rơi ⇒ ô điện thoại biến khỏi trang Lãnh đạo Công ty (CR-25) và người đọc hiểu là ' +
        '"chưa công bố"; `sortOrder` rơi ⇒ mặc định lặng về 0 ⇒ dòng nhảy lên ĐẦU, đảo thứ tự cấp ' +
        'bậc Công ty tự sắp.',
    ).toEqual([]);

    expect(duongCuoi).toBe(`${GOC}/${LD_A.publicId}`);
  });

  it('⭐⭐ mở A → đóng → mở B ⇒ ô mang dữ liệu của B, và lượt Lưu ghi vào ĐÚNG B', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, LD_A);
    await screen.findByDisplayValue(LD_A.fullName);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Huỷ' }));

    await moSua(nguoiDung, LD_B);

    expect(
      screen.queryByDisplayValue(LD_A.phone),
      '⛔ Ô "Điện thoại liên hệ" đang bày số của người MỞ TRƯỚC. `Form.useForm()` nằm ở component ' +
        'ngoài Modal và panel render nó vô điều kiện ⇒ hình dạng T51.12 — và hậu quả ở đây là số ' +
        'điện thoại của người A đăng công khai dưới tên người B.',
    ).toBeNull();
    await screen.findByDisplayValue(LD_B.fullName);
    expect(screen.getByLabelText('Điện thoại liên hệ')).toHaveValue(LD_B.phone);
    expect(screen.getByLabelText('Chức danh')).toHaveValue(LD_B.title);

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    expect(duongCuoi, 'lượt Lưu phải ghi vào dòng ĐANG mở').toBe(`${GOC}/${LD_B.publicId}`);
    expect(
      thanCuoi,
      '⛔ Payload mang dữ liệu của người mở TRƯỚC ⇒ cổng công khai đăng tên/chức danh/điện thoại ' +
        'của người A dưới bản ghi của người B, kèm thông báo "Đã cập nhật".',
    ).toMatchObject({
      fullName: LD_B.fullName,
      title: LD_B.title,
      phone: LD_B.phone,
      email: LD_B.email,
      sortOrder: LD_B.sortOrder,
    });
  });
});
