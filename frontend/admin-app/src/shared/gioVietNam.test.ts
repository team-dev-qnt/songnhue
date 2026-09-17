import dayjs from 'dayjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { bayGio, ngayHomNay, ngayLich } from './format';

/**
 * T63.18 — `dayjs()` trần đọc múi giờ của MÁY, và cái sai ấy đi XUỐNG CSDL.
 *
 * <h3>Vì sao bài này đo được thứ một lượt rà bằng mắt ⛔ thấy</h3>
 *
 * Khuyết tật gốc ⛔ phải "giờ hiện sai vài tiếng" — nó là **lệch cả một NGÀY**:
 * `completedOn: dayjs().format('YYYY-MM-DD')` trên một máy đặt UTC, vào lúc 00:30
 * giờ Việt Nam, ghi xuống **ngày hôm trước**. Hồ sơ công trình nhận một ngày hoàn
 * thành sai, ⛔ một dòng lỗi nào.
 *
 * ⚠ Bộ kiểm chạy dưới `TZ=UTC` (ghim ở `vite.config.ts` cùng lượt T63.18), nên môi
 * trường của bài này DỰNG LẠI ĐƯỢC điều kiện runner — trên máy dev đặt `+07` thì cả
 * hai vế trùng nhau và bài ⛔ phân biệt được gì (luật 9).
 */
describe('T63.18 — giờ Việt Nam ⛔ giờ máy', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('⛔⛔ nửa đêm giờ VN: `dayjs()` trần và `bayGio()` cho HAI NGÀY khác nhau', () => {
    // 17/09 17:30 UTC  =  18/09 00:30 giờ Việt Nam.
    vi.setSystemTime(new Date('2026-09-17T17:30:00Z'));

    // Vế đối chứng: đây là thứ mã CŨ ghi xuống CSDL.
    // ĐÂY là vế đối chứng: bài này tồn tại để chứng minh `dayjs()` trần và `bayGio()` cho HAI
    // kết quả khác nhau, nên nó **bắt buộc** phải gọi được cái sai. Ngoại lệ hẹp tới từng DÒNG
    // — ⛔ nới cho cả `*.test.ts`: một bài kiểm dựng mốc bằng `dayjs()` trần rồi khẳng định
    // payload sẽ tự sai theo máy chạy nó, và đó đúng là lớp lỗi T63.18.
    // eslint-disable-next-line no-restricted-syntax
    expect(dayjs().format('YYYY-MM-DD')).toBe('2026-09-17');
    // Vế khẳng định: đây là ngày người trực ban ĐANG SỐNG trong đó.
    expect(ngayHomNay()).toBe('2026-09-18');
    expect(bayGio().format('YYYY-MM-DD')).toBe('2026-09-18');
  });

  it('⛔ đầu ngày giờ VN cũng lệch theo chiều ngược: 16:59 UTC vẫn là 17/09 ở VN… ', () => {
    // 17/09 16:59 UTC = 17/09 23:59 giờ VN — cùng ngày, để bài trên ⛔ xanh vì lý do sai.
    vi.setSystemTime(new Date('2026-09-17T16:59:00Z'));
    // ĐÂY là vế đối chứng: bài này tồn tại để chứng minh `dayjs()` trần và `bayGio()` cho HAI
    // kết quả khác nhau, nên nó **bắt buộc** phải gọi được cái sai. Ngoại lệ hẹp tới từng DÒNG
    // — ⛔ nới cho cả `*.test.ts`: một bài kiểm dựng mốc bằng `dayjs()` trần rồi khẳng định
    // payload sẽ tự sai theo máy chạy nó, và đó đúng là lớp lỗi T63.18.
    // eslint-disable-next-line no-restricted-syntax
    expect(dayjs().format('YYYY-MM-DD')).toBe('2026-09-17');
    expect(ngayHomNay()).toBe('2026-09-17');
  });

  it('`ngayLich` giữ nguyên ngày người dùng CHỌN, ⛔ quy đổi múi giờ', () => {
    // Một giá trị từ `DatePicker`: người dùng bấm ô 17/09, ý họ là ngày ấy.
    const chon = dayjs('2026-09-17T00:00:00');
    expect(ngayLich(chon)).toBe('2026-09-17');
  });

  it('⭐ so theo NGÀY bằng chuỗi ⛔ phụ thuộc offset của hai vế', () => {
    vi.setSystemTime(new Date('2026-09-17T17:30:00Z'));
    const homNayVN = dayjs('2026-09-18T00:00:00');
    // Cách ĐÚNG: cả hai vế là chuỗi YYYY-MM-DD.
    expect(ngayLich(homNayVN) === ngayHomNay()).toBe(true);
    // Cách CŨ trộn hai offset — `isSame(…,'day')` cắt startOf('day') theo offset RIÊNG
    // từng vế, nên nó nói SAI về đúng ngày mà người trực ban đang đứng.
    // ĐÂY là vế đối chứng: bài này tồn tại để chứng minh `dayjs()` trần và `bayGio()` cho HAI
    // kết quả khác nhau, nên nó **bắt buộc** phải gọi được cái sai. Ngoại lệ hẹp tới từng DÒNG
    // — ⛔ nới cho cả `*.test.ts`: một bài kiểm dựng mốc bằng `dayjs()` trần rồi khẳng định
    // payload sẽ tự sai theo máy chạy nó, và đó đúng là lớp lỗi T63.18.
    // eslint-disable-next-line no-restricted-syntax
    expect(homNayVN.isSame(dayjs(), 'day')).toBe(false);
  });
});
