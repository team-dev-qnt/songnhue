// ⚠ Lấy từ `shared/api-types` chứ không từ component: đây là hình dạng DÂY (backend trả gì), và
// kiểu ở `ApprovalActions.tsx` là hợp đồng PROP. Trước đây file này mượn kiểu của component —
// kiểu đó mang thêm `primary`/`danger`/`requiresReason` mà backend không gửi, nên chỗ này mô tả
// sai payload thật và không ai thấy.
import { type AllowedActionView } from '@/shared/api-types';

/**
 * Kiểu dữ liệu CMS — bản sao của DTO backend (`content/api/*Dtos.java`).
 *
 * <h3>Vì sao chép tay chứ không sinh tự động từ OpenAPI</h3>
 *
 * Sinh tự động thì mỗi lần backend đổi một trường là FE có kiểu mới **mà không ai đọc** —
 * lỗi biên dịch xuất hiện ở chỗ dùng, cách xa nguyên nhân. Chép tay thì mỗi trường đi qua
 * mắt một lần, và chỗ nào backend gọi là `publicId` mà FE gọi là `id` sẽ bị phát hiện lúc
 * chép chứ không phải lúc chạy. Đây cũng là cách `api-types.ts` của MOD-05 đang làm.
 */

export type ArticleStatus = 'NHAP' | 'CHO_DUYET' | 'DA_DUYET' | 'XUAT_BAN' | 'GO_BAI' | 'LUU_TRU';

export interface ArticleSummary {
  publicId: string;
  title: string;
  slug: string;
  status: ArticleStatus;
  publishedAt: string | null;
  viewCount: number;
  categoryNames: string[];
}

export interface ArticleDetail {
  publicId: string;
  title: string;
  slug: string;
  summary: string | null;
  content: string;
  coverAttachmentPublicId: string | null;
  source: string | null;
  status: ArticleStatus;
  publishedAt: string | null;
  reviewNote: string | null;
  metaTitle: string | null;
  metaDescription: string | null;
  metaKeywords: string | null;
  /** Số ký hiệu văn bản — `null` với tin bài thường. Xem `ArticleSaveRequest`. */
  docNumber: string | null;
  /** Ngày ký ban hành, dạng `YYYY-MM-DD`. KHÁC `publishedAt` (thời gian đăng lên cổng). */
  docIssuedDate: string | null;
  viewCount: number;
  /**
   * ⚠ **Tính ở BE**, đừng ghép lại ở đây. Ba điều kiện (đã duyệt · trạng thái cho phép ·
   * đã tới giờ đăng) mà FE tự suy thì màn hình quản trị và cổng công khai sẽ có ngày trả
   * lời khác nhau về cùng một bài — và người sửa nội dung là người phát hiện ra.
   */
  publiclyVisible: boolean;
  categoryPublicIds: string[];
  /**
   * Tài liệu đính kèm — **BẢN ĐANG BIÊN TẬP** (WS-40).
   *
   * ⛔ Cổng công khai đọc **bản chụp phiên bản**, không đọc danh sách này. Hai danh sách khác
   * nhau chừng nào bản sửa chưa được duyệt — và đó chính là điểm của cơ chế, không phải lỗi.
   */
  documents: ArticleDocumentView[];
  /** Nút được phép bấm, đã lọc theo quyền và theo `workflow_transitions`. */
  allowedActions: AllowedActionView[];
}

/**
 * Một tài liệu đính kèm, đã ghép siêu dữ liệu của tệp — khớp `TaiLieuDinhKem` của BE.
 *
 * ⚠ `label` và `originalName` là **hai thứ khác nhau** và màn hình phải hiện cả hai: chỉ hiện
 * nhãn thì ba dòng cùng mang chữ *"Xem quyết định ở đây"* là không truy được cái nào là cái nào.
 */
