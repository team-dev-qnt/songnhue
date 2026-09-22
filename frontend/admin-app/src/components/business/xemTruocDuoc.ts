/**
 * Định dạng trình duyệt dựng được trong một khung — T84.6.
 *
 * <h2>⛔ Đi bằng `contentType`, ⛔ đuôi tên tệp</h2>
 *
 * Tên tệp do người dùng đặt và nói dối được; `contentType` do **magic bytes** xác định
 * (`FileValidator` ở backend, cùng lập luận nó ⛔ tin đuôi tệp lúc tải lên). Một tệp `bao-cao.pdf`
 * thật ra là HTML mà mở `inline` trong khung cùng gốc với trang quản trị là một đường XSS.
 *
 * <h2>Vì sao là tệp `.ts` riêng chứ ⛔ nằm cạnh component</h2>
 *
 * `frontend/eslint.config.mjs` bật `react-refresh/only-export-components`, và CI chạy
 * `--max-warnings=0` ⇒ mức **LỖI**. `allowConstantExport` ⛔ cứu một `function`. Cùng lý do đã tách
 * `AlignClass.ts`, `editorExtensions.ts`, `tableCommands.ts`.
 *
 * @param contentType kiểu MIME backend trả; `null`/rỗng ⇒ ⛔ xem trước được
 * @param taiDuoc tệp đã qua quét virus chưa — `false` thì mọi đường đọc đều 409 `SYS-0009`
 */
export function xemTruocDuoc(contentType: string | null | undefined, taiDuoc: boolean): boolean {
  if (!taiDuoc) {
    return false;
  }
  const loai = (contentType ?? '').toLowerCase();
  return loai === 'application/pdf' || loai.startsWith('image/');
}
