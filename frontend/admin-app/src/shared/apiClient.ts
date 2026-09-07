import axios, {
  AxiosHeaders,
  type AxiosError,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';

import { type ApiEnvelope, type ErrorDetail, type PageResult } from './api-types';
import { type ErrorHandling, entryFor, messageFor } from './error-map';

/**
 * HTTP client **duy nhất** của admin-app (conventions.md §2.5).
 *
 * ESLint chặn `axios` và `fetch` ở mọi file khác — không phải để cho gọn, mà vì bốn thứ
 * dưới đây phải đúng ở *mọi* request, và mỗi client tự chế là một chỗ quên:
 *
 * 1. **Access token trong bộ nhớ, không localStorage.** Đây là điều kiện để một lỗ XSS
 *    không lấy được vé. Hệ quả phải chấp nhận: F5 là mất token — nên có {@link bootstrapSession}.
 * 2. **CSRF double-submit.** Header `X-CSRF-Token` phải khớp cookie `XSRF-TOKEN` ở mọi
 *    request đổi dữ liệu, kể cả `/auth/refresh` (backend cố ý KHÔNG miễn cho nó).
 * 3. **Làm mới token đúng một lượt.** Mười request cùng nhận 401 phải dùng chung một lần
 *    gọi refresh, và refresh xoay vòng token nên gọi song song là tự kích hoạt cơ chế
 *    phát hiện dùng lại của backend → thu hồi cả family, đá người dùng ra ngoài.
 * 4. **Bóc envelope + chuẩn hoá lỗi.** Nơi duy nhất biết hình dạng `{success, data, error}`.
 */

// =============================================================================
// Trạng thái phiên — cố ý chỉ nằm trong bộ nhớ
// =============================================================================

let accessToken: string | null = null;

/**
 * Tên cookie CSRF — theo quy ước Angular/axios, khớp `CsrfTokens.COOKIE` phía backend.
 *
 * ⛔ Cố ý **không** có biến `csrfToken` song song: vé CSRF chỉ có một nguồn là cookie.
 * Xem giải thích ở {@link currentCsrfToken}.
 */
const CSRF_COOKIE = 'XSRF-TOKEN';

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function clearTokens(): void {
  accessToken = null;
}

/**
 * Vé CSRF đang dùng — **đọc thẳng từ cookie, không giữ bản sao nào.**
 *
 * ⚠⚠ Bản đầu giữ thêm một bản trong bộ nhớ và cho nó thắng cookie
 * (`csrfToken ?? readCookie(...)`). Đó là lỗi, và nó tự lộ ra ngay khi có bài kiểm: đổi mật
 * khẩu thành công thì máy chủ thu hồi phiên và **xoá cookie**, nhưng bản trong RAM vẫn còn,
 * nên FE tiếp tục gửi một vé mà trình duyệt không còn giữ. Máy chủ ghi đúng triệu chứng đó:
 *
 * ```
 * Từ chối CSRF: POST /api/v1/auth/change-password — header có, cookie thiếu
 * ```
 *
 * ⭐ **Vì sao bỏ hẳn bản sao chứ không chỉ đảo thứ tự ưu tiên.** Máy chủ đối chiếu header
 * với **cookie**. Nên khi cookie vắng mặt thì *không giá trị nào* gửi lên có thể đi qua —
 * bản sao trong bộ nhớ không cứu được lượt gọi nào, nó chỉ đổi thông báo lỗi từ "thiếu vé"
 * thành "vé không khớp" và đẩy người đọc log đi tìm một lỗi đối chiếu không tồn tại.
 *
 * Bản sao đó cũng không giải quyết vấn đề nào có thật: cookie `XSRF-TOKEN` cố ý **không**
 * httpOnly, `Path=/`, cùng origin — luôn đọc được bằng JS. Phản hồi đăng nhập vừa đặt
 * cookie vừa trả vé trong thân; trình duyệt xử lý `Set-Cookie` xong mới giải lời hứa, nên
 * không có khe hở nào giữa hai thứ. Giữ hai nguồn cho **một** sự thật chỉ tạo ra cơ hội để
 * chúng lệch nhau.
 */
function currentCsrfToken(): string | null {
  return readCookie(CSRF_COOKIE);
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  const found = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix));
  return found ? decodeURIComponent(found.slice(prefix.length)) : null;
}

