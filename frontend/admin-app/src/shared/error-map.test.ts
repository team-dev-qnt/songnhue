import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import { ERROR_CATALOG, entryFor, messageFor } from './error-map';

/**
 * Canh chuyện lệch giữa danh mục mã lỗi của backend và bản sao ở FE.
 *
 * Trước bài kiểm này, việc đồng bộ chỉ được nhắc bằng một dòng chú thích trong
 * `ErrorCode.java` ("cập nhật cả shared/error-map.ts") — tức là dựa vào trí nhớ, và
 * đúng kiểu nợ đã trôi từ WS-4 sang WS-5 rồi WS-7 (31 → 36 → 43 → 49 mã).
 *
 * Đọc thẳng file properties của backend chứ không chép lại danh sách mã: chép lại thì
 * chính bản chép đó lại là thứ phải nhớ cập nhật.
 */
const RELATIVE_PATH = 'backend/core/src/main/resources/error-messages.properties';

/**
 * Tìm gốc kho mã bằng cách đi ngược lên từ thư mục đang chạy.
 *
 * ⚠ Không dùng `import.meta.url`: Vitest chạy trong môi trường jsdom nên Vite đổi nó
 * thành URL `http://`, và `fileURLToPath` từ chối — lỗi hiện ra là "The URL must be of
 * scheme file", chẳng liên quan gì tới việc đang kiểm. Đi ngược từ `process.cwd()` thì
 * đúng dù lệnh chạy từ `frontend/` hay từ `frontend/admin-app/`.
 */
function findPropertiesFile(): string {
  let current = process.cwd();
  for (let depth = 0; depth < 6; depth += 1) {
    const candidate = join(current, RELATIVE_PATH);
    if (existsSync(candidate)) {
      return candidate;
    }
    const parent = dirname(current);
    if (parent === current) {
      break;
    }
    current = parent;
  }
  throw new Error(`Không tìm thấy ${RELATIVE_PATH} tính từ ${resolve(process.cwd())}`);
}

const PROPERTIES_PATH = findPropertiesFile();

/** Khoá trong file properties: dòng `MÃ=câu`, bỏ chú thích và dòng trống. */
function backendErrorCodes(): string[] {
  return readFileSync(PROPERTIES_PATH, 'utf8')
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith('#'))
    .map((line) => line.slice(0, line.indexOf('=')))
    .filter((code) => code.length > 0);
}

/** Tách riêng để bài kiểm bên dưới chứng minh được là nó thật sự bắt được lệch. */
function drift(backend: readonly string[], frontend: readonly string[]) {
  return {
    thieuOFrontend: backend.filter((code) => !frontend.includes(code)),
    thuaOFrontend: frontend.filter((code) => !backend.includes(code)),
  };
}

describe('error-map đồng bộ với danh mục của backend', () => {
  const backend = backendErrorCodes();
  const frontend = Object.keys(ERROR_CATALOG);

  it('phủ đúng bằng danh mục backend, không thiếu không thừa', () => {
    expect(drift(backend, frontend)).toEqual({ thieuOFrontend: [], thuaOFrontend: [] });
  });

  // Con số này CỐ Ý phải sửa tay mỗi lần thêm mã. Bài kiểm trên đã canh việc hai bên khớp nhau;
  // bài này canh việc *người viết mã biết mình vừa thêm một mã lỗi* — thêm mã là một quyết định
  // (nó vào tài liệu bàn giao, vào bảng tra cứu của người vận hành), không phải một chi tiết trôi qua.
  it('có đủ 55 mã: 50 đến hết WS-12, + 5 mã CMS của WS-13 (danh mục & bài viết)', () => {
    expect(frontend).toHaveLength(55);
    expect(backend).toHaveLength(55);
  });

  // conventions.md §1.5 — mỗi cơ chế canh gác phải có bài kiểm chứng minh nó bắt được vi phạm.
  // Không có bài này thì `drift` trả rỗng vì lý do gì cũng "xanh", kể cả vì đọc nhầm file rỗng.
  it('bài kiểm trên thật sự bắt được lệch', () => {
    expect(drift(['SYS-0001', 'MỚI-9999'], ['SYS-0001'])).toEqual({
      thieuOFrontend: ['MỚI-9999'],
      thuaOFrontend: [],
    });
    expect(drift(['SYS-0001'], ['SYS-0001', 'ĐÃ-BỎ'])).toEqual({
      thieuOFrontend: [],
      thuaOFrontend: ['ĐÃ-BỎ'],
    });
  });
});

describe('cách chọn câu hiển thị', () => {
  it('ưu tiên câu của API vì nó đã điền tham số', () => {
    expect(messageFor('ADM-2006', 'Giá trị tham số "backup.retention-days" không hợp lệ')).toBe(
      'Giá trị tham số "backup.retention-days" không hợp lệ',
    );
  });

  it('rơi về bản sao khi API không nói được câu nào', () => {
    expect(messageFor('AUTH-3001', '   ')).toBe('Không có quyền thực hiện thao tác này');
  });

  it('mã lạ vẫn có câu và không làm vỡ luồng', () => {
    expect(entryFor('XYZ-9999').handling).toBe('toast');
    expect(messageFor('XYZ-9999', 'Câu do backend mới trả về')).toBe('Câu do backend mới trả về');
  });
});
