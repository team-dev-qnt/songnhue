import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * Mỗi phương thức của client CMS phải có **một màn hình gọi nó** — luật 27, câu hỏi số 4 của
 * checklist §7.3 (`phase2-plan.md`).
 *
 * ## Vì sao bài này tồn tại
 *
 * Checklist sáu câu của dự án hỏi cho mỗi cột/khoá/tham số mới: *có endpoint ghi không*, và *có màn
 * hình nào gọi endpoint đó không*. Ba bộ canh đã có phủ được vế **cột** và vế **khoá `settings`**
 * (`CotPhase2CoDocGhiTest`, `PortalSettingsReadTest`, `HydroSettingsReadTest`). Vế **endpoint ↔ màn
 * hình** thì chưa cái nào chạm tới — và đó chính là vế mà lượt rà 28/8 tìm ra hai lần
 * (*"`PUT` không màn hình nào gọi"*, *"component có ba props mà nơi gọi truyền rỗng"*), rồi T28.47
 * phải dọn bằng tay lần nữa.
 *
 * Triệu chứng luôn im lặng: backend có endpoint, có phân quyền, có bài kiểm HTTP xanh — và **người
 * dùng không có nút nào để bấm**. Không lỗi, không log, không ai đếm.
 *
 * ## ⚠ Phạm vi tự khai (luật 28)
 *
 * Bài này soi **đúng một tệp**: `features/cms/api.ts`. Đó không phải sự lười — đo 08/09/2026, nó là
 * **client tập trung DUY NHẤT** của `admin-app`. 47 tệp còn lại gọi `api.get/post/put/delete` thẳng
 * trong chính component, nên ở đó nơi gọi *là* màn hình và khuyết tật này không biểu diễn được.
 *
 * <p>⛔ Ngày nào có feature thứ hai dựng client tập trung, tệp ấy phải được thêm vào {@link CLIENT} —
 * và {@link boDoThayDuTepClient} sẽ đỏ để nhắc, thay vì để bộ canh âm thầm hẹp lại.
 */

/** Client tập trung được soi. Thêm tệp mới vào đây khi có feature thứ hai dựng client riêng. */
const CLIENT = ['frontend/admin-app/src/features/cms/api.ts'];

const GOC_MA = 'frontend/admin-app/src';

/**
 * ⛔ Endpoint đã dựng mà **chưa màn hình nào gọi** — đo 08/09/2026.
 *
 * Đây **không** phải danh sách miễn trừ theo nghĩa "chấp nhận được". Nó là một **sổ nợ đọc được bằng
 * máy**: mỗi dòng là một chức năng backend đã trả tiền để dựng và người dùng chưa chạm tới được. Bài
 * kiểm đòi tập này **khớp CHÍNH XÁC** với thực tế theo cả hai chiều, nên:
 *
 * - thêm một endpoint mới mà quên màn hình ⇒ **đỏ**;
 * - dựng xong màn hình mà quên xoá dòng ở đây ⇒ **cũng đỏ**, danh sách buộc phải teo đi.
 */
const CHUA_CO_MAN_HINH: Record<string, string> = {
  bannerImageUrl:
    'GET /banners/{id}/image-url — cặp đọc của replaceBannerImage; hiện BannersTab dựng URL ảnh theo đường khác nên nó chưa có người dùng.',
  fileUrl:
    'GET /media/files/{id}/url — cùng lý do với bannerImageUrl: MediaBrowser hiện lấy ảnh qua đường /public/files/ chứ không qua URL ký sẵn.',
  deleteContactNote:
    'DELETE /contacts/notes/{id} — người xử lý ghi được ghi chú nội bộ vào một liên hệ nhưng KHÔNG xoá được ghi chú gõ nhầm.',
  pendingFeedbackCount:
    'GET /feedbacks/pending-count — số góp ý chờ duyệt, dựng để làm phù hiệu trên menu mà phù hiệu ấy chưa được vẽ.',
};

// ---------------------------------------------------------------------------

/** ⚠ Không dùng `import.meta.url` — Vite đổi nó thành URL `http://` trong jsdom (xem error-map.test.ts). */
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

/**
 * Tên các phương thức khai trong client.
 *
 * Bắt cả hai lối viết của object literal: `ten(...)` và `ten: (...) =>`. Neo vào **đúng hai dấu cách
 * đầu dòng** để không nhặt nhầm hàm lồng bên trong thân phương thức.
 */
