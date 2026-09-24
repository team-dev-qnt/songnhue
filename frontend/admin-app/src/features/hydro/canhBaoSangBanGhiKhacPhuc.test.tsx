import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';
import { type AlertEventRow, type Station } from '@/shared/api-types';

/**
 * ⭐⭐ **Từ một cảnh báo ngưỡng sang bản ghi khắc phục** — T33.10.
 *
 * <h2>⛔⛔ Khuyết tật đo 24/09/2026: một cơ chế ĐỦ MẢNH mà ⛔ đường nào đi tới</h2>
 *
 * `alert_event_public_id` có cột, có trường trên entity, có trong DTO tạo, có `OPS-2021` từ chối
 * một giá trị ⛔ trỏ vào cảnh báo nào, có `HydroAlertPort.alertEventExists`. Và **⛔ một người dùng
 * nào ghi nổi vào đó**: `dungPayloadSuaChua` (đường TẠO) ⛔ gửi trường ấy, còn `dungPayloadSuaBanGhi`
 * (đường SỬA) chỉ **chép lại** `row.alertEventId` — thứ vĩnh viễn `null` vì ⛔ ai đặt lần đầu.
 * Đúng hình dạng **T54.1** (*"Học vấn"* — tám mảnh, ⛔ đường vào lẫn đường ra), và nó sống được vì
 * mỗi mảnh nhìn riêng đều đúng.
 *
 * <h2>Ba trở ngại `AlertHistoryPage` tự kê ra từ WS-33, đo lại 24/09</h2>
 *
 * <ol>
 *   <li>*"⛔ có tuyến `/van-hanh/bao-tri`"* — <b>vẫn đúng</b>, lịch sử bảo trì nằm trong trang chi
 *       tiết công trình. ⇒ Mở thẳng `MaintenanceFormModal` tại chỗ, ⛔ điều hướng đi đâu cả.
 *   <li>*"dòng cảnh báo ⛔ mang định danh công trình"* — <b>vẫn đúng</b>: `AlertEventRow` chỉ có
 *       `stationId`/`stationCode`/`stationName`. ⇒ Tra `GET /hyd/stations/{publicId}`, đã trả kèm
 *       `constructions[]`; ⛔ cần đổi một dòng backend nào.
 *   <li>*"biểu mẫu nhận chưa đọc tham số `alertEventId`"* — <b>vẫn đúng</b>. ⇒ Hai prop mới.
 * </ol>
 *
 * <p>⇒ Lý do T23.8 nêu (*"một liên kết trỏ tới route ⛔ có thật trông như chức năng có mà hỏng"*)
 * ⛔ còn áp dụng, vì ⛔ có liên kết nào — hộp thoại mở tại chỗ.
 */

/**
 * ⚠⚠ **⛔ ép kiểu `as unknown as AlertEventRow` ở đây.**
 *
 * Bản đầu của tôi có ép, và nó che mất `conditionType: 'GREATER_THAN'` — một giá trị ⛔ tồn tại
 * (kiểu thật là `'GT' | 'LT' | 'OUT_OF_RANGE' | 'RATE_OF_CHANGE'`). Hệ quả: `LOAI_DIEU_KIEN_NGUONG`
 * tra ra `undefined` và **ô bảng NÉM ngay trong lúc render**, nên cả năm bài đỏ với một câu ⛔ liên
 * quan gì tới thứ chúng canh. Đúng T51.14 — `tsc` bắt được thứ `vitest` ⛔ thấy — chỉ là phép ép
 * kiểu đã tự tay tắt nó đi. Đồ gá **phải chịu** kiểu thật.
 */
