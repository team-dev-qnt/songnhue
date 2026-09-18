import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * **Sửa một sự kiện công tác → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 6 trường** — T47.17
 * (CN-04.4).
 *
 * <h2>Trường nào rơi thì hỏng ra sao — timeline là HỒ SƠ PHÁP LÝ của một con người</h2>
 *
 * <ul>
 *   <li><b>`effectiveOn`</b> — **trục** của timeline, và nó ⛔ phải ngày ký. Đặc tả nói thẳng ở
 *       {@code SuKienForm}: *một quyết định ký tháng 3 có hiệu lực từ tháng 1 phải nằm ở tháng 1*.
 *       Đánh rơi nó ⇒ `@NotNull` chặn ⇒ lỗi ồn ào; nhưng đánh rơi **`decisionDate`** thì ⛔ ai chặn,
 *       và lịch sử công tác mất ngày ký của đúng cái quyết định ấy.
 *   <li><b>`decisionNo`</b> — số hiệu quyết định là thứ **duy nhất** tra ngược được ra văn bản gốc
 *       trong tủ hồ sơ. Mất nó là còn lại một dòng chữ ⛔ ai kiểm chứng được, và với loại
 *       **KỶ LUẬT** thì đó là một kết luận ⛔ có căn cứ nằm trong hồ sơ nhân sự.
 *   <li><b>`detail`</b> — diễn giải mang phạm vi áp dụng và thời hạn ("hết hiệu lực sau 3 tháng").
 *       Rơi mất thì một quyết định kỷ luật **có thời hạn** đọc thành một quyết định vô thời hạn.
 *   <li><b>`eventType`</b> — loại sự kiện quyết định dòng ấy hiện dưới nhãn nào và vào báo cáo
 *       BCNS nào. Sai loại là sai thống kê nhân sự trình Ban giám đốc.
 * </ul>
 *
 * <h2>Chỗ khe hở nằm</h2>
 *
 * {@code Modal.onOk} dựng payload bằng cách **liệt kê tay 6 khoá** — đúng hình dạng đã gây ra
 * `T63.8` (menu) và `T63.12` (đơn vị). Nên danh sách trường ở đây đọc từ
 * **`HoSoConDtos.SuKienRequest`**, ⛔ chép tay (luật 14/29).
 *
 * <h2>⚠ Vế *"mở A → đóng → mở B"* ở đây là VẾ MỚI, ⛔ phải bản sao</h2>
 *
 * `hoSoConKhongTronDuLieu.test.tsx` canh vế ấy cho **`LyLichFormModal`** — một hộp thoại khác, chỉ
 * *cùng họ*. Đo được: `grep -c SuKienFormModal` trong tệp ấy = **0**. `SuKienFormModal` là màn hình
 * **thứ tư** dính cơ chế `rc-field-form` của T51.12, và javadoc của nó tự khai rằng thứ có thể lẫn
 * giữa hai người ở đây là **quyết định kỷ luật** — nên vế ấy phải được **ĐO**, ⛔ suy ra từ việc
 * đọc mã (T53.7: ba biện pháp phòng chồng nhau ⛔ cộng lại thành an toàn).
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTOS = readFileSync(
  join(GOC_KHO, 'backend/hr/src/main/java/com/songnhue/hr/api/HoSoConDtos.java'),
  'utf8',
);

