import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { type ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

import { HuongDanPage } from './HuongDanPage';

/**
 * **Trang Hướng dẫn dựng THẬT** — ⛔ chỉ kiểm bộ đọc markdown.
 *
 * <h2>⛔⛔ Vì sao `phamViMarkdown.test.ts` ⛔ ĐỦ</h2>
 *
 * Bài kia khẳng định *tài liệu phân tích ra đúng mô hình*. Nó ⛔ nói gì về việc mô hình ấy có
 * **thành phần tử trên màn hình** ⛔ — đúng nửa còn lại của luật 27. Một `switch` thiếu nhánh,
 * một `dungInline` quên đệ quy, một bộ lọc ẩn nhầm: cả ba để bài kia xanh trọn vẹn trong khi
 * người dùng mở ra thấy trang trắng.
 *
 * ⭐ Và lượt đệ quy là ca đã xảy ra thật: mô hình inline bản đầu phẳng, bộ canh đỏ, sửa xong thì
 * `HuongDanPage` **⛔ tự biết** — chỉ `tsc` bắt. Bài này là vế thứ ba.
 */

afterEach(cleanup);

/** Quyền của mọi màn hình *Quản trị hệ thống* — dùng làm vế "người có quyền". */
const QUYEN_QUAN_TRI = [
  'adm:user:view',
  'adm:role:view',
  'adm:org-unit:view',
  'adm:setting:view',
  'adm:system-config:view',
  'adm:audit:view',
  'adm:backup:view',
  'adm:health:view',
  'adm:notification:broadcast',
];

/**
 * Bộ quyền rộng dùng cho nhóm bài **tìm kiếm**.
 *
 * ⚠⚠ ⛔ dùng {@link QUYEN_QUAN_TRI} ở đó: nó chỉ có `adm:*`, nên §6.6 *Ngưỡng và cảnh báo* bị
 * **bộ lọc vai trò** thu gọn và bài tìm kiếm đỏ vì một lý do ⛔ liên quan gì tới tìm kiếm. Đã mắc
 * đúng lỗi ấy 20/09 — một bài kiểm đỏ vì lý do sai dẫn người đọc đi sửa nhầm chỗ.
 */
const QUYEN_RONG = [
  ...QUYEN_QUAN_TRI,
  'hyd:threshold:view',
  'hyd:measurement:view',
  'hyd:alert:view',
  'hyd:report:view',
  'hyd:station:view',
  'hr:leave:request',
  'cms:article:view',
  'ops:construction:view',
];

function dung(quyen: string[] = [], vaiTro: string[] = ['VIEWER'], neo = ''): void {
  const khongDung = () => {
    throw new Error('AuthContext giả');
  };
  const auth = {
    status: 'authenticated' as const,
    user: { id: 'toi', username: 'canbo', fullName: 'Cán bộ', roles: vaiTro, permissions: quyen },
    hasPermission: (q: string) => quyen.includes(q),
    hasRole: (r: string) => vaiTro.includes(r),
    maintenance: false,
    login: khongDung,
    verifyTwoFactor: khongDung,
    confirmEnrollment: khongDung,
    logout: khongDung,
    endSession: khongDung,
    reloadProfile: khongDung,
  } as unknown as AuthContextValue;

  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const boc = (con: ReactNode) => (
    <MemoryRouter initialEntries={[`/huong-dan${neo}`]}>
      <QueryClientProvider client={qc}>
        <AuthContext.Provider value={auth}>
          <AntdApp>{con}</AntdApp>
        </AuthContext.Provider>
      </QueryClientProvider>
    </MemoryRouter>
  );
  render(boc(<HuongDanPage />));
}

/** Chữ trong vùng tài liệu — ⛔ gồm thanh công cụ và mục lục. */
function chuTaiLieu(): string {
  return document.querySelector('.sn-hd__noi-dung')?.textContent ?? '';
}

/**
 * Mục §9.6 có đang được dựng ⛔?
 *
 * ⚠⚠ **⛔ hỏi bằng `textContent`** — §3 *Bản đồ màn hình* là một bảng liệt kê **mọi** màn hình
 * của hệ thống, và nó là mục CHUNG nên ⛔ bao giờ bị lọc. Chuỗi *"Sao lưu & khôi phục"* vì thế
 * xuất hiện kể cả khi mục §9.6 đã thu gọn đúng ⇒ một khẳng định theo chữ sẽ **⛔ phân biệt được
 * hai trạng thái** (luật 9). Hỏi theo **neo của tiêu đề** thì phân biệt được.
 */
function coMucSaoLuu(): boolean {
  return document.getElementById('96-sao-lưu--khôi-phục') !== null;
}

describe('dựng tài liệu', () => {
  it('⭐ ra tiêu đề, đề mục, bảng, ô lưu ý', () => {
    dung();
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      'Hướng dẫn sử dụng hệ thống Thủy lợi Sông Nhuệ',
    );
    expect(screen.getAllByRole('table').length).toBeGreaterThan(5);
  });

  it('⭐⭐ ⛔ dấu markdown nào lọt ra chữ người dùng đọc', () => {
    dung(QUYEN_QUAN_TRI);
    const chu = chuTaiLieu();
    expect(chu.length).toBeGreaterThan(5_000);
    expect(chu, '⛔ Chữ đậm in ra nguyên văn hai dấu sao').not.toContain('**');
    expect(chu, '⛔ Liên kết in ra nguyên văn cú pháp markdown').not.toContain('](');
    expect(chu, '⛔ Thẻ khai `man-hinh` in ra cho người dùng đọc').not.toContain('<!--');
    expect(chu, '⛔ Có dòng bộ đọc ⛔ hiểu').not.toContain('⛔ đọc được]');
  });

  it('⭐ định dạng ra đúng THẺ, ⛔ phải chữ phẳng — vế phân biệt của luật 2', () => {
    dung(QUYEN_QUAN_TRI);
    const goc = document.querySelector('.sn-hd__noi-dung')!;
    expect(goc.querySelectorAll('strong').length).toBeGreaterThan(50);
    expect(goc.querySelectorAll('em').length).toBeGreaterThan(10);
    expect(goc.querySelectorAll('.sn-hd__luu-y').length).toBeGreaterThan(5);
    expect(goc.querySelectorAll('.sn-hd__so-do').length).toBeGreaterThan(3);
  });

  it('⭐⭐ neo trong mục lục trỏ tới tiêu đề CÓ THẬT trong DOM', () => {
    dung(QUYEN_QUAN_TRI);
    const dieuHuong = screen.getByRole('navigation', { name: 'Mục lục tài liệu' });
    const lienKet = within(dieuHuong).getAllByRole('link');
    expect(lienKet.length).toBeGreaterThanOrEqual(11);
    for (const a of lienKet) {
      const neo = a.getAttribute('href') ?? '';
      expect(neo.startsWith('#')).toBe(true);
      expect(document.getElementById(neo.slice(1)), `neo ${neo} ⛔ có đích`).not.toBeNull();
    }
  });

  it('nút In gọi `window.print`', async () => {
    const inRa = vi.fn();
    vi.stubGlobal('print', inRa);
    dung();
    await userEvent.click(screen.getByRole('button', { name: /In/ }));
    expect(inRa).toHaveBeenCalledTimes(1);
    vi.unstubAllGlobals();
  });
});

