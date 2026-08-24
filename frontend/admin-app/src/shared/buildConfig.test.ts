// @vitest-environment node
//
// ⚠ BẮT BUỘC, không phải cho nhanh. Bộ test mặc định chạy `jsdom`, ở đó `import.meta.url` là một
// URL `http://localhost/…` chứ không phải `file://` — nên `fileURLToPath` bên trong chính
// `vite.config.ts` ném `TypeError: The URL must be of scheme file` và file này hỏng trước khi
// chạy được bài nào. Đây là bài duy nhất trong bộ cần nạp cấu hình build thật.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import viteConfig from '../../vite.config';

/**
 * Cấu hình **bản dựng thật** của admin-app — những thứ chỉ lộ ra sau khi đã đóng gói.
 *
 * <h2>Vì sao bài này nằm ở bộ test FE chứ không ở `backend/app/.../deploy/`</h2>
 *
 * `FrontendSameOriginTest` (bên BE) cũng canh cấu hình FE, nhưng nó chạy trong job `backend`
 * của CI — mà job đó **bị bỏ qua khi PR chỉ đụng `frontend/`** (bộ lọc là
 * `^(backend/|\.github/workflows/)`). Một thay đổi FE làm hỏng đúng thứ nó canh sẽ đi lọt, và
 * lọt im lặng: GitHub tính `skipped` của một required check là **ĐẠT**. Bài canh cấu hình FE
 * phải sống cùng chỗ với thứ nó canh.
 *
 * <h2>Canh giá trị ĐÃ GIẢI, không canh văn bản</h2>
 *
 * Bài này **import chính `vite.config.ts`** và đọc giá trị thật, thay vì `grep` chuỗi
 * `sourcemap: false` trong tệp. Dự án đã trả giá cho lối canh văn bản: một khẳng định
 * `includes('.sn-align-center')` vẫn xanh sau khi thuộc tính đã bị xoá hẳn.
 */
describe('cấu hình bản dựng admin-app', () => {
  const config = viteConfig as { build?: { sourcemap?: unknown; outDir?: string } };

  it('⭐ KHÔNG phát sourcemap ra bản dựng thật', () => {
    // ⛔ Đo ngày 24/8 trên `songnhue-admin-app:local`: 68 tệp `.map` nằm trong image, và
    //    `GET /assets/ApprovalActions-*.js.map` trả 200 với 4.799 byte mã nguồn gốc. Sourcemap
    //    của admin-app mang nguyên văn TypeScript kèm chú thích nội bộ — mã quyền, hình dạng
    //    endpoint, và chính những đoạn ghi lại chỗ đã từng hở.
    expect(
      config.build?.sourcemap,
      'bật sourcemap ở bản dựng thật là phát nguyên mã nguồn admin ra ngoài — xem chú thích ở vite.config.ts',
    ).toBe(false);
  });

  it('⭐⭐ dist/ đã dựng không chứa tệp .map nào', () => {
    // Đây mới là HỆ QUẢ thật, còn bài trên là nguyên nhân. Giữ cả hai vì chúng hỏng theo hai
    // đường khác nhau: ai đó có thể bật sourcemap qua biến môi trường, qua plugin, hoặc qua một
    // tệp cấu hình thứ hai mà không đụng vào dòng `sourcemap` nào cả.
    //
    // ⚠ Chưa dựng thì bỏ qua thay vì xanh giả — một khẳng định chạy qua tập rỗng không khẳng
    //   định gì (CLAUDE.md luật 7). CI luôn chạy `npm run build` nên ở đó bài này có việc thật.
    const dist = join(__dirname, '../../dist/assets');
    if (!existsSync(dist)) {
      return;
    }
    const maps = readdirSync(dist).filter((f) => f.endsWith('.map'));
    expect(
      maps,
      `dist/assets có ${maps.length} tệp sourcemap: ${maps.slice(0, 3).join(', ')}`,
    ).toHaveLength(0);
  });

  it('⭐ nginx của image chặn hẳn .map — lớp phòng thủ thứ hai, ở tầng khác', () => {
    // Kể cả bundle lỡ mang .map trở lại thì máy chủ cũng không đưa ra.
    //
    // ⚠ Trỏ vào Dockerfile chứ không vào một tệp `.conf` riêng: cấu hình nginx nằm trong
    //   heredoc của Dockerfile. Bản đầu của bài này trỏ vào `admin-app.nginx.conf` — một
    //   đường dẫn KHÔNG TỒN TẠI — và có nhánh `if (!existsSync) return`, nên nó xanh trọn vẹn
    //   mà chưa từng khẳng định gì (luật 7). Không có nhánh thoát nào ở đây là cố ý.
    const dockerfile = readFileSync(
      join(__dirname, '../../../../deploy/docker/admin-app.Dockerfile'),
      'utf8',
    );
    expect(
      dockerfile,
      'thiếu khối `location ~ \\.map$` → tệp sourcemap nằm trong ảnh sẽ được phục vụ, vì ' +
        '`location /assets/` có `try_files $uri =404`',
    ).toMatch(/location\s+~\s+\\\.map\$\s*\{[^}]*return\s+404/);
  });
});