// =============================================================================
// Lỗi đã chuẩn hoá
// =============================================================================

/**
 * Lỗi mà mọi màn hình nhận được — đã có mã, câu tiếng Việt và `traceId`.
 *
 * Màn hình **không bao giờ** phải đụng tới `AxiosError`: hình dạng của nó khác nhau giữa
 * lỗi mạng, lỗi HTTP và lỗi huỷ request, và đó là nguồn của những nhánh `if` sai lặng lẽ.
 */
export class ApiClientError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly handling: ErrorHandling,
    readonly severity: 'info' | 'warning' | 'error',
    readonly httpStatus: number | null,
    /** Mã tra log — hiện trên trang lỗi để người dùng đọc cho quản trị viên. */
    readonly traceId: string | null,
    readonly details: ErrorDetail[],
  ) {
    super(message);
    this.name = 'ApiClientError';
  }

  /**
   * Lỗi theo trường, dạng AntD `Form.setFields` dùng được ngay.
   *
   * Tham số kiểu để khớp với form đã gõ kiểu: `fieldErrors<keyof CreateUserRequest & string>()`.
   * Phép ép kiểu bên trong là **có chủ ý và có rủi ro thật** — backend trả tên trường theo
   * DTO của nó, và nếu hai bên lệch tên thì AntD lặng lẽ bỏ qua dòng đó. Đổi lại là mọi
   * form không phải tự viết vòng lặp ánh xạ. Lệch tên trường lộ ra ngay lần thử đầu tiên.
   */
  fieldErrors<TName extends string = string>(): { name: TName; errors: string[] }[] {
    return this.details.map((detail) => ({ name: detail.field as TName, errors: [this.message] }));
  }
}

function toApiClientError(error: unknown): ApiClientError {
  if (error instanceof ApiClientError) {
    return error;
  }

  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<ApiEnvelope<unknown>>;
    const body = axiosError.response?.data;
    const code = body?.error?.code ?? null;

    // Không có response = chưa tới được máy chủ. Đừng bịa mã lỗi nghiệp vụ cho nó:
    // "Lỗi hệ thống" và "mất mạng" là hai việc người dùng xử lý khác hẳn nhau.
    if (!axiosError.response) {
      return new ApiClientError(
        'NETWORK',
        'Không kết nối được máy chủ, kiểm tra đường truyền rồi thử lại',
        'toast',
        'error',
        null,
        null,
        [],
      );
    }

    const entry = entryFor(code);
    return new ApiClientError(
      code ?? 'SYS-0001',
      messageFor(code, body?.error?.message),
      entry.handling,
      entry.severity,
      axiosError.response.status,
      body?.traceId ?? null,
      body?.error?.details ?? [],
    );
  }

  return new ApiClientError(
    'SYS-0001',
    error instanceof Error ? error.message : 'Lỗi không xác định',
    'toast',
    'error',
    null,
    null,
    [],
  );
}

// =============================================================================
// Sự kiện phiên — tránh phụ thuộc vòng với AuthProvider
// =============================================================================

/**
 * `apiClient` phát sự kiện, `AuthProvider` nghe. Ngược lại (client gọi thẳng provider)
 * là vòng import: provider cần client để gọi API, client cần provider để đăng xuất.
 */
export type SessionEvent =
  | { type: 'sessionLost'; reason: string }
  | { type: 'mustChangePassword' }
  | { type: 'maintenance' };

type SessionListener = (event: SessionEvent) => void;

const listeners = new Set<SessionListener>();

