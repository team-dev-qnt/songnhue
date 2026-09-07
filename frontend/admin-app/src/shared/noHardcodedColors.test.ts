import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

import * as tokens from 'design-tokens';
import { describe, expect, it } from 'vitest';

import { boChuThich } from '../testsupport/boChuThich';

/**
 * **Màu của `admin-app` chỉ được đến từ `design-tokens`** — `ui-styles.md` §2.1, nợ **T25.23**.
 *
 * <h2>⛔⛔ Vì sao bộ canh này ra đời muộn hơn nợ 10 ngày, và nợ lớn lên gấp 2,4 lần trong lúc đó</h2>
 *
 * `noHardcodedColors.test.ts` của `public-web` ra đời 28/08/2026 và **cố ý** chỉ phủ cổng công
 * khai; javadoc của nó ghi thẳng giới hạn ấy kèm số đo *"admin-app còn 25 mã hex ở 12 tệp"*. Ghi
 * ra giới hạn là đúng (luật 28). Nhưng **một giới hạn được ghi ra vẫn là một giới hạn**: suốt 10
 * ngày sau đó ⛔ không có gì đỏ khi ai đó thêm màu mới vào `admin-app`, và đo lại ngày 07/09/2026
 * ra **62 lượt / 16 tệp** — gấp **2,4 lần**. Bốn tệp mà số đo cũ không biết, trong đó
 * `richTextEditor.css` một mình mang **18 mã** và nó ra đời ở WS-41 ngày **04/09** — tức nợ lớn
 * thêm *sau khi* đã có người đo và ghi sổ.
 *
 * <p>⇒ Bài học vào §11: **ghi số đo vào sổ ⛔ không chặn được gì; chỉ một bộ canh mới chặn.** Một
 * con số trong sổ đứng yên từ ngày viết, còn mã thì không.
 *
 * <h2>⭐ Bộ canh này làm HAI việc, và việc thứ hai mới là việc mới</h2>
 *
 * <ol>
 *   <li><b>Trần chỉ-được-giảm</b> ({@link NGUONG}). Không đòi 0 ngay — đòi 0 ngay thì hoặc phải
 *       hoãn bộ canh tới khi dọn xong 62 chỗ (và trong lúc hoãn thì nợ lại lớn lên, đúng vòng vừa
 *       xảy ra), hoặc phải khai một danh sách miễn trừ mà danh sách miễn trừ thì <b>biến "tôi đã
 *       nghĩ tới" thành "tôi được phép quên"</b>. Một con số thì ⛔ không quên được: mỗi lượt thêm
 *       màu mới là một lượt CI đỏ.
 *   <li><b>Chống trôi giữa hằng số và token</b> ({@link #hangSoTrongCssPhaiTrungTokenNoGoiTen}).
 *       `admin-global.css` tự cho phép <i>"hằng số trùng với tokens"</i> ở ngay dòng 7 của nó, và
 *       7 mã hex bên dưới đều kèm chú thích gọi đúng tên token — <i>người viết BIẾT</i>. Nhưng
 *       "trùng" là một lời hứa, ⛔ không phải một ràng buộc: đổi `brandColors.primary` trong
 *       `design-tokens` thì bảy chỗ chép tay <b>ở lại phía sau</b>, và ⛔ không có gì đỏ. Bài này
 *       biến lời hứa ấy thành bất biến đo được (luật 14).
 * </ol>
 *
 * <h2>⚠ Phạm vi của CHÍNH bộ canh này, nói ra để ⛔ không lặp lại luật 28</h2>
 *
 * Phủ `.ts` · `.tsx` · `.css` dưới `admin-app/src`, ⛔ trừ tệp `.test.` và `testsupport/`. ⛔ Chưa
 * phủ: màu đặt qua `theme.useToken()` của AntD (⛔ không phải hex nên ⛔ không thấy được ở tầng
 * này), và `design-tokens` — nơi <b>duy nhất</b> một mã màu được phép viết ra.
 *
 * <p>⚠⚠ Đo 07/09: một phần đáng kể trong 26 mã màu khác nhau là **bảng màu của AntD**
 * (`#1677ff`, `#52c41a`, `#f5222d`, `#fa8c16`, `#d9d9d9`, `#f0f0f0`…). Với chúng, đường đúng
 * thường ⛔ **không phải** thêm vào `design-tokens` mà là dùng `theme.useToken()` — thêm bản sao
 * của bảng màu AntD vào tokens là dựng nguồn sự thật thứ hai cho cùng một dải màu.
 *
 * ⚠ Bỏ chú thích trước khi soi ở bài trần — javadoc này gọi tên hàng chục mã hex, và một bộ canh
 * theo văn bản đỏ trên chính tệp nó canh là lỗi đã mắc **ba lần** ở đợt WS-36.
 */

