import { useQuery } from '@tanstack/react-query';

import { type DashboardView, type MapPointView, type StationLayerView } from '@/shared/api-types';
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

/**
 * ⭐ Lớp **điểm đo thuỷ văn** cho bản đồ — T35.1.
 *
 * <p>⛔ Truy vấn RIÊNG, ⛔ không gộp vào {@link useMapPoints}: hai lớp có nhịp sống ngược nhau. Toạ
 * độ công trình chỉ đổi khi có người sửa hồ sơ (`staleTime` 10 phút), còn điểm đo mang **giá trị đo
 * và trạng thái tín hiệu** — chúng đổi mỗi khung 10 phút của nguồn.
 *
 * <p>⚠ Nhịp làm mới ở đây là **2 phút**, cố ý ⛔ KHÁC con số 5 phút của widget cổng công khai
 * (OI-09, T35.12). Hai con số trả lời hai câu hỏi khác nhau: người trực cần số mới nhất, còn cổng
 * công khai cân bằng giữa độ tươi và tải máy chủ. ⛔ Đừng gộp thành một tham số — *"một công tắc
 * cho hai bóng đèn cũng là lỗi"*.
 *
 * <p>⚠ Đòi quyền `hyd:station:view`, trong khi bản đồ mở bằng `ops:dashboard:view`. Hôm nay cả 5
 * vai trò có quyền sau đều có quyền trước (đo 04/09/2026), nên chưa ai gặp lớp rỗng. Xem javadoc
 * `StationController#mapPoints` — ngày nào lệch, lớp này im lặng rỗng.
 */
export function useStationLayer() {
  return useQuery({
    queryKey: ['hyd', 'stations', 'map-points'],
    queryFn: () => api.get<StationLayerView>('/hyd/stations/map-points'),
    refetchInterval: NHIP_DIEM_DO_MS,
  });
}

/** ⭐ Nhịp nội bộ — xem {@link useStationLayer}. ⛔ Không dùng chung với cổng công khai. */
const NHIP_DIEM_DO_MS = 2 * 60 * 1000;
