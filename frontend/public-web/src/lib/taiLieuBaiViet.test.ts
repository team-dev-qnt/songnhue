import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';
import { articleDocUrl, constructionDocUrl, fileUrl } from './routes';

/**
 * Tài liệu đính kèm bài viết trên cổng — WS-40.
 *
 * <h2>Bài này canh cái gì, và KHÔNG canh cái gì (quy tắc 28)</h2>
 *
 * <b>Canh</b> ba thứ, tất cả đều là bất biến chứ không phải hình dạng chữ:
 * <ol>
 *   <li>{@code articleDocUrl} dựng một đường <b>KHÁC</b> {@code fileUrl} — nếu hai hàm trả cùng
 *       một chuỗi thì cả cơ chế "siết" đã bị hoàn tác;</li>
 *   <li><b>không tệp nguồn nào</b> tự ghép đường dẫn tài liệu bằng tay: một chỗ duy nhất;</li>
 *   <li>trang bài viết tiêu thụ {@code article.documents}, và khối hiển thị <b>tự biến mất</b>
 *       khi rỗng — ⛔ không "Đang cập nhật" (quy tắc 16).</li>
 * </ol>
 *
 * <p><b>Không</b> canh: rằng backend thật trả 404 cho bài chưa xuất bản. Đó là bốn vế của
 * {@code ArticleAttachmentTest} phía backend, đo qua HTTP thật — và chúng đã có bài kiểm chứng
 * ngược riêng. Ghi ra đây để cái xanh của lớp này <b>không đọc như một lời bảo đảm</b> về phạm vi
 * công bố.
 */

const goc = process.cwd();

function doc(duongDanTuongDoi: string): string {
  return readFileSync(join(goc, duongDanTuongDoi), 'utf8');
}

/** Mọi tệp `.ts`/`.tsx` dưới `src/`, trừ chính các bài kiểm. */
function moiTepNguon(dir: string = join(goc, 'src')): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const full = join(dir, e.name);
    if (e.isDirectory()) return moiTepNguon(full);
    return /\.tsx?$/.test(e.name) && !/\.test\.tsx?$/.test(e.name) ? [full] : [];
  });
}

describe('articleDocUrl — đường HẸP, cố ý khác đường tệp của cổng', () => {
  it('rỗng → null, không dựng liên kết chết', () => {
    expect(articleDocUrl(null)).toBeNull();
    expect(articleDocUrl(undefined)).toBeNull();
    expect(articleDocUrl('')).toBeNull();
  });

  it('⭐⭐ KHÁC `fileUrl` — đây chính là cả nội dung của cơ chế', () => {
    const id = '7c9e6679-7425-40de-944b-e07fc1f90ae7';
    // ⛔ `/public/files/{id}` chỉ phục vụ MEDIA_FOLDER · BANNER · SITE_CONFIG · MENU_ITEM;
    //    TAI_LIEU cố ý nằm ngoài (backend có bài kiểm HTTP đóng đinh cả hai chiều). Hai hàm trả
    //    về CÙNG một chuỗi nghĩa là ai đó đã "dọn dẹp" cho chúng dùng chung — và mọi liên kết tài
    //    liệu trên cổng thành 404 câm, đúng §10.52.
    expect(articleDocUrl(id)).not.toBe(fileUrl(id));
    expect(articleDocUrl(id)).not.toBe(constructionDocUrl(id));
    expect(articleDocUrl(id)).toBe(`/api/v1/public/article-documents/${id}`);
  });

  it('⛔ không có dấu `?` — cùng ràng buộc với `fileUrl`', () => {
    // Tham số truy vấn trong đường dẫn ảnh/tệp là thứ làm hỏng bộ đệm ISR và presigned; ràng
    // buộc này đã có cho `fileUrl` từ trước, giữ nguyên cho hàm mới.
    expect(articleDocUrl('7c9e6679-7425-40de-944b-e07fc1f90ae7')).not.toContain('?');
  });
});

describe('⛔ Một chỗ duy nhất dựng đường dẫn tài liệu', () => {
  it('⭐ không tệp nguồn nào tự ghép `/public/article-documents/` ngoài `routes.ts`', () => {
    const viPham = moiTepNguon()
      .map((f) => ({ ten: f.slice(goc.length + 1), nguon: boChuThich(readFileSync(f, 'utf8')) }))
      .filter((t) => t.nguon.includes('/public/article-documents/'))
      .map((t) => t.ten);

    // Khẳng định về SỐ LƯỢNG kèm danh sách: đúng một tệp, và tệp ấy là `routes.ts`. Rải đường
    // dẫn ra nhiều nơi thì lượt đổi cấu trúc URL đầu tiên sẽ sót một chỗ, và triệu chứng là
    // "một loại liên kết không hoạt động" — rất khó truy (cùng lập luận với `PortalCache`).
    expect(viPham).toEqual(['src/lib/routes.ts']);
  });

  it('⛔ TIỀN ĐỀ: bộ bóc chú thích không cắt mất phần thân', () => {
    // Luật 29 + bài học 28/8. Không có tiền đề này thì `viPham` rỗng vì lý do gì cũng "xanh",
    // kể cả vì `boChuThich` nuốt sạch tệp.
    expect(boChuThich(doc('src/lib/routes.ts'))).toContain('article-documents');
    expect(moiTepNguon().length).toBeGreaterThan(50);
  });
});

describe('⭐ Khối "Tài liệu đính kèm" — nối đúng và im lặng khi rỗng', () => {
  const khoi = boChuThich(doc('src/components/article/TaiLieuDinhKem.tsx'));
  const trangBai = boChuThich(doc('src/app/bai-viet/[slug]/page.tsx'));

  it('trang bài viết TIÊU THỤ `article.documents` — nửa vòng đọc–ghi phải khép', () => {
    // Quy tắc 27: backend trả một trường mà không màn hình nào đọc là một nửa vòng chạy hoàn
    // hảo cho ra số không. Đây là đầu đọc của trường `documents`.
    expect(trangBai).toContain('article.documents');
    expect(trangBai).toContain('TaiLieuDinhKem');
  });

  it('⛔ khối dùng `articleDocUrl`, KHÔNG dùng `fileUrl`', () => {
    expect(khoi).toContain('articleDocUrl');
    expect(khoi).not.toContain('fileUrl');
  });

  it('⭐⭐ rỗng ⇒ trả `null` ngay đầu component — ⛔ không "Đang cập nhật"', () => {
    // Canh CẤU TRÚC (có nhánh thoát sớm) chứ không canh chữ: một khẳng định kiểu
    // `not.toContain('Đang cập nhật')` sẽ xanh cả khi ai đó viết "Chưa có tài liệu" thay vào.
    expect(khoi).toMatch(/documents\.length === 0\s*\)\s*\{\s*return null;/);
    expect(khoi).not.toMatch(/Đang cập nhật|Chưa có tài liệu nào/);
  });

  it('nhãn loại tệp đọc `contentType`, ⛔ không suy từ đuôi tên tệp', () => {
    // Tên tệp do người dùng đặt và nói dối được; `contentType` do magic-bytes xác định. Cùng
    // lập luận với việc `AttachmentPort` không tin đuôi tệp lúc tải lên.
    expect(khoi).toContain('contentType');
    expect(khoi).not.toMatch(/originalName|\.split\('\.'\)|endsWith\('\.pdf'\)/);
  });
});
