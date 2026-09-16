import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from '../lib/boChuThich';

/**
 * **T61.39 — thông báo quyền riêng tư và ô đồng ý trên hai biểu mẫu cổng** (NĐ 13/2023 Điều 11, 13).
 *
 * ## Bất biến canh ở đây
 *
 * 1. Cả hai biểu mẫu đọc `site.privacy.notice` (qua prop do trang truyền xuống) và **chỉ** hiện ô
 *    đồng ý khi có nội dung. ⛔⛔ Hiện ô khi thông báo RỖNG là dựng một **bằng chứng đồng ý giả**:
 *    bản ghi mang mốc thời gian trông như đã tuân thủ, trong khi ⛔ ai được thông báo gì.
 * 2. Ô đồng ý là `required` — trình duyệt chặn trước, backend chặn lại (`SYS-0003`, luật 12).
 * 3. Hai trang truyền đúng hai khoá xuống; thiếu một trang là nửa cặp đọc–ghi (luật 27).
 * 4. Liên kết chính sách đi qua `lienKetAnToan` — giá trị do quản trị nhập, T61.34.
 *
 * ## Vì sao soi mã nguồn
 *
 * Cùng lý lẽ `gopYCauHinh.test.ts`: một bản giả trả đủ giá trị sẽ xanh y hệt dù trang ghi cứng.
 * Thứ cần khẳng định là **nguồn** của quyết định.
 */
const GOC = join(__dirname, '..');
const doc = (p: string) => boChuThich(readFileSync(join(GOC, p), 'utf8'));

const BIEU_MAU = ['components/ContactForm.tsx', 'components/FeedbackForm.tsx'];
const TRANG = ['app/lien-he/page.tsx', 'app/gop-y/page.tsx'];

describe('Thông báo quyền riêng tư — T61.39', () => {
  it('⛔⛔ ô đồng ý chỉ hiện khi CÓ thông báo, và nó là trường bắt buộc', () => {
    for (const tep of BIEU_MAU) {
      const ma = doc(tep);
      expect(ma, `${tep}: phải suy từ nội dung thông báo`).toMatch(/coThongBao\s*=/);
      expect(ma, `${tep}: khối đồng ý phải đứng sau điều kiện coThongBao`).toMatch(
        /\{coThongBao && \(/,
      );
      expect(ma, `${tep}: ô đồng ý phải là checkbox name="dongY"`).toMatch(
        /type="checkbox"\s+name="dongY"\s+required/,
      );
      expect(ma, `${tep}: chỉ gửi trường dongY khi có thông báo`).toMatch(
        /coThongBao \? \{ dongY:/,
      );
    }
  });

  it('⭐ hai trang truyền ĐÚNG hai khoá settings xuống biểu mẫu — thiếu một trang là nửa cặp đọc–ghi', () => {
    for (const tep of TRANG) {
      const ma = doc(tep);
      expect(ma, `${tep}: site.privacy.notice`).toContain("'site.privacy.notice'");
      expect(ma, `${tep}: site.privacy.policy-url`).toContain("'site.privacy.policy-url'");
    }
  });

  it('⛔ liên kết chính sách do quản trị nhập ⇒ phải đi qua lienKetAnToan (T61.34)', () => {
    for (const tep of BIEU_MAU) {
      expect(doc(tep), tep).toMatch(
        /lienKetAnToan\(duongDanChinhSach\)|lienKetAnToan\(cauHinh\.duongDanChinhSach\)/,
      );
    }
  });

  it('⚠ chống tập rỗng: bốn tệp đang tồn tại và có nội dung', () => {
    for (const tep of [...BIEU_MAU, ...TRANG]) {
      expect(doc(tep).length, tep).toBeGreaterThan(500);
    }
  });
});
