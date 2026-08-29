import { type PageResult } from '@/shared/api-types';
import { api } from '@/shared/apiClient';

import {
  type ArticleDetail,
  type ArticleSaveRequest,
  type ArticleSummary,
  type BannerRequest,
  type BannerView,
  type CategoryNode,
  type FolderNode,
  type MediaFile,
  type MenuNode,
  type MenuPosition,
  type MenuRequest,
  type ContactView,
  type SiteSettingItem,
  type VersionContent,
  type VersionSummary,
} from './types';

/**
 * Lời gọi API của nhóm CMS — gom một chỗ, không rải trong page.
 *
 * <h3>Vì sao gom</h3>
 *
 * Đường dẫn API là thứ **đổi được**, và mỗi page tự viết `/cms/articles/${id}` thì lần đổi
 * đầu tiên phải đi tìm bằng grep. Quan trọng hơn: khoá của TanStack Query phải nhất quán
 * giữa nơi đọc và nơi làm mới — hai chỗ gõ khác nhau một ký tự là dữ liệu cũ nằm lại trên
 * màn hình sau khi lưu, mà không có lỗi nào.
 */

const BASE = '/cms';

/** Khoá cache — dùng cả khi đọc lẫn khi `invalidateQueries`. */
export const cmsKeys = {
  articles: (filter?: unknown) => ['cms', 'articles', filter ?? null] as const,
  article: (publicId: string) => ['cms', 'article', publicId] as const,
  versions: (publicId: string) => ['cms', 'article', publicId, 'versions'] as const,
  versionContent: (publicId: string, versionId: string) =>
    ['cms', 'article', publicId, 'version', versionId] as const,
  categories: () => ['cms', 'categories'] as const,
  folders: () => ['cms', 'folders'] as const,
  files: (folderId: string | null) => ['cms', 'files', folderId] as const,
  fileUsages: (publicId: string) => ['cms', 'file', publicId, 'usages'] as const,
  banners: () => ['cms', 'banners'] as const,
  menu: (position: MenuPosition) => ['cms', 'menu', position] as const,
  siteConfig: () => ['cms', 'site-config'] as const,
  contacts: (status?: string, page = 0) => ['cms', 'contacts', status ?? 'all', page] as const,
};

