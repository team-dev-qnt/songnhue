import { AffiliatedUnitsLinks } from '@/components/home/AffiliatedUnitsLinks';
import { CategoryServicesGrid } from '@/components/home/CategoryServicesGrid';
import { GroupLabel } from '@/components/home/GroupLabel';
import { HomeBannerSlider } from '@/components/home/HomeBannerSlider';
import { HomeCategoryNews } from '@/components/home/HomeCategoryNews';
import { HomeConstructionMap } from '@/components/home/HomeConstructionMap';
import { HomeContactBlock } from '@/components/home/HomeContactBlock';
import { HomeMediaGallery } from '@/components/home/HomeMediaGallery';
import { HomeNewsColumn } from '@/components/home/HomeNewsColumn';
import { OperationsBlock } from '@/components/home/OperationsBlock';
import { PublishedDocumentsSection } from '@/components/home/PublishedDocumentsSection';
import { WaterLevelBlock } from '@/components/home/WaterLevelBlock';
import {
  getArticles,
  getBanners,
  getConstructionCatalog,
  getPhotos,
  getMenu,
  getServerTime,
  getSiteConfig,
  getSubsidiaries,
} from '@/lib/api';
import { chonKhoiChuyenMuc, nhanNhanhTin } from '@/lib/homeCategories';
import { fileUrl, buildMenuTree } from '@/lib/routes';
import { docBool, docSo } from '@/lib/settings';

/**
 * Trang chủ Cổng TTĐT Thủy lợi Sông Nhuệ — bố cục dựng lại 29/08/2026.
 *
 * <h2>Năm nhóm, không còn mười một khối rời</h2>
 *
 * <pre>
 *   Nhóm 1  Tin tức &amp; sự kiện      slider 8/12 │ danh sách tin có ảnh 4/12
 *                                     + hàng chuyên mục con (3 cột bằng nhau)
 *   Nhóm 2  Điều hành &amp; số liệu    Mực nước (kín bề rộng) → Vận hành (kín bề rộng) → bản đồ
 *   Nhóm 3  Văn bản                 Công bố thông tin · biểu mẫu gửi ý kiến (kín bề rộng)
 *   Nhóm 4  Tổ chức &amp; đơn vị       Xí nghiệp trực thuộc · liên kết
 *   Nhóm 5  Truyền thông            Video · ảnh · chuyên mục &amp; lĩnh vực
 * </pre>
 *
 * <h2>⭐ Vì sao Mực nước và Vận hành KHÔNG nằm cạnh nhau</h2>
 *
 * Hai khối ấy là bảng: mực nước là biểu tổng hợp theo tuyến sông (CN-03.4, tám cột), vận hành là
 * tình hình từng cống (CN-02.11, sáu cột). Nhét một bảng tám cột vào nửa bề rộng thì hoặc chữ bé
 * lại hoặc cột bị cắt — cả hai đều làm hỏng đúng thứ khối ấy sinh ra để làm. Xếp chồng, mỗi khối
 * kín bề rộng khung.
 *
 * <h2>⭐ Hai cột cạnh nhau phải cao bằng nhau — bằng CẤU TRÚC</h2>
 *
 * Lưới CSS đã kéo giãn các ô con theo hàng ({@code items-stretch} là mặc định), nhưng cái giãn
 * là Ô, không phải cái THẺ bên trong nó. Nên thẻ phải nhận {@code h-full}, và bên trong lại phải
 * là {@code flex-col} với phần co giãn mang {@code flex-1 min-h-0}. Thiếu một mắt trong chuỗi
 * ấy là bên cao bên thấp — lỗi đo được ở bản duyệt ngày 29/08.
 *
 * <p>⛔ Đừng canh bằng một chiều cao chốt sẵn ({@code max-h-[510px]} kiểu cổng tham chiếu): con
 * số ấy đúng với đúng bộ nội dung hôm nay và sai ngay khi một tiêu đề dài thêm một dòng.
 *
 * <h2>⭐ 29/08 — ba thay đổi Công ty yêu cầu sau khi duyệt bản vẽ</h2>
 *
 * <ol>
 *   <li><b>Trụ sở &amp; đầu mối liên hệ rời khỏi trang chủ</b>, dồn hết về {@code /lien-he}. Ở
 *       đây chỉ còn biểu mẫu, chiếm trọn bề rộng — xem {@link HomeContactBlock}.
 *   <li><b>Cơ cấu tổ chức KHÔNG lên trang chủ.</b> Bản vẽ có một ô "Sơ đồ tổ chức Công ty" +
 *       "Lãnh đạo Công ty" ở Nhóm 4; cả hai đã là trang riêng dưới nhánh Giới thiệu
 *       ({@code /gioi-thieu/co-cau-to-chuc}, {@code /gioi-thieu/lanh-dao}) và đọc cùng
 *       {@code org_units}. Dựng thêm một bản rút gọn ở trang chủ là nơi thứ hai hiển thị cùng
 *       một cây — thứ sẽ lệch ngay lần đầu ai đó sửa một bên (luật 14).
 *   <li><b>Hàng chuyên mục con</b> dưới slider — xem {@link HomeCategoryNews}.
 * </ol>
 *
 * <h2>⛔ `site.home.blocks` đã thôi được dùng làm công tắc bật/tắt khối</h2>
 *
 * Bố cục trang chủ <b>là</b> cây nội dung Công ty đã duyệt; muốn bớt một khối thì bỏ mục tương
 * ứng khỏi menu, và card ở khối Chuyên mục biến mất theo — cùng một nguồn. Hàng chuyên mục mới
 * đi đúng luật ấy: nó dựng từ các mục con của nhánh menu {@code site.home.news-category}.
 */
