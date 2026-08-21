/**
 * Design tokens — **nguồn duy nhất** của màu sắc và khoảng cách (conventions.md §3).
 *
 * Ba nơi tiêu thụ: theme AntD (admin-app), theme ECharts (biểu đồ Phase 2), và Tailwind
 * config của public-web.
 *
 * <h3>Vì sao là một workspace riêng, không nằm trong admin-app</h3>
 *
 * Hai ứng dụng FE **ngang hàng nhau**. Nếu tokens ở trong admin-app thì cổng thông tin
 * công khai phải phụ thuộc vào ứng dụng quản trị nội bộ để lấy màu — quan hệ ngược, và
 * kéo theo cả mã nguồn admin-app vào bối cảnh build của public-web. Một gói nhỏ mà cả
 * hai cùng phụ thuộc thì quan hệ đúng chiều và mỗi image chỉ tải phần nó cần.
 *
 * ⛔ **Cấm khai màu tại chỗ trong page/component.** Không phải vì "cho gọn" — mà vì
 * năm màu trạng thái dưới đây mang **nghĩa nghiệp vụ**: đỏ = sự cố đang mở, vàng = cảnh
 * báo ngưỡng, xám = trạm mất tín hiệu (chốt G3). Một màn hình tự chọn sắc đỏ khác là
 * người trực đọc sai mức nghiêm trọng, chứ không phải lệch thẩm mỹ.
 *
 * Gói này **không có bước biên dịch**: `exports` trỏ thẳng vào `.ts` và mỗi app tự
 * transpile (Vite làm sẵn; Next cần khai `transpilePackages`). Thêm một bước build chỉ
 * để phát ra vài hằng số là thêm một chỗ quên chạy lại.
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
  /** Nền nhẹ brand — dùng cho hover state trên card, row highlight */
  primaryLight: '#e6f4ff',
  /** Gradient from (đậm hơn primary một chút) — login bg, hero section */
  primaryGradientFrom: '#003eb3',
  /** Gradient to (nhạt hơn primary) */
  primaryGradientTo: '#1677ff',
} as const;

export const neutralColors = {
  textBase: '#1f1f1f',
  textSecondary: '#595959',
  border: '#d9d9d9',
  bgLayout: '#f0f2f5',
  bgContainer: '#ffffff',
  /** Nền thanh bên — tối để phân tách vùng điều hướng khỏi vùng nội dung */
  bgSider: '#001529',
  /** Nền hover nhẹ — danh sách, table row */
  bgHover: '#fafafa',
  /** Màu bóng đổ — dùng trong box-shadow */
  shadowColor: 'rgba(0, 0, 0, 0.08)',
} as const;

/**
 * Kích thước dùng chung.
 *
 * `fontFamily` đặt tường minh và có `Noto Sans` đứng đầu: font Google hỗ trợ đầy đủ
 * dấu tiếng Việt, trọng lượng đa dạng, và hiển thị nhất quán trên mọi nền tảng.
 * `system-ui` đứng sau làm lưới an toàn khi CDN không gọi được.
 */
export const sizing = {
  fontFamily:
    "'Noto Sans', system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
  fontSize: 14,
  borderRadius: 6,
  headerHeight: 56,
  siderWidth: 248,
  siderCollapsedWidth: 64,
} as const;

/**
 * Shadow — ba cấp độ nổi. Giá trị lấy từ Material Design elevation nhưng nhẹ hơn —
 * hệ thống quản trị cần depth vừa phải, không phải floating card.
 */
export const shadow = {
  /** Card mặc định, input */
  sm: '0 1px 3px 0 rgba(0, 0, 0, 0.08), 0 1px 2px -1px rgba(0, 0, 0, 0.06)',
  /** Card hover, dropdown */
  md: '0 4px 12px 0 rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.06)',
  /** Modal, drawer, sticky header */
  lg: '0 8px 24px 0 rgba(0, 0, 0, 0.12), 0 4px 8px -4px rgba(0, 0, 0, 0.08)',
} as const;

