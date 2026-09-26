import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import userEvent from '@testing-library/user-event';
import { cleanup, render, screen } from '@testing-library/react';
import { App as AntdApp } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type * as ApiClientModule from '@/shared/apiClient';

import { type LichDonViView } from './hrVocabulary';

/**
 * **Lịch nghỉ đơn vị — CN-04.9, T57.18 vế (b).**
 *
 * ## ⛔⛔ Bài chính ở đây đo một điều mà một bài "render ra chữ" ⛔ đo được
 *
 * Con số phần trăm trên mỗi ô **phải là số máy chủ gửi xuống** (quy tắc 3). Một giao diện tự chia
 * `soNguoiNghi / quanSo` sẽ cho ra **đúng cùng một con số** trong mọi dữ liệu "hợp lý" — nên một
 * bài dùng dữ liệu hợp lý sẽ xanh ở **cả hai** bản và ⛔ khẳng định gì (luật 9).
 *
 * ⇒ Dữ liệu của bài cố ý **mâu thuẫn**: `3/10` mà máy chủ khai `99%`. Chỉ một trong hai cách hiện
 * ra được con số ấy. Đây là cùng thủ pháp T48.7 (*cặp giá trị tự nhiên nhất lại là cặp ⛔ khẳng
 * định gì*), chỉ khác là ở đây nó áp cho một phép chia thay vì một đường lưới.
 *
 * ## ⚠ Vì sao mẫu số ⛔ được suy ở trình duyệt
 *
 * Mẫu số là *quân số còn làm việc*, suy từ `EmploymentStatus.daNghi()`: người nghỉ thai sản **vẫn**
 * là quân số, người đã nghỉ việc thì ⛔. Trình duyệt ⛔ có cách nào biết luật ấy, và ngưỡng thì nằm
 * trong `settings` sửa được lúc chạy.
 */

let lich: LichDonViView;
const goi = vi.fn();

vi.mock('@/shared/apiClient', async (importOriginal) => {
  const thuc = await importOriginal<typeof ApiClientModule>();
  return {
    ...thuc,
    api: {
      ...thuc.api,
      get: vi.fn(async (url: string, params?: Record<string, unknown>) => {
        goi(url, params);
        if (url === '/org-units/selectable') {
          return [
            {
              publicId: 'dv-a',
              code: 'XN-A',
              name: 'Xí nghiệp A',
              shortName: null,
              unitType: 'XI_NGHIEP',
              path: '/1/dv-a/',
              depth: 1,
              sortOrder: 0,
              active: true,
              address: null,
              phone: null,
              email: null,
              headUserPublicId: null,
              deputyUserPublicId: null,
              trongPhamVi: true,
              children: [],
            },
          ];
        }
        return lich;
      }),
    },
  };
});

const { LichNghiDonViPage } = await import('./LichNghiDonViPage');

function oNgay(ngay: string, soNguoiNghi: number, tyLePhanTram: number | null, vuot: boolean) {
  return { ngay, soNguoiNghi, tyLePhanTram, vuotNguong: vuot };
}

/** Một tháng đủ ô, chỉ khác nhau ở ngày 10 — mọi ngày khác để 0 cho khỏi nhiễu. */
function thang2026_09(o10: ReturnType<typeof oNgay>, quanSo: number): LichDonViView {
  const ngay = Array.from({ length: 30 }, (_, i) =>
    oNgay(`2026-09-${String(i + 1).padStart(2, '0')}`, 0, quanSo > 0 ? 0 : null, false),
  );
  ngay[9] = o10;
  return {
    donViPublicId: 'dv-a',
    tenDonVi: 'Xí nghiệp A',
    thang: '2026-09',
    quanSo,
    nguongPhanTram: 30,
    don: [],
    ngay,
  };
}

function dung() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <AntdApp>
        <LichNghiDonViPage />
      </AntdApp>
    </QueryClientProvider>,
  );
}

/** Mở ô chọn rồi bấm *Xí nghiệp A* — trang chỉ gọi API lịch SAU bước này. */
async function chonDonVi() {
  const nguoiDung = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  await nguoiDung.click(screen.getByRole('combobox'));
  await nguoiDung.click(await screen.findByText('Xí nghiệp A'));
}

