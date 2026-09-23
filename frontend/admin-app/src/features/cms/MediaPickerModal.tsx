import { Modal } from 'antd';
import { useState } from 'react';

import { MediaBrowser } from './MediaBrowser';
import { type KhoTep, type MediaFile } from './types';

/** Ba loại tệp hộp chọn phục vụ — khớp prop `loai` của `MediaBrowser`. */
export type LoaiTep = 'image' | 'video' | 'document';

/**
 * Chữ trên hộp thoại, tra theo loại.
 *
 * ⛔ Một bảng tra chứ ⛔ ternary lồng nhau: thêm loại thứ tư là thêm MỘT dòng ở đây, và TypeScript
 * bắt ngay nếu quên — `Record<LoaiTep, …>` đòi đủ khoá.
 */
const CHU_THEO_LOAI: Record<LoaiTep, { tieuDe: string; nutOk: string }> = {
  image: { tieuDe: 'Chọn ảnh từ thư viện', nutOk: 'Chèn ảnh' },
  video: { tieuDe: 'Chọn video từ thư viện', nutOk: 'Chèn video' },
  document: { tieuDe: 'Chọn tài liệu từ kho', nutOk: 'Dùng tài liệu này' },
};

/**
 * Hộp chọn tệp — ảnh đại diện, ảnh chèn giữa bài (T20.7) và **tài liệu đính kèm** (WS-40).
 *
 * <h3>Vì sao là hàm hứa, không phải cặp `open`/`onSelect`</h3>
 *
 * Trình soạn thảo cần chèn ảnh **tại vị trí con trỏ đang đứng**. Với cặp `open`/`onSelect`
 * thì nơi gọi phải tự giữ trạng thái "đang mở để làm gì" giữa hai lần render, và vị trí con
 * trỏ có thể đã đổi. Bọc thành một lời hứa thì lời gọi đọc thẳng như một câu:
 * `const tep = await chonTep()` — chèn ngay dòng dưới, không có khoảng giữa để trạng thái
 * trôi đi.
 *
 * <h3>⭐ Một hook cho hai kho, không phải hai hook</h3>
 *
 * Một `ArticleEditorPage` mở **cả hai** hộp — ảnh bìa/ảnh trong bài, và tài liệu đính kèm — nên
 * nó gọi hook này hai lần với `kho` khác nhau. Hai lượt gọi giữ trạng thái riêng và render hai
 * `<Modal>` riêng; ⛔ đừng gộp thành một hook đa mục đích với một biến "đang mở để làm gì", đó
 * đúng là trạng thái mà kiểu hàm-hứa sinh ra để loại bỏ.
 */
export function useMediaPicker(tuyChon?: { kho?: KhoTep; loai?: LoaiTep }) {
  const kho: KhoTep = tuyChon?.kho ?? 'MEDIA';
  // ⚠⚠ Tra theo `loai` chứ ⛔ theo `kho === 'TAI_LIEU'`. Bản trước dùng một ternary HAI nhánh cho
  //   một câu hỏi BA trạng thái (ảnh · tài liệu · video), nên mở hộp chọn video ra thì tiêu đề nói
  //   *"Chọn ảnh từ thư viện"* và nút nói *"Chèn ảnh"* — đúng hình dạng đã gây ra T63.8.
  const loai: LoaiTep = tuyChon?.loai ?? (kho === 'TAI_LIEU' ? 'document' : 'image');
  const chu = CHU_THEO_LOAI[loai];

  const [state, setState] = useState<{
    open: boolean;
    resolve?: (file: MediaFile | null) => void;
  }>({ open: false });
  const [selected, setSelected] = useState<MediaFile | null>(null);

  const chonTep = (): Promise<MediaFile | null> => {
    setSelected(null);
    return new Promise((resolve) => setState({ open: true, resolve }));
  };

  const dong = (file: MediaFile | null) => {
    state.resolve?.(file);
    setState({ open: false });
  };

  const picker = (
    <Modal
      open={state.open}
      title={chu.tieuDe}
      width={900}
      okText={chu.nutOk}
      cancelText="Huỷ"
      okButtonProps={{ disabled: selected === null }}
      onCancel={() => dong(null)}
      onOk={() => dong(selected)}
      destroyOnHidden
    >
      <MediaBrowser
        kho={kho}
        loai={loai}
        height={440}
        selectedId={selected?.publicId ?? null}
        onSelect={setSelected}
      />
    </Modal>
  );

  return { chonTep, picker };
}
