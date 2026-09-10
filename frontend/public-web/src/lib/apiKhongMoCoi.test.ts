import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * Mỗi hàm của client cổng công khai phải có **một trang gọi nó** — luật 27, câu hỏi số 4 của
 * checklist §7.3.
 *
 * ## Vì sao bài này ra đời (T47.10)
 *
 * Bộ canh anh em `admin-app/src/features/cms/apiKhongMoCoi.test.ts` đã canh đúng câu hỏi này từ
 * 08/09 — nhưng **chỉ trên `admin-app`**. Đó là lần thứ **TƯ** cùng hình dạng luật 28 trong kho
 * (`CiPathFilterTest` × 2, `NginxSecurityHeadersTest`, `PortalSettingsReadTest`): một bộ canh đúng
 * luật, hẹp hơn nơi nó phải chặn, và **cái xanh của nó đọc như một lời bảo đảm**.
 *
 * Khe ấy ⛔ không rỗng. Đo 10/09/2026, `public-web/src/lib/api.ts` có **19 hàm export** và một
 * trong số đó — `getWaterLevels()` → `GET /hydro/muc-nuoc` — có **0 nơi gọi trong toàn bộ
 * frontend**. Nó ⛔ không chết vì bị bỏ quên lúc dựng: WS-44 thay bảng ấy bằng lưới
 * `getWaterLevelGrid()` → `/hydro/luoi-muc-nuoc`, và ⛔ không ai gỡ hàm cũ.
 *
 * ⚠⚠ Và **hai javadoc vẫn khai nó là nguồn**: `WaterLevelBlock.tsx` và trang
 * `muc-nuoc-luong-mua/page.tsx` đều viết *"Số đến từ `GET /api/v1/public/hydro/muc-nuoc`"* trong
 * khi cả hai nhận `LuoiMucNuoc` từ endpoint lưới. Đúng hình dạng T46.7 — **một chú thích ⛔ không
 * phải một đường đọc**, và ở đây nó còn chỉ sai chỗ cho người sửa tiếp theo.
 *
 * ## ⚠ Vì sao đếm CẢ lời gọi bên trong `api.ts` (khác bộ canh anh em)
 *
 * `admin-app` khai client bằng một object literal nên phương thức hiếm khi gọi nhau, và bộ canh ở
 * đó **bỏ qua** chính tệp client. Ở đây thì ngược: 16 hàm bọc endpoint đều gọi `apiGet<T>()`. Bỏ
 * qua `api.ts` sẽ biến `apiGet`/`apiGetWithMeta` thành hai **dương tính giả**, và cách rẻ nhất để
 * dập chúng là thêm hai dòng ngoại lệ — tức khoét rỗng chính bộ canh.
 *
 * ⇒ Đếm mọi nơi gọi kể cả trong `api.ts` là phép đo **đúng nghĩa với-tới-được**: hàm A gọi hàm B,
 * A có trang dùng ⇒ B có người dùng. Dương tính giả bị giết bằng **cấu trúc**, ⛔ không bằng một
 * danh sách miễn trừ (cùng lập luận đã dùng ở `AuditRedactionRuleTest`, T48.1).
 *
 * ⚠ Cái giá phải khai (luật 28): một **cụm** hàm chết mà chỉ gọi lẫn nhau sẽ đi lọt. Đo 10/09 thì
 * cụm ấy ⛔ không tồn tại — 16/19 hàm nối thẳng tới một trang.
 */

/** Client tập trung được soi. Thêm tệp mới vào đây khi có tệp `lib/api*.ts` thứ hai. */
const CLIENT = ['frontend/public-web/src/lib/api.ts'];

const GOC_MA = 'frontend/public-web/src';

/**
 * ⛔ Endpoint đã dựng mà **chưa trang nào gọi** — đo 10/09/2026.
 *
 * Đây **không** phải danh sách miễn trừ theo nghĩa "chấp nhận được". Nó là một **sổ nợ đọc được
 * bằng máy**, khớp CHÍNH XÁC theo cả hai chiều: thêm endpoint mà quên trang ⇒ **đỏ**; dựng xong
 * trang mà quên xoá dòng ⇒ **cũng đỏ**, danh sách buộc phải teo đi.
 */
