import { BarChart, GaugeChart, LineChart, PieChart } from 'echarts/charts';
import {
  DatasetComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { echartsTheme, echartsWallTheme } from 'design-tokens';

/**
 * Nạp ECharts theo **kiểu chọn lọc** — T23.3.
 *
 * <h3>Vì sao không `import * as echarts from 'echarts'`</h3>
 *
 * Bó đầy đủ mang theo mọi loại biểu đồ (bản đồ nhiệt, cây, sankey, đồ thị 3D…), bộ vẽ SVG,
 * và toàn bộ nhóm component tương tác — vài trăm KB cho một màn hình dùng đúng bốn loại.
 * Bó này chỉ nạp thứ có người dùng; thêm một loại biểu đồ mới về sau là thêm **một dòng
 * import ở đây**, và dòng đó nhìn thấy được trong diff.
 *
 * ⚠ Quên đăng ký một component thì ECharts **không ném lỗi** — nó vẽ ra một khung trống.
 * Đó là lý do `BaseChart` có trạng thái rỗng tường minh: một biểu đồ trắng trơn phải là
 * câu "không có dữ liệu", không được là triệu chứng của việc thiếu import.
 *
 * <h3>Bộ vẽ Canvas, không phải SVG</h3>
 *
 * Wall mode 4K vẽ lại toàn bộ biểu đồ mỗi chu kỳ làm mới và chạy liên tục nhiều giờ.
 * Canvas giữ số lượng nút DOM không đổi; SVG sinh một nút cho mỗi phần tử đồ hoạ, và ở
 * độ phân giải đó là hàng nghìn nút phải dựng lại mỗi lượt.
 */
echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GaugeChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DatasetComponent,
  CanvasRenderer,
]);

export const THEME_SANG = 'songnhue';
export const THEME_TUONG = 'songnhue-wall';

// Đăng ký một lần lúc nạp module. `registerTheme` gọi lại với cùng tên là ghi đè, không
// lỗi — nhưng module ES chỉ chạy một lượt nên chuyện đó không xảy ra.
echarts.registerTheme(THEME_SANG, echartsTheme);
echarts.registerTheme(THEME_TUONG, echartsWallTheme);

export { echarts };
