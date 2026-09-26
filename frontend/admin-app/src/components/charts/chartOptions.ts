import { statusColors, type StatusColorKey } from '@songnhue/design-tokens';

import { type StatusVocabulary } from '@/components/business/statusVocabulary';

/**
 * Dựng cấu hình ECharts — **hàm thuần**, tách hẳn khỏi component.
 *
 * <h3>Vì sao tách ra một file riêng</h3>
 *
 * ECharts vẽ lên `<canvas>`, mà jsdom không có bộ vẽ canvas — nên mọi phép khẳng định về
 * *nội dung* biểu đồ nếu nằm trong component sẽ không kiểm được ở CI, và thứ duy nhất
 * kiểm được là "component không ném lỗi". Câu hỏi đáng kiểm lại là những câu ở đây: lát
 * "Sự cố" có đúng màu đỏ của badge không, cột có đúng thứ tự không, không có dữ liệu thì
 * có tự bịa ra một mục nào không.
 *
 * ⛔ **Không hàm nào ở đây được viết một mã màu.** Màu lấy từ `design-tokens` qua bộ từ
 * vựng trạng thái — cùng nguồn với `StatusBadge`. Đây là điều kiện của T23.1: hai bảng
 * màu thì cột trên biểu đồ và badge trên bảng sẽ lệch nhau, và không ai coi đó là lỗi.
 */

/** Một nhóm số liệu từ API thống kê. */
export interface Bucket {
  key: string;
  label: string;
  count: number;
}

/** Nhãn hiển thị: ưu tiên bộ từ vựng, rơi về nhãn backend gửi kèm. */
export function nhanCua(bucket: Bucket, tuVung?: StatusVocabulary): string {
  return tuVung?.[bucket.key]?.label ?? bucket.label ?? bucket.key;
}

/**
 * Màu của một nhóm.
 *
 * Không có trong bộ từ vựng thì trả `undefined` để ECharts dùng dãy màu của theme —
 * chứ **không** rơi về một màu trạng thái nào. Rơi về `normal` thì một mã lạ (VD một
 * loại công trình thêm sau) sẽ hiện màu xanh "bình thường" như thể đó là một khẳng định.
 */
export function mauCua(bucket: Bucket, tuVung?: StatusVocabulary): string | undefined {
  const khoa: StatusColorKey | undefined = tuVung?.[bucket.key]?.color;
  return khoa ? statusColors[khoa] : undefined;
}

/** Biểu đồ cột ngang — dùng cho nhóm có nhãn dài (tên đơn vị). */
export function optionCotNgang(buckets: Bucket[], tuVung?: StatusVocabulary) {
  // Sắp tăng dần vì ECharts vẽ trục y từ dưới lên: không đảo thì nhóm lớn nhất nằm đáy,
  // ngược với cách người ta đọc một bảng xếp hạng.
  const theoThuTu = [...buckets].sort((a, b) => a.count - b.count);
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 24, top: 16, bottom: 8, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: theoThuTu.map((b) => nhanCua(b, tuVung)),
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 28,
        data: theoThuTu.map((b) => ({
          value: b.count,
          name: nhanCua(b, tuVung),
          itemStyle: { color: mauCua(b, tuVung) },
          bucketKey: b.key,
        })),
        label: { show: true, position: 'right' },
      },
    ],
  };
}

/** Biểu đồ cột dọc — nhóm ít mục, nhãn ngắn (loại công trình, cấp quản lý). */
export function optionCotDoc(buckets: Bucket[], tuVung?: StatusVocabulary) {
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 8, top: 16, bottom: 8, containLabel: true },
    xAxis: { type: 'category', data: buckets.map((b) => nhanCua(b, tuVung)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        barMaxWidth: 48,
        data: buckets.map((b) => ({
          value: b.count,
          name: nhanCua(b, tuVung),
          itemStyle: { color: mauCua(b, tuVung) },
          bucketKey: b.key,
        })),
        label: { show: true, position: 'top' },
      },
    ],
  };
}

/**
 * Biểu đồ tròn — dùng cho phân bố theo trạng thái.
 *
 * Dạng vành khuyên chứ không phải bánh đặc: phần lỗ giữa để đặt tổng số, và mắt người
 * so sánh độ dài cung tốt hơn so sánh diện tích quạt.
 */
export function optionTron(buckets: Bucket[], tuVung?: StatusVocabulary) {
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        avoidLabelOverlap: true,
        label: { show: false },
        data: buckets.map((b) => ({
          value: b.count,
          name: nhanCua(b, tuVung),
          itemStyle: { color: mauCua(b, tuVung) },
          bucketKey: b.key,
        })),
      },
    ],
  };
}

