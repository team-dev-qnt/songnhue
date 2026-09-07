import type { Metadata } from 'next';
import Link from 'next/link';

import { ArticleList } from '@/components/ArticleList';
import { Breadcrumb } from '@/components/Breadcrumb';
import { DanhDauTuKhoa } from '@/components/DanhDauTuKhoa';
import { PortalSidebar } from '@/components/PortalSidebar';
import type { ConstructionSearchRow } from '@/lib/api';
import { getArticles, getCategories, getConstructionSearch, getSiteConfig } from '@/lib/api';
import { khoiVanHanhBat } from '@/lib/khoiVanHanh';
import { ROUTES } from '@/lib/routes';

/**
 * Tìm kiếm — CN-01.8 phần công khai.
 *
 * ⚠ `revalidate` vẫn có tác dụng vì mỗi bộ tham số truy vấn là một bản cache riêng: người
 * dùng gõ đi gõ lại cùng một từ khoá thì lượt sau lấy từ bộ đệm.
 *
 * <h2>⭐⭐ T36.10 — ba phần CN-01.8 còn thiếu, đo được ngày 07/09</h2>
 *
 * <ol>
 *   <li><b>Phạm vi Công trình</b>. Trang này trước chỉ gọi `getArticles`, tức ô tìm kiếm nói
 *       *"tìm kiếm"* mà chỉ tìm được một trong ba loại đối tượng CN-01.8 nêu.
 *   <li><b>Bộ lọc</b> — phạm vi · chuyên mục · khoảng ngày đăng.
 *   <li><b>Highlight từ khoá</b> — `DanhDauTuKhoa`.
 * </ol>
 *
 * <h2>⚠ "Văn bản" ĐÃ nằm trong phạm vi Bài viết — ⛔ không phải một phạm vi thứ ba</h2>
 *
 * Trên cổng này, *văn bản* là **bài viết thuộc một chuyên mục** (`site.home.documents-category`),
 * ⛔ không phải một entity riêng — chúng dùng chung bảng `articles` và mang thêm `docNumber` /
 * `docIssuedDate`. Dựng một tab "Văn bản" gọi một endpoint khác là dựng nguồn thứ hai cho cùng
 * một dữ liệu; ai muốn lọc riêng thì chọn chuyên mục ấy ở ô Chuyên mục.
 *
 * <h2>⛔ Bộ lọc đi bằng GET, ⛔ không bằng trạng thái phía client</h2>
 *
 * Cả trang là server component và mọi bộ lọc là một liên kết/`<form method="get">` thật. Nhờ
 * vậy: kết quả **chia sẻ được bằng URL**, nút Back hoạt động đúng, và bộ đệm ISR còn tác dụng.
 * Một bộ lọc chạy bằng `useState` sẽ vứt cả ba thứ ấy để đổi lấy việc ⛔ không phải tải lại trang.
 */
export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Tìm kiếm - Thủy lợi Sông Nhuệ',
  // Trang kết quả tìm kiếm không nên nằm trong chỉ mục: nó sinh vô số URL cùng nội dung.
  robots: { index: false, follow: true },
};

/** Hai phạm vi tìm kiếm — xem javadoc về "văn bản". */
type PhamVi = 'bai-viet' | 'cong-trinh';

const PHAM_VI: { ma: PhamVi; nhan: string }[] = [
  { ma: 'bai-viet', nhan: 'Bài viết & văn bản' },
  { ma: 'cong-trinh', nhan: 'Công trình' },
];

