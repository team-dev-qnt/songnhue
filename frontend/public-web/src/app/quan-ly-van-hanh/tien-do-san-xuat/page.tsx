import type { Metadata } from 'next';
import Link from 'next/link';

import { ArticleList } from '@/components/ArticleList';
import { EmptyBlock } from '@/components/home/EmptyBlock';
import { PageShell } from '@/components/PageShell';
import { SectionNav } from '@/components/SectionNav';
import type { CategoryNode } from '@/lib/api';
import { getArticles, getCategories, getSiteConfig } from '@/lib/api';
import { ROUTES } from '@/lib/routes';

export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Tiến độ sản xuất - Thủy lợi Sông Nhuệ',
  description: 'Tiến độ sản xuất theo từng năm và từng vụ: Vụ Xuân, Vụ Mùa, Vụ Đông.',
  alternates: { canonical: ROUTES.quanLyVanHanh.tienDoSanXuat },
};

interface PageProps {
  searchParams: Promise<{ nam?: string; vu?: string }>;
}

/**
 * Quản lý, vận hành &gt; **Tiến độ sản xuất** — CR-30, §5.5.
 *
 * <h2>Vì sao dựng bằng CMS chứ không bằng một entity mới</h2>
 *
 * §5.5 mô tả đúng một luồng: *chọn Năm → chọn Vụ → hiển thị nội dung tiến độ tương ứng*. Nó
 * <b>không</b> nói tiến độ đo bằng chỉ tiêu gì, đơn vị gì, ai nhập, tần suất nào — tức là chưa
 * đủ để thiết kế một bảng số liệu. Dựng một entity {@code production_progress} lúc này là đoán
 * hộ Công ty một mô hình nghiệp vụ, rồi phải di chuyển dữ liệu khi đoán sai.
 *
 * <p>Cây danh mục thì đã có sẵn ba cấp và đúng hình dạng cần: <b>gốc → Năm → Vụ</b>. Mỗi kỳ là
 * một bài viết, nên nó dùng lại nguyên bộ quy trình duyệt, đính kèm và nhật ký kiểm toán của
 * CMS mà không thêm một dòng lược đồ nào. Khi Công ty chốt bộ chỉ tiêu thì việc nâng lên thành
 * số liệu tổng hợp là một quyết định riêng, có căn cứ.
 *
 * <h2>Bộ chọn không cần JavaScript</h2>
 *
 * Năm và Vụ đi qua tham số URL, nên trang là Server Component thuần: chia sẻ được đường dẫn
 * của một kỳ cụ thể, và bộ chọn vẫn chạy khi bundle chưa tải xong.
 */
export default async function TienDoSanXuatPage({ searchParams }: PageProps) {
  const { nam, vu } = await searchParams;
  const [config, categories] = await Promise.all([getSiteConfig(), getCategories()]);

  const goc = config?.['site.page.production-progress-category'] ?? 'tien-do-san-xuat';
  const tatCa = categories ?? [];
  const nut = tatCa.find((c) => c.slug === goc);

  const cacNam = nut ? conTrucTiep(tatCa, nut) : [];
  const namChon = cacNam.find((c) => c.slug === nam) ?? cacNam[0];
  const cacVu = namChon ? conTrucTiep(tatCa, namChon) : [];
  const vuChon = cacVu.find((c) => c.slug === vu) ?? cacVu[0];

  const articles = vuChon ? await getArticles({ category: vuChon.slug, size: 20 }) : null;

  return (
    <PageShell
      title="Tiến độ sản xuất"
      description="Chọn năm và vụ để xem tiến độ sản xuất tương ứng."
      breadcrumb={[{ label: 'Quản lý, vận hành' }, { label: 'Tiến độ sản xuất' }]}
    >
      {cacNam.length === 0 ? (
        <EmptyBlock>
          Chưa có kỳ sản xuất nào được đăng. Mỗi năm là một danh mục con của &ldquo;Tiến độ sản
          xuất&rdquo;, và mỗi vụ (Vụ Xuân / Vụ Mùa / Vụ Đông) là một danh mục con của năm đó — tạo ở
          màn hình Danh mục của trang quản trị.
        </EmptyBlock>
      ) : (
        <>
          <div className="flex flex-col gap-4 rounded-xl border border-surface-border bg-surface-bgLayout/60 p-4">
            <BoChon
              nhan="Năm"
              muc={cacNam}
              dangChon={namChon?.slug}
              // Đổi năm thì bỏ luôn vụ đang chọn: vụ của năm cũ không tồn tại ở năm mới, và
              // giữ lại tham số ấy là dựng một URL trỏ vào một kỳ không có thật.
              dungLink={(c) => `${ROUTES.quanLyVanHanh.tienDoSanXuat}?nam=${c.slug}`}
            />
            {cacVu.length > 0 ? (
              <BoChon
                nhan="Vụ"
                muc={cacVu}
                dangChon={vuChon?.slug}
                dungLink={(c) =>
                  `${ROUTES.quanLyVanHanh.tienDoSanXuat}?nam=${namChon?.slug ?? ''}&vu=${c.slug}`
                }
              />
            ) : null}
          </div>

          <div className="mt-6">
            {cacVu.length === 0 ? (
              <EmptyBlock>
                Năm {namChon?.name} chưa có vụ nào. Thêm danh mục con Vụ Xuân / Vụ Mùa / Vụ Đông cho
                năm này ở trang quản trị.
              </EmptyBlock>
            ) : (
              <ArticleList
                page={articles}
                basePath={ROUTES.quanLyVanHanh.tienDoSanXuat}
                extraQuery={`nam=${namChon?.slug ?? ''}&vu=${vuChon?.slug ?? ''}`}
                emptyText={`Chưa có nội dung tiến độ cho ${vuChon?.name} ${namChon?.name}.`}
              />
            )}
          </div>
        </>
      )}
      <SectionNav duongDan={ROUTES.quanLyVanHanh.tienDoSanXuat} />
    </PageShell>
  );
}

