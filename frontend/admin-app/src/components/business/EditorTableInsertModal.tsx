import { Button, InputNumber, Modal, Space, Switch, Typography } from 'antd';
import { useState } from 'react';

import {
  lyDoKichThuocSai,
  TRAN_COT_LUOI,
  TRAN_COT_SO,
  TRAN_HANG_LUOI,
  TRAN_HANG_SO,
  type KichThuocBang,
} from './tableCommands';

/**
 * **Hộp chọn kích thước bảng** — WS-41 (T41.5).
 *
 * <h3>Vì sao có CẢ hai đường chọn</h3>
 *
 * QuanTran báo: *"table đang chỉ cho phép insert 3x3"*. Đường sửa hiển nhiên là một lưới rê chuột
 * kiểu Word — nhanh nhất cho cỡ thường gặp, hai cú bấm là xong.
 *
 * Nhưng **lưới một mình là dựng lại đúng khiếu nại, chỉ đổi con số từ 3 thành 10**: bảng tiến độ
 * sản xuất theo tháng cần **13 cột** (CR-30), và không lưới nào rộng tới đó mà còn bấm được. Nên
 * ô nhập số đi kèm — nó vừa là đường tới cỡ lớn, vừa là **đường bàn phím** duy nhất cho người
 * không dùng chuột được.
 *
 * <h3>⛔ Lưới dựng bằng `div` + CSS grid, không phải bảng</h3>
 *
 * Hai lý do: (1) đây là một bộ chọn, không phải dữ liệu dạng bảng — dùng thẻ bảng ở đây là nói
 * dối trình đọc màn hình; (2) bộ canh `bangCuonNgang.test.ts` quét **văn bản nguồn thô** của mọi
 * `.tsx` tìm chuỗi `<Table`, nên một component bảng ở đây sẽ đòi khai `scroll={{ x }}` — một yêu
 * cầu vô nghĩa với một lưới 8×10 ô vuông. ⚠ Cùng lý do tệp này **không** dùng `<TableOutlined />`:
 * chuỗi ấy cũng khớp `<Table`.
 *
 * <h3>Vì sao Modal chứ không Popover</h3>
 *
 * `admin-app` có **0** `Popover` và **0** `popupRender`, nhưng **29** tệp dùng `<Modal>`. Modal là
 * khuôn có tiền lệ, dựng được trong jsdom, và không kéo theo lớp nổi định vị bằng hình học — thứ
 * mà `testsupport/setup.ts` cố ý để jsdom trả 0.
 */

export interface EditorTableInsertModalProps {
  open: boolean;
  onCancel: () => void;
  /** Chèn bảng. Nơi gọi tự đóng hộp thoại — component này không giữ trạng thái mở. */
  onInsert: (kichThuoc: KichThuocBang, coHangTieuDe: boolean) => void;
}

/** Toạ độ đang rê/di chuyển tới trên lưới. `null` = chưa trỏ vào ô nào. */
type ODangNgam = KichThuocBang | null;

