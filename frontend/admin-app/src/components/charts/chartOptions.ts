import { statusColors, type StatusColorKey } from 'design-tokens';

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
        startAngle: 200,
        endAngle: -20,
        progress: { show: false },
        axisLine: { lineStyle: { width: 14, color: dai } },
        axisLabel: { distance: 18, fontSize: 10 },
        pointer: { width: 4, length: '60%' },
        detail: { valueAnimation: false, formatter: '{value}%', fontSize: 22, offset: [0, '30%'] },
        title: { offset: [0, '78%'], fontSize: 12 },
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