const CANH_BAO: AlertEventRow = {
  id: 'cb-0001',
  stationId: 'tram-0001',
  stationCode: 'F01519',
  stationName: 'Trạm Lương Cổ',
  measurementTypeName: 'Mực nước',
  unit: 'm',
  alertLevelCode: 'BD3',
  alertLevelName: 'Báo động III',
  colorToken: 'alert-level-3',
  conditionType: 'GT',
  status: 'DANG_XAY_RA',
  startedAt: '2026-09-24T01:00:00Z',
  confirmedAt: '2026-09-24T01:10:00Z',
  endedAt: null,
  triggerValue: '4.200',
  peakValue: '4.500',
  peakAt: '2026-09-24T02:00:00Z',
  reason: 'Vượt ngưỡng',
  daXacNhan: true,
  dongBoiNguoi: false,
  note: null,
};

function lienKet(constructionId: string, code: string | null, name: string | null) {
  return {
    id: `lk-${constructionId}`,
    constructionId,
    constructionCode: code,
    constructionName: name,
    role: 'THUONG_LUU',
    primary: true,
  };
}

function tram(...ds: ReturnType<typeof lienKet>[]): Station {
  return {
    id: 'tram-0001',
    code: 'F01519',
    name: 'Trạm Lương Cổ',
    constructions: ds,
  } as unknown as Station;
}

const goi = vi.fn();
let tramTra: Station = tram();

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong.startsWith('/hyd/stations/')) return tramTra;
      return [];
    }),
    getPage: vi.fn(async () => ({
      items: [CANH_BAO],
      meta: { page: 1, size: 20, totalElements: 1, totalPages: 1 },
    })),
    post: vi.fn(async (duong: string, than: unknown) => {
      goi('POST', duong, than);
      return { id: 'bg-1' };
    }),
    put: vi.fn(async () => ({})),
  },
}));

const { AlertHistoryPage } = await import('./AlertHistoryPage');

const NHAN_NUT = 'Tạo bản ghi khắc phục';

