import Link from 'next/link';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Không tìm thấy trang',
  // Trang lỗi lọt vào chỉ mục tìm kiếm là một dạng rác: người dùng bấm từ Google
  // vào đúng một trang báo lỗi.
  robots: { index: false, follow: false },
};

export default function NotFound() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-20 text-center">
      <p className="text-5xl font-bold text-brand-primary">404</p>
      <h1 className="mt-4 text-xl font-semibold text-surface-textBase">Không tìm thấy trang</h1>
      <p className="mt-2 text-surface-textSecondary">
        Đường dẫn không tồn tại hoặc nội dung đã được chuyển sang chuyên mục khác.
      </p>
      <Link
        href="/"
        className="mt-6 inline-block rounded bg-brand-primary px-5 py-2 text-white hover:bg-brand-primaryHover"
      >
        Về trang chủ
      </Link>
    </div>
  );
}