export function onSessionEvent(listener: SessionListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function emit(event: SessionEvent): void {
  listeners.forEach((listener) => listener(event));
}

// =============================================================================
// Instance
// =============================================================================

/**
 * Gốc của mọi lượt gọi API.
 *
 * ⚠⚠ Mặc định là đường dẫn **tương đối** — tức là **cùng origin** với trang. Đó là hình dạng
 * mà production dùng (nginx đứng trước cả hệ, T11.5), và cũng là hình dạng của `make dev-docker`
 * (nginx của image admin-app chuyển tiếp `/api/`) lẫn `make dev-native` (Vite proxy `/api`).
 *
 * Trỏ sang một origin khác thì mọi lượt gọi phải qua CORS, mà backend **không cấu hình CORS**
 * — preflight trả thẳng `403 Invalid CORS request`, và giao diện chết từ ô đăng nhập.
 *
 * ⚠ Dùng `||` chứ **không** dùng `??`: khi tệp compose truyền `VITE_API_BASE_URL:` để trống,
 * Vite nhúng vào bundle một **chuỗi rỗng**, mà chuỗi rỗng không phải `null`/`undefined` nên `??`
 * giữ nguyên nó. Hậu quả: `baseURL = ''`, lượt gọi rơi về `/auth/login` — mất hẳn tiền tố
 * `/api/v1`, nginx trả `index.html`, và axios báo lỗi phân tích JSON ở một chỗ chẳng liên quan.
 */
const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';
const MUTATING_METHODS = new Set(['post', 'put', 'patch', 'delete']);

/** Đường dẫn không bao giờ được kéo theo vòng làm mới token — chính chúng là luồng cấp token. */
const AUTH_ENTRY_PATHS = ['/auth/login', '/auth/2fa/', '/auth/refresh'];

const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  // Refresh token và vé CSRF đều đi bằng cookie. Với `BASE_URL` tương đối thì cùng origin
  // nên cookie tự đi kèm; giữ cờ này để cấu hình trỏ sang origin khác vẫn hoạt động.
  withCredentials: true,
  timeout: 30_000,
});

http.interceptors.request.use((config) => {
  const headers = AxiosHeaders.from(config.headers);

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }
  if (MUTATING_METHODS.has((config.method ?? 'get').toLowerCase())) {
    const token = currentCsrfToken();
    if (token) {
      headers.set('X-CSRF-Token', token);
    }
  }

  config.headers = headers;
  return config;
});

// =============================================================================
// Làm mới token — một lượt duy nhất
// =============================================================================

/** Lượt refresh đang chạy. Request thứ hai trở đi chờ chính lời hứa này. */
let refreshInFlight: Promise<boolean> | null = null;

interface RetriableConfig extends InternalAxiosRequestConfig {
  /** Cờ đánh dấu "đã thử lại rồi" — chặn vòng lặp vô hạn khi lượt thử lại cũng 401. */
  __retried?: boolean;
}

function isAuthEntryPath(url: string | undefined): boolean {
  if (!url) {
    return false;
  }
  return AUTH_ENTRY_PATHS.some((path) => url.includes(path));
}

async function runRefresh(): Promise<boolean> {
  try {
    const response =
      await http.post<ApiEnvelope<{ accessToken: string; csrfToken: string }>>('/auth/refresh');
    const data = response.data.data;
    if (!data?.accessToken) {
      return false;
    }
    // Vé CSRF mới đi kèm phản hồi này bằng `Set-Cookie`; không chép lại vào bộ nhớ.
    setAccessToken(data.accessToken);
    return true;
  } catch {
    clearTokens();
    return false;
  }
}

