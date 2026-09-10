import { AimOutlined } from '@ant-design/icons';
import { Alert, Button, Input, Space, Typography } from 'antd';
import { useState } from 'react';

import { bocToaDo, trongVungSongNhue, type ToaDoDaBoc } from '@/shared/toaDo';

interface DanToaDoProps {
  /** Gọi khi bóc được một cặp hợp lệ. ⛔ ⛔ Không gọi khi bóc hỏng — xem ghi chú "im lặng" bên dưới. */
  onChange: (viDo: number, kinhDo: number) => void;
  disabled?: boolean;
}

/**
 * **Ô DÁN TOẠ ĐỘ** — WS-46 / T46.2.
 *
 * <h3>⭐ Vì sao ô này tồn tại bên cạnh hai ô số đã có</h3>
 *
 * Đường vào dữ liệu toạ độ trên thực tế **⛔ không phải** gõ tay hai số thập phân sáu chữ số. Người
 * vận hành mở Google Maps, bấm chuột phải vào công trình, *Sao chép toạ độ*, rồi dán. Thứ nằm
 * trong clipboard khi ấy là `21.048201, 105.782500` — **một chuỗi**, và dán nó vào `InputNumber`
 * cho ra `21`: mất phần thập phân, mất luôn kinh độ, **⛔ không một dòng báo lỗi**.
 *
 * ⇒ Ô này nhận nguyên chuỗi ấy và tự tách. Bản đồ chọn điểm vẫn còn nguyên cho người muốn trỏ
 * chuột; hai đường bổ trợ nhau, ⛔ không thay nhau.
 *
 * <h3>⛔⛔ Hai loại phản hồi, và chúng ⛔ KHÔNG được nói cùng một câu</h3>
 *
 * <ul>
 *   <li>**⛔ không đọc được** → `error`. Ô toạ độ nằm cạnh ô *Lý trình* trên cùng một bước biểu
 *       mẫu, nên dán nhầm ô là chuyện **sẽ** xảy ra. `bocToaDo` cố ý trả `null` cho `K72+000` thay
 *       vì "cố hiểu" nó thành `72, 0`.
 *   <li>**đọc được nhưng ngoài vùng Công ty** → `warning`, và **vẫn điền**. Một công trình ngoài
 *       khung bao vẫn có thể đúng (Công ty mở rộng phạm vi); chặn cứng là biến một lời nhắc hữu
 *       ích thành bức tường. Nhưng im lặng thì một cặp dán nhầm đi thẳng lên bản đồ điều hành.
 * </ul>
 *
 * ⚠ Trạng thái thứ ba — **cặp bị ĐẢO** (kinh độ trước) — ⛔ không cần nhánh riêng: với Việt Nam
 * kinh độ ~105 luôn vượt biên vĩ độ ±90, nên `bocToaDo` trả `null` và nó rơi vào nhánh *⛔ không
 * đọc được*. ⛔ Đừng "sửa hộ" bằng cách tự đảo — xem javadoc `toaDo.ts`.
 */
export function DanToaDo({ onChange, disabled }: DanToaDoProps) {
  const [text, setText] = useState('');
  const [ketQua, setKetQua] = useState<ToaDoDaBoc | null>(null);
  const [loi, setLoi] = useState(false);

  const xuLy = (chuoi: string) => {
    setText(chuoi);
    if (!chuoi.trim()) {
      setKetQua(null);
      setLoi(false);
      return;
    }
    const t = bocToaDo(chuoi);
    setKetQua(t);
    setLoi(t === null);
    if (t) {
      onChange(t.viDo, t.kinhDo);
    }
  };

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={4}>
      <Typography.Text strong>Dán toạ độ</Typography.Text>
      <Input
        allowClear
        disabled={disabled}
        value={text}
        status={loi ? 'error' : undefined}
        prefix={<AimOutlined />}
        placeholder="Dán từ Google Maps, ví dụ: 21.048201, 105.782500"
        onChange={(e) => xuLy(e.target.value)}
        // ⚠ `onPaste` NGOÀI `onChange`: dán vào một ô ĐANG CÓ chữ thì React bắn `change` với giá
        //   trị đã ghép, còn người dùng thì nghĩ mình vừa thay cả ô. Đọc thẳng clipboard cho ra
        //   đúng thứ họ vừa dán.
        onPaste={(e) => {
          const dan = e.clipboardData.getData('text');
          if (dan) {
            e.preventDefault();
            xuLy(dan);
          }
        }}
      />

      {loi ? (
        <Alert
          type="error"
          showIcon
          message="⛔ Không đọc được toạ độ từ chuỗi này"
          description={
            <span>
              Nhận ba dạng: <b>21.048201, 105.782500</b> ·{' '}
              <b>21°02&apos;53.5&quot;N 105°46&apos;57.0&quot;E</b> · liên kết Google&nbsp;Maps. Vĩ
              độ đứng trước — nếu dán kinh độ trước thì vĩ độ vượt ±90 và chuỗi bị từ chối ở đây
              thay vì tạo một điểm sai trên bản đồ.
            </span>
          }
        />
      ) : null}

      {ketQua && !trongVungSongNhue(ketQua) ? (
        <Alert
          type="warning"
          showIcon
          message="⚠ Điểm này nằm ngoài vùng sông Nhuệ (Hà Nội – Hà Nam)"
          description={
            <span>
              Đã điền <b>{ketQua.viDo}</b> / <b>{ketQua.kinhDo}</b>. Kiểm lại xem có nhầm vĩ độ và
              kinh độ cho nhau không. Nếu công trình thật sự ở đó thì cứ lưu — đây là lời nhắc, ⛔
              không phải ràng buộc.
            </span>
          }
        />
      ) : null}

      {ketQua && trongVungSongNhue(ketQua) ? (
        <Typography.Text type="success">
          ✓ Vĩ độ {ketQua.viDo} · Kinh độ {ketQua.kinhDo}
        </Typography.Text>
      ) : null}

      <Button
        size="small"
        type="link"
        style={{ paddingLeft: 0 }}
        disabled={disabled}
        href="https://www.google.com/maps"
        target="_blank"
        rel="noopener noreferrer"
      >
        Mở Google Maps để lấy toạ độ
      </Button>
    </Space>
  );
}
