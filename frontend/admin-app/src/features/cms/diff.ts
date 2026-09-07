/**
 * So sánh hai phiên bản bài viết — T20.5.
 *
 * <h3>So sánh theo DÒNG, và vì sao không phải theo từ</h3>
 *
 * Nội dung bài là HTML do trình soạn thảo sinh ra. So sánh theo từ trên chuỗi HTML cho ra
 * một biển thay đổi vô nghĩa (`<p>` đổi chỗ, thuộc tính `class` xáo lại) mà người biên tập
 * không đọc được. So theo **khối văn bản** — mỗi thẻ khối là một dòng, chỉ giữ phần chữ —
 * thì kết quả đọc được: "đoạn này thêm", "đoạn này sửa".
 *
 * ⛔ Cố ý **không** kéo thư viện diff. Thuật toán dưới đây là LCS kinh điển, ~40 dòng, và
 * một phụ thuộc nữa là một dòng nữa phải theo dõi CVE cho đúng một màn hình.
 */

export type DiffKind = 'giu' | 'them' | 'bot';

export interface DiffRow {
  kind: DiffKind;
  text: string;
}

/**
 * Tách HTML thành danh sách khối văn bản đọc được.
 *
 * ⚠ Dùng `DOMParser` chứ không phải biểu thức chính quy. Nội dung đã được backend khử
 * trùng bằng `HtmlSanitizer` nên không có mã chạy được, nhưng bóc thẻ bằng regex vẫn sai ở
 * những chỗ tầm thường (thuộc tính chứa dấu `>`), và ở đây trình duyệt đã có sẵn bộ phân
 * tích đúng.
 *
 * `DOMParser` **không** chạy script và không tải tài nguyên ngoài — khác hẳn `innerHTML`.
 */
export function toBlocks(html: string | null | undefined): string[] {
  if (!html || html.trim().length === 0) {
    return [];
  }
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const blocks: string[] = [];

  doc.body
    .querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, blockquote, td, th, figcaption')
    .forEach((node) => {
      const text = (node.textContent ?? '').replace(/\s+/g, ' ').trim();
      if (text.length > 0) {
        blocks.push(text);
      }
    });

  // Nội dung không có thẻ khối nào (một dòng chữ trần) vẫn phải so sánh được.
  if (blocks.length === 0) {
    const text = (doc.body.textContent ?? '').replace(/\s+/g, ' ').trim();
    return text.length > 0 ? [text] : [];
  }
  return blocks;
}

/**
 * So hai danh sách khối bằng dãy con chung dài nhất.
 *
 * Trả về danh sách dòng theo thứ tự đọc, mỗi dòng mang một trong ba nhãn. Dòng `bot` đứng
 * trước dòng `them` tương ứng — đọc như "trước / sau".
 */
export function diffBlocks(before: string[], after: string[]): DiffRow[] {
  const m = before.length;
  const n = after.length;

  // lcs[i][j] = độ dài dãy con chung dài nhất của before[i..] và after[j..]
  const lcs: number[][] = Array.from({ length: m + 1 }, () => new Array<number>(n + 1).fill(0));
  for (let i = m - 1; i >= 0; i -= 1) {
    for (let j = n - 1; j >= 0; j -= 1) {
      lcs[i][j] =
        before[i] === after[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }

  const rows: DiffRow[] = [];
  let i = 0;
  let j = 0;
  while (i < m && j < n) {
    if (before[i] === after[j]) {
      rows.push({ kind: 'giu', text: before[i] });
      i += 1;
      j += 1;
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      rows.push({ kind: 'bot', text: before[i] });
      i += 1;
    } else {
      rows.push({ kind: 'them', text: after[j] });
      j += 1;
    }
  }
  while (i < m) {
    rows.push({ kind: 'bot', text: before[i] });
    i += 1;
  }
  while (j < n) {
    rows.push({ kind: 'them', text: after[j] });
    j += 1;
  }
  return rows;
}

/** Tóm tắt một lượt so sánh — hiện ở đầu màn hình để biết ngay có đáng đọc kỹ không. */
export function summarizeDiff(rows: readonly DiffRow[]): {
  them: number;
  bot: number;
  khongDoi: boolean;
} {
  const them = rows.filter((r) => r.kind === 'them').length;
  const bot = rows.filter((r) => r.kind === 'bot').length;
  return { them, bot, khongDoi: them === 0 && bot === 0 };
}
