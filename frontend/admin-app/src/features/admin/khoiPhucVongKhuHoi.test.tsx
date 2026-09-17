import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Mở hộp thoại khôi phục của bản sao lưu A → đóng → mở của bản B ⇒ ba ô phải RỖNG** — T63.17,
 * hình dạng T51.12 ở dạng <b>nguy hiểm nhất của cả đợt</b>.
 *
 * <h2>⛔⛔⛔ Vì sao ở đây nó ⛔ phải "một ô hiển thị sai"</h2>
 *
 * Ba ô của hộp thoại này <b>LÀ</b> ba lớp chặn mà javadoc {@code RestoreModal} khai ra: gõ đúng
 * cụm {@code SONGNHUE} · lý do đi vào nhật ký bảo mật · <b>mã 2FA nhập lại ngay lúc thao tác</b>.
 * {@code BackupPage} render {@code <RestoreModal backup={restoring} …/>} <b>vô điều kiện</b>,
 * {@code Form.useForm()} nằm NGOÀI {@code Modal}, và {@code onCancel} <b>⛔ gọi
 * {@code resetFields()}</b> — chỉ {@code onClose}.
 *
 * <p>⇒ Người vận hành mở bản sao lưu A, gõ đủ ba ô, đổi ý và bấm <i>Hủy</i>; mở bản sao lưu
 * <b>B</b> thì ba ô <b>đã điền sẵn</b> và nút <i>Khôi phục</i> chỉ còn một cú bấm. Ba lớp chặn
 * dựng cho <i>bản A</i> nay đứng ra bảo lãnh cho <i>bản B</i> — thao tác <b>ghi đè toàn bộ cơ sở
 * dữ liệu</b>, ⛔ có đường lùi nào ngoài bản chụp tự động.
 *
 * <p>⚠ {@code preserve={false}} ⛔ cứu được: nó dọn kho <b>khi Form unmount</b>, mà T53.7 đo được
 * {@code destroyOnHidden} chỉ tháo cây con <b>sau khi hoạt ảnh đóng chạy xong</b>. Một cuộc đua,
 * ⛔ một bảo đảm (luật 7).
 *
 * <p>⚠⚠ Và mã 2FA nằm lại trong DOM sau khi đóng hộp thoại là một khe hở riêng: nó là bí mật
 * <b>một lần</b>, ⛔ phải một giá trị biểu mẫu bình thường.
 */

const A = {
  id: 'bk-a',
  fileName: 'songnhue-2026-09-16-0200.dump',
  startedAt: '2026-09-16T02:00:00Z',
  finishedAt: '2026-09-16T02:04:00Z',
  sizeBytes: 104857600,
  status: 'SUCCEEDED',
  checksumSha256: 'aa11bb22cc33dd44ee55ff6677889900aabbccddeeff00112233445566778899',
  trigger: 'SCHEDULED',
};
const B = { ...A, id: 'bk-b', fileName: 'songnhue-2026-09-17-0200.dump' };

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) =>
      duong === '/backups'
        ? [A, B]
        : { running: false, lastSuccessAt: null, restoreAvailable: true },
    ),
    post: vi.fn(async () => ({ jobId: 'j1' })),
    delete: vi.fn(async () => ({})),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { BackupPage } = await import('./BackupPage');

/** ⚠ MỘT `QueryClient` cho cả bài — provider mới giữa chừng làm vế A → B xanh giả. */
let qc: QueryClient;

function dung() {
  const chuaKhai = () => {
    throw new Error('AuthContext giả: bài kiểm này ⛔ dựng phần đó');
  };
  const auth = {
    status: 'authenticated' as const,
    user: { id: 'toi', username: 'admin' },
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
          <BackupPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

async function moKhoiPhuc(nguoiDung: ReturnType<typeof userEvent.setup>, b: typeof A) {
  const hang = (await screen.findByText(b.fileName)).closest('tr');
  if (!hang) throw new Error(`⛔ tìm thấy hàng của ${b.fileName}`);
  const nut = [...hang.querySelectorAll('button')].find(
    (x) => x.textContent?.trim() === 'Khôi phục',
  );
  if (!nut) throw new Error('⛔ tìm thấy nút Khôi phục của hàng');
  await nguoiDung.click(nut);
  await screen.findByText('Khôi phục dữ liệu từ bản sao lưu');
}

const O_CUM = 'Gõ chính xác "SONGNHUE" để xác nhận';
const O_LY_DO = 'Lý do khôi phục';
const O_2FA = 'Mã xác thực hai bước';

beforeEach(() => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
});

afterEach(() => {
  cleanup();
  qc.clear();
});

describe('Khôi phục dữ liệu — ba lớp chặn ⛔ được mang sang bản sao lưu khác', () => {
  it('⚠ chống tập rỗng: mở lần đầu ⇒ ba ô RỖNG và điền được', async () => {
    const nguoiDung = userEvent.setup();
    dung();
    await moKhoiPhuc(nguoiDung, A);
    expect(screen.getByLabelText(O_CUM)).toHaveValue('');
    await nguoiDung.type(screen.getByLabelText(O_CUM), 'SONGNHUE');
    expect(screen.getByLabelText(O_CUM)).toHaveValue('SONGNHUE');
  });

  it('⭐⭐ điền đủ ba ô cho bản A → Hủy → mở bản B ⇒ ba ô phải RỖNG LẠI', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moKhoiPhuc(nguoiDung, A);
    await nguoiDung.type(screen.getByLabelText(O_CUM), 'SONGNHUE');
    await nguoiDung.type(screen.getByLabelText(O_LY_DO), 'Khôi phục sau sự cố mất điện phòng máy');
    await nguoiDung.type(screen.getByLabelText(O_2FA), '123456');
    await nguoiDung.click(screen.getByRole('button', { name: 'Hủy' }));

    await moKhoiPhuc(nguoiDung, B);

    expect(
      screen.getByLabelText(O_CUM),
      '⛔ Cụm xác nhận gõ cho bản sao lưu A vẫn nằm đó khi mở bản B. Lớp chặn thứ NHẤT của một ' +
        'thao tác GHI ĐÈ TOÀN BỘ CSDL đã được thoả trước, cho một bản sao lưu người dùng chưa hề ' +
        'xác nhận.',
    ).toHaveValue('');
    expect(
      screen.getByLabelText(O_2FA),
      '⛔ Mã 2FA của lượt trước còn trong ô. Nó là bí mật MỘT LẦN và là lớp chặn chứng minh "người ' +
        'đang ngồi đó thật sự giữ thiết bị thứ hai" — javadoc RestoreModal khai đúng câu ấy.',
    ).toHaveValue('');
    expect(
      screen.getByLabelText(O_LY_DO),
      '⛔ Lý do đi vào nhật ký bảo mật là của bản A',
    ).toHaveValue('');
  });

  it('⭐ tên tệp trên hộp thoại phải là bản ĐANG mở — vế phân biệt hai trạng thái', async () => {
    const nguoiDung = userEvent.setup();
    dung();
    await moKhoiPhuc(nguoiDung, A);
    await nguoiDung.click(screen.getByRole('button', { name: 'Hủy' }));
    await moKhoiPhuc(nguoiDung, B);
    expect(screen.getAllByText(B.fileName).length).toBeGreaterThan(0);
  });
});
