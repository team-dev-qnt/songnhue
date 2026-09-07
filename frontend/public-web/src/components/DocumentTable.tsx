import Link from 'next/link';

import type { ArticleRow } from '@/lib/api';
import { formatDate, formatNgayThuan, ROUTES } from '@/lib/routes';

interface DocumentTableProps {
  documents: ArticleRow[];
  /** Chú thích dưới bảng khi rỗng — mỗi nơi gọi nói đúng lý do rỗng của mình. */
  khiRong: React.ReactNode;
}

/**
 * Bảng danh sách văn bản — **một** bảng, dùng ở cả trang chủ lẫn trang danh mục.
 *
 * <h2>Nguồn của thiết kế: `thuyloisongday.vn/van-ban` (tải và đo 01/09/2026)</h2>
 *
 * QuanTran chỉ đích danh trang ấy làm chuẩn. Số đo lấy từ
 * `uisteeringdocument/uidocumentlist/default.css` của họ:
 *
 * <pre>
 *   bảng        width:100% · border-collapse · mỗi &lt;tr&gt; border 1px #E2E7ED
 *   ô           padding 10px 15px · mọi th/td trừ cuối có border-right
 *   sọc         tr:nth-child(odd) background #F3F6FB
 *   cột 1       Số ký hiệu        width 200px  · chữ đậm vừa (500)
 *   cột 2       Trích yếu         co giãn      · chữ thường (400)
 *   cột 3       Nội dung chi tiết width 170px  · nút 142×40, viền 1px #719BC6, chữ #1259A0
 *   cột 4       Ngày ban hành     min-width 150px · căn giữa
 *   cột 5       Thời gian đăng tải min-width 150px · căn giữa
 *   ≤768px      cột 3·4·5 display:none; .date-mobile và a.mobile bật lên ⇒ bảng còn 2 cột
 * </pre>
 *
 * <h2>⛔⛔ Bề rộng cột là TỈ LỆ, không phải pixel của họ — đo được 01/09 rằng chép pixel thì hỏng</h2>
 *
 * Bảng của cổng tham chiếu rộng khoảng <b>1160px</b> (họ dựng nó kín bề rộng trang). Bảng này nằm
 * trong cột 8/12 của khung 1232px, tức khoảng <b>776px</b>. Chép nguyên bốn con số cố định
 * (200 + 170 + 150 + 150 = 670px) vào một khung hẹp hơn 384px thì cột "Trích yếu" — cột QUAN
 * TRỌNG NHẤT, nơi đặt tên văn bản — chỉ còn phần thừa. Đo trên trình duyệt thật: <b>102px</b>,
 * tức khoảng bốn ký tự mỗi dòng.
 *
 * <p>Nên bốn cột cố định viết bằng <b>phần trăm suy từ chính tỉ lệ của họ</b> (200/1160 ≈ 17%,
 * 170/1160 ≈ 15%, 150/1160 ≈ 13%): cùng một hình dạng, đúng ở mọi bề rộng khung thay vì đúng
 * ở đúng bề rộng trang của họ. Đây chính là chính sách *"lấy hình dạng của họ mà không lấy hằng
 * số của họ"* — và lượt viết đầu của tệp này đã vi phạm nó ở đúng chỗ dễ vi phạm nhất.
 *
 * <h2>⛔ Lấy HÌNH DẠNG của họ, KHÔNG lấy mã màu của họ</h2>
 *
 * `noHardcodedColors.test.ts` canh toàn bộ `public-web`, và `docs/ui-styles.md` bắt mọi màu đi qua
 * `design-tokens`. Nên bề rộng cột, padding, sọc chẵn lẻ và luật ẩn cột ở 768px được chép nguyên;
 * còn `#E2E7ED` → `surface-border`, `#F3F6FB` → `surface-bgLayout`, `#2A58A0`/`#1259A0`/`#719BC6`
 * → `brand-primary`. Cùng chính sách đã dùng với cổng tham chiếu `bocongan.gov.vn` ngày 01/09:
 * *lấy hình dạng của họ mà không lấy hằng số của họ*.
 *
 * <h2>⛔⛔ Ô rỗng thì ĐỂ TRỐNG</h2>
 *
 * `docNumber` và `docIssuedDate` là hai ô biên tập viên nhập tay (CR-07 — cổng không đồng bộ dữ
 * liệu từ hệ thống văn bản điều hành của Thành phố). Chưa ai nhập thì ô trống, **không** dấu gạch,
 * **không** "Đang cập nhật", và tuyệt đối không suy ngày ban hành từ `publishedAt`: một văn bản ký
 * năm 2015 đăng lại năm 2026 sẽ mang một ngày ban hành sai mà không ai nhìn ra (quy tắc 16).
 * Bản trang chủ 29/08 từng có bốn văn bản viết cứng kèm số hiệu và người ký, tất cả bịa (§10.54).
 *
 * <h2>⚠ Đường lui trên điện thoại là một PHẦN của thiết kế, không phải phần thêm</h2>
 *
 * Ẩn ba cột cuối ở màn hình hẹp mà không đưa ngày và nút xuống hai cột còn lại là **mất** ngày
 * ban hành và mất lối vào bài — trên đúng nhóm thiết bị chiếm phần lớn lượt truy cập một cổng
 * thông tin. Cổng tham chiếu dựng sẵn hai khối ẩn (`.date-mobile`, `a.mobile`) cho việc đó; ở đây
 * là hai khối `md:hidden`, và `bangVanBan.test.ts` khẳng định cả hai còn nguyên.
 */
