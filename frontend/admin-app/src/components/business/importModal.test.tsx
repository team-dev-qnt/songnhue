import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import userEvent from '@testing-library/user-event';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const upload = vi.fn((..._args: unknown[]) => Promise.resolve({} as unknown));
const getTep = vi.fn((..._args: unknown[]) =>
  Promise.resolve({ blob: new Blob(['x']), tenTep: 'mau.csv' }),
);

class GiaApiClientError extends Error {}

vi.mock('@/shared/apiClient', () => ({
  api: {
    upload: (...args: unknown[]) => upload(...args),
    getTep: (...args: unknown[]) => getTep(...args),
  },
  ApiClientError: GiaApiClientError,
}));

const { ImportModal, TRAN_DONG_NHAP } = await import('./ImportModal');

/**
 * **Bài kiểm cho hộp thoại nhập DÙNG CHUNG** — G8, và cho mọi màn hình nhập về sau.
 *
 * ## Vì sao cái xanh hiện tại chưa nói gì
 *
 * `ConstructionImportTest` (backend) có 10 bài và ⛔ không bài nào chạm tới màn hình:
 * nó gọi thẳng `ConstructionImportService`. Ba khuyết tật đo được ngày 09/09/2026 nằm **trọn vẹn ở
 * phía giao diện**, nên cả 10 bài ấy về nguyên tắc ⛔ không thể thấy:
 *
 * 1. `accept=".xlsx,.xls"` — chặn đúng định dạng **CSV** mà bộ đọc xử lý đầy đủ, và mời đúng định
 *    dạng **`.xls`** (OLE2) mà bộ đọc ⛔ không đọc nổi.
 * 2. Chữ *"đúng biểu mẫu"* trong khi kho ⛔ không có một tệp mẫu nào.
 * 3. Bảng lỗi từng dòng ⛔ không bao giờ được vẽ ở đường **nhập thật** — backend NÉM `OPS-2016`
 *    nên lượt ấy rơi vào `onError`, và `onError` chỉ có một dòng toast.
 *
 * ## ⚠ Bài canh trần dòng đọc THẲNG tệp Java
 *
 * Câu gợi ý *"tối đa 5.000 dòng"* là bản sao thứ hai của `SpreadsheetReader.MAX_ROWS`. Luật 14 của
 * dự án: chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ. Có tiền lệ trong
 * kho — `error-map.test.ts` và `alertLevelColors.test.ts` cùng đọc tệp backend theo cách này.
 */

/**
 * ⚠⚠ Đường dẫn phải viết từ **gốc kho** và bắt đầu bằng `backend/`, ⛔ không phải một đường tương
 * đối kiểu `../../../backend/…`.
 *
 * <p>`CiPathFilterTest.DUONG_DAN_BACKEND` là `['"](backend/[^'"]*)['"]` — nó quét mã nguồn test của
 * FE để biết bộ lọc đường dẫn của CI phải bao những tệp nào. Một đường tương đối ⛔ không khớp, nên
 * bộ canh ấy sẽ **⛔ không nhìn thấy** phụ thuộc này. Đúng hình dạng T11.71 và §11.13: một bộ canh
 * ⛔ không thấy đường dẫn thì ⛔ không thể báo bộ lọc CI đang bỏ sót.
 */
const DUONG_READER =
  'backend/core/src/main/java/com/songnhue/core/common/importer/SpreadsheetReader.java';

/**
 * ⚠ ⛔ Không dùng `import.meta.url`: Vitest chạy jsdom nên Vite đổi nó thành URL `http://`, và
 * `readFileSync` từ chối bằng một lỗi ⛔ không liên quan gì tới thứ đang kiểm. Đi ngược từ
 * `process.cwd()` thì đúng dù lệnh chạy từ `frontend/` hay từ `frontend/admin-app/` — cùng khuôn
 * với `shared/error-map.test.ts`.
 */