export default async function SearchPage({
  searchParams,
}: {
  searchParams: Promise<{
    q?: string;
    page?: string;
    'pham-vi'?: string;
    'chuyen-muc'?: string;
    'tu-ngay'?: string;
    'den-ngay'?: string;
  }>;
}) {
  const sp = await searchParams;
  const tuKhoa = (sp.q ?? '').trim();
  const trang = Number(sp.page ?? 0);
  // ⛔ Giá trị lạ rơi về `bai-viet`, ⛔ không ném: đây là chuỗi đến thẳng từ thanh địa chỉ.
  const phamVi: PhamVi = sp['pham-vi'] === 'cong-trinh' ? 'cong-trinh' : 'bai-viet';
  const chuyenMuc = (sp['chuyen-muc'] ?? '').trim();
  const tuNgay = (sp['tu-ngay'] ?? '').trim();
  const denNgay = (sp['den-ngay'] ?? '').trim();

  const [ketQuaBai, ketQuaCongTrinh, danhMuc, latestNews, config] = await Promise.all([
    // ⚠ Chỉ gọi endpoint của phạm vi ĐANG xem. Gọi cả hai để hiện số bên tab kia là hai lượt
    //   hỏi CSDL cho mỗi lượt gõ phím — và con số ấy ⛔ không ai dùng để quyết định gì.
    phamVi === 'bai-viet'
      ? getArticles({
          q: tuKhoa || undefined,
          category: chuyenMuc || undefined,
          tuNgay: tuNgay || undefined,
          denNgay: denNgay || undefined,
          page: trang,
        })
      : Promise.resolve(null),
    phamVi === 'cong-trinh'
      ? getConstructionSearch({ q: tuKhoa || undefined, page: trang })
      : Promise.resolve(null),
    getCategories(),
    getArticles({ size: 6 }),
    getSiteConfig(),
  ]);

  const tongKetQua =
    phamVi === 'bai-viet' ? ketQuaBai?.totalElements : ketQuaCongTrinh?.totalElements;

  /** Chuỗi truy vấn giữ lại mọi bộ lọc khi sang trang — ⛔ trừ `page`. */
  const giuLoc = (() => {
    const q = new URLSearchParams();
    if (tuKhoa) q.set('q', tuKhoa);
    if (phamVi !== 'bai-viet') q.set('pham-vi', phamVi);
    if (chuyenMuc) q.set('chuyen-muc', chuyenMuc);
    if (tuNgay) q.set('tu-ngay', tuNgay);
    if (denNgay) q.set('den-ngay', denNgay);
    return q.toString();
  })();

  const lienKetPhamVi = (ma: PhamVi) => {
    const q = new URLSearchParams();
    if (tuKhoa) q.set('q', tuKhoa);
    if (ma !== 'bai-viet') q.set('pham-vi', ma);
    // ⛔ Chuyên mục và khoảng ngày ⛔ KHÔNG mang sang phạm vi Công trình: hai bộ lọc ấy ⛔ không
    //    tồn tại ở đó, và một tham số câm trên URL đọc như một bộ lọc đang có hiệu lực.
    if (ma === 'bai-viet') {
      if (chuyenMuc) q.set('chuyen-muc', chuyenMuc);
      if (tuNgay) q.set('tu-ngay', tuNgay);
      if (denNgay) q.set('den-ngay', denNgay);
    }
    const s = q.toString();
    return s ? `${ROUTES.search}?${s}` : ROUTES.search;
  };

  return (
    <div className="mx-auto max-w-[1232px] px-4 py-4 sm:px-6 animate-fade-in">
      <Breadcrumb
        items={[{ label: 'Tìm kiếm' }, ...(tuKhoa ? [{ label: `Từ khóa: "${tuKhoa}"` }] : [])]}
      />

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-12 lg:gap-8">
        <main className="lg:col-span-8">
          <div className="mb-6 rounded-xl border border-surface-border bg-white p-5 shadow-xs sm:p-6">
            <div className="flex items-center gap-2.5 border-b border-surface-border pb-3">
              <span className="h-6 w-1.5 rounded-full bg-brand-primary"></span>
              <h1 className="text-xl font-bold tracking-tight text-surface-textBase sm:text-2xl">
                {tuKhoa ? `Kết quả tìm kiếm cho "${tuKhoa}"` : 'Tìm kiếm nội dung trên cổng'}
              </h1>
            </div>

            {/* ⛔ `<form method="get">` THẬT — xem javadoc trang. Trường ẩn `pham-vi` giữ tab
                đang chọn khi người dùng gõ lại từ khoá; ⛔ không có nó thì mỗi lượt tìm nhảy
                về Bài viết và người dùng ⛔ không hiểu vì sao. */}
            <form action={ROUTES.search} method="get" className="mt-5 flex flex-col gap-3">
              <input type="hidden" name="pham-vi" value={phamVi} />
              <div className="flex gap-2">
                <div className="relative flex-1">
                  <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-surface-textSecondary">
                    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                      />
                    </svg>
                  </span>
                  <label htmlFor="q" className="sr-only">
                    Từ khoá tìm kiếm
                  </label>
                  <input
                    id="q"
                    name="q"
                    type="search"
                    defaultValue={tuKhoa}
                    placeholder="Nhập từ khóa tìm kiếm (gõ không dấu vẫn tìm được)..."
                    className="w-full rounded-lg border border-surface-border py-2.5 pl-9 pr-4 text-xs text-surface-textBase transition-colors focus:border-brand-primary focus:outline-none focus:ring-1 focus:ring-brand-primary sm:text-sm"
                  />
                </div>
                <button
                  type="submit"
                  className="flex items-center gap-1.5 rounded-lg bg-brand-primary px-5 py-2.5 text-xs font-bold text-white shadow-xs transition-colors hover:bg-brand-primaryHover sm:text-sm"
                >
                  <span>Tìm</span>
                </button>
              </div>

              {/* ⚠ Ba bộ lọc này CHỈ áp cho phạm vi Bài viết. Hiện chúng ở phạm vi Công trình là
                  bày ra những ô ⛔ không quyết định gì — đúng thứ quy tắc 15 gọi là công tắc chết,
                  chỉ khác là nó chết ở giao diện. */}
              {phamVi === 'bai-viet' ? (
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                  <div className="flex flex-col gap-1.5">
                    <label
                      htmlFor="chuyen-muc"
                      className="text-[11px] font-semibold text-surface-textSecondary"
                    >
                      Chuyên mục
                    </label>
                    <select
                      id="chuyen-muc"
                      name="chuyen-muc"
                      defaultValue={chuyenMuc}
                      className="h-10 rounded-lg border border-surface-border px-2.5 text-xs text-surface-textBase focus:border-brand-primary focus:outline-none"
                    >
                      <option value="">Tất cả chuyên mục</option>
                      {(danhMuc ?? []).map((c) => (
                        <option key={c.slug} value={c.slug}>
                          {'— '.repeat(Math.max((c.depth ?? 1) - 1, 0))}
                          {c.name}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <label
                      htmlFor="tu-ngay"
                      className="text-[11px] font-semibold text-surface-textSecondary"
                    >
                      Đăng từ ngày
                    </label>
                    <input
                      id="tu-ngay"
                      name="tu-ngay"
                      type="date"
                      defaultValue={tuNgay}
                      className="h-10 rounded-lg border border-surface-border px-2.5 text-xs text-surface-textBase focus:border-brand-primary focus:outline-none"
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <label
                      htmlFor="den-ngay"
                      className="text-[11px] font-semibold text-surface-textSecondary"
                    >
                      Đến ngày
                    </label>
                    <input
                      id="den-ngay"
                      name="den-ngay"
                      type="date"
                      defaultValue={denNgay}
                      className="h-10 rounded-lg border border-surface-border px-2.5 text-xs text-surface-textBase focus:border-brand-primary focus:outline-none"
                    />
                  </div>
                </div>
              ) : null}
            </form>

            {/* ───── Chuyển phạm vi — liên kết thật, giữ từ khoá ───── */}
            <div
              role="tablist"
              aria-label="Phạm vi tìm kiếm"
              className="mt-4 flex flex-wrap gap-2 border-t border-surface-border pt-4"
            >
              {PHAM_VI.map((p) => (
                <Link
                  key={p.ma}
                  href={lienKetPhamVi(p.ma)}
                  role="tab"
                  aria-selected={p.ma === phamVi}
                  className={`rounded-lg px-3.5 py-2 text-xs font-semibold transition-colors ${
                    p.ma === phamVi
                      ? 'bg-brand-primary text-white'
                      : 'border border-surface-border text-surface-textSecondary hover:border-brand-primary hover:text-brand-primary'
                  }`}
                >
                  {p.nhan}
                </Link>
              ))}
            </div>

            {/* ⛔ `undefined` (chưa gọi / backend im lặng) KHÁC `0` (đã tìm, không có gì). Gộp hai
                cái vào một dòng "Tìm thấy 0 kết quả" là khẳng định một điều chưa đo được. */}
            {tongKetQua !== undefined ? (
              <div className="mt-3 flex items-center justify-between text-xs text-surface-textSecondary">
                <span>
                  Tìm thấy <strong className="font-bold text-brand-primary">{tongKetQua}</strong>{' '}
                  kết quả phù hợp
                </span>
              </div>
            ) : null}
          </div>

          <div className="mt-6">
            {phamVi === 'bai-viet' ? (
              <ArticleList
                page={ketQuaBai}
                basePath={ROUTES.search}
                extraQuery={giuLoc}
                tuKhoa={tuKhoa}
                emptyText="Không tìm thấy bài viết hoặc văn bản nào khớp với bộ lọc."
              />
            ) : (
              <BangCongTrinh
                ketQua={ketQuaCongTrinh}
                tuKhoa={tuKhoa}
                basePath={ROUTES.search}
                extraQuery={giuLoc}
              />
            )}
          </div>
        </main>

        <div className="lg:col-span-4">
          <PortalSidebar
            latestArticles={latestNews?.content ?? []}
            hotline={config?.['company.hotline']}
            docSystemUrl={config?.['site.external.doc-system-url']}
            hienKhoiVanHanh={khoiVanHanhBat(config)}
          />
        </div>
      </div>
    </div>
  );
}

/**
 * Kết quả phạm vi Công trình.
 *
 * ⛔ ⛔ Mỗi dòng ⛔ **không** dựng liên kết tới một trang chi tiết công trình: cổng ⛔ không có
 * trang ấy (danh mục là **một bảng**, `/quan-ly-van-hanh/danh-muc-cong-trinh`). Dựng một
 * `<Link>` trỏ vào 404 là đúng §10.54 — cổng quảng cáo những khu vực bấm vào là không có.
 */
function BangCongTrinh({
  ketQua,
  tuKhoa,
  basePath,
  extraQuery,
}: {
  ketQua: {
    content: ConstructionSearchRow[];
    totalPages: number;
    number: number;
  } | null;
  tuKhoa: string;
  basePath: string;
  extraQuery: string;
}) {
  if (!ketQua || ketQua.content.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-surface-border bg-white p-12 text-center shadow-2xs">
        <p className="text-sm font-medium text-surface-textSecondary">
          {tuKhoa
            ? 'Không tìm thấy công trình nào khớp với từ khoá.'
            : 'Nhập từ khoá để tìm công trình theo tên hoặc mã.'}
        </p>
      </div>
    );
  }

  const link = (target: number) =>
    `${basePath}?${extraQuery ? `${extraQuery}&` : ''}page=${target}`;

  return (
    <>
      <div className="overflow-x-auto rounded-xl border border-surface-border bg-white shadow-xs">
        <table className="w-full min-w-[720px] border-collapse text-sm">
          <thead>
            <tr className="border-b border-surface-border text-left text-[11px] font-bold tracking-wide text-surface-textSecondary">
              <th className="px-4 py-3 font-bold">Mã</th>
              <th className="px-4 py-3 font-bold">Tên công trình</th>
              <th className="px-4 py-3 font-bold">Địa điểm</th>
              <th className="px-4 py-3 font-bold">Đơn vị quản lý</th>
            </tr>
          </thead>
          <tbody>
            {ketQua.content.map((c) => (
              <tr key={c.code} className="border-b border-surface-border/60 last:border-b-0">
                <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-surface-textSecondary">
                  <DanhDauTuKhoa chu={c.code} tuKhoa={tuKhoa} />
                </td>
                <td className="px-4 py-3 font-semibold text-surface-textBase">
                  <DanhDauTuKhoa chu={c.name} tuKhoa={tuKhoa} />
                </td>
                {/* ⛔ Rỗng nói ra là rỗng — ⛔ không dựng một dấu gạch (quy tắc 16). */}
                <td className="px-4 py-3 text-surface-textBase">
                  {c.location ?? <span className="text-surface-textSecondary">Chưa nhập</span>}
                </td>
                <td className="px-4 py-3 text-surface-textBase">
                  {c.unitName ?? (
                    <span className="text-surface-textSecondary">Chưa phân đơn vị</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {ketQua.totalPages > 1 ? (
        <nav aria-label="Phân trang" className="mt-10 flex items-center justify-center gap-2">
          {ketQua.number > 0 ? (
            <Link
              href={link(ketQua.number - 1)}
              className="flex items-center gap-1 rounded-lg border border-surface-border bg-white px-3.5 py-2 text-xs font-semibold text-surface-textBase shadow-2xs transition-colors hover:border-brand-primary hover:text-brand-primary"
            >
              ← Trang trước
            </Link>
          ) : null}
          <span className="px-2 text-xs text-surface-textSecondary">
            Trang {ketQua.number + 1} / {ketQua.totalPages}
          </span>
          {ketQua.number + 1 < ketQua.totalPages ? (
            <Link
              href={link(ketQua.number + 1)}
              className="flex items-center gap-1 rounded-lg border border-surface-border bg-white px-3.5 py-2 text-xs font-semibold text-surface-textBase shadow-2xs transition-colors hover:border-brand-primary hover:text-brand-primary"
            >
              Trang sau →
            </Link>
          ) : null}
        </nav>
      ) : null}
    </>
  );
}