export function DocumentTable({ documents, khiRong }: DocumentTableProps) {
  if (documents.length === 0) {
    return <>{khiRong}</>;
  }

  return (
    // Bảng cuộn NGANG trong lòng nó — không để trang cuộn ngang (bộ đo Playwright canh
    // `scrollWidth <= clientWidth` ở cả bốn bề rộng).
    <div className="overflow-x-auto rounded-lg border border-surface-border shadow-2xs">
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="border-b border-surface-border bg-surface-bgLayout">
            <th scope="col" className="w-[17%] px-[15px] py-[10px] text-sm font-bold">
              Số ký hiệu
            </th>
            <th
              scope="col"
              className="border-l border-surface-border px-[15px] py-[10px] text-sm font-bold"
            >
              Trích yếu
            </th>
            <th
              scope="col"
              className="hidden w-[15%] border-l border-surface-border px-[15px] py-[10px] text-center text-sm font-bold md:table-cell"
            >
              Nội dung chi tiết
            </th>
            <th
              scope="col"
              className="hidden w-[13%] border-l border-surface-border px-[15px] py-[10px] text-center text-sm font-bold md:table-cell"
            >
              Ngày ban hành
            </th>
            <th
              scope="col"
              className="hidden w-[13%] border-l border-surface-border px-[15px] py-[10px] text-center text-[13px] font-bold md:table-cell"
            >
              Thời gian đăng tải
            </th>
          </tr>
        </thead>
        <tbody>
          {documents.map((doc, i) => (
            <tr
              key={doc.slug}
              // Sọc chẵn lẻ theo đúng cổng tham chiếu — hàng lẻ (chỉ số chẵn) tô nền nhạt.
              className={`border-b border-surface-border last:border-b-0 ${
                i % 2 === 0 ? 'bg-surface-bgLayout/50' : 'bg-white'
              }`}
            >
              <td className="align-top px-[15px] py-[10px]">
                <Link
                  href={ROUTES.article(doc.slug)}
                  className="text-sm font-medium text-surface-textBase transition-colors hover:text-brand-primary"
                >
                  {/* Chưa ai nhập số ký hiệu ⇒ KHÔNG bịa. Dùng tiêu đề làm chữ của liên kết để ô
                      vẫn bấm được, và nói rõ bằng `sr-only` rằng đây không phải một số ký hiệu. */}
                  {doc.docNumber ?? <span className="sr-only">Xem văn bản: {doc.title}</span>}
                </Link>
                {/* ĐƯỜNG LUI MOBILE — ba cột bên phải bị ẩn ở màn hình hẹp. */}
                <div className="mt-1 space-y-0.5 text-[12px] text-surface-textSecondary md:hidden">
                  {doc.docIssuedDate ? (
                    <div>Ngày ban hành: {formatNgayThuan(doc.docIssuedDate)}</div>
                  ) : null}
                  {doc.publishedAt ? <div>Đăng tải: {formatDate(doc.publishedAt)}</div> : null}
                </div>
              </td>

              <td className="align-top border-l border-surface-border px-[15px] py-[10px]">
                <Link
                  href={ROUTES.article(doc.slug)}
                  className="text-sm text-surface-textBase transition-colors hover:text-brand-primary"
                >
                  {doc.title}
                </Link>
                {/* ĐƯỜNG LUI MOBILE — cột "Nội dung chi tiết" bị ẩn ở màn hình hẹp. */}
                <div className="mt-2 md:hidden">
                  <NutXemChiTiet slug={doc.slug} />
                </div>
              </td>

              <td className="hidden align-top border-l border-surface-border px-[15px] py-[10px] text-center md:table-cell">
                <NutXemChiTiet slug={doc.slug} />
              </td>

              <td className="hidden align-top border-l border-surface-border px-[15px] py-[10px] text-center text-sm md:table-cell">
                {formatNgayThuan(doc.docIssuedDate)}
              </td>

              <td className="hidden align-top border-l border-surface-border px-[15px] py-[10px] text-center text-sm md:table-cell">
                {formatDate(doc.publishedAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * Nút "Xem chi tiết" — hình dạng 142×40 của cổng tham chiếu, màu lấy từ `design-tokens`.
 *
 * ⚠ `w-full max-w-[142px]`, không phải `w-[142px]` trần: cột chứa nó rộng 15% (≈116px ở khung
 * 776px), nên một bề rộng cứng 142px sẽ đẩy bảng tràn ra ngoài — đúng con số của họ, sai khung
 * của ta.
 */
function NutXemChiTiet({ slug }: { slug: string }) {
  return (
    <Link
      href={ROUTES.article(slug)}
      className="inline-flex h-10 w-full max-w-[142px] items-center justify-center gap-2 rounded border border-brand-primary/40 bg-white px-2 text-sm font-medium text-brand-primary transition-colors hover:bg-brand-primaryLight"
    >
      <svg
        className="h-4 w-4"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.75}
          d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
        />
      </svg>
      <span>Xem chi tiết</span>
    </Link>
  );
}