function docReader(): string {
  let current = process.cwd();
  for (let depth = 0; depth < 6; depth += 1) {
    const candidate = join(current, DUONG_READER);
    if (existsSync(candidate)) {
      return readFileSync(candidate, 'utf8');
    }
    const parent = dirname(current);
    if (parent === current) {
      break;
    }
    current = parent;
  }
  throw new Error(`Không tìm thấy ${DUONG_READER} tính từ ${process.cwd()}`);
}

/**
 * ⚠ Bọc `<App>` của AntD là BẮT BUỘC, ⛔ không phải trang trí.
 *
 * `App.useApp()` ngoài `<App>` trả về một đối tượng rỗng, nên `message.error(...)` ném
 * `TypeError: message.error is not a function` — và nó ném **bên trong `onError` của mutation**,
 * tức là một unhandled rejection ở nơi khác hẳn chỗ đọc kết quả. Bài kiểm hỏng ở đúng nhánh xử lý
 * lỗi mà nó sinh ra để canh, với một thông điệp ⛔ không liên quan gì tới khuyết tật.
 */
function dung() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <App>
        <ImportModal
          open
          onClose={() => {}}
          title="Nhập kiểm thử"
          moTa="Mô tả kiểm thử."
          duongDan={{
            xemTruoc: '/ops/constructions/import/preview',
            nhap: '/ops/constructions/import',
            mau: '/ops/constructions/import/template',
          }}
          tenTepMau="mau.csv"
          khoaCanLamMoi={['ops', 'constructions']}
        />
      </App>
    </QueryClientProvider>,
  );
  return qc;
}

/** `Upload.Dragger` của AntD dựng một `<input type="file">` ẩn — đó là chỗ `accept` thật sự nằm. */
function oChonTep(): HTMLInputElement {
  const input = document.querySelector('input[type="file"]');
  if (!input) {
    throw new Error('không tìm thấy ô chọn tệp — hộp thoại đã đổi cấu trúc');
  }
  return input as HTMLInputElement;
}

