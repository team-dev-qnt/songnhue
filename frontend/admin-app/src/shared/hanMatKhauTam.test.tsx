import { App as AntdApp } from 'antd';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { boChuThich } from '@/testsupport/boChuThich';

import { HanMatKhauTam } from './HuongDanMatKhau';

/**
 * **T73.8 (ASVS 2.3.1) — người phát mật khẩu tạm phải biết nó hết hạn lúc nào.**
 *
 * Mật khẩu tạm nay có hạn (`security.password.temp-ttl-hours`); quá hạn thì đăng nhập nhận
 * `AUTH-0010`. Một hộp thoại phát mật khẩu tạm ⛔ nói ra con số ấy là để người dùng nhận một
 * câu lỗi vào ngày thứ tư mà ⛔ ai dặn trước.
 */
function veVoi(chinhSach: unknown) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  // Đệm nạp sẵn + `staleTime` 30 phút ⇒ ⛔ lượt gọi mạng nào; thứ được kiểm là cách VẼ con số.
  qc.setQueryData(['auth', 'password-policy'], chinhSach);
  return render(
    <QueryClientProvider client={qc}>
      <AntdApp>
        <HanMatKhauTam />
      </AntdApp>
    </QueryClientProvider>,
  );
}

describe('HanMatKhauTam — T73.8', () => {
  afterEach(cleanup);

  it('nói ĐÚNG số giờ backend trả — giá trị thử ≠ mặc định 72 (T48.7)', () => {
    const { container } = veVoi({
      minLength: 10,
      requireLetterAndDigit: true,
      tempPasswordTtlHours: 5,
    });
    expect(container.textContent).toContain('hết hiệu lực sau 5 giờ');
  });

  it('⛔ chưa có số (hợp đồng thiếu trường) ⇒ ⛔ vẽ gì — quy tắc 16', () => {
    const { container } = veVoi({ minLength: 10, requireLetterAndDigit: true });
    expect(container.textContent).toBe('');
  });

  it('⭐ cả HAI hộp thoại phát mật khẩu tạm đều nói hạn, và component ⛔ ghi cứng số giờ', () => {
    const trang = boChuThich(
      readFileSync(join(process.cwd(), 'src/features/admin/UsersPage.tsx'), 'utf8'),
    );
    expect(trang.match(/<HanMatKhauTam \/>/g)?.length, 'thêm tài khoản + đặt lại mật khẩu').toBe(2);

    const nguon = boChuThich(
      readFileSync(join(process.cwd(), 'src/shared/HuongDanMatKhau.tsx'), 'utf8'),
    );
    const than = nguon.slice(nguon.indexOf('export function HanMatKhauTam'));
    expect(than).not.toMatch(/\d+ giờ/);
    expect(than).toContain('chinhSach.tempPasswordTtlHours');
  });
});
