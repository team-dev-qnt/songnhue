import { useCallback, useRef, useState } from 'react';

import { api } from '@/shared/apiClient';

/** ⛔ Phải khớp `HydroReportExportHandler.HAN_TAI` ở backend — luật 14, một hạn dùng hai nơi nhớ. */
const HAN_TAI_GIO = 24;

/** Nhịp hỏi lại trạng thái việc nền. Chậm hơn thì người dùng tưởng treo; nhanh hơn là hỏi thừa. */
const NHIP_HOI_MS = 1200;

/** ⛔ Trần số lượt hỏi — một vòng lặp không cận trên giao diện là một tab treo im lặng. */
const TRAN_LUOT_HOI = 150;

type TrangThaiViec = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

/** Thân của `POST /hyd/bao-cao/xuat` — `JobRef` của Core. */
interface ViecVuaDat {
  publicId: string;
}

/** Thân của `GET /jobs/{id}` — `JobDtos.JobStatusView`. ⚠ Trường là `jobId`, ⛔ không phải `publicId`. */
interface TrangThaiViecNen {
  jobId: string;
  status: TrangThaiViec;
  progress: number;
  lastError: string | null;
}

export interface YeuCauXuat {
  loai: 'BC13' | 'BC05' | 'BC12';
  tuNgay: string;
  denNgay: string;
  stationPublicId?: string;
  maLoaiChiSo?: string;
}

/**
 * Đặt một lượt kết xuất báo cáo rồi **chờ việc nền xong và tải tệp về** — T34.7.
 *
 * ⭐⭐ Vì sao giao diện phải tự hỏi lại thay vì bấm-xong-là-có-tệp: bản kết xuất đi qua **hàng đợi
 * việc nền** (pattern P5). BC-12 một tháng của một điểm đo là ~4.500 dòng — giữ request mở suốt lượt
 * dựng ấy là chiếm một luồng và một connection, và proxy cắt ở 60 giây. Backend trả `202` kèm mã
 * việc; hook này biến chuỗi ấy thành **một** thao tác cho người dùng.
 *
 * ⛔ **Không** `window.open` đường dẫn tải: endpoint đòi `Authorization`, và một tab mới ⛔ không
 * mang theo header ấy — nó sẽ trả 401 và người dùng thấy một tab trắng. Tải qua `api.getTep` rồi
 * dựng blob.
 *
 * ⚠ Vòng chờ có **trần** và có đường **dừng**: một `while` không cận trên giao diện là một tab quay
 * mãi mà ⛔ không có thông điệp nào; và người dùng đóng ngăn kéo giữa chừng thì vòng chờ phải biết
 * dừng, ⛔ không tiếp tục gọi máy chủ cho một màn hình đã đóng.
 */
export function useXuatBaoCao() {
  const [dangCho, setDangCho] = useState(false);
  const [loi, setLoi] = useState<string | null>(null);
  const huy = useRef(false);

  const xuat = useCallback(async (yeuCau: YeuCauXuat) => {
    setLoi(null);
    setDangCho(true);
    huy.current = false;
    try {
      const viec = await api.post<ViecVuaDat>('/hyd/bao-cao/xuat', yeuCau);

      let xong = false;
      for (let i = 0; i < TRAN_LUOT_HOI && !huy.current; i++) {
        await new Promise((r) => setTimeout(r, NHIP_HOI_MS));
        const tt = await api.get<TrangThaiViecNen>(`/jobs/${viec.publicId}`);
        if (tt.status === 'SUCCEEDED') {
          xong = true;
          break;
        }
        if (tt.status === 'FAILED' || tt.status === 'CANCELLED') {
          // ⛔ Nói ra trạng thái THẬT: "xuất hỏng" chung chung để lại người dùng không biết nên bấm
          //   lại hay đi báo quản trị. FAILED là hỏng khi dựng; CANCELLED là có người dừng nó.
          throw new Error(
            `Việc kết xuất kết thúc ở trạng thái ${tt.status}` +
              (tt.lastError ? ` — ${tt.lastError}` : ''),
          );
        }
      }
      if (!xong) {
        throw new Error(
          `Việc kết xuất chưa xong sau ${Math.round((TRAN_LUOT_HOI * NHIP_HOI_MS) / 1000)} giây — ` +
            'xem Quản trị › Việc nền để biết nó đang ở đâu',
        );
      }

      // ⚠ Tải qua axios (đã mang header xác thực), ⛔ không mở tab mới.
      const { blob, tenTep } = await api.getTep(`/hyd/bao-cao/tai/${viec.publicId}`);
      const ten = tenTep ?? `${yeuCau.loai}.csv`;

      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = ten;
      a.click();
      URL.revokeObjectURL(url);
      return ten;
    } catch (e) {
      setLoi(e instanceof Error ? e.message : 'Không kết xuất được báo cáo');
      throw e;
    } finally {
      setDangCho(false);
    }
  }, []);

  const dungCho = useCallback(() => {
    huy.current = true;
    setDangCho(false);
  }, []);

  return { xuat, dangCho, loi, dungCho, hanTaiGio: HAN_TAI_GIO };
}