beforeEach(() => {
  // ⛔⛔ Ghim đồng hồ vào một khoảnh khắc mà UTC và UTC+7 ở HAI THÁNG KHÁC NHAU: 20:00Z ngày
  //    31/08 là 03:00 ngày 01/09 giờ Việt Nam. Bộ kiểm chạy `TZ=UTC` (ghim từ T63.18), nên một
  //    bản dùng `dayjs()` trần sẽ gửi `thang=8` còn bản đúng gửi `thang=9`.
  // `shouldAdvanceTime` để React Query và hoạt ảnh antd vẫn tiến được — ghim đồng hồ ở đây là để
  // chốt NGÀY, ⛔ phải để đóng băng cả vòng đời component.
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date('2026-08-31T20:00:00Z'));
  lich = thang2026_09(oNgay('2026-09-10', 3, 99, true), 10);
});

afterEach(() => {
  vi.useRealTimers();
  cleanup();
  goi.mockClear();
});

describe('Lịch nghỉ đơn vị — T57.18(b)', () => {
  it('⛔ Chưa chọn đơn vị thì ⛔ gọi API lịch — nó là tham số BẮT BUỘC, gửi thiếu là 400', async () => {
    dung();
    expect(await screen.findByText('Chọn một đơn vị để xem lịch nghỉ của tháng')).toBeTruthy();
    expect(goi.mock.calls.map((c) => c[0])).not.toContain('/hr/nghi-phep/lich');
  });

  it('⭐⭐ Tỉ lệ là SỐ CỦA MÁY CHỦ — dữ liệu 3/10 mà máy chủ khai 99%', async () => {
    dung();
    await chonDonVi();

    expect(
      await screen.findByText('3 người · 99%'),
      'Một giao diện tự chia 3/10 sẽ hiện 30%. Hai cách hiểu, hai con số — và chỉ một cái ' +
        'đi qua được luật quân số (thai sản vẫn là quân số) lẫn ngưỡng trong `settings`.',
    ).toBeTruthy();
    expect(screen.queryByText('3 người · 30%')).toBeNull();
  });

  it('⭐⭐ Tháng gửi lên là tháng theo UTC+7, ⛔ theo giờ máy — T63.18 ở cỡ THÁNG', async () => {
    dung();
    await chonDonVi();
    // ⚠ Mỏ neo chờ-tải phải ĐỘC LẬP với con số bài này đang hỏi: neo vào `3 người · 99%` thì một
    //   bản phá ở phép hiện tỉ lệ làm bài NÀY đỏ theo, và thông điệp đỏ nói sai nguyên nhân
    //   (§11.19 — đỏ đúng lúc mà SAI CHỖ vẫn dẫn người đọc đi lạc).
    await screen.findByText(/quân số 10 người/);

    const lanGoi = goi.mock.calls.find((c) => c[0] === '/hr/nghi-phep/lich');
    expect(lanGoi, 'phải có đúng một lượt gọi lịch để mà hỏi tham số của nó').toBeTruthy();
    expect(lanGoi?.[1]).toMatchObject({ donVi: 'dv-a', nam: 2026, thang: 9 });
  });

  it('⚠ `tyLePhanTram === null` là trạng thái THỨ BA, ⛔ phải 0%', async () => {
    // Đơn vị ⛔ có quân số ⇒ ⛔ có mẫu số để chia. Vẽ `0%` ở đây là nói *⛔ ai nghỉ* trong khi
    // thực tế là *⛔ tính được* — và người đọc ⛔ có cách nào phân biệt.
    lich = thang2026_09(oNgay('2026-09-10', 1, null, false), 0);
    dung();
    await chonDonVi();

    expect(await screen.findByText('1 người')).toBeTruthy();
    expect(screen.queryByText('1 người · 0%')).toBeNull();
    expect(
      screen.getByText(/chưa có CBNV nào đang làm việc/),
      'Màn hình phải NÓI RA vì sao cột tỉ lệ trống, thay vì để một ô rỗng tự giải thích',
    ).toBeTruthy();
  });

  it('⚠ Vế chống tập rỗng: ngày ⛔ ai nghỉ thì ⛔ vẽ huy hiệu nào', async () => {
    // Thiếu vế này thì ba bài trên vẫn xanh khi ô nào cũng vẽ một huy hiệu — lúc ấy *"có huy
    // hiệu"* ⛔ còn phân biệt được ngày có người nghỉ với ngày ⛔ có (luật 9).
    dung();
    await chonDonVi();
    await screen.findByText(/quân số 10 người/);

    expect(screen.queryByText('0 người · 0%')).toBeNull();
    expect(screen.queryAllByText(/người ·/)).toHaveLength(1);
  });
});
