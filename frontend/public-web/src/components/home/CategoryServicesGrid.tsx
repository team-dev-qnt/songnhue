import Link from 'next/link';

import type { CategoryNode } from '@/lib/api';
import { ROUTES } from '@/lib/routes';

interface CategoryServicesGridProps {
  categories: CategoryNode[];
}

const DEFAULT_SERVICES = [
  {
    title: 'Quản lý Công trình Thủy lợi',
    description: 'Vận hành cống, trạm bơm tiêu thoát nước, cụm đầu mối lưu vực sông Nhuệ',
    slug: 'quan-ly-cong-trinh',
    iconType: 'dam',
  },
  {
    title: 'Phòng chống Thiên tai & PCTT',
    description: 'Phương án hộ đê, ứng phó ngập úng mùa mưa bão và xả lũ khẩn cấp',
    slug: 'phong-chong-thien-tai',
    iconType: 'shield',
  },
  {
    title: 'Dịch vụ Tưới tiêu Nội đồng',
    description: 'Cung cấp nước phục vụ sản xuất nông nghiệp, sinh hoạt và cải tạo môi trường',
    slug: 'dich-vu-thuy-loi',
    iconType: 'water',
  },
  {
    title: 'Cải cách Hành chính & Pháp chế',
    description: 'Quy trình thủ tục, công khai minh bạch dịch vụ công và văn bản chính sách',
    slug: 'cai-cach-hanh-chinh',
    iconType: 'file',
  },
];

export function CategoryServicesGrid({ categories }: CategoryServicesGridProps) {
  // Lấy các category cấp 0 từ backend hoặc fallback sang bộ chuyên mục tiêu chuẩn
  const rootCategories = categories.filter((c) => c.depth === 0);
  const items =
    rootCategories.length >= 4
      ? rootCategories.slice(0, 4).map((c, i) => ({
          title: c.name,
          description: c.description || DEFAULT_SERVICES[i]?.description || '',
          slug: c.slug,
          iconType: DEFAULT_SERVICES[i]?.iconType || 'water',
        }))
      : DEFAULT_SERVICES;

  const renderIcon = (type: string) => {
    switch (type) {
      case 'dam':
        return (
          <svg
            className="h-6 w-6 text-brand-primary"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
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
          <svg
            className="h-6 w-6 text-brand-primary"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
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
          <svg
            className="h-6 w-6 text-brand-primary"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.75}
              d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"
            />
          </svg>
        );
      case 'file':
      default:
        return (
          <svg
            className="h-6 w-6 text-brand-primary"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.75}
              d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
            />
          </svg>
        );
    }
  };

  return (
    <section className="mt-10 sm:mt-14">
      <div className="flex items-center justify-between border-b-2 border-brand-primary pb-2.5">
        <div className="flex items-center gap-2">
          <span className="h-5 w-1.5 rounded-full bg-brand-primary"></span>
          <h2 className="text-base font-bold uppercase tracking-tight text-surface-textBase sm:text-lg">
            Chuyên mục & Lĩnh vực Hoạt động
          </h2>
        </div>
      </div>

      <div className="mt-5 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
        {items.map((item) => (
          <Link
            key={item.slug}
            href={ROUTES.category(item.slug)}
            className="group relative flex flex-col overflow-hidden rounded-xl border border-surface-border bg-white p-5 shadow-xs transition-all duration-300 ease-smooth hover:-translate-y-1 hover:border-brand-primary hover:shadow-md"
          >
            {/* Vạch Accent Bar 3px trên đỉnh */}
            <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-brand-primaryGradientFrom to-brand-primary opacity-0 transition-opacity duration-300 group-hover:opacity-100" />

            <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-brand-primaryLight transition-transform duration-300 ease-smooth group-hover:scale-110">
              {renderIcon(item.iconType)}
            </div>

            <h3 className="mt-4 text-sm font-bold text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary sm:text-base">
              {item.title}
            </h3>

            {item.description ? (
              <p className="mt-2 line-clamp-2 text-xs text-surface-textSecondary">
                {item.description}
              </p>
            ) : null}

            <div className="mt-4 flex items-center gap-1 text-xs font-semibold text-brand-primary">
              <span>Khám phá</span>
              <span className="transition-transform duration-200 group-hover:translate-x-1">➔</span>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}