describe('lọc theo vai trò', () => {
  it('⭐⭐ người ⛔ có quyền quản trị ⛔ thấy mục Sao lưu — và ĐƯỢC BÁO là đã thu gọn', () => {
    dung([]);
    expect(coMucSaoLuu()).toBe(false);
    // ⛔⛔ Vế CHỊU LỰC: ẩn mà ⛔ nói ra thì người dùng kết luận hệ thống ⛔ CÓ chức năng ấy.
    expect(screen.getByText(/mục khác đã thu gọn/)).toBeInTheDocument();
  });

  it('⭐⭐ người CÓ quyền thì thấy — vế phân biệt (luật 9)', () => {
    dung(QUYEN_QUAN_TRI);
    expect(coMucSaoLuu()).toBe(true);
  });

  it('⭐⭐ mục CHUNG ⛔ bao giờ bị lọc mất, kể cả tài khoản ⛔ một quyền nào', () => {
    dung([]);
    const chu = chuTaiLieu();
    // Đăng nhập · vai trò · xử lý sự cố: đúng với mọi người. Lọc mất chúng là giấu phần hướng
    // dẫn đăng nhập khỏi đúng người đang ⛔ đăng nhập được.
    expect(chu).toContain('Đăng nhập và tài khoản');
    expect(chu).toContain('Vai trò và quyền');
    expect(chu).toContain('Xử lý tình huống thường gặp');
  });

  it('⭐ bấm "Hiện tất cả" thì mọi mục quay lại', async () => {
    dung([]);
    expect(coMucSaoLuu()).toBe(false);
    await userEvent.click(screen.getByRole('button', { name: 'Hiện tất cả' }));
    expect(coMucSaoLuu()).toBe(true);
    expect(screen.getByText(/Đang hiện TOÀN BỘ tài liệu/)).toBeInTheDocument();
  });

  it('⛔ ô chọn vai trò KHÔNG liệt kê vai trò khác khi tài khoản ⛔ có `adm:role:view`', async () => {
    dung(['cms:article:view']);
    await userEvent.click(screen.getByLabelText('Lọc theo vai trò'));
    // ⚠ Soi TRONG danh sách thả xuống. `screen.queryByText` toàn trang sẽ khớp bảng 12 vai trò
    //   ở §4.1 của chính tài liệu ⇒ bài đỏ vì lý do sai.
    const thaXuong = document.querySelector('.ant-select-dropdown') as HTMLElement;
    expect(thaXuong, '⛔ ⛔ mở được danh sách thả xuống').not.toBeNull();
    expect(within(thaXuong).getByText('Vai trò của tôi')).toBeInTheDocument();
    // Bày 12 vai trò rồi ⛔ tra được quyền của chúng là hứa một thứ ⛔ giữ được — người dùng
    // chọn xong thấy tài liệu trống trơn.
    expect(within(thaXuong).queryByText('Quản lý Xí nghiệp')).toBeNull();
  });
});

