import { DatePicker, Space, Tag } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';

import { APP_TIMEZONE, toApiInstant } from '@/shared/format';

/**
 * Bộ lọc khoảng thời gian dùng chung.
 *
 * Trả ra **chuỗi ISO UTC** cho API, còn hiển thị theo UTC+7 — người dùng chọn "16/08" là
 * chọn ngày 16 giờ Việt Nam, không phải 16 giờ UTC. Lệch 7 tiếng ở hai đầu khoảng lọc
 * nghe thì nhỏ, nhưng với bảng nhật ký kiểm toán phân mảnh theo tháng thì nó lấy nhầm
 * cả mảnh dữ liệu.
 */
export interface DateRange {
  from?: string;
  to?: string;
}

const PRESETS: { label: string; days: number }[] = [
  { label: '7 ngày', days: 7 },
  { label: '30 ngày', days: 30 },
  { label: '90 ngày', days: 90 },
];

export function DateRangeFilter({
  value,
  onChange,
  showPresets = true,
}: {
  value: DateRange;
  onChange: (range: DateRange) => void;
  showPresets?: boolean;
}) {
  const picked: [Dayjs, Dayjs] | null =
    value.from && value.to
      ? [dayjs(value.from).tz(APP_TIMEZONE), dayjs(value.to).tz(APP_TIMEZONE)]
      : null;

  const applyDays = (days: number) => {
    const to = dayjs().tz(APP_TIMEZONE);
    onChange({ from: toApiInstant(to.subtract(days, 'day').startOf('day')), to: toApiInstant(to) });
  };

  return (
    <Space wrap>
      <DatePicker.RangePicker
        value={picked}
        format="DD/MM/YYYY"
        placeholder={['Từ ngày', 'Đến ngày']}
        onChange={(range) => {
          if (!range?.[0] || !range[1]) {
            onChange({});
            return;
          }
          // Đầu ngày / cuối ngày theo UTC+7: chọn cùng một ngày cho cả hai đầu vẫn phải
          // lấy trọn 24 giờ hôm đó, chứ không phải một khoảng rỗng.
          onChange({
            from: toApiInstant(range[0].startOf('day')),
            to: toApiInstant(range[1].endOf('day')),
          });
        }}
      />
      {showPresets &&
        PRESETS.map((preset) => (
          <Tag
            key={preset.days}
            style={{ cursor: 'pointer' }}
            onClick={() => applyDays(preset.days)}
          >
            {preset.label}
          </Tag>
        ))}
    </Space>
  );
}
