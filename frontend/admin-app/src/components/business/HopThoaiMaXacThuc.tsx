import { Alert, Button, Input, Modal, Space, Typography } from 'antd';
import { useState } from 'react';

/**
 * Hộp thoại nhập lại mã xác thực hai bước trước một thao tác nhạy cảm — T61.42.
 *
 * Dùng cho: lưu tham số nhóm nhạy cảm · nhập cấu hình · đặt/xoá bí mật tích hợp.
 *
 * ⛔ Ô mã ⛔ giữ giá trị giữa hai lần mở: mã TOTP sống 30 giây và máy chủ từ chối dùng lại, nên
 * một mã cũ còn nằm trong ô là một lượt sai dựng sẵn — mà lượt sai bị đếm vào khoá tài khoản.
 * `destroyOnHidden` gỡ cây con khi đóng, nên `useState` bên trong khởi động lại từ rỗng.
 */
export function HopThoaiMaXacThuc({
  open,
  title,
  moTa,
  loi,
  dangGui,
  onXacNhan,
  onHuy,
}: {
  open: boolean;
  title: string;
  moTa?: React.ReactNode;
  /** Câu lỗi của lượt gửi trước (mã sai…) — hiện ngay trong hộp thoại, ⛔ đóng hộp thoại. */
  loi?: string | null;
  dangGui: boolean;
  onXacNhan: (maXacThuc: string) => void;
  onHuy: () => void;
}) {
  return (
    <Modal
      title={title}
      open={open}
      onCancel={onHuy}
      footer={null}
      destroyOnHidden
      maskClosable={!dangGui}
    >
      <NoiDung moTa={moTa} loi={loi} dangGui={dangGui} onXacNhan={onXacNhan} onHuy={onHuy} />
    </Modal>
  );
}

function NoiDung({
  moTa,
  loi,
  dangGui,
  onXacNhan,
  onHuy,
}: {
  moTa?: React.ReactNode;
  loi?: string | null;
  dangGui: boolean;
  onXacNhan: (maXacThuc: string) => void;
  onHuy: () => void;
}) {
  const [ma, setMa] = useState('');
  const hopLe = /^\d{6}$/.test(ma);

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (hopLe && !dangGui) {
          onXacNhan(ma);
          setMa('');
        }
      }}
    >
      {moTa && <Typography.Paragraph type="secondary">{moTa}</Typography.Paragraph>}
      {loi && <Alert type="error" showIcon message={loi} style={{ marginBottom: 12 }} />}
      <Typography.Text>Mã xác thực hai bước</Typography.Text>
      <Input
        aria-label="Mã xác thực hai bước"
        autoFocus
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        placeholder="123456"
        value={ma}
        onChange={(e) => setMa(e.target.value.replace(/\D/g, ''))}
        style={{ marginTop: 4, marginBottom: 16 }}
      />
      <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
        <Button onClick={onHuy} disabled={dangGui}>
          Huỷ
        </Button>
        <Button type="primary" htmlType="submit" disabled={!hopLe} loading={dangGui}>
          Xác nhận
        </Button>
      </Space>
    </form>
  );
}