const CHUA_CO_TRANG_GOI: Record<string, string> = {
  getWaterLevels:
    'GET /hydro/muc-nuoc — bảng "Mực nước, lượng mưa" bản CŨ (CR-13 · CR-33 · T35.7). WS-44 thay bằng lưới getWaterLevelGrid() → /hydro/luoi-muc-nuoc. ⭐ QuanTran chốt 10/09/2026: GIỮ endpoint và hàm bọc, ĐỂ DÀNH dùng về sau — đây là một quyết định, ⛔ không phải một thứ bị bỏ quên. ⛔ Đừng gỡ, và cũng đừng điều tra lại: câu hỏi đã hỏi và đã trả lời (T49.8).',
};

// ---------------------------------------------------------------------------

/** ⚠ Không dùng `import.meta.url` — Vite đổi nó thành URL `http://` trong jsdom. */
function timTuGocKho(duongDanTuongDoi: string): string {
  let hienTai = process.cwd();
  for (let sau = 0; sau < 6; sau += 1) {
    const thu = join(hienTai, duongDanTuongDoi);
    if (existsSync(thu)) {
      return thu;
    }
    const cha = dirname(hienTai);
    if (cha === hienTai) {
      break;
    }
    hienTai = cha;
  }
  throw new Error(`Không tìm thấy ${duongDanTuongDoi} tính từ ${resolve(process.cwd())}`);
}

function moiTepNguon(thuMuc: string): string[] {
  const ket: string[] = [];
  for (const ten of readdirSync(thuMuc)) {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) {
      ket.push(...moiTepNguon(duong));
    } else if (/\.tsx?$/.test(ten)) {
      ket.push(duong);
    }
  }
  return ket;
}

/** Tên các hàm `export function` / `export async function` ở **cấp cao nhất** của client. */
export function hamClient(nguon: string): string[] {
  const ten = new Set<string>();
  for (const m of nguon.matchAll(/^export\s+(?:async\s+)?function\s+([A-Za-z_][A-Za-z0-9_]*)/gm)) {
    ten.add(m[1]);
  }
  return [...ten].sort();
}

/**
 * Bỏ **dòng khai báo** của chính các hàm ấy trước khi đếm.
 *
 * ⛔ Thiếu bước này thì mọi hàm đều tự đếm cho mình một lời gọi ⇒ tập mồ côi **luôn rỗng**, và bộ
 * canh xanh vĩnh viễn (luật 7).
 */
function boDongKhaiBao(nguon: string): string {
  return nguon.replace(/^export\s+(?:async\s+)?function\s+[A-Za-z_][A-Za-z0-9_]*/gm, '');
}

/**
 * Bỏ **chú thích** trước khi đếm — giữ nguyên chuỗi ký tự.
 *
 * ⛔⛔ Bộ canh này bắt CHÍNH TÔI ở lượt chạy đầu (10/09/2026). Tôi vừa sửa javadoc của
 * {@code WaterLevelBlock} và viết {@code getWaterLevels()} — **kèm ngoặc** — nên bộ đếm tính một
 * **chú thích** là một lời gọi, và tập mồ côi tụt từ 1 xuống **0**: bộ canh xanh trong đúng tình
 * huống nó sinh ra để bắt. Đây là T46.7 lần thứ ba trong kho, lần này do chính người viết bộ canh
 * gây ra khi đang đi vá T46.7.
 *
 * ⚠ Phải bỏ qua chuỗi ký tự, ⛔ không được cắt thô: {@code 'https://x'} chứa {@code //}, cắt thô
 * là nuốt mất phần còn lại của dòng ⇒ giấu đi một lời gọi thật ⇒ **đỏ giả**.
 *
 * ⚠ Bộ canh anh em ở {@code admin-app} thủng **y hệt**. Đo 10/09: ở đó chú thích chưa che gì (4 mồ
 * côi giống nhau ở cả hai phép đo), nhưng lỗ vẫn còn nên nó cũng được vá cùng lượt. Hàm nhân đôi ở
 * hai workspace vì chúng ⛔ không nhập khẩu chéo được — mỗi bản mang đối chứng riêng.
 */
