import type { SubsidiaryRow } from '@/lib/api';
import { ROUTES } from '@/lib/routes';
import Link from 'next/link';
import { EmptyBlock } from './EmptyBlock';

/**
 * Cổng TTĐT của cơ quan quản lý cấp trên — liên kết điều hướng, không phải dữ liệu nghiệp vụ
 * của Công ty, nên không có endpoint nào phục vụ chúng và cũng không nên có.
 *
 * ⚠ CR-21 yêu cầu **rà soát lại tên và đường dẫn theo tổ chức hiện hành**. "Sở Nông nghiệp và
 * Môi trường" là tên tài liệu chỉ định đích danh — đã sửa. Hai dòng còn lại giữ nguyên tên
 * đang dùng và **chờ Công ty xác nhận**: đổi tên một cơ quan nhà nước theo suy đoán của người
 * viết mã thì sai cũng không ai phát hiện được, mà cổng lại là nơi công bố.
 */
const EXTERNAL_PORTALS = [
  { name: 'Bộ Nông nghiệp & PTNT', url: 'https://www.mard.gov.vn' },
  { name: 'UBND Thành phố Hà Nội', url: 'https://hanoi.gov.vn' },
  { name: 'Sở Nông nghiệp và Môi trường Hà Nội', url: 'https://sonnptnt.hanoi.gov.vn' },
  { name: 'Cục Thủy lợi', url: 'http://cucthuyloi.gov.vn' },
];

interface AffiliatedUnitsLinksProps {
  /** Xí nghiệp trực thuộc — CR-19. Rỗng khi Công ty chưa nhập (OI-05 còn chờ chốt 7 hay 8 XN). */
  subsidiaries: SubsidiaryRow[];
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
export function AffiliatedUnitsLinks({ subsidiaries }: AffiliatedUnitsLinksProps) {
  return (
    <section className="mt-10 sm:mt-14">
      <div className="flex items-center justify-between border-b-2 border-brand-primary pb-2.5">
        <div className="flex items-center gap-2">
          <span className="h-5 w-1.5 rounded-full bg-brand-primary"></span>
          <h2 className="text-base font-bold uppercase tracking-tight text-surface-textBase sm:text-lg">
            Đơn vị Trực thuộc &amp; Mạng lưới Liên kết
          </h2>
        </div>
        {subsidiaries.length > 0 ? (
          <Link
            href={ROUTES.gioiThieu.xiNghiep}
            className="text-xs font-semibold text-brand-primary hover:underline"
          >
            Xem đầy đủ ➔
          </Link>
        ) : null}
      </div>

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
                className="rounded-lg border border-surface-border bg-white p-3.5 shadow-xs transition-colors hover:border-brand-primary"
              >
                <p className="text-sm font-bold text-surface-textBase">{xn.shortName || xn.name}</p>
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
            ))}
          </div>
        )}
      </div>

      <div className="mt-6 flex flex-wrap items-center justify-center gap-3 rounded-xl border border-surface-border bg-surface-bgLayout p-4 text-xs sm:justify-between">
        <span className="font-bold text-surface-textBase">Liên kết Cổng TTĐT:</span>
        <div className="flex flex-wrap items-center gap-3 sm:gap-4">
          {EXTERNAL_PORTALS.map((p) => (
            <a
              key={p.name}
              href={p.url}
              target="_blank"
              rel="noopener noreferrer"
              className="rounded-md border border-surface-border bg-white px-3 py-1.5 font-medium text-surface-textSecondary transition-all duration-200 hover:border-brand-primary hover:text-brand-primary hover:shadow-2xs"
            >
              {p.name} ↗
            </a>
          ))}
        </div>
      </div>
    </section>
  );
}
