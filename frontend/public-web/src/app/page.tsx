import Link from 'next/link';

import { ArticleCard } from '@/components/ArticleCard';
import { getArticles, getBanners, getCategories, getSiteConfig } from '@/lib/api';
import { fileUrl, ROUTES } from '@/lib/routes';

/**
 * Trang chủ — T16.3.
 *
 * <h3>Khối hiển thị lấy từ cấu hình, không cứng trong mã</h3>
 *
 * `site.home.blocks` là một mảng JSON và **thứ tự phần tử là thứ tự khối** (T15.4). Khối lạ
 * bị bỏ qua thay vì làm hỏng trang: Công ty đặt một mã khối chưa được dựng thì trang vẫn
 * chạy, chỉ thiếu khối đó.
 *
 * ⛔ Khối `THUY_VAN` cần MOD-03 (Phase 2) — hiện chưa dựng, nên nó rơi vào nhánh "bỏ qua".
 * Cố ý không hiện một khung rỗng ghi "đang cập nhật": trang chủ của cơ quan nhà nước có một
 * ô trống thường trực trông như hệ thống hỏng.
 */
/**
 * ⚠ Số viết thẳng, KHÔNG import hằng số: Next đọc `export const revalidate` bằng phân tích
 * tĩnh và từ chối build nếu giá trị không phải literal ("Invalid segment configuration
 * export"). `REVALIDATE_SECONDS` ở `lib/api.ts` phải bằng đúng con số này —
 * `revalidate-config.test.ts` canh việc đó.
 */
export const revalidate = 300;

const SO_BAI_TRANG_CHU = 6;

export default async function HomePage() {
  const [config, banners, latest, categories] = await Promise.all([
    getSiteConfig(),
    getBanners(),
    getArticles({ size: SO_BAI_TRANG_CHU }),
    getCategories(),
  ]);

  const blocks = docKhoi(config?.['site.home.blocks']);

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      {blocks.includes('SLIDER') && banners && banners.length > 0 ? (
        <section aria-label="Ảnh nổi bật" className="mb-10">
          {/*
            Chỉ hiện ảnh đầu tiên. Trình chiếu tự chạy cần JavaScript phía trình duyệt, mà
            tham số của nó (thời gian dừng, hiệu ứng) đã có sẵn ở `site.slider.*` — dựng
            phần tương tác là việc của WS-20 khi có màn hình quản trị để đặt tham số. Hiện
            một ảnh tĩnh còn hơn một trình chiếu chưa ai cấu hình được.
          */}
          <BannerHero banner={banners[0]} />
        </section>
      ) : null}

      {blocks.includes('FEATURED') || blocks.includes('NEWS') ? (
        <section>
          <div className="flex items-baseline justify-between">
            <h2 className="text-lg font-semibold text-surface-textBase">Tin mới</h2>
            <Link href={ROUTES.search} className="text-sm text-brand-primary hover:underline">
              Xem tất cả
            </Link>
          </div>
          {latest && latest.content.length > 0 ? (
            <div className="mt-4 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {latest.content.map((article) => (
                <ArticleCard key={article.slug} article={article} />
              ))}
            </div>
          ) : (
            <p className="mt-4 text-surface-textSecondary">Chưa có bài viết nào được đăng.</p>
          )}
        </section>
      ) : null}

      {blocks.includes('NOTICE') && categories && categories.length > 0 ? (
        <section className="mt-10">
          <h2 className="text-lg font-semibold text-surface-textBase">Chuyên mục</h2>
          <ul className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {categories
              .filter((c) => c.depth === 0)
              .map((c) => (
                <li key={c.slug}>
                  <Link
                    href={ROUTES.category(c.slug)}
                    className="block rounded border border-surface-border p-4 transition-colors hover:border-brand-primary"
                  >
                    <span className="font-medium text-surface-textBase">{c.name}</span>
                    {c.description ? (
                      <span className="mt-1 block text-sm text-surface-textSecondary">
                        {c.description}
                      </span>
                    ) : null}
                  </Link>
                </li>
              ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}

function BannerHero({
  banner,
}: {
  banner: { title: string; description: string | null; imageId: string; linkUrl: string | null };
}) {
  const image = fileUrl(banner.imageId);
  const content = (
    <div className="relative overflow-hidden rounded">
      {image ? (
        <img src={image} alt={banner.title} className="h-64 w-full object-cover sm:h-80" />
      ) : null}
      <div className="absolute inset-x-0 bottom-0 bg-black/50 p-4 text-white">
        <p className="text-lg font-semibold">{banner.title}</p>
        {banner.description ? <p className="mt-1 text-sm">{banner.description}</p> : null}
      </div>
    </div>
  );

  return banner.linkUrl ? <Link href={banner.linkUrl}>{content}</Link> : content;
}

/** Mảng JSON từ `settings`; hỏng hoặc thiếu thì rơi về bộ khối mặc định. */
function docKhoi(raw: string | undefined): string[] {
  if (!raw) {
    return ['SLIDER', 'FEATURED', 'NEWS', 'NOTICE'];
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === 'string') : [];
  } catch {
    // Giá trị hỏng không được làm trắng trang chủ. `SettingValidator` đã chặn ở phía ghi,
    // nên tới được đây nghĩa là có ai đó sửa thẳng CSDL — vẫn phải sống sót.
    console.error('[cổng] site.home.blocks không phải JSON hợp lệ');
    return ['SLIDER', 'FEATURED', 'NEWS', 'NOTICE'];
  }
}
