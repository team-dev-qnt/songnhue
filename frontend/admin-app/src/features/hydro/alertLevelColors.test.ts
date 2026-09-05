import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

import { alertLevelColorTokens, laKhoaMauHopLe, mauMucCanhBao, statusColors } from 'design-tokens';
import { describe, expect, it } from 'vitest';

/**
 * ⭐⭐ **Phép kiểm nhớ hộ hai nơi** — T35.14, luật 14.
 *
 * Danh sách khoá màu mức cảnh báo sống ở **hai** tệp mà không công cụ nào nối được:
 *
 * - `frontend/design-tokens/src/index.ts` → `alertLevelColors` (nơi có mã màu thật);
 * - `backend/hydro/.../AlertLevelService.java` → `KHOA_MAU_CHO_PHEP` (nơi chặn lượt ghi).
 *
 * Java ⛔ không import được TypeScript và ngược lại, nên sự trùng khít giữa chúng là một điều **con
 * người phải nhớ** — đúng chỗ mà luật 14 nói cần một phép kiểm nhớ hộ. Bài này đọc **thẳng tệp
 * Java** thay vì chép lại danh sách, theo đúng khuôn `error-map.test.ts` đang dùng cho mã lỗi
 * BE ↔ FE: chép lại là dựng nguồn sự thật thứ ba.
 *
 * ⚠ Hệ quả nếu ai đó thêm một slot mà chỉ sửa một tệp: bài này đỏ, và đỏ đúng chỗ.
 */

const DUONG_DAN_TUONG_DOI =
  'backend/hydro/src/main/java/com/songnhue/hydro/application/AlertLevelService.java';

/**
 * Tìm gốc kho bằng cách đi ngược lên từ thư mục đang chạy — cùng khuôn `error-map.test.ts`.
 *
 * ⚠ ⛔ Không ghép `'..', '..'` cứng: lệnh chạy được từ `frontend/` lẫn `frontend/admin-app/`, và
 * bản đầu của bài này ghép cứng nên đỏ ngay với `ENOENT` trỏ vào một đường dẫn ngoài kho.
 * ⚠ ⛔ Không dùng `import.meta.url`: Vitest chạy trong jsdom, Vite đổi nó thành URL `http://` và
 * `fileURLToPath` từ chối — lỗi hiện ra chẳng liên quan gì tới thứ đang kiểm.
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

const DUONG_DAN_JAVA = timTepJava();

/** Rút các chuỗi trong khối `KHOA_MAU_CHO_PHEP = Set.of( … );` của tệp Java. */
function khoaMauCuaBackend(): string[] {
  const ma = readFileSync(DUONG_DAN_JAVA, 'utf8');
  const khoi = /KHOA_MAU_CHO_PHEP\s*=\s*Set\.of\(([\s\S]*?)\);/.exec(ma);
  if (!khoi) {
    throw new Error(
      '⛔ Không tìm thấy `KHOA_MAU_CHO_PHEP = Set.of(...)` trong AlertLevelService.java. ' +
        'Hoặc hằng số đã bị đổi tên, hoặc đã bị xoá — cả hai đều phải là quyết định có ý thức, ' +
        'không phải hệ quả phụ. ⛔ Đừng nới mẫu khớp này cho hết đỏ.',
    );
  }
  return [...khoi[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
}

describe('bảng màu mức cảnh báo — BE ↔ FE', () => {
  it('⭐ hai danh sách khoá trùng khít', () => {
    const backend = khoaMauCuaBackend();

    // ⚠ Khẳng định về SỐ LƯỢNG đứng TRƯỚC, và nó cố ý không chia sẻ giả định nào với phép so tập
    //   hợp bên dưới (luật 29). Nếu mẫu regex trên bắt hụt và trả về mảng rỗng thì `toEqual([])`
    //   với một FE cũng rỗng vẫn xanh trọn vẹn — đúng cái bẫy đã cắn ngày 28/8.
    expect(backend.length).toBeGreaterThanOrEqual(3);
    expect(alertLevelColorTokens.length).toBe(backend.length);

    expect([...alertLevelColorTokens].sort()).toEqual([...backend].sort());
  });

  it('mọi khoá của bảng đều ra một mã màu hex thật', () => {
    for (const khoa of alertLevelColorTokens) {
      expect(mauMucCanhBao(khoa)).toMatch(/^#[0-9a-f]{6}$/i);
      expect(laKhoaMauHopLe(khoa)).toBe(true);
    }
  });

  /**
   * ⛔ Khoá lạ phải **nhìn thấy được**, ⛔ không rơi về một màu trông như đã cấu hình xong.
   *
   * Dữ liệu cũ (mức tạo trước T35.14) hoặc một lượt `PUT` thẳng qua API có thể mang khoá ngoài
   * bảng. Trả `statusColors.unknown` (xám) là câu trả lời đúng: nó khác hẳn ba màu nghiệp vụ nên
   * người vận hành nhìn ra ngay. ⛔ Trả màu đỏ thì đó là một cảnh báo giả.
   */
  it('⛔ khoá lạ ra màu "không xác định", ⛔ không phải một màu nghiệp vụ', () => {
    for (const la of ['banana', 'alert-level-99', '', null, undefined]) {
      expect(mauMucCanhBao(la)).toBe(statusColors.unknown);
      expect(laKhoaMauHopLe(la)).toBe(false);
    }
    expect(mauMucCanhBao('banana')).not.toBe(statusColors.danger);
  });

  /**
   * ⭐ Bài **tự kiểm chứng** — chứng minh phép so ở trên bắt được vi phạm, chứ không chỉ xanh vì
   * hai bên tình cờ cùng rỗng (conventions.md §1.5).
   */
  it('⭐ tự kiểm chứng: bộ đọc tệp Java thật sự đọc được, và thiếu một khoá là đỏ', () => {
    const backend = khoaMauCuaBackend();

    expect(backend).toContain('alert-level-1');

    const thieuMot = backend.slice(1);
    expect([...alertLevelColorTokens].sort()).not.toEqual([...thieuMot].sort());
  });
});
