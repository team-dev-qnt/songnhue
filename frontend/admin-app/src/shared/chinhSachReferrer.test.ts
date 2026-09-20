import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * ⛔⛔ Chính sách `Referer` ⛔ được cắt danh tính ứng dụng — nếu cắt thì BẢN ĐỒ CHẾT (T75.3).
 *
 * <h2>Khuyết tật (đo 20/09/2026 trên chính máy chủ tile, ⛔ suy đoán)</h2>
 *
 * `index.html` khai `<meta name="referrer" content="same-origin">` từ phase 1. Với giá trị ấy trình
 * duyệt bỏ **hẳn** header `Referer` ở mọi request cross-origin — kể cả request ảnh. Tile Usage
 * Policy của OpenStreetMap nhận diện ứng dụng gọi bằng đúng header đó, nên mọi ô bản đồ của
 * `admin-app` nhận về ô *"Access blocked"*:
 *
 * <pre>
 *   không Referer                   ⇒ x-blocked: Access denied. See …/policies/tiles/
 *   Referer https://admin…vn/       ⇒ x-tilerender: azure-01.openstreetmap.org
 *   Referer http://localhost:5173/  ⇒ x-tilerender: azure-01.openstreetmap.org
 * </pre>
 *
 * Ba màn hình cùng hỏng: ô chọn vị trí trong hồ sơ công trình (`LocationPickerMap`), bản đồ GIS của
 * dashboard điều hành (`ConstructionMap`), và mọi lớp bản đồ dựng trên chúng.
 *
 * <h2>Vì sao bài này canh HAI tệp chứ ⛔ một</h2>
 *
 * Chính sách referrer được khai ở hai nơi cho cùng một tài liệu: thẻ `<meta>` trong `index.html` và
 * header `Referrer-Policy` của nginx trong `admin-app.Dockerfile`. Thẻ meta được xử lý **sau** header
 * nên nó THẮNG — tức trước lượt vá, cái có hiệu lực (`same-origin`) khác hẳn cái người đọc
 * `Dockerfile` tưởng đang chạy (`strict-origin-when-cross-origin`). Luật 14: chỗ nào con người phải
 * nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ.
 *
 * <h2>Phạm vi bài này ⛔ phủ (luật 28)</h2>
 *
 * Nó canh **nguyên nhân** (giá trị chính sách), ⛔ canh **hậu quả** (ô bản đồ có tải được ⛔) — jsdom
 * ⛔ gọi mạng và ⛔ có khái niệm referrer policy. Một lượt đo thật vào `tile.openstreetmap.org` sẽ
 * làm bộ kiểm phụ thuộc mạng và đỏ mỗi lần OSM bảo trì, nên nó ⛔ thuộc về đây; nó thuộc về lượt đi
 * đường người dùng thật trước khi giao.
 */

/**
 * ⚠ ⛔ dùng `import.meta.url`: dưới jsdom nó là `http://localhost/…` (xem `setup.ts`). Đi lên từ
 * `process.cwd()` — cùng khuôn `hieuUngVaoTrang.test.ts`.
 */
function tim(...ungVien: string[]): string {
  let d = process.cwd();
  for (let i = 0; i < 6; i++) {
    for (const ten of ungVien) {
      const duong = join(d, ten);
      if (existsSync(duong)) return duong;
    }
    d = dirname(d);
  }
  throw new Error(`⛔ Không tìm thấy ${ungVien.join(' | ')} tính từ ${process.cwd()}`);
}

const HTML = readFileSync(tim('index.html', 'admin-app/index.html'), 'utf8');
const DOCKERFILE = readFileSync(
  tim('deploy/docker/admin-app.Dockerfile', '../../deploy/docker/admin-app.Dockerfile'),
  'utf8',
);

/**
 * Những chính sách CẮT SẠCH `Referer` ở request cross-origin — tức làm bản đồ chết.
 *
 * `no-referrer` và `same-origin` ⛔ gửi gì cả. Mọi giá trị còn lại của chuẩn đều gửi ít nhất phần
 * origin, đủ để OSM nhận diện ứng dụng.
 */
const CAT_SACH_REFERER = ['no-referrer', 'same-origin'];

function chinhSachTrongMeta(): string {
  const m = HTML.match(/<meta\s+name=["']referrer["']\s+content=["']([^"']+)["']/i);
  expect(m, '⛔ đọc được `<meta name="referrer">` trong index.html').not.toBeNull();
  return m![1].trim().toLowerCase();
}

function chinhSachTrongNginx(): string {
  const m = DOCKERFILE.match(/add_header\s+Referrer-Policy\s+["']([^"']+)["']/i);
  expect(m, '⛔ đọc được `add_header Referrer-Policy` trong admin-app.Dockerfile').not.toBeNull();
  return m![1].trim().toLowerCase();
}

describe('Chính sách Referer của admin-app', () => {
  it('⛔ được dùng chính sách cắt sạch Referer — OSM chặn tile khi thiếu header ấy', () => {
    expect(
      CAT_SACH_REFERER,
      'Đặt `same-origin`/`no-referrer` là làm mọi ô bản đồ trả về "Access blocked" — đo 20/09/2026',
    ).not.toContain(chinhSachTrongMeta());
  });

  it('thẻ <meta> và header nginx khai CÙNG một chính sách (luật 14)', () => {
    expect(
      chinhSachTrongMeta(),
      'Thẻ meta được xử lý SAU header nên nó thắng — hai nơi lệch nghĩa là ' +
        'thứ đang chạy khác thứ người đọc Dockerfile tưởng đang chạy',
    ).toBe(chinhSachTrongNginx());
  });

  it('ghim đúng `strict-origin-when-cross-origin` — vế "strict" ⛔ phải trang trí', () => {
    expect(
      chinhSachTrongMeta(),
      'Đây là giá trị DUY NHẤT thoả cả ba: (a) gửi origin ⇒ OSM phục vụ tile; (b) cắt đường dẫn ' +
        'màn hình quản trị (có mã hồ sơ, id bản ghi) khỏi site thứ ba; (c) vế `strict` ⇒ ⛔ gửi gì ' +
        'khi HTTPS → HTTP, nên origin nội bộ ⛔ rò qua một liên kết plaintext. `unsafe-url` hỏng (b), ' +
        '`origin-when-cross-origin` hỏng (c).',
    ).toBe('strict-origin-when-cross-origin');
  });
});
