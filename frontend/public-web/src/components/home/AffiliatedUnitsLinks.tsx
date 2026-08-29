import Link from 'next/link';

import type { MenuLink, SubsidiaryRow } from '@/lib/api';
import { isExternal, menuHref, ROUTES } from '@/lib/routes';
import { EmptyBlock } from './EmptyBlock';
import { SectionTitle } from './SectionTitle';

interface AffiliatedUnitsLinksProps {
  /** Xí nghiệp trực thuộc — CR-19. Rỗng khi Công ty chưa nhập (OI-05 còn chờ chốt 7 hay 8 XN). */
  subsidiaries: SubsidiaryRow[];
  /**
   * Liên kết sang cổng TTĐT cơ quan cấp trên — menu vị trí `LIEN_KET` (CR-21).
   *
   * ⛔ Trước 28/08/2026 đây là hằng số `EXTERNAL_PORTALS` viết cứng ngay trong tệp này. CR-21
   * yêu cầu Công ty *"rà soát lại tên và đường link chính thức"* — mà rà xong thì không có cách
   * nào sửa: đổi một cái tên là sửa mã nguồn rồi dựng lại image. Nay bốn dòng ấy nằm trong
   * `menu_items`, sửa ở đúng màn hình Menu mà Công ty đã dùng cho menu đầu trang và chân trang.
   */
  portalLinks: MenuLink[];
}

/**
 * Khối **ĐƠN VỊ TRỰC THUỘC &amp; MẠNG LƯỚI LIÊN KẾT** — CR-19, CR-21.
 *
 * <h2>CR-19: khối này đã hết "chưa được đấu nối"</h2>
 *
 * Nợ <b>T11.30</b> đã trả: {@code GET /api/v1/public/org-units/subsidiaries} là đường để cổng
 * lấy danh sách Xí nghiệp từ {@code org_units}. Trước lượt này không có đường nào, nên khối
 * chỉ có một câu báo lỗi.
 *
 * <p>⚠ Có đường không có nghĩa là có dữ liệu: bảng {@code org_units} cố ý <b>không seed</b>
 * (nó là dữ liệu chịu tải — phân quyền tầng 3 neo vào id của nó), và <b>OI-05</b> còn đang hỏi
 * Công ty chốt 7 hay 8 Xí nghiệp. Nên khối vẫn rỗng cho tới lượt nhập liệu — nhưng nay là rỗng
 * vì <i>chưa ai nhập</i>, không phải rỗng vì <i>không có đường nào để lấy</i>. Hai câu trả lời
 * khác nhau, và ô rỗng phải nói đúng câu của mình.
 */
export function AffiliatedUnitsLinks({ subsidiaries, portalLinks }: AffiliatedUnitsLinksProps) {
  return (
    <section className="mt-5">
      <SectionTitle
        href={ROUTES.gioiThieu.xiNghiep}
        phu={
          subsidiaries.length > 0 ? (
            <Link
              href={ROUTES.gioiThieu.xiNghiep}
              className="text-xs font-semibold text-brand-primary hover:underline"
            >
              Xem đầy đủ ➔
            </Link>
          ) : null
        }
      >
        Đơn vị trực thuộc
      </SectionTitle>

      <div className="mt-5">
        {subsidiaries.length === 0 ? (
          <EmptyBlock>
            Chưa có Xí nghiệp trực thuộc nào trong danh mục tổ chức. Danh sách này được nhập ở màn
            hình Sơ đồ tổ chức của trang quản trị.
          </EmptyBlock>
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {subsidiaries.map((xn) => (
              <div
                key={xn.code}
                className="flex h-full flex-col overflow-hidden rounded-lg border border-surface-border bg-white shadow-xs transition-colors hover:border-brand-primary"
              >
                {/* Dải nhận diện cao 90px — tỉ lệ thẻ logo của cổng tham chiếu. KHÔNG có ảnh
                    logo riêng cho từng Xí nghiệp (chúng dùng chung nhận diện Công ty), nên đây
                    là một dấu hiệu vẽ bằng SVG chứ không phải một ô ảnh rỗng chờ tệp. */}
                <div className="flex h-[90px] shrink-0 items-center gap-3 bg-gradient-to-br from-chrome-navy800 to-chrome-navy500 px-4">
                  <span className="flex h-[52px] w-[52px] shrink-0 items-center justify-center rounded-full border-2 border-brand-gold">
                    <svg
                      className="h-6 w-6 text-brand-gold"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                      aria-hidden="true"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={1.8}
                        d="M3 7h18M6 7v10M18 7v10M3 17h18M9 7v4h6V7"
                      />
                    </svg>
                  </span>
                  <span className="min-w-0">
                    <span className="block text-[10px] font-bold tracking-wide text-brand-gold">
                      Xí nghiệp trực thuộc
                    </span>
                    <span className="mt-0.5 block text-sm font-bold leading-tight text-white">
                      {xn.shortName || xn.name}
                    </span>
                  </span>
                </div>
                <div className="p-3.5">
                  {/* ⛔ Bản trước có tám xí nghiệp viết cứng KÈM SỐ ĐIỆN THOẠI, tất cả bịa
                    (§10.54). Nay mỗi ô chỉ hiện thứ thật sự có trong `org_units`; thiếu thì
                    không có dòng nào, không có dấu gạch giả làm một giá trị. */}
                  {xn.address ? (
                    <p className="mt-1 line-clamp-2 text-xs text-surface-textSecondary">
                      {xn.address}
                    </p>
                  ) : null}
                  {xn.phone ? (
                    <a
                      href={`tel:${xn.phone.replace(/\D/g, '')}`}
                      className="mt-1.5 inline-block text-xs font-semibold text-brand-primary hover:underline"
                    >
                      {xn.phone}
                    </a>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ⛔ Danh sách rỗng ⇒ KHÔNG render dải này. Không rơi về bốn liên kết mặc định: một dải
          liên kết chỉ hiện đúng lúc CSDL hỏng là dải không ai soi, và nó quảng cáo những địa
          chỉ mà không ai còn kiểm chứng (cùng cái bẫy đã gỡ khỏi `SiteHeader`, §10.54). */}
      {portalLinks.length > 0 ? (
        <div className="mt-6 flex flex-wrap items-center justify-center gap-3 rounded-xl border border-surface-border bg-surface-bgLayout p-4 text-xs sm:justify-between">
          <span className="font-bold text-surface-textBase">Liên kết cổng TTĐT:</span>
          <div className="flex flex-wrap items-center gap-3 sm:gap-4">
            {portalLinks.map((muc) => {
              const href = menuHref(muc);
              return href ? (
                <a
                  key={muc.label}
                  href={href}
                  target={muc.openNewTab ? '_blank' : undefined}
                  rel={isExternal(muc) ? 'noopener noreferrer' : undefined}
                  className="rounded-md border border-surface-border bg-white px-3 py-1.5 font-medium text-surface-textSecondary transition-all duration-200 hover:border-brand-primary hover:text-brand-primary hover:shadow-2xs"
                >
                  {muc.label} ↗
                </a>
              ) : null;
            })}
          </div>
        </div>
      ) : null}
    </section>
  );
}
