import { Button, Result, Typography } from 'antd';
import { Link } from 'react-router-dom';

/**
 * Trang 403.
 *
 * Cố ý nói rõ **thiếu quyền nào** thay vì "bạn không có quyền": quản trị viên nhận được
 * ảnh chụp màn hình này là cấp quyền được ngay, không phải dò lại ma trận 334 dòng phân
 * quyền. Mã quyền không phải bí mật — nó nằm sẵn trong tài liệu nghiệm thu.
 */
export function ForbiddenPage({
  requiredPermission,
  traceId,
}: {
  requiredPermission?: string;
  traceId?: string | null;
}) {
  return (
    <Result
      status="403"
      title="Không có quyền truy cập"
      subTitle="Chức năng này không nằm trong phạm vi tài khoản của bạn. Liên hệ quản trị hệ thống nếu cần dùng."
      extra={
        <>
          {requiredPermission && (
            <Typography.Paragraph type="secondary">
              Quyền cần có: <Typography.Text code>{requiredPermission}</Typography.Text>
            </Typography.Paragraph>
          )}
          {traceId && (
            <Typography.Paragraph type="secondary">
              Mã tra cứu:{' '}
              <Typography.Text code copyable>
                {traceId}
              </Typography.Text>
            </Typography.Paragraph>
          )}
          <Link to="/">
            <Button type="primary">Về trang chủ</Button>
          </Link>
        </>
      }
    />
  );
}