export function boChuThich(nguon: string): string {
  const ket: string[] = [];
  let trangThai: 'ma' | 'khoi' | 'dong' | '"' | "'" | '`' = 'ma';
  for (let i = 0; i < nguon.length; i += 1) {
    const c = nguon[i];
    const ke = nguon[i + 1] ?? '';
    if (trangThai === 'ma') {
      if (c === '/' && ke === '*') {
        trangThai = 'khoi';
        i += 1;
      } else if (c === '/' && ke === '/') {
        trangThai = 'dong';
        i += 1;
      } else if (c === '"' || c === "'" || c === '`') {
        trangThai = c;
        ket.push(c);
      } else {
        ket.push(c);
      }
    } else if (trangThai === 'khoi') {
      if (c === '*' && ke === '/') {
        trangThai = 'ma';
        i += 1;
      } else {
        ket.push(c === '\n' ? '\n' : ' ');
      }
    } else if (trangThai === 'dong') {
      if (c === '\n') {
        trangThai = 'ma';
        ket.push('\n');
      }
    } else {
      if (c === '\\') {
        ket.push('  ');
        i += 1;
      } else {
        if (c === trangThai) {
          trangThai = 'ma';
        }
        ket.push(c);
      }
    }
  }
  return ket.join('');
}

/**
 * Số lời gọi `ten(` — chấp nhận **tham số kiểu**: `apiGet<Foo>(`.
 *
 * ⚠ Bản đầu của phép đo này thiếu nhánh `<...>` và cho ra **hai dương tính giả** (`apiGet` 16 lời
 * gọi, `apiGetWithMeta` 4, đều bị đếm thành 0). Một bộ dò đếm hụt thì cách sửa rẻ nhất là thêm
 * ngoại lệ — tức tự tay tháo bộ canh (§11.17).
 */
export function soNoiGoi(ten: string, tep: { duong: string; noiDung: string }[]): number {
  const mau = new RegExp(`\\b${ten}\\s*(?:<[^<>()]*>)?\\s*\\(`, 'g');
  let dem = 0;
  for (const t of tep) {
    if (/\.test\.tsx?$/.test(t.duong)) {
      continue;
    }
    dem += (t.noiDung.match(mau) ?? []).length;
  }
  return dem;
}

function doMoCoi(): { tatCa: string[]; moCoi: string[] } {
  const duongClient = CLIENT.map((c) => timTuGocKho(c));
  const goc = timTuGocKho(GOC_MA);
  const tep = moiTepNguon(goc).map((duong) => ({
    duong,
    noiDung: boDongKhaiBao(boChuThich(readFileSync(duong, 'utf8'))),
  }));

  const tatCa = duongClient.flatMap((d) => hamClient(readFileSync(d, 'utf8')));
  const moCoi = tatCa.filter((ten) => soNoiGoi(ten, tep) === 0).sort();
  return { tatCa, moCoi };
}

// ---------------------------------------------------------------------------