function dung(quyen: string[] = ['hyd:alert:view', 'ops:maintenance:report-incident']) {
  const chuaKhai = () => {
    throw new Error('AuthContext giả: bài kiểm này ⛔ dựng phần đó');
  };
  const auth = {
    status: 'authenticated' as const,
    user: { id: 'toi', username: 'truc' },
    hasPermission: (q: string) => quyen.includes(q),
    hasRole: () => false,
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
          <MemoryRouter>
            <AlertHistoryPage />
          </MemoryRouter>
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

/**
 * Điền mọi ô BẮT BUỘC của biểu mẫu sửa chữa ở chế độ *Khắc phục sự cố*.
 *
 * ⚠ Ba ô, và ⛔ ô nào điền sẵn được: **Mức độ** là phán đoán của người đi hiện trường (⛔ suy từ
 * mức cảnh báo — *Báo động III* nói về mực nước, ⛔ nói về mức độ hư hỏng công trình), **Nội dung**
 * là mô tả, **Tên nhà thầu** là ai làm. Điền hộ bất kỳ ô nào trong ba là bịa dữ liệu nghiệp vụ.
 */
async function dienCacOBatBuoc(nguoiDung: ReturnType<typeof userEvent.setup>, noiDung: string) {
  await nguoiDung.click(screen.getByLabelText('Mức độ'));
  await nguoiDung.click(await screen.findByTitle('Cao'));
  await nguoiDung.type(screen.getByRole('textbox', { name: /nội dung/i }), noiDung);
  // ⚠⚠ Đi đường **nhà thầu ngoài**, ⛔ phải vì nó dễ hơn: chuỗi `'Đơn vị nội bộ'` là nhãn của CẢ
  //    một nút radio LẪN ô chọn cây bên dưới nó, nên `getByLabelText` ở đây là một phép chọn
  //    NHẬP NHẰNG — lượt bấm rơi vào nút radio và hộp thoại cây ⛔ bao giờ mở. Nhà thầu ngoài là
  //    một đường nghiệp vụ thật (phần lớn sửa chữa lớn do nhà thầu làm) và nó ⛔ có chỗ nhập nhằng.
  await nguoiDung.click(screen.getByRole('radio', { name: 'Nhà thầu ngoài' }));
  await nguoiDung.type(
    screen.getByRole('textbox', { name: /tên nhà thầu/i }),
    'Công ty XD Sông Nhuệ',
  );
}

beforeEach(() => {
  tramTra = tram(lienKet('ct-0001', 'CT-01', 'Cống Lương Cổ'));
});

afterEach(() => {
  cleanup();
  goi.mockClear();
});

describe('Cảnh báo → bản ghi khắc phục (T33.10)', () => {
  /**
   * ⭐⭐ **Bài chịu lực của cả dòng nợ.** Nó đi trọn vòng: bấm nút trên dòng cảnh báo → hộp thoại
   * → Lưu → và khẳng định thân `POST` mang **`alertEventId`**. Đó chính là trường mà trước bản vá
   * này ⛔ đường nào của người dùng đặt nổi.
   */
  /**
   * ⚠⚠ Người dùng ở bài này có **CẢ HAI** quyền tạo, và đó là vế phân biệt — ⛔ phải tiện tay.
   *
   * Lượt phá đầu (bỏ `loaiMacDinh`) **⛔ làm đỏ được gì** khi người dùng chỉ có
   * `ops:maintenance:report-incident`: danh sách loại khi ấy rút còn đúng một mục, nên
   * `loaiChoPhep[0]` **tình cờ** đã là *Khắc phục sự cố* và khẳng định `workType` xanh vì lý do
   * sai (luật 9). Có cả hai quyền thì mặc định của biểu mẫu là `SUA_CHUA` — mục ĐẦU của
   * `MAINTENANCE_TYPE` — và `loaiMacDinh` mới thật sự phải làm việc.
   */
  it('⛔⛔ đi trọn vòng ⇒ POST mang `alertEventId` và đúng công trình liên kết', async () => {
    const nguoiDung = userEvent.setup();
    dung(['hyd:alert:view', 'ops:maintenance:report-incident', 'ops:maintenance:create']);

    await nguoiDung.click(await screen.findByRole('button', { name: NHAN_NUT }));

    // Chỉ MỘT công trình liên kết ⇒ bỏ qua bước chọn, vào thẳng biểu mẫu.
    expect(await screen.findByText('Ghi nhận công việc sửa chữa')).toBeInTheDocument();

    // ⚠ Loại đã là *Khắc phục sự cố* nên ô **Mức độ** thành BẮT BUỘC — và nó ⛔ có giá trị điền
    //   sẵn, cố ý: mức độ sự cố là phán đoán của người đi hiện trường, ⛔ suy được từ mức cảnh báo.
    await dienCacOBatBuoc(nguoiDung, 'Khắc phục sự cố tràn bờ');
    await nguoiDung.click(screen.getByRole('button', { name: 'OK' }));

    await waitFor(() => expect(goi).toHaveBeenCalled());
    const [, duong, than] = goi.mock.calls[0] as [string, string, Record<string, unknown>];

    // ⛔ `/ops/maintenance-logs` trần: sự cố đi cửa riêng với quyền riêng (ma trận §6).
    expect(duong).toBe('/ops/maintenance-logs/incidents');
    expect(than.alertEventId, 'trường này là toàn bộ lý do T33.10 tồn tại').toBe(CANH_BAO.id);
    expect(than.constructionId).toBe('ct-0001');
    expect(than.workType).toBe('KHAC_PHUC_SU_CO');
  });

  it('⛔ ⛔ có quyền ghi nhận sự cố ⇒ ⛔ có nút — ⛔ bày một lựa chọn chắc chắn 403', async () => {
    dung(['hyd:alert:view']);

    // ⚠ Cột "Điểm đo" ghép `mã — tên` thành MỘT nút văn bản, nên phải khớp bằng mẫu: tiền đề của
    //   bài này là bảng ĐÃ dựng xong, nếu không thì "⛔ có nút" xanh vì bảng còn đang tải.
    expect(await screen.findByText(new RegExp(CANH_BAO.stationName))).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: NHAN_NUT })).toBeNull();
  });

  /**
   * ⛔⛔ Quy tắc 16 — *"số 0 là một câu khẳng định"*. Một danh sách RỖNG ở đây đọc như *"hệ thống
   * hỏng"*; thứ người trực cần biết là **việc cần làm nằm ở màn hình khác**.
   */
  it('⛔⛔ điểm đo chưa liên kết công trình ⇒ nói ra, ⛔ phải một danh sách rỗng', async () => {
    tramTra = tram();
    const nguoiDung = userEvent.setup();
    dung();

    await nguoiDung.click(await screen.findByRole('button', { name: NHAN_NUT }));

    expect(await screen.findByText(/chưa liên kết công trình nào/i)).toBeInTheDocument();
    expect(screen.queryByText('Ghi nhận công việc sửa chữa')).toBeNull();
  });

  /**
   * ⛔⛔ Một điểm đo thuộc **nhiều** công trình là chuyện bình thường — thượng lưu cống này là hạ
   * lưu cống kia. Đoán hộ là gắn sự cố vào sai hồ sơ, mà hồ sơ ấy dùng để quyết toán sửa chữa.
   */
  it('⛔⛔ hai công trình liên kết ⇒ phải CHỌN, ⛔ đoán hộ', async () => {
    tramTra = tram(
      lienKet('ct-0001', 'CT-01', 'Cống Lương Cổ'),
      lienKet('ct-0002', 'CT-02', 'Cống Vân Đình'),
    );
    const nguoiDung = userEvent.setup();
    dung();

    await nguoiDung.click(await screen.findByRole('button', { name: NHAN_NUT }));

    expect(await screen.findByText(/CT-02 — Cống Vân Đình/)).toBeInTheDocument();
    // Chưa chọn thì ⛔ biểu mẫu nào được mở.
    expect(screen.queryByText('Ghi nhận công việc sửa chữa')).toBeNull();

    await nguoiDung.click(screen.getAllByText('Chọn')[1]);

    expect(await screen.findByText('Ghi nhận công việc sửa chữa')).toBeInTheDocument();
    await dienCacOBatBuoc(nguoiDung, 'Khắc phục tại Vân Đình');
    await nguoiDung.click(screen.getByRole('button', { name: 'OK' }));

    await waitFor(() => expect(goi).toHaveBeenCalled());
    const [, , than] = goi.mock.calls[0] as [string, string, Record<string, unknown>];
    expect(than.constructionId, 'phải là công trình vừa CHỌN').toBe('ct-0002');
  });

  /**
   * ⚠ `StationConstructionView` tự khai: `constructionCode` về `null` khi công trình đã bị xoá mềm
   * sau lúc liên kết được khai, và *"giao diện phải nói ra điều đó, ⛔ giấu cả dòng đi: một liên
   * kết trỏ vào công trình đã xoá là thứ người vận hành cần thấy để dọn"*.
   */
  it('⚠ liên kết trỏ vào công trình ĐÃ XOÁ ⇒ hiện ra mà ⛔ chọn được', async () => {
    tramTra = tram(lienKet('ct-0009', null, null), lienKet('ct-0001', 'CT-01', 'Cống Lương Cổ'));
    const nguoiDung = userEvent.setup();
    dung();

    await nguoiDung.click(await screen.findByRole('button', { name: NHAN_NUT }));

    expect(await screen.findByText(/đã bị xoá/i)).toBeInTheDocument();
    // Một lựa chọn hợp lệ duy nhất, nhưng vì có dòng thứ hai nên KHÔNG tự đi thẳng — người trực
    // phải nhìn thấy dòng hỏng trước đã.
    expect(screen.getAllByText('Chọn')).toHaveLength(1);
  });
});
