import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * **Mốc đo của hộp thoại *Nhập tay số đo* phải là giờ của lượt mở HIỆN TẠI** — T63.17.
 *
 * <h2>Cùng cơ chế T51.12, hậu quả ⛔ phải "trộn hai bản ghi" mà là một MỐC THỜI GIAN SAI</h2>
 *
 * {@code SuspectReadingsPage} render {@code <NhapTaySoDoModal open={moNhapTay} …/>} <b>vô điều
 * kiện</b>, {@code Form.useForm()} nằm NGOÀI {@code Modal}, và ô <i>Thời điểm đo</i> lấy giá trị mặc
 * định từ {@code initialValues={{ mocDo: dayjs() }}} — một giá trị tính lúc render. Nhưng
 * {@code rc-field-form@2.7.1} áp {@code merge(initialValues, this.store)} ⇒ <b>kho THẮNG</b>, nên
 * từ lượt mở thứ hai trở đi ô ấy vẫn là {@code dayjs()} của lượt mở <b>ĐẦU TIÊN</b>.
 *
 * <p>{@code onCancel} gọi {@code resetFields()} và điều đó <b>⛔ cứu được</b>: {@code resetFields()}
 * đặt kho về {@code this.initialValues} — tức chính cái {@code dayjs()} cũ.
 *
 * <p>⛔⛔ Vì sao nó nặng: hộp thoại này là <b>đường ghi tay duy nhất</b> khi API gián đoạn, và
 * chính nó khai <i>"⛔ ghi đè được số đo đã có"</i>. Quy tắc 18 nói nguồn ⛔ có API lịch sử ⇒ một
 * số đo đóng vào sai khung 10 phút là <b>sai vĩnh viễn</b>, và đường sửa duy nhất là hàng chờ
 * <i>Nghi ngờ</i> — vốn dựng cho sai số của <i>thiết bị</i>, ⛔ phải cho sai số của biểu mẫu.
 *
 * <p>⚠ Bài dùng đồng hồ giả: hai lượt mở cách nhau <b>2 giờ</b>, ⛔ thì hai trạng thái ra cùng một
 * ô và bài ⛔ khẳng định gì (luật 9).
 */

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: { post: vi.fn(async () => ({})) },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { NhapTaySoDoModal } = await import('./NhapTaySoDoModal');

const DIEM_DO = [
  {
    id: 'tram-1',
    code: '1501',
    name: 'Cống Liên Mạc',
    measurementTypes: [{ id: 'mt-1', code: 'MUC_NUOC', name: 'Mực nước', unit: 'm' }],
  },
] as never[];

let qc: QueryClient;

function cay(open: boolean) {
  return (
    <QueryClientProvider client={qc}>
      <AntdApp>
        <NhapTaySoDoModal open={open} diemDo={DIEM_DO} onClose={() => {}} onDone={() => {}} />
      </AntdApp>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date('2026-09-17T03:00:00+07:00'));
});

afterEach(() => {
  vi.useRealTimers();
  cleanup();
  qc.clear();
});

describe('Nhập tay số đo — mốc đo ⛔ được đóng băng ở lượt mở đầu', () => {
  it('⚠ chống tập rỗng: lượt mở ĐẦU bày đúng giờ hiện tại', async () => {
    const { rerender } = render(cay(false));
    rerender(cay(true));
    const o = (await screen.findByLabelText('Thời điểm đo')) as HTMLInputElement;
    expect(o.value).toContain('17/09/2026 03:00');
  });

  it('⭐⭐ mở lúc 03:00 → đóng → mở lúc 05:00 ⇒ ô Thời điểm đo phải là 05:00', async () => {
    const nguoiDung = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const { rerender } = render(cay(false));
    rerender(cay(true));
    await screen.findByLabelText('Thời điểm đo');

    await nguoiDung.click(screen.getByRole('button', { name: 'Đóng' }));
    rerender(cay(false));

    vi.setSystemTime(new Date('2026-09-17T05:00:00+07:00'));
    rerender(cay(true));

    const o = (await screen.findByLabelText('Thời điểm đo')) as HTMLInputElement;
    expect(
      o.value,
      '⛔ Ô "Thời điểm đo" đang giữ giờ của lượt mở ĐẦU TIÊN. `initialValues={{ mocDo: dayjs() }}` thua ' +
        'kho giá trị của `Form.useForm()` (rc-field-form: merge(iv, store)), và `resetFields()` ' +
        'đưa kho về đúng cái `dayjs()` cũ ấy. Nguồn ⛔ có API lịch sử ⇒ một số đo đóng vào sai ' +
        'khung 10 phút là sai VĨNH VIỄN (quy tắc 18).',
    ).toContain('17/09/2026 05:00');
  });
});