export interface ArticleDocumentView {
  publicId: string;
  /** Tên gợi nhớ; `null` = chưa đặt ⇒ cổng hiện `originalName`. ⛔ Không sinh nhãn mặc định. */
  label: string | null;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  /** `false` = còn đang quét virus — hiện đúng trạng thái ấy thay vì một nút tải sẽ bị từ chối. */
  downloadable: boolean;
}

export interface ArticleSaveRequest {
  title: string;
  slug?: string;
  summary?: string;
  content: string;
  coverAttachmentPublicId?: string | null;
  authorPublicId?: string | null;
  source?: string;
  publishedAt?: string | null;
  metaTitle?: string;
  metaDescription?: string;
  metaKeywords?: string;
  /**
   * Số ký hiệu văn bản, ví dụ `43/2015/NĐ-CP`.
   *
   * ⛔ Để trống ⇒ ô tương ứng trên cổng ĐỂ TRỐNG. Cổng không có thực thể "văn bản": một văn
   * bản là một bài viết thuộc nhánh "Công bố thông tin" (CR-07), nên hai trường này là ô nhập
   * tay, không phải dữ liệu đồng bộ từ hệ thống văn bản điều hành của Thành phố (CN-01.7).
   */
  docNumber?: string;
  /** Ngày ký ban hành, dạng `YYYY-MM-DD` (không có giờ, không có múi giờ). */
  docIssuedDate?: string | null;
  categoryPublicIds: string[];
  /**
   * Tài liệu đính kèm, **theo đúng thứ tự người dùng sắp** (WS-40).
   *
   * ⚠ Mảng chứ không tập: thứ tự là dữ liệu người dùng nhập, khác `categoryPublicIds`.
   * Bỏ trống hoặc mảng rỗng = bài không có tệp nào — một câu trả lời hợp lệ.
   */
  documents?: ArticleDocumentLink[];
}

export interface VersionSummary {
  publicId: string;
  versionNo: number;
  title: string;
  note: string | null;
  createdAt: string;
  /** Bản đang phục vụ cổng công khai — không nhất thiết là bản mới nhất (copy-on-write). */
  servingPublic: boolean;
}

export interface VersionContent {
  publicId: string;
  versionNo: number;
  title: string;
  summary: string | null;
  content: string;
  metaTitle: string | null;
  metaDescription: string | null;
  createdAt: string;
}

export interface CategoryNode {
  publicId: string;
  name: string;
  slug: string;
  parentPublicId: string | null;
  depth: number;
  sortOrder: number;
  visible: boolean;
}

export interface FolderNode {
  publicId: string;
  name: string;
  parentPublicId: string | null;
  depth: number;
  sortOrder: number;
}

export interface MediaFile {
  publicId: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
}

/** Mã tệp + tên gợi nhớ — thứ gửi lên khi lưu bài. */
export interface ArticleDocumentLink {
  publicId: string;
  label?: string | null;
}

/**
 * Hai kho tệp dùng chung bộ máy thư viện, khác **phạm vi công bố** — khớp enum `KhoTep` của BE.
 *
 * - `MEDIA` — ảnh và video: công khai ngay khi tải lên (`/api/v1/public/files/{id}`).
 * - `TAI_LIEU` — tài liệu: **chỉ ra cổng qua một bài đã xuất bản**
 *   (`/api/v1/public/article-documents/{id}`); đường tệp công khai trả 404.
 *
 * ⛔ Đây là hai từ vựng phải khớp giữa hai phía. Gõ sai chuỗi thì backend ném 422 chứ không âm
 * thầm trả kho kia — `KhoTep.tuThamSo` cố ý không rơi về mặc định khi gặp giá trị lạ.
 */
export type KhoTep = 'MEDIA' | 'TAI_LIEU';

export interface BannerView {
  publicId: string;
  title: string;
  description: string | null;
  imageAttachmentPublicId: string;
  linkUrl: string | null;
  openNewTab: boolean;
  sortOrder: number;
  active: boolean;
  startAt: string | null;
  endAt: string | null;
  /** Dẫn xuất từ `active` + khoảng lịch — BE tính sẵn để hai bên không hiểu khác nhau. */
  visibleNow: boolean;
}