describe('Client cổng công khai: mọi hàm đều có trang gọi (luật 27 · luật 28, T47.10)', () => {
  it('⭐⭐ tập endpoint chưa có trang gọi khớp CHÍNH XÁC sổ nợ — cả hai chiều', () => {
    const { moCoi } = doMoCoi();
    const daKhai = Object.keys(CHUA_CO_TRANG_GOI).sort();

    expect(
      moCoi,
      [
        'Tập "endpoint chưa có trang gọi" lệch sổ nợ CHUA_CO_TRANG_GOI.',
        '',
        `  Đo được : ${JSON.stringify(moCoi)}`,
        `  Sổ khai : ${JSON.stringify(daKhai)}`,
        '',
        'Thừa ở "đo được" = một endpoint vừa dựng mà chưa trang nào gọi — backend đã trả tiền để',
        'dựng và người dùng ⛔ không có gì để mở (luật 27, triệu chứng luôn im lặng).',
        'Thừa ở "sổ khai" = trang đã dựng xong mà quên xoá dòng nợ — danh sách phải TEO ĐI.',
      ].join('\n'),
    ).toEqual(daKhai);
  });

  it('⛔ mỗi dòng nợ phải nói ra CHUYỆN GÌ mất đi, không chỉ tên hàm', () => {
    for (const [ten, lyDo] of Object.entries(CHUA_CO_TRANG_GOI)) {
      expect(
        lyDo.length,
        `lý do của \`${ten}\` quá ngắn — phải nói ra người dùng mất gì`,
      ).toBeGreaterThanOrEqual(60);
    }
  });

  it('⛔ bộ dò thật sự dò được — chống xanh trên tập rỗng', () => {
    const { tatCa, moCoi } = doMoCoi();

    // Luật 7 + luật 32: một con số đếm được là thứ duy nhất phân biệt "⛔ không có vi phạm" với
    // "bộ dò đã chết". Đo 10/09/2026: 19 hàm export, 1 mồ côi.
    expect(tatCa.length, 'không bóc được hàm nào — client đổi cách khai?').toBeGreaterThanOrEqual(
      15,
    );
    expect(moCoi.length).toBeLessThan(tatCa.length);

    // Đối chứng PHẢI-TÌM-THẤY và PHẢI-KHÔNG-TÌM-THẤY cho chính bộ bóc tên.
    const mau = [
      'export async function getArticles(): Promise<void> {',
      'export function getBanners() {',
      '  export function hamLong() {',
      'function khongExport() {',
      'export const khongPhaiHam = 1;',
    ].join('\n');
    expect(hamClient(mau)).toEqual(['getArticles', 'getBanners']);
  });

  it('⛔ bộ đếm lời gọi phân biệt được hai trạng thái — kể cả khi có tham số kiểu', () => {
    // Luật 9. Bản đầu của bộ đếm bỏ sót `apiGet<Foo>(` và cho ra hai dương tính giả; đối chứng này
    // đỏ đúng ngày ai đó rút nhánh `<...>` ra khỏi mẫu.
    const tep = [
      { duong: 'a.ts', noiDung: 'apiGet<Foo>(x); apiGet(y);' },
      { duong: 'b.test.ts', noiDung: 'apiGet(z); apiGet(z); apiGet(z);' },
    ];
    expect(soNoiGoi('apiGet', tep), 'phải đếm cả dạng có tham số kiểu, và BỎ tệp .test.').toBe(2);
    expect(soNoiGoi('khongCoAiGoi', tep), 'tên không xuất hiện thì phải ra 0').toBe(0);
  });

  it('⛔⛔ một CHÚ THÍCH ⛔ không phải một lời gọi — và một URL ⛔ không phải một chú thích', () => {
    // T46.7, lần thứ ba trong kho. Bộ canh này đỏ đúng ngày ai đó gỡ `boChuThich` ra khỏi đường đo.
    expect(soNoiGoi('foo', [{ duong: 'a.ts', noiDung: boChuThich('// foo();') }])).toBe(0);
    expect(soNoiGoi('foo', [{ duong: 'a.ts', noiDung: boChuThich('/** {@link foo()} */') }])).toBe(
      0,
    );
    // Đối chứng PHẢI-ĐẾM-ĐƯỢC: cắt thô theo `//` sẽ nuốt mất lời gọi sau một URL ⇒ đỏ giả.
    expect(
      soNoiGoi('foo', [{ duong: 'a.ts', noiDung: boChuThich("const u = 'https://x'; foo();") }]),
      'chuỗi chứa `//` ⛔ không được coi là chú thích',
    ).toBe(1);
    expect(soNoiGoi('foo', [{ duong: 'a.ts', noiDung: boChuThich('foo(); /* foo() */') }])).toBe(1);
  });

  it('⛔ bộ canh thấy đủ tệp client — không âm thầm hẹp lại', () => {
    const goc = timTuGocKho(GOC_MA);
    const clientTapTrung = moiTepNguon(goc).filter(
      (d) => /\/lib\/api\.ts$/.test(d) && !/\.test\./.test(d),
    );

    expect(
      clientTapTrung.length,
      `Tìm thấy ${clientTapTrung.length} tệp \`lib/api.ts\` trong public-web nhưng CLIENT chỉ khai ${CLIENT.length}. ` +
        'Thêm tệp mới vào CLIENT, nếu không bộ canh này im lặng bỏ qua nó.',
    ).toEqual(CLIENT.length);
  });
});