/** Hex 3 hoặc 6 chữ số — dạng duy nhất Tailwind/CSS nhận. */
const HEX = /#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?\b/g;

/**
 * ⛔ **Trần CHỈ ĐƯỢC GIẢM** — số mã màu **chưa ghim** vào token. Đo 07/09/2026 = **54**.
 *
 * <p>Đường đi: 62 mã tổng → ghim 7 mã của `admin-global.css` vào token (và lượt ghim ấy **bắt được
 * 2 mã đã trôi khỏi token thật**) → còn **54** chưa ghim.
 *
 * <p>Dọn được chỗ nào thì **hạ số này xuống** trong cùng commit — hạ nó là phần *"đã trả nợ"* của
 * lượt dọn, ⛔ không phải một việc dọn dẹp về sau. Ai cần nâng nó lên thì đang thêm nợ, và phải
 * nói ra lý do ở đây.
 *
 * <p>⚠ Hai đường hạ nó, và chúng ⛔ không tương đương: <b>ghim</b> (thêm chú thích `/* token *\/`
 * đúng giá trị) chỉ chặn trôi; <b>thay bằng token thật</b> mới xoá hẳn bản sao. Ghim là bước đệm
 * hợp lệ cho CSS thuần — nơi ⛔ không import TypeScript được.
 */
const NGUONG = 54;

const THU_MUC_BO_QUA = new Set(['testsupport']);

function timGoc(): string {
  let hienTai = process.cwd();
  for (let sau = 0; sau < 6; sau += 1) {
    const ungVien = join(hienTai, 'src', 'admin-global.css');
    if (existsSync(ungVien)) return join(hienTai, 'src');
    const cha = dirname(hienTai);
    if (cha === hienTai) break;
    hienTai = cha;
  }
  throw new Error(`⛔ Không tìm thấy admin-app/src tính từ ${resolve(process.cwd())}`);
}

const GOC = timGoc();

function timNguon(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) {
      return THU_MUC_BO_QUA.has(ten) ? [] : timNguon(duong);
    }
    const laNguon = ten.endsWith('.tsx') || ten.endsWith('.ts') || ten.endsWith('.css');
    return laNguon && !ten.includes('.test.') ? [duong] : [];
  });
}

const TEP = timNguon(GOC).map((duong) => ({
  ten: duong.slice(GOC.length + 1),
  tho: readFileSync(duong, 'utf8'),
}));

/** Mọi giá trị màu do `design-tokens` công bố, phẳng hoá thành `đường.dẫn → giá trị`. */
function bangToken(): Map<string, string> {
  const ra = new Map<string, string>();
  for (const [nhom, giaTri] of Object.entries(tokens)) {
    if (giaTri && typeof giaTri === 'object') {
      for (const [khoa, v] of Object.entries(giaTri as Record<string, unknown>)) {
        if (typeof v === 'string') ra.set(`${nhom}.${khoa}`, v.toLowerCase());
      }
    }
  }
  return ra;
}

const TOKEN = bangToken();

