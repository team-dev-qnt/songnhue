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
/**
 * ⚠ Kiểu này **chính là** hợp đồng dây — xem `AllowedActionView` trong `shared/api-types.ts`.
 * Khai lại ở đây để component không kéo theo `shared/`, nhưng hai bên phải khớp: có
 * `apiTypeParity.test.ts` đối chiếu, lệch một trường là bài kiểm đỏ.
 *
 * ⛔ Đừng thêm trường trình bày vào đây. Bản trước có `primary?: boolean` và `danger?: boolean`
 * mà **không nơi nào điền** — backend chỉ gửi `(action, label, toState, requiresReason)`. Hệ quả:
 * mọi nút render y hệt nhau, kể cả "Gỡ bài". Một trường chỉ có người đọc mà không có người ghi là
 * một lỗi, không phải việc để dành (CLAUDE.md luật 15). Muốn nhấn mạnh nút thì nguồn phải là dữ
 * liệu backend gửi, không phải một cờ tuỳ chọn không ai đặt.
 */
export interface AllowedAction {
  /** Mã hành động backend hiểu, VD `SUBMIT`, `APPROVE`, `REQUEST_CHANGES`. */
  action: string;
  label: string;
  /** Trạng thái sau khi bấm. */
  toState: string;
  /** Bắt buộc kèm lý do — engine từ chối nếu thiếu, nên phải hỏi trước khi gửi. */
  requiresReason: boolean;
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
        {actions.map((action, viTri) => (
          <Button
            key={action.action}
            // Nút đầu danh sách là nút chính. Đây KHÔNG phải giao diện tự đoán nghiệp vụ: backend
            // trả danh sách đã sắp theo `workflow_transitions.sort_order`, tức là thứ tự do người
            // khai quy trình quyết định. Ở bước "Chờ duyệt" thì `APPROVE` đứng trước
            // `REQUEST_CHANGES`, và nút chính đúng là Duyệt.
            //
            // ⛔ KHÔNG có nút nào được tô đỏ: `workflow_transitions` không có cột nào nói bước nào
            //    là nguy hiểm. Bản trước đọc `action.danger` — một cờ không ai điền — nên thực tế
            //    cũng chưa từng có nút đỏ nào. Ghi ra đây thay vì bịa một danh sách tên hành động
            //    "nguy hiểm" ở phía giao diện; cần thì thêm cột và seed, như đã làm với
            //    `requires_reason`.
            type={viTri === 0 ? 'primary' : 'default'}
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