/**
 * Đồng hồ tỉ lệ phần trăm.
 *
 * <p>⚠ Ba vùng màu đi theo **hướng tốt lên**: càng gần 100% càng xanh. Dùng cho những tỉ
 * lệ mà cao là tốt (đã số hoá toạ độ, đã có hồ sơ). Tỉ lệ mà cao là xấu thì phải đảo
 * `nguoc = true`, chứ không được vẽ bằng đúng hàm này rồi giải thích bằng nhãn — người
 * đọc lướt qua chỉ thấy màu.
 */
export function optionDongHo(phanTram: number, nhan: string, nguoc = false) {
  const chan = Math.min(100, Math.max(0, Number.isFinite(phanTram) ? phanTram : 0));
  const dai: [number, string][] = nguoc
    ? [
        [0.5, statusColors.normal],
        [0.8, statusColors.warning],
        [1, statusColors.danger],
      ]
    : [
        [0.5, statusColors.danger],
        [0.8, statusColors.warning],
        [1, statusColors.normal],
      ];

  return {
    series: [
      {
        type: 'gauge',
        min: 0,
        max: 100,
        splitNumber: 4,
        startAngle: 200,
        endAngle: -20,
        center: ['50%', '62%'],
        radius: '95%',
        progress: { show: false },
        axisLine: { lineStyle: { width: 10, color: dai } },
        splitLine: { length: 6, lineStyle: { width: 1.5, color: '#fff' } },
        axisTick: { show: false },
        axisLabel: { distance: 8, fontSize: 10, color: 'inherit' },
        pointer: { width: 3.5, length: '52%', itemStyle: { color: 'auto' } },
        anchor: { show: true, showAbove: true, size: 6, itemStyle: { borderWidth: 2 } },
        detail: {
          valueAnimation: false,
          formatter: '{value}%',
          fontSize: 18,
          fontWeight: 700,
          offsetCenter: [0, '30%'],
          color: 'inherit',
        },
        title: {
          offsetCenter: [0, '70%'],
          fontSize: 12,
          color: 'inherit',
        },
        data: [{ value: Math.round(chan), name: nhan }],
      },
    ],
  };
}

/**
 * Biểu đồ đường theo thời gian.
 *
 * <p>⚠ **Phase 1 chưa có nơi gọi hàm này**, và đó là điều được ghi ra chứ không giấu đi:
 * chuỗi thời gian đầu tiên của hệ thống là mực nước 24 giờ (MOD-03, Phase 2). Giữ nó ở
 * đây vì nó là hàm thuần và được kiểm bằng bài kiểm riêng — phần rủi ro thật (dựng thực
 * thể ECharts, đổi kích thước, huỷ) nằm ở `BaseChart` và đã có hai loại biểu đồ khác đi
 * qua hằng ngày. ⛔ Nếu Phase 2 đến mà vẫn không ai gọi thì phải **xoá**, không phải giữ.
 */
export function optionDuong(
  moc: string[],
  chuoi: { ten: string; giaTri: (number | null)[]; mauKhoa?: StatusColorKey }[],
) {
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, data: chuoi.map((c) => c.ten) },
    grid: { left: 8, right: 16, top: 16, bottom: 32, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: moc },
    yAxis: { type: 'value', scale: true },
    series: chuoi.map((c) => ({
      name: c.ten,
      type: 'line',
      smooth: false,
      showSymbol: false,
      // `connectNulls: false` là chủ ý: khoảng trống dữ liệu phải nhìn thấy được. Nối
      // liền qua chỗ mất tín hiệu là vẽ ra một đoạn số liệu chưa từng được đo.
      connectNulls: false,
      data: c.giaTri,
      lineStyle: c.mauKhoa ? { color: statusColors[c.mauKhoa] } : undefined,
      itemStyle: c.mauKhoa ? { color: statusColors[c.mauKhoa] } : undefined,
    })),
  };
}

