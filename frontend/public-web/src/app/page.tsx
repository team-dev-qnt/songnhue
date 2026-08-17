import Link from 'next/link';

import { NAV_ITEMS } from '@/lib/site';

/**
 * Trang chủ tạm.
 *
 * <h3>Chỗ này thể hiện đúng một điều: khung ISR đã chạy</h3>
 *
 * `revalidate` đặt ở đây là bản mẫu cho mọi trang nội dung của Phase 1: trang được dựng
 * sẵn thành HTML tĩnh, phục vụ tức thì, và tự dựng lại theo chu kỳ — cộng thêm đường dựng
 * lại **ngay lập tức** khi biên tập viên duyệt bài, qua `POST /api/revalidate`.
 *
 * Vì sao không render động mỗi lượt: cổng thông tin công khai chịu lượt xem không kiểm
 * soát được (một bài viết được chia sẻ rộng là đủ), mà cùng lúc đó backend đang phục vụ
 * hệ điều hành nội bộ trên **cùng một máy chủ**. HTML tĩnh giữ cho lượt đọc của công chúng
 * không đụng tới CSDL.
 */
export const revalidate = 300;

export default function HomePage() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-12">
      <section className="rounded border border-surface-border bg-surface-bgLayout p-8">
        <h1 className="text-2xl font-bold text-surface-textBase sm:text-3xl">
          Cổng thông tin điện tử Công ty Thủy lợi Sông Nhuệ
        </h1>
        <p className="mt-3 max-w-3xl text-surface-textSecondary">
          Trang đang trong quá trình xây dựng. Các chuyên mục tin tức, văn bản, số liệu thủy văn và
          bản đồ công trình sẽ lần lượt được đưa vào vận hành.
        </p>
      </section>

      <section className="mt-10">
        <h2 className="text-lg font-semibold text-surface-textBase">Chuyên mục</h2>
        <ul className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {NAV_ITEMS.filter((item) => item.href !== '/').map((item) => (
            <li key={item.href}>
              <Link
                href={item.href}
                className="block rounded border border-surface-border p-4 transition-colors hover:border-brand-primary"
              >
                <span className="font-medium text-surface-textBase">{item.label}</span>
                <span className="mt-1 block text-sm text-surface-textSecondary">
                  Đang cập nhật nội dung
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
