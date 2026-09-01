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
  /** Nút được phép bấm, đã lọc theo quyền và theo `workflow_transitions`. */
  allowedActions: AllowedActionView[];
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
