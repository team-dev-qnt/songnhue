import { Modal } from 'antd';
import { useState } from 'react';

import { MediaBrowser } from './MediaBrowser';
import { type MediaFile } from './types';

/**
 * Hộp chọn ảnh — dùng cho ảnh đại diện và ảnh chèn giữa bài (T20.7).
 *
 * <h3>Vì sao là hàm hứa, không phải cặp `open`/`onSelect`</h3>
 *
 * Trình soạn thảo cần chèn ảnh **tại vị trí con trỏ đang đứng**. Với cặp `open`/`onSelect`
 * thì nơi gọi phải tự giữ trạng thái "đang mở để làm gì" giữa hai lần render, và vị trí con
 * trỏ có thể đã đổi. Bọc thành một lời hứa thì lời gọi đọc thẳng như một câu:
 * `const anh = await chonAnh()` — chèn ngay dòng dưới, không có khoảng giữa để trạng thái
 * trôi đi.
 */
export function useMediaPicker() {
  const [state, setState] = useState<{
    open: boolean;
    resolve?: (file: MediaFile | null) => void;
  }>({ open: false });
  const [selected, setSelected] = useState<MediaFile | null>(null);

  const chonAnh = (): Promise<MediaFile | null> => {
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
      title="Chọn ảnh từ thư viện"
      width={900}
      okText="Chèn ảnh"
      cancelText="Huỷ"
      okButtonProps={{ disabled: selected === null }}
      onCancel={() => dong(null)}
      onOk={() => dong(selected)}
      destroyOnHidden
    >
      <MediaBrowser
        imagesOnly
        height={440}
        selectedId={selected?.publicId ?? null}
        onSelect={setSelected}
      />
    </Modal>
  );

  return { chonAnh, picker };
}
