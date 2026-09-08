import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import { coNoiDungThuc, KHOI_CO_NOI_DUNG } from './noiDungCoThuc';

/**
 * **Vị từ "bài viết có nội dung thật" phải giống nhau ở hai bản** — T41.21.
 *
 * Bản Java (`ArticleService.KHOI_CO_NOI_DUNG` + `lamSachNoiDung`) là **chốt chặn**: nó ném
 * `CMS-2023`. Bản TypeScript ở đây chỉ để người soạn bài biết ngay, thay vì gõ xong cả bài mới bị
 * từ chối.
 *
 * ⚠ Hai bản lệch nhau cho ra đúng hai kiểu hỏng, và cả hai đều đọc như lỗi hệ thống:
 * - TS rộng hơn Java ⇒ màn hình báo hợp lệ, bấm Lưu thì máy chủ từ chối.
 * - Java rộng hơn TS ⇒ màn hình chặn một bài mà máy chủ sẵn sàng nhận.
 *
 * Nên bài này ⛔ **không chép lại danh sách thẻ** — nó đọc thẳng tệp Java (cùng khuôn
 * `alertLevelColors.test.ts` và `error-map.test.ts`). Chép lại thì chính bản chép đó lại là thứ
 * phải nhớ cập nhật.
 */

const DUONG_DAN_TUONG_DOI =
  'backend/content/src/main/java/com/songnhue/content/application/ArticleService.java';

/**
 * ⚠ ⛔ Không ghép `'..', '..'` cứng: lệnh chạy được từ `frontend/` lẫn `frontend/admin-app/`.
 * ⚠ ⛔ Không dùng `import.meta.url`: Vitest chạy trong jsdom, Vite đổi nó thành URL `http://`.
 */
function timTepJava(): string {
  let hienTai = process.cwd();
  for (let sau = 0; sau < 6; sau += 1) {
    const ungVien = join(hienTai, DUONG_DAN_TUONG_DOI);
    if (existsSync(ungVien)) {
      return ungVien;
    }
    const cha = dirname(hienTai);
    if (cha === hienTai) {
      break;
    }
    hienTai = cha;
  }
  throw new Error(`Không tìm thấy ${DUONG_DAN_TUONG_DOI} tính từ ${resolve(process.cwd())}`);
}

const MA_JAVA = readFileSync(timTepJava(), 'utf8');

/** Rút danh sách thẻ trong `Pattern.compile("<(?:img|iframe|table|hr)\\b", …)` của tệp Java. */
function theCuaBackend(): string[] {
  const khoi = /KHOI_CO_NOI_DUNG\s*=\s*[\s\S]*?Pattern\.compile\(\s*"<\(\?:([a-z|]+)\)/.exec(
    MA_JAVA,
  );
  if (!khoi) {
    throw new Error(
      '⛔ Không tìm thấy `KHOI_CO_NOI_DUNG = Pattern.compile("<(?:…)` trong ArticleService.java. ' +
        'Hoặc hằng đã đổi tên, hoặc chốt chặn CMS-2023 đã bị gỡ — cả hai đều phải là quyết định ' +
        'có ý thức, và cả hai đều phải sửa bài kiểm này cùng lượt.',
    );
  }
  return khoi[1].split('|');
}

describe('coNoiDungThuc — chặn đúng bài rỗng', () => {
  it('⭐ trình soạn thảo trống trả `<p></p>` — phải là RỖNG', () => {
    // Đây là chuỗi TipTap thật sự gửi, ⛔ không phải chuỗi rỗng. Đó là lý do `required` của AntD
    // và `@NotBlank` của backend đều cho nó qua.
    expect(coNoiDungThuc('<p></p>')).toBe(false);
  });

  it('chuỗi rỗng và `undefined` cũng là rỗng', () => {
    expect(coNoiDungThuc('')).toBe(false);
    expect(coNoiDungThuc(undefined)).toBe(false);
    expect(coNoiDungThuc(null)).toBe(false);
  });

  it('chỉ khoảng trắng — cả `&nbsp;`, dấu cách thường, lẫn ký tự U+00A0 thật', () => {
    expect(coNoiDungThuc('<p>&nbsp;</p>')).toBe(false);
    expect(coNoiDungThuc('<p>  </p>')).toBe(false);
    // ⚠ Viết bằng escape, ⛔ không dán ký tự thật: dán vào thì dòng này trông y hệt dòng trên và
    //   vế "U+00A0" trong tên bài kiểm thành một lời khai không có gì đứng sau.
    expect(coNoiDungThuc('<p>\u00A0\u00A0</p>')).toBe(false);
    // Jsoup trả `&nbsp;` về dạng ký tự, nên chuỗi backend thấy là dạng thứ hai — cả hai phải chặn.
    expect(coNoiDungThuc('\u00A0')).toBe(false);
  });

  it('nhiều đoạn văn rỗng lồng nhau vẫn là rỗng', () => {
    expect(coNoiDungThuc('<p></p><p></p><blockquote><p></p></blockquote>')).toBe(false);
  });
});

describe('coNoiDungThuc — ⛔ KHÔNG chặn nhầm (luật 9)', () => {
  it('bài có chữ thì hợp lệ', () => {
    expect(coNoiDungThuc('<p>Một câu.</p>')).toBe(true);
  });

  it('⭐⭐ bài chỉ gồm MỘT TẤM ẢNH vẫn hợp lệ — bản tin ảnh là hình dạng có thật', () => {
    expect(coNoiDungThuc('<figure><img src="/api/v1/public/files/x" alt=""></figure>')).toBe(true);
  });

  it('⭐ bài chỉ gồm MỘT BẢNG SỐ LIỆU vẫn hợp lệ — thông báo mực nước là hình dạng có thật', () => {
    expect(coNoiDungThuc('<table><tbody><tr><td></td></tr></tbody></table>')).toBe(true);
  });

  it('video nhúng và đường kẻ ngang cũng tính là nội dung', () => {
    expect(
      coNoiDungThuc('<p><iframe src="https://www.youtube-nocookie.com/embed/a"></iframe></p>'),
    ).toBe(true);
    expect(coNoiDungThuc('<hr>')).toBe(true);
  });

  it('⛔ tên thẻ phải khớp CẢ TỪ — `<image>` không phải `<img>`', () => {
    // Nếu phép dò dùng `includes('<img')` thì `<imgx>` hay `<image>` cũng lọt, và bài rỗng đi qua.
    expect(coNoiDungThuc('<p><image-holder></image-holder></p>')).toBe(false);
  });
});

describe('⭐⭐ Hai bản, một luật — danh sách thẻ phải khớp bản Java', () => {
  it('đọc được hằng của backend, và tệp Java không rỗng', () => {
    // conventions.md §1.5 — đọc nhầm một tệp rỗng thì phép so dưới xanh mà không so gì.
    expect(MA_JAVA.length).toBeGreaterThan(10_000);
    expect(MA_JAVA).toContain('class ArticleService');
    expect(theCuaBackend().length).toBeGreaterThanOrEqual(3);
  });

  it('danh sách thẻ của TypeScript trùng khít danh sách của Java', () => {
    expect([...KHOI_CO_NOI_DUNG].sort()).toEqual([...theCuaBackend()].sort());
  });

  it('⭐ backend thật sự CÓ chốt chặn — không chỉ có một hằng không ai đọc (quy tắc 15)', () => {
    expect(MA_JAVA).toContain('ErrorCode.CMS_2023');
    expect(MA_JAVA).toContain('KHOI_CO_NOI_DUNG.matcher(sach).find()');
  });
});
