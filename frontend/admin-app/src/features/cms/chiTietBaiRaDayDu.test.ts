import { describe, expect, it } from 'vitest';

import { docTep, truongCuaRecord } from './docRecordJava';

/**
 * **Chiều RA của hợp đồng bài viết: DTO Java → `types.ts`** — T84.5.
 *
 * <h3>Khe hở đã ĐO, ⛔ phải một rủi ro lý thuyết</h3>
 *
 * Kho có `soanBaiVongKhuHoi.test.tsx` canh chiều **VÀO** (`SaveRequest` → payload), và nó đã cứu
 * `authorPublicId`. Chiều **RA** thì **⛔ có một bộ canh nào** (đo 22/09/2026): backend thêm một
 * trường vào `ArticleDetail` mà `types.ts` ⛔ khai thì TypeScript ⛔ thấy gì — `api.get<T>()` là một
 * lời **khẳng định kiểu**, ⛔ phải một phép kiểm lúc chạy. Trường ấy về tới trình duyệt rồi bị vứt
 * trong im lặng.
 *
 * Đó đúng là trạng thái `authorName` đã nằm: backend có `author_user_id` từ 19/08/2026, DTO ⛔ trả,
 * và ⛔ ai biết cho tới khi có người đi đọc mã (luật 27).
 *
 * <h3>⚠ Bộ canh này ⛔ kiểm KIỂU, chỉ kiểm SỰ CÓ MẶT</h3>
 *
 * Nó hỏi *"mọi thành phần của record Java có xuất hiện trong khai báo interface TS ⛔"*. Một trường
 * khai sai kiểu (`string` cho một `Instant`) đi lọt — vá chỗ ấy đòi một bộ sinh mã, và đó là một
 * lượt riêng. Phạm vi ấy **khai ra ở đây** chứ ⛔ để cái xanh của nó đọc như bảo đảm rộng hơn (luật 28).
 */

const DTOS = docTep('backend/content/src/main/java/com/songnhue/content/api/ArticleDtos.java');
const TYPES = docTep('frontend/admin-app/src/features/cms/types.ts');

/** Thân của `export interface <ten> { ... }`, cắt theo ngoặc CÂN BẰNG. */
function thanInterface(ten: string): string {
  const mo = TYPES.indexOf(`export interface ${ten} {`);
  if (mo < 0) throw new Error(`Không tìm thấy interface ${ten} trong types.ts`);
  let sau = 0;
  const bat = TYPES.indexOf('{', mo);
  for (let i = bat; i < TYPES.length; i += 1) {
    if (TYPES[i] === '{') sau += 1;
    if (TYPES[i] === '}') {
      sau -= 1;
      if (sau === 0) return TYPES.slice(bat + 1, i);
    }
  }
  throw new Error(`Interface ${ten} ⛔ đóng ngoặc`);
}

/**
 * Tên các thuộc tính khai ở cấp NGOÀI CÙNG của một interface.
 *
 * ⚠ Bỏ chú thích **trước** khi quét: javadoc của `types.ts` nhắc tên trường rất nhiều, và tính một
 * chú thích là một khai báo là đúng lỗi T46.7 · T49.6 đã trả giá hai lần.
 */
function thuocTinhCua(ten: string): Set<string> {
  const than = thanInterface(ten)
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ');
  const ra = new Set<string>();
  let sau = 0;
  for (const dong of than.split('\n')) {
    // Chỉ nhận dòng ở cấp ngoài cùng — tránh nhận trường của một object lồng.
    if (sau === 0) {
      const m = /^\s*(\w+)\??\s*:/.exec(dong);
      if (m) ra.add(m[1]);
    }
    sau += (dong.match(/[{[]/g) ?? []).length - (dong.match(/[}\]]/g) ?? []).length;
  }
  return ra;
}

/**
 * Trường backend trả mà admin **cố ý ⛔ đọc**, kèm lý do.
 *
 * ⛔ Thêm dòng vào đây phải viết lý do: một danh sách miễn ⛔ có lý do là một chỗ để giấu trường bị
 * quên, đúng thứ `GIU_NGUYEN_KHI_NULL` đã làm với `authorPublicId` suốt từ T47.17.
 */
const CO_Y_KHONG_DOC: Record<string, string> = {};

describe('Chi tiết bài viết — chiều RA đủ trường (T84.5)', () => {
  it('⚠ chống tập rỗng: đọc được cả hai record Java và cả hai interface TS', () => {
    expect(truongCuaRecord(DTOS, 'ArticleDetail').length).toBeGreaterThanOrEqual(18);
    expect(truongCuaRecord(DTOS, 'ArticleSummary').length).toBeGreaterThanOrEqual(7);
    expect(thuocTinhCua('ArticleDetail').size).toBeGreaterThanOrEqual(18);
    expect(thuocTinhCua('ArticleSummary').size).toBeGreaterThanOrEqual(7);
  });

  it('⭐ phép đọc phân biệt được hai trạng thái — tự kiểm trên dữ liệu GIẢ', () => {
    // Một bộ canh ⛔ chứng minh được nó phân biệt được thì cái xanh của nó ⛔ nói gì (luật 1).
    const gia = 'public record Gia(UUID publicId, String authorName, List<String> ten) {';
    expect(truongCuaRecord(gia, 'Gia')).toEqual(['publicId', 'authorName', 'ten']);
    expect(truongCuaRecord(gia, 'Gia')).not.toContain('viewCount');
  });

  it.each(['ArticleDetail', 'ArticleSummary'])(
    '⭐⭐ mọi trường %s backend trả đều có trong types.ts',
    (ten) => {
      const khaiTs = thuocTinhCua(ten);
      const thieu = truongCuaRecord(DTOS, ten).filter(
        (t) => !khaiTs.has(t) && !(t in CO_Y_KHONG_DOC),
      );
      expect(
        thieu,
        `${ten}: backend trả trường này mà admin ⛔ khai ⇒ nó về tới trình duyệt rồi bị vứt, ⛔ một dòng lỗi`,
      ).toEqual([]);
    },
  );

  it('⭐ tác giả nằm trong CẢ HAI phía — chống việc bài trên xanh vì đọc hụt', () => {
    expect(truongCuaRecord(DTOS, 'ArticleDetail')).toEqual(
      expect.arrayContaining(['authorPublicId', 'authorName']),
    );
    expect([...thuocTinhCua('ArticleDetail')]).toEqual(
      expect.arrayContaining(['authorPublicId', 'authorName']),
    );
    expect(truongCuaRecord(DTOS, 'ArticleSummary')).toContain('authorName');
    expect([...thuocTinhCua('ArticleSummary')]).toContain('authorName');
  });

  it('⛔ mọi dòng miễn trừ đều có lý do ≥ 40 ký tự', () => {
    for (const [truong, lyDo] of Object.entries(CO_Y_KHONG_DOC)) {
      expect(lyDo.length, `miễn trừ "${truong}" thiếu lý do đủ dài`).toBeGreaterThanOrEqual(40);
    }
  });
});
