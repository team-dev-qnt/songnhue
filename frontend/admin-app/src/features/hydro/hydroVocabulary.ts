import { type PositionRole } from '@/shared/api-types';

/**
 * Nhãn tiếng Việt của vai trò vị trí điểm đo — <b>một chỗ duy nhất</b>.
 *
 * Bảng, biểu mẫu, bộ lọc và cột xuất báo cáo đều lấy chữ từ đây. Chép chữ vào từng màn
 * hình thì tới lúc Công ty đổi cách gọi (chuyện đã xảy ra với "tình hình vận hành") sẽ
 * còn sót một chỗ, và chỗ sót đó không có triệu chứng nào ngoài việc đọc thấy lạ.
 */
export const VAI_TRO_VI_TRI: Record<PositionRole, string> = {
  THUONG_LUU: 'Thượng lưu',
  HA_LUU: 'Hạ lưu',
  BE_HUT: 'Bể hút',
  MN_SONG: 'Mực nước sông',
  MUA: 'Đo mưa',
};

export const VAI_TRO_VI_TRI_OPTIONS = (Object.keys(VAI_TRO_VI_TRI) as PositionRole[]).map(
  (value) => ({ value, label: VAI_TRO_VI_TRI[value] }),
);

/**
 * ⛔ Vai trò được phép KHÔNG liên kết công trình nào.
 *
 * 4/19 điểm đo là trạm thuỷ văn tham chiếu (TV Hà Nội, TV Ba Thá, An Cảnh, TB Hồng Vân).
 * Với chúng, "chưa liên kết" là dữ liệu ĐỦ — hiện cảnh báo thiếu dữ liệu ở đây là dạy
 * người dùng bỏ qua cảnh báo.
 */
export const VAI_TRO_KHONG_CAN_CONG_TRINH: readonly PositionRole[] = ['MN_SONG'];
