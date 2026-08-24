interface AffiliatedUnit {
  name: string;
  shortName: string;
  address: string;
  phone: string;
}

const AFFILIATED_UNITS: AffiliatedUnit[] = [
  {
    name: 'Xí nghiệp Thủy lợi Hà Đông',
    shortName: 'XN Hà Đông',
    address: 'Phường Vạn Phúc, Quận Hà Đông, Hà Nội',
    phone: '(024) 3382 4580',
  },
  {
    name: 'Xí nghiệp Thủy lợi Thanh Oai',
    shortName: 'XN Thanh Oai',
    address: 'Thị trấn Kim Bài, Huyện Thanh Oai, Hà Nội',
    phone: '(024) 3387 3210',
  },
  {
    name: 'Xí nghiệp Thủy lợi Ứng Hòa',
    shortName: 'XN Ứng Hòa',
    address: 'Thị trấn Vân Đình, Huyện Ứng Hòa, Hà Nội',
    phone: '(024) 3388 2315',
  },
  {
    name: 'Xí nghiệp Thủy lợi Phú Xuyên',
    shortName: 'XN Phú Xuyên',
    address: 'Thị trấn Phú Minh, Huyện Phú Xuyên, Hà Nội',
    phone: '(024) 3385 4120',
  },
  {
    name: 'Xí nghiệp Thủy lợi Nam Từ Liêm',
    shortName: 'XN Nam Từ Liêm',
    address: 'Phường Tây Mỗ, Quận Nam Từ Liêm, Hà Nội',
    phone: '(024) 3789 1234',
  },
  {
    name: 'Xí nghiệp Thủy lợi Hoài Đức',
    shortName: 'XN Hoài Đức',
    address: 'Thị trấn Trạm Trôi, Huyện Hoài Đức, Hà Nội',
    phone: '(024) 3366 5432',
  },
  {
    name: 'Xí nghiệp Thủy lợi Thường Tín',
    shortName: 'XN Thường Tín',
    address: 'Thị trấn Thường Tín, Huyện Thường Tín, Hà Nội',
    phone: '(024) 3376 4321',
  },
  {
    name: 'Xí nghiệp Thủy lợi Thanh Trì',
    shortName: 'XN Thanh Trì',
    address: 'Xã Tứ Hiệp, Huyện Thanh Trì, Hà Nội',
    phone: '(024) 3861 2345',
  },
];

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

      {/* Lưới các Xí nghiệp thủy lợi trực thuộc */}
      <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {AFFILIATED_UNITS.map((unit) => (
          <div
            key={unit.name}
            className="group flex flex-col justify-between rounded-lg border border-surface-border bg-white p-3.5 shadow-2xs transition-all duration-200 hover:border-brand-primary hover:shadow-xs"
          >
            <div>
              <span className="text-[11px] font-bold text-brand-primary uppercase tracking-wider">
                {unit.shortName}
              </span>
              <h3 className="mt-0.5 text-xs font-semibold text-surface-textBase">{unit.name}</h3>
              <p className="mt-1 line-clamp-1 text-[11px] text-surface-textSecondary">
                📍 {unit.address}
              </p>
            </div>
            <div className="mt-2.5 flex items-center justify-between border-t border-surface-border pt-2 text-[11px]">
              <span className="text-surface-textSecondary">Điện thoại:</span>
              <a
                href={`tel:${unit.phone.replace(/\D/g, '')}`}
                className="font-medium text-brand-primary hover:underline"
              >
                {unit.phone}
              </a>
            </div>
          </div>
        ))}
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
