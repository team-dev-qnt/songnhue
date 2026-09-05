import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { beforeEach, describe, expect, it, vi } from 'vitest';

const get = vi.fn((..._args: unknown[]) => Promise.resolve([] as unknown));
const upload = vi.fn((..._args: unknown[]) => Promise.resolve({} as unknown));

vi.mock('@/shared/apiClient', () => ({
  api: {
    get: (...args: unknown[]) => get(...args),
    upload: (...args: unknown[]) => upload(...args),
  },
  ApiClientError: class extends Error {},
}));

const { cmsApi, cmsKeys } = await import('./api');

/**
 * Kho tài liệu — hai kho dùng chung bộ máy, khác **phạm vi công bố** (WS-40).
 *
 * <h2>Bài này canh cái gì, và KHÔNG canh cái gì (quy tắc 28)</h2>
 *
 * <b>Canh</b> ba thứ, tất cả đều là những chỗ hỏng **im lặng**:
 * <ol>
 *   <li>khoá react-query <b>phân biệt được</b> hai kho — thiếu vế này thì hộp chọn ảnh và hộp
 *       chọn tài liệu dùng chung một mục cache và hộp thứ hai hiện danh sách của hộp thứ nhất;</li>
 *   <li>lời gọi API gửi xuống <b>tên tham số backend thật sự đọc</b> ({@code kho}, {@code type})
 *       — trước 04/09 nó gửi {@code contentType}, một tham số <b>không ai đọc</b>;</li>
 *   <li>{@code MediaBrowser} truyền {@code kho}/{@code loai} vào <b>cả</b> khoá lẫn lời gọi, và
 *       vào <b>cả</b> lượt {@code invalidateQueries}.</li>
 * </ol>
 *
 * <p><b>Không</b> canh: rằng hộp thoại vẽ ra đúng, rằng bấm vào thì chèn đúng chỗ. Tới 04/09
 * <b>không bài kiểm nào render {@code MediaBrowser}</b> — ghi ra để cái xanh ở đây không đọc
 * thành một lời bảo đảm về giao diện (nợ ghi ở {@code master-tracking.md} T40.15).
 */

const goc = process.cwd();

function doc(duongDanTuongDoi: string): string {
  return readFileSync(join(goc, duongDanTuongDoi), 'utf8');
}

beforeEach(() => {
  get.mockClear();
  upload.mockClear();
});

describe('⛔⛔ Khoá cache phân biệt được hai kho', () => {
  const thuMuc = '11111111-2222-3333-4444-555555555555';

  it('⭐ hộp chọn ẢNH và hộp chọn TÀI LIỆU không dùng chung một mục cache', () => {
    // Đây là lỗi *sẽ chắc chắn xảy ra*: hai lời gọi ấy nằm cách nhau vài dòng trong cùng một
    // `ArticleEditorPage`. Trước WS-40 khoá chỉ có `folderId`.
    expect(cmsKeys.files(thuMuc, 'MEDIA', 'image')).not.toEqual(
      cmsKeys.files(thuMuc, 'TAI_LIEU', 'document'),
    );
  });

  it('⭐ `loai` khác nhau trong CÙNG một kho cũng phải khác khoá', () => {
    expect(cmsKeys.files(thuMuc, 'MEDIA', 'image')).not.toEqual(
      cmsKeys.files(thuMuc, 'MEDIA', 'video'),
    );
  });

  it('mặc định giữ nguyên hành vi cũ — kho MEDIA, không lọc loại', () => {
    expect(cmsKeys.files(thuMuc)).toEqual(cmsKeys.files(thuMuc, 'MEDIA', undefined));
  });

  it('⛔ TIỀN ĐỀ: khoá vẫn phân biệt được THƯ MỤC — không nuốt mất vế cũ', () => {
    // Luật 29. Thêm hai chiều mới mà làm hỏng chiều cũ thì ba bài trên vẫn xanh trọn vẹn.
    expect(cmsKeys.files(thuMuc, 'MEDIA')).not.toEqual(cmsKeys.files('khac', 'MEDIA'));
  });
});