/**
 * Transition — tốc độ và easing chuẩn. Xem `docs/ui-styles.md` §5.2.
 */
export const transition = {
  /** Đổi màu, opacity */
  fast: '150ms cubic-bezier(0.4, 0, 0.2, 1)',
  /** Hover state, focus ring */
  normal: '200ms cubic-bezier(0.4, 0, 0.2, 1)',
  /** Card lift, dropdown, menu mở */
  smooth: '300ms cubic-bezier(0.4, 0, 0.2, 1)',
  /** Page fade-in, modal enter */
  slow: '500ms cubic-bezier(0.4, 0, 0.2, 1)',
  /** Easing mặc định */
  easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
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
 * Gói tokens không phụ thuộc vào thư viện vẽ đồ thị: nó chỉ mô tả *màu và chữ trông
 * thế nào*, còn ai vẽ là chuyện của app. admin-app nhận object này qua
 * `echarts.registerTheme('songnhue', echartsTheme)` ở `components/charts/setup.ts`.
 *
 * ⛔ **Cấm khai bảng màu thứ hai trong mã biểu đồ** (T23.1). Hai bảng màu thì badge
 * trạng thái trên bảng và cột trên biểu đồ sẽ lệch nhau — và không ai coi đó là lỗi
 * để đi sửa, vì mỗi màn hình nhìn riêng đều "trông ổn".
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

/**
 * Bảng màu chế độ màn hình lớn phòng điều hành — CN-02.5, wall mode.
 *
 * <h3>Vì sao nền tối, và vì sao KHÔNG đảo luôn năm màu trạng thái</h3>
 *
 * Màn hình treo tường sáng suốt ngày trong phòng trực; nền trắng 85 inch là nguồn
 * chói liên tục vào mắt người ngồi dưới. Nhưng **năm màu trạng thái giữ nguyên**: đỏ
 * vẫn là sự cố, vàng vẫn là cảnh báo. Đổi sắc độ theo nền là tạo ra hai bảng nghĩa cho
 * cùng một hệ thống, và người trực đọc màn hình tường rồi mở máy tính tra tiếp sẽ thấy
 * hai màu khác nhau cho cùng một công trình.
 *
 * Chỉ **nền và chữ** đảo — đó là phần không mang nghĩa nghiệp vụ.
 */
export const wallColors = {
  bg: '#0b1220',
  /** Nền thẻ, nổi hơn nền trang một bậc để phân tách khối mà không cần viền dày. */
  surface: '#111c2e',
  border: '#22314a',
  textBase: '#f0f4fa',
  textSecondary: '#9aabc4',
} as const;

/**
 * Theme ECharts cho wall mode.
 *
 * ⚠ Sinh **từ** `echartsTheme` chứ không chép lại: mọi thứ mang nghĩa (dãy màu chuỗi
 * dữ liệu, font) phải là cùng một nguồn, chỉ những giá trị phụ thuộc nền mới ghi đè.
 * Chép cả object ra thì lần sau ai đổi màu chuỗi dữ liệu sẽ đổi đúng một trong hai bản.
 */
export const echartsWallTheme = {
  ...echartsTheme,
  textStyle: { ...echartsTheme.textStyle, color: wallColors.textBase },
  title: { textStyle: { color: wallColors.textBase, fontWeight: 600 } },
  legend: { textStyle: { color: wallColors.textSecondary } },
  categoryAxis: {
    ...echartsTheme.categoryAxis,
    axisLine: { lineStyle: { color: wallColors.border } },
    axisLabel: { color: wallColors.textSecondary },
  },
  valueAxis: {
    ...echartsTheme.valueAxis,
    axisLabel: { color: wallColors.textSecondary },
    splitLine: { lineStyle: { color: wallColors.border, type: 'dashed' } },
  },
} as const;