function refreshOnce(): Promise<boolean> {
  refreshInFlight ??= runRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

/**
 * Khôi phục phiên sau khi tải lại trang.
 *
 * Access token nằm trong bộ nhớ nên F5 là mất; cookie refresh thì còn. Gọi hàm này một
 * lần lúc khởi động: có cookie hợp lệ thì người dùng đi tiếp, không thì về trang đăng nhập.
 * Thiếu bước này, giữ token trong bộ nhớ sẽ biến thành "F5 là đăng nhập lại" — và áp lực
 * sửa cho tiện sẽ đẩy token xuống localStorage, tức là bỏ luôn lớp phòng thủ XSS.
 */
export function bootstrapSession(): Promise<boolean> {
  return refreshOnce();
}

http.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    const apiError = toApiClientError(error);
    const config = axios.isAxiosError(error)
      ? (error.config as RetriableConfig | undefined)
      : undefined;

    if (apiError.handling === 'maintenance') {
      emit({ type: 'maintenance' });
    }
    if (apiError.handling === 'changePassword') {
      emit({ type: 'mustChangePassword' });
    }

    const canRetry = config && !config.__retried && !isAuthEntryPath(config.url);

    // 401 hoặc vé CSRF lệch → làm mới một lượt rồi gửi lại đúng một lần.
    // AUTH-0008 (phát hiện dùng lại refresh token) KHÔNG thử lại: backend đã thu hồi cả
    // family, gọi refresh nữa chỉ tạo thêm một sự kiện bảo mật giả.
    const worthRefreshing =
      apiError.code !== 'AUTH-0008' &&
      (apiError.httpStatus === 401 || apiError.handling === 'retryCsrf');

    if (canRetry && worthRefreshing) {
      const ok = await refreshOnce();
      if (ok) {
        config.__retried = true;
        return http.request(config);
      }
    }

    // ⚠ Điều kiện `!isAuthEntryPath` áp cho CẢ hai nhánh, không riêng nhánh 401.
    // Thiếu nó thì lần mở trang đầu tiên — chưa đăng nhập, `bootstrapSession` gọi
    // `/auth/refresh` và nhận AUTH-0002 — cũng bị coi là "mất phiên" và bắn thông báo
    // "Phiên đăng nhập hết hạn" vào mặt người chưa từng đăng nhập. Lượt refresh hỏng
    // được báo bằng giá trị trả về `false`, không bằng sự kiện.
    const isSessionEndpoint = isAuthEntryPath(config?.url);
    if (!isSessionEndpoint && (apiError.handling === 'reauth' || apiError.httpStatus === 401)) {
      clearTokens();
      emit({ type: 'sessionLost', reason: apiError.message });
    }

    return Promise.reject(apiError);
  },
);

// =============================================================================
// Bóc envelope
// =============================================================================

/**
 * Bóc `data` ra khỏi envelope.
 *
 * `success: false` kèm HTTP 2xx là chuyện không được xảy ra theo §2.1, nhưng nếu xảy ra
 * thì im lặng trả `undefined` còn tệ hơn nhiều — màn hình sẽ hiện bảng rỗng như thể
 * không có dữ liệu, thay vì báo lỗi.
 *
 * ⚠⚠ **204 No Content không có thân, và thân rỗng là THÀNH CÔNG — không phải envelope hỏng.**
 *
 * Đây là lỗi đã biến việc ĐÃ XONG thành lỗi hiện lên màn hình, trên **24 endpoint** cùng lúc:
 * xoá, sắp xếp lại, đánh dấu đã đọc, gỡ đăng, khoá tài khoản… và đổi mật khẩu.
 *
 * Cơ chế: `ResponseEnvelopeAdvice` là một `ResponseBodyAdvice`, mà Spring **không gọi advice
 * khi handler trả `void`** — không có thân thì không converter nào chạy. Nên 24 endpoint ấy
 * trả 204 trần, không envelope. Phía này, axios đặt `response.data = ''` cho thân rỗng;
 * `''.success` là `undefined`, `!undefined` là `true`, và hàm ném `SYS-0001 "Thao tác không
 * thành công"` — **sau khi máy chủ đã commit xong**.
 *
 * Hậu quả nặng nhất đo được ở luồng đổi mật khẩu: `api.post` ném trước khi tới `endSession()`,
 * nên phiên không được dọn, người dùng thấy báo lỗi và bấm gửi lại — lần này backend trả
 * **403 AUTH-0005** vì phiên đã bị thu hồi cùng vé CSRF ngay ở lượt đầu. Nhật ký máy chủ đọc ra
 * `change-password → 204` rồi hai lượt `403`. Việc xong từ lượt đầu; chỉ đường ra là hỏng.
 *
 * ⚠ Lượt sửa trước đã chữa **triệu chứng thứ hai** của cùng chuỗi này (guard đẩy ngược về biểu
 * mẫu — xem `ChangePasswordPage`), mà không tìm ra mắt xích đầu tiên nằm ở đây. Đó là lý do phải
 * đặt phép kiểm ở chính hàm này chứ không ở màn hình: 24 đường vào cùng đi qua đây.
 */
