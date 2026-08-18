import type { Metadata } from 'next';
import type { ReactNode } from 'react';

import { SiteFooter } from '@/components/SiteFooter';
import { SiteHeader } from '@/components/SiteHeader';
import { SITE, SITE_URL } from '@/lib/site';

import './globals.css';

/**
 * Metadata gốc — mọi trang kế thừa rồi ghi đè phần của mình (T9.3).
 *
 * `metadataBase` là thứ khiến `openGraph.images` và thẻ canonical trở thành **URL tuyệt
 * đối**. Thiếu nó, Next phát ra đường dẫn tương đối, và Facebook/Zalo không lấy được ảnh
 * xem trước — lỗi chỉ lộ ra khi ai đó chia sẻ liên kết, tức là sau khi đã lên production.
 */
export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: SITE.name,
    // Phase 1 đặt `title` riêng cho từng bài viết; hậu tố gắn tự động.
    template: `%s · ${SITE.shortName}`,
  },
  description: SITE.description,
  applicationName: SITE.shortName,
  alternates: { canonical: '/' },
  openGraph: {
    type: 'website',
    locale: SITE.locale,
    url: SITE_URL,
    siteName: SITE.name,
    title: SITE.name,
    description: SITE.description,
  },
  twitter: { card: 'summary_large_image' },
  robots: { index: true, follow: true },
};

export default function RootLayout({ children }: { children: ReactNode }) {
  // `lang="vi"` không phải chi tiết trang trí: trình đọc màn hình chọn giọng theo nó, và
  // trình duyệt dựa vào nó để gợi ý dịch. Hệ thống chỉ có tiếng Việt (chốt BOQ đợt 1).
  return (
    <html lang="vi">
      <body className="flex min-h-screen flex-col">
        {/* Liên kết bỏ qua điều hướng — bắt buộc cho người dùng bàn phím và trình đọc màn hình */}
        <a
          href="#noi-dung"
          className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-brand-primary focus:px-4 focus:py-2 focus:text-white"
        >
          Bỏ qua, tới nội dung chính
        </a>
        <SiteHeader />
        <main id="noi-dung" className="flex-1">
          {children}
        </main>
        <SiteFooter />
      </body>
    </html>
  );
}
