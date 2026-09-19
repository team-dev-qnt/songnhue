import { render } from '@testing-library/react';
import { ConfigProvider, theme } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { antdTheme } from '@/shared/antdTheme';

import { WallFrame } from './WallFrame';

/**
 * ⛔⛔ Chữ phụ của wall mode phải SÁNG trên nền tối — T67.8 (WS-67).
 *
 * Đo 19/09/2026 trên trình duyệt thật: nhãn cả 10 thẻ KPI mang `rgba(31, 31, 31, 0.45)` — chữ tối trên
 * nền `#111c2e`, vô hình. `Typography type="secondary"` đọc token `colorTextDescription`, token ấy được
 * thuật toán tối SUY RA từ hạt giống `colorTextBase`, mà `ConfigProvider` lồng của `WallFrame` thừa kế
 * hạt giống `#1f1f1f` của chủ đề sáng bọc ngoài. Bị che suốt vì chính khung wall trước đó ⛔ hiện ra được
 * (T67.7 — `transform` còn giữ lại nhốt nó còn 24 px).
 *
 * Token là giá trị JS nên jsdom đo được thật, ⛔ cần CSS (bộ kiểm chạy ⛔ CSS antd — `setup.ts`).
 */
function doSang(mau: string): number {
  const rgba = /rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/.exec(mau);
  const hex = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})/i.exec(mau);
  const [r, g, b] = rgba
    ? [Number(rgba[1]), Number(rgba[2]), Number(rgba[3])]
    : hex
      ? [parseInt(hex[1], 16), parseInt(hex[2], 16), parseInt(hex[3], 16)]
      : [NaN, NaN, NaN];
  return (0.299 * r + 0.587 * g + 0.114 * b) / 255;
}

function DocToken({ ra }: { ra: Record<string, string> }) {
  const { token } = theme.useToken();
  ra.moTa = token.colorTextDescription;
  ra.phu = token.colorTextSecondary;
  ra.chinh = token.colorText;
  return null;
}

function doTrong(con: 'wall' | 'thuong') {
  const ra: Record<string, string> = {};
  render(
    <ConfigProvider theme={antdTheme}>
      <MemoryRouter>
        {con === 'wall' ? (
          <WallFrame capNhatLuc={undefined} rotateSeconds={0}>
            <DocToken ra={ra} />
          </WallFrame>
        ) : (
          <DocToken ra={ra} />
        )}
      </MemoryRouter>
    </ConfigProvider>,
  );
  return ra;
}

describe('Màu chữ trong wall mode (T67.8)', () => {
  it('⛔⛔ colorTextDescription / Secondary / Text trong WallFrame đều SÁNG (nền tối #111c2e)', () => {
    const ra = doTrong('wall');
    for (const [ten, mau] of Object.entries(ra)) {
      expect(
        doSang(mau),
        `${ten} = ${mau} — chữ tối trên nền tối. Kiểm hạt giống colorTextBase trong WallFrame (T67.8)`,
      ).toBeGreaterThan(0.5);
    }
  });

  it('⭐ đối chứng: NGOÀI wall, chữ phụ của chủ đề sáng là chữ TỐI (bộ đo phân biệt được hai trạng thái)', () => {
    const ra = doTrong('thuong');
    expect(doSang(ra.moTa)).toBeLessThan(0.5);
  });
});