/**
 * Biểu đồ đường **tô theo CHIỀU** — mỗi đoạn xanh hay đỏ tuỳ số liệu đang lên hay xuống.
 *
 * <h2>⛔⛔ Nước LÊN = ĐỎ, ⛔ phải quy ước chứng khoán (chốt QuanTran 26/09/2026)</h2>
 *
 * Ở bảng giá chứng khoán xanh = tăng. Ở đây **ngược lại**, và đó là chủ ý: với mực nước thì
 * *lên* là phía nguy hiểm, còn cả hệ này đã dùng `statusColors.danger` cho cảnh báo ở GIS,
 * dashboard và bảng ngưỡng. Dạy người trực ban hai nghĩa ngược nhau cho cùng một màu, trên
 * cùng một màn hình, là cách chắc chắn để một cảnh báo thật bị đọc nhầm.
 *
 * <h2>⛔ Vì sao là hàm RIÊNG, ⛔ phải nới `optionDuong`</h2>
 *
 * `optionDuong` có **người dùng thứ hai**: `features/hr/BaoCaoNhanSuPage.tsx` (Tuyển mới /
 * Nghỉ việc / Điều động). Nới nó là lặng lẽ sơn lại biểu đồ nhân sự theo một "chiều" ⛔ có
 * nghĩa ở đó — *nghỉ việc giảm* tô đỏ hay xanh?
 *
 * <h2>⛔⛔ Ba chuỗi CHỒNG NHAU, ⛔ phải `visualMap` — và đây là phép ĐO, ⛔ phải khẩu vị</h2>
 *
 * Cách hiển nhiên là `visualMap` theo một chiều phái sinh (delta). **Đo 26/09/2026 trên chính
 * ECharts của kho này: nó ⛔ chạy**, theo hai kiểu khác nhau và cả hai đều tệ:
 *
 * <ul>
 *   <li>`visualMap` theo chiều **trục** (giá trị) ⇒ `LineView.getVisualGradient` **NÉM**
 *       `Cannot read properties of undefined (reading 'coord')`;
 *   <li>`visualMap` theo chiều **⛔ phải trục** (delta) ⇒ **⛔ hiệu lực gì** — nét vẫn mang màu
 *       mặc định của bảng màu. Lý do: ECharts chỉ tô được *nét* qua đường gradient theo trục; ở
 *       chiều khác nó rơi về tô **điểm**, mà `showSymbol: false` thì ⛔ có điểm nào để tô.
 * </ul>
 *
 * ⇒ Dùng đúng khuôn mà kho đã trả giá và đang chạy ở cổng công khai (`BieuDoDienBien.tsx`): một
 * chuỗi **nền vô hình** mang tooltip + chú giải, cộng các chuỗi **phủ** mỗi chuỗi một màu phẳng.
 *
 * <h2>Vì sao mỗi đoạn phải có ĐỦ HAI đầu</h2>
 *
 * Một đoạn nối mốc *i−1* → *i*. Muốn tô nó thì chuỗi phủ phải có giá trị ở **cả hai** mốc — nên
 * mốc quay đầu xuất hiện ở **hai** chuỗi cùng lúc, và đó chính là chỗ hai màu gặp nhau.
 *
 * ⚠ Đoạn **PHẲNG** (delta đúng 0) ⛔ phải "lên" mà cũng ⛔ phải "xuống" ⇒ chuỗi thứ ba, màu xám.
 * Bỏ nó đi là để một quãng mực nước đứng yên **biến mất khỏi hình** — trông y hệt mất tín hiệu.
 *
 * ⚠ `connectNulls: false` ở mọi chuỗi. Một đoạn nối qua quãng mất tín hiệu còn sinh ra một "chiều"
 * ⛔ ai đo được — tệ hơn cả việc ⛔ vẽ gì.
 */
export function optionDuongXuHuong(moc: string[], ten: string, giaTri: (number | null)[]) {
  /** Giữ mốc *i* trong chuỗi phủ khi đoạn kề nó (trái hoặc phải) đi đúng chiều `hop`. */
  const phu = (hop: (delta: number) => boolean) =>
    giaTri.map((v, i) => {
      if (v === null) return null;
      const truoc = i > 0 ? giaTri[i - 1] : null;
      const sau = i + 1 < giaTri.length ? giaTri[i + 1] : null;
      const doanTrai = truoc === null || truoc === undefined ? null : v - truoc;
      const doanPhai = sau === null || sau === undefined ? null : sau - v;
      const thuoc = (d: number | null) => d !== null && hop(d);
      return thuoc(doanTrai) || thuoc(doanPhai) ? v : null;
    });

  const chung = { type: 'line', smooth: false, showSymbol: false, connectNulls: false } as const;

  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, data: [ten] },
    grid: { left: 8, right: 16, top: 16, bottom: 32, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: moc },
    yAxis: { type: 'value', scale: true },
    series: [
      {
        // ⭐ Nền VÔ HÌNH: nó là thứ duy nhất mang tooltip và chú giải, nên người dùng thấy MỘT
        //   đường mực nước chứ ⛔ phải ba chuỗi rời. Ba chuỗi phủ tắt tooltip để một mốc ⛔ hiện
        //   ba dòng giống nhau.
        ...chung,
        name: ten,
        data: giaTri,
        lineStyle: { opacity: 0 },
        itemStyle: { color: statusColors.unknown },
      },
      {
        ...chung,
        name: 'Đang lên',
        data: phu((d) => d > 0),
        lineStyle: { color: statusColors.danger },
        itemStyle: { color: statusColors.danger },
        tooltip: { show: false },
      },
      {
        ...chung,
        name: 'Đang xuống',
        data: phu((d) => d < 0),
        lineStyle: { color: statusColors.normal },
        itemStyle: { color: statusColors.normal },
        tooltip: { show: false },
      },
      {
        ...chung,
        name: 'Không đổi',
        data: phu((d) => d === 0),
        lineStyle: { color: statusColors.unknown },
        itemStyle: { color: statusColors.unknown },
        tooltip: { show: false },
      },
    ],
  };
}
