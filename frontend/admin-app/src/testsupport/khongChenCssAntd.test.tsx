import { createCache, StyleProvider } from '@ant-design/cssinjs';
import type * as RTL from '@testing-library/react';
import { render, screen } from '@testing-library/react';
import { Button, Modal } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

/**
 * Canh cơ chế ở `setup.ts`: trong bộ kiểm, antd ⛔ được chèn `<style>` vào document — WS-67.
 *
 * Lý do và số đo nằm ở javadoc của `setup.ts`. Tóm lại: antd 6 luôn bật CSS variables, và mỗi lượt
 * `getComputedStyle` của jsdom trên một phần tử antd tốn cỡ **165 ms** (antd 5: ~2 ms). Một bài mở
 * hộp thoại từ 0,6 s lên 13,5 s, sát thời hạn 15 s.
 *
 * ⛔ Bài này ⛔ đo thời gian: khẳng định thời gian chạy đỏ theo tải máy chứ ⛔ theo mã (T63.14). Nó
 * canh **cấu trúc** — antd có chèn vào document ⛔ — là thứ quyết định thời gian ấy.
 */
const SELECTOR_ANTD = 'style[data-css-hash], style[data-token-hash]';

afterEach(() => {
  document.head.querySelectorAll(SELECTOR_ANTD).forEach((s) => s.remove());
});

describe('antd ⛔ chèn CSS vào document trong bộ kiểm (WS-67)', () => {
  it('⭐ đối chứng: `render` GỐC ⇒ antd CHÈN style vào document (nên bài dưới phân biệt được)', async () => {
    // ⚠ `cache` riêng: bộ đệm mặc định của cssinjs nhớ "đã chèn" theo đường dẫn style, nên nếu dùng
    //   chung với bài dưới thì bài nào chạy sau sẽ ⛔ chèn lại gì — đo ra 0 vì lý do sai (luật 9).
    const goc = await vi.importActual<typeof RTL>('@testing-library/react');
    goc.render(
      <StyleProvider cache={createCache()}>
        <Button type="primary">Lưu</Button>
      </StyleProvider>,
    );
    expect(
      document.querySelectorAll(SELECTOR_ANTD).length,
      'antd ⛔ còn chèn style vào document ngay cả khi ⛔ bọc gì — bài dưới đang khẳng định trên một tập rỗng',
    ).toBeGreaterThan(0);
    goc.cleanup();
  });

  it('⛔⛔ `render` của bộ kiểm ⇒ 0 style antd trong document, kể cả khi mở hộp thoại', () => {
    render(
      <>
        <Button type="primary">Lưu</Button>
        <Modal open title="Xác nhận">
          nội dung
        </Modal>
      </>,
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(
      document.querySelectorAll(SELECTOR_ANTD).length,
      'antd đang chèn CSS vào document ⇒ mỗi getComputedStyle lại tốn hàng trăm ms (xem setup.ts). ' +
        'Kiểm: có HAI bản @ant-design/cssinjs không (`npm ls @ant-design/cssinjs`)?',
    ).toBe(0);
  });
});
