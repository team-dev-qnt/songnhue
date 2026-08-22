import { AffiliatedUnitsLinks } from '@/components/home/AffiliatedUnitsLinks';
import { CategoryServicesGrid } from '@/components/home/CategoryServicesGrid';
import { DirectiveDocumentsSection } from '@/components/home/DirectiveDocumentsSection';
import { HomeHeroFeatured } from '@/components/home/HomeHeroFeatured';
import { HomeLatestNewsFeed } from '@/components/home/HomeLatestNewsFeed';
import { HomeMediaGallery } from '@/components/home/HomeMediaGallery';
import { HydrologyQuickWidget } from '@/components/home/HydrologyQuickWidget';
import { getArticles, getBanners, getCategories, getSiteConfig } from '@/lib/api';

/**
 * Trang chủ Cổng Thông tin Điện tử Thủy lợi Sông Nhuệ — T16.3.
 *
 * Cấu trúc Cổng thông tin Đa tầng (Multi-tier Portal) chuẩn Quốc gia:
 * 1. Khối Tiêu điểm & Dòng thời sự nóng (Bố cục 8 : 4)
 * 2. Dải Giám sát Thủy văn & Cảnh báo Thiên tai PCTT (Đặc thù nghiệp vụ)
 * 3. Khối Chỉ đạo Điều hành & Văn bản Quy phạm
 * 4. Lưới Chuyên mục Dịch vụ Công ích (4 Cột)
 * 5. Truyền thông Đa phương tiện & Thư viện Ảnh công trình
 * 6. Danh bạ Đơn vị Trực thuộc & Mạng lưới Liên kết Cơ quan Quản lý
 */

/**
 * ⚠ Số viết thẳng, KHÔNG import hằng số: Next đọc `export const revalidate` bằng phân tích
 * tĩnh và từ chối build nếu giá trị không phải literal. `REVALIDATE_SECONDS` ở `lib/api.ts`
 * phải bằng đúng con số này.
 */
export const revalidate = 300;

const SO_BAI_TRANG_CHU = 12;

export default async function HomePage() {
  const [config, banners, latest, categories] = await Promise.all([
    getSiteConfig(),
    getBanners(),
    getArticles({ size: SO_BAI_TRANG_CHU }),
    getCategories(),
  ]);

  const blocks = docKhoi(config?.['site.home.blocks']);
  const hotline = config?.['company.hotline'];
  const allArticles = latest?.content ?? [];
  const noticeArticles = allArticles.filter((a) =>
    a.title.toLowerCase().includes('thông báo')
  );
  const primaryBanner = banners && banners.length > 0 ? banners[0] : null;

  return (
    <div className="mx-auto max-w-[1240px] px-4 py-6 sm:px-6 sm:py-8 animate-fade-in">
      {/* ───── PHÂN VÙNG 1: TIÊU ĐIỂM & DÒNG THỜI SỰ (Hero Grid 8 : 4) ───── */}
      {(blocks.includes('SLIDER') || blocks.includes('FEATURED') || blocks.includes('NEWS')) && (
        <section aria-label="Tin tức tiêu điểm & Dòng thời sự">
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-12 lg:gap-8">
            <div className="lg:col-span-8">
              <HomeHeroFeatured banner={primaryBanner} articles={allArticles} />
            </div>
            <div className="lg:col-span-4">
              <HomeLatestNewsFeed latestArticles={allArticles} noticeArticles={noticeArticles} />
            </div>
          </div>
        </section>
      )}

      {/* ───── PHÂN VÙNG 2: GIÁM SÁT THỦY VĂN & MỰC NƯỚC (Hydrology Bar) ───── */}
      <div className="mt-8 sm:mt-10">
        <HydrologyQuickWidget hotline={hotline} />
      </div>

      {/* ───── PHÂN VÙNG 3: CHỈ ĐẠO ĐIỀU HÀNH & VĂN BẢN QUY PHẠM ───── */}
      <DirectiveDocumentsSection directiveArticles={allArticles.slice(1, 4)} />

      {/* ───── PHÂN VÙNG 4: LƯỚI CHUYÊN MỤC DỊCH VỤ CÔNG ÍCH ───── */}
      <CategoryServicesGrid categories={categories ?? []} />

      {/* ───── PHÂN VÙNG 5: TRUYỀN THÔNG ĐA PHƯƠNG TIỆN & THƯ VIỆN ẢNH ───── */}
      <HomeMediaGallery />

      {/* ───── PHÂN VÙNG 6: ĐƠN VỊ TRỰC THUỘC & MẠNG LƯỚI LIÊN KẾT ───── */}
      <AffiliatedUnitsLinks />
    </div>
  );
}

/** Mảng JSON từ `settings`; hỏng hoặc thiếu thì rơi về bộ khối mặc định. */
function docKhoi(raw: string | undefined): string[] {
  if (!raw) {
    return ['SLIDER', 'FEATURED', 'NEWS', 'NOTICE', 'THUY_VAN'];
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === 'string') : [];
  } catch {
    console.error('[cổng] site.home.blocks không phải JSON hợp lệ');
    return ['SLIDER', 'FEATURED', 'NEWS', 'NOTICE', 'THUY_VAN'];
  }
}