/**
 * Con trực tiếp của một nút — đối chiếu `parentSlug`, KHÔNG suy từ vị trí trong mảng.
 *
 * <h3>⚠⚠ Bản trước suy theo vị trí, và nó đã sai trên site đang chạy</h3>
 *
 * Nó duyệt tiếp từ chỗ của cha và nhận mọi mục `depth = cha.depth + 1` cho tới mục `depth <=
 * cha.depth` kế tiếp. Phép suy ấy giả định danh sách phẳng còn **nguyên vẹn** — mà backend thì
 * lọc bỏ danh mục ẩn khỏi giữa danh sách.
 *
 * Đo được ngày 27/8 trên máy: CR-01 ẩn mục "Thông báo" (`path = '/2/'`), hai danh mục con của
 * nó vẫn hiện, và vì danh sách sắp theo `path` dạng **chuỗi** — `'/12/' < '/2/'` — chúng rơi
 * đúng sau `tien-do-san-xuat` (`path = '/12/'`). Trang này khi đó hiện *"Lịch vận hành cống &
 * trạm bơm"* và *"Thông báo xả nước đệm"* làm các **Năm**.
 *
 * ⛔ Không lỗi nào báo ra: hai danh mục đều có thật, đều đang hiện, trang vẫn dựng bình thường.
 * Chỉ nội dung là vô nghĩa. Backend nay cũng ẩn cả nhánh dưới một danh mục ẩn — hai bản vá độc
 * lập cho cùng một sự cố, vì mỗi bên đều tự nó đủ để sinh ra lỗi.
 */
function conTrucTiep(tatCa: CategoryNode[], cha: CategoryNode): CategoryNode[] {
  return tatCa.filter((muc) => muc.parentSlug === cha.slug);
}

function BoChon({
  nhan,
  muc,
  dangChon,
  dungLink,
}: {
  nhan: string;
  muc: CategoryNode[];
  dangChon: string | undefined;
  dungLink: (c: CategoryNode) => string;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-xs font-bold text-surface-textSecondary">{nhan}</span>
      {muc.map((c) => {
        const chon = c.slug === dangChon;
        return (
          <Link
            key={c.slug}
            href={dungLink(c)}
            aria-current={chon ? 'true' : undefined}
            className={`rounded-lg border px-3 py-1.5 text-xs font-semibold transition-colors ${
              chon
                ? 'border-brand-primary bg-brand-primary text-white'
                : 'border-surface-border bg-white text-surface-textBase hover:border-brand-primary hover:text-brand-primary'
            }`}
          >
            {c.name}
          </Link>
        );
      })}
    </div>
  );
}
