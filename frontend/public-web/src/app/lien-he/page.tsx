import type { Metadata } from 'next';

import { EmptyBlock } from '@/components/home/EmptyBlock';
import { PageShell } from '@/components/PageShell';
import { getSiteConfig } from '@/lib/api';
import { ROUTES } from '@/lib/routes';
import { SITE } from '@/lib/site';

export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Liên hệ - Thủy lợi Sông Nhuệ',
  description:
    'Địa chỉ trụ sở, điện thoại, fax, email và bản đồ đường đi tới Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ.',
  alternates: { canonical: ROUTES.lienHe },
};

/**
 * **Liên hệ** — CR-22, và mục cấp 1 thứ bảy của cây nội dung §3.
 *
 * <h2>Từ một bài viết thành một trang</h2>
 *
 * Trước đợt này "Liên hệ" là bài viết `/bai-viet/lien-he` với nội dung *"Nội dung đang được cập
 * nhật"*. Vấn đề không phải nội dung rỗng mà là <b>chỗ đặt</b>: địa chỉ và số điện thoại của
 * Công ty đã nằm trong `settings` (nhóm `company.*`, sửa được trên màn hình cấu hình), nên để
 * chúng trong thân một bài viết là tạo nguồn thứ hai — biên tập viên sửa bài, chân trang vẫn
 * hiện số cũ, và không ai biết bên nào đúng.
 *
 * <p>Trang này đọc thẳng `settings`. Một nguồn, hai nơi hiển thị.
 *
 * <h2>Bản đồ dùng CHUNG khoá với chân trang</h2>
 *
 * `site.footer.map-embed` giữ mã nhúng Google Map của trụ sở, đã qua
 * `HtmlSanitizer.cleanMapEmbed()` lúc ghi. Trang này và chân trang cùng đọc khoá ấy — một trụ
 * sở thì một bản đồ (luật 14). Tên khoá còn tiền tố `footer` là dấu vết lịch sử; đổi tên khoá
 * là một migration đụng vào dữ liệu đang chạy để đổi lấy sự gọn gàng, chưa đáng.
 *
 * <p>⚠ Khung nhúng cần `frame-src https://www.google.com` trong CSP — đã mở ở `next.config.ts`
 * cùng lượt này. Trước đó cổng công khai không có CSP nào, nên khung nào cũng nhúng được và
 * chuyện đó mới là vấn đề.
 *
 * <h2>⛔ Form liên hệ chưa dựng — và vì sao không dựng tạm</h2>
 *
 * §3 mô tả trang Liên hệ gồm cả form. CR-22 chỉ yêu cầu bản đồ, và form thì <b>không dựng được
 * ở tầng giao diện</b>: nó cần một bảng lưu, một endpoint nhận, chống spam, và một màn hình
 * cho người xử lý (quyền `cms:contact:manage` đã có trong RBAC nhưng chưa có gì đứng sau).
 * Một form gửi đi mà không ai nhận tệ hơn hẳn không có form: người dân tin là đã gửi được.
 */