/** `#c8def7; /* brandColors.primaryLight *\/` → cặp (mã, tên token nó tự khai). */
const HEX_KEM_CHU_THICH = /(#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?)\b[^\n]*?\/\*\s*([\w.]+)\s*\*\//g;

/**
 * Gỡ khỏi bản đếm những mã **đã GHIM**: có chú thích gọi tên một token và **trùng đúng giá trị**
 * token ấy. Trả kèm số cặp đã soi và danh sách lệch.
 *
 * <p>⭐ Vì sao mã đã ghim ⛔ không tính là vi phạm: thứ bộ canh này chống ⛔ không phải *"có ký tự
 * `#` trong tệp"* mà là **sự TRÔI** — một bản sao thầm lặng ở lại phía sau khi token đổi. Một mã
 * được máy đối chiếu mỗi lượt CI thì ⛔ không trôi được nữa. Đếm nó là đếm sai đơn vị, và tệ hơn:
 * nó khiến việc *ghim* ⛔ không làm con số nhúc nhích, tức người dọn ⛔ không thấy công của mình.
 *
 * <p>⚠ Mã có chú thích mà **lệch** thì ⛔ KHÔNG được gỡ — nó vừa đếm vào trần vừa bị báo lệch.
 */
function boGhim(tho: string): { conLai: string; soCap: number; lech: string[] } {
  const lech: string[] = [];
  let soCap = 0;
  const conLai = tho.replace(HEX_KEM_CHU_THICH, (nguyen, ma: string, duongDan: string) => {
    const mong = TOKEN.get(duongDan);
    if (mong === undefined) return nguyen; // chú thích ⛔ không trỏ token nào — vẫn là vi phạm
    soCap += 1;
    if (mong !== ma.toLowerCase()) {
      lech.push(`${ma} tự khai là \`${duongDan}\`, mà token nay là \`${mong}\``);
      return nguyen;
    }
    return nguyen.replace(ma, '<đã-ghim>');
  });
  return { conLai, soCap, lech };
}

const DA_XU_LY = TEP.map(({ ten, tho }) => ({ ten, ...boGhim(tho) }));

const VI_PHAM = DA_XU_LY.flatMap(({ ten, conLai }) =>
  (boChuThich(conLai).match(HEX) ?? []).map((m) => `${ten}: ${m}`),
);

const SO_CAP_DA_GHIM = DA_XU_LY.reduce((t, m) => t + m.soCap, 0);
const LECH = DA_XU_LY.flatMap(({ ten, lech }) => lech.map((l) => `${ten}: ${l}`));

describe('Màu của admin-app chỉ đến từ design-tokens', () => {
  it('⚠ tìm được tệp để soi, GỒM CẢ `.css` — bài chạy qua tập rỗng thì xanh mà không canh gì (luật 7)', () => {
    // ⛔ Vế này là thứ ngăn bộ canh chết âm thầm khi ai đó đổi cấu trúc thư mục: một `timNguon`
    //    trả mảng rỗng làm MỌI bài dưới đây xanh trọn vẹn.
    expect(TEP.length).toBeGreaterThanOrEqual(100);
    // ⭐ Và phải có `.css` — 25/62 mã nằm trong hai tệp CSS thuần. Một bộ canh chỉ soi `.tsx` sẽ
    //    báo 37 và đọc như một tin tốt.
    expect(TEP.filter((t) => t.ten.endsWith('.css')).length).toBeGreaterThanOrEqual(2);
    // Bỏ chú thích xong vẫn phải còn mã: `boChuThich` hỏng trả chuỗi rỗng cũng làm mọi bài xanh.
    expect(TEP.reduce((t, m) => t + boChuThich(m.tho).length, 0)).toBeGreaterThan(200_000);
  });

  it('⛔⛔ số mã màu ghi cứng CHỈ ĐƯỢC GIẢM', () => {
    expect(
      VI_PHAM.length,
      `⛔ ${VI_PHAM.length} mã màu ghi cứng, trần đang là ${NGUONG} (đo 07/09/2026).\n` +
        'Dọn được thì HẠ trần xuống trong cùng commit. Thêm màu mới thì:\n' +
        '  · màu thương hiệu / trạng thái nghiệp vụ → `frontend/design-tokens/src/index.ts`\n' +
        '  · màu thuộc bảng màu AntD → `theme.useToken()`, ⛔ ĐỪNG chép vào design-tokens\n' +
        `Vi phạm:\n${VI_PHAM.join('\n')}`,
    ).toBeLessThanOrEqual(NGUONG);
  });

  it('⭐⭐ hằng số đã GHIM phải TRÙNG token mà nó tự gọi tên — chống trôi (luật 14)', () => {
    // `admin-global.css:7` tự cho phép "hằng số trùng với tokens". Bài này biến chữ *trùng* từ
    // một lời hứa thành một ràng buộc: đổi token mà quên chỗ chép tay là ĐỎ, ⛔ không phải im lặng.
    //
    // ⭐⭐ Nó bắt được lỗi THẬT ngay lượt chạy đầu tiên (07/09/2026): `::selection` của toàn bộ
    //    admin khai `#e6f4ff /* brandColors.primaryLight */` và `#0958d9 /* brandColors.primary */`
    //    — hai giá trị xanh AntD cũ — trong khi token đã chuyển sang dải navy `#c8def7`/`#165bb6`
    //    từ lượt đổi nhận diện. Màu bôi chọn lệch thương hiệu, chú thích khẳng định là khớp, và
    //    ⛔ không có gì đỏ. Đây đúng là *"bảy chỗ chép tay ở lại phía sau"* mà javadoc của bộ canh
    //    `public-web` đã tiên đoán bằng chữ, 10 ngày trước khi có ai đo.
    expect(LECH, LECH.join('\n')).toEqual([]);
  });

  it('⚠ có cặp GHIM thật để soi — ⛔ không có cặp nào thì bài chống trôi ⛔ không canh gì (luật 7)', () => {
    expect(
      SO_CAP_DA_GHIM,
      '⛔ Không tìm thấy cặp `#hex /* token.path */` nào — mẫu khớp đã chết?',
    ).toBeGreaterThanOrEqual(7);
  });

  it('⛔ kiểm chứng ngược: hai mẫu khớp bắt được đúng thứ chúng nói là bắt', () => {
    // Luật 1 — không có bài này thì một regex gõ sai cho hai bài trên xanh trọn vẹn mãi mãi.
    expect('className="bg-[#061b37]"'.match(HEX)).toEqual(['#061b37']);
    expect('background: #fff;'.match(HEX)).toEqual(['#fff']);
    // …và ⛔ không bắt nhầm thứ ⛔ không phải màu:
    expect('href="/bai-viet#muc-2"'.match(HEX)).toBeNull();
    expect('const n = 12;'.match(HEX)).toBeNull();

    // Mẫu cặp: bắt được, và đọc đúng tên token.
    const cap = [
      ...'  background: #e6f4ff; /* brandColors.primaryLight */'.matchAll(HEX_KEM_CHU_THICH),
    ];
    expect(cap).toHaveLength(1);
    expect(cap[0][1]).toBe('#e6f4ff');
    expect(cap[0][2]).toBe('brandColors.primaryLight');
  });

  it('⭐ bảng token đọc được thật, và ⛔ không rỗng — vế chống bài trên xanh vì Map rỗng', () => {
    // ⛔ `TOKEN` rỗng thì `mong === undefined` ở MỌI cặp ⇒ `lech` rỗng ⇒ bài chống trôi xanh mà
    //    ⛔ không so một lần nào. Khẳng định về SỐ LƯỢNG ở đây ⛔ không chia sẻ giả định nào với
    //    phép so ấy (luật 29).
    expect(TOKEN.size).toBeGreaterThanOrEqual(30);
    expect(TOKEN.get('neutralColors.border')).toBe('#d9d9d9');
    expect(TOKEN.get('brandColors.primaryLight')).toBeDefined();
  });
});
