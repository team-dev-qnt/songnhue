import type { Metadata } from 'next';

import { ContactForm } from '@/components/ContactForm';
import { EmptyBlock } from '@/components/home/EmptyBlock';
import { PageShell } from '@/components/PageShell';
import { getSiteConfig, getSubsidiaries } from '@/lib/api';
import { ROUTES } from '@/lib/routes';
import { SITE } from '@/lib/site';

export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Liên hệ - Thủy lợi Sông Nhuệ',
  description:
    'Địa chỉ trụ sở, điện thoại, fax, email, đầu mối liên hệ các Xí nghiệp và bản đồ đường đi tới Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ.',
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
 * <h2>⭐ 29/08 — trang này là NƠI ĐẦY ĐỦ DUY NHẤT của thông tin liên hệ</h2>
 *
 * Công ty yêu cầu dồn toàn bộ mục "Trụ sở &amp; đầu mối liên hệ" từ trang chủ về đây. Trước đó
 * cùng bộ khoá `company.*` hiển thị ở <b>ba</b> nơi: chân trang (rút gọn), khối trang chủ
 * (trung bình), trang này (đầy đủ). Ba mức đầy đủ khác nhau cho cùng một thứ nghĩa là người
 * đọc phải đoán chỗ nào mới là chỗ có đủ, và người sửa mã phải nhớ ba chỗ mỗi lần thêm một
 * trường. Nay: chân trang giữ dòng tóm tắt + bản đồ, trang chủ chỉ còn biểu mẫu, mọi con số
 * nằm ở đây.
 *
 * <p>Bổ sung cùng lượt: <b>đầu mối liên hệ từng Xí nghiệp</b> (điện thoại, thư điện tử, số của
 * Giám đốc) đọc từ `org_units` — trước nay chỉ có ở `/gioi-thieu/xi-nghiep`, tức người đi tìm
 * một số điện thoại phải biết trước rằng nó nằm trong mục Giới thiệu.
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
 * <h2>⭐ 29/08: form liên hệ ĐÃ dựng — điều kiện cũ nay đã đủ</h2>
 *
 * Chú thích trước ở đây từ chối dựng form, và lý do ấy đúng vào lúc ấy: *"một form gửi đi mà
 * không ai nhận tệ hơn hẳn không có form: người dân tin là đã gửi được"*. Bốn thứ nó đòi nay có
 * cả bốn — bảng `contacts` (`V202608291043`), endpoint `POST /api/v1/public/contacts`, hạn mức
 * tần suất trên tiền tố công khai, và màn hình cho người xử lý (`cms:contact:manage` nay có
 * `ContactController` đứng sau). `ContactHttpTest` đi trọn vòng: gửi bằng đường công khai, đọc
 * lại bằng đường quản trị.
 *
 * <p>⛔ Còn thiếu <b>reCAPTCHA</b> — khoá thuộc G13, Công ty chưa cấp. Ghi ra để sự vắng mặt của
 * nó không bị đọc thành "đã cân nhắc và không cần".
 */
