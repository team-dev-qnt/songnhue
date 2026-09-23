import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { Editor } from '@tiptap/core';
import { afterEach, describe, expect, it } from 'vitest';

import { EXTENSIONS_SOAN_THAO } from './editorExtensions';
import { DUONG_VIDEO } from './VideoTep';

/**
 * **Node `videoTep` — video tải lên, thẻ `<video>`** (T84.14).
 *
 * <h3>Bài này canh ba thứ, và cái thứ ba là cái đắt nhất</h3>
 *
 * <ol>
 *   <li>`renderHTML` phát đúng bộ thuộc tính — thiếu `playsinline` là iOS ép TOÀN MÀN HÌNH;</li>
 *   <li>`insertVideoTep('')` trả `false` — ⛔ chèn một thẻ ⛔ nguồn;</li>
 *   <li>⭐⭐ **tiền tố đường dẫn khớp `HtmlSanitizer.TIEN_TO_VIDEO_NOI_BO`.** Lệch một ký tự thì bộ
 *       lọc backend gỡ CẢ THẺ ở lượt Lưu: biên tập viên chèn video, thấy nó chạy trong khung soạn
 *       thảo, bấm Lưu, mở lại — video biến mất, ⛔ một dòng lỗi nào. Hai chuỗi ở hai ngôn ngữ, đúng
 *       chỗ luật 14 mô tả.</li>
 * </ol>
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');

function dungEditor(content: string): Editor {
  return new Editor({ extensions: EXTENSIONS_SOAN_THAO, content });
}

let editor: Editor | null = null;
afterEach(() => {
  editor?.destroy();
  editor = null;
});

describe('videoTep — node video tải lên', () => {
  it('⭐⭐ tiền tố đường dẫn KHỚP hằng của HtmlSanitizer (luật 14)', () => {
    const java = readFileSync(
      join(GOC_KHO, 'backend/core/src/main/java/com/songnhue/core/common/util/HtmlSanitizer.java'),
      'utf8',
    );
    const m = /TIEN_TO_VIDEO_NOI_BO\s*=\s*"([^"]+)"/.exec(java);
    expect(m, 'không đọc được hằng ở phía Java — bộ canh này đã chết').not.toBeNull();
    expect(m![1]).toBe(DUONG_VIDEO);
  });

  it('⭐ renderHTML phát controls + preload=metadata + playsinline', () => {
    editor = dungEditor('<p></p>');
    expect(editor.chain().focus().insertVideoTep('abc-123').run()).toBe(true);

    const html = editor.getHTML();
    expect(html).toContain('<video');
    expect(html).toContain(`${DUONG_VIDEO}abc-123`);
    expect(html).toContain('controls');
    // ⛔ `preload="auto"`: ba video trong một bài là ba tệp ĐẦY ĐỦ kéo về lúc mở trang.
    expect(html).toContain('preload="metadata"');
    // ⛔ Thiếu nó thì iOS ép video chạy TOÀN MÀN HÌNH ngay khi bấm phát.
    expect(html).toContain('playsinline');
  });

  it('⛔ ⛔ tự chạy, ⛔ lặp, ⛔ tắt tiếng — video tự phát giữa bài tin là thứ phải đi tìm nút tắt', () => {
    editor = dungEditor('<p></p>');
    editor.chain().focus().insertVideoTep('abc-123').run();

    const html = editor.getHTML();
    expect(html).not.toContain('autoplay');
    expect(html).not.toContain('loop');
    expect(html).not.toContain('muted');
  });

  it('⭐ `insertVideoTep("")` trả false — nơi gọi báo được thay vì giả vờ đã làm', () => {
    editor = dungEditor('<p></p>');
    expect(editor.chain().focus().insertVideoTep('').run()).toBe(false);
    expect(editor.getHTML()).not.toContain('<video');
  });

  it('⭐⭐ parseHTML đọc ngược được — mở lại bài đã lưu thì video còn đó', () => {
    // ⛔ Thiếu `parseHTML` thì TipTap bỏ thẻ lạ, và cú Lưu kế tiếp XOÁ video khỏi CSDL — im lặng.
    editor = dungEditor(`<video src="${DUONG_VIDEO}xyz" controls></video>`);
    expect(editor.getHTML()).toContain('<video');
    expect(editor.getHTML()).toContain(`${DUONG_VIDEO}xyz`);
  });

  it('⛔ `videoTep` và `videoEmbed` là HAI node, hai thẻ DOM khác nhau', () => {
    // Gộp chúng là phải nhớ hai luật lọc backend trong một chỗ (tên miền vs tiền tố đường dẫn).
    editor = dungEditor('<p></p>');
    expect(editor.schema.nodes.videoTep).toBeDefined();
    expect(editor.schema.nodes.videoEmbed).toBeDefined();
    expect(editor.schema.nodes.videoTep).not.toBe(editor.schema.nodes.videoEmbed);
  });
});
