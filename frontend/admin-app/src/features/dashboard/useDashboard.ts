import { useQuery } from '@tanstack/react-query';

import { type DashboardView, type MapPointView } from '@/shared/api-types';
import { api } from '@/shared/apiClient';

/** Chu kỳ dự phòng khi chưa có phản hồi nào — chỉ dùng cho **lượt chờ đầu tiên**. */
const CHU_KY_DU_PHONG_MS = 5 * 60_000;

/**
 * Số liệu dashboard, tự làm mới theo chu kỳ **do máy chủ quyết định** — T23.5, M2.15.
 *
 * <h3>⚠ Chu kỳ đọc từ chính phản hồi, không phải hằng số ở FE</h3>
 *
 * Tham số `system.dashboard.auto-refresh-minutes` có màn hình sửa (quy tắc 12). Nếu FE
 * ghi cứng 5 phút thì quản trị viên đổi xuống 1 phút, giao diện báo lưu thành công, và
 * **không gì thay đổi** — đúng lỗi công tắc chết đã trả giá ở WS-12 với hạn mức tải tệp.
 *
 * <p>Vòng lặp tự khép kín: mỗi lượt làm mới mang về giá trị mới nhất của tham số, nên đổi
 * cấu hình có hiệu lực chậm nhất sau **một** chu kỳ cũ, không cần tải lại trang.
 *
 * <p>⛔ Cố ý **không** dùng `refetchIntervalInBackground`: màn hình quản trị mở trong tab
 * nền mà vẫn gọi API mỗi vài phút suốt ngày là tải vô ích. Chế độ màn hình lớn thì luôn
 * là tab đang hiện, nên không mất gì.
 */
export function useDashboard() {
  return useQuery({
    queryKey: ['ops', 'dashboard'],
    queryFn: () => api.get<DashboardView>('/ops/dashboard'),
    refetchInterval: (query) => {
      const giay = query.state.data?.autoRefreshSeconds;
      return giay && giay > 0 ? giay * 1000 : CHU_KY_DU_PHONG_MS;
    },
  });
}

/**
 * Điểm công trình cho bản đồ.
 *
 * <p>Tách khỏi {@link useDashboard} vì **nhịp sống khác hẳn**: KPI đổi theo từng lượt ghi
 * nhận, còn toạ độ chỉ đổi khi có người sửa hồ sơ. Gộp vào một truy vấn thì mỗi chu kỳ
 * làm mới kéo lại toàn bộ danh sách marker mà không có gì thay đổi — nhân với tám tiếng
 * chạy liên tục của màn hình treo tường.
 */
export function useMapPoints() {
  return useQuery({
    queryKey: ['ops', 'dashboard', 'map-points'],
    queryFn: () => api.get<MapPointView[]>('/ops/dashboard/map-points'),
    staleTime: 10 * 60_000,
  });
}
