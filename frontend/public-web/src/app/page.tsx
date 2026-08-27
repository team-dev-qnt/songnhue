import { AffiliatedUnitsLinks } from '@/components/home/AffiliatedUnitsLinks';
import { CategoryServicesGrid } from '@/components/home/CategoryServicesGrid';
import { HomeBannerSlider } from '@/components/home/HomeBannerSlider';
import { HomeHotNews } from '@/components/home/HomeHotNews';
import { HomeLatestNewsFeed } from '@/components/home/HomeLatestNewsFeed';
import { HomeMediaGallery } from '@/components/home/HomeMediaGallery';
import { OperationsBlock } from '@/components/home/OperationsBlock';
import { PublishedDocumentsSection } from '@/components/home/PublishedDocumentsSection';
import { WaterLevelBlock } from '@/components/home/WaterLevelBlock';
import {
  getArticles,
  getBanners,
  getMenu,
  getServerTime,
  getSiteConfig,
  getSubsidiaries,
} from '@/lib/api';
import { buildMenuTree } from '@/lib/routes';
import { docBool, docSo } from '@/lib/settings';

/**
 * Trang chủ Cổng TTĐT Thủy lợi Sông Nhuệ.
 *
 * <h2>Thứ tự khối bám đúng §3 của "YÊU CẦU CHỈNH SỬA WEBSITE" 27/08/2026</h2>
 *
 * <pre>
 *   Slider ảnh hoạt động  (CR-10)   ─┐ cột trái 8/12
 *   3 tin Hot             (CR-11)   ─┘
 *   Tin tức – Sự kiện     (CR-12)   ── cột phải 4/12
 *   Mực nước, lượng mưa   (CR-13/14)
 *   Vận hành công trình   (CR-15)      ← khối MỚI
 *   Công bố thông tin     (CR-16/17)   ← thay khối "Chỉ đạo điều hành"
 *   Chuyên mục &amp; lĩnh vực (CR-18)
 *   Truyền thông &amp; hình ảnh (CR-20)
 *   Liên kết đơn vị       (CR-19/21)
 * </pre>
 *
 * <h2>⛔ `site.home.blocks` đã thôi được dùng làm công tắc bật/tắt khối</h2>
 *
 * Khoá ấy liệt kê `["SLIDER","FEATURED","NEWS","NOTICE","THUY_VAN"]` — một từ vựng có từ trước
 * cây nội dung §3 và nay không còn ánh xạ vào khối nào: `FEATURED` (bài đinh) đã bị CR-10 thay
 * bằng slider, `NOTICE` đã bị CR-01 bỏ khỏi cây nội dung. Giữ nó là giữ một công tắc mà nhãn
 * nói một đằng còn tác dụng một nẻo — nguy hiểm hơn không có công tắc nào (luật 15).
 *
 * Bố cục trang chủ nay <b>là</b> cây nội dung Công ty đã duyệt; muốn bớt một khối thì bỏ mục
 * tương ứng khỏi menu, và card ở khối Chuyên mục biến mất theo — cùng một nguồn (§2).
 */
export const revalidate = 300;

/** Bài lấy về cho cột trái + cột phải. 3 tin Hot + tối đa 20 bài của khối Tin tức – Sự kiện. */
const SO_BAI_TIN_TUC = 24;

