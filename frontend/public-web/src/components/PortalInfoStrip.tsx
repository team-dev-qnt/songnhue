import { PortalClock } from '@/components/PortalClock';
import { PortalTicker, type TickerItem } from '@/components/home/PortalTicker';
import { ROUTES } from '@/lib/routes';

/**
 * Dải thông tin nằm **dưới** thanh điều hướng — bố cục 29/08/2026.
 *
 * <pre>
 *   [ đồng hồ ]│[ ────── chữ chạy ────── ]│[ trực ban PCTT ]
 * </pre>
 *
 * <h2>Vì sao trực ban KHÔNG chạy cùng dải</h2>
 *
 * Số trực ban phòng chống thiên tai là thứ người ta tìm đúng vào lúc hoảng. Một con số chỉ
 * hiện ra vài giây mỗi vòng là một con số phải chờ. Nó ghim bên phải và đứng yên; phần chạy
 * chỉ chở tin bài và thông tin hành chính.
 *
 * <h2>Dưới lg thì bỏ đồng hồ, không xuống dòng</h2>
 *
 * Ba phần trên một hàng 390px là ba phần đều bị bóp. Đồng hồ là phần ít giá trị nhất trong
 * ba (điện thoại nào cũng có sẵn một cái) nên nó ẩn trước — cổng tham chiếu cũng bỏ đúng
 * phần này ở bề rộng nhỏ.
 */
interface PortalInfoStripProps {
  /** Bài mới nhất — đã lấy sẵn ở phía máy chủ. Rỗng thì dải chỉ còn phần thông tin. */
  tinMoi: { slug: string; title: string }[];
  hotline: string;
  gioLamViec: string;
  email: string;
}

export function PortalInfoStrip({ tinMoi, hotline, gioLamViec, email }: PortalInfoStripProps) {
  // ⛔ Không mục nào viết cứng: bài từ API, hai mục còn lại từ `settings`. Khoá rỗng thì mục
  //    ấy biến mất khỏi dải chứ không thay bằng chữ mặc định (luật 16).
  const muc: TickerItem[] = [
    ...tinMoi.map((b) => ({ text: b.title, href: ROUTES.article(b.slug) })),
    ...(gioLamViec ? [{ text: `Giờ làm việc: ${gioLamViec}` }] : []),
    ...(email ? [{ text: `Thư điện tử: ${email}` }] : []),
  ];

  if (muc.length === 0 && !hotline) return null;

  return (
    <div className="w-full border-b border-surface-border/60 bg-surface-bgLayout/50">
      <div className="mx-auto flex max-w-[1232px] items-center gap-5 px-4 sm:px-6">
        <div className="hidden shrink-0 border-r border-brand-primary py-3 pr-5 text-sm text-surface-textSecondary lg:block">
          <PortalClock />
        </div>

        <PortalTicker items={muc} />

        {hotline ? (
          <div className="flex shrink-0 items-center gap-2 border-l border-surface-border/60 py-3 pl-5">
            <span className="flex h-2 w-2 shrink-0 rounded-full bg-red-600" />
            <span className="hidden whitespace-nowrap text-[13px] text-surface-textSecondary sm:inline">
              Trực ban PCTT 24/7:
            </span>
            <a
              href={`tel:${hotline.replace(/\D/g, '')}`}
              className="whitespace-nowrap text-[13px] font-bold text-red-700 hover:underline"
            >
              {hotline}
            </a>
          </div>
        ) : null}
      </div>
    </div>
  );
}
