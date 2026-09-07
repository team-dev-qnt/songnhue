import { Modal } from 'antd';
import { useState } from 'react';

import { MediaBrowser } from './MediaBrowser';
import { type KhoTep, type MediaFile } from './types';

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
export function useMediaPicker(tuyChon?: { kho?: KhoTep }) {
  const kho: KhoTep = tuyChon?.kho ?? 'MEDIA';
  const laTaiLieu = kho === 'TAI_LIEU';

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
      title={laTaiLieu ? 'Chọn tài liệu từ kho' : 'Chọn ảnh từ thư viện'}
      width={900}
      okText={laTaiLieu ? 'Dùng tài liệu này' : 'Chèn ảnh'}
      cancelText="Huỷ"
      okButtonProps={{ disabled: selected === null }}
      onCancel={() => dong(null)}
      onOk={() => dong(selected)}
      destroyOnHidden
    >
      <MediaBrowser
        kho={kho}
        loai={laTaiLieu ? 'document' : 'image'}
        height={440}
        selectedId={selected?.publicId ?? null}
        onSelect={setSelected}
      />
    </Modal>
  );

  return { chonTep, picker };
}
