import Link from 'next/link';

import type { MenuLink } from '@/lib/api';
import { isExternal, menuHref } from '@/lib/routes';
import { EmptyBlock } from './EmptyBlock';
import { SectionTitle } from './SectionTitle';

/** Bộ biểu tượng xoay vòng — thuần trang trí, không mang thông tin nghiệp vụ nào. */
const VONG_BIEU_TUONG = ['dam', 'shield', 'water', 'file'] as const;

interface CategoryServicesGridProps {
  /** Cây menu HEADER đã dựng — cùng đúng một nguồn với thanh điều hướng. */
  menuTree: { item: MenuLink; children: MenuLink[] }[];
}

/**
 * Khối **CHUYÊN MỤC &amp; LĨNH VỰC HOẠT ĐỘNG** — CR-18.
 *
 * <h2>Vì sao đọc MENU chứ không đọc `categories`</h2>
 *
 * §2 của tài liệu chỉnh sửa ra một ràng buộc bằng lời: *"Menu chính, footer, các card chuyên
 * mục và cây nội dung phải dùng CHUNG một hệ phân loại"*. Bản trước đọc
 * `GET /public/categories` — một nguồn <b>khác</b> với thanh menu — nên hai chỗ có thể trôi ra
 * khỏi nhau mà không có lỗi nào: thêm một danh mục là card xuất hiện dù menu không có, ẩn một
 * mục menu thì card vẫn còn.
 *
 * Đọc thẳng cây menu thì "đồng bộ" không còn là một việc phải nhớ — nó là điều duy nhất có
 * thể xảy ra (quy tắc 12).
 *
 * <h2>Chọn mục nào làm card, mà không gọi tên mục nào</h2>
 *
 * Card = mục cấp 1 <b>có menu con</b> hoặc <b>trỏ vào một chuyên mục</b>. Luật ấy tự loại
 * "Trang chủ", "Liên hệ" (đường dẫn đơn, không con) và "Văn bản điều hành" (liên kết ra
 * ngoài), để lại đúng năm mục §3 liệt kê — <b>mà không viết một nhãn nào vào mã</b>. Lọc theo
 * danh sách nhãn thì Công ty đổi tên "Hoạt động Đảng, đoàn thể" là card biến mất, và không ai
 * biết vì sao.
 */
export function CategoryServicesGrid({ menuTree }: CategoryServicesGridProps) {
  const items = menuTree
    .filter(({ item, children }) => {
      if (item.linkType === 'EXTERNAL_DOC') return false;
      return children.length > 0 || item.linkType === 'CATEGORY';
    })
    .map(({ item, children }, i) => ({
      label: item.label,
      // Mục `NONE` chỉ mở menu con nên không có đường dẫn của riêng nó — card trỏ vào mục con
      // đầu tiên. Không có con nào dùng được thì card thành thẻ không bấm, chứ không trỏ `#`.
      href: menuHref(item) ?? children.map(menuHref).find(Boolean) ?? null,
      children,
      external: isExternal(item),
      iconType: VONG_BIEU_TUONG[i % VONG_BIEU_TUONG.length],
    }));

  return (
    <section className="mt-5">
      <SectionTitle>Chuyên mục &amp; lĩnh vực hoạt động</SectionTitle>

      {items.length === 0 ? (
        <div className="mt-5">
          <EmptyBlock>Menu chính chưa có mục nào để dựng thành chuyên mục.</EmptyBlock>
        </div>
      ) : (
        /* ⚠ Dừng ở ba cột, KHÔNG mở tới `xl:grid-cols-5`: năm thẻ trên một hàng 1232px cho mỗi
           thẻ ~215px, mà nhãn dài nhất ("Hoạt động Đảng, đoàn thể") đã chiếm ba dòng ở cỡ đó.
           Bản vẽ chốt ba cột cho cả khối này lẫn "Chuyên mục ảnh" ngay trên nó. */
        <div className="mt-5 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((item) => {
            const than = (
              <>
                <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-brand-primaryGradientFrom to-brand-primary opacity-0 transition-opacity duration-300 group-hover:opacity-100" />
                <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-brand-primaryLight transition-transform duration-300 ease-smooth group-hover:scale-110">
                  <BieuTuong loai={item.iconType} />
                </div>
                <h3 className="mt-4 text-sm font-bold text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary sm:text-base">
                  {item.label}
                </h3>
                {item.children.length > 0 ? (
                  <p className="mt-2 line-clamp-2 text-xs text-surface-textSecondary">
                    {item.children.map((c) => c.label).join(' · ')}
                  </p>
                ) : null}
                {item.href ? (
                  <div className="mt-auto flex items-center gap-1 pt-4 text-xs font-semibold text-brand-primary">
                    <span>Khám phá</span>
                    <span className="transition-transform duration-200 group-hover:translate-x-1">
                      ➔
                    </span>
                  </div>
                ) : null}
              </>
            );

            const lop =
              'group relative flex h-full flex-col overflow-hidden rounded-xl border border-surface-border bg-white p-5 shadow-xs transition-all duration-300 ease-smooth hover:-translate-y-1 hover:border-brand-primary hover:shadow-md';

            return item.href ? (
              <Link
                key={item.label}
                href={item.href}
                rel={item.external ? 'noopener noreferrer' : undefined}
                className={lop}
              >
                {than}
              </Link>
            ) : (
              <div key={item.label} className={lop}>
                {than}
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

function BieuTuong({ loai }: { loai: string }) {
  const chung = 'h-6 w-6 text-brand-primary';
  switch (loai) {
    case 'dam':
      return (
        <svg className={chung} fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.75}
            d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"
          />
        </svg>
      );
    case 'shield':
      return (
        <svg className={chung} fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.75}
            d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
          />
        </svg>
      );
    case 'water':
      return (
        <svg className={chung} fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.75}
            d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"
          />
        </svg>
      );
    default:
      return (
        <svg className={chung} fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.75}
            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
          />
        </svg>
      );
  }
}
