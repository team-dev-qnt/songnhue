import { connection } from 'next/server';

import { API_INTERNAL_BASE_URL } from '@/lib/site';

/**
 * Đường duy nhất gọi backend từ cổng công khai — T16.1.
 *
 * <h3>Vì sao gói lại thay vì gọi `fetch` thẳng ở từng trang</h3>
 *
 * Ba thứ phải giống nhau ở mọi lượt gọi, và ba thứ đó đều là loại "quên một chỗ thì không
 * có lỗi nào, chỉ có hành vi sai":
 *
 * 1. **Bóc envelope.** Backend trả `{ success, data, error, traceId }`. Trang nào quên bóc
 *    sẽ render `undefined` chứ không nổ.
 * 2. **Nhãn cache.** Không gắn nhãn thì `POST /api/revalidate` của backend không có gì để
 *    xoá, và trang đứng yên tới hết chu kỳ — đúng thứ T16.5 sinh ra để tránh.
 * 3. **Xử lý lỗi.** Cổng công khai *không được* trắng trang vì backend hắt hơi.
 */

/** Nhãn cache — phải khớp `PortalCache` phía backend. Hai nơi nhớ cùng một chuỗi. */
export const CACHE_TAGS = {
  /** Mọi chỗ liệt kê bài viết: trang chủ, trang danh mục, tìm kiếm. */
  articles: 'bai-viet',
  /** Menu, banner, cấu hình nhận diện — thứ nằm trên mọi trang. */
  layout: 'giao-dien',
} as const;

/** Chu kỳ dựng lại khi không có ai gọi revalidate. Lưới an toàn, không phải cơ chế chính. */
export const REVALIDATE_SECONDS = 300;

interface Envelope<T> {
  success: boolean;
  data?: T;
  /** Chỉ có ở response phân trang — conventions.md §2.1. */
  meta?: PageMeta;
  error?: { code: string; message: string };
  traceId?: string;
}

/**
 * ⚠⚠ Phân trang của hệ này **không** trả `Page` của Spring nguyên khối.
 *
 * Envelope tách đôi: phần tử vào `data` (một mảng), thông tin trang vào `meta`. Và `meta.page`
 * **đếm từ 1** (§1.3), trong khi tham số `page` gửi lên **đếm từ 0** — hai quy ước ngược nhau
 * trên cùng một khái niệm.
 *
 * Đoán nhầm chỗ này không gây lỗi biên dịch: TypeScript tin kiểu ta khai, nên `data.content`
 * là `undefined` và trang chết lúc chạy với "Cannot read properties of undefined". Đúng thứ
 * đã xảy ra ở lượt chạy thật đầu tiên của WS-16.
 */
