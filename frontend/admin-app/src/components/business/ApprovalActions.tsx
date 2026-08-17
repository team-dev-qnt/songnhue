import { App, Button, Input, Modal, Space } from 'antd';
import { useState } from 'react';

/**
 * Nút thao tác workflow — **render từ `allowedActions` do API trả về** (conventions.md §3).
 *
 * <h3>Vì sao không tự suy quyền ở FE</h3>
 *
 * Bộ chuyển trạng thái nằm ở bảng `workflow_transitions`, khoá theo bộ ba
 * `(trạng thái hiện tại, hành động, vai trò)`, và **chỉ Workflow engine được đổi trạng
 * thái** (CLAUDE.md quy tắc 4). Nếu FE tự dựng nút theo kiểu "đang CHỜ_DUYỆT và mình là
 * Trưởng phòng thì hiện nút Duyệt", thì mỗi lần khách thêm một bước duyệt vào bảng
 * transitions là FE lệch — hiện nút bấm vào báo lỗi, hoặc tệ hơn, giấu mất nút hợp lệ.
 *
 * Component này cố tình **không biết** workflow nào cả: nó chỉ vẽ những gì backend nói
 * là làm được lúc này.
 */
export interface AllowedAction {
  /** Mã hành động backend hiểu, VD `SUBMIT`, `APPROVE`, `REJECT`. */
  action: string;
  label: string;
  /** Nút chính của màn hình (thường là duyệt). */
  primary?: boolean;
  danger?: boolean;
  /** Backend yêu cầu kèm lý do — điển hình là từ chối; lý do đi vào nhật ký. */
  requiresReason?: boolean;
}

export function ApprovalActions({
  actions,
  onAction,
  disabled = false,
}: {
  actions: readonly AllowedAction[] | undefined;
  onAction: (action: string, reason?: string) => Promise<void>;
  disabled?: boolean;
}) {
  const { message } = App.useApp();
  const [pending, setPending] = useState<AllowedAction | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  if (!actions || actions.length === 0) {
    return null;
  }

  const run = async (action: AllowedAction, withReason?: string) => {
    setBusy(true);
    try {
      await onAction(action.action, withReason);
      message.success(`Đã ${action.label.toLowerCase()}`);
      setPending(null);
      setReason('');
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Space wrap>
        {actions.map((action) => (
          <Button
            key={action.action}
            type={action.primary ? 'primary' : 'default'}
            danger={action.danger}
            disabled={disabled || busy}
            onClick={() => {
              if (action.requiresReason) {
                setPending(action);
              } else {
                void run(action);
              }
            }}
          >
            {action.label}
          </Button>
        ))}
      </Space>

      <Modal
        open={pending !== null}
        title={pending?.label}
        okText="Xác nhận"
        cancelText="Hủy"
        confirmLoading={busy}
        okButtonProps={{ disabled: reason.trim().length === 0 }}
        onCancel={() => {
          setPending(null);
          setReason('');
        }}
        onOk={() => {
          if (pending) {
            void run(pending, reason.trim());
          }
        }}
      >
        <Input.TextArea
          rows={4}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          placeholder="Nhập lý do — nội dung này được ghi vào nhật ký và gửi cho người liên quan"
        />
      </Modal>
    </>
  );
}
