import { QueryClient } from '@tanstack/react-query';

import { ApiClientError } from '@/shared/apiClient';

/**
 * Cấu hình TanStack Query dùng chung.
 *
 * <h3>Không thử lại những lỗi không bao giờ tự khỏi</h3>
 *
 * Mặc định của thư viện là thử lại 3 lần. Với `AUTH-3001` (thiếu quyền) hay `SYS-0004`
 * (không tìm thấy), ba lượt thử chỉ làm người dùng chờ lâu gấp ba rồi vẫn nhận đúng lỗi
 * đó — mà với `SYS-0002` (chạm hạn mức) thì còn tệ hơn: thử lại chính là thứ đang bị
 * chặn, và nó đẩy tài khoản sâu hơn vào hạn mức.
 *
 * Chỉ thử lại thứ có lý do tự khỏi: lỗi mạng và lỗi 5xx nhất thời.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Màn hình quản trị hay để mở cả ngày; tự nạp lại khi quay lại tab là hợp lý.
      refetchOnWindowFocus: true,
      retry: (failureCount, error) => {
        if (failureCount >= 2) {
          return false;
        }
        if (!(error instanceof ApiClientError)) {
          return false;
        }
        if (error.code === 'NETWORK') {
          return true;
        }
        return error.httpStatus !== null && error.httpStatus >= 500;
      },
    },
    mutations: {
      // Thao tác ghi KHÔNG bao giờ tự thử lại: gửi hai lần một lệnh khôi phục dữ liệu
      // hay một thông báo toàn công ty là hậu quả thật, còn lỗi thì người dùng bấm lại được.
      retry: false,
    },
  },
});
