import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

/**
 * **Sửa một mục lý lịch → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 9 trường** — T47.17 (CN-04.3).
 *
 * <h2>Trường nào mất thì hỏng ra sao</h2>
 *
 * <ul>
 *   <li><b>`expiresOn`</b> — nặng nhất. Để TRỐNG nghĩa là *"⛔ hết hiệu lực"* (đúng cho bằng đại
 *       học), nên đánh rơi nó ⛔ để lại ô rỗng đáng ngờ nào: một **chứng chỉ hành nghề** vừa mất
 *       hạn dùng sẽ **thôi xuất hiện** trong cảnh báo hết hạn M4.9, và thứ ấy im lặng cho tới ngày
 *       có người cầm chứng chỉ quá hạn đi vận hành công trình.
 *   <li><b>`certificateNo`</b> · <b>`institution`</b> — số hiệu và nơi cấp là thứ duy nhất tra cứu
 *       ngược được khi cần xác minh; mất chúng là còn lại một dòng chữ ⛔ ai kiểm chứng được.
 *   <li><b>`grade`</b> · <b>`major`</b> — căn cứ xét chuyên môn trên báo cáo BCNS.
 * </ul>
 *
 * <h2>Chỗ khe hở nằm</h2>
 *
 * `onOk` dựng payload bằng cách **liệt kê tay 9 khoá** — hình dạng đã gây ra `T63.8`, `T63.12`. Nên
 * danh sách trường ở đây đọc từ **`HoSoConDtos.LyLichRequest`**, ⛔ chép tay (luật 14/29).
 *
 * <p>⚠ **Vế *"mở A → đóng → mở B"* của màn hình này đã có bài riêng** —
 * `hoSoConKhongTronDuLieu.test.tsx` (T53.7/T53.8), nên bài này ⛔ lặp lại nó. Ghi ra đây để lượt rà
 * sau ⛔ đọc sự vắng mặt ấy thành một khoảng trống.
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

/** Một **chứng chỉ có hạn** — loại mục duy nhất mà `expiresOn` mất đi là mất một cái chuông. */
const MUC = {
  publicId: 'll-1',
  kind: 'CHUNG_CHI' as const,
  name: 'Chứng chỉ hành nghề tư vấn giám sát',
  grade: 'Hạng II',
  major: 'Thuỷ lợi — công trình thuỷ',
  institution: 'Bộ Xây dựng',
  certificateNo: 'HNTV-2023-04127',
  issuedOn: '2023-06-15',
  expiresOn: '2028-06-15',
  note: 'Gia hạn trước 3 tháng theo quy định',
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

const { LyLichFormModal } = await import('./LyLichFormModal');

function dung() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <AntdApp>
        <LyLichFormModal open hoSoId="nv-1" muc={MUC} onClose={() => {}} onSaved={() => {}} />
      </AntdApp>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  duongCuoi = '';
  thanCuoi = {};
});

describe('Mục lý lịch — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 9 trường của LyLichRequest', () => {
    const truong = truongCuaRecord('LyLichRequest');
    expect(truong.length).toBeGreaterThanOrEqual(9);
    expect(truong).toEqual(
      expect.arrayContaining(['kind', 'name', 'grade', 'certificateNo', 'issuedOn', 'expiresOn']),
    );
  });

  it('⭐⭐ sửa mục lý lịch → Lưu ⛔ đổi gì ⇒ đủ 9 trường, hạn hiệu lực còn nguyên', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await screen.findByDisplayValue(MUC.name);
    await screen.findByDisplayValue(MUC.certificateNo);

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('LyLichRequest')
      .map((t) => ({ t, kyVong: (MUC as Record<string, unknown>)[t], thucTe: thanCuoi[t] }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `expiresOn` mất đi ⛔ để lại ô rỗng đáng ' +
        'ngờ nào — nó có nghĩa "⛔ hết hiệu lực", nên một chứng chỉ hành nghề thôi xuất hiện trong ' +
        'cảnh báo hết hạn M4.9 và ⛔ ai biết cho tới ngày cần tới nó.',
    ).toEqual([]);

    expect(duongCuoi).toBe(`/hr/employees/nv-1/ly-lich/${MUC.publicId}`);
  });
});
