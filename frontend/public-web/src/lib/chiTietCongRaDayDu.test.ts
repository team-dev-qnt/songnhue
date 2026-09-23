import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

/**
 * **Chiều RA của hợp đồng bài viết trên CỔNG: `PublicArticleDetail.java` → `lib/api.ts`** — T84.5.
 *
 * <h3>Khe hở đã ĐO</h3>
 *
 * `apiKhongMoCoi.test.ts` canh *"hàm trong `api.ts` có ai gọi ⛔"*; ⛔ bộ canh nào hỏi chiều ngược
 * lại — *"backend trả trường này mà cổng có khai ⛔"*. Backend thêm một trường mà `api.ts` ⛔ khai thì
 * `apiGet<T>()` vẫn xanh (nó là một **khẳng định kiểu**, ⛔ phải phép kiểm lúc chạy) và giá trị ấy đi
 * về tới máy chủ Next rồi bị vứt, ⛔ một dòng lỗi. Đó đúng là trạng thái `authorName` đã nằm.
 *
 * <h3>⚠ Vì sao chép phép đọc thay vì import của `admin-app`</h3>
 *
 * Hai ứng dụng là hai gói workspace riêng, ⛔ có đường module nào giữa chúng, và mỗi bên chạy bằng
 * một cấu hình vitest riêng. Bản chép ở đây **cố ý tối giản** và mang một bài **tự-kiểm trên dữ liệu
 * giả** — để nếu hai bản có lệch đi thì bản yếu hơn ⛔ âm thầm cho ra tập NHỎ HƠN (T48.4: hai bộ đọc,
 * bộ yếu hơn xanh vì lý do sai).
 *
 * <h3>Phạm vi — khai ra thay vì để cái xanh đọc rộng hơn (luật 28)</h3>
 *
 * Chỉ kiểm **sự có mặt** của tên trường, ⛔ kiểm kiểu. `PublicArticleDetail` là một record PHẲNG còn
 * `ArticleDetail` của TS thì `extends ArticleRow` ⇒ phải hợp hai interface lại.
 */

const GOC_KHO = join(dirname(fileURLToPath(import.meta.url)), '../../../..');
const JAVA = readFileSync(
  join(
    GOC_KHO,
    'backend/content/src/main/java/com/songnhue/content/application/PublicArticleDetail.java',
  ),
  'utf8',
);
const API_TS = readFileSync(join(dirname(fileURLToPath(import.meta.url)), 'api.ts'), 'utf8');

function truongCuaRecord(nguon: string, ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(nguon);
  if (!m) throw new Error(`Không tìm thấy record ${ten}`);
  let than = m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ');
  let truoc = '';
  while (truoc !== than) {
    truoc = than;
    than = than.replace(/<[^<>]*>/g, '');
  }
  return than
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

/** Thân của một interface, cắt theo ngoặc CÂN BẰNG (⛔ theo dòng — javadoc có dấu ngoặc). */
function thanInterface(nguon: string, ten: string): string {
  const mo = new RegExp(`export interface ${ten}(\\s+extends [\\w, ]+)?\\s*\\{`).exec(nguon);
  if (!mo) throw new Error(`Không tìm thấy interface ${ten}`);
  const bat = nguon.indexOf('{', mo.index);
  let sau = 0;
  for (let i = bat; i < nguon.length; i += 1) {
    if (nguon[i] === '{') sau += 1;
    if (nguon[i] === '}') {
      sau -= 1;
      if (sau === 0) return nguon.slice(bat + 1, i);
    }
  }
  throw new Error(`Interface ${ten} ⛔ đóng ngoặc`);
}

function thuocTinhCua(ten: string): Set<string> {
  const than = thanInterface(API_TS, ten)
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ');
  const ra = new Set<string>();
  let sau = 0;
  for (const dong of than.split('\n')) {
    if (sau === 0) {
      const m = /^\s*(\w+)\??\s*:/.exec(dong);
      if (m) ra.add(m[1]);
    }
    sau += (dong.match(/[{[]/g) ?? []).length - (dong.match(/[}\]]/g) ?? []).length;
  }
  return ra;
}

/**
 * Trường backend trả mà cổng **cố ý ⛔ đọc**, kèm lý do ≥ 40 ký tự.
 *
 * ⛔ Một dòng miễn trừ ⛔ lý do là một chỗ để giấu trường bị quên.
 */
const CO_Y_KHONG_DOC: Record<string, string> = {};

describe('Chi tiết bài trên cổng — chiều RA đủ trường (T84.5)', () => {
  it('⚠ chống tập rỗng: đọc được record Java và hợp hai interface TS', () => {
    expect(truongCuaRecord(JAVA, 'PublicArticleDetail').length).toBeGreaterThanOrEqual(14);
    expect(thuocTinhCua('ArticleDetail').size).toBeGreaterThanOrEqual(5);
    expect(thuocTinhCua('ArticleRow').size).toBeGreaterThanOrEqual(6);
  });

  it('⭐ phép đọc phân biệt được hai trạng thái — tự kiểm trên dữ liệu GIẢ', () => {
    const gia = 'public record Gia(String slug, String authorName, List<CategoryRef> categories) {';
    expect(truongCuaRecord(gia, 'Gia')).toEqual(['slug', 'authorName', 'categories']);
    expect(truongCuaRecord(gia, 'Gia')).not.toContain('viewCount');
  });

  it('⭐⭐ mọi trường PublicArticleDetail trả ra đều có trong lib/api.ts', () => {
    // `ArticleDetail extends ArticleRow` ⇒ hợp hai tập, ⛔ chỉ đọc một.
    const khaiTs = new Set([...thuocTinhCua('ArticleDetail'), ...thuocTinhCua('ArticleRow')]);
    const thieu = truongCuaRecord(JAVA, 'PublicArticleDetail').filter(
      (t) => !khaiTs.has(t) && !(t in CO_Y_KHONG_DOC),
    );
    expect(
      thieu,
      'backend trả trường này mà cổng ⛔ khai ⇒ giá trị về tới máy chủ Next rồi bị vứt, ⛔ một dòng lỗi',
    ).toEqual([]);
  });

  it('⭐ tác giả nằm ở CẢ HAI phía — chống việc bài trên xanh vì đọc hụt', () => {
    expect(truongCuaRecord(JAVA, 'PublicArticleDetail')).toContain('authorName');
    expect([...thuocTinhCua('ArticleDetail')]).toContain('authorName');
  });

  it('⛔ mọi dòng miễn trừ đều có lý do ≥ 40 ký tự', () => {
    for (const [truong, lyDo] of Object.entries(CO_Y_KHONG_DOC)) {
      expect(lyDo.length, `miễn trừ "${truong}" thiếu lý do đủ dài`).toBeGreaterThanOrEqual(40);
    }
  });
});
