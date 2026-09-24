import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';
import { type SettingView, type Station } from '@/shared/api-types';
import type * as ApiClientModule from '@/shared/apiClient';

/**
 * ⛔⛔ **Danh sách điểm đo lên cổng phải CHỌN được, ⛔ phải gõ tay chuỗi mã** — T28.48.
 *
 * <h2>Khuyết tật, đo 24/09/2026</h2>
 *
 * `hydro.portal.station-codes` là khoá `STRING`, `is_editable = TRUE`, nên cặp đọc–ghi của nó
 * **hoàn chỉnh** — đây ⛔ phải nửa cặp như dòng nợ từng xếp. Ô ghi là `<Input>` **chữ tự do** của
 * {@link SettingsPage}, trong khi giá trị là danh sách **mã điểm đo** ngăn bằng dấu phẩy.
 *
 * Hệ quả gõ nhầm một mã: `PublicHydroService.mucNuoc()` lọc mã ấy ra, bảng mực nước trên cổng
 * **ngắn đi một dòng**, và lý do duy nhất là một dòng `log.warn` trong nhật ký hệ thống — nơi
 * ⛔ ai đọc. Chính javadoc ở đó đã gọi tên: *"9 dòng SỐ THẬT trông y hệt một bảng đúng — ⛔ có ô
 * rỗng nào, ⛔ có dấu gạch nào, ⛔ có gì để người đọc nghi ngờ"*.
 *
 * <h2>⚠⚠ Điều kiện *"nâng khi OI-03 về"* trong dòng nợ là SAI</h2>
 *
 * OI-03 quyết định Công ty **công bố mã NÀO** — tức **dữ liệu**. Nó ⛔ quyết định **hình dạng ô
 * nhập**. Việc này ⛔ bị chặn bởi ai, và nó đã nằm chờ một điều kiện ⛔ bao giờ liên quan.
 *
 * <h2>⭐ Hằng khoá ĐO từ backend, ⛔ gõ tay</h2>
 *
 * Tiền lệ `ONhomNhanCanhBao` tự khai lỗ của nó: *"Hai nơi phải nhớ cùng một chuỗi (luật 14). Gõ sai
 * ở đây ⛔ làm gì đỏ — ô chọn chỉ lặng lẽ ⛔ hiện ra"*. Ở đây mọi bài bên dưới dùng chuỗi **đọc từ
 * `HydroSettings.java`**, nên hằng phía FE trôi một ký tự là cả năm bài đỏ ngay — ⛔ phải một bài
 * canh-văn-bản riêng, và ⛔ có cách nào im nó bằng cách sửa chú thích.
 *
 * ⚠ `CiPathFilterTest` **ĐO** mọi hằng chuỗi `'backend/…'` trong mã kiểm FE rồi đối chiếu với bộ
 * lọc `frontend` của `ci.yml` — nên đường dẫn dưới đây tự nó kéo job FE chạy khi tệp Java ấy đổi.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');

const NGUON_HYDRO_SETTINGS = readFileSync(
  join(GOC_KHO, 'backend/hydro/src/main/java/com/songnhue/hydro/application/HydroSettings.java'),
  'utf8',
);

/** `public static final String KHOA_DIEM_DO_LEN_CONG = "hydro.portal.station-codes";` */
const KHOA = (() => {
  const khop = /KHOA_DIEM_DO_LEN_CONG\s*=\s*"([^"]+)"/.exec(NGUON_HYDRO_SETTINGS);
  // Tiền đề (luật 7): ⛔ bóc được thì mọi bài dưới chạy trên một khoá BỊA và đều xanh vô nghĩa.
  if (!khop) {
    throw new Error('⛔ đọc được KHOA_DIEM_DO_LEN_CONG trong HydroSettings.java');
  }
  return khop[1];
})();

const goi = vi.fn();

const DIEM_DO: Station[] = [
  diemDo('F01519', 'Cống Lương Cổ', true),
  diemDo('F01520', 'Cống Vân Đình', true),
  diemDo('F01521', 'Cống Nhật Tựu', true),
  diemDo('F01599', 'Trạm đã ngừng dùng', false),
];

function diemDo(code: string, name: string, active: boolean): Station {
  return {
    id: `id-${code}`,
    code,
    name,
    apiCode: code,
    apiSourceId: null,
    apiSourceCode: null,
    positionRole: 'THUONG_LUU',
    orgUnitId: null,
    orgUnitName: null,
    riverName: null,
    chainage: null,
    chainageM: null,
    latitude: null,
    longitude: null,
    interpolated: false,
    active,
    description: null,
    measurementTypes: [],
    constructions: [],
  } as unknown as Station;
}

