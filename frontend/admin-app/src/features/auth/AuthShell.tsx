import { Card, Typography } from 'antd';
import { type ReactNode } from 'react';

import { neutralColors } from '@/shared/tokens';

/** Khung chung của các màn hình chưa đăng nhập — một chỗ để không lệch bố cục giữa 4 bước. */
export function AuthShell({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: neutralColors.bgLayout,
        padding: 16,
      }}
    >
      <Card style={{ width: '100%', maxWidth: 420 }}>
        <Typography.Title level={4} style={{ marginBottom: 4 }}>
          {title}
        </Typography.Title>
        {subtitle && (
          <Typography.Paragraph type="secondary" style={{ marginBottom: 20 }}>
            {subtitle}
          </Typography.Paragraph>
        )}
        {children}
      </Card>
    </div>
  );
}
