import {
  DeleteOutlined,
  InsertRowAboveOutlined,
  InsertRowBelowOutlined,
  InsertRowLeftOutlined,
  InsertRowRightOutlined,
  MergeCellsOutlined,
  SplitCellsOutlined,
} from '@ant-design/icons';
import { Button, Divider, Popconfirm, Space, Tooltip } from 'antd';

import { type LenhBang, type TrangThaiBang } from './tableCommands';

/**
 * **Thanh công cụ ngữ cảnh của bảng** — WS-41 (T41.6).
 *
 * <h3>Vì sao neo trong luồng + `sticky`, không phải menu nổi</h3>
 *
 * Bảng thường nằm sau vài đoạn văn, và vùng soạn thảo **không có trần chiều cao** — nó tự cao lên
 * và trang mới là thứ cuộn. Một thanh đặt cứng ở đầu khung sẽ nằm ngoài màn hình đúng lúc người
 * dùng bấm vào ô hàng 8. `sticky` giải đúng chuyện đó.
 *
 * ⚠⚠ `sticky` chỉ chạy nhờ T41.3 đã **gỡ `overflow: hidden` khỏi `.sn-editor`** — `overflow:hidden`
 * biến nó thành một hộp cuộn không bao giờ cuộn, và sticky sẽ **im lặng không làm gì**. Đừng đặt
 * lại thuộc tính ấy.
 *
 * ⛔ Không dùng `BubbleMenu`: gói `@tiptap/extension-bubble-menu` là optionalDependency chưa khai
 * trong `package.json`, và nó định vị bằng `getBoundingClientRect` — mà `testsupport/setup.ts` cố ý
 * để jsdom trả 0, nên **không bài kiểm nào trong CI chạm tới được**.
 *
 * <h3>⛔ Component này KHÔNG nhận `editor`</h3>
 *
 * Nó nhận `trangThai` (dữ liệu) và `onLenh` (một hàm). Nhờ vậy nó dựng được trong bài kiểm mà
 * không cần một `Editor` sống, và `tableCommands.test.ts` canh bằng cấu trúc rằng tệp này không
 * chứa chuỗi `editor.`.
 */

export interface EditorTableBarProps {
  trangThai: TrangThaiBang;
  onLenh: (lenh: LenhBang) => void;
}

interface MucLenh {
  lenh: LenhBang;
  nhan: string;
  icon?: React.ReactNode;
  /** ⛔ `false` = giữ nút BẬT dù `can()` nói gì — xem `LENH_CAN_NOI_DOI`. */
  theoCan?: boolean;
  /** Nút phá huỷ hiện CHỮ, không chỉ biểu tượng. */
  chu?: boolean;
}

const NHOM_HANG: MucLenh[] = [
  { lenh: 'addRowBefore', nhan: 'Thêm hàng trên', icon: <InsertRowAboveOutlined /> },
  { lenh: 'addRowAfter', nhan: 'Thêm hàng dưới', icon: <InsertRowBelowOutlined /> },
  // ⛔ `theoCan: false` — chốt chặn của prosemirror-tables nằm trong `if (dispatch)` nên `can()`
  //    LUÔN trả `true`. Khoá nút theo nó là khoá sai; bật nút rồi đọc kết quả `run()` mới đúng.
  { lenh: 'deleteRow', nhan: 'Xoá hàng', theoCan: false, chu: true },
];

const NHOM_COT: MucLenh[] = [
  { lenh: 'addColumnBefore', nhan: 'Thêm cột trái', icon: <InsertRowLeftOutlined /> },
  { lenh: 'addColumnAfter', nhan: 'Thêm cột phải', icon: <InsertRowRightOutlined /> },
  { lenh: 'deleteColumn', nhan: 'Xoá cột', theoCan: false, chu: true },
];

const NHOM_O: MucLenh[] = [
  { lenh: 'mergeCells', nhan: 'Gộp ô', icon: <MergeCellsOutlined />, theoCan: false },
  { lenh: 'splitCell', nhan: 'Tách ô', icon: <SplitCellsOutlined /> },
];

export function EditorTableBar({ trangThai, onLenh }: EditorTableBarProps) {
  const nut = (muc: MucLenh) => {
    const khoa = muc.theoCan === false ? false : !trangThai.chayDuoc[muc.lenh];
    return (
      <Tooltip key={muc.lenh} title={muc.nhan}>
        <Button
          size="small"
          icon={muc.icon}
          disabled={khoa}
          aria-label={muc.nhan}
          onClick={() => onLenh(muc.lenh)}
        >
          {muc.chu ? muc.nhan : undefined}
        </Button>
      </Tooltip>
    );
  };

  return (
    <div className="sn-editor__tablebar" role="toolbar" aria-label="Công cụ bảng">
      <Space wrap size={4}>
        {NHOM_HANG.map(nut)}
        <Divider type="vertical" />
        {NHOM_COT.map(nut)}
        <Divider type="vertical" />
        {NHOM_O.map(nut)}
        <Divider type="vertical" />

        {/* Hai nút trạng thái: `aria-pressed` phản ánh HÌNH HỌC của bảng, không phải vị trí con
            trỏ — xem `tieuDeTheoHinhHoc`. Đọc sai chỗ này thì bấm "bật" lại thành tắt. */}
        <Button
          size="small"
          type={trangThai.hangTieuDe ? 'primary' : 'default'}
          aria-pressed={trangThai.hangTieuDe}
          aria-label="Hàng tiêu đề"
          onClick={() => onLenh('toggleHeaderRow')}
        >
          Hàng tiêu đề
        </Button>
        <Button
          size="small"
          type={trangThai.cotTieuDe ? 'primary' : 'default'}
          aria-pressed={trangThai.cotTieuDe}
          aria-label="Cột tiêu đề"
          onClick={() => onLenh('toggleHeaderColumn')}
        >
          Cột tiêu đề
        </Button>

        <Divider type="vertical" />
        <Popconfirm
          title="Xoá cả bảng?"
          description="Toàn bộ nội dung trong bảng sẽ mất. Có thể Hoàn tác ngay sau đó."
          okText="Xoá bảng"
          okButtonProps={{ danger: true }}
          cancelText="Huỷ"
          onConfirm={() => onLenh('deleteTable')}
        >
          <Button size="small" danger icon={<DeleteOutlined />} aria-label="Xoá bảng">
            Xoá bảng
          </Button>
        </Popconfirm>
      </Space>
    </div>
  );
}