function thamSo(key: string, value: string, label: string): SettingView {
  return {
    key,
    value,
    effectiveValue: value,
    valueType: 'STRING',
    defaultValue: '',
    groupCode: 'HYDRO',
    label,
    description: null,
    validation: null,
    editable: true,
    exportable: true,
    canXacThucLai: false,
  };
}

const NHAN = 'Điểm đo lên cổng';

let danhSachThamSo: SettingView[] = [];

vi.mock('@/shared/apiClient', async (importOriginal) => {
  const thuc = await importOriginal<typeof ApiClientModule>();
  return {
    ...thuc,
    api: {
      ...thuc.api,
      get: vi.fn(async (url: string) =>
        url === '/settings' ? danhSachThamSo : url === '/hyd/stations' ? DIEM_DO : [],
      ),
      put: vi.fn(async (url: string, than: unknown) => {
        goi('PUT', url, than);
        return {};
      }),
      post: vi.fn(async () => ({})),
    },
  };
});

const { SettingsPage } = await import('./SettingsPage');

function dung() {
  const khongDung = () => {
    throw new Error('AuthContext giả');
  };
  const auth = {
    status: 'authenticated' as const,
    user: { id: 'toi', username: 'sieuquantri' },
    hasPermission: (q: string) =>
      ['adm:setting:view', 'adm:setting:update', 'hyd:station:view'].includes(q),
    hasRole: () => false,
    maintenance: false,
    login: khongDung,
    verifyTwoFactor: khongDung,
    confirmEnrollment: khongDung,
    logout: khongDung,
    endSession: khongDung,
    reloadProfile: khongDung,
  } as unknown as AuthContextValue;
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>
          <SettingsPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  goi.mockClear();
});

