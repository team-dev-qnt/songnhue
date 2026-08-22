import { Editor, getSchema } from '@tiptap/core';
import { TableKit } from '@tiptap/extension-table';
import { StarterKit } from '@tiptap/starter-kit';
import { ALIGN_CLASSES } from 'design-tokens/editor-schema';
import { afterEach, describe, expect, it } from 'vitest';

import { AlignClass, ALIGN_TYPES } from './AlignClass';
import { FigureImage } from './FigureImage';
import { VideoEmbed } from './VideoEmbed';

/**
 * **Căn lề phải thật sự đặt được class lên đúng nút.**
 *
 * <h3>Bài kiểm này sinh ra từ một lỗi thật, và lỗi đó im lặng hoàn toàn</h3>
 *
 * `AlignClass` từng khai áp dụng cho `'image'` và `'figure'`. Không tên nào tồn tại trong
 * schema — nút ảnh do `FigureImage` đăng ký mang tên `figureImage`. Hậu quả:
 *
 * - TipTap **bỏ qua lặng lẽ** một `addGlobalAttributes` trỏ vào type không có thật, nên nút
 *   ảnh không hề có thuộc tính `align`;
 * - lệnh `setAlign` vẫn trả `true` (nhờ `'paragraph'` đứng trong cùng danh sách), nên nút
 *   trên thanh công cụ sáng lên như đã làm xong việc.
 *
 * Người soạn bấm "căn giữa" cho một tấm ảnh, nhìn không thấy gì đổi, bấm thêm vài lần nữa,
 * rồi kết luận là hệ thống hỏng. Không có dòng lỗi nào để lần ra.
 *
 * ⚠ Bài kiểm cũ không bắt được vì **chưa có bài kiểm nào** cho phần này — `EditorVocabularyTest`
 * chỉ hỏi "class căn lề có sống sót qua bộ lọc không" (có), chứ không hỏi "có ai đặt được
 * class đó lên ảnh không" (không).
 */

const EXTENSIONS = [
  StarterKit.configure({ heading: { levels: [2, 3, 4] } }),
  TableKit.configure({ table: { resizable: true } }),
  AlignClass,
  FigureImage,
  VideoEmbed,
];

const editors: Editor[] = [];

function moTrinhSoanThao(content: string): Editor {
  const editor = new Editor({ extensions: EXTENSIONS, content });
  editors.push(editor);
  return editor;
}

afterEach(() => {
  editors.splice(0).forEach((editor) => editor.destroy());
});

describe('AlignClass', () => {
  it('⭐ mọi tên nút trong NHOM_AP_DUNG đều CÓ THẬT trong schema', () => {
    const schema = getSchema(EXTENSIONS);
    const khongTonTai = ALIGN_TYPES.filter((name) => !(name in schema.nodes));

    expect(
      khongTonTai,
      'Tên nút không có trong schema thì TipTap bỏ qua lặng lẽ: nút đó không nhận được ' +
        'thuộc tính `align`, và người dùng bấm căn lề mà không có gì xảy ra. Đây chính là ' +
        'lỗi mà bản đầu mắc phải với `image` và `figure`.',
    ).toEqual([]);
  });

  it('⭐ mọi nút trong NHOM_AP_DUNG đều thật sự nhận được thuộc tính `align`', () => {
    const schema = getSchema(EXTENSIONS);

    // Kiểm tên nút tồn tại là chưa đủ: `addGlobalAttributes` có thể trỏ đúng tên mà vẫn
    // không gắn được thuộc tính. Đây mới là điều kiện dùng được.
    const thieuThuocTinh = ALIGN_TYPES.filter(
      (name) => schema.nodes[name] && !('align' in (schema.nodes[name].spec.attrs ?? {})),
    );

    expect(thieuThuocTinh).toEqual([]);
  });

  it('⭐⭐ căn giữa một tấm ẢNH thì class đi vào thẻ figure', () => {
    const editor = moTrinhSoanThao(
      '<figure><img src="/api/v1/public/files/abc" alt="Cống"></figure>',
    );
    // Chọn cả nút ảnh — đúng thứ xảy ra khi người dùng bấm vào tấm ảnh.
    editor.commands.setNodeSelection(0);

    expect(editor.commands.setAlign('center')).toBe(true);
    expect(editor.getHTML()).toContain(ALIGN_CLASSES[1]);
  });

  it('căn lề đoạn văn vẫn chạy — bản sửa không được đổi hành vi cũ', () => {
    const editor = moTrinhSoanThao('<p>Một đoạn văn</p>');
    editor.commands.setTextSelection(2);

    expect(editor.commands.setAlign('right')).toBe(true);
    expect(editor.getHTML()).toContain(ALIGN_CLASSES[2]);
  });

  it('bỏ căn lề thì class biến mất, các class khác giữ nguyên', () => {
    const editor = moTrinhSoanThao(
      `<figure class="${ALIGN_CLASSES[1]} sn-w-1-2"><img src="/x" alt=""></figure>`,
    );
    editor.commands.setNodeSelection(0);

    expect(editor.commands.unsetAlign()).toBe(true);
    const html = editor.getHTML();
    expect(html).not.toContain(ALIGN_CLASSES[1]);
    expect(html, 'bề ngang ảnh không liên quan gì tới căn lề — xoá lây là mất định dạng').toContain(
      'sn-w-1-2',
    );
  });

  it('căn lề chữ trong ô bảng vẫn chạy — ô bảng chứa một đoạn văn', () => {
    // Lượt đỏ đầu tiên của bài kiểm này đến từ đây, và **mã không sai**: tôi tưởng vùng chọn
    // trong ô bảng là "không có gì để căn". Thực ra ô bảng chứa một `paragraph`, nên căn lề
    // chạy đúng — và đó là hành vi người dùng mong đợi.
    const editor = moTrinhSoanThao('<table><tbody><tr><td>Ô</td></tr></tbody></table>');
    editor.commands.setTextSelection(4);

    expect(editor.commands.setAlign('right')).toBe(true);
  });

  it('⛔ chỗ không căn lề được thì trả `false`, để nơi gọi báo cho người dùng', () => {
    // Khối mã: nội dung là văn bản thuần, không nằm trong đoạn văn nào. Căn lề mã nguồn là
    // vô nghĩa, nên `false` ở đây vừa đúng vừa **có thật** — nhánh cảnh báo trong
    // `RichTextEditor` không phải mã chết.
    //
    // Bản cũ trả `true` trong **mọi** trường hợp, vì `.some()` dừng ngay ở `paragraph`. Giao
    // diện khi đó không bao giờ phân biệt được "đã căn lề" với "không có chỗ nào để căn".
    const editor = moTrinhSoanThao('<pre><code>if (x) return;</code></pre>');
    editor.commands.setTextSelection(3);

    expect(editor.commands.setAlign('left')).toBe(false);
  });
});
