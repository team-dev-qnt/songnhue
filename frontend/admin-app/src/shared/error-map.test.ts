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
  it('có đủ 135 mã: 62 đến hết WS-15, + 9 mã OPS của WS-17, + OPS-2017 (WS-18), + OPS-2018/2019 (nghiệm thu lại WS-19), + CMS-2015 (logo Liên kết cổng TTĐT, 29/08), + SYS-0011 (tệp vượt trần multipart, 30/08), + HYD-1002/2005/2006 (danh mục điểm đo, WS-28), + HYD-2007 (ô đã có số đo, WS-32), + OPS-2020 (hiệu lực ở tương lai, V3), + HYD-2008 (liên kết điểm đo ↔ công trình trùng, T28.19), + OPS-2021/HYD-2009/HYD-2010/HYD-2011 (máy cảnh báo ngưỡng, WS-33), + HYD-2012/HYD-2013 (khoảng ngày báo cáo, WS-34), + HYD-2014/HYD-2015 (kết xuất báo cáo, T34.7), + CMS-2016/2017 (tài liệu đính kèm bài viết, WS-40 04/09), + CMS-2018/2019/2020 (xử lý liên hệ + phân loại, WS-36 06/09), + CMS-2021 (reCAPTCHA biểu mẫu liên hệ, T36.6), + CMS-2022 (trần dòng bản xuất liên hệ, T36.5), + CMS-2023 (bài viết rỗng trên thực tế — `@NotBlank` không bắt được `<p></p>`, T41.21 08/09), + ADM-2014/2015/2016 (ma trận phân quyền sửa được, T27.31 08/09), + SYS-0012 (tệp nhập vượt trần dòng — trước đó CẮT CỤT trong im lặng; mã SYS vì bộ đọc nằm ở `core` và mọi module nhập qua nó, T42.4/T42.19 09/09), + HYD-2016 (địa chỉ gốc mang mã số — sự cố staging 9 ngày, 0 byte dữ liệu, T50.1 10/09), + HR-1001/1002/1003/2002 (hồ sơ CBNV + danh mục chức vụ — dòng mã HRM đầu tiên, WS-51 10/09), + HR-2003 (trần dung lượng RIÊNG của từng thư mục hồ sơ — khác SYS-0010 là hạn mức cả hồ sơ, CN-04.5 WS-53 10/09), + ADM-2017/2018 (liên kết tài khoản ↔ hồ sơ CBNV — cột `users.employee_id` có 0 đường ghi suốt 28 ngày; ADM-2018 cấm TỰ liên kết vì ô này quyết định ai đọc được trường 🔒 của ai, T51.8 10/09), + HR-2004/2005/2007/2008 (nghỉ phép CN-04.9, WS-57 14/09 — đơn 0 ngày công / chồng ngày / huỷ đơn đã bắt đầu nghỉ / trùng ngày lễ), + HR-2009 (báo cáo CÓ trong danh mục mà chưa dựng được — BCNS-07 mẫu 2C-BNV chờ G6; mã riêng chứ ⛔ KHÔNG phải 404, vì *mã không tồn tại* và *mã có thật, chưa dựng được* dẫn tới hai việc khác hẳn nhau, CN-04.8 WS-58 14/09), + ADM-2019 (job mã hoá lại sang khoá AES mới còn hàng chưa đổi — job phải HỎNG vì một job xanh là lời mời gỡ khoá cũ, T61.11 14/09), + ADM-2020 (tự xoá tài khoản của chính mình — nút xoá tài khoản mở ra giao diện, T61.21 14/09), + AUTH-0009/ADM-2021 (vá vượt 2FA bằng đăng ký lại qua vé challenge + đường đặt lại 2FA của quản trị viên, T61.30 15/09), + ADM-2022 (trần cấp quyền = quyền của chính người cấp, T54.4 15/09), + ADM-2023/2024 (nhập lại mã 2FA trước thao tác nhạy cảm — mã sai là 403 chứ ⛔ 401, T61.42 15/09), + ADM-2025 (tự đặt lại mật khẩu của chính mình qua cửa quản trị — T61.31 16/09; cùng lý lẽ ADM-2021 ở đường 2FA), + SYS-0013/SYS-0014 (sai động từ HTTP trả 405 thay vì gộp về 400; tệp nhập nở quá trần khi giải nén — T61.40 16/09). + CMS-2024 (địa chỉ liên kết mang `javascript:` bị chặn NGAY LÚC GHI — vế ghi của T61.34, T63.4 16/09; mã riêng chứ ⛔ dùng lại CMS-2012 *"đích ⛔ tồn tại hoặc đã bị xoá"*, vì hai câu ấy dẫn người quản trị đi hai hướng khác hẳn nhau). + OPS-2027→2031 (Báo cáo nhanh 18/09 — Q ngoài mọi cỡ máy · vượt số máy thiết kế · kỳ đã chốt · khung giờ ngược · biên cỡ máy ⛔ liền nhau). ⚠ CỘNG BỐN chứ ⛔ KHÔNG phải năm: mã "vượt số dư phép" là `HR-2001`, đã nằm trong danh mục từ 13/08/2026 và mồ côi 32 ngày chờ CN-04.9. Bản đầu của WS-57 đúc thêm `HR-2006` trùng nghĩa — hai mã cho MỘT trạng thái là hai câu trả lời cho cùng một câu hỏi, và mã cũ sẽ mồ côi thêm một phase nữa', () => {
    expect(frontend).toHaveLength(135);
    expect(backend).toHaveLength(135);
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
