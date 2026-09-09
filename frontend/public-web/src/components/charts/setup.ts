import { LineChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TooltipComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

import { echartsTheme } from 'design-tokens';

/**
 * Đăng ký ECharts cho **cổng công khai** — WS-45.
 *
 * <h2>⛔⛔ `MarkLineComponent` là thứ thiếu thì ECharts IM LẶNG</h2>
 *
 * Ba đường ngang BĐ1/BĐ2/BĐ3 của §7.1 đi bằng `markLine`. Quên đăng ký component ấy thì ECharts
 * ⛔ **không ném, ⛔ không cảnh báo** — nó chỉ đơn giản ⛔ không vẽ. Biểu đồ trông hoàn chỉnh, và
 * cái thiếu là đúng thứ nói cho người đọc biết mực nước đã vượt báo động hay chưa.
 *
 * <p>⚠ Khác với `toolbox`/`dataZoom`: hai cái ấy thiếu thì ECharts **in `console.error`** kèm đúng
 * dòng import cần thêm. Nên chỉ `markLine` mới cần một dòng cảnh báo ở đây.
 *
 * <h2>⛔ Vì sao đăng ký CHỌN LỌC chứ ⛔ không `import * as echarts from 'echarts'`</h2>
 *
 * Bản đầy đủ kéo theo mọi loại biểu đồ, mọi component, mọi renderer — trên một trang **cổng công
 * khai** đo bằng NFR-02 (*trang chủ < 3 giây*, DOD1.17 còn treo). Ở đây cần đúng một loại biểu đồ
 * đường, nên nhập đúng phần ấy.
 *
 * <p>⛔ **KHÔNG** đăng ký `ToolboxComponent`: nút xuất PNG dựng bằng `getDataURL()` gọi tay (xem
 * `BieuDoDienBien`), vì `toolbox` của ECharts đặt nút **bên trong** vùng vẽ với văn bản tiếng Anh
 * ⛔ không đổi được sang tiếng Việt một cách gọn, và nó ⛔ không xuất được CSV — thứ §7.3 đòi cùng
 * lúc.
 */
echarts.use([
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  MarkLineComponent,
  CanvasRenderer,
]);

/** ⚠ Cùng một tên chủ đề với `admin-app` — hai cổng vẽ cùng một bộ màu (`design-tokens`). */
export const THEME = 'songnhue';

echarts.registerTheme(THEME, echartsTheme);

export { echarts };
