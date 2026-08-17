import { SITE } from '@/lib/site';

export function SiteFooter() {
  return (
    <footer className="mt-12 border-t border-surface-border bg-surface-bgLayout">
      <div className="mx-auto max-w-6xl px-4 py-8 text-sm text-surface-textSecondary">
        <p className="font-semibold text-surface-textBase">{SITE.name}</p>
        {/*
          Thông tin liên hệ, giấy phép trang thông tin điện tử và tên người chịu trách
          nhiệm nội dung là bắt buộc với cổng thông tin của tổ chức nhà nước — nhưng đó là
          dữ liệu Công ty cấp, chưa có. Để trống chỗ này còn hơn điền số giả rồi quên thay.
        */}
        <p className="mt-2">
          Nội dung liên hệ và thông tin giấy phép sẽ do Công ty cung cấp và quản trị trong phần cấu
          hình hệ thống.
        </p>
        <p className="mt-4 text-xs">
          © {new Date().getFullYear()} {SITE.shortName}
        </p>
      </div>
    </footer>
  );
}
