import '@testing-library/jest-dom/vitest';

/**
 * Thiết lập chung cho test của admin-app.
 *
 * `matchMedia` không tồn tại trong jsdom nhưng AntD gọi nó lúc dựng bố cục đáp ứng —
 * thiếu thì component nào dùng Grid/Layout cũng ném lỗi ngay ở lần render đầu, và thông
 * báo lỗi chẳng liên quan gì tới thứ đang kiểm.
 */
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
