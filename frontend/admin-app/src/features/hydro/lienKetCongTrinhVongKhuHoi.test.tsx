import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Station } from '@/shared/api-types';

/**
 * **Mở liên kết của điểm đo A → đóng → mở điểm đo B ⇒ ô *Vai trò* phải là của B** — T63.17,
 * hình dạng T51.12 lần thứ NĂM.
 *
 * <h2>Vì sao ở màn hình này nó ⛔ dừng ở một ô hiển thị sai</h2>
 *
 * {@code LienKetCongTrinhModal} khai {@code initialValues={{ role: diemDo?.positionRole, … }}} —
 * một giá trị **phụ thuộc bản ghi** — trong khi {@code Form.useForm()} sống ở component NGOÀI
 * {@code Modal} và {@code StationsPage} render nó **vô điều kiện**. Đo được trong
 * {@code rc-field-form@2.7.1}: {@code setInitialValues(iv, init)} chạy
 * {@code merge(initialValues, this.store)} ⇒ **kho giá trị THẮNG `initialValues`**. Nên lượt mở
 * thứ hai bày lại vai trò của điểm đo TRƯỚC, và {@code onCancel} gọi {@code resetFields()} cũng
 * ⛔ cứu được: {@code resetFields()} ⛔ xoá trắng, nó đưa kho về {@code this.initialValues} —
 * tức {@code initialValues} của lượt TRƯỚC.
 *
 * <p>⛔⛔ Hậu quả nghiệp vụ: chính ô này quyết định điểm đo được xếp cột **TL hay HL** trong biểu
 * tổng hợp theo tuyến sông (chú thích {@code extra} của ô nói đúng câu ấy), và ràng buộc *"liên
 * kết CHÍNH phải trùng vai trò của hồ sơ điểm đo"* khiến một vai trò sai vừa đủ hợp lệ để lưu
 * xuống. Trực ban sau đó đọc mực nước thượng lưu ở cột hạ lưu — ⛔ một dòng lỗi nào.
 *
 * <p>⚠ Bài này ⛔ được {@code rerender} bằng một {@code QueryClientProvider} MỚI: cả cây unmount
 * thì nó xanh kể cả khi bản vá đã bị gỡ hẳn. Một {@code QueryClient} duy nhất, chỉ đổi prop
 * {@code diemDo} — đúng đường người dùng đi (bấm nút Liên kết ở dòng khác của bảng).
 */

function dungDiemDo(p: Partial<Station> & Pick<Station, 'id' | 'code' | 'positionRole'>): Station {
  return {
    name: `Điểm đo ${p.code}`,
    apiCode: `F0${p.code}`,
    apiSourceId: null,
    apiSourceCode: null,
    orgUnitId: null,
    orgUnitName: null,
    riverName: null,
    chainage: null,
    chainageM: null,
    latitude: null,
    longitude: null,
    interpolated: false,
    active: true,
    description: null,
    measurementTypes: [],
    constructions: [],
    thieuLienKetCongTrinh: false,
    chuaGanDonVi: false,
    ...p,
  } as Station;
}

/** Điểm đo THƯỢNG LƯU của một cống. */
const DIEM_A = dungDiemDo({ id: 'tram-a', code: '1501', positionRole: 'THUONG_LUU' });
/** Điểm đo HẠ LƯU — vai trò cố ý khác A, ⛔ thì hai trạng thái ra cùng một ô (luật 9). */
const DIEM_B = dungDiemDo({ id: 'tram-b', code: '1502', positionRole: 'HA_LUU' });

let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    getPage: vi.fn(async () => ({
      items: [
        { publicId: 'ct-1', code: 'CT-01', name: 'Cống Liên Mạc' },
        { publicId: 'ct-2', code: 'CT-02', name: 'Cống Vân Đình' },
      ],
      meta: { page: 0, size: 100, total: 2, totalPages: 1 },
    })),
    post: vi.fn(async (_duong: string, than: unknown) => {
      thanCuoi = than as Record<string, unknown>;
      return {};
    }),
    delete: vi.fn(),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { LienKetCongTrinhModal } = await import('./LienKetCongTrinhModal');

let qc: QueryClient;

/** Một cây duy nhất; đổi điểm đo bằng `rerender` trên CÙNG provider — xem javadoc. */
function dung(diemDo: Station | null) {
  return render(
    <QueryClientProvider client={qc}>
      <AntdApp>
        <LienKetCongTrinhModal diemDo={diemDo} onClose={() => {}} onDone={() => {}} />
      </AntdApp>
    </QueryClientProvider>,
  );
}

function cay(diemDo: Station | null) {
  return (
    <QueryClientProvider client={qc}>
      <AntdApp>
        <LienKetCongTrinhModal diemDo={diemDo} onClose={() => {}} onDone={() => {}} />
      </AntdApp>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  thanCuoi = {};
});

afterEach(() => {
  cleanup();
  qc.clear();
});

const O_VAI_TRO = 'Vai trò của điểm đo với công trình này';

describe('Liên kết công trình — vòng khứ hồi giữa hai điểm đo', () => {
  it('⚠ chống tập rỗng: mở điểm đo A ⇒ ô Vai trò bày đúng vai trò của A', async () => {
    const { rerender } = dung(null);
    rerender(cay(DIEM_A));
    await screen.findByText('Thượng lưu');
  });

  it('⭐⭐ mở A → đóng → mở B ⇒ ô Vai trò là của B, ⛔ giữ lại của A', async () => {
    const { rerender } = dung(null);
    rerender(cay(DIEM_A));
    await screen.findByText('Thượng lưu');

    // Đóng đúng đường người dùng đi: nút X / ESC / bấm nền đều rơi vào `onCancel`.
    rerender(cay(null));
    rerender(cay(DIEM_B));

    expect(
      await screen.findByText('Hạ lưu'),
      '⛔ Ô "Vai trò" đang bày vai trò của điểm đo MỞ TRƯỚC. Kho giá trị của `Form.useForm()` ' +
        'sống lâu hơn hộp thoại và THẮNG `initialValues` (rc-field-form: merge(iv, store)) ⇒ ' +
        'liên kết được khai với vai trò sai, và đó chính là thứ xếp cột TL/HL của biểu tổng hợp ' +
        'theo tuyến sông.',
    ).toBeInTheDocument();
    expect(screen.queryByText('Thượng lưu')).toBeNull();
  });

  it('⭐⭐ và lượt Lưu gửi lên vai trò của B — ⛔ phải của A', async () => {
    const nguoiDung = userEvent.setup();
    const { rerender } = dung(null);
    rerender(cay(DIEM_A));
    await screen.findByText('Thượng lưu');
    rerender(cay(null));
    rerender(cay(DIEM_B));
    await screen.findByText('Hạ lưu');

    await nguoiDung.click(screen.getByLabelText('Công trình'));
    await nguoiDung.click(await screen.findByTitle('CT-01 — Cống Liên Mạc'));
    await nguoiDung.click(screen.getByRole('button', { name: 'Khai liên kết' }));

    await vi.waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));
    expect(
      thanCuoi.role,
      '⛔ Payload mang vai trò của điểm đo mở TRƯỚC ⇒ một hàng `station_constructions` sai vai ' +
        'trò được ghi xuống kèm thông báo "Đã khai liên kết".',
    ).toBe('HA_LUU');
  });

  it('⚠ ô Vai trò phải có mặt — nếu nhãn đổi thì ba bài trên xanh vì lý do SAI', () => {
    render(cay(DIEM_A));
    expect(screen.getByLabelText(O_VAI_TRO)).toBeTruthy();
  });
});