function unwrap<T>(response: AxiosResponse<ApiEnvelope<T>>): T {
  const envelope = response.data;

  // Kiểm cả ba: mã trạng thái (nguồn sự thật), thân rỗng do axios đặt, và `null` phòng khi
  // một `transformResponse` khác đi qua. Chỉ kiểm một trong ba là để hở đúng chỗ vừa hỏng.
  if (response.status === 204 || envelope == null || (envelope as unknown) === '') {
    return undefined as T;
  }

  if (!envelope.success) {
    const code = envelope.error?.code ?? null;
    const entry = entryFor(code);
    throw new ApiClientError(
      code ?? 'SYS-0001',
      messageFor(code, envelope.error?.message),
      entry.handling,
      entry.severity,
      200,
      envelope.traceId ?? null,
      envelope.error?.details ?? [],
    );
  }
  return envelope.data as T;
}

export const api = {
  async get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
    const response = await http.get<ApiEnvelope<T>>(url, { params });
    return unwrap(response);
  },

  /** Truy vấn phân trang: phần tử ở `data`, thông tin trang ở `meta` (§2.1). */
  async getPage<T>(url: string, params?: Record<string, unknown>): Promise<PageResult<T>> {
    const response = await http.get<ApiEnvelope<T[]>>(url, { params });
    // `?? []` không phải cho chắc: `unwrap` trả `undefined` khi thân rỗng, và bốn dòng dưới
    // đọc `items.length`. Một truy vấn phân trang không nên trả 204, nhưng "không nên" và
    // "không thể" là hai chuyện khác nhau — và chỗ này hỏng thì hỏng bằng màn hình trắng.
    const items = unwrap(response) ?? ([] as T[]);
    return {
      items,
      meta: response.data?.meta ?? {
        page: 1,
        size: items.length,
        totalElements: items.length,
        totalPages: 1,
      },
    };
  },

  /**
   * Tải một tệp nhị phân — bản kết xuất báo cáo (T34.7).
   *
   * ⛔⛔ ⛔ Đừng thay bằng `window.open(url)`: endpoint đòi header `Authorization`, và một tab mới
   * ⛔ không mang theo nó — người dùng nhận một tab trắng kèm 401, và triệu chứng ấy đọc như "hệ
   * thống hỏng" chứ ⛔ không như "thiếu quyền".
   *
   * ⚠ `responseType: 'blob'` ⇒ thân phản hồi ⛔ KHÔNG đi qua `unwrap`: endpoint tệp trả byte trần,
   * ⛔ không bọc envelope (§10.52 — envelope bọc `byte[]` làm ảnh cổng im lặng suốt bốn ngày).
   *
   * @returns blob kèm tên tệp lấy từ `Content-Disposition` — ⛔ FE ⛔ không tự đặt tên, vì tên tệp là
   *   thứ người dùng lưu lại rồi gửi đi, và hai nơi đặt tên là hai cách gọi cùng một báo cáo
   */
  async getTep(url: string): Promise<{ blob: Blob; tenTep: string | null }> {
    const response = await http.get<Blob>(url, { responseType: 'blob' });
    const cd = response.headers['content-disposition'];
    const m = typeof cd === 'string' ? /filename="?([^";]+)"?/i.exec(cd) : null;
    return { blob: response.data, tenTep: m ? m[1] : null };
  },

  async post<T>(url: string, body?: unknown): Promise<T> {
    const response = await http.post<ApiEnvelope<T>>(url, body);
    return unwrap(response);
  },

  async put<T>(url: string, body?: unknown): Promise<T> {
    const response = await http.put<ApiEnvelope<T>>(url, body);
    return unwrap(response);
  },

  async patch<T>(url: string, body?: unknown): Promise<T> {
    const response = await http.patch<ApiEnvelope<T>>(url, body);
    return unwrap(response);
  },

  async delete<T>(url: string): Promise<T> {
    const response = await http.delete<ApiEnvelope<T>>(url);
    return unwrap(response);
  },

  /** Tải tệp lên — để axios tự đặt `Content-Type` kèm boundary của multipart. */
  async upload<T>(url: string, form: FormData): Promise<T> {
    const response = await http.post<ApiEnvelope<T>>(url, form);
    return unwrap(response);
  },
};

/** Chỉ dành cho bài kiểm — màn hình dùng `api.*`. */
export const __httpForTests = http;
