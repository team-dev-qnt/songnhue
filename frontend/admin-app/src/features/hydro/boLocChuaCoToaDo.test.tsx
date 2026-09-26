import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';
import { type Station } from '@/shared/api-types';

/**
 * ⭐⭐ **Danh sách "chưa số hoá vị trí" phải BÀY RA ĐƯỢC, ⛔ phải một con số** — T35.2.
 *
 * <h2>Vế bị bác ngày 04/09/2026</h2>
 *
 * Lượt kiểm chứng độc lập giữ dòng nợ này ở `[~]` với đúng một lý do: `chuaSoHoaViTri` chỉ được
 * đọc ở **một** chỗ — `OperationsDashboardPage` lấy `.length` đổ vào một câu chữ. Sáu trường
 * `publicId`/`code`/`name`/`positionRole`/`riverName`/`chainage` đi ra dây rồi **bị vứt**:
 * ⛔ bảng, ⛔ modal, ⛔ nút xuất. Người đọc sổ tưởng Công ty mở màn hình là thấy 19 dòng để đi cấp
 * toạ độ; thực tế họ chỉ thấy **con số 19**.
 *
 * <h2>⛔ Vì sao danh sách nằm ở ĐÂY chứ ⛔ ở dashboard</h2>
 *
 * Chỗ **NHẬP** toạ độ là màn hình Điểm đo — biểu mẫu sửa và nút *Nhập vị trí từ tệp* (T42.20).
 * Một drawer chỉ-đọc trên dashboard là thêm một nơi **hiển thị** mà vẫn ⛔ ai làm được gì. Và
 * màn hình này đã có sẵn đúng khuôn: `Segmented` *"Tất cả / Chưa gán đơn vị / Chưa liên kết
 * công trình"*, mỗi mục kèm số đếm.
 *
 * <h2>⚠ Cờ do BACKEND tính</h2>
 *
 * `chuaSoHoaViTri` là cờ trên từng dòng, cùng vị từ với `/map-points` (`Station.chuaSoHoaViTri`).
 * Tự so `!s.latitude || !s.longitude` ở FE là **bản sao thứ ba**, và ⛔ có gì buộc nó bằng con số
 * dashboard đang hiện — đúng thứ `T68.36` vừa trả giá. Bộ canh vế backend: `StationMapHttpTest`.
 */

const CO_TOA_DO = diemDo('F01519', 'Trạm Lương Cổ', false);
const THIEU_1 = diemDo('F01520', 'Trạm Vân Đình', true);
const THIEU_2 = diemDo('F01521', 'Trạm Nhật Tựu', true);

function diemDo(code: string, name: string, chuaSoHoaViTri: boolean): Station {
  return {
    id: `id-${code}`,
    code,
    name,
    apiCode: code,
    apiSourceId: null,
    apiSourceCode: null,
    positionRole: 'MN_SONG',
    orgUnitId: 'dv-1',
    orgUnitName: 'Xí nghiệp A',
    riverName: null,
    chainage: null,
    chainageM: null,
    latitude: chuaSoHoaViTri ? null : '21.048201',
    longitude: chuaSoHoaViTri ? null : '105.782500',
    interpolated: false,
    active: true,
    description: null,
    measurementTypes: [],
    constructions: [],
    thieuLienKetCongTrinh: false,
    chuaGanDonVi: false,
    chuaSoHoaViTri,
  } as unknown as Station;
}

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong === '/hyd/stations') return [CO_TOA_DO, THIEU_1, THIEU_2];
      if (duong === '/hyd/api-sources') return [];
      if (duong === '/hyd/measurement-types') return [];
      if (duong === '/org-units/tree') return [];
      return [];
    }),
    put: vi.fn(async () => CO_TOA_DO),
    post: vi.fn(async () => CO_TOA_DO),
  },
}));

const { StationsPage } = await import('./StationsPage');