export interface BannerRequest {
  title: string;
  description?: string;
  linkUrl?: string;
  openNewTab: boolean;
  active: boolean;
  startAt?: string | null;
  endAt?: string | null;
}

/**
 * ⚠ Ba giá trị này phải khớp enum `MenuPosition` của backend và ràng buộc
 * `ck_menu_items_position` trong CSDL. Ba nơi nhớ cùng một danh sách — quy tắc 14;
 * `SiteLayoutTest.viTriMenuKhongLech` canh hộ.
 */
export type MenuPosition = 'HEADER' | 'FOOTER' | 'LIEN_KET';

export type MenuLinkType = 'CATEGORY' | 'ARTICLE' | 'URL' | 'EXTERNAL_DOC' | 'NONE';

export interface MenuNode {
  publicId: string;
  label: string;
  linkType: MenuLinkType;
  parentPublicId: string | null;
  categoryPublicId: string | null;
  categorySlug: string | null;
  articlePublicId: string | null;
  articleSlug: string | null;
  url: string | null;
  openNewTab: boolean;
  depth: number;
  sortOrder: number;
  active: boolean;
  /**
   * `publicId` của logo, hoặc `null`.
   *
   * ⚠ Chỉ đặt được cho mục ở vị trí `LIEN_KET` — backend trả `CMS-2015` cho mọi vị trí khác.
   * Menu Header/Footer là menu chữ, cổng không dựng ô ảnh nào cho chúng.
   */
  logoAttachmentId: string | null;
}

export interface MenuRequest {
  label: string;
  linkType: MenuLinkType;
  parentId?: string | null;
  categoryId?: string | null;
  articleId?: string | null;
  url?: string | null;
  openNewTab: boolean;
  active: boolean;
}

/** Một tham số của nhóm `SITE` — khớp `core.spi.SettingItem`. */
export type ContactStatus = 'MOI' | 'DA_DOC' | 'DANG_XU_LY' | 'DA_PHAN_HOI' | 'DONG' | 'LUU_TRU';

/**
 * Một liên hệ / phản ánh gửi từ cổng công khai — CN-01.4.
 *
 * ⛔ `content` và `subject` là chuỗi do người lạ trên Internet nhập. Hiển thị bằng text thường;
 * KHÔNG `dangerouslySetInnerHTML`, không `Typography` với `dangerouslySetInnerHTML`.
 */
export interface ContactView {
  publicId: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  subject: string;
  content: string;
  status: ContactStatus;
  createdAt: string;
  readAt: string | null;
  /** ⛔ `null` = chưa phân loại. Màn hình nói "Chưa phân loại", ⛔ không hiện chuỗi rỗng. */
  categoryPublicId: string | null;
  categoryName: string | null;
  assignedUnitPublicId: string | null;
  assignedUnitName: string | null;
  /**
   * Lý do / nội dung phản hồi của **bước chuyển gần nhất**.
   *
   * ⚠ Bước ⛔ không đòi lý do sẽ **xoá** giá trị cũ — cố ý. Giữ lại là để một câu giải thích của
   * tháng trước đứng cạnh trạng thái của hôm nay.
   */
  resolutionNote: string | null;
}

/**
 * Một phân loại liên hệ — danh mục **do Công ty tự vận hành** (CLAUDE.md quy tắc 16).
 *
 * ⛔ Danh mục ra đời **rỗng**: chưa có văn bản nào của Công ty cấp danh sách này.
 */
export interface ContactCategoryView {
  publicId: string;
  code: string;
  name: string;
  /** Tắt = ⛔ không hiện ở ô chọn nữa, nhưng liên hệ cũ **vẫn giữ** phân loại này. */
  active: boolean;
  sortOrder: number;
}

