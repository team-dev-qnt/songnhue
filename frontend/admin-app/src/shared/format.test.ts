import { describe, expect, it } from 'vitest';

import { formatAge, formatBytes, formatDateTime, formatDuration, formatNumber } from './format';

describe('formatDateTime', () => {
  /**
   * Bài kiểm quan trọng nhất file này: cùng một mốc UTC phải ra cùng một chuỗi UTC+7,
   * **bất kể máy chạy test đặt múi giờ nào**. Viết `expect(...).toBe('...')` với một mốc
   * đã tính sẵn ra +7 chính là để bắt lỗi "quên `.tz()` nên dùng giờ máy" — lỗi đó xanh
   * hết trên máy dev ở Việt Nam và chỉ đỏ khi CI chạy ở UTC.
   */
  it('đổi mốc UTC sang UTC+7 theo định dạng dd/MM/yyyy HH:mm', () => {
    expect(formatDateTime('2026-08-16T02:00:00Z')).toBe('16/08/2026 09:00');
  });

  it('qua ngày khi cộng 7 giờ vượt nửa đêm', () => {
    expect(formatDateTime('2026-08-16T19:30:00Z')).toBe('17/08/2026 02:30');
  });

  it('giá trị rỗng hiện dấu gạch thay vì "Invalid Date"', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime('không-phải-ngày')).toBe('—');
  });
});

describe('formatNumber', () => {
  it('ngăn nghìn kiểu Việt Nam', () => {
    expect(formatNumber(1234567)).toBe('1.234.567');
  });

  it('phần thập phân dùng dấu phẩy', () => {
    expect(formatNumber(1234.5, 2)).toBe('1.234,50');
  });
});

describe('formatBytes / formatDuration / formatAge', () => {
  it('đổi đơn vị dung lượng theo cơ số 1024', () => {
    expect(formatBytes(1024)).toBe('1,0 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5,0 MB');
  });

  it('thời lượng đọc được bằng tiếng Việt', () => {
    expect(formatDuration(45_000)).toBe('45 giây');
    expect(formatDuration(3_930_000)).toBe('1 giờ 5 phút');
  });

  it('phân biệt "chưa từng" với "vừa xong" — hai tình trạng sao lưu khác hẳn nhau', () => {
    expect(formatAge(null)).toBe('chưa từng');
    expect(formatAge(30)).toBe('vừa xong');
    expect(formatAge(31 * 3600)).toBe('31 giờ trước');
  });
});

import { formatInvestment } from './format';

describe('formatInvestment', () => {
  it('không lỗi khi truyền null/undefined', () => {
    expect(formatInvestment(null)).toBeNull();
    expect(formatInvestment(undefined)).toBeNull();
  });

  it('định dạng VNĐ cho số dưới 1 triệu', () => {
    expect(formatInvestment(500_000)).toBe('500.000 VNĐ');
    expect(formatInvestment(999_999)).toBe('999.999 VNĐ');
  });

  it('đổi sang triệu VNĐ (giữ tối đa 2 chữ số thập phân)', () => {
    expect(formatInvestment(1_000_000)).toBe('~ 1 triệu VNĐ');
    expect(formatInvestment(1_500_000)).toBe('~ 1,5 triệu VNĐ');
    expect(formatInvestment(1_555_000)).toBe('~ 1,56 triệu VNĐ');
    expect(formatInvestment(999_999_999)).toBe('~ 1.000 triệu VNĐ');
  });

  it('đổi sang tỷ VNĐ (giữ tối đa 2 chữ số thập phân)', () => {
    expect(formatInvestment(1_000_000_000)).toBe('~ 1 tỷ VNĐ');
    expect(formatInvestment(1_500_000_000)).toBe('~ 1,5 tỷ VNĐ');
    expect(formatInvestment(1_555_000_000)).toBe('~ 1,56 tỷ VNĐ');
  });
});
