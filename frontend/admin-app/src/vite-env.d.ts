/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Địa chỉ gốc của API, **đã gồm `/api/v1`** — VD `http://localhost:18080/api/v1`.
   *
   * ⚠ Nhúng vào bundle lúc build (xem `vite.config.ts`). Không đặt thì rơi về `/api/v1`
   * cùng origin, đúng cho trường hợp nginx đứng trước cả FE lẫn API.
   */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
