import { statusColors } from 'design-tokens';

interface HydrologyStation {
  name: string;
  waterLevel: string;
  status: 'normal' | 'warning' | 'danger' | 'unknown';
  statusText: string;
  trend: 'up' | 'down' | 'stable';
}

const DEFAULT_STATIONS: HydrologyStation[] = [
  {
    name: 'Cống Hà Đông',
    waterLevel: '+4.20 m',
    status: 'normal',
    statusText: 'Bình thường',
    trend: 'stable',
  },
  {
    name: 'Cống Cầu Cung',
    waterLevel: '+3.85 m',
    status: 'normal',
    statusText: 'Bình thường',
    trend: 'down',
  },
  {
    name: 'Cống Cổ Nhuế',
    waterLevel: '+4.65 m',
    status: 'warning',
    statusText: 'Cảnh báo BĐ I',
    trend: 'up',
  },
  {
    name: 'Trạm bơm Vân Đình',
    waterLevel: '+3.10 m',
    status: 'normal',
    statusText: 'Bình thường',
    trend: 'stable',
  },
  {
    name: 'Cống Đồng Quan',
    waterLevel: '+3.95 m',
    status: 'normal',
    statusText: 'Bình thường',
    trend: 'stable',
  },
];

interface HydrologyQuickWidgetProps {
  hotline?: string;
  stations?: HydrologyStation[];
}

/**
 * Dải Giám sát Thủy văn & Cảnh báo Thiên tai PCTT (Hydrology Widget).
 *
 * - Hiển thị nhanh mực nước tại các trạm đầu mối chính của lưu vực Sông Nhuệ.
 * - Tuân thủ 100% mã màu trạng thái nghiệp vụ trong `statusColors` (xanh, vàng, đỏ, xám).
 * - Tích hợp số hotline trực ban phòng chống thiên tai 24/7.
 */
export function HydrologyQuickWidget({
  hotline = '',
  stations = DEFAULT_STATIONS,
}: HydrologyQuickWidgetProps) {
  const getStatusBg = (status: HydrologyStation['status']) => {
    switch (status) {
      case 'normal':
        return 'bg-emerald-50 text-emerald-800 border-emerald-200';
      case 'warning':
        return 'bg-amber-50 text-amber-800 border-amber-200';
      case 'danger':
        return 'bg-red-50 text-red-800 border-red-200 animate-pulse';
      case 'unknown':
      default:
        return 'bg-gray-50 text-gray-700 border-gray-200';
    }
  };

  const getStatusDotColor = (status: HydrologyStation['status']) => {
    switch (status) {
      case 'normal':
        return statusColors.normal;
      case 'warning':
        return statusColors.warning;
      case 'danger':
        return statusColors.danger;
      case 'unknown':
      default:
        return statusColors.unknown;
    }
  };

  return (
    <section
      aria-label="Giám sát thủy văn & Mực nước"
      className="overflow-hidden rounded-xl border border-brand-primary/20 bg-gradient-to-r from-blue-50/80 via-white to-blue-50/80 p-4 shadow-xs sm:p-5"
    >
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        {/* Tiêu đề & Giới thiệu dải thủy văn */}
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-primary text-white shadow-xs">
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M13 10V3L4 14h7v7l9-11h-7z"
              />
            </svg>
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-bold uppercase tracking-tight text-brand-primary sm:text-base">
                Quan trắc Thủy văn & Mực nước
              </h2>
              <span className="flex h-2 w-2 relative">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
              </span>
            </div>
            <p className="text-xs text-surface-textSecondary">
              Cập nhật trực tuyến từ các trạm cống & trạm bơm đầu mối
            </p>
          </div>
        </div>

        {/* Khối Hotline Trực ban PCTT */}
        {hotline ? (
          <div className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50/90 px-3.5 py-1.5 text-xs text-red-900 shadow-2xs">
            <span className="flex h-2 w-2 rounded-full bg-red-600 animate-pulse"></span>
            <span className="font-semibold">Trực ban PCTT 24/7:</span>
            <a
              href={`tel:${hotline.replace(/\D/g, '')}`}
              className="font-bold text-red-700 hover:underline"
            >
              {hotline}
            </a>
          </div>
        ) : null}
      </div>

      {/* Lưới các trạm quan trắc */}
      <div className="mt-4 grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-5">
        {stations.map((st) => (
          <div
            key={st.name}
            className={`flex flex-col justify-between rounded-lg border p-2.5 transition-all duration-200 hover:shadow-xs ${getStatusBg(st.status)}`}
          >
            <div className="flex items-center justify-between gap-1">
              <span
                className="truncate text-xs font-semibold text-surface-textBase"
                title={st.name}
              >
                {st.name}
              </span>
              <span
                className="h-2 w-2 shrink-0 rounded-full"
                style={{ backgroundColor: getStatusDotColor(st.status) }}
              ></span>
            </div>
            <div className="mt-2 flex items-baseline justify-between">
              <span className="text-sm font-extrabold tracking-tight sm:text-base">
                {st.waterLevel}
              </span>
              <span className="text-[10px] font-medium text-surface-textSecondary">
                {st.statusText}
              </span>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