function phuongThucClient(nguon: string): string[] {
  const ten = new Set<string>();
  for (const m of nguon.matchAll(/^ {2}([a-z][A-Za-z0-9_]*)\s*(?:\(|:\s*(?:async\s*)?\()/gm)) {
    ten.add(m[1]);
  }
  return [...ten].sort();
}

/** Số lời gọi `ten(` ở mọi tệp nguồn KHÁC, đã bỏ bài kiểm. */
function soNoiGoi(ten: string, tep: { duong: string; noiDung: string }[], boQua: string[]): number {
  const mau = new RegExp(`\\b${ten}\\s*\\(`, 'g');
  let dem = 0;
  for (const t of tep) {
    if (boQua.some((b) => t.duong.endsWith(b)) || /\.test\.tsx?$/.test(t.duong)) {
      continue;
    }
    dem += (t.noiDung.match(mau) ?? []).length;
  }
  return dem;
}

function doMoCoi(): { tatCa: string[]; moCoi: string[] } {
  const duongClient = CLIENT.map((c) => timTuGocKho(c));
  const goc = timTuGocKho(GOC_MA);
  const tep = moiTepNguon(goc).map((duong) => ({ duong, noiDung: readFileSync(duong, 'utf8') }));

  const tatCa = duongClient.flatMap((d) => phuongThucClient(readFileSync(d, 'utf8')));
  const moCoi = tatCa.filter((ten) => soNoiGoi(ten, tep, CLIENT) === 0).sort();
  return { tatCa, moCoi };
}

// ---------------------------------------------------------------------------

describe('Client CMS: mọi phương thức đều có màn hình gọi (luật 27, §7.3 câu 4)', () => {
  it('⭐⭐ tập endpoint chưa có màn hình khớp CHÍNH XÁC sổ nợ — cả hai chiều', () => {
    const { moCoi } = doMoCoi();
    const daKhai = Object.keys(CHUA_CO_MAN_HINH).sort();

    expect(
      moCoi,
      [
        'Tập "endpoint chưa có màn hình" lệch sổ nợ CHUA_CO_MAN_HINH.',
        '',
        `  Đo được : ${JSON.stringify(moCoi)}`,
        `  Sổ khai : ${JSON.stringify(daKhai)}`,
        '',
        'Thừa ở "đo được" = một endpoint vừa dựng mà chưa màn hình nào gọi — backend đã trả tiền',
        'để dựng và người dùng không có nút nào bấm (luật 27, triệu chứng luôn im lặng).',
        'Thừa ở "sổ khai" = màn hình đã dựng xong mà quên xoá dòng nợ — danh sách phải TEO ĐI.',
      ].join('\n'),
    ).toEqual(daKhai);
  });

  it('⛔ mỗi dòng nợ phải nói ra CHUYỆN GÌ mất đi, không chỉ tên endpoint', () => {
    // Một danh sách miễn trừ không có lý do đo được sẽ phình ra trong im lặng: người sau chỉ cần
    // thêm một dòng là hết đỏ. Ngưỡng 60 ký tự ép người thêm phải viết ra hậu quả.
    for (const [ten, lyDo] of Object.entries(CHUA_CO_MAN_HINH)) {
      expect(
        lyDo.length,
        `lý do của \`${ten}\` quá ngắn — phải nói ra người dùng mất gì`,
      ).toBeGreaterThanOrEqual(60);
    }
  });

  it('⛔ bộ dò thật sự dò được — chống xanh trên tập rỗng', () => {
    const { tatCa, moCoi } = doMoCoi();

    // Quy tắc 7 + quy tắc 32: một con số đếm được là thứ duy nhất phân biệt "không có vi phạm"
    // với "bộ dò đã chết". Đo 08/09/2026: 62 phương thức, 7 mồ côi → **4** sau khi T37.15 dựng
    // renameFolder · deleteFolder · replaceBannerImage. Danh sách phải TEO ĐI, ⛔ không phình ra.
    expect(
      tatCa.length,
      'không bóc được phương thức nào — client đổi cách khai?',
    ).toBeGreaterThanOrEqual(50);
    expect(moCoi.length).toBeLessThan(tatCa.length);

    // Đối chứng PHẢI-TÌM-THẤY và PHẢI-KHÔNG-TÌM-THẤY cho chính bộ bóc tên.
    const mau = [
      '  listArticles(): Promise<void> {',
      '  taoBai: async (x: number) => {',
      '    khongPhaiPhuongThuc() {',
    ].join('\n');
    expect(phuongThucClient(mau)).toEqual(['listArticles', 'taoBai']);
  });

  it('⛔ bộ canh thấy đủ tệp client — không âm thầm hẹp lại', () => {
    // Ngày có feature thứ hai dựng client tập trung, dòng này đỏ và buộc phải cập nhật CLIENT.
    const goc = timTuGocKho(GOC_MA);
    const clientTapTrung = moiTepNguon(goc).filter(
      (d) => /\/api\.ts$/.test(d) && !/\.test\./.test(d),
    );

    expect(
      clientTapTrung.length,
      `Tìm thấy ${clientTapTrung.length} tệp \`api.ts\` trong admin-app nhưng CLIENT chỉ khai ${CLIENT.length}. ` +
        'Thêm tệp mới vào CLIENT, nếu không bộ canh này im lặng bỏ qua nó.',
    ).toEqual(CLIENT.length);
  });
});

/** Tên hàm nhắc tới trong javadoc lớp — giữ ở đây để đổi tên hàm thì javadoc đỏ theo. */
export const boDoThayDuTepClient = 'bộ canh thấy đủ tệp client — không âm thầm hẹp lại';
