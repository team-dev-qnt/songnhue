import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/app/auth/AuthProvider';
import { useAuth } from '@/app/auth/useAuth';
import type { MeResponse } from '@/shared/api-types';

/**
 * **Đăng xuất phải xoá ĐỆM TRUY VẤN, ⛔ chỉ xoá trạng thái đăng nhập — ASVS 8.2.3 (T63.3).**
 *
 * ## Khe hở đã có thật, và ⛔ dòng nợ nào sở hữu nó
 *
 * Lượt tự đánh giá ASVS (T61.28) liệt kê mục 8.2.3 trong bảng, nhưng nó rơi ra ngoài **cả hai**
 * gói việc tiếp theo: ⛔ có trong `T61.48` (7 mục chờ quyết định), ⛔ có trong `T61.49` (5 mục
 * chờ đo trên máy chủ). Nó nằm im cho tới lượt kiểm kê 16/09.
 *
 * ## Vì sao nó ⛔ phải chuyện dọn dẹp cho gọn
 *
 * `endSession()` cũ dọn ba thứ — token, `user`, `status` — và cả ba đều là **trạng thái đăng
 * nhập**. Bản thân **dữ liệu** thì nằm trong bộ nhớ của TanStack Query với `gcTime` mặc định
 * **5 phút**. Trong cửa sổ ấy, người kế tiếp đăng nhập trên cùng trình duyệt sẽ thấy đệm của
 * người trước **hiện ra trước** rồi mới bị thay, vì `staleTime: 30_000` cho phép react-query
 * phục vụ bản đệm ngay trong lúc nạp lại.
 *
 * <p>Máy dùng chung ở văn phòng là ca **thường**, ⛔ phải ca hiếm. Và thứ nằm trong đệm ⛔ phải
 * dữ liệu vô hại: hồ sơ CBNV, danh bạ, nhật ký kiểm toán, và những màn hình 🔒 vừa **giải mã**
 * CCCD / số tài khoản / lương (quy tắc 10, NĐ 13/2023).
 *
 * ## Bài kiểm này đo cái gì
 *
 * ⛔ Nó ⛔ hỏi *"mã có gọi `queryClient.clear()` ⛔"* — đó là canh văn bản (luật 2), và nó sẽ
 * xanh cả khi lời gọi ấy nằm ở một nhánh ⛔ bao giờ chạy tới. Nó nạp dữ liệu vào đệm **thật**,
 * bấm đăng xuất qua giao diện **thật**, rồi hỏi chính đệm ấy còn gì ⛔.
 *
 * <h2>⛔⛔ Và ba bài đầu tiên của chính tệp này MÙ trước đường phổ biến nhất — T85.13</h2>
 *
 * Bản đầu (16/09) giả `onSessionEvent: () => () => {}`, tức nó **bịt** đúng đường mà javadoc của
 * {@code endSession} nêu **đích danh** là lý do bảo đảm phải nằm ở đó: *"phiên còn kết thúc qua
 * đường khác (backend thu hồi phiên ⇒ `onSessionEvent`…)"*. Đo 23/09: nhánh {@code 'sessionLost'}
 * **chép ba dòng** của {@code endSession} vào chỗ khác thay vì **gọi** nó ⇒ ⛔ có
 * {@code queryClient.clear()}. Token bị dọn (ở {@code apiClient}), {@code user}/{@code status} bị
 * dọn — chỉ **dữ liệu** ở lại, đúng thứ T63.3 sinh ra để xoá.
 *
 * <p>Đường ấy ⛔ phải ca hiếm: nó là *token hết hạn*, *phiên bị thu hồi từ xa*, *lượt làm mới
 * hỏng* — phổ biến hơn hẳn việc bấm nút Đăng xuất. Ba bài cũ xanh, và cái xanh ấy đọc như một lời
 * bảo đảm cho **cả** ASVS 8.2.3 (luật 28). ⇒ Mẫu giả nay **phát được** sự kiện thật.
 */

const HO_SO: MeResponse = {
  id: '00000000-0000-0000-0000-000000000001',
  username: 'ketoan.a',
  fullName: 'Người dùng thứ nhất',
  orgUnitId: null,
  roles: ['ADMIN_HR'],
  permissions: ['hr:employee:view-sensitive'],
  mustChangePassword: false,
  twoFactorEnrolled: true,
  coHoSoNhanSu: true,
};