describe('tìm kiếm', () => {
  it('⭐⭐ gõ ⛔ dấu vẫn ra kết quả và có TÔ SÁNG', async () => {
    dung(QUYEN_RONG);
    await userEvent.type(screen.getByLabelText('Tìm trong tài liệu'), 'nguong canh bao');

    expect(document.getElementById('66-ngưỡng-và-cảnh-báo')).not.toBeNull();
    // ⛔⛔ Tìm ra mà ⛔ tô gì thì người dùng đọc thành *"hệ thống tìm sai"* — vế này ⛔ bỏ được.
    expect(document.querySelectorAll('.sn-hd__noi-dung mark').length).toBeGreaterThan(0);
  });

  /**
   * ⚠⚠ **Từ khoá ở đây là `nguong bao`, ⛔ phải `nguong canh bao` — và khác biệt ấy đo được.**
   *
   * Bản đầu của bài này dùng `nguong canh bao` rồi **xanh trên cả bản phá**: chuỗi ấy trùng khít
   * nhãn menu *Ngưỡng cảnh báo* nằm trong chỉ mục, nên phép so nguyên cụm cũng khớp ⇒ bài ⛔ phân
   * biệt được hai trạng thái nó sinh ra để phân biệt (luật 9). Đo lại: `nguong bao` có đủ hai từ
   * trong chỉ mục của §6.6 mà **⛔ xuất hiện như một cụm liền**, nên chỉ phép khớp-từng-từ mới ra.
   */
  it('⭐⭐ nhiều từ RỜI NHAU vẫn khớp — `nguong bao` ra "Ngưỡng và cảnh báo"', async () => {
    dung(QUYEN_RONG);
    await userEvent.type(screen.getByLabelText('Tìm trong tài liệu'), 'nguong bao');
    expect(document.getElementById('66-ngưỡng-và-cảnh-báo')).not.toBeNull();
  });

  it('⭐ lọc bớt mục thật, ⛔ phải chỉ tô màu — vế phân biệt', async () => {
    dung(QUYEN_RONG);
    const truoc = chuTaiLieu().length;
    await userEvent.type(screen.getByLabelText('Tìm trong tài liệu'), 'nghi phep');
    expect(chuTaiLieu().length).toBeLessThan(truoc);
  });

  it('⭐⭐ tìm được MÃ LỖI — nửa còn lại của lời dặn "báo kèm mã lỗi" ở §11', async () => {
    dung(QUYEN_RONG);
    await userEvent.type(screen.getByLabelText('Tìm trong tài liệu'), 'HYD-2016');
    // ⚠ Soi trong THÂN bảng: phần dẫn nhập của khối có nêu một mã làm ví dụ, nên `within` cả
    //   khối sẽ khớp hai chỗ và bài đỏ vì lý do sai.
    const than = document.querySelector('#tra-cuu-ma-loi tbody') as HTMLElement;
    expect(within(than).getByText('HYD-2016')).toBeInTheDocument();
  });

  it('⚠ từ khoá bịa ⇒ nói rõ ⛔ có gì, ⛔ im lặng trả trang trống', async () => {
    dung(QUYEN_RONG);
    await userEvent.type(screen.getByLabelText('Tìm trong tài liệu'), 'zzzkhongtontai');
    expect(screen.getAllByText(/Không có mục nào khớp|Không có mã lỗi nào khớp/).length).toBe(2);
  });
});

