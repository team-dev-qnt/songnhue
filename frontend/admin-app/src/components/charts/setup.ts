import { BarChart, GaugeChart, LineChart, PieChart, TreeChart } from 'echarts/charts';
import {
  DatasetComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer, SVGRenderer } from 'echarts/renderers';
import { echartsTheme, echartsWallTheme } from '@songnhue/design-tokens';

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
 * <h3>Bộ vẽ Canvas cho MÀN HÌNH, SVG chỉ cho lượt KẾT XUẤT</h3>
 *
 * Wall mode 4K vẽ lại toàn bộ biểu đồ mỗi chu kỳ làm mới và chạy liên tục nhiều giờ.
 * Canvas giữ số lượng nút DOM không đổi; SVG sinh một nút cho mỗi phần tử đồ hoạ, và ở
 * độ phân giải đó là hàng nghìn nút phải dựng lại mỗi lượt.
 *
 * ⚠⚠ `SVGRenderer` thêm 14/09/2026 cho **CN-04.1 xuất sơ đồ tổ chức** — và nó ⛔ **không**
 * đổi cách vẽ của một biểu đồ nào đang chạy: `BaseChart` gọi `echarts.init` ⛔ không truyền
 * `renderer`, nên mặc định vẫn là Canvas. Bộ vẽ SVG chỉ được dùng ở **một** chỗ: một thực
 * thể tạm, ngoài màn hình, sống đúng một lượt `renderToSVGString()` rồi `dispose()`
 * (`xuatSoDo.ts`). Ghi ra đây vì câu ở trên trước đó khẳng định kho **không** nạp SVG —
 * một chú thích nói quá nguy hiểm hơn không có chú thích.
 *
 * <h3>`TreeChart` — thêm 14/09/2026 (CN-04.1)</h3>
 *
 * Sơ đồ tổ chức là loại biểu đồ thứ năm, và nó đi đúng con đường mà đoạn trên mô tả: **một
 * dòng import, nhìn thấy được trong diff**. ⚠ Quên dòng ấy thì ECharts ⛔ không ném lỗi —
 * nó vẽ một khung trắng, đúng thứ `BaseChart.empty` sinh ra để phân biệt.
 */
echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GaugeChart,
  TreeChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DatasetComponent,
  CanvasRenderer,
  SVGRenderer,
]);

export const THEME_SANG = 'songnhue';
export const THEME_TUONG = 'songnhue-wall';

// Đăng ký một lần lúc nạp module. `registerTheme` gọi lại với cùng tên là ghi đè, không
// lỗi — nhưng module ES chỉ chạy một lượt nên chuyện đó không xảy ra.
echarts.registerTheme(THEME_SANG, echartsTheme);
echarts.registerTheme(THEME_TUONG, echartsWallTheme);

export { echarts };