describe('ImportModal', () => {
  beforeEach(() => {
    upload.mockReset();
    upload.mockResolvedValue({
      applied: false,
      totalRows: 0,
      toCreate: 0,
      toUpdate: 0,
      errors: [],
    });
    getTep.mockReset();
    getTep.mockResolvedValue({ blob: new Blob(['x']), tenTep: 'mau.csv' });
  });

  afterEach(cleanup);

  it('⭐⭐ nhận .csv và KHÔNG nhận .xls — bộ đọc nhận diện XLSX bằng chữ ký ZIP', () => {
    dung();

    const accept = oChonTep().getAttribute('accept') ?? '';

    expect(accept).toContain('.csv');
    // ⛔ `.xls` là OLE2, ⛔ không phải ZIP ⇒ `SpreadsheetReader` đẩy nó xuống nhánh CSV và người
    //    dùng nhận câu "tệp thiếu cột bắt buộc" cho một tệp hoàn toàn đúng.
    expect(accept.split(',').map((s) => s.trim())).not.toContain('.xls');
    expect(accept).toContain('.xlsx');
  });

  it('⭐ có nút tải tệp mẫu, và nó gọi ĐÚNG endpoint của backend', async () => {
    dung();

    await userEvent.click(screen.getByRole('button', { name: /tải tệp mẫu/i }));

    await waitFor(() => expect(getTep).toHaveBeenCalledTimes(1));
    expect(getTep).toHaveBeenCalledWith('/ops/constructions/import/template');
  });

  it('⛔ hộp thoại KHÔNG còn hứa "đúng biểu mẫu" mà không đưa được biểu mẫu', () => {
    dung();

    // Nút tải mẫu là thứ biến lời hứa thành một hành động — có nó thì câu chữ mới có nghĩa.
    expect(screen.getByRole('button', { name: /tải tệp mẫu/i })).toBeTruthy();
  });

  /**
   * ⭐⭐ Bài chịu lực của tệp này.
   *
   * <p>Lượt nhập thật lập LẠI kế hoạch (cố ý — giữa hai lượt có thể có người vừa thêm một mã trùng),
   * nên xem trước sạch mà nhập vẫn đỏ được. Trước 09/09 người dùng chỉ nhận một dòng toast cho tình
   * huống ấy: bảng lỗi ngay bên dưới ⛔ không bao giờ vẽ, vì `errors` chỉ về theo nhánh `onSuccess`
   * mà backend thì **ném**.
   *
   * <p>Phép đo phân biệt được hai trạng thái (luật 9): bản cũ gọi `upload` đúng **2 lần** (xem
   * trước + nhập) rồi dừng; bản mới gọi **3 lần** — lượt thứ ba là xem trước chạy lại để lấy danh
   * sách dòng lỗi. Và bảng lỗi phải thật sự hiện ra.
   */
  it('⭐⭐ nhập thất bại ⇒ chạy lại xem trước và VẼ ĐƯỢC bảng lỗi từng dòng', async () => {
    dung();

    // 1. Xem trước: sạch ⇒ nút "Nhập dữ liệu" mở khoá.
    upload.mockResolvedValueOnce({
      applied: false,
      totalRows: 2,
      toCreate: 2,
      toUpdate: 0,
      errors: [],
    });

    await userEvent.upload(
      oChonTep(),
      new File(['ma_cong_trinh\nA-1\n'], 'ok.csv', { type: 'text/csv' }),
    );
    await waitFor(() => expect(upload).toHaveBeenCalledTimes(1));
    const nutNhap = await screen.findByRole('button', { name: /nhập dữ liệu/i });
    await waitFor(() => expect(nutNhap.hasAttribute('disabled')).toBe(false));

    // 2. Nhập thật: backend NÉM OPS-2016 …
    upload.mockRejectedValueOnce(new GiaApiClientError('Tệp nhập còn dòng lỗi'));
    // 3. … và lượt xem trước chạy lại phải trả về dòng lỗi cụ thể.
    upload.mockResolvedValueOnce({
      applied: false,
      totalRows: 2,
      toCreate: 1,
      toUpdate: 0,
      errors: [{ rowNumber: 7, column: 'ma_cong_trinh', message: "Mã 'A-1' đã tồn tại" }],
    });

    await userEvent.click(nutNhap);

    await waitFor(() => expect(upload).toHaveBeenCalledTimes(3));
    expect(upload.mock.calls[2][0]).toBe('/ops/constructions/import/preview');

    // ⭐ Khẳng định trên MÀN HÌNH, ⛔ không chỉ trên số lượt gọi: một lượt gọi thêm mà bảng vẫn
    //   trống thì người dùng vẫn ⛔ không biết dòng nào hỏng.
    expect(await screen.findByText(/Mã 'A-1' đã tồn tại/)).toBeTruthy();
    expect(screen.getByText('7')).toBeTruthy();
  });

  it('⚠⚠ TRAN_DONG_NHAP phải khớp SpreadsheetReader.MAX_ROWS của backend — luật 14', () => {
    const nguon = docReader();
    const khop = nguon.match(/MAX_ROWS\s*=\s*(\d+)/);

    expect(khop, 'không đọc được MAX_ROWS — bài kiểm đang so với một chuỗi rỗng').toBeTruthy();

    const tran = Number(khop![1]);
    expect(tran).toBeGreaterThan(0);

    // ⭐ So với HẰNG SỐ, ⛔ không so với chuỗi đã render: hằng số là thứ mọi nơi gọi dùng lại, còn
    //   chuỗi render chỉ chứng minh đúng một hộp thoại. Nếu ai đó thêm một màn hình nhập mới và
    //   chép tay con số vào đó, phép so này vẫn phải là chỗ duy nhất cần đúng.
    expect(
      TRAN_DONG_NHAP,
      `backend ép trần ${tran} dòng nhưng giao diện đang nói ${TRAN_DONG_NHAP}`,
    ).toBe(tran);

    dung();
    expect(document.body.textContent).toContain(tran.toLocaleString('vi-VN'));
  });
});
