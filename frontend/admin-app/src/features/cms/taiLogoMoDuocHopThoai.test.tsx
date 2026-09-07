import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp, Button, Upload } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { boChuThich } from '@/testsupport/boChuThich';

/**
 * **Nút "Tải logo" phải mở được hộp thoại chọn tệp.**
 *
 * <h2>Sự cố 01/09/2026</h2>
 *
 * QuanTran: *"Phần upload image cho mục Liên kết cổng TTĐT đang không hoạt động"*. Bấm vào thì
 * **không có gì xảy ra** — không hộp thoại, không lỗi, không dòng nào trong console.
 *
 * <p>Nguyên nhân nằm ở đúng một thuộc tính:
 *
 * ```tsx
 * <Upload beforeUpload={…}>
 *   <Button onClick={(event) => event.stopPropagation()}>Tải logo</Button>
 * </Upload>
 * ```
 *
 * `rc-upload` (lõi của `<Upload>`) **không** gắn trình xử lý lên nút. Nó gắn lên chính `<span>`
 * bọc `children`, và nhận cú bấm **nhờ sự kiện nổi bọt lên từ nút**. `stopPropagation` trên nút
 * chặn đúng cái nổi bọt ấy, nên `input[type=file].click()` không bao giờ được gọi.
 *
 * <p>`stopPropagation` vốn ở đó vì lý do chính đáng: nút nằm trong tiêu đề một nút cây
 * `<Tree>`, và không chặn thì bấm "Tải logo" cũng chọn/gập nút cây. Bản vá **giữ nguyên ý
 * định** ấy, chỉ đổi **chỗ đặt**: đưa `stopPropagation` ra lớp bọc *ngoài* `<Upload>`. Nổi bọt
 * khi đó đi qua rc-upload trước (hộp thoại mở), rồi mới bị chặn (cây không phản ứng). Thứ tự
 * nổi bọt làm được việc mà không cờ boolean nào làm được.
 *
 * <h2>⭐ Vì sao bài này dựng `<Upload>` THẬT</h2>
 *
 * Cơ chế bị hỏng là cơ chế **của rc-upload**, không phải của mã ta viết. Một bài kiểm chỉ soi
 * chuỗi `stopPropagation` trong tệp nguồn sẽ canh được *hình dạng hôm nay* mà không biết gì về
 * *lý do* — và sẽ xanh trọn vẹn nếu một bản AntD sau này đổi cách gắn sự kiện. Ở đây ta bắt
 * đúng thứ quan sát được: `input[type=file]` có được `click()` hay không.
 */

/** Bắt lời gọi `click()` trên MỌI `<input>` — đó là cách rc-upload mở hộp thoại chọn tệp. */
function rinhCuBamVaoONhapTep() {
  return vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {});
}

describe('Nút trong <Upload> — stopPropagation đặt sai chỗ là hỏng câm', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('⛔ BẢN HỎNG: stopPropagation trên nút BÊN TRONG <Upload> → hộp thoại KHÔNG mở', async () => {
    // Chép nguyên văn cấu trúc của `MenusTab.tsx` trước bản vá. Bài này phải ĐỎ nếu ai đó
    // "sửa" nó thành xanh — nó ghi lại hành vi sai, có chủ đích, để bài dưới có nghĩa.
    const bam = rinhCuBamVaoONhapTep();
    const nguoiDung = userEvent.setup();

    render(
      <AntdApp>
        <Upload showUploadList={false} beforeUpload={() => false}>
          <Button onClick={(event) => event.stopPropagation()}>Tải logo</Button>
        </Upload>
      </AntdApp>,
    );
    await nguoiDung.click(screen.getByRole('button', { name: 'Tải logo' }));

    expect(
      bam,
      'nếu lời gọi này XẢY RA thì AntD đã đổi cách gắn sự kiện — đọc lại cả bản vá',
    ).not.toHaveBeenCalled();
  });

  it('⭐ BẢN VÁ: stopPropagation ở lớp bọc NGOÀI → hộp thoại mở, và cây vẫn không nhận cú bấm', async () => {
    const bam = rinhCuBamVaoONhapTep();
    const cayNhanDuoc = vi.fn();
    const nguoiDung = userEvent.setup();

    render(
      <AntdApp>
        {/* `onClick` ở đây đóng vai cây `<Tree>` bọc ngoài trong `MenusTab`. */}
        <div onClick={cayNhanDuoc}>
          <span onClick={(event) => event.stopPropagation()}>
            <Upload showUploadList={false} beforeUpload={() => false}>
              <Button>Tải logo</Button>
            </Upload>
          </span>
        </div>
      </AntdApp>,
    );
    await nguoiDung.click(screen.getByRole('button', { name: 'Tải logo' }));

    // Hai khẳng định, và cần CẢ HAI: bản vá phải chữa được lỗi *mà không* làm mất lý do
    // `stopPropagation` từng tồn tại.
    expect(bam, 'hộp thoại chọn tệp phải mở').toHaveBeenCalled();
    expect(cayNhanDuoc, 'cây bọc ngoài vẫn không được nhận cú bấm').not.toHaveBeenCalled();
  });
});

describe('MenusTab dùng đúng cách đặt đã kiểm ở trên', () => {
  // ⚠ Hai bài trên chứng minh CƠ CHẾ, không chứng minh `MenusTab.tsx` dùng đúng cơ chế ấy —
  //   đúng khoảng trống §10.62 (`SvgSanitizer` có 9 bài kiểm mà không nằm trên đường chạy nào).
  // ⚠ Cắt chú thích TRƯỚC khi định vị. Lượt viết đầu không cắt, và `indexOf('<Upload')` bắt
  //   trúng chú thích của chính bản vá (nó nhắc tới `<Upload>`), nên khối cắt ra ôm luôn phần
  //   nằm trước thẻ thật — bài kiểm đỏ oan. Xem `testsupport/boChuThich.ts`.
  const ma = boChuThich(readFileSync(join(process.cwd(), 'src/features/cms/MenusTab.tsx'), 'utf8'));

  it('không còn `stopPropagation` nào trên nút nằm trong <Upload>', () => {
    const khoiUpload = ma.slice(ma.indexOf('<Upload'), ma.indexOf('</Upload>'));
    expect(
      khoiUpload.length,
      'không tìm thấy khối <Upload> — bài kiểm đang soi tập rỗng',
    ).toBeGreaterThan(100);
    expect(khoiUpload).not.toContain('stopPropagation');
  });

  it('lớp bọc ngoài <Upload> vẫn chặn nổi bọt lên cây', () => {
    const truocUpload = ma.slice(Math.max(0, ma.indexOf('<Upload') - 400), ma.indexOf('<Upload'));
    expect(truocUpload).toContain('stopPropagation');
  });
});