describe('⚠ Tham số gửi xuống phải là tên backend THẬT SỰ đọc', () => {
  const thuMuc = '11111111-2222-3333-4444-555555555555';

  it('⭐⭐ gửi `kho` và `type` — ⛔ KHÔNG gửi `contentType` (tham số không ai đọc)', async () => {
    await cmsApi.files(thuMuc, 'TAI_LIEU', 'document');

    const [duongDan, thamSo] = get.mock.calls[0] as unknown as [string, Record<string, unknown>];
    expect(duongDan).toBe(`/cms/media/folders/${thuMuc}/files`);
    expect(thamSo).toEqual({ kho: 'TAI_LIEU', type: 'document' });
    // Backend chỉ nhận `type`/`from`/`to`. Gửi `contentType` mà tưởng đã lọc thì nhận về TẤT CẢ,
    // và hộp chọn tài liệu hiện cả ảnh lẫn video — im lặng, đúng hình dạng quy tắc 15.
    expect(Object.keys(thamSo)).not.toContain('contentType');
  });

  it('tải lên mang `kho` trong query string', async () => {
    await cmsApi.uploadFile(thuMuc, new File(['x'], 'a.pdf'), 'TAI_LIEU');
    expect((upload.mock.calls[0] as unknown as [string])[0]).toContain('kho=TAI_LIEU');
  });

  it('không truyền kho ⇒ MEDIA, giữ nguyên hành vi của mọi nơi gọi cũ', async () => {
    await cmsApi.files(thuMuc);
    expect((get.mock.calls[0] as unknown as [string, Record<string, unknown>])[1]).toMatchObject({
      kho: 'MEDIA',
    });
  });
});

describe('⭐ MediaBrowser nối đủ dây — cả ba chỗ', () => {
  const nguon = doc('src/features/cms/MediaBrowser.tsx');

  it('khoá đọc, lời gọi và lượt làm mới đều mang `kho` + `loai`', () => {
    // Ba chỗ, và chỗ bị quên thường là chỗ thứ ba (`invalidateQueries`) — triệu chứng của nó là
    // "tải tệp lên xong danh sách không đổi", một lỗi người dùng báo mà lập trình viên không
    // tái hiện được vì họ bấm nút Tải lại.
    expect(nguon).toContain('cmsKeys.files(activeFolder, kho, loai)');
    expect(nguon).toContain('cmsApi.files(activeFolder, kho, loai)');
    expect(nguon.match(/cmsKeys\.files\(activeFolder, kho, loai\)/g)).toHaveLength(3);
  });

  it('⛔ không còn nơi nào gọi `cmsKeys.files(activeFolder)` trần', () => {
    expect(nguon).not.toMatch(/cmsKeys\.files\(activeFolder\)/);
  });

  it('⛔⛔ ảnh xem trước CHỈ dựng cho kho MEDIA', () => {
    // `previewUrl` trỏ vào `/public/files/{id}` — đường trả 404 với `TAI_LIEU`. Thiếu vế
    // `kho === 'MEDIA'` thì kho tài liệu đầy ô ảnh vỡ trông y hệt tệp hỏng.
    expect(nguon).toMatch(/const laAnh = kho === 'MEDIA' &&/);
  });
});

describe('⭐ Kho tài liệu có LỐI VÀO — tuyến và mục menu đi thành cặp', () => {
  const router = doc('src/app/router.tsx');
  const menu = doc('src/app/menu.tsx');

  it('cả tuyến lẫn mục menu đều tồn tại, cùng đường dẫn và cùng quyền', () => {
    // Quy tắc 27: một màn hình dựng xong mà không có lối vào là một nửa vòng. Và hai nơi phải
    // nhớ cùng một chuỗi đường dẫn ⇒ cần một phép kiểm nhớ hộ (quy tắc 14).
    expect(router).toContain("'/noi-dung/kho-tai-lieu'");
    expect(menu).toContain("'/noi-dung/kho-tai-lieu'");
    expect(router).toContain("'KhoTaiLieuPage'");
  });

  it('⛔ dùng lại `cms:media:manage`, KHÔNG thêm mã quyền mới', () => {
    // Thêm một mã quyền là thêm một dòng phân quyền phải seed, phải cấp cho từng vai trò, và
    // phải nhớ. Cùng bộ máy, cùng nhóm người dùng thì dùng lại quyền đã có.
    const khoiMenu = menu.slice(menu.indexOf("key: 'kho-tai-lieu'"));
    expect(khoiMenu.slice(0, 300)).toContain("permissions: ['cms:media:manage']");
  });
});