const postGia = vi.fn();
const getGia = vi.fn();
const bootstrapGia = vi.fn();

/**
 * Người nghe mà `AuthProvider` đăng ký — giữ lại để bài kiểm **phát** được sự kiện thật.
 *
 * ⚠ Mẫu giả cũ trả một hàm rỗng, nên `AuthProvider` đăng ký vào hư vô và nhánh
 * `'sessionLost'` ⛔ bao giờ chạy trong bộ kiểm. Đó là lý do khe hở T85.13 vô hình.
 */
type SuKienPhien = { type: 'sessionLost'; reason: string } | { type: 'maintenance' };
const nguoiNghe: Array<(e: SuKienPhien) => void> = [];

/** Phát một sự kiện phiên đúng như `apiClient` làm, trong `act` để React kịp dựng lại. */
async function phatSuKien(e: SuKienPhien) {
  await act(async () => {
    nguoiNghe.forEach((f) => f(e));
    await Promise.resolve();
  });
}

vi.mock('@/shared/apiClient', () => ({
  api: {
    get: (url: string) => getGia(url) as unknown,
    post: (url: string, body?: unknown) => postGia(url, body) as unknown,
  },
  bootstrapSession: () => bootstrapGia() as unknown,
  clearTokens: vi.fn(),
  setAccessToken: vi.fn(),
  onSessionEvent: (listener: (e: SuKienPhien) => void) => {
    nguoiNghe.push(listener);
    return () => {
      const i = nguoiNghe.indexOf(listener);
      if (i >= 0) {
        nguoiNghe.splice(i, 1);
      }
    };
  },
  ApiClientError: class extends Error {},
}));

/** Khoá truy vấn của một màn hình 🔒 — đúng loại dữ liệu ⛔ được sống qua lượt đăng xuất. */
const KHOA_NHAY_CAM = ['hr', 'employee', 'ho-so-nhay-cam', 'NV-0042'];

const DU_LIEU_NHAY_CAM = {
  hoTen: 'Nguyễn Văn A',
  soCccd: '001199012345',
  soTaiKhoan: '19001234567890',
  luongCoBan: '18500000',
};

function NutDangXuat() {
  const { logout, status } = useAuth();
  return (
    <div>
      <span>trạng thái: {status}</span>
      <button type="button" onClick={() => void logout()}>
        Đăng xuất
      </button>
    </div>
  );
}

function dungManHinh(queryClient: QueryClient) {
  return render(
    <AntdApp>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <NutDangXuat />
        </AuthProvider>
      </QueryClientProvider>
    </AntdApp>,
  );
}