function dung(duongDan = '/thuy-van/diem-do') {
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
          <MemoryRouter initialEntries={[duongDan]}>
            <StationsPage />
            <HienUrl />
          </MemoryRouter>
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

/**
 * Bày phần truy vấn của URL ra DOM để khẳng định được — `MemoryRouter` ⛔ đụng `window.location`.
 */
function HienUrl() {
  return <span data-testid="url-hien-tai">{useLocation().search}</span>;
}

/** Tên điểm đo đang hiện trong THÂN bảng — ⛔ tính chữ ở ô lọc hay ở modal. */
function tenDangHien(): string[] {
  const than = document.querySelector('.ant-table-tbody');
  if (!than) return [];
  return [CO_TOA_DO, THIEU_1, THIEU_2]
    .filter((s) => within(than as HTMLElement).queryByText(s.name) !== null)
    .map((s) => s.name);
}

afterEach(() => {
  cleanup();
});

describe('Bộ lọc "Chưa có toạ độ" (T35.2)', () => {
  it('⭐⭐ ô lọc có mặt kèm SỐ ĐẾM, và nó lọc đúng tập', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    const o = await screen.findByText('Chưa có toạ độ (2)');
    // Tiền đề (luật 7): chưa lọc thì bảng phải đủ ba dòng, nếu không mọi khẳng định dưới đây
    // đều xanh trên một bảng vốn đã rỗng.
    await waitFor(() => expect(tenDangHien()).toHaveLength(3));

    await nguoiDung.click(o);

    expect(tenDangHien()).toEqual([THIEU_1.name, THIEU_2.name]);
  });

  /**
   * ⭐ Đường vào sâu từ ô ghi chú bản đồ của dashboard: con số ở đó dẫn thẳng vào đây **đã bật
   * sẵn** ô lọc. ⛔ có vế này thì người đọc vẫn phải tự tìm đúng ô sau khi bấm — tức con số vẫn
   * ⛔ bấm được theo nghĩa nó hứa.
   */
  it('⭐ `?loc=CHUA_CO_TOA_DO` bật sẵn ô lọc ngay lượt dựng đầu', async () => {
    dung('/thuy-van/diem-do?loc=CHUA_CO_TOA_DO');

    await waitFor(() => expect(tenDangHien()).toEqual([THIEU_1.name, THIEU_2.name]));
  });

  /**
   * ⛔⛔ Tham số URL là **dữ liệu người lạ gõ được**, và `docBoLoc` tồn tại để nó ⛔ chui thẳng vào
   * `BoLoc` bằng một phép ép kiểu.
   *
   * ⚠⚠ **Bản đầu của bài này là XANH GIẢ, và lượt phá bắt được.** Nó chỉ khẳng định *bảng vẫn đủ
   * ba dòng* — mà với `raw as BoLoc` thì giá trị rác rơi xuống **nhánh mặc định** của chuỗi lọc,
   * nên bảng vẫn đủ ba dòng và bài vẫn xanh trên đúng bản hỏng nó sinh ra để bắt (luật 9).
   *
   * ⇒ Vế phân biệt THẬT là ô `Segmented`: một `value` ⛔ khớp option nào làm **⛔ ô nào sáng**.
   * Bảng trông bình thường trong khi thanh lọc ⛔ nói được người dùng đang xem tập gì — và đó
   * mới là thứ hỏng.
   */
  it('⛔ `?loc=` rác ⇒ ô "Tất cả" SÁNG, ⛔ phải một thanh lọc ⛔ ô nào được chọn', async () => {
    dung('/thuy-van/diem-do?loc=KHONG-CO-THAT');

    await waitFor(() => expect(tenDangHien()).toHaveLength(3));

    const dangSang = document.querySelector('.ant-segmented-item-selected');
    expect(dangSang?.textContent, 'thanh lọc phải chỉ rõ đang xem tập nào').toBe('Tất cả (3)');
  });

  /**
   * ⚠ Một lượt bấm tay phải THẮNG tham số URL — `boLocThuCong` đứng trước trong chuỗi `??`.
   */
  it('⚠ bấm tay sau khi tới bằng liên kết ⇒ đổi được', async () => {
    const nguoiDung = userEvent.setup();
    dung('/thuy-van/diem-do?loc=CHUA_CO_TOA_DO');

    await waitFor(() => expect(tenDangHien()).toHaveLength(2));

    await nguoiDung.click(screen.getByText('Tất cả (3)'));

    await waitFor(() => expect(tenDangHien()).toHaveLength(3));
  });

  /**
   * ⛔⛔ **Bài này ra đời vì một lượt phá ⛔ làm đỏ được gì.**
   *
   * Bản nháp của tôi khẳng định trong javadoc rằng ⛔ gỡ `?loc=` thì *"URL sẽ thắng lại ở lượt
   * render kế và ô lọc tự nhảy về"*. Gỡ hẳn đoạn gỡ tham số ⇒ **⛔ bài nào đỏ**: `boLocThuCong`
   * giữ nguyên qua mọi lượt render nên URL ⛔ thắng lại được. Chú thích ấy **nói quá**, và một
   * chú thích nói quá nguy hiểm hơn ⛔ có chú thích (T53.8).
   *
   * ⇒ Lý do THẬT — và nay có bài kiểm: **F5 và chia sẻ liên kết**. Một URL còn `?loc=CHUA_CO_TOA_DO`
   * trong khi màn hình đang xem *Tất cả* là một URL **nói dối**; người nhận mở ra sẽ thấy một màn
   * hình khác thứ người gửi đang nhìn. Cùng lý do với `xoaMaDatSan` ở cùng tệp.
   */
  it('⛔ bấm tay gỡ luôn `?loc=` — URL ⛔ được nói khác màn hình', async () => {
    const nguoiDung = userEvent.setup();
    dung('/thuy-van/diem-do?loc=CHUA_CO_TOA_DO');

    // Tiền đề (luật 7): tham số phải ĐANG CÓ, nếu không bài này xanh vì ⛔ có gì để gỡ.
    expect(screen.getByTestId('url-hien-tai').textContent).toBe('?loc=CHUA_CO_TOA_DO');

    await nguoiDung.click(await screen.findByText('Tất cả (3)'));

    await waitFor(() => expect(screen.getByTestId('url-hien-tai').textContent).toBe(''));
  });
});