export function EditorTableInsertModal({ open, onCancel, onInsert }: EditorTableInsertModalProps) {
  const [ngam, setNgam] = useState<ODangNgam>(null);
  const [hang, setHang] = useState(3);
  const [cot, setCot] = useState(3);
  const [coHangTieuDe, setCoHangTieuDe] = useState(true);

  const loiKichThuoc = lyDoKichThuocSai({ hang, cot });

  const dong = () => {
    setNgam(null);
    onCancel();
  };

  /**
   * ⭐ MỘT đường chèn cho cả hai cách chọn.
   *
   * Bản đầu để lưới gọi thẳng `onInsert` còn nút Chèn đọc `coHangTieuDe` — và đó là cách công tắc
   * "Có hàng tiêu đề" bị bỏ rơi ở đường lưới: người dùng tắt nó, bấm một ô lưới, và bảng vẫn có
   * hàng tiêu đề. Không lỗi, không cách nào biết. Gom lại một chỗ thì không còn chỗ để quên.
   */
  const chen = (kichThuoc: KichThuocBang) => {
    if (lyDoKichThuocSai(kichThuoc) !== null) {
      return;
    }
    onInsert(kichThuoc, coHangTieuDe);
    setNgam(null);
  };

  /** Điều hướng lưới bằng bàn phím — ô đang ngắm đóng vai con trỏ. */
  const phim = (event: React.KeyboardEvent, o: KichThuocBang) => {
    const buoc: Record<string, KichThuocBang> = {
      ArrowRight: { hang: o.hang, cot: Math.min(o.cot + 1, TRAN_COT_LUOI) },
      ArrowLeft: { hang: o.hang, cot: Math.max(o.cot - 1, 1) },
      ArrowDown: { hang: Math.min(o.hang + 1, TRAN_HANG_LUOI), cot: o.cot },
      ArrowUp: { hang: Math.max(o.hang - 1, 1), cot: o.cot },
    };

    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      chen(o);
      return;
    }
    const toi = buoc[event.key];
    if (toi) {
      event.preventDefault();
      setNgam(toi);
      // Tiêu điểm đi theo ô đang ngắm, nếu không thì mũi tên di chuyển phần tô sáng mà trình đọc
      // màn hình vẫn đọc ô cũ — người dùng bàn phím nghe một đằng, thấy một nẻo.
      document.getElementById(idO(toi))?.focus();
    }
  };

  return (
    <Modal open={open} title="Chèn bảng" footer={null} onCancel={dong} width={420} destroyOnHidden>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div
          role="grid"
          aria-label="Chọn nhanh kích thước bảng"
          className="sn-luoi-chon-bang"
          onMouseLeave={() => setNgam(null)}
        >
          {Array.from({ length: TRAN_HANG_LUOI }, (_, r) =>
            Array.from({ length: TRAN_COT_LUOI }, (_, c) => {
              const o: KichThuocBang = { hang: r + 1, cot: c + 1 };
              const trongVung = ngam !== null && o.hang <= ngam.hang && o.cot <= ngam.cot;
              return (
                <button
                  key={idO(o)}
                  id={idO(o)}
                  type="button"
                  // ⚠ Chỉ ô đầu nhận Tab; các ô còn lại tới bằng mũi tên. Không có roving tabindex
                  //   thì bàn phím phải bấm Tab 80 lần để đi hết lưới.
                  tabIndex={o.hang === 1 && o.cot === 1 ? 0 : -1}
                  aria-label={`${o.hang} hàng × ${o.cot} cột`}
                  className={`sn-luoi-chon-bang__o${trongVung ? ' sn-luoi-chon-bang__o--chon' : ''}`}
                  onMouseEnter={() => setNgam(o)}
                  onFocus={() => setNgam(o)}
                  onClick={() => chen(o)}
                  onKeyDown={(event) => phim(event, o)}
                />
              );
            }),
          )}
        </div>

        <Typography.Text type="secondary" aria-live="polite">
          {ngam
            ? `${ngam.hang} hàng × ${ngam.cot} cột`
            : 'Rê chuột trên lưới, hoặc nhập số bên dưới'}
        </Typography.Text>

        <Space wrap align="center">
          <span>Số hàng</span>
          <InputNumber
            min={1}
            max={TRAN_HANG_SO}
            value={hang}
            onChange={(v) => setHang(v ?? 1)}
            aria-label="Số hàng"
          />
          <span>Số cột</span>
          <InputNumber
            min={1}
            max={TRAN_COT_SO}
            value={cot}
            onChange={(v) => setCot(v ?? 1)}
            aria-label="Số cột"
          />
        </Space>

        <Space align="center">
          <Switch checked={coHangTieuDe} onChange={setCoHangTieuDe} id="sn-cong-tac-hang-tieu-de" />
          <label htmlFor="sn-cong-tac-hang-tieu-de">Hàng đầu là tiêu đề</label>
        </Space>

        {loiKichThuoc && <Typography.Text type="danger">{loiKichThuoc}</Typography.Text>}

        <Space style={{ justifyContent: 'flex-end', width: '100%' }}>
          <Button onClick={dong}>Huỷ</Button>
          <Button
            type="primary"
            disabled={loiKichThuoc !== null}
            onClick={() => chen({ hang, cot })}
          >
            Chèn
          </Button>
        </Space>
      </Space>
    </Modal>
  );
}

function idO({ hang, cot }: KichThuocBang): string {
  return `sn-o-luoi-${hang}-${cot}`;
}