export default async function LienHePage() {
  const [config, subsidiaries] = await Promise.all([getSiteConfig(), getSubsidiaries()]);

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

  // ⛔ Ô nào rỗng thì BIẾN MẤT khỏi danh sách, không hiện nhãn kèm dấu gạch — một dấu gạch
  //    trông như một giá trị đã kiểm chứng (quy tắc 16).
  const dong = [
    { nhan: 'Địa chỉ trụ sở', giaTri: diaChi, icon: 'diaChi' as const },
    { nhan: 'Điện thoại', giaTri: dienThoai, icon: 'dienThoai' as const, dienThoai: true },
    { nhan: 'Fax', giaTri: fax, icon: 'fax' as const },
    { nhan: 'Thư điện tử', giaTri: email, icon: 'thu' as const, thu: true },
    { nhan: 'Giờ làm việc', giaTri: gioLamViec, icon: 'gio' as const },
  ].filter((d) => d.giaTri);

  // ⚠ 01/09/2026: bỏ `x.directorPhone` khỏi điều kiện — trường ấy đã gỡ khỏi record công khai
  //   (số của một cá nhân, NĐ 13/2023). Một Xí nghiệp chỉ có tên giám đốc mà không có tổng đài
  //   hay hộp thư thì KHÔNG còn là một đầu mối liên hệ, nên không lên bảng này.
  const dauMoi = (subsidiaries ?? []).filter((x) => x.phone || x.email);

  return (
    <PageShell title="Liên hệ" description={tenCongTy} breadcrumb={[{ label: 'Liên hệ' }]}>
      {/* ───── Trực ban PCTT — đặt TRÊN mọi thứ khác, không nằm trong danh sách ─────
          Người vào trang này chia làm hai nhóm rất khác nhau: người có việc hành chính và
          người đang có sự cố. Nhóm thứ hai không đọc danh sách — nên số trực ban phải là thứ
          đầu tiên đập vào mắt, không phải dòng thứ năm của một bảng. */}
      {hotline ? (
        <section className="mb-6 flex flex-col gap-3 rounded-xl border border-red-200 bg-red-50/70 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-[11px] font-bold tracking-wide text-red-700">
              Trực ban phòng chống thiên tai 24/7
            </p>
            <a
              href={`tel:${hotline.replace(/\D/g, '')}`}
              className="mt-1 block text-2xl font-extrabold text-red-700 hover:underline"
            >
              {hotline}
            </a>
          </div>
          <p className="max-w-md text-xs leading-relaxed text-surface-textSecondary">
            Việc khẩn cấp thì gọi số này. Biểu mẫu bên dưới đi theo luồng xử lý hành chính — có
            người đọc, nhưng không phải ngay lập tức.
          </p>
        </section>
      ) : null}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <section className="rounded-xl border border-surface-border bg-white p-5 shadow-xs">
          <h2 className="border-b border-surface-border pb-3 text-sm font-bold tracking-tight text-brand-primary">
            Trụ sở &amp; đầu mối liên hệ
          </h2>
          {dong.length === 0 ? (
            <div className="mt-4">
              <EmptyBlock>
                Thông tin liên hệ chưa được cấu hình. Các khoá nhóm `company.*` được nhập ở màn hình
                Cấu hình hệ thống của trang quản trị.
              </EmptyBlock>
            </div>
          ) : (
            <dl className="mt-4 flex flex-col gap-4 text-sm">
              {dong.map((d) => (
                <div key={d.nhan} className="flex items-start gap-3">
                  <span
                    aria-hidden="true"
                    className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-primaryLight text-brand-primary"
                  >
                    <BieuTuongLienHe loai={d.icon} />
                  </span>
                  <div className="min-w-0">
                    <dt className="text-[11px] font-bold tracking-wide text-surface-textSecondary">
                      {d.nhan}
                    </dt>
                    <dd className="mt-0.5 leading-relaxed text-surface-textBase">
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
                </div>
              ))}
            </dl>
          )}
        </section>

        <section className="flex flex-col rounded-xl border border-surface-border bg-white p-5 shadow-xs">
          <h2 className="border-b border-surface-border pb-3 text-sm font-bold tracking-tight text-brand-primary">
            Bản đồ trụ sở
          </h2>
          <div className="mt-4 flex-1">
            {mapEmbed ? (
              <div
                className="h-full overflow-hidden rounded-lg border border-surface-border [&_iframe]:h-full [&_iframe]:min-h-[320px] [&_iframe]:w-full [&_iframe]:border-0"
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

      {/* ───── Đầu mối liên hệ các Xí nghiệp — CR-26, đọc `org_units` ───── */}
      <section className="mt-6 rounded-xl border border-surface-border bg-white p-5 shadow-xs">
        <h2 className="border-b border-surface-border pb-3 text-sm font-bold tracking-tight text-brand-primary">
          Đầu mối liên hệ các Xí nghiệp
        </h2>
        <div className="mt-4">
          {dauMoi.length === 0 ? (
            <EmptyBlock>
              Chưa có Xí nghiệp nào trong danh mục tổ chức có số điện thoại hoặc thư điện tử. Danh
              sách nhập ở màn hình Sơ đồ tổ chức của trang quản trị; cổng đọc thẳng từ đó nên không
              có bản sao nào để cập nhật riêng.
            </EmptyBlock>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-surface-border text-left text-[11px] font-bold tracking-wide text-surface-textSecondary">
                    <th className="py-2.5 pr-4 font-bold">Xí nghiệp</th>
                    <th className="py-2.5 pr-4 font-bold">Điện thoại</th>
                    <th className="py-2.5 pr-4 font-bold">Thư điện tử</th>
                    <th className="py-2.5 font-bold">Giám đốc</th>
                  </tr>
                </thead>
                <tbody>
                  {dauMoi.map((xn) => (
                    <tr key={xn.code} className="border-b border-surface-border/60 last:border-b-0">
                      <td className="py-3 pr-4 font-semibold text-surface-textBase">{xn.name}</td>
                      <td className="py-3 pr-4">
                        {xn.phone ? (
                          <a
                            href={`tel:${xn.phone.replace(/\D/g, '')}`}
                            className="text-brand-primary hover:underline"
                          >
                            {xn.phone}
                          </a>
                        ) : (
                          <span className="text-surface-textSecondary">Chưa có</span>
                        )}
                      </td>
                      <td className="py-3 pr-4">
                        {xn.email ? (
                          <a
                            href={`mailto:${xn.email}`}
                            className="text-brand-primary hover:underline"
                          >
                            {xn.email}
                          </a>
                        ) : (
                          <span className="text-surface-textSecondary">Chưa có</span>
                        )}
                      </td>
                      <td className="py-3">
                        {xn.directorName ? (
                          <span className="text-surface-textBase">{xn.directorName}</span>
                        ) : (
                          <span className="text-surface-textSecondary">Chưa có</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>

      {/* ───── Biểu mẫu — kín bề rộng, cùng component với trang chủ ───── */}
      <section className="mt-6 rounded-xl border border-surface-border bg-white p-5 shadow-xs sm:p-6">
        <h2 className="border-b border-surface-border pb-3 text-sm font-bold tracking-tight text-brand-primary">
          Gửi phản ánh, kiến nghị
        </h2>
        <div className="mt-4">
          <ContactForm />
        </div>
      </section>
    </PageShell>
  );
}

/** Biểu tượng của một dòng liên hệ — thuần trang trí, `aria-hidden` đặt ở thẻ bọc. */
function BieuTuongLienHe({ loai }: { loai: 'diaChi' | 'dienThoai' | 'fax' | 'thu' | 'gio' }) {
  const d = {
    diaChi:
      'M17.657 16.657 13.414 20.9a2 2 0 0 1-2.827 0l-4.244-4.243a8 8 0 1 1 11.314 0zM15 11a3 3 0 1 1-6 0 3 3 0 0 1 6 0z',
    dienThoai:
      'M3 5a2 2 0 0 1 2-2h3.28a1 1 0 0 1 .948.684l1.498 4.493a1 1 0 0 1-.502 1.21l-2.257 1.13a11 11 0 0 0 5.516 5.516l1.13-2.257a1 1 0 0 1 1.21-.502l4.493 1.498a1 1 0 0 1 .684.949V19a2 2 0 0 1-2 2h-1C9.716 21 3 14.284 3 6V5z',
    fax: 'M17 17h2a2 2 0 0 0 2-2v-4a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v4a2 2 0 0 0 2 2h2m2 4h6a2 2 0 0 0 2-2v-4a2 2 0 0 0-2-2H9a2 2 0 0 0-2 2v4a2 2 0 0 0 2 2zm8-12V5a2 2 0 0 0-2-2H9a2 2 0 0 0-2 2v4h10z',
    thu: 'M3 8l7.89 5.26a2 2 0 0 0 2.22 0L21 8M5 19h14a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2z',
    gio: 'M12 8v4l3 3m6-3a9 9 0 1 1-18 0 9 9 0 0 1 18 0z',
  }[loai];

  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d={d} />
    </svg>
  );
}