function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(DTOS);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong HoSoConDtos.java`);
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
 * Một quyết định **KỶ LUẬT** — loại sự kiện mà mỗi trường rơi đều đổi nghĩa pháp lý của dòng.
 *
 * ⚠ Mọi ô đều có giá trị: ô trống làm bài mù trước chính thứ nó canh (rỗng ↔ rơi ⛔ phân biệt
 * được — luật 9).
 */
const SK_A = {
  publicId: 'sk-a',
  eventType: 'KY_LUAT' as const,
  effectiveOn: '2026-03-01',
  decisionNo: '145/QĐ-TLSN',
  decisionDate: '2026-02-18',
  title: 'Khiển trách — chậm báo cáo vận hành cống Vân Đình',
  detail: 'Áp dụng theo Nội quy lao động Điều 27; hết hiệu lực sau 3 tháng kể từ ngày hiệu lực',
  updatedAt: '2026-03-02T01:00:00Z',
};

/** Sự kiện của **người khác**, ⛔ ô nào trùng A — giá trị trùng làm bài mù trước vế "trộn A vào B". */
const SK_B = {
  publicId: 'sk-b',
  eventType: 'DIEU_DONG' as const,
  effectiveOn: '2025-11-15',
  decisionNo: '087/QĐ-TLSN',
  decisionDate: '2025-11-02',
  title: 'Điều động về Xí nghiệp Thuỷ lợi Thanh Trì',
  detail: 'Giữ nguyên hệ số lương và phụ cấp chức vụ',
  updatedAt: '2025-11-16T01:00:00Z',
};

let duongCuoi = '';
let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async () => []),
    put: vi.fn(async (duong: string, than: unknown) => {
      duongCuoi = duong;
      thanCuoi = than as Record<string, unknown>;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { SuKienFormModal } = await import('./SuKienFormModal');

/**
 * ⚠⚠ `qc` dựng ở `beforeEach` và dùng lại NGUYÊN cho mọi lượt `rerender`.
 *
 * Dựng một `QueryClientProvider` MỚI giữa chừng làm React tháo cả cây con — lúc ấy vế "mở A → đóng
 * → mở B" thành **XANH GIẢ**, vì thứ dọn sạch biểu mẫu là lượt unmount của bài kiểm chứ ⛔ phải cơ
 * chế của màn hình.
 */
let qc: QueryClient;

function boc(children: React.ReactNode) {
  return (
    <QueryClientProvider client={qc}>
      <AntdApp>{children}</AntdApp>
    </QueryClientProvider>
  );
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

describe('Sự kiện công tác — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 6 trường của HoSoConDtos.SuKienRequest', () => {
    const truong = truongCuaRecord('SuKienRequest');
    expect(truong.length).toBeGreaterThanOrEqual(6);
    expect(truong).toEqual(
      expect.arrayContaining([
        'eventType',
        'effectiveOn',
        'decisionNo',
        'decisionDate',
        'title',
        'detail',
      ]),
    );
  });

  it('⭐⭐ sửa sự kiện → Lưu ⛔ đổi gì ⇒ đủ 6 trường, số + ngày ký quyết định còn nguyên', async () => {
    const nguoiDung = userEvent.setup();
    render(
      boc(
        <SuKienFormModal open hoSoId="hs-a" suKien={SK_A} onClose={() => {}} onSaved={() => {}} />,
      ),
    );

    await screen.findByDisplayValue(SK_A.title);
    await screen.findByDisplayValue(SK_A.decisionNo);

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('SuKienRequest')
      .map((t) => ({ t, kyVong: (SK_A as Record<string, unknown>)[t], thucTe: thanCuoi[t] }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `decisionNo`/`decisionDate` là đường duy ' +
        'nhất tra ngược ra văn bản gốc — mất chúng là một kết luận KỶ LUẬT ⛔ còn căn cứ nào trong ' +
        'hồ sơ; `detail` mang thời hạn áp dụng, rơi mất thì quyết định có thời hạn đọc thành vô ' +
        'thời hạn.',
    ).toEqual([]);

    expect(duongCuoi).toBe(`/hr/employees/hs-a/timeline/${SK_A.publicId}`);
  });

  it('⭐⭐ mở sự kiện của A → đóng → mở của B ⇒ ô mang dữ liệu B, và Lưu ghi vào ĐÚNG B', async () => {
    const nguoiDung = userEvent.setup();
    const { rerender } = render(
      boc(
        <SuKienFormModal open hoSoId="hs-a" suKien={SK_A} onClose={() => {}} onSaved={() => {}} />,
      ),
    );
    await screen.findByDisplayValue(SK_A.title);

    rerender(
      boc(
        <SuKienFormModal
          open={false}
          hoSoId="hs-a"
          suKien={SK_A}
          onClose={() => {}}
          onSaved={() => {}}
        />,
      ),
    );
    rerender(
      boc(
        <SuKienFormModal open hoSoId="hs-b" suKien={SK_B} onClose={() => {}} onSaved={() => {}} />,
      ),
    );

    await waitFor(() => expect(screen.getByLabelText('Nội dung')).toHaveValue(SK_B.title));
    expect(
      screen.queryByDisplayValue(SK_A.decisionNo),
      '⛔ Ô "Số quyết định" đang bày số của sự kiện MỞ TRƯỚC. `Form.useForm()` nằm ở component ' +
        'ngoài Modal ⇒ `initialValues` ⛔ được áp lại cho lượt mở thứ hai (T51.12 · T53.7).',
    ).toBeNull();
    expect(screen.getByLabelText('Số quyết định')).toHaveValue(SK_B.decisionNo);
    expect(screen.getByLabelText('Diễn giải')).toHaveValue(SK_B.detail);

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    expect(duongCuoi, 'lượt Lưu phải ghi vào sự kiện ĐANG mở, của ĐÚNG hồ sơ đang mở').toBe(
      `/hr/employees/hs-b/timeline/${SK_B.publicId}`,
    );
    expect(
      thanCuoi,
      '⛔ Payload mang dữ liệu của sự kiện mở TRƯỚC ⇒ một lượt Lưu chép quyết định kỷ luật của ' +
        'người A sang hồ sơ người B, kèm thông báo "Đã cập nhật sự kiện".',
    ).toMatchObject({
      eventType: SK_B.eventType,
      effectiveOn: SK_B.effectiveOn,
      decisionNo: SK_B.decisionNo,
      decisionDate: SK_B.decisionDate,
      title: SK_B.title,
      detail: SK_B.detail,
    });
  });
});