export const revalidate = 300;

/** Bài lấy về cho khối tin tức. Dư ra so với số hiển thị để đổi `site.home.news-count` không cần deploy. */
const SO_BAI_TIN_TUC = 24;

export default async function HomePage() {
  const [
    config,
    banners,
    photos,
    latest,
    headerMenu,
    portalLinks,
    subsidiaries,
    catalog,
    serverTime,
  ] = await Promise.all([
    getSiteConfig(),
    getBanners(),
    getPhotos(),
    getArticles({ size: SO_BAI_TIN_TUC }),
    getMenu('HEADER'),
    getMenu('LIEN_KET'),
    getSubsidiaries(),
    getConstructionCatalog(),
    getServerTime(),
  ]);

  const hotline = config?.['company.hotline'] ?? '';
  const allArticles = latest?.content ?? [];
  const menuTree = buildMenuTree(headerMenu ?? []);

  // ⚠ Mọi tham số dưới đây đọc từ `settings`, không có số nào viết trong tệp này. Mặc định thứ hai
  //   là lưới an toàn khi CSDL chưa có khoá, không phải nơi chốt giá trị.
  const soAnhSlider = docSo(config?.['site.slider.max-items'], 20);
  const nhipSlider = docSo(config?.['site.slider.interval-seconds'], 5);
  const soBaiTinTuc = docSo(config?.['site.home.news-count'], 5);
  const nhipLamMoi = docSo(config?.['site.home.realtime.refresh-seconds'], 300);
  const soVanBan = docSo(config?.['site.home.documents-count'], 6);
  const danhMucVanBan = config?.['site.home.documents-category'] ?? 'cong-bo-thong-tin';
  const danhMucTin = config?.['site.home.news-category'] ?? '';
  const soBaiChuyenMuc = docSo(config?.['site.home.category-news-count'], 4);

  // Nhãn và danh sách chuyên mục con đều lấy từ MENU — không có chuỗi 'tin-tuc' hay
  // "Tin tức – Sự kiện" nào viết trong tệp này nữa.
  const tieuDeTin = nhanNhanhTin(menuTree, danhMucTin);
  const chuyenMuc = chonKhoiChuyenMuc(menuTree, danhMucTin);

  // Hai lượt gọi dưới đây phụ thuộc `config` ở lượt trên nên phải chờ tới đây; chúng chạy song
  // song với nhau. Danh mục lạ ⇒ backend trả rỗng, khối nói thẳng là chưa có bài — không nổ.
  const [documents, baiTheoChuyenMuc] = await Promise.all([
    getArticles({ category: danhMucVanBan, size: soVanBan }),
    Promise.all(
      chuyenMuc.map(async (cm) => ({
        ...cm,
        articles: (await getArticles({ category: cm.slug, size: soBaiChuyenMuc }))?.content ?? [],
      })),
    ),
  ]);

  return (
    <div className="mx-auto max-w-[1232px] px-4 py-6 sm:px-6 sm:py-8 animate-fade-in">
      {/* ═════════ NHÓM 1 · TIN TỨC & SỰ KIỆN ═════════ */}
      <section aria-label="Ảnh hoạt động và tin tức">
        <div className="grid grid-cols-1 items-stretch gap-6 lg:grid-cols-12 lg:gap-9">
          {/* Không có mục menu cho nhánh tin ⇒ không có cột tin ⇒ slider chiếm cả 12 cột.
              Đây là chính sách "bố cục trang chủ LÀ cây menu" ở dạng bố cục, không phải một
              nhánh dự phòng: bỏ mục menu là cố ý bỏ khối. */}
          <div className={tieuDeTin ? 'lg:col-span-8' : 'lg:col-span-12'}>
            <HomeBannerSlider
              banners={(banners ?? []).slice(0, soAnhSlider)}
              intervalSeconds={nhipSlider}
              autoplay={docBool(config?.['site.slider.autoplay'], true)}
              showArrows={docBool(config?.['site.slider.show-arrows'], true)}
              showDots={docBool(config?.['site.slider.show-dots'], true)}
            />
          </div>
          {tieuDeTin ? (
            <div className="lg:col-span-4">
              {/* Khối này THAY cả `HomeHotNews` lẫn `HomeLatestNewsFeed`: cùng một nguồn bài mà chia
                  hai chỗ, hai kiểu trình bày, người đọc phải quét hai lần. */}
              <HomeNewsColumn
                articles={allArticles}
                soBai={soBaiTinTuc}
                tieuDe={tieuDeTin}
                categorySlug={danhMucTin}
              />
            </div>
          ) : null}
        </div>
      </section>

      <HomeCategoryNews blocks={baiTheoChuyenMuc} />

      {/* ═════════ NHÓM 2 · ĐIỀU HÀNH & SỐ LIỆU ═════════ */}
      <GroupLabel>Điều hành &amp; số liệu công trình</GroupLabel>
      <div className="mt-5 space-y-6">
        <WaterLevelBlock hotline={hotline} refreshSeconds={nhipLamMoi} updatedAt={serverTime} />
        <OperationsBlock refreshSeconds={nhipLamMoi} updatedAt={serverTime} />
      </div>
      <HomeConstructionMap catalog={catalog ?? []} />

      {/* ═════════ NHÓM 3 · VĂN BẢN & TIẾP NHẬN Ý KIẾN ═════════ */}
      <GroupLabel>Văn bản &amp; tiếp nhận ý kiến</GroupLabel>
      <PublishedDocumentsSection
        documents={documents?.content ?? []}
        categorySlug={danhMucVanBan}
        docSystemUrl={config?.['site.external.doc-system-url'] ?? ''}
      />
      {/* ⛔ Không truyền `company.*` xuống nữa — địa chỉ, điện thoại, thư điện tử và giờ làm việc
          nay chỉ hiển thị ở `/lien-he` (yêu cầu Công ty 29/08). Khối này còn đúng biểu mẫu. */}
      <HomeContactBlock />

      {/* ═════════ NHÓM 4 · TỔ CHỨC & ĐƠN VỊ ═════════ */}
      <GroupLabel>Tổ chức &amp; đơn vị trực thuộc</GroupLabel>
      <AffiliatedUnitsLinks subsidiaries={subsidiaries ?? []} portalLinks={portalLinks ?? []} />

      {/* ═════════ NHÓM 5 · TRUYỀN THÔNG ═════════ */}
      <GroupLabel>Truyền thông &amp; chuyên mục</GroupLabel>
      {/* ⚠ Ba props của khối này TRƯỚC ĐÂY không nơi gọi nào truyền — `<HomeMediaGallery />` trần,
          nên `videoId` luôn `undefined` và khối rỗng vĩnh viễn ở mọi môi trường (quy tắc 15 ở dạng
          React). Khoá rỗng ⇒ mảng rỗng ⇒ khối nói thẳng là chưa có — không có bộ ảnh dự phòng. */}
      <HomeMediaGallery
        videoId={config?.['site.home.video-id'] || undefined}
        videoTitle={config?.['site.home.video-title'] || undefined}
        photos={(photos ?? []).map((a) => ({
          id: a.publicId,
          title: a.title,
          imageUrl: fileUrl(a.publicId) ?? '',
        }))}
      />
      <CategoryServicesGrid menuTree={menuTree} />
    </div>
  );
}