export default async function HomePage() {
  const [config, banners, latest, headerMenu, portalLinks, subsidiaries, serverTime] =
    await Promise.all([
      getSiteConfig(),
      getBanners(),
      getArticles({ size: SO_BAI_TIN_TUC }),
      getMenu('HEADER'),
      getMenu('LIEN_KET'),
      getSubsidiaries(),
      getServerTime(),
    ]);

  const hotline = config?.['company.hotline'] ?? '';
  const allArticles = latest?.content ?? [];

  // ⚠ Mọi tham số dưới đây đọc từ `settings`, không có số nào viết trong tệp này — §2 của tài
  //   liệu: "chu kỳ refresh, số bài hiển thị, số ảnh slider, thời gian chuyển ảnh phải cấu
  //   hình được". Mặc định thứ hai ở đây là lưới an toàn khi CSDL chưa có khoá, không phải
  //   nơi chốt giá trị.
  const soAnhSlider = docSo(config?.['site.slider.max-items'], 20);
  const nhipSlider = docSo(config?.['site.slider.interval-seconds'], 5);
  const soBaiTinTuc = docSo(config?.['site.home.news-count'], 5);
  const nhipLamMoi = docSo(config?.['site.home.realtime.refresh-seconds'], 300);
  const soVanBan = docSo(config?.['site.home.documents-count'], 6);
  const danhMucVanBan = config?.['site.home.documents-category'] ?? 'cong-bo-thong-tin';

  // Khối Công bố thông tin đọc riêng một nhánh danh mục, nên phải là lượt gọi riêng. Chờ tới
  // đây mới gọi vì slug của nó đến từ `config` ở lượt trên.
  const documents = await getArticles({ category: danhMucVanBan, size: soVanBan });

  return (
    <div className="mx-auto max-w-[1240px] px-4 py-6 sm:px-6 sm:py-8 animate-fade-in">
      {/* ───── 1. Slider ảnh hoạt động + 3 tin Hot | Tin tức – Sự kiện ───── */}
      <section aria-label="Ảnh hoạt động và tin tức nổi bật">
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12 lg:gap-8">
          <div className="flex flex-col gap-5 lg:col-span-8">
            <HomeBannerSlider
              banners={(banners ?? []).slice(0, soAnhSlider)}
              intervalSeconds={nhipSlider}
              autoplay={docBool(config?.['site.slider.autoplay'], true)}
              showArrows={docBool(config?.['site.slider.show-arrows'], true)}
              showDots={docBool(config?.['site.slider.show-dots'], true)}
            />
            <HomeHotNews articles={allArticles} />
          </div>
          <div className="lg:col-span-4">
            <HomeLatestNewsFeed latestArticles={allArticles} soBai={soBaiTinTuc} />
          </div>
        </div>
      </section>

      {/* ───── 2. Mực nước, lượng mưa · 3. Vận hành công trình ───── */}
      <div className="mt-8 grid grid-cols-1 gap-6 lg:grid-cols-2 sm:mt-10">
        <WaterLevelBlock hotline={hotline} refreshSeconds={nhipLamMoi} updatedAt={serverTime} />
        <OperationsBlock refreshSeconds={nhipLamMoi} updatedAt={serverTime} />
      </div>

      {/* ───── 4. Công bố thông tin ───── */}
      <PublishedDocumentsSection
        documents={documents?.content ?? []}
        categorySlug={danhMucVanBan}
      />

      {/* ───── 5. Chuyên mục & lĩnh vực hoạt động ───── */}
      <CategoryServicesGrid menuTree={buildMenuTree(headerMenu ?? [])} />

      {/* ───── 6. Truyền thông & hình ảnh hoạt động ───── */}
      {/* ⚠ Ba props của khối này TRƯỚC ĐÂY không nơi gọi nào truyền — `<HomeMediaGallery />`
          trần, nên `videoId` luôn `undefined` và khối rỗng vĩnh viễn ở mọi môi trường (quy tắc
          15 ở dạng React). Hai khoá `site.home.video-*` dựng ở `V202608281038`. Vế `photos` vẫn
          chưa có nguồn: nó cần endpoint công khai cho thư viện ảnh công trình (nợ T11.30). */}
      <HomeMediaGallery
        videoId={config?.['site.home.video-id'] || undefined}
        videoTitle={config?.['site.home.video-title'] || undefined}
      />

      {/* ───── 7. Đơn vị trực thuộc & liên kết ───── */}
      <AffiliatedUnitsLinks subsidiaries={subsidiaries ?? []} portalLinks={portalLinks ?? []} />
    </div>
  );
}
