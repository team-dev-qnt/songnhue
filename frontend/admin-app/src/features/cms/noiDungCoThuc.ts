/**
 * Bài viết có nội dung thật không — nửa **giao diện** của `CMS-2023` (T41.21).
 *
 * ## Vì sao không dùng `required` của AntD
 *
 * Trình soạn thảo trống ⛔ không trả chuỗi rỗng: TipTap luôn giữ ít nhất một đoạn văn, nên
 * `editor.getHTML()` trả `<p></p>`. Luật `required` chỉ hỏi *"có giá trị không"*, và `<p></p>` là
 * một giá trị — nên nó cho qua, y hệt `@NotBlank` ở backend cho qua.
 *
 * ## ⚠ Hai bản, một luật (quy tắc 14)
 *
 * Vị từ này tồn tại **hai nơi**: bản Java `ArticleService.lamSachNoiDung` là chốt chặn thật (quy tắc
 * 12 — nằm ở chỗ dữ liệu đi qua), bản này chỉ để người soạn bài biết ngay thay vì gõ xong cả bài
 * mới bị từ chối. Hai bản lệch nhau là màn hình báo hợp lệ rồi máy chủ từ chối, hoặc ngược lại —
 * cả hai đều trông như lỗi hệ thống. `noiDungCoThuc.test.ts` đọc **thẳng tệp Java** và đòi hai danh
 * sách thẻ khớp nhau.
 */

/**
 * Thẻ tự nó **là** nội dung — bài chỉ gồm một tấm ảnh, một bảng số liệu hay một video nhúng là bài
 * hợp lệ. Phải khớp `ArticleService.KHOI_CO_NOI_DUNG`.
 */
export const KHOI_CO_NOI_DUNG = ['img', 'iframe', 'table', 'hr'] as const;

export function coNoiDungThuc(html: string | null | undefined): boolean {
  const s = html ?? '';
  const chu = s
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    // ⚠ Khoảng trắng không ngắt viết bằng escape, ⛔ không dán ký tự thật — dán vào thì dòng này
    //   trông y hệt một lệnh không làm gì và không ai đọc ra được.
    .replace(/\u00A0/g, ' ')
    .trim();
  if (chu.length > 0) {
    return true;
  }
  return new RegExp(`<(?:${KHOI_CO_NOI_DUNG.join('|')})\\b`, 'i').test(s);
}
