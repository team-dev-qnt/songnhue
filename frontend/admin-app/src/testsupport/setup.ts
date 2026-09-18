import '@testing-library/jest-dom/vitest';
import type * as RTL from '@testing-library/react';
import { vi } from 'vitest';

/**
 * ⛔⛔ CSS của antd chèn vào một nút RỜI khỏi document — WS-67 (antd 6).
 *
 * <h2>Vì sao</h2>
 *
 * antd 6 bật **CSS variables mặc định** và ⛔ còn cho tắt (`cssVar` chỉ nhận `prefix`/`key`). Luật
 * `.css-var-root` mang **381 biến** và khớp với phần tử gốc của mọi thành phần, nên mỗi lượt
 * `getComputedStyle` của jsdom phải xử lý chừng ấy biến. Đo 18/09/2026, cùng máy, cùng bài kiểm
 * (`xacThucLaiCauHinh`, lượt bấm Lưu mở hộp thoại):
 *
 * <pre>
 *   antd 5.29.3  — 35 lượt getComputedStyle,    71 ms tổng   (297 KB CSS)
 *   antd 6.6.4   — 36 lượt getComputedStyle, 5 926 ms tổng   (295 KB CSS — CÙNG khối lượng)
 *   antd 6 + nút rời                          ⇒ bấm Lưu 42 ms
 * </pre>
 *
 * ⇒ Khối lượng CSS ⛔ phải nguyên nhân; **biến tuỳ biến** mới là. Bài ấy chạy 13,5–14,7 s trên
 * thời hạn 15 s: xanh ở máy rảnh, đỏ ngay khi máy bận — và runner CI chỉ có 2 lõi.
 *
 * <h2>Vì sao sửa ở đây chứ ⛔ nới `testTimeout`</h2>
 *
 * Nới thời hạn là tự tay tháo một cổng kiểm (§11.17 · T63.14): bài kiểm kế tiếp chậm vì một vòng
 * lặp thật cũng sẽ lọt. Còn ở đây ta **đo được** thứ chậm là gì, và nó là thứ `vite.config.ts` đã
 * khai là ⛔ xử lý (`css: false`): CSS mà antd chèn **lúc chạy** đi vòng qua tuỳ chọn ấy.
 *
 * <h2>Cái giá — nói ra (luật 28)</h2>
 *
 * Trong bộ kiểm, **⛔ quy tắc CSS nào của antd có hiệu lực**. Đo 18/09: **0** tệp kiểm dùng
 * `toBeVisible`/`getComputedStyle` để khẳng định, nên ⛔ khẳng định nào đổi nghĩa. Bài kiểm mới cần
 * khẳng định *"phần tử bị CSS ẩn"* phải biết điều này. `khongChenCssAntd.test.tsx` canh cơ chế:
 * nó đỏ nếu antd lại chèn `<style>` vào document (VD: có HAI bản `@ant-design/cssinjs`, và bản của
 * `StyleProvider` này ⛔ phải bản antd đọc — khi ấy bọc vẫn chạy mà ⛔ tác dụng gì).
 */
vi.mock('@testing-library/react', async (importOriginal) => {
  const goc = await importOriginal<typeof RTL>();
  const { createElement } = await import('react');
  const { StyleProvider } = await import('@ant-design/cssinjs');
  const hopRoi = typeof document === 'undefined' ? undefined : document.createElement('div');
  type Boc = React.JSXElementConstructor<{ children: React.ReactNode }>;
  const bocThem = (trong?: Boc): Boc =>
    function BocKhongCssAntd({ children }) {
      const noi = trong ? createElement(trong, null, children) : children;
      return createElement(StyleProvider, { container: hopRoi }, noi);
    };
  return {
    ...goc,
    render: ((ui: React.ReactNode, options?: RTL.RenderOptions) =>
      goc.render(ui, {
        ...options,
        wrapper: bocThem(options?.wrapper as Boc | undefined),
      })) as typeof goc.render,
    renderHook: ((cb: (props: unknown) => unknown, options?: RTL.RenderHookOptions<unknown>) =>
      goc.renderHook(cb, {
        ...options,
        wrapper: bocThem(options?.wrapper as Boc | undefined),
      })) as typeof goc.renderHook,
  };
});

/**
 * Thiết lập chung cho test của admin-app.
 *
 * `matchMedia` không tồn tại trong jsdom nhưng AntD gọi nó lúc dựng bố cục đáp ứng —
 * thiếu thì component nào dùng Grid/Layout cũng ném lỗi ngay ở lần render đầu, và thông
 * báo lỗi chẳng liên quan gì tới thứ đang kiểm.
 *
 * ⚠ `setupFiles` chạy cho MỌI bài, kể cả bài khai `@vitest-environment node` (VD
 * `buildConfig.test.ts` phải nạp `vite.config.ts` thật, mà `fileURLToPath` ở đó đòi
 * `import.meta.url` dạng `file://` — jsdom cho ra `http://localhost/…`). Ở môi trường node
 * thì không có `window`, và tệp này từng ném `ReferenceError: window is not defined` làm cả
 * bộ hỏng trước khi chạy bài nào. Những bản giả dưới đây chỉ có nghĩa khi CÓ DOM.
 */
const coDom = typeof window !== 'undefined';

if (coDom) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}

/**
 * `ResizeObserver` cũng không có trong jsdom.
 *
 * ⚠ Bản giả này **cố ý không bắn lượt đo nào**: jsdom không dựng bố cục nên mọi kích
 * thước đều bằng 0, và một lượt bắn giả với `contentRect` bịa ra sẽ làm bài kiểm khẳng
 * định trên một con số không có thật. Nơi dùng lấy bề rộng bằng
 * `getBoundingClientRect()`, và bài kiểm nào cần một bề rộng cụ thể thì tự đặt bằng
 * {@link datBeRongCua} — tường minh hơn hẳn việc phụ thuộc vào bản giả.
 */
class ResizeObserverGia {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}
if (coDom && !('ResizeObserver' in window)) {
  Object.defineProperty(window, 'ResizeObserver', { writable: true, value: ResizeObserverGia });
}

/**
 * Ép bề rộng mà `getBoundingClientRect()` trả về, cho mọi phần tử.
 *
 * Dùng để kiểm bố cục ở ba bề rộng thiết bị (3840 / 1920 / 1366) — jsdom luôn trả 0 nên
 * không có cách nào khác để bài kiểm chạm tới đường mã tính số cột.
 */
export function datBeRongCua(beRong: number): void {
  Object.defineProperty(HTMLElement.prototype, 'getBoundingClientRect', {
    configurable: true,
    writable: true,
    value: () => ({
      width: beRong,
      height: 800,
      top: 0,
      left: 0,
      right: beRong,
      bottom: 800,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    }),
  });
}