describe('Đăng xuất và đệm truy vấn', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    nguoiNghe.length = 0;
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    bootstrapGia.mockResolvedValue(true);
    getGia.mockResolvedValue(HO_SO);
    postGia.mockResolvedValue(undefined);
  });

  afterEach(cleanup);

  it('⭐⭐ bấm Đăng xuất thì dữ liệu 🔒 trong đệm biến mất, ⛔ nằm lại chờ người kế tiếp', async () => {
    queryClient.setQueryData(KHOA_NHAY_CAM, DU_LIEU_NHAY_CAM);

    // Tiền đề phải là một KHẲNG ĐỊNH, ⛔ phải một giả định: nếu lượt seed ⛔ vào đệm thì
    // phép so sau đó xanh vì lý do sai (luật 7 — xanh trên tập rỗng).
    expect(queryClient.getQueryData(KHOA_NHAY_CAM)).toEqual(DU_LIEU_NHAY_CAM);

    dungManHinh(queryClient);
    await screen.findByText('trạng thái: authenticated');

    await userEvent.click(screen.getByRole('button', { name: 'Đăng xuất' }));
    await waitFor(() => expect(screen.getByText('trạng thái: anonymous')).toBeTruthy());

    expect(queryClient.getQueryData(KHOA_NHAY_CAM)).toBeUndefined();
  });

  it('đệm rỗng HOÀN TOÀN sau đăng xuất — ⛔ chỉ mỗi khoá bài kiểm này biết tên', async () => {
    // Vế phân biệt với một bản vá chỉ xoá đúng vài khoá được liệt kê tay. Danh sách gõ tay
    // là luật 28 ở dạng tệ nhất: nó đúng hôm nay và hụt đúng vào màn hình 🔒 thêm ngày mai.
    queryClient.setQueryData(KHOA_NHAY_CAM, DU_LIEU_NHAY_CAM);
    queryClient.setQueryData(['danh-ba'], [{ hoTen: 'Trần Thị B', dienThoai: '0912345678' }]);
    queryClient.setQueryData(['audit', 'logs'], [{ hanhDong: 'EMPLOYEE_UPDATED' }]);

    dungManHinh(queryClient);
    await screen.findByText('trạng thái: authenticated');

    await userEvent.click(screen.getByRole('button', { name: 'Đăng xuất' }));
    await waitFor(() => expect(screen.getByText('trạng thái: anonymous')).toBeTruthy());

    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
  });

  it('⛔ đệm cũng phải sạch khi phiên kết thúc vì BACKEND từ chối, ⛔ vì người dùng bấm nút', async () => {
    // `logout` gọi `POST /auth/logout` rồi mới dọn. Nếu lời gọi ấy hỏng — phiên đã bị thu hồi
    // từ xa, mạng đứt — thì đường dọn vẫn phải chạy: *"đăng xuất hỏng nên vẫn ở trong"* ⛔ phải
    // một trạng thái được phép tồn tại, và dữ liệu 🔒 nằm lại là phần nguy hiểm của nó.
    postGia.mockRejectedValue(new Error('403 AUTH-0005'));
    queryClient.setQueryData(KHOA_NHAY_CAM, DU_LIEU_NHAY_CAM);

    dungManHinh(queryClient);
    await screen.findByText('trạng thái: authenticated');

    await userEvent.click(screen.getByRole('button', { name: 'Đăng xuất' }));
    await waitFor(() => expect(screen.getByText('trạng thái: anonymous')).toBeTruthy());

    expect(queryClient.getQueryData(KHOA_NHAY_CAM)).toBeUndefined();
  });

  it('⛔⛔ T85.13: phiên mất vì TOKEN HẾT HẠN cũng phải xoá đệm — đường này phổ biến hơn nút Đăng xuất', async () => {
    // Đường `sessionLost`: token hết hạn · phiên bị thu hồi từ xa · lượt làm mới hỏng.
    // `apiClient` đã `clearTokens()` rồi mới phát sự kiện, nên token ⛔ phải thứ còn lại —
    // thứ còn lại là DỮ LIỆU, đúng cái T63.3 sinh ra để xoá.
    queryClient.setQueryData(KHOA_NHAY_CAM, DU_LIEU_NHAY_CAM);
    queryClient.setQueryData(['danh-ba'], [{ hoTen: 'Trần Thị B', dienThoai: '0912345678' }]);
    expect(queryClient.getQueryData(KHOA_NHAY_CAM)).toEqual(DU_LIEU_NHAY_CAM);

    dungManHinh(queryClient);
    await screen.findByText('trạng thái: authenticated');

    await phatSuKien({ type: 'sessionLost', reason: 'Phiên đăng nhập đã hết hạn' });

    // Tiền đề, ⛔ phải kết luận: khẳng định này chứng minh sự kiện ĐÃ tới được `AuthProvider`.
    // Thiếu nó thì một mẫu giả ⛔ đăng ký được người nghe sẽ làm bài đỏ với chẩn đoán SAI —
    // *"bản vá ⛔ chạy"* trong khi sự thật là *"sự kiện ⛔ bao giờ được phát"* (§11.19).
    await waitFor(() => expect(screen.getByText('trạng thái: anonymous')).toBeTruthy());

    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
  });

  it('⭐ vế phân biệt: báo BẢO TRÌ ⛔ phải kết thúc phiên ⇒ đệm phải CÒN NGUYÊN', async () => {
    // ⛔ Có vế này thì một bản vá gọi `endSession()` cho MỌI sự kiện phiên cũng xanh — và nó
    // sai: banner bảo trì ⛔ đăng xuất ai cả, xoá đệm ở đó là ném đi công việc dở của người
    // đang dùng. Luật 9: một khẳng định ⛔ phân biệt được hai trạng thái thì ⛔ khẳng định gì.
    queryClient.setQueryData(KHOA_NHAY_CAM, DU_LIEU_NHAY_CAM);

    dungManHinh(queryClient);
    await screen.findByText('trạng thái: authenticated');

    await phatSuKien({ type: 'maintenance' });

    expect(screen.getByText('trạng thái: authenticated')).toBeTruthy();
    expect(queryClient.getQueryData(KHOA_NHAY_CAM)).toEqual(DU_LIEU_NHAY_CAM);
  });
});
