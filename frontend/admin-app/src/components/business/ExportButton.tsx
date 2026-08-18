import { DownloadOutlined } from '@ant-design/icons';
import { App, Button, Modal, Progress, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';

import { type JobAccepted, type JobStatusView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';

/**
 * Nút kết xuất chạy nền — hình dạng chuẩn của mọi việc dài hơi (conventions.md §1.3).
 *
 * <h3>Vì sao không tải trực tiếp</h3>
 *
 * Kết xuất báo cáo hay nhật ký kiểm toán quét qua hàng trăm nghìn dòng. Giữ một request
 * HTTP mở suốt thời gian đó thì nginx cắt ở phút thứ n, người dùng nhận trang trắng, mà
 * việc phía máy chủ vẫn chạy tiếp — không ai biết nó xong hay hỏng. Backend trả **202 +
 * `jobId`**, FE hỏi tiến độ theo chu kỳ.
 *
 * Đóng hộp thoại **không** hủy việc: nó vẫn chạy, kết quả về hộp thư trong ứng dụng.
 */
export function ExportButton({
  /** Endpoint tạo việc, VD `/audit-logs/export`. */
  endpoint,
  payload,
  label = 'Kết xuất',
  disabled,
}: {
  endpoint: string;
  payload?: unknown;
  label?: string;
  disabled?: boolean;
}) {
  const { message } = App.useApp();
  const [job, setJob] = useState<JobStatusView | null>(null);
  const [open, setOpen] = useState(false);
  const [starting, setStarting] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Dọn bộ đếm khi component biến mất — nếu không, người dùng rời màn hình là còn một
  // vòng hỏi tiến độ chạy mãi trong nền, gọi setState lên component đã tháo.
  useEffect(
    () => () => {
      if (timer.current) {
        clearTimeout(timer.current);
      }
    },
    [],
  );

  const poll = async (jobId: string) => {
    try {
      const status = await api.get<JobStatusView>(`/jobs/${jobId}`);
      setJob(status);
      if (status.status === 'PENDING' || status.status === 'RUNNING') {
        timer.current = setTimeout(() => void poll(jobId), 2000);
      }
    } catch (error) {
      const apiError = error instanceof ApiClientError ? error : null;
      message.error(apiError?.message ?? 'Không tra được tiến độ kết xuất');
    }
  };

  const start = async () => {
    setStarting(true);
    try {
      const accepted = await api.post<JobAccepted>(endpoint, payload);
      setOpen(true);
      setJob(null);
      await poll(accepted.jobId);
    } catch (error) {
      const apiError = error instanceof ApiClientError ? error : null;
      message.error(apiError?.message ?? 'Không tạo được việc kết xuất');
    } finally {
      setStarting(false);
    }
  };

  const done = job?.status === 'SUCCEEDED';
  const failed = job?.status === 'FAILED';

  return (
    <>
      <Button
        icon={<DownloadOutlined />}
        loading={starting}
        disabled={disabled}
        onClick={() => void start()}
      >
        {label}
      </Button>

      <Modal
        open={open}
        title={label}
        onCancel={() => setOpen(false)}
        footer={null}
        maskClosable={false}
      >
        <Progress
          percent={job?.progress ?? 0}
          status={failed ? 'exception' : done ? 'success' : 'active'}
        />
        {!done && !failed && (
          <Typography.Paragraph type="secondary">
            Việc đang chạy nền. Đóng cửa sổ này không hủy việc — kết quả sẽ được báo về hộp thư.
          </Typography.Paragraph>
        )}
        {done && job?.result && (
          <Typography.Paragraph>
            {/* Liên kết tải có hạn ngắn do backend cấp; không lưu lại, không chia sẻ. */}
            <a href={job.result} target="_blank" rel="noreferrer">
              Tải tệp kết quả
            </a>
          </Typography.Paragraph>
        )}
        {failed && <Typography.Paragraph type="danger">{job?.lastError}</Typography.Paragraph>}
      </Modal>
    </>
  );
}
