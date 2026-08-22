interface PhotoItem {
  id: string;
  title: string;
  location: string;
  imageUrl: string;
}

const DEFAULT_PHOTOS: PhotoItem[] = [
  {
    id: 'p1',
    title: 'Cụm công trình đầu mối Cống Liên Mạc',
    location: 'Bắc Từ Liêm, Hà Nội',
    imageUrl: 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=600&q=80',
  },
  {
    id: 'p2',
    title: 'Trạm bơm tiêu úng Yên Nghĩa',
    location: 'Hà Đông, Hà Nội',
    imageUrl: 'https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80',
  },
  {
    id: 'p3',
    title: 'Tuyến kênh chính dẫn dòng Sông Nhuệ',
    location: 'Thanh Oai, Hà Nội',
    imageUrl: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=600&q=80',
  },
  {
    id: 'p4',
    title: 'Công tác nạo vét và khơi thông dòng chảy',
    location: 'Ứng Hòa, Hà Nội',
    imageUrl: 'https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=600&q=80',
  },
];

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
 */
export function HomeMediaGallery({
  videoId = 'dQw4w9WgXcQ', // Hoặc ID video phóng sự chính thức của Sông Nhuệ
  videoTitle = 'Phóng sự: Nỗ lực đảm bảo nguồn nước phục vụ sản xuất và phòng chống thiên tai lưu vực Sông Nhuệ',
  photos = DEFAULT_PHOTOS,
}: HomeMediaGalleryProps) {
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
          <p className="mt-2.5 text-xs font-semibold text-surface-textBase sm:text-sm">
            {videoTitle}
          </p>
        </div>

        {/* CỘT PHẢI (5 CỘT): THƯ VIỆN HÌNH ẢNH CÔNG TRÌNH */}
        <div className="grid grid-cols-2 gap-3.5 lg:col-span-5">
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
      </div>
    </section>
  );
}
