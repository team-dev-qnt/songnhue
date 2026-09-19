import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from '../lib/boChuThich';

/**
 * **T73.9 — mọi lượt POST của cổng phải được xếp loại: mang vé biểu mẫu, hoặc miễn kèm lý do.**
 *
 * Backend đòi vé ở `InboundSubmissionGate` (liên hệ · góp ý). Một biểu mẫu quên gắn `ve` ⛔ làm gì
 * nổ: mọi lượt gửi của người dân nhận `CMS-2025` và câu *"vui lòng bấm Gửi lại"* — mãi mãi. Nên
 * vế trái của bộ canh này **ĐO** từ mã nguồn (mọi tệp có `method: 'POST'`), ⛔ liệt kê tay: tệp
 * POST mới ra đời mà chưa ai xếp loại là một lượt đỏ gọi đích danh nó (luật 28).
 *
 * ⚠ Vì sao soi mã nguồn: `public-web` cố ý ⛔ có môi trường DOM (xem `vitest.config.mts`), nên
 * hành vi của vé được kiểm ở `lib/veBieuMau.test.ts`; ở đây chỉ còn câu hỏi **ai dùng nó**.
 * Và mọi phép soi chạy trên `boChuThich(ma)` — một chú thích nhắc `ve` ⛔ được đếm là một lượt gửi.
 */
const SRC = join(__dirname, '..');

/** Tệp POST tới đường có cổng vé. Khoá = tệp, giá trị = đường nó gửi tới. */
const DUNG_VE: Record<string, string> = {
  'components/ContactForm.tsx': '/api/v1/public/contacts',
  'components/FeedbackForm.tsx': '/api/v1/public/feedbacks',
};

/** Tệp POST ⛔ cần vé — lý do ≥ 40 ký tự, để một lượt miễn ⛔ lọt qua bằng một chữ "ok". */
const MIEN: Record<string, string> = {
  'components/ViewTracker.tsx':
    'Bộ đếm lượt xem ⛔ đi qua InboundSubmissionGate; gắn vé là bắt mọi lượt xem bài chờ vài giây mới được đếm',
};

function moiTepMa(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) return moiTepMa(duong);
    return /\.(ts|tsx)$/.test(ten) && !/\.test\.tsx?$/.test(ten) ? [duong] : [];
  });
}

/**
 * Thân của lượt `fetch('<duong>', …)` — từ chữ `fetch(` tới `});` đầu tiên sau nó. `null` khi
 * ⛔ có lượt gọi ấy.
 */
function luotGoi(ma: string, duong: string): string | null {
  const dau = ma.indexOf(`fetch('${duong}'`);
  if (dau < 0) return null;
  const cuoi = ma.indexOf('});', dau);
  return cuoi < 0 ? null : ma.slice(dau, cuoi + 3);
}

/** Thân JSON có thuộc tính `ve` (viết tắt `ve,` hoặc `ve: …`) — ⛔ khớp `vendor`, `veBieuMau:`. */
const coTruongVe = (than: string) => /[{,]\s*ve\s*[,:}]/.test(than);

describe('Vé biểu mẫu — mọi lượt POST của cổng được xếp loại (T73.9)', () => {
  const tepPost = moiTepMa(SRC)
    .filter((t) => /method:\s*'POST'/.test(boChuThich(readFileSync(t, 'utf8'))))
    .map((t) => relative(SRC, t))
    .sort();

  it('vế trái ĐO được, ⛔ rỗng — và mọi tệp POST đều đã xếp loại', () => {
    expect(
      tepPost.length,
      'đo ra 0 tệp POST ⇒ phép đo hỏng, ⛔ phải "cổng hết POST"',
    ).toBeGreaterThanOrEqual(3);
    const daXep = new Set([...Object.keys(DUNG_VE), ...Object.keys(MIEN)]);
    const chuaXep = tepPost.filter((t) => !daXep.has(t));
    expect(
      chuaXep,
      'tệp POST mới: thêm vào DUNG_VE (gửi tới đường có cổng vé) hoặc MIEN kèm lý do',
    ).toEqual([]);
    const thua = [...daXep].filter((t) => !tepPost.includes(t));
    expect(thua, 'tệp đã xếp loại mà ⛔ còn POST — gỡ dòng cũ').toEqual([]);
  });

  it('lý do miễn đủ dài để nói được vì sao', () => {
    for (const [tep, lyDo] of Object.entries(MIEN)) {
      expect(lyDo.length, tep).toBeGreaterThanOrEqual(40);
    }
  });

  it('⛔⛔ biểu mẫu dùng vé: xin ở onFocus · lấy TRƯỚC khi gửi · gắn `ve` vào thân · xử lý CMS-2025', () => {
    for (const [tep, duong] of Object.entries(DUNG_VE)) {
      const ma = boChuThich(readFileSync(join(SRC, tep), 'utf8'));
      expect(ma, `${tep}: tạo người giữ vé`).toMatch(/useState\(\(\) => taoNguoiGiuVe\(\)\)/);
      expect(ma, `${tep}: xin vé khi người dùng bắt đầu điền`).toMatch(
        /<form[^>]*onFocus=\{giuVe\.batDau\}/,
      );

      const goi = luotGoi(ma, duong);
      expect(goi, `${tep}: ⛔ thấy fetch('${duong}'`).not.toBeNull();
      expect(
        coTruongVe(goi!),
        `${tep}: thân POST ⛔ mang \`ve\` ⇒ mọi lượt gửi nhận CMS-2025`,
      ).toBe(true);
      expect(
        ma.indexOf('await giuVe.lay()'),
        `${tep}: phải lấy vé (và chờ đủ tuổi) TRƯỚC lượt fetch`,
      ).toBeGreaterThan(-1);
      expect(ma.indexOf('await giuVe.lay()')).toBeLessThan(ma.indexOf(`fetch('${duong}'`));
      expect(ma, `${tep}: bị từ chối vé thì bỏ vé và báo người dùng`).toMatch(
        /if \(await laLoiVe\(res\)\) \{\s*giuVe\.bo\(\);\s*datTt\(\{ loai: 'loi', thongDiep: THONG_DIEP_VE \}\)/,
      );
    }
  });

  it('tự kiểm: bộ bóc lượt gọi PHÂN BIỆT được thân có `ve` và thân ⛔ có (luật 1 · luật 9)', () => {
    const co = `const res = await fetch('/x', {\n  body: JSON.stringify({\n    content: a,\n    ve,\n  }),\n});`;
    const khong = `const res = await fetch('/x', {\n  body: JSON.stringify({\n    content: a,\n    vendor: b,\n  }),\n});\nconst ve = 1;`;
    const chiTrongChuThich = boChuThich(
      `await fetch('/x', {\n  body: JSON.stringify({\n    content: a, // ve,\n  }),\n});`,
    );
    expect(coTruongVe(luotGoi(co, '/x')!)).toBe(true);
    expect(coTruongVe(luotGoi(khong, '/x')!), '`ve` NGOÀI lượt gọi và `vendor` ⛔ được tính').toBe(
      false,
    );
    expect(coTruongVe(luotGoi(chiTrongChuThich, '/x')!), '`ve` trong chú thích ⛔ được tính').toBe(
      false,
    );
    expect(luotGoi(co, '/khac'), 'đường khác ⇒ ⛔ có lượt gọi').toBeNull();
  });
});
