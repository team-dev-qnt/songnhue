import { EmptyBlock } from './EmptyBlock';

interface PhotoItem {
  id: string;
  title: string;
  location: string;
  imageUrl: string;
}

interface HomeMediaGalleryProps {
  videoId?: string;
  videoTitle?: string;
  photos?: PhotoItem[];
}

/**
 * Khối Truyền thông Đa phương tiện & Thư viện Ảnh (Media Hub).
 *
 * - Video phóng sự nhúng an toàn qua domain `youtube-nocookie.com` tuân thủ CSP.
 * - Thư viện hình ảnh các công trình thủy lợi tiêu biểu.
 *
 * <h2>⚠⚠ Ba props này TỪNG không nơi gọi nào truyền</h2>
 *
 * Trang chủ gọi `<HomeMediaGallery />` trần cho tới 28/08/2026, nên `videoId` luôn `undefined` và
 * khối luôn hiện hai ô rỗng — ở dev, ở staging, ở mọi nơi. Mã hiển thị thì hoàn chỉnh; thứ thiếu
 * là một ô để Công ty nhập. Quy tắc 15 ở dạng React: một tham số bày ra mà không ai đọc.
 *
 * <p>Vế video nay đọc `site.home.video-id` / `site.home.video-title` (`V202608281038`). Vế `photos`
 * còn chờ endpoint công khai cho thư viện ảnh công trình (nợ T11.30) — mở một thư viện ảnh nội bộ
 * ra công khai là quyết định về phạm vi công bố, không phải một dòng mã.
 *
 * ⛔ Không có video mặc định. Bản trước từng nhúng một video kèm tiêu đề "Phóng sự … Sông Nhuệ"
 * hoàn toàn bịa và nó đã lên staging (§10.54).
 */
export function HomeMediaGallery({ videoId, videoTitle, photos = [] }: HomeMediaGalleryProps) {
  return (
    <section className="mt-10 sm:mt-14">
      <div className="flex items-center justify-between border-b-2 border-brand-primary pb-2.5">
        <div className="flex items-center gap-2">
          <span className="h-5 w-1.5 rounded-full bg-brand-primary"></span>
          <h2 className="text-base font-bold uppercase tracking-tight text-surface-textBase sm:text-lg">
            Truyền thông & Hình ảnh Hoạt động
          </h2>
        </div>
      </div>

      <div className="mt-5 grid grid-cols-1 gap-6 lg:grid-cols-12 lg:gap-8">
        {/* CỘT TRÁI (7 CỘT): VIDEO PHÓNG SỰ */}
        <div className="flex flex-col lg:col-span-7">
          {videoId ? (
            <div className="group relative overflow-hidden rounded-xl border border-surface-border bg-black shadow-sm">
              <div className="aspect-[16/9] w-full">
                <iframe
                  src={`https://www.youtube-nocookie.com/embed/${videoId}?rel=0`}
                  title={videoTitle}
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                  allowFullScreen
                  loading="lazy"
                  className="h-full w-full border-0"
                />
              </div>
            </div>
          ) : (
            <EmptyBlock>
              Chưa có video phóng sự. Dán mã video vào ô &ldquo;Mã video phóng sự trang chủ&rdquo; ở
              màn hình Cấu hình hệ thống.
            </EmptyBlock>
          )}
          {videoId && videoTitle ? (
            <p className="mt-2.5 text-xs font-semibold text-surface-textBase sm:text-sm">
              {videoTitle}
            </p>
          ) : null}
        </div>

        {/* CỘT PHẢI (5 CỘT): THƯ VIỆN HÌNH ẢNH CÔNG TRÌNH */}
        <div className="lg:col-span-5">
          {photos.length === 0 ? (
            <EmptyBlock>
              Thư viện ảnh công trình chưa mở ra cổng công khai — ảnh hiện trạng đang nằm trong hồ
              sơ từng công trình ở trang quản trị.
            </EmptyBlock>
          ) : (
            <div className="grid grid-cols-2 gap-3.5">
              {photos.slice(0, 4).map((p) => (
                <div
                  key={p.id}
                  className="group relative flex flex-col overflow-hidden rounded-lg border border-surface-border bg-white shadow-2xs"
                >
                  <div className="aspect-[4/3] w-full overflow-hidden bg-surface-bgLayout">
                    <img
                      src={p.imageUrl}
                      alt={p.title}
                      loading="lazy"
                      className="h-full w-full object-cover transition-transform duration-500 ease-smooth group-hover:scale-110"
                    />
                  </div>
                  <div className="p-2.5">
                    <h3 className="line-clamp-1 text-xs font-bold text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary">
                      {p.title}
                    </h3>
                    <span className="mt-0.5 block text-[10px] text-surface-textSecondary">
                      📍 {p.location}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
