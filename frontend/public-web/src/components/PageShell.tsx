import { Breadcrumb, type BreadcrumbItem } from '@/components/Breadcrumb';

interface PageShellProps {
  title: string;
  description?: string;
  breadcrumb: BreadcrumbItem[];
  children: React.ReactNode;
}

/**
 * Khung chung cho tám trang nội dung dựng ở đợt chỉnh sửa 27/08/2026.
 *
 * <h3>Vì sao gom lại</h3>
 *
 * Tám trang mới (bốn của "Quản lý, vận hành", ba của "Giới thiệu", một trang Liên hệ) có cùng
 * một khung: breadcrumb, tiêu đề gạch chân màu thương hiệu, một dòng mô tả. Chép khung ấy tám
 * lần là tám nơi phải sửa khi Công ty đổi cách trình bày tiêu đề, và lần thứ chín sẽ lệch.
 *
 * ⚠ Cố ý <b>không</b> có sidebar: ba trang cũ (danh mục, bài viết, tìm kiếm) dùng bố cục 8:4
 * với `PortalSidebar`, còn tám trang này là bảng và sơ đồ — chúng cần cả chiều ngang. Ép cùng
 * một bố cục cho hai loại nội dung khác nhau là cách bảng 7 cột của CR-28 bị bóp thành 8/12
 * chiều rộng và phải cuộn ngang trên cả màn hình desktop.
 */
export function PageShell({ title, description, breadcrumb, children }: PageShellProps) {
  return (
    <div className="mx-auto max-w-[1232px] px-4 py-4 sm:px-6 animate-fade-in">
      <Breadcrumb items={breadcrumb} />

      <header className="mb-6 border-b-2 border-brand-primary pb-3">
        <div className="flex items-center gap-2.5">
          <span className="h-6 w-1.5 rounded-full bg-brand-primary" />
          <h1 className="text-lg font-bold tracking-tight text-surface-textBase sm:text-xl">
            {title}
          </h1>
        </div>
        {description ? (
          <p className="mt-2 text-sm text-surface-textSecondary">{description}</p>
        ) : null}
      </header>

      {children}
    </div>
  );
}
