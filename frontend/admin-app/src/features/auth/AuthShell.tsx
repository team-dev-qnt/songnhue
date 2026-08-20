import { Card, Typography } from 'antd';
import { type ReactNode } from 'react';

import { brandColors, neutralColors, shadow } from 'design-tokens';

/**
 * Khung chung của các màn hình chưa đăng nhập — một chỗ để không lệch bố cục giữa 4 bước.
 *
 * Giao diện đăng nhập là ấn tượng đầu tiên: gradient nền nhẹ + card nổi + accent bar
 * trên đầu truyền tải sự chuyên nghiệp mà không rườm rà.
 */
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
        background: `linear-gradient(135deg, ${neutralColors.bgLayout} 0%, #e6f0fa 50%, ${neutralColors.bgLayout} 100%)`,
        padding: 16,
      }}
    >
      <div className="sn-page-enter" style={{ width: '100%', maxWidth: 420 }}>
        <Card
          style={{
            boxShadow: shadow.lg,
            borderRadius: 12,
            overflow: 'hidden',
            border: 'none',
          }}
        >
          {/* Accent bar — dải mỏng màu brand ở trên đầu card */}
          <div
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              height: 3,
              background: `linear-gradient(90deg, ${brandColors.primaryGradientFrom}, ${brandColors.primaryGradientTo})`,
              borderRadius: '12px 12px 0 0',
            }}
          />
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
    </div>
  );
}
