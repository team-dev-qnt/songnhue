import { Button, Result, Typography } from 'antd';
import { isRouteErrorResponse, useNavigate, useRouteError } from 'react-router-dom';

import { ApiClientError } from '@/shared/apiClient';

import { ForbiddenPage } from './ForbiddenPage';
import { NotFoundPage } from './NotFoundPage';

/**
 * Bắt mọi lỗi chưa ai xử lý trong cây route (trang 500).
 *
 * **`traceId` là lý do trang này tồn tại.** Không có nó, người dùng chỉ báo được "hệ thống
 * lỗi" và người trực phải mò log theo giờ. Có nó thì một chuỗi chép ra là đủ để lần đúng
 * request đó — envelope của backend luôn kèm `traceId`, kể cả khi thành công (§2.1).
 *
 * ⛔ Không hiện `error.stack`: đó là bản đồ nội bộ hệ thống, và backend cũng đã cố tình
 * không trả stacktrace ra API (§2.2). Chi tiết nằm ở log, tra bằng traceId.
 */
export function RouteErrorBoundary() {
  const error = useRouteError();
  const navigate = useNavigate();

  if (isRouteErrorResponse(error) && error.status === 404) {
    return <NotFoundPage />;
  }

  if (error instanceof ApiClientError) {
    if (error.handling === 'forbidden') {
      return <ForbiddenPage traceId={error.traceId} />;
    }
    return (
      <Result
        status="500"
        title="Không thực hiện được thao tác"
        subTitle={error.message}
        extra={
          <>
            {error.traceId && (
              <Typography.Paragraph type="secondary">
                Mã tra cứu:{' '}
                <Typography.Text code copyable>
                  {error.traceId}
                </Typography.Text>
              </Typography.Paragraph>
            )}
            <Button type="primary" onClick={() => navigate(0)}>
              Tải lại
            </Button>
          </>
        }
      />
    );
  }

  return (
    <Result
      status="500"
      title="Lỗi hệ thống"
      subTitle="Đã xảy ra lỗi không mong muốn. Vui lòng tải lại trang; nếu vẫn lỗi, báo quản trị hệ thống."
      extra={
        <Button type="primary" onClick={() => navigate(0)}>
          Tải lại
        </Button>
      }
    />
  );
}