/** Một ghi chú nội bộ. ⛔⛔ ⛔ KHÔNG BAO GIỜ hiển thị ở cổng công khai. */
export interface ContactNoteView {
  publicId: string;
  content: string;
  createdAt: string;
  createdBy: number | null;
}

export interface SiteSettingItem {
  key: string;
  value: string | null;
  /** Giá trị đang có hiệu lực (đã rơi về mặc định nếu chưa đặt) — thứ cổng thật sự dùng. */
  effectiveValue: string | null;
  valueType: string;
  defaultValue: string | null;
  groupCode: string;
  label: string;
  description: string | null;
  validation: string | null;
  editable: boolean;
}

// ═══════════════ CN-01.6 — Góp ý / đánh giá (T36.8) ═══════════════

/**
 * ⭐ `TU_CHOI` và `AN` là **hai** trạng thái, ⛔ không phải một.
 *
 * `TU_CHOI` = chưa từng hiện trên cổng. `AN` = đã hiện rồi bị gỡ xuống. Gộp lại thì lịch sử ⛔
 * không trả lời được câu hỏi duy nhất người ta sẽ hỏi lúc có khiếu nại: *"nội dung ấy đã từng
 * công khai chưa?"*
 */
export type FeedbackStatus = 'CHO_DUYET' | 'DA_DUYET' | 'TU_CHOI' | 'AN';

/**
 * Một góp ý / đánh giá gửi từ cổng — CN-01.6, chốt **D1** (kiểm duyệt 100%).
 *
 * ⛔ `content` là chuỗi do người lạ trên Internet nhập, và ở đây nó đi **xa hơn** `contacts`: sau
 * khi duyệt nó hiện trên **cổng công khai**. Hiển thị bằng text thường; ⛔ KHÔNG
 * `dangerouslySetInnerHTML` ở bất kỳ đâu.
 */
export interface FeedbackView {
  publicId: string;
  /** ⛔ `null` = gửi ẩn danh, và đó là HỢP LỆ — đây là phiếu khảo sát, ⛔ không phải khiếu nại. */
  fullName: string | null;
  /** ⛔ ⛔ Có ở bản quản trị, ⛔ KHÔNG BAO GIỜ ra bản công khai (NĐ 13/2023). */
  email: string | null;
  /** 1..5, `null` khi người gửi chỉ viết góp ý mà ⛔ không chấm sao. */
  rating: number | null;
  content: string;
  status: FeedbackStatus;
  createdAt: string;
  /** ⛔ ⛔ Chỗ cán bộ viết **về** người gửi — ⛔ KHÔNG BAO GIỜ ra cổng công khai. */
  moderationNote: string | null;
}

/**
 * Tổng hợp mức độ hài lòng — CN-01.6.
 *
 * ⛔⛔ `diemTrungBinh` ⛔ **không bao giờ** được hiển thị một mình. Nó tính trên **hai lớp lọc**:
 * chỉ mục `DA_DUYET`, và trong đó chỉ mục **có chấm điểm**. Một con số 4,2 trần trụi ⛔ không
 * phân biệt được *"4,2 trên 5 phiếu"* với *"4,2 trên 500 phiếu"*, và cũng ⛔ không cho ai thấy
 * rằng có 40 phiếu bị từ chối nằm ngoài phép tính (CLAUDE.md luật 9).
 *
 * ⚠ `null` nghĩa là **chưa ai chấm** — ⛔ không phải 0 sao (quy tắc 16). Backend bỏ hẳn trường
 * này khỏi JSON khi nó `null`, nên ở đây kiểu là optional.
 */
export interface FeedbackSummary {
  tong: number;
  choDuyet: number;
  daDuyet: number;
  tuChoi: number;
  an: number;
  /** **Mẫu số** của `diemTrungBinh` — ⛔ luôn hiển thị kèm. */
  soCoDiem: number;
  diemTrungBinh?: string | number | null;
}
