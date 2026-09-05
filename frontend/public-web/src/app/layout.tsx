import type { Metadata } from 'next';
import type { ReactNode } from 'react';

import { SiteFooter } from '@/components/SiteFooter';
import { SiteHeader } from '@/components/SiteHeader';
import { getSiteConfig } from '@/lib/api';
import { fileUrl } from '@/lib/routes';
import { SITE, SITE_URL } from '@/lib/site';

import './globals.css';

/**
 * Metadata gốc — mọi trang kế thừa rồi ghi đè phần của mình (T9.3).
 *
 * `metadataBase` là thứ khiến `openGraph.images` và thẻ canonical trở thành **URL tuyệt
 * đối**. Thiếu nó, Next phát ra đường dẫn tương đối, và Facebook/Zalo không lấy được ảnh
 * xem trước — lỗi chỉ lộ ra khi ai đó chia sẻ liên kết, tức là sau khi đã lên production.
 */
export async function generateMetadata(): Promise<Metadata> {
  // Tên cổng, khẩu hiệu và favicon do Công ty đặt trên màn hình cấu hình giao diện (T15.2).
  // Hằng số trong `site.ts` chỉ còn là lưới an toàn khi backend chưa gọi được — cổng thông
  // tin hiện sai tên vì API hắt hơi thì tệ hơn nhiều so với hiện tên mặc định.
  const config = await getSiteConfig();

  const name = config?.['site.name'] || SITE.name;
  const description = config?.['site.slogan'] || SITE.description;
  const favicon = fileUrl(config?.['site.favicon.attachment-id']);

  return {
    metadataBase: new URL(SITE_URL),
    title: {
      default: name,
      template: `%s · ${SITE.shortName}`,
    },
    description,
    applicationName: SITE.shortName,
    alternates: { canonical: '/' },
    // ⚠⚠ Biểu tượng tab dùng `/thumbnail.png`, KHÔNG dùng `/logo.png`.
    //
    // Đo hai tệp bằng cách đọc kênh alpha: `logo.png` là hình TRẮNG trên nền trong suốt (100%
    // điểm đục nằm ở vùng gần trắng), còn `thumbnail.png` là bản NAVY (#063060). Thanh tab của
    // trình duyệt gần như luôn sáng màu, nên logo trắng ở đó là một ô trống — người dùng mất
    // đúng thứ giúp họ tìm lại tab.
    //
    // ⛔ Đây là một lỗi CÓ SẴN chứ không phải lỗi mới: tệp cũ `logo-song-nhue.png` cũng trắng
    //    trên nền trong suốt, tức biểu tượng tab của cổng chưa từng nhìn thấy được. Sửa luôn ở
    //    lượt đổi logo thay vì chép nguyên lỗi sang tệp mới.
    icons: favicon ? { icon: favicon } : { icon: '/thumbnail.png', apple: '/thumbnail.png' },
    openGraph: {
      type: 'website',
      locale: SITE.locale,
      url: SITE_URL,
      siteName: name,
      title: name,
      description,
      images: [
        {
          url: '/thumbnail.png',
          // Đo bằng `sips`: tệp thật là 800×800. Khai 800×600 làm nơi hiển thị cắt ảnh theo một
          // tỉ lệ không tồn tại — thẻ chia sẻ mất một phần logo mà không có gì báo.
          width: 800,
          height: 800,
          alt: name,
        },
      ],
    },
    twitter: {
      card: 'summary_large_image',
      images: ['/thumbnail.png'],
    },
    robots: { index: true, follow: true },
  };
}

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
