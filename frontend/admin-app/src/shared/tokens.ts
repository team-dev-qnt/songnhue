/**
 * Design tokens — **nguồn duy nhất** của màu sắc và khoảng cách (conventions.md §3).
 *
 * Ba nơi tiêu thụ file này: theme AntD (admin-app), theme ECharts (biểu đồ Phase 2),
 * và Tailwind config của public-web (WS-9 import lại đúng file này).
 *
 * ⛔ **Cấm khai màu tại chỗ trong page/component.** Không phải vì "cho gọn" — mà vì
 * năm màu trạng thái dưới đây mang **nghĩa nghiệp vụ**: đỏ = sự cố đang mở, vàng = cảnh
 * báo ngưỡng, xám = trạm mất tín hiệu (chốt G3). Một màn hình tự chọn sắc đỏ khác là
 * người trực đọc sai mức nghiêm trọng, chứ không phải lệch thẩm mỹ.
 */

/** Năm màu trạng thái hệ thống — conventions.md §3, `function-spec.md` §4 (GIS + dashboard). */
export const statusColors = {
  /** Bình thường / đã duyệt / hoạt động */
  normal: '#52c41a',
  /** Cảnh báo — vượt ngưỡng mức 1, chờ duyệt, sắp hết hạn */
  warning: '#faad14',
  /** Nguy cấp — sự cố đang mở, vượt ngưỡng mức cao, thao tác hỏng */
  danger: '#f5222d',
  /** Không xác định — **trạm thủy văn mất tín hiệu** (G3), bản ghi nghi ngờ, đã ngừng */
  unknown: '#8c8c8c',
  /** Ngừng hoạt động / đã xoá mềm */
  inactive: '#262626',
} as const;

export type StatusColorKey = keyof typeof statusColors;

/** Màu thương hiệu. Xanh nước — công ty thuỷ lợi, giữ trung tính để không đấu với 5 màu trạng thái. */
export const brandColors = {
  primary: '#0958d9',
  primaryHover: '#1677ff',
  link: '#0958d9',
  info: '#1677ff',
} as const;

export const neutralColors = {
  textBase: '#1f1f1f',
  textSecondary: '#595959',
  border: '#d9d9d9',
  bgLayout: '#f0f2f5',
  bgContainer: '#ffffff',
  /** Nền thanh bên — tối để phân tách vùng điều hướng khỏi vùng nội dung */
  bgSider: '#001529',
} as const;

/**
 * Kích thước dùng chung.
 *
 * `fontFamily` đặt tường minh và có `system-ui` đứng đầu: chuỗi mặc định của AntD
 * không phủ hết dấu tiếng Việt trên vài máy Windows đời cũ, chữ bị rơi về font
 * thay thế và cao thấp lộn xộn giữa các dòng có dấu.
 */
export const sizing = {
  fontFamily:
    "system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif",
  fontSize: 14,
  borderRadius: 6,
  headerHeight: 56,
  siderWidth: 248,
  siderCollapsedWidth: 64,
} as const;

/**
 * Ánh xạ mức nghiêm trọng → khoá màu.
 *
 * Dùng chung cho `StatusBadge`, `ThresholdValue`, thông báo in-app và (Phase 2) lớp GIS,
 * để cùng một mức nghiêm trọng không hiện hai màu khác nhau ở hai màn hình.
 */
export const severityColorKey = {
  INFO: 'normal',
  WARNING: 'warning',
  DANGER: 'danger',
} as const satisfies Record<string, StatusColorKey>;

/**
 * Theme ECharts — khai bằng object thuần, **không import `echarts`**.
 *
 * Phase 0 chưa có biểu đồ nào; kéo cả thư viện vẽ đồ thị vào bundle chỉ để giữ một
 * bảng màu là trả giá bằng dung lượng tải mà chẳng ai dùng. Phase 2 nhận object này
 * qua `echarts.registerTheme('songnhue', echartsTheme)` — không phải sửa gì ở đây.
 */
export const echartsTheme = {
  color: [
    brandColors.primary,
    statusColors.normal,
    statusColors.warning,
    statusColors.danger,
    statusColors.unknown,
  ],
  backgroundColor: 'transparent',
  textStyle: { fontFamily: sizing.fontFamily, fontSize: sizing.fontSize },
  title: { textStyle: { color: neutralColors.textBase, fontWeight: 600 } },
  legend: { textStyle: { color: neutralColors.textSecondary } },
  grid: { containLabel: true, left: 12, right: 12, top: 40, bottom: 12 },
  categoryAxis: {
    axisLine: { lineStyle: { color: neutralColors.border } },
    axisLabel: { color: neutralColors.textSecondary },
    splitLine: { show: false },
  },
  valueAxis: {
    axisLine: { show: false },
    axisLabel: { color: neutralColors.textSecondary },
    splitLine: { lineStyle: { color: neutralColors.border, type: 'dashed' } },
  },
} as const;
