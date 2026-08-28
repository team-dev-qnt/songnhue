import { describe, expect, it } from 'vitest';

import { GAP_NGOAI_PX, vuaThanhNgang } from './vuaThanhNgang';

/**
 * **Ngưỡng của thanh điều hướng là một phép đo, và đây là phần kết luận của phép đo ấy.**
 *
 * Số dùng trong bài này KHÔNG bịa: chúng lấy từ lượt đo ngày 28/08 bằng chính font đang chạy
 * (Noto Sans 600 của `@fontsource`, qua fontTools) trên tám nhãn thật của `/menus/HEADER`.
 * Khung chứa = `max-w-[1240px]` trừ `px-6` hai bên = 1192px.
 */
const KHUNG = 1192;

/** Nút Tìm kiếm ở cỡ 12px `px-2`: đệm 16 + kính lúp 16 + `gap-1.5` 6 + chữ "TÌM KIẾM" ≈ 51. */
const TIM = 89;

describe('Thanh ngang có vừa khung không', () => {
  it('⭐ bộ nhãn đang chạy — HOA 12px px-2 — thì VỪA', () => {
    // Tổng đo được 1150,6px; trừ gap và nút Tìm kiếm ra bề rộng riêng của thanh mục.
    expect(vuaThanhNgang({ trong: KHUNG, thuoc: 1150.6 - GAP_NGOAI_PX - TIM, tim: TIM })).toBe(
      true,
    );
  });

  it('⛔ chính bộ nhãn ấy ở cỡ cũ — HOA 13px px-3 — thì KHÔNG vừa', () => {
    // 1297,5px: cấu hình sẽ tràn 105,5px, tức đúng lỗi §10.62 tái phát.
    expect(vuaThanhNgang({ trong: KHUNG, thuoc: 1297.5 - GAP_NGOAI_PX - TIM, tim: TIM })).toBe(
      false,
    );
  });

  it('⭐⭐ vừa KHÍT vẫn là vừa, và thừa một pixel là không', () => {
    // Biên. Một dấu `<` viết nhầm thành `<=` (hoặc ngược lại) chỉ lộ ra ở đúng hai trường hợp này.
    const thuoc = KHUNG - GAP_NGOAI_PX - TIM;
    expect(vuaThanhNgang({ trong: KHUNG, thuoc, tim: TIM })).toBe(true);
    expect(vuaThanhNgang({ trong: KHUNG, thuoc: thuoc + 1, tim: TIM })).toBe(false);
  });

  it('⛔ khung bề rộng 0 trả `null`, KHÔNG trả `false`', () => {
    // Tab chạy nền và lượt vẽ để in đều cho `clientWidth = 0`. Trả `false` ở đó là đá thanh về
    // ngăn kéo rồi bật lại khi người dùng quay lại tab — nhấp nháy không ai lần ra nguyên nhân.
    expect(vuaThanhNgang({ trong: 0, thuoc: 100, tim: TIM })).toBeNull();
    expect(vuaThanhNgang({ trong: -5, thuoc: 100, tim: TIM })).toBeNull();
    expect(vuaThanhNgang({ trong: Number.NaN, thuoc: 100, tim: TIM })).toBeNull();
    expect(vuaThanhNgang({ trong: KHUNG, thuoc: Number.NaN, tim: TIM })).toBeNull();
  });

  it('⭐ nút Tìm kiếm được TÍNH VÀO — nếu không, thanh sẽ đè lên nó', () => {
    // Bỏ quên nó là lỗi dễ mắc nhất: thanh mục vừa khít khung, nhưng nút Tìm kiếm nằm cùng hàng.
    const thuoc = KHUNG - GAP_NGOAI_PX - TIM + 1;
    expect(vuaThanhNgang({ trong: KHUNG, thuoc, tim: TIM })).toBe(false);
    expect(vuaThanhNgang({ trong: KHUNG, thuoc, tim: 0 })).toBe(true);
  });
});
