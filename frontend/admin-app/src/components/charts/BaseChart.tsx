import { Empty, Skeleton } from 'antd';
import { useEffect, useRef } from 'react';

import { THEME_SANG, THEME_TUONG, echarts } from './setup';

export interface BaseChartProps {
  /** Cấu hình ECharts đã dựng sẵn — component này không biết gì về nghiệp vụ. */
  option: Record<string, unknown>;
  /** ⛔ Bắt buộc khai tường minh: xem giải thích ở đầu file. */
  empty: boolean;
  loading?: boolean;
  height?: number | string;
  /** Chế độ màn hình lớn — đổi theme, không đổi ý nghĩa màu. */
  wall?: boolean;
  emptyText?: string;
}

/**
 * Khung chung của mọi biểu đồ — T23.2.
 *
 * <h3>⛔ Không có dữ liệu thì KHÔNG vẽ biểu đồ trống</h3>
 *
 * CN-03.4 nói rõ điều này cho thuỷ văn; ở đây áp cho tất cả. Một biểu đồ có trục, có
 * lưới, có chú giải mà không có cột nào trông **y hệt** một biểu đồ mà mọi giá trị đều
 * bằng 0 — và đó là hai câu khác nhau. Tệ hơn: nó cũng trông y hệt trường hợp quên đăng
 * ký component ở `setup.ts`, vì ECharts không ném lỗi mà chỉ vẽ khung rỗng.
 *
 * <p>`empty` là **thuộc tính bắt buộc**, không suy đoán từ `option`. Suy đoán thì mỗi
 * loại biểu đồ cất dữ liệu một chỗ khác nhau (`series[].data`, `dataset.source`,
 * `series[].data[].value`), và một phép đoán sai làm biểu đồ có dữ liệu hiện ra chữ
 * "không có dữ liệu" — hỏng theo chiều nguy hiểm hơn.
 *
 * <h3>Đổi theme phải dựng lại thực thể</h3>
 *
 * ECharts chốt theme lúc `init`; `setOption` sau đó không đổi được. Nên `wall` nằm trong
 * danh sách phụ thuộc của effect dựng, còn `option` thì không — dữ liệu mới chỉ cần
 * `setOption`, dựng lại cả biểu đồ mỗi lượt làm mới là nhấp nháy trên màn hình treo tường.
 */
export function BaseChart({
  option,
  empty,
  loading = false,
  height = 280,
  wall = false,
  emptyText = 'Không có dữ liệu',
}: BaseChartProps) {
  const khungRef = useRef<HTMLDivElement>(null);
  const bieuDoRef = useRef<echarts.ECharts | null>(null);

  const anBieuDo = loading || empty;

  useEffect(() => {
    if (anBieuDo || !khungRef.current) {
      return;
    }
    const bieuDo = echarts.init(khungRef.current, wall ? THEME_TUONG : THEME_SANG, {
      renderer: 'canvas',
    });
    bieuDoRef.current = bieuDo;

    // ⚠ Theo dõi bề rộng bằng ResizeObserver chứ không bằng sự kiện `resize` của window:
    // biểu đồ nằm trong lưới co giãn, nên thanh bên đóng/mở làm khung hẹp lại mà cửa sổ
    // không đổi kích thước — và biểu đồ sẽ tràn ra ngoài thẻ chứa nó.
    const theoDoi = new ResizeObserver(() => bieuDo.resize());
    theoDoi.observe(khungRef.current);

    return () => {
      theoDoi.disconnect();
      bieuDo.dispose();
      bieuDoRef.current = null;
    };
  }, [anBieuDo, wall]);

  useEffect(() => {
    // `notMerge` để dữ liệu cũ không sót lại: chuỗi 5 mục thay bằng chuỗi 3 mục mà gộp
    // thì hai mục cũ vẫn nằm trên biểu đồ.
    bieuDoRef.current?.setOption(option, { notMerge: true });
  }, [option]);

  if (loading) {
    return <Skeleton active paragraph={{ rows: 4 }} />;
  }
  if (empty) {
    return (
      <div style={{ height, display: 'grid', placeItems: 'center' }}>
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />
      </div>
    );
  }
  return <div ref={khungRef} style={{ width: '100%', height }} />;
}
