import type * as TiptapReact from '@tiptap/react';
import { render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { Component, type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { RichTextEditor } from './RichTextEditor';

/**
 * ⛔⛔ Trình soạn thảo bài viết SẬP khi effect nhận một editor ĐÃ HUỶ — WS-67 (T67.6).
 *
 * <h2>Khuyết tật (đo 19/09/2026 trên stack Docker thật, và trên bản dựng production của `dev`)</h2>
 *
 * Mở `/noi-dung/bai-viet/moi` ⇒ `TypeError: Cannot read properties of null (reading 'cached')`, React
 * Router bắt lỗi, **⛔ có ô soạn thảo nào**. Có sẵn TỪ TRƯỚC lượt nâng React 19 · antd 6: bản
 * production của điểm cắt `202b565` và của `dev` trước #165 (`eb803e5`, tiptap 3.31.0 — đúng phiên
 * bản staging lẫn production đang chạy) đều sập y hệt.
 *
 * <h2>Cơ chế — trình tự ĐO bằng nhật ký gắn tạm vào `@tiptap/react` trên trình duyệt thật</h2>
 *
 * <pre>
 *   6600 ms  useEditor dựng editor NGAY TRONG render + hẹn giờ huỷ sau 1 ms nếu chưa "mounted"
 *   6610 ms  hẹn giờ bắn — component CHƯA commit ⇒ editor BỊ HUỶ (schema = null)
 *   6771 ms  effect của useEditor chạy ⇒ dựng editor MỚI
 *   6773 ms  CÙNG lượt commit ấy, effect của RichTextEditor vẫn cầm editor CŨ ⇒ getHTML() ⇒ sập
 * </pre>
 *
 * React Router 7 dựng trang trong `startTransition`, mà render của transition bị cắt lát (nhường luồng
 * ~5 ms một lần) ⇒ hẹn giờ 1 ms của tiptap bắn trước lượt commit.
 *
 * <h2>⚠ Vì sao bài này DỰNG TRẠNG THÁI chứ ⛔ dựng lại cuộc đua bằng giờ thật</h2>
 *
 * Bản đầu dùng `createRoot` + `startTransition` + một component bận 30 ms: cuộc đua CÓ xảy ra trong
 * jsdom (nhật ký: editor bị huỷ khi `mounted=false`), nhưng React ở đó lại **dựng lại component từ
 * đầu** (`useSyncExternalStore` thấy kho đổi giữa lượt render) nên commit với một editor còn sống ⇒
 * bài **XANH trên mã CHƯA vá**. Một bài ⛔ thể đỏ là một bài ⛔ canh gì (luật 1) ⇒ bỏ. Ở đây ta dựng
 * đúng TRẠNG THÁI đã đo trên trình duyệt: bọc `useEditor` thật, và ở lượt đầu huỷ editor nó trả về —
 * y như hẹn giờ 1 ms đã làm. Phần còn lại (dựng editor mới, render lại) là mã tiptap THẬT.
 */

const trangThai = { huyLuotDau: false, daHuy: false };

vi.mock('@tiptap/react', async (importOriginal) => {
  const goc = await importOriginal<typeof TiptapReact>();
  const { useLayoutEffect } = await import('react');
  return {
    ...goc,
    useEditor: ((options, deps) => {
      const editor = goc.useEditor(options, deps);
      // ⚠ Huỷ trong `useLayoutEffect` — tức SAU render, TRƯỚC passive effect: đúng khe mà hẹn giờ
      //   1 ms bắn vào trên trình duyệt. Huỷ ngay trong render thì một bản vá kiểm `isDestroyed`
      //   LÚC RENDER cũng xanh, trong khi nó vẫn sập trên trình duyệt (bản vá đầu của tôi — T67.6).
      useLayoutEffect(() => {
        if (trangThai.huyLuotDau && editor && !trangThai.daHuy) {
          trangThai.daHuy = true;
          editor.destroy();
        }
      });
      return editor;
    }) as typeof goc.useEditor,
  };
});

class BatLoi extends Component<
  { children: ReactNode; onLoi: (e: unknown) => void },
  { loi: boolean }
> {
  state = { loi: false };
  static getDerivedStateFromError() {
    return { loi: true };
  }
  componentDidCatch(e: unknown) {
    this.props.onLoi(e);
  }
  render() {
    return this.state.loi ? <p>ĐÃ SẬP</p> : this.props.children;
  }
}

afterEach(() => {
  trangThai.huyLuotDau = false;
  trangThai.daHuy = false;
});

describe('Trình soạn thảo nhận một editor đã huỷ ở lượt commit đầu (T67.6)', () => {
  it('⛔⛔ editor lượt đầu ĐÃ HUỶ ⇒ ⛔ sập, và tiptap dựng lại được ô soạn thảo mang đúng nội dung', async () => {
    trangThai.huyLuotDau = true;
    const loi: unknown[] = [];
    const { container } = render(
      <App>
        <BatLoi onLoi={(e) => loi.push(e)}>
          <RichTextEditor value="<p>Nội dung đã lưu</p>" onChange={() => {}} />
        </BatLoi>
      </App>,
    );

    await waitFor(() => expect(container.querySelector('.ProseMirror')).not.toBeNull());
    expect(
      trangThai.daHuy,
      'bài ⛔ dựng được trạng thái cần kiểm — editor lượt đầu chưa bị huỷ',
    ).toBe(true);
    expect(
      loi.map((e) => String(e)),
      'Effect của RichTextEditor gọi vào một editor ĐÃ HUỶ (getHTML/setContent) ⇒ cả trang soạn bài sập',
    ).toEqual([]);
    expect(screen.queryByText('ĐÃ SẬP')).toBeNull();
    expect(container.querySelector('.ProseMirror')!.textContent).toContain('Nội dung đã lưu');
  });

  it('⭐ đối chứng: editor KHÔNG bị huỷ ⇒ dựng bình thường (bài trên ⛔ đỏ vì lý do khác)', async () => {
    const { container } = render(
      <App>
        <RichTextEditor value="<p>Nội dung đã lưu</p>" onChange={() => {}} />
      </App>,
    );
    await waitFor(() => expect(container.querySelector('.ProseMirror')).not.toBeNull());
    expect(container.querySelector('.ProseMirror')!.textContent).toContain('Nội dung đã lưu');
  });
});
