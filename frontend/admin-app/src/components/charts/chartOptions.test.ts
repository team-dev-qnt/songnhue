import { statusColors } from 'design-tokens';
import { describe, expect, it } from 'vitest';

import { CONSTRUCTION_STATUS, CONSTRUCTION_TYPE } from '@/components/business/statusVocabulary';

import {
  mauCua,
  nhanCua,
  optionCotDoc,
  optionCotNgang,
  optionDongHo,
  optionDuong,
  optionTron,
  type Bucket,
} from './chartOptions';

/**
 * Cấu hình biểu đồ — T23.11.
 *
 * <p>Kiểm ở tầng **hàm dựng cấu hình** chứ không ở tầng component: ECharts vẽ lên
 * `<canvas>` mà jsdom không có bộ vẽ canvas, nên một bài kiểm render component chỉ chứng
 * minh được "không ném lỗi". Những câu hỏi đáng tiền nằm ở đây.
 */

interface CoMauItem {
  name: string;
  value: number;
  itemStyle: { color?: string };
}

function duLieuSeries(option: unknown): CoMauItem[] {
  return (option as { series: { data: CoMauItem[] }[] }).series[0].data;
}

describe('màu biểu đồ lấy từ đúng nguồn với badge', () => {
  const theoTrangThai: Bucket[] = [
    { key: 'BINH_THUONG', label: 'BINH_THUONG', count: 12 },
    { key: 'SU_CO', label: 'SU_CO', count: 3 },
    { key: 'BAO_TRI', label: 'BAO_TRI', count: 1 },
  ];

  it('⭐ lát "Sự cố" đúng bằng màu đỏ mà StatusBadge dùng', () => {
    const lat = duLieuSeries(optionTron(theoTrangThai, CONSTRUCTION_STATUS)).find(
      (m) => m.name === 'Sự cố',
    );

    // Đây là toàn bộ nội dung của T23.1: một bảng màu, không phải hai. Nếu ai đó viết một
    // mã màu tại chỗ trong chart thì lát bánh và badge trên bảng sẽ khác nhau, mà nhìn
    // riêng từng màn hình thì cả hai đều "trông ổn".
    expect(lat?.itemStyle.color).toBe(statusColors.danger);
    expect(statusColors[CONSTRUCTION_STATUS.SU_CO.color]).toBe(statusColors.danger);
  });

  it('⛔ mã lạ KHÔNG rơi về màu "bình thường" mà để theme tự chọn', () => {
    // Rơi về `normal` nghĩa là một trạng thái hệ thống chưa biết sẽ hiện màu xanh — tức
    // là một khẳng định "ổn" về thứ chưa ai xác nhận.
    expect(
      mauCua({ key: 'MA_MOI_TINH', label: 'Mã mới', count: 1 }, CONSTRUCTION_STATUS),
    ).toBeUndefined();
  });

  it('nhãn lấy từ bộ từ vựng, rơi về nhãn backend gửi khi không có', () => {
    expect(nhanCua({ key: 'TRAM_BOM', label: 'TRAM_BOM', count: 2 }, CONSTRUCTION_TYPE)).toBe(
      'Trạm bơm',
    );
    expect(nhanCua({ key: '7', label: 'Xí nghiệp Hà Đông', count: 2 })).toBe('Xí nghiệp Hà Đông');
  });
});

describe('cột ngang', () => {
  it('sắp tăng dần để nhóm lớn nhất nằm trên cùng khi ECharts vẽ trục y', () => {
    const option = optionCotNgang([
      { key: 'a', label: 'Xí nghiệp A', count: 2 },
      { key: 'b', label: 'Xí nghiệp B', count: 9 },
      { key: 'c', label: 'Xí nghiệp C', count: 5 },
    ]);

    expect((option as { yAxis: { data: string[] } }).yAxis.data).toEqual([
      'Xí nghiệp A',
      'Xí nghiệp C',
      'Xí nghiệp B',
    ]);
  });

  it('⛔ không tự bịa mục nào khi danh sách rỗng', () => {
    // Một biểu đồ sinh ra mục "(không có)" trông y hệt một nhóm dữ liệu thật bằng 0.
    expect(duLieuSeries(optionCotNgang([]))).toHaveLength(0);
    expect(duLieuSeries(optionCotDoc([]))).toHaveLength(0);
    expect(duLieuSeries(optionTron([]))).toHaveLength(0);
  });
});

describe('đồng hồ tỉ lệ', () => {
  function dai(option: unknown): [number, string][] {
    return (option as { series: { axisLine: { lineStyle: { color: [number, string][] } } }[] })
      .series[0].axisLine.lineStyle.color;
  }

  it('chặn giá trị ngoài khoảng thay vì vẽ kim ra ngoài mặt đồng hồ', () => {
    expect(giaTri(optionDongHo(140, 'x'))).toBe(100);
    expect(giaTri(optionDongHo(-8, 'x'))).toBe(0);
    expect(giaTri(optionDongHo(Number.NaN, 'x'))).toBe(0);
  });

  it('⭐ hướng màu đảo được — cao là tốt hay cao là xấu là hai chuyện khác nhau', () => {
    // Không có tham số này thì một tỉ lệ "càng cao càng xấu" (VD tỉ lệ hồ sơ quá hạn) sẽ
    // hiện xanh ở mức 90%, và người đọc lướt qua chỉ nhìn màu.
    expect(dai(optionDongHo(90, 'x'))[2][1]).toBe(statusColors.normal);
    expect(dai(optionDongHo(90, 'x', true))[2][1]).toBe(statusColors.danger);
  });

  function giaTri(option: unknown): number {
    return (option as { series: { data: { value: number }[] }[] }).series[0].data[0].value;
  }
});

describe('biểu đồ đường', () => {
  it('⛔ không nối liền qua khoảng trống dữ liệu', () => {
    const option = optionDuong(
      ['00:00', '00:10', '00:20'],
      [{ ten: 'Trạm A', giaTri: [1.2, null, 1.4] }],
    );

    // Nối liền qua chỗ mất tín hiệu là vẽ ra một đoạn số liệu chưa từng được đo — trên
    // biểu đồ mực nước thì đó là bịa ra một mực nước.
    expect((option as { series: { connectNulls: boolean }[] }).series[0].connectNulls).toBe(false);
  });
});
