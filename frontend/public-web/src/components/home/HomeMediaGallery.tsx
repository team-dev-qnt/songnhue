import { PortalImage } from '@/components/PortalImage';
import { EmptyBlock } from './EmptyBlock';
import { SectionTitle } from './SectionTitle';

interface PhotoItem {
  id: string;
  title: string;
  imageUrl: string;
}

interface HomeMediaGalleryProps {
  videoId?: string;
  videoTitle?: string;
  photos?: PhotoItem[];
}

/**
 * Nhóm 5 — **Video giới thiệu** và **Chuyên mục ảnh**, bố cục 29/08/2026.
 *
 * <h2>⚠⚠ Ba props này TỪNG không nơi gọi nào truyền</h2>
 *
 * Trang chủ gọi `<HomeMediaGallery />` trần cho tới 28/08/2026, nên `videoId` luôn `undefined` và
 * khối luôn hiện hai ô rỗng — ở dev, ở staging, ở mọi nơi. Mã hiển thị thì hoàn chỉnh; thứ thiếu
 * là một ô để Công ty nhập. Quy tắc 15 ở dạng React: một tham số bày ra mà không ai đọc.
 *
 * <p>Vế video nay đọc `site.home.video-id` / `site.home.video-title` (`V202608281038`).
 *
 * <h2>⭐ 29/08: hai mục có tiêu đề riêng, và KHÔNG ảnh nào hiện hai lần</h2>
 *
 * Bản vẽ tách thành hai khối: cột phải 5/12 cạnh video là danh sách ảnh nhỏ 103×68, rồi bên
 * dưới là "Chuyên mục ảnh" lưới ba cột. Nhưng bản vẽ vẽ <b>cùng ba tấm ảnh</b> ở cả hai chỗ —
 * đó là một lỗi của bản vẽ, không phải một ý đồ: cổng chỉ có MỘT nguồn ảnh
 * ({@code /public/photos}), nên hai khối lấy cùng mảng là người xem cuộn qua đúng bộ ảnh hai
 * lần và tưởng thư viện dài gấp đôi thực tế.
 *
 * <p>Nên mảng được <b>cắt</b>, không được nhân đôi: {@link SO_ANH_CANH_VIDEO} tấm đầu vào cột
 * cạnh video, phần còn lại vào lưới bên dưới. Hết ảnh sau lượt cắt thứ nhất ⇒ mục "Chuyên mục
 * ảnh" <b>không được dựng</b> — một ô rỗng nói "chưa có ảnh" trong khi ảnh đang hiện ngay phía
 * trên là một câu sai, và câu sai ấy còn khó lần ra hơn một ô trống.
 *
 * ⛔ Không có video mặc định. Bản trước từng nhúng một video kèm tiêu đề "Phóng sự … Sông Nhuệ"
 * hoàn toàn bịa và nó đã lên staging (§10.54).
 */

/**
 * Số ảnh đứng cạnh video. Bốn dòng 103×68 kèm khoảng cách vừa lấp chiều cao của khung video
 * 16:9 ở cột 7/12 — nhiều hơn thì cột phải dài hơn cột trái, đúng thứ yêu cầu 29/08 gọi là
 * *"bên cao bên thấp"*.
 */
const SO_ANH_CANH_VIDEO = 4;

/** Số ô của lưới "Chuyên mục ảnh" — hai hàng ba cột. */
const SO_ANH_LUOI = 6;

export function HomeMediaGallery({ videoId, videoTitle, photos = [] }: HomeMediaGalleryProps) {
  const anhCanhVideo = photos.slice(0, SO_ANH_CANH_VIDEO);
  const anhLuoi = photos.slice(SO_ANH_CANH_VIDEO, SO_ANH_CANH_VIDEO + SO_ANH_LUOI);

  return (
    <>
      <section className="mt-5">
        <SectionTitle>Video giới thiệu</SectionTitle>

        <div className="mt-5 grid grid-cols-1 items-stretch gap-6 lg:grid-cols-12 lg:gap-9">
          {/* CỘT TRÁI (7/12): VIDEO */}
          <div className="flex flex-col lg:col-span-7">
            {videoId ? (
              <div className="group relative overflow-hidden rounded-lg border border-surface-border bg-black shadow-xs">
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
              <div className="flex flex-1 items-center">
                <EmptyBlock>
                  Chưa có video giới thiệu. Dán mã video vào ô &ldquo;Mã video phóng sự trang
                  chủ&rdquo; ở màn hình Cấu hình hệ thống.
                </EmptyBlock>
              </div>
            )}
            {videoId && videoTitle ? (
              <p className="mt-3 text-[15px] font-semibold text-surface-textBase">{videoTitle}</p>
            ) : null}
          </div>

          {/* CỘT PHẢI (5/12): ẢNH MỚI NHẤT — dòng ảnh 103×68 + tiêu đề, đúng bản vẽ */}
          <div className="flex flex-col lg:col-span-5">
            {anhCanhVideo.length === 0 ? (
              <div className="flex flex-1 items-center">
                <EmptyBlock>
                  Chưa có ảnh trong thư viện. Chọn thư mục ảnh ở ô &ldquo;Thư mục ảnh của thư viện
                  trang chủ&rdquo; trong Cấu hình website, rồi tải ảnh vào thư mục ấy ở Thư viện
                  media.
                </EmptyBlock>
              </div>
            ) : (
              <ul className="flex flex-1 flex-col justify-between gap-1">
                {anhCanhVideo.map((p) => (
                  <li
                    key={p.id}
                    className="flex items-center gap-4 border-b border-surface-border/60 py-3 last:border-b-0"
                  >
                    <PortalImage
                      src={p.imageUrl || null}
                      alt={p.title}
                      ratio="aspect-[103/68]"
                      className="w-[103px] shrink-0 rounded-md"
                    />
                    {/* Tiêu đề rỗng ⇒ ô chữ bỏ trống, KHÔNG hiện tên tệp và không bịa một câu.
                        Ảnh Công ty gửi mang tên do máy sinh; BE trả rỗng (sửa ở #57). */}
                    {p.title ? (
                      <span className="line-clamp-3 text-[15px] leading-snug text-surface-textBase">
                        {p.title}
                      </span>
                    ) : (
                      <span className="text-xs italic text-surface-textSecondary">
                        Ảnh chưa có chú thích
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </section>

      {/* ⛔ Không dựng mục này khi lượt cắt thứ nhất đã lấy hết ảnh — xem ghi chú đầu tệp. */}
      {anhLuoi.length > 0 ? (
        <section className="mt-9">
          <SectionTitle>Chuyên mục ảnh</SectionTitle>
          <div className="mt-5 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {anhLuoi.map((p) => (
              <figure key={p.id} className="group">
                <PortalImage
                  src={p.imageUrl || null}
                  alt={p.title}
                  ratio="aspect-[386/220]"
                  className="rounded-lg"
                />
                {p.title ? (
                  <figcaption className="mt-3 line-clamp-2 text-[15px] font-semibold leading-snug text-surface-textBase transition-colors duration-200 group-hover:text-brand-primary">
                    {p.title}
                  </figcaption>
                ) : null}
              </figure>
            ))}
          </div>
        </section>
      ) : null}
    </>
  );
}