describe('Điểm đo lên cổng chọn được từ danh mục (T28.48)', () => {
  it('⛔⛔ giá trị đã lưu hiện ra thành TÊN ĐIỂM ĐO — ⛔ phải một chuỗi mã thô', async () => {
    danhSachThamSo = [thamSo(KHOA, 'F01519,F01520', NHAN)];
    dung();

    expect(await screen.findByText(/Cống Lương Cổ/)).toBeTruthy();
    expect(screen.getByText(/Cống Vân Đình/)).toBeTruthy();

    // ⚠ Vế phân biệt "đã vá" với "vẫn là ô chữ": ô cũ hiện nguyên văn chuỗi ngăn phẩy.
    expect(screen.queryByDisplayValue('F01519,F01520')).toBeNull();
  });

  /**
   * ⭐⭐ **Thứ tự CHỌN là thứ tự hiển thị trên cổng** — ⛔ phải chi tiết thẩm mỹ.
   *
   * `HydroSettings.maDiemDoLenCong()` cố ý dùng `LinkedHashSet` và nói rõ vì sao: *"thứ tự gõ LÀ
   * thứ tự hiển thị trên cổng … một tập ⛔ thứ tự làm lời hứa ấy sai một cách ⛔ nhìn thấy được —
   * bảng vẫn đủ dòng, chỉ xếp sai"*. Và `PublicHydroService` đã lường trước đúng ô chọn này:
   * *"một danh sách 'chọn được nhưng ⛔ xếp được' thì lần đầu Công ty dùng đã phải mở lại mã"*.
   *
   * ⇒ Bài này chọn **F01521 trước, F01519 sau** rồi đòi đúng thứ tự ấy trong thân `PUT`. Một bản
   * vá sắp lại theo danh mục (thứ tự rất "tự nhiên" khi dựng `options`) sẽ đỏ ở đây.
   */
  it('⛔⛔ chọn hai mã rồi Lưu ⇒ PUT chuỗi ngăn phẩy theo ĐÚNG thứ tự đã chọn', async () => {
    danhSachThamSo = [thamSo(KHOA, '', NHAN)];
    const nguoiDung = userEvent.setup();
    dung();

    const o = await screen.findByLabelText(NHAN);
    await nguoiDung.click(o);
    await nguoiDung.click(await screen.findByTitle('F01521 — Cống Nhật Tựu'));
    await nguoiDung.click(await screen.findByTitle('F01519 — Cống Lương Cổ'));
    await nguoiDung.click(screen.getByRole('button', { name: 'Lưu' }));

    await waitFor(() =>
      expect(goi).toHaveBeenCalledWith('PUT', `/settings/${KHOA}`, {
        value: 'F01521,F01519',
      }),
    );
  });

  /**
   * ⛔⛔ Ba trạng thái, ba câu trả lời khác nhau (T59.0) — ⛔ gộp thành *"⛔ có trong danh sách"*:
   *
   * <ul>
   *   <li>mã **⛔ khớp điểm đo nào** ⇒ gõ nhầm, sửa hoặc bỏ;
   *   <li>mã của một trạm **đã NGỪNG** ⇒ mã đúng, trạm đã thôi dùng — `PublicHydroService` loại
   *       `NGUNG` <b>trước</b> bộ lọc này nên nó ⛔ bao giờ lên cổng dù có trong danh sách;
   *   <li>mã bình thường.
   * </ul>
   *
   * ⛔⛔ Và cả hai loại trên **PHẢI còn nhìn thấy được**: nếu ô chọn lặng lẽ bỏ chúng thì lượt Lưu
   * kế tiếp **ghi đè mất** cấu hình của Công ty mà ⛔ ai bấm nút xoá (luật 9 — đúng cái bẫy mà
   * `docMangPublicId` của tiền lệ dựng ba trạng thái để tránh).
   */
  it('⛔⛔ mã lạ và mã của trạm đã NGỪNG vẫn HIỆN RA, kèm lý do', async () => {
    danhSachThamSo = [thamSo(KHOA, 'F01519,F09999,F01599', NHAN)];
    dung();

    expect(await screen.findByText(/Cống Lương Cổ/)).toBeTruthy();
    expect(screen.getByText(/F09999/)).toBeTruthy();
    expect(screen.getByText(/⛔ khớp điểm đo nào/)).toBeTruthy();
    expect(screen.getByText(/F01599/)).toBeTruthy();
    expect(screen.getByText(/đã ngừng/i)).toBeTruthy();
  });

  it('⚠ trạm đã NGỪNG ⛔ bày ra để chọn mới — máy chủ loại nó TRƯỚC bộ lọc này', async () => {
    danhSachThamSo = [thamSo(KHOA, '', NHAN)];
    const nguoiDung = userEvent.setup();
    dung();

    await nguoiDung.click(await screen.findByLabelText(NHAN));

    expect(await screen.findByTitle('F01519 — Cống Lương Cổ')).toBeTruthy();
    expect(screen.queryByTitle('F01599 — Trạm đã ngừng dùng')).toBeNull();
  });

  /**
   * ⛔⛔ **Rỗng nghĩa là CÔNG BỐ TẤT CẢ, ⛔ phải ⛔ công bố gì.**
   *
   * Hai trạng thái ấy trông y hệt nhau trên một ô chọn trống, và đoán sai chiều nào cũng hỏng:
   * người quản trị xoá hết tag để "tạm ẩn bảng" sẽ **công bố toàn bộ 19 điểm đo**. Quy ước do
   * `V202609041064` + `maDiemDoLenCong()` chốt, nên chỗ duy nhất nói được là ngay cạnh ô.
   */
  it('⛔⛔ nói rõ ô RỖNG nghĩa là công bố TẤT CẢ, và nói rõ THỨ TỰ có nghĩa', async () => {
    danhSachThamSo = [thamSo(KHOA, '', NHAN)];
    dung();

    // Hai chỗ, hai khoảnh khắc — ⛔ phải một lượt chép thừa:
    //   · placeholder chỉ hiện khi ô RỖNG, tức ngay sau khi người quản trị xoá hết tag;
    //   · dòng chú thích luôn hiện, cho người ĐANG CÓ tag và sắp xoá.
    expect(await screen.findByText('Để trống = công bố tất cả')).toBeTruthy();
    expect(screen.getByText(/ô rỗng nghĩa là công bố tất cả/i)).toBeTruthy();

    // Lời hứa thứ hai của khoá, và là lý do máy chủ dùng `LinkedHashSet` — ⛔ nói ra thì ⛔ ai đoán
    // được rằng bấm theo thứ tự nào cũng đổi bảng trên cổng.
    expect(screen.getByText(/thứ tự chọn là thứ tự hiển thị trên cổng/i)).toBeTruthy();
  });

  /**
   * ⚠ VẾ PHÂN BIỆT (luật 28 · luật 9) — ⛔ có nó thì một bản vá đổi **mọi** khoá `STRING` thành ô
   * chọn điểm đo cũng làm năm bài trên xanh, và nó phá mọi tham số chữ còn lại của hệ.
   */
  it('⚠ khoá STRING KHÁC cùng nhóm HYDRO vẫn là ô chữ tự do', async () => {
    danhSachThamSo = [thamSo('hydro.mot.khoa.chu.khac', 'F01519,F01520', 'Khoá chữ khác')];
    dung();

    expect(await screen.findByDisplayValue('F01519,F01520')).toBeTruthy();
    expect(screen.queryByLabelText('Khoá chữ khác')).toBeNull();
  });
});
