import { EmptyBlock } from './EmptyBlock';

const EXTERNAL_PORTALS = [
  { name: 'Bộ Nông nghiệp & PTNT', url: 'https://www.mard.gov.vn' },
  { name: 'UBND Thành phố Hà Nội', url: 'https://hanoi.gov.vn' },
  { name: 'Sở Nông nghiệp & PTNT Hà Nội', url: 'https://sonnptnt.hanoi.gov.vn' },
  { name: 'Cục Thủy lợi', url: 'http://cucthuyloi.gov.vn' },
];

export function AffiliatedUnitsLinks() {
  return (
    <section className="mt-10 sm:mt-14">
      <div className="flex items-center justify-between border-b-2 border-brand-primary pb-2.5">
        <div className="flex items-center gap-2">
          <span className="h-5 w-1.5 rounded-full bg-brand-primary"></span>
          <h2 className="text-base font-bold uppercase tracking-tight text-surface-textBase sm:text-lg">
            Đơn vị Trực thuộc & Mạng lưới Liên kết
          </h2>
        </div>
      </div>

      {/* Đơn vị trực thuộc
        ⛔ Bản trước liệt kê 8 xí nghiệp kèm địa chỉ và SỐ ĐIỆN THOẠI, tất cả viết cứng và không
           có thật. Bộ canh số điện thoại ở `siteContactConfig.test.ts` nhận đúng hình dạng ấy
           từ 24/8 — nhưng nó chỉ đọc `SiteFooter.tsx` và `SiteHeader.tsx`, nên tám số này nằm
           ngoài tầm với suốt thời gian đó (luật 12 · §10.54).

           Danh sách đơn vị phải đến từ `org_units`; chưa có endpoint công khai nào phục vụ nó.
      */}
      <div className="mt-5">
        <EmptyBlock>
          Danh sách đơn vị trực thuộc chưa được đấu nối từ danh mục tổ chức của hệ thống.
        </EmptyBlock>
      </div>

      {/* Cổng thông tin cơ quan cấp trên & đối tác */}
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
