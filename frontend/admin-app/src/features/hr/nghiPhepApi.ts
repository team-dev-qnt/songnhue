import { api } from '@/shared/apiClient';

import {
  type DonNghiRequest,
  type DonNghiTrangView,
  type DonNghiView,
  type NgayLeRequest,
  type NgayLeView,
  type SoDuPhepView,
  type XemTruocDonView,
} from './hrVocabulary';
import { type AllowedAction } from '@/components/business/ApprovalActions';

const GOC = '/hr/nghi-phep';

/**
 * Nghỉ phép — CN-04.9.
 *
 * ⛔⛔ **⛔ Không dùng `api.get<PageResult<…>>`.** Backend của nghỉ phép trả một kiểu trang **riêng**
 * (`DonTrangView`: `muc`/`tong`/`trang`/`co`), ⛔ không phải `PageResult` của Spring Data. Nhận
 * nhầm kiểu ⇒ `data.content` là `undefined` ⇒ bảng **RỖNG VĨNH VIỄN** mà ⛔ không một dòng lỗi nào
 * — đúng khuyết tật `apiPaging.test.ts` đã bắt ở WS-54.
 */
export const nghiPhepApi = {
  xemTruoc(don: DonNghiRequest): Promise<XemTruocDonView> {
    return api.post<XemTruocDonView>(`${GOC}/xem-truoc`, don);
  },

  nop(don: DonNghiRequest): Promise<DonNghiView> {
    return api.post<DonNghiView>(GOC, don);
  },

  cuaToi(page: number, size: number): Promise<DonNghiTrangView> {
    return api.get<DonNghiTrangView>(`${GOC}/cua-toi`, { page, size });
  },

  /** `nam` bỏ trống = năm hiện tại **theo giờ Việt Nam**, do backend quyết (quy tắc 1). */
  soDu(nam?: number): Promise<SoDuPhepView> {
    return api.get<SoDuPhepView>(`${GOC}/so-du`, nam === undefined ? undefined : { nam });
  },

  choDuyet(page: number, size: number): Promise<DonNghiTrangView> {
    return api.get<DonNghiTrangView>(`${GOC}/cho-duyet`, { page, size });
  },

  cuaNhanVien(employeePublicId: string, page: number, size: number): Promise<DonNghiTrangView> {
    return api.get<DonNghiTrangView>(`${GOC}/cua-nhan-vien/${employeePublicId}`, { page, size });
  },

  /**
   * Nút được phép bấm — **do backend quyết**, ⛔ không do giao diện suy từ trạng thái.
   *
   * ⚠ Người gọi gửi `APPROVE`; việc đổi nó thành `ESCALATE` khi đơn đủ dài nằm ở **service**
   * (chốt C2). Đặt phép đổi ấy ở đây là biến hai khoá `settings` thành núm điều khiển *trình
   * duyệt* thay vì điều khiển *quy trình*.
   */
  hanhDong(publicId: string): Promise<AllowedAction[]> {
    return api.get<AllowedAction[]>(`${GOC}/${publicId}/hanh-dong`);
  },

  thucHien(publicId: string, action: string, reason?: string): Promise<DonNghiView> {
    return api.post<DonNghiView>(`${GOC}/${publicId}/hanh-dong`, { action, reason });
  },

  ngayLe(): Promise<NgayLeView[]> {
    return api.get<NgayLeView[]>('/hr/ngay-le');
  },

  taoNgayLe(body: NgayLeRequest): Promise<NgayLeView> {
    return api.post<NgayLeView>('/hr/ngay-le', body);
  },

  suaNgayLe(publicId: string, body: NgayLeRequest): Promise<NgayLeView> {
    return api.put<NgayLeView>(`/hr/ngay-le/${publicId}`, body);
  },

  xoaNgayLe(publicId: string): Promise<void> {
    return api.delete<void>(`/hr/ngay-le/${publicId}`);
  },
};