export interface ArticleFilter {
  q?: string;
  status?: string;
  categoryId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const cmsApi = {
  // ---- Bài viết -----------------------------------------------------------

  searchArticles(filter: ArticleFilter): Promise<PageResult<ArticleSummary>> {
    return api.getPage<ArticleSummary>(`${BASE}/articles`, filter as Record<string, unknown>);
  },

  // ---- Hộp thư liên hệ ----------------------------------------------------

  listContacts(
    status: string | undefined,
    page: number,
    size: number,
  ): Promise<PageResult<ContactView>> {
    return api.getPage<ContactView>(`${BASE}/contacts`, { status, page, size });
  },

  markContactRead(publicId: string): Promise<ContactView> {
    return api.patch<ContactView>(`${BASE}/contacts/${publicId}/read`, {});
  },

  getArticle(publicId: string): Promise<ArticleDetail> {
    return api.get<ArticleDetail>(`${BASE}/articles/${publicId}`);
  },

  createArticle(body: ArticleSaveRequest): Promise<ArticleDetail> {
    return api.post<ArticleDetail>(`${BASE}/articles`, body);
  },

  updateArticle(publicId: string, body: ArticleSaveRequest): Promise<ArticleDetail> {
    return api.put<ArticleDetail>(`${BASE}/articles/${publicId}`, body);
  },

  /**
   * Chuyển trạng thái.
   *
   * ⛔ `action` là mã backend trả về trong `allowedActions`, **không** phải hằng số khai ở
   * FE. Khai cứng ở đây là dựng bộ luật thứ hai bên cạnh bảng `workflow_transitions`, rồi
   * hai bộ sẽ lệch nhau lúc khách thêm một bước duyệt.
   */
  transition(publicId: string, action: string, reason?: string): Promise<ArticleDetail> {
    return api.post<ArticleDetail>(`${BASE}/articles/${publicId}/transitions`, { action, reason });
  },

  deleteArticle(publicId: string): Promise<void> {
    return api.delete<void>(`${BASE}/articles/${publicId}`);
  },

  versions(publicId: string): Promise<VersionSummary[]> {
    return api.get<VersionSummary[]>(`${BASE}/articles/${publicId}/versions`);
  },

  versionContent(publicId: string, versionId: string): Promise<VersionContent> {
    return api.get<VersionContent>(`${BASE}/articles/${publicId}/versions/${versionId}`);
  },

  restoreVersion(publicId: string, versionId: string): Promise<ArticleDetail> {
    return api.post<ArticleDetail>(`${BASE}/articles/${publicId}/versions/${versionId}/restore`);
  },

  // ---- Danh mục -----------------------------------------------------------

  categories(): Promise<CategoryNode[]> {
    return api.get<CategoryNode[]>(`${BASE}/categories`);
  },

  createCategory(body: { name: string; slug?: string; parentId?: string | null }) {
    return api.post<CategoryNode>(`${BASE}/categories`, body);
  },

  renameCategory(publicId: string, body: { name: string; slug?: string }) {
    return api.put<CategoryNode>(`${BASE}/categories/${publicId}`, body);
  },

  /**
   * Ẩn / hiện một danh mục trên cổng công khai.
   *
   * ⚠⚠ Cột `categories.visible` có từ `V202608191016` và DTO quản trị **trả nó ra** từ đầu, nhưng
   * cho tới 27/08/2026 **không endpoint nào ghi nó** — quản trị viên thấy trạng thái Hiện/Ẩn mà
   * không đổi được. Chuyện thành gấp khi migration CR-01 chọn *ẩn* danh mục "Thông báo" với lý do
   * "ẩn là thao tác quay lui được bằng một cú bấm": lý do ấy chỉ đúng khi cú bấm đó tồn tại.
   *
   * ⛔ Ẩn một danh mục cha thì **cả nhánh dưới nó** rút khỏi cổng, kể cả con đang `visible` —
   * `PublicPortalService.hienTrenCong()` lọc theo `path`. Đây là bản vá của lỗi trang Tiến độ sản
   * xuất liệt kê hai danh mục mồ côi làm các "Năm" (T24.24).
   */
  setCategoryVisibility(publicId: string, visible: boolean) {
    return api.put<CategoryNode>(`${BASE}/categories/${publicId}/visibility`, { visible });
  },

  moveCategory(publicId: string, newParentId: string | null) {
    return api.put<CategoryNode>(`${BASE}/categories/${publicId}/parent`, { newParentId });
  },

  deleteCategory(publicId: string): Promise<void> {
    return api.delete<void>(`${BASE}/categories/${publicId}`);
  },

  // ---- Thư viện media -----------------------------------------------------

  folders(): Promise<FolderNode[]> {
    return api.get<FolderNode[]>(`${BASE}/media/folders`);
  },

  createFolder(body: { name: string; parentId?: string | null }) {
    return api.post<FolderNode>(`${BASE}/media/folders`, body);
  },

  renameFolder(publicId: string, name: string) {
    return api.put<FolderNode>(`${BASE}/media/folders/${publicId}`, { name });
  },

  deleteFolder(publicId: string): Promise<void> {
    return api.delete<void>(`${BASE}/media/folders/${publicId}`);
  },

  files(folderId: string, params?: { q?: string; contentType?: string }): Promise<MediaFile[]> {
    return api.get<MediaFile[]>(`${BASE}/media/folders/${folderId}/files`, params);
  },

  uploadFile(folderId: string, file: File): Promise<MediaFile> {
    const form = new FormData();
    form.append('file', file);
    return api.upload<MediaFile>(`${BASE}/media/folders/${folderId}/files`, form);
  },

  /**
   * URL tải tệp — dùng cho khung xem trước trong màn hình quản trị.
   *
   * ⚠ Đây là **presigned URL sống ngắn**, khác hẳn đường dẫn ảnh trong bài viết (bài viết
   * dùng `/api/v1/public/files/{id}` ổn định vĩnh viễn, vì trang ISR sống hàng giờ). Không
   * lưu giá trị này vào nội dung bài.
   */
  fileUrl(publicId: string): Promise<{ url: string }> {
    return api.get<{ url: string }>(`${BASE}/media/files/${publicId}/url`);
  },

  fileUsages(publicId: string): Promise<string[]> {
    return api.get<string[]>(`${BASE}/media/files/${publicId}/usages`);
  },

  deleteFile(publicId: string): Promise<void> {
    return api.delete<void>(`${BASE}/media/files/${publicId}`);
  },

  // ---- Banner -------------------------------------------------------------

  banners(): Promise<BannerView[]> {
    return api.get<BannerView[]>(`${BASE}/banners`);
  },

  createBanner(title: string, file: File): Promise<BannerView> {
    const form = new FormData();
    form.append('title', title);
    form.append('file', file);
    return api.upload<BannerView>(`${BASE}/banners`, form);
  },

  updateBanner(publicId: string, body: BannerRequest): Promise<BannerView> {
    return api.put<BannerView>(`${BASE}/banners/${publicId}`, body);
  },

  replaceBannerImage(publicId: string, file: File): Promise<BannerView> {
    const form = new FormData();
    form.append('file', file);
    return api.upload<BannerView>(`${BASE}/banners/${publicId}/image`, form);
  },

  bannerImageUrl(publicId: string): Promise<{ url: string }> {
    return api.get<{ url: string }>(`${BASE}/banners/${publicId}/image-url`);
  },

  reorderBanners(publicIds: string[]): Promise<void> {
    return api.put<void>(`${BASE}/banners/reorder`, { publicIds });
  },

  deleteBanner(publicId: string): Promise<void> {
    return api.delete<void>(`${BASE}/banners/${publicId}`);
  },

  // ---- Menu ---------------------------------------------------------------

  menu(position: MenuPosition): Promise<MenuNode[]> {
    return api.get<MenuNode[]>(`${BASE}/menus/${position}`);
  },

  createMenuItem(position: MenuPosition, body: MenuRequest): Promise<MenuNode> {
    return api.post<MenuNode>(`${BASE}/menus/${position}`, body);
  },

  updateMenuItem(publicId: string, body: MenuRequest): Promise<MenuNode> {
    return api.put<MenuNode>(`${BASE}/menus/items/${publicId}`, body);
  },

  reorderMenu(publicIds: string[]): Promise<void> {
    return api.put<void>(`${BASE}/menus/items/reorder`, { publicIds });
  },

  deleteMenuItem(publicId: string): Promise<void> {
    return api.delete<void>(`${BASE}/menus/items/${publicId}`);
  },

  // ---- Cấu hình giao diện -------------------------------------------------

  siteConfig(): Promise<SiteSettingItem[]> {
    return api.get<SiteSettingItem[]>(`${BASE}/site-config`);
  },

  updateSiteConfig(key: string, value: string): Promise<SiteSettingItem> {
    return api.put<SiteSettingItem>(`${BASE}/site-config/${key}`, { value });
  },

  uploadBrandImage(key: string, file: File): Promise<{ attachmentPublicId: string }> {
    const form = new FormData();
    form.append('file', file);
    return api.upload<{ attachmentPublicId: string }>(
      `${BASE}/site-config/brand-images/${key}`,
      form,
    );
  },
};