export default async function LienHePage() {
  const config = await getSiteConfig();

  const tenCongTy = config?.['site.name'] ?? SITE.name;
  // ⛔ Dự phòng RỖNG, không phải giá trị thật viết cứng. Ngày 24/8 một bản vá giao diện đã đặt
  //    lại đúng bộ giá trị thật làm dự phòng ở `SiteFooter`, và nó khôi phục nguyên trạng lỗi
  //    cũ theo hình dạng khó thấy hơn: màn hình vẫn đúng nên không ai biết số điện thoại người
  //    dân gọi khi có sự cố lại đang nằm trong mã nguồn.
  const diaChi = config?.['company.address'] ?? '';
  const dienThoai = config?.['company.phone'] ?? '';
  const fax = config?.['company.fax'] ?? '';
  const email = config?.['company.email'] ?? '';
  const hotline = config?.['company.hotline'] ?? '';
  const gioLamViec = config?.['company.working-hours'] ?? '';
  const mapEmbed = config?.['site.footer.map-embed'] ?? '';

  const dong = [
    { nhan: 'Địa chỉ trụ sở', giaTri: diaChi },
    { nhan: 'Điện thoại', giaTri: dienThoai, dienThoai: true },
    { nhan: 'Fax', giaTri: fax },
    { nhan: 'Email', giaTri: email, thu: true },
    { nhan: 'Trực ban PCTT 24/7', giaTri: hotline, dienThoai: true },
    { nhan: 'Giờ làm việc', giaTri: gioLamViec },
  ].filter((d) => d.giaTri);

  return (
    <PageShell title="Liên hệ" description={tenCongTy} breadcrumb={[{ label: 'Liên hệ' }]}>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <section className="rounded-xl border border-surface-border bg-white p-5 shadow-xs">
          <h2 className="border-b border-surface-border pb-3 text-sm font-bold uppercase tracking-tight text-brand-primary">
            Thông tin liên hệ
          </h2>
          {dong.length === 0 ? (
            <div className="mt-4">
              <EmptyBlock>
                Thông tin liên hệ chưa được cấu hình. Các khoá nhóm `company.*` được nhập ở màn hình
                Cấu hình hệ thống của trang quản trị.
              </EmptyBlock>
            </div>
          ) : (
            <dl className="mt-4 divide-y divide-surface-border text-sm">
              {dong.map((d) => (
                <div key={d.nhan} className="flex flex-col gap-1 py-3 sm:flex-row sm:gap-4">
                  <dt className="w-44 shrink-0 font-semibold text-surface-textSecondary">
                    {d.nhan}
                  </dt>
                  <dd className="text-surface-textBase">
                    {d.dienThoai ? (
                      <a
                        href={`tel:${d.giaTri.replace(/\D/g, '')}`}
                        className="font-medium text-brand-primary hover:underline"
                      >
                        {d.giaTri}
                      </a>
                    ) : d.thu ? (
                      <a
                        href={`mailto:${d.giaTri}`}
                        className="font-medium text-brand-primary hover:underline"
                      >
                        {d.giaTri}
                      </a>
                    ) : (
                      d.giaTri
                    )}
                  </dd>
                </div>
              ))}
            </dl>
          )}
        </section>

        <section className="rounded-xl border border-surface-border bg-white p-5 shadow-xs">
          <h2 className="border-b border-surface-border pb-3 text-sm font-bold uppercase tracking-tight text-brand-primary">
            Bản đồ trụ sở
          </h2>
          <div className="mt-4">
            {mapEmbed ? (
              <div
                className="overflow-hidden rounded-lg border border-surface-border [&_iframe]:h-80 [&_iframe]:w-full [&_iframe]:border-0"
                // eslint-disable-next-line react/no-danger -- HtmlSanitizer.cleanMapEmbed() lúc ghi
                dangerouslySetInnerHTML={{ __html: mapEmbed }}
              />
            ) : (
              <>
                <EmptyBlock>
                  Mã nhúng bản đồ chưa được cấu hình (khoá `site.footer.map-embed` ở màn hình Cấu
                  hình hệ thống).
                </EmptyBlock>
                {diaChi ? (
                  <a
                    href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
                      `${tenCongTy} ${diaChi}`,
                    )}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-brand-primary hover:underline"
                  >
                    <span>Tra địa chỉ trên Google Maps</span>
                    <span aria-hidden="true">↗</span>
                  </a>
                ) : null}
              </>
            )}
          </div>
        </section>
      </div>

      {/* ───── Form liên hệ — chưa dựng, nói thẳng thay vì dựng một form không ai nhận ───── */}
      <section className="mt-6 rounded-xl border border-surface-border bg-white p-5 shadow-xs">
        <h2 className="border-b border-surface-border pb-3 text-sm font-bold uppercase tracking-tight text-brand-primary">
          Gửi phản ánh, kiến nghị
        </h2>
        <div className="mt-4">
          <EmptyBlock>
            Biểu mẫu gửi phản ánh trực tuyến chưa được dựng — nó cần một nơi lưu và một đầu mối tiếp
            nhận ở phía quản trị, không chỉ là ô nhập trên trang. Trong lúc chờ, vui lòng liên hệ
            qua điện thoại hoặc email ở trên.
          </EmptyBlock>
        </div>
      </section>
    </PageShell>
  );
}
