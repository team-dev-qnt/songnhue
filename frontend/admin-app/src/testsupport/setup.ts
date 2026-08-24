import '@testing-library/jest-dom/vitest';

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
