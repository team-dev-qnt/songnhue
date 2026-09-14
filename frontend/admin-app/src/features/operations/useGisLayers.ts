import { useQuery } from '@tanstack/react-query';

import { type LopGisVe } from '@/components/dashboard/ConstructionMap';
import { type GisLayerView } from '@/shared/api-types';
import { api } from '@/shared/apiClient';

const GOC = '/ops/gis-layers';

/**
 * Nạp các lớp bản đồ **đang bật** kèm nội dung GeoJSON — CN-02.4 / M2.9.
 *
 * <h2>⛔⛔ Chỉ nạp lớp ĐÃ CÓ TỆP</h2>
 *
 * Một lớp đã khai mà chưa nạp tệp là trạng thái **có thật** (khai trước, nạp sau). Gọi
 * `/noi-dung` cho nó sẽ nhận **404** — tức mỗi lần mở bản đồ là một lỗi trong console và một
 * lượt gọi vô ích. Lọc bằng `coTep` ở đây, ⛔ không bằng cách bắt lỗi.
 *
 * <h2>⚠ Một lượt gọi cho mỗi lớp — và đó là đánh đổi có ý thức</h2>
 *
 * Gộp mọi lớp vào một phản hồi nghe hấp dẫn, nhưng nó buộc máy chủ **nối** vài chục MB GeoJSON
 * thành một chuỗi JSON trong bộ nhớ, trên VPS 2 nhân (T28.35). Tách ra thì trình duyệt tải song
 * song, và một lớp hỏng ⛔ không kéo cả bản đồ theo.
 *
 * <p>⚠ `staleTime` dài: nội dung một lớp bản đồ đổi khi có người nạp tệp mới, ⛔ không đổi theo
 * nhịp số liệu. Nạp lại mỗi 2 phút cùng dashboard là tải lại vài chục MB cho ⛔ không gì cả.
 */
export function useGisLayers(bat = true) {
  const danhSach = useQuery({
    queryKey: ['ops', 'gis-layers', 'dang-bat'],
    queryFn: () => api.get<GisLayerView[]>(GOC, { chiDangBat: true }),
    enabled: bat,
    staleTime: 10 * 60 * 1000,
  });

  // ⛔⛔ `Array.isArray` chứ ⛔ không `?? []`. Bài kiểm của dashboard điều hành đỏ ngay lượt chạy
  //    đầu với `.filter is not a function` — và nó lộ ra một điểm GIÒN THẬT, ⛔ không phải một
  //    trục trặc của đồ gá: một lượt gọi **phụ** (lớp bản đồ) trả về hình dạng ⛔ không mong đợi
  //    sẽ ném trong thân render và **hạ cả dashboard điều hành** — màn hình Trực ban dùng hằng
  //    ngày. Lớp bản đồ là thứ trang trí; nó ⛔ không được quyền làm sập thứ chính.
  const coTep = Array.isArray(danhSach.data) ? danhSach.data.filter((l) => l.coTep) : [];

  const noiDung = useQuery({
    queryKey: ['ops', 'gis-layers', 'noi-dung', coTep.map((l) => l.publicId).join(',')],
    enabled: bat && coTep.length > 0,
    staleTime: 10 * 60 * 1000,
    queryFn: async (): Promise<LopGisVe[]> => {
      const ket = await Promise.all(
        coTep.map(async (view) => {
          try {
            return { view, geojson: await api.get<unknown>(`${GOC}/${view.publicId}/noi-dung`) };
          } catch {
            // ⛔ Một lớp hỏng ⛔ không được kéo cả bản đồ theo — bỏ nó và vẽ những lớp còn lại.
            //   ⚠ Im lặng ở đây là CÓ CHỦ ĐÍCH: người xem bản đồ ⛔ không sửa được lớp hỏng, còn
            //   người quản trị thấy nó ở màn hình *Lớp bản đồ GIS* (cột "Dữ liệu").
            return null;
          }
        }),
      );
      return ket.filter((x): x is LopGisVe => x !== null);
    },
  });

  return noiDung.data ?? [];
}
