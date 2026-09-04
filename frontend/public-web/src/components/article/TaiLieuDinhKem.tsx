import type { TaiLieuRef } from '@/lib/api';
import { articleDocUrl } from '@/lib/routes';

/**
 * Khối "Tài liệu đính kèm" ở cuối bài viết — WS-40, CN-01.1
 * (*"Tệp đính kèm | File | Nhiều tệp (pdf, docx, xlsx…)"*).
 *
 * ⛔ **Không tệp ⇒ không vẽ gì.** Không dòng "Đang cập nhật", không khung rỗng có tiêu đề: quy
 * tắc 16 — một ô chưa có nguồn thì để trống, và ở đây "trống" nghĩa là khối biến mất. Ràng buộc
 * ấy ép ngay ở đầu component chứ không ở nơi gọi, để nơi gọi thứ hai (nếu có) không phải nhớ.
 *
 * ⛔ Đường tải đi qua {@link articleDocUrl}, **không** qua `fileUrl()` — xem javadoc của nó. Đây
 * là chỗ §10.52 sẽ tái phát nếu ai đó "dọn dẹp" cho hai chỗ dùng chung một hàm.
 */
export function TaiLieuDinhKem({ documents }: { documents: TaiLieuRef[] }) {
  if (documents.length === 0) {
    return null;
  }

  return (
    <section className="mt-8 rounded-xl border border-surface-border bg-surface-subtle/60 p-4 sm:p-5">
      <h2 className="text-sm font-bold tracking-tight text-emerald-800">Tài liệu đính kèm</h2>
      <ul className="mt-3 space-y-2">
        {documents.map((tep) => {
          const href = articleDocUrl(tep.publicId);
          if (href === null) {
            return null;
          }
          return (
            <li key={tep.publicId}>
              <a
                href={href}
                // ⚠ `download` một mình KHÔNG đủ: trình duyệt bỏ qua thuộc tính ấy khi liên kết
                //   khác gốc, mà `API_BASE_URL` có thể khác gốc. Thứ thật sự quyết định là header
                //   `Content-Disposition: attachment` backend đặt — thuộc tính này chỉ là lớp
                //   thứ hai cho trường hợp cùng gốc.
                download
                className="group flex items-start gap-2.5 rounded-lg border border-surface-border bg-white px-3 py-2.5 shadow-xs transition hover:border-brand-primary/60"
              >
                <NhanLoai contentType={tep.contentType} />
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-semibold text-brand-primary group-hover:underline">
                    {tep.title}
                  </span>
                  <span className="mt-0.5 block text-xs text-surface-textSecondary">
                    {dungLuong(tep.sizeBytes)}
                  </span>
                </span>
              </a>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

/**
 * Nhãn loại tệp — đọc từ `contentType`, ⛔ **không** suy từ đuôi tên tệp.
 *
 * Tên tệp do người dùng đặt và nói dối được; `contentType` do magic-bytes ở backend xác định.
 * Đây là cùng một lập luận với việc `AttachmentPort` không tin đuôi tệp lúc tải lên.
 */
function NhanLoai({ contentType }: { contentType: string }) {
  const { nhan, mau } = kieuTep(contentType);
  return (
    <span
      className={`mt-0.5 rounded px-1.5 py-0.5 text-[10px] font-extrabold ${mau}`}
      aria-hidden
    >
      {nhan}
    </span>
  );
}

function kieuTep(contentType: string): { nhan: string; mau: string } {
  if (contentType === 'application/pdf') {
    return { nhan: 'pdf', mau: 'bg-red-50 text-red-700' };
  }
  if (contentType.includes('word')) {
    return { nhan: 'doc', mau: 'bg-sky-50 text-sky-700' };
  }
  if (contentType.includes('excel') || contentType.includes('spreadsheet')) {
    return { nhan: 'xls', mau: 'bg-emerald-50 text-emerald-700' };
  }
  if (contentType.includes('zip')) {
    return { nhan: 'zip', mau: 'bg-amber-50 text-amber-800' };
  }
  // ⛔ Không bịa một đuôi cho định dạng lạ — "tệp" là câu trả lời đúng và trung thực.
  return { nhan: 'tệp', mau: 'bg-slate-100 text-slate-700' };
}

/**
 * Dung lượng cho người đọc.
 *
 * ⚠ Dùng 1024, khớp `formatBytes` của admin-app: hai màn hình nói hai con số khác nhau về cùng
 * một tệp là thứ người dùng sẽ báo là lỗi, và họ đúng.
 */
function dungLuong(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const kb = bytes / 1024;
  if (kb < 1024) {
    return `${Math.round(kb)} KB`;
  }
  return `${(kb / 1024).toFixed(1)} MB`;
}