describe('liên kết sâu từ nút ? trên thanh tiêu đề', () => {
  it('⭐⭐ mở `#neo` của một mục ĐANG BỊ THU GỌN thì bộ lọc tự nhường chỗ', () => {
    // Tài khoản ⛔ có `adm:backup:view` ⇒ §9.6 lẽ ra bị thu gọn. Nhưng liên kết trỏ thẳng vào nó.
    dung([], ['VIEWER'], '#96-sao-lưu--khôi-phục');
    expect(
      coMucSaoLuu(),
      '⛔ Neo trỏ vào một mục đang bị lọc mà ⛔ nhường chỗ ⇒ trang mở ở đầu tài liệu và ⛔ gì ' +
        'xảy ra. Người dùng kết luận nút ? hỏng.',
    ).toBe(true);
    expect(screen.getByText(/Đang hiện TOÀN BỘ tài liệu/)).toBeInTheDocument();
  });

  it('⛔ ⛔ có neo thì bộ lọc vẫn chạy bình thường — vế phân biệt', () => {
    dung([], ['VIEWER']);
    expect(coMucSaoLuu()).toBe(false);
  });

  it('⭐ neo trỏ vào mục tài khoản VẪN xem được thì ⛔ tắt bộ lọc', () => {
    dung(['cms:article:view'], ['CONTENT_EDITOR'], '#71-quy-trình-duyệt-bài-viết');
    expect(document.getElementById('71-quy-trình-duyệt-bài-viết')).not.toBeNull();
    // Bộ lọc vẫn bật ⇒ mục ngoài vai trò vẫn thu gọn.
    expect(coMucSaoLuu()).toBe(false);
  });
});

describe('Màn hình của bạn', () => {
  it('⭐⭐ chỉ liệt kê màn hình tài khoản MỞ ĐƯỢC', () => {
    dung(['cms:article:view']);
    const khoi = document.querySelector('#man-hinh-cua-ban') as HTMLElement;
    const chu = khoi.textContent ?? '';
    expect(chu).toContain('Bài viết');
    expect(
      chu,
      '⛔ Liệt kê màn hình người dùng ⛔ mở được là hứa một thứ ⛔ giữ được',
    ).not.toContain('Sao lưu & khôi phục');
    // Màn hình ⛔ đòi quyền luôn có mặt.
    expect(chu).toContain('Tổng quan');
  });

  it('⭐ đếm đúng và nói ra mẫu số — ⛔ chỉ in một con số trần (quy tắc 16)', () => {
    dung(QUYEN_QUAN_TRI);
    const khoi = document.querySelector('#man-hinh-cua-ban') as HTMLElement;
    expect(khoi.textContent).toMatch(/\d+\/\d+\s*màn hình/);
  });
});