export interface PageMeta {
  /** ĐẾM TỪ 1. Muốn dùng làm chỉ số trang thì phải trừ đi 1. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

interface FetchOptions {
  tags?: string[];
  revalidate?: number;
}

/**
 * Gọi một endpoint công khai.
 *
 * @returns `null` khi backend trả 404 **hoặc** khi không gọi được. Cố ý gộp hai trường hợp:
 *   nơi gọi xử lý chúng như nhau (hiện "không có nội dung"), và phân biệt chỉ tạo ra hai
 *   nhánh mà nhánh thứ hai không ai kiểm.
 */
export async function apiGet<T>(path: string, options: FetchOptions = {}): Promise<T | null> {
  const result = await apiGetWithMeta<T>(path, options);
  return result ? result.data : null;
}

/** Bản trả cả `meta` — dùng cho endpoint phân trang. */
export async function apiGetWithMeta<T>(
  path: string,
  options: FetchOptions = {},
): Promise<{ data: T | null; meta?: PageMeta } | null> {
  // ⛔⛔ KHÔNG cho phép lượt gọi này chạy trong lúc prerender.
  //
  //    `next build` dựng sẵn mọi route có `revalidate` và ghi HTML kết quả vào image. Lượt
  //    build chạy ở CI, nơi backend KHÔNG tồn tại: `fetch` hỏng, khối `catch` bên dưới nuốt
  //    lỗi và trả `null`, nên thứ được nướng vào image là một trang không có nội dung nào.
  //    Mỗi container mới phục vụ đúng bản ấy cho tới khi ISR dựng lại xong — tức sau MỖI lượt
  //    triển khai, và `--force-recreate` (§10.53) bảo đảm điều đó xảy ra mỗi lần.
  //
  //    Đo được ngày 25/8 trên chính cây mã đang chạy staging: `prerender-manifest` liệt kê `/`,
  //    và `.next/server/app/index.html` chứa 19 liên kết `/bai-viet/…` KHÔNG cái nào có thật —
  //    toàn bộ là dữ liệu bịa của bộ fallback (§10.54).
  //
  //    ⭐ Đặt ở ĐÂY chứ không ở từng trang: đây là chỗ DUY NHẤT mọi lượt đọc API đi qua, nên
  //      "đọc API mà vẫn bị dựng sẵn" trở thành điều không biểu diễn được, thay vì một dòng
  //      phải nhớ thêm ở mỗi route mới (luật 12). `sitemap.ts` là bằng chứng chuyện đó xảy ra
  //      thật: nó đọc API bằng một đường không ai để ý. Tài liệu của Next dùng `connection()`
  //      đúng theo kiểu này cho các driver CSDL đồng bộ.
  //
  //    ⚠ KHÔNG dùng `dynamic = 'force-dynamic'`: nó hạ mặc định fetch xuống `no-store`, backend
  //      sẽ phải trả lời mọi lượt truy cập. `connection()` không đụng tới cache của fetch, nên
  //      `next.revalidate` bên dưới vẫn giữ nguyên — backend chỉ bị hỏi 1 lần / 5 phút.
  await connection();

  // Địa chỉ NỘI BỘ: hàm này chỉ chạy phía máy chủ (Server Component / route handler).
  const url = `${API_INTERNAL_BASE_URL}/public${path}`;

  try {
    const response = await fetch(url, {
      headers: { Accept: 'application/json' },
      next: {
        revalidate: options.revalidate ?? REVALIDATE_SECONDS,
        tags: options.tags ?? [],
      },
    });

    if (!response.ok) {
      if (response.status !== 404) {
        // Không ném: một lỗi backend không được phép làm trắng cả trang. Ghi log để người
        // vận hành thấy, rồi trả null cho nơi gọi tự quyết định hiển thị gì.
        console.error(`[cổng] ${url} trả HTTP ${response.status}`);
      }
      return null;
    }

    const envelope = (await response.json()) as Envelope<T>;
    if (!envelope.success) {
      return null;
    }
    return { data: envelope.data ?? null, meta: envelope.meta };
  } catch (error) {
    console.error(`[cổng] không gọi được ${url}`, error);
    return null;
  }
}

// ---- Kiểu dữ liệu, mirror của DTO backend --------------------------------

export interface CategoryRef {
  slug: string;
  name: string;
}

export interface ArticleRow {
  slug: string;
  title: string;
  summary: string | null;
  coverAttachmentPublicId: string | null;
  publishedAt: string | null;
  viewCount: number;
  /**
   * Số ký hiệu văn bản — ví dụ `43/2015/NĐ-CP`. `null`/vắng mặt với tin bài thường.
   *
   * <p>Cổng không có thực thể "văn bản": một văn bản **là** một bài viết thuộc nhánh
   * `cong-bo-thong-tin` (CR-07 — cổng KHÔNG đồng bộ từ hệ thống văn bản điều hành của Thành phố,
   * CN-01.7). Hai trường này là ô để biên tập viên nhập, thêm 01/09/2026 cho bảng danh sách văn bản.
   *
   * ⛔ Rỗng ⇒ **ô bảng để trống**. Không dấu gạch, không "Đang cập nhật" (quy tắc 16).
   */
  docNumber: string | null;
  /**
   * Ngày ký ban hành, dạng `YYYY-MM-DD` — một NGÀY thuần, không phải mốc thời gian.
   *
   * ⚠ Hiển thị bằng `formatNgayThuan()`, **không** `formatDate()`: `formatDate` đi qua `new Date()`
   * nên chuỗi này bị hiểu là nửa đêm UTC rồi quy múi giờ — lệch một ngày ở nửa số múi giờ.
   *
   * ⛔ KHÁC `publishedAt` (thời gian đăng lên cổng), và **không cột nào được suy ra từ cột kia**:
   * một văn bản ký năm 2015 có thể được đăng lại năm 2026.
   */
  docIssuedDate: string | null;
  /**
   * Chuyên mục **đang hiện** của bài — CR-12 cần nhãn "Tin thủy lợi / Tin Công ty".
   *
   * ⚠ Backend lọc `visible` trước khi trả: một chuyên mục đang ẩn không được lộ ra dưới dạng
   * nhãn. Mảng rỗng nghĩa là bài không thuộc chuyên mục nào đang hiện — nơi hiển thị bỏ hẳn
   * ô tag, không thay bằng một nhãn mặc định.
   */
  categories: CategoryRef[];
}

export interface ArticleDetail extends ArticleRow {
  content: string;
  metaTitle: string | null;
  metaDescription: string | null;
  metaKeywords: string | null;
  /** Bài Lưu trữ — vẫn vào được bằng địa chỉ trực tiếp nhưng phải gắn `noindex`. */
  archived: boolean;
  /**
   * Ô "Nguồn tin" biên tập viên nhập, **nguyên văn** — URL bài gốc, tên báo, hoặc `null`.
   *
   * ⛔ `null` nghĩa là *chưa ai điền*, KHÔNG phải *nguồn là Công ty*. Xem `docNguonBaiViet()` —
   * chân bài in cứng "Nguồn: Cổng TTĐT Thủy lợi Sông Nhuệ" cho mọi bài suốt tới 31/08/2026 (T26.63).
   */
  source: string | null;
  categories: CategoryRef[];
}

/** Kết quả một trang, đã ghép `data` với `meta` để nơi gọi chỉ cầm một thứ. */
export interface PagedArticles {
  content: ArticleRow[];
  totalElements: number;
  totalPages: number;
  /** ĐẾM TỪ 0 — đã quy đổi từ `meta.page` để khớp tham số gửi lên. */
  number: number;
}

export type MenuLinkType = 'CATEGORY' | 'ARTICLE' | 'URL' | 'EXTERNAL_DOC' | 'NONE';

export interface MenuLink {
  label: string;
  linkType: MenuLinkType;
  categorySlug: string | null;
  articleSlug: string | null;
  url: string | null;
  openNewTab: boolean;
  depth: number;
  parentLabel: string | null;
  /**
   * `publicId` của logo, hoặc `null`.
   *
   * ⚠ Chỉ mục ở vị trí `LIEN_KET` mới có (CR-21). Nơi hiển thị **phải** xử lý được `null`: Công ty
   * tải logo từng cơ quan một, nên "một nửa danh sách có logo" là trạng thái bình thường. Ô rỗng
   * thì hiện tên — không dựng khung ảnh xám chờ tệp.
   */
  logoId: string | null;
}

export interface BannerItem {
  title: string;
  description: string | null;
  imageId: string;
  linkUrl: string | null;
  openNewTab: boolean;
  sortOrder: number;
}

export interface CategoryNode {
  slug: string;
  name: string;
  description: string | null;
  /**
   * Slug danh mục cha; `null` với danh mục gốc.
   *
   * ⚠⚠ Dùng trường này để dựng cây — **đừng** suy quan hệ cha–con từ vị trí trong mảng. Phép
   * suy theo vị trí (mọi mục `depth = n+1` đứng sau một mục `depth = n` là con của mục ấy)
   * đúng chừng nào danh sách còn nguyên vẹn, và sai ngay lượt đầu tiên backend lọc bỏ một mục
   * khỏi giữa danh sách. Đo được trên máy: trang "Tiến độ sản xuất" từng liệt kê hai danh mục
   * của mục "Thông báo" (đã ẩn) làm các **Năm** của nó.
   */
  parentSlug: string | null;
  depth: number;
  sortOrder: number;
}

/**
 * Một nút của sơ đồ tổ chức công bố — CR-24.
 *
 * ⚠ Cố ý **không** có `path`: materialized path là chuỗi id chạy số (`/1/4/9/`), và backend
 * không trả nó ra đường công khai. Nếu một ngày trường ấy xuất hiện ở đây thì đó là dấu hiệu
 * ai đó vừa tái dùng DTO của màn hình quản trị cho cổng.
 */
export interface OrgChartNode {
  code: string;
  name: string;
  shortName: string | null;
  unitType: string;
  children: OrgChartNode[];
}

/**
 * Một dòng bảng "Lãnh đạo Công ty" — CR-25.
 *
 * ⚠⚠ 01/09/2026 gỡ cột `phone`. Số điện thoại của một cá nhân là dữ liệu cá nhân (NĐ 13/2023),
 * và không cơ chế nào phân biệt được số tổng đài với số di động riêng. Gỡ ở **record backend**,
 * không chỉ ẩn ở component — ẩn ở giao diện thì trường vẫn đi qua dây.
 */
export interface LeaderRow {
  fullName: string;
  title: string;
}

/**
 * Một dòng bảng "Xí nghiệp trực thuộc" — CR-26.
 *
 * ⚠⚠ 01/09/2026 gỡ `directorPhone` (số của một **cá nhân**).
 * ⛔ Nhưng `phone` và `email` GIỮ NGUYÊN — đó là tổng đài và hộp thư của **đơn vị**. Đây là ranh
 * giới của cả đợt gỡ: gỡ số của người, giữ số của tổ chức.
 */
export interface SubsidiaryRow {
  code: string;
  name: string;
  shortName: string | null;
  address: string | null;
  phone: string | null;
  email: string | null;
  directorName: string | null;
}

/**
 * Một dòng của bảng danh mục công trình 7 cột — CR-28 / §5.1.
 *
 * ⚠ `latitude`/`longitude` về dạng **chuỗi**: backend dùng `BigDecimal` (quy tắc 2 — cấm
 * float cho mọi số đo), và Jackson tuần tự hoá nó thành chuỗi để không mất chữ số khi đi qua
 * `number` của JavaScript. Đừng `parseFloat` rồi in lại — chỉ nối thẳng vào liên kết bản đồ.
 */
export interface ConstructionRow {
  code: string;
  name: string;
  constructionType: string;
  location: string | null;
  mainSpec: string | null;
  operatingProcedureFileId: string | null;
  protectionPlanFileId: string | null;
  latitude: string | null;
  longitude: string | null;
}

/** Danh mục công trình của một Xí nghiệp — CR-27 gom theo đơn vị quản lý. */
export interface UnitCatalog {
  unitCode: string | null;
  unitName: string;
  unitShortName: string | null;
  constructions: ConstructionRow[];
}

/** Cụm khoá–giá trị của nhóm `SITE`. Khoá lạ trả về `undefined`, nơi gọi tự đặt mặc định. */
export type SiteConfig = Record<string, string>;

// ---- Hàm gọi cụ thể --------------------------------------------------------

export function getSiteConfig(): Promise<SiteConfig | null> {
  return apiGet<SiteConfig>('/site-config', { tags: [CACHE_TAGS.layout] });
}

export function getMenu(position: 'HEADER' | 'FOOTER' | 'LIEN_KET'): Promise<MenuLink[] | null> {
  return apiGet<MenuLink[]>(`/menus/${position}`, { tags: [CACHE_TAGS.layout] });
}

export function getBanners(): Promise<BannerItem[] | null> {
  return apiGet<BannerItem[]>('/banners', { tags: [CACHE_TAGS.layout] });
}

/**
 * Ảnh của thư viện trên trang chủ.
 *
 * ⚠ Không có trường "nơi chụp": ảnh Công ty gửi không kèm dữ liệu ấy, và bịa một địa điểm cho
 *   mỗi ảnh là đúng thứ CLAUDE.md luật 16 cấm. `PhotoRow` chỉ mang thứ có thật.
 */
export interface PhotoRow {
  publicId: string;
  title: string;
}

export function getPhotos(): Promise<PhotoRow[] | null> {
  return apiGet<PhotoRow[]>('/photos', { tags: [CACHE_TAGS.layout] });
}

export function getCategories(): Promise<CategoryNode[] | null> {
  return apiGet<CategoryNode[]>('/categories', { tags: [CACHE_TAGS.layout] });
}

export function getArticles(params: {
  category?: string;
  q?: string;
  page?: number;
  size?: number;
}): Promise<PagedArticles | null> {
  const query = new URLSearchParams();
  if (params.category) query.set('category', params.category);
  if (params.q) query.set('q', params.q);
  query.set('page', String(params.page ?? 0));
  query.set('size', String(params.size ?? 12));

  return apiGetWithMeta<ArticleRow[]>(`/articles?${query.toString()}`, {
    tags: [CACHE_TAGS.articles],
  }).then((result) => {
    if (!result) {
      return null;
    }
    const meta = result.meta;
    return {
      content: result.data ?? [],
      totalElements: meta?.totalElements ?? 0,
      totalPages: meta?.totalPages ?? 0,
      // `meta.page` đếm từ 1, tham số `page` đếm từ 0 — quy đổi ở đúng một chỗ này.
      number: meta ? meta.page - 1 : 0,
    };
  });
}

export function getArticle(slug: string): Promise<ArticleDetail | null> {
  return apiGet<ArticleDetail>(`/articles/${encodeURIComponent(slug)}`, {
    tags: [CACHE_TAGS.articles],
  });
}

// ---- Tổ chức & công trình công khai (T11.30) --------------------------------

/**
 * ⚠ Ba nhóm dữ liệu dưới đây gắn nhãn cache **riêng**, không dùng chung `layout`.
 *
 * Chúng đổi theo nhịp khác hẳn tin bài: một Xí nghiệp đổi số điện thoại vài năm một lần.
 * Gộp nhãn thì mỗi lượt xuất bản bài viết lại xoá luôn cả bảng công trình khỏi bộ đệm —
 * không sai kết quả, nhưng backend bị hỏi lại một câu mà câu trả lời không hề đổi.
 */
export const ORG_TAG = 'to-chuc';
export const CONSTRUCTION_TAG = 'cong-trinh';

/** Sơ đồ cây cơ cấu tổ chức — CR-24. */
export function getOrgChart(): Promise<OrgChartNode[] | null> {
  return apiGet<OrgChartNode[]>('/org-units/chart', { tags: [ORG_TAG] });
}

/** Bảng Lãnh đạo Công ty — CR-25. */
export function getCompanyLeaders(): Promise<LeaderRow[] | null> {
  return apiGet<LeaderRow[]>('/org-units/leaders', { tags: [ORG_TAG] });
}

/** Bảng Xí nghiệp trực thuộc — CR-26, và khối "Đơn vị trực thuộc" trang chủ (CR-19). */
export function getSubsidiaries(): Promise<SubsidiaryRow[] | null> {
  return apiGet<SubsidiaryRow[]>('/org-units/subsidiaries', { tags: [ORG_TAG] });
}

/** Danh mục công trình gom theo Xí nghiệp — CR-27, CR-28. */
export function getConstructionCatalog(): Promise<UnitCatalog[] | null> {
  return apiGet<UnitCatalog[]>('/constructions', { tags: [CONSTRUCTION_TAG] });
}

/**
 * Một dòng của khối "Tình hình vận hành công trình" — CN-02.11, 6 cột (chốt G4).
 *
 * ⚠ `parameterValue` về dạng **chuỗi**: backend dùng `BigDecimal` (quy tắc 2 — cấm float/double
 * cho mọi số đo), và đọc nó thành `number` ở đây là đánh mất đúng thứ kiểu ấy bảo vệ.
 *
 * ⛔ Không có `note` và không có người cập nhật. Hai trường ấy có trong bảng nhưng thuộc phạm vi
 * nội bộ; chúng không nằm trong DTO công khai của backend, nên chúng cũng không có chỗ ở đây.
 */
export interface OperationStatusRow {
  constructionCode: string;
  constructionName: string;
  /** `null` = chưa gán đơn vị quản lý — hiện "Chưa phân đơn vị quản lý", không giấu dòng đi. */
  unitName: string | null;
  statusCode: string;
  statusName: string;
  /** Mã màu `#rrggbb` do Công ty đặt trong danh mục mã. Xem `mauTrangThaiHopLe()`. */
  statusColor: string;
  /** `null` khi mã không mang tham số (VD "Đóng kín") — ⛔ không quy về `0`. */
  parameterValue: string | null;
  parameterUnit: string | null;
  effectiveAt: string;
  updatedAt: string | null;
}

/**
 * Tình hình vận hành hiện hành của từng công trình — CN-02.11.
 *
 * ⚠ Đây là dữ liệu **nhập tay** của trực ban, không phải số liệu tự động của trạm bơm. §5.3 của
 * văn bản nghiệm thu đòi bốn trường theo ngày (trạng thái trạm, số máy chạy, lưu lượng) và cần một
 * API nguồn chưa tồn tại — **OI-02 còn mở**.
 */
export function getOperationStatuses(): Promise<OperationStatusRow[] | null> {
  return apiGet<OperationStatusRow[]>('/constructions/operation-statuses', {
    tags: [CONSTRUCTION_TAG],
  });
}

/**
 * Giờ máy chủ — mốc cho dòng "Cập nhật lúc" của CR-35.
 *
 * ⛔ `revalidate: 0` là bắt buộc và là điểm khác biệt duy nhất của lượt gọi này. Một mốc thời
 * gian nằm trong bộ đệm 5 phút thì nó nói dối chính xác 5 phút, mà cả lý do tồn tại của dòng
 * "Cập nhật lúc" là để người xem biết số liệu mới đến bao giờ. Đây cũng là lý do mốc lấy từ
 * **máy chủ** chứ không phải `new Date()` phía máy khách: đồng hồ máy khách sai thì cả trang
 * nói sai theo, và không ai đối chiếu được.
 */
export function getServerTime(): Promise<string | null> {
  return apiGet<string>('/now', { revalidate: 0 });
}
