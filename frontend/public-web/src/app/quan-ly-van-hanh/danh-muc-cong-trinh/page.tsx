import type { Metadata } from 'next';

import { BanDoHeThongCongTrinh } from '@/components/congtrinh/BanDoHeThongCongTrinh';
import { EmptyBlock } from '@/components/home/EmptyBlock';
import { PageShell } from '@/components/PageShell';
import { SectionNav } from '@/components/SectionNav';
import type { ConstructionRow } from '@/lib/api';
import { getConstructionCatalog, getSiteConfig } from '@/lib/api';
import { constructionDocUrl, mapUrl, ROUTES } from '@/lib/routes';

export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Danh mục công trình - Thủy lợi Sông Nhuệ',
  description:
    'Danh sách công trình thủy lợi theo từng Xí nghiệp trực thuộc, kèm quy trình vận hành, phương án bảo vệ và vị trí.',
  alternates: { canonical: ROUTES.quanLyVanHanh.danhMucCongTrinh },
};

/** Bảy cột đúng thứ tự §5.1 — bảng ở đây và bảng trong tài liệu phải đọc ra cùng một thứ. */
const COT = [
  'Tên công trình',
  'Địa điểm',
  'Thông tin chủ yếu',
  'Quy trình vận hành',
  'Phương án bảo vệ',
  'Vị trí',
];

/**
 * Quản lý, vận hành &gt; **Danh mục công trình** — CR-27, CR-28, CR-29, CR-44.
 *
 * <h2>CR-27: đây là dữ liệu, không phải một bài viết</h2>
 *
 * Bản dev xử lý danh mục công trình "như một bài viết". Hệ quả: người xem không lọc được theo
 * Xí nghiệp, không mở được tệp Quyết định, và mỗi lần thêm một trạm bơm là một lượt sửa nội
 * dung tay. Trang này đọc {@code GET /api/v1/public/constructions} — cùng bảng
 * {@code constructions} mà màn hình quản trị ghi vào.
 *
 * <h2>⛔ Chưa có dữ liệu, và đó là câu trả lời đúng lúc này</h2>
 *
 * <b>G8</b> còn mở: Công ty chưa gửi danh mục công trình tổng thể (Excel) kèm mã và toạ độ.
 * Bảng rỗng ⇒ trang nói thẳng là chưa có. Cấm seed vài trạm bơm "cho đẹp demo" — chính bộ dữ
 * liệu bịa kiểu ấy đã lên staging ngày 25/8 và không ai nhìn ra cổng đang rỗng (§10.54).
 *
 * <h2>CR-29 · Bản đồ hệ thống — chưa dựng, và vì sao</h2>
 *
 * CR-29 đòi bản PDF hai tỷ lệ (1/50.000 và 1/75.000) cho công chúng, còn tệp KMZ thì phải
 * đăng nhập mới xem/tải. Vế PDF thiếu <b>tệp</b> — Công ty chưa gửi. Vế KMZ thiếu <b>cơ chế
 * đăng nhập trên cổng</b>, thứ thuộc CR-08 và chưa dựng; và <b>OI-07</b> còn đang hỏi cho tải
 * về hay nhúng viewer. Dựng một nút tải trước khi có cả tệp lẫn tầng chặn là dựng một lối vào
 * không ai canh — nên chỗ này để trống kèm lý do, không để một nút chết.
 */
export default async function DanhMucCongTrinhPage() {
  // Hai lượt gọi độc lập ⇒ song song. `config` chỉ để lấy ảnh sơ đồ hệ thống; bảng công trình
  // không phụ thuộc nó, nên trang vẫn dựng đủ khi khoá cấu hình chưa có.
  const [catalogRaw, config] = await Promise.all([getConstructionCatalog(), getSiteConfig()]);
  const catalog = catalogRaw ?? [];

  return (
    <PageShell
      title="Danh mục công trình"
      description="Danh sách công trình thủy lợi do từng Xí nghiệp trực thuộc quản lý."
      breadcrumb={[{ label: 'Quản lý, vận hành' }, { label: 'Danh mục công trình' }]}
    >
      {catalog.length === 0 ? (
        <EmptyBlock>
          Danh mục công trình chưa được nhập. Dữ liệu công trình được quản lý ở màn hình Danh mục
          công trình của trang quản trị và công bố nguyên trạng ra cổng.
        </EmptyBlock>
      ) : (
        <div className="space-y-8">
          {catalog.map((donVi) => (
            <section key={donVi.unitCode ?? donVi.unitName}>
              <h2 className="flex items-center gap-2 border-b border-surface-border pb-2 text-base font-bold text-brand-primary">
                <span className="h-4 w-1.5 rounded-full bg-brand-primary" />
                {donVi.unitName}
                <span className="text-xs font-medium text-surface-textSecondary">
                  ({donVi.constructions.length} công trình)
                </span>
              </h2>

              <div className="mt-3 overflow-x-auto rounded-xl border border-surface-border bg-white shadow-xs">
                <table className="w-full min-w-[980px] border-collapse text-sm">
                  <caption className="sr-only">Bảng công trình do {donVi.unitName} quản lý</caption>
                  <thead>
                    <tr className="bg-brand-primaryLight text-left text-xs text-brand-primary">
                      <th scope="col" className="w-14 px-4 py-3 font-bold">
                        TT
                      </th>
                      {COT.map((ten) => (
                        <th key={ten} scope="col" className="px-4 py-3 font-bold">
                          {ten}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-surface-border">
                    {donVi.constructions.map((ct, i) => (
                      <DongCongTrinh key={ct.code} row={ct} stt={i + 1} />
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          ))}
        </div>
      )}

      {/* ───── CR-29 · Bản đồ hệ thống ─────
          ⭐ 01/09: khối này TRƯỚC ĐÂY là một `EmptyBlock` nói "bản đồ hệ thống chưa được đăng",
             trong khi trang chủ đang hiện đúng bản đồ ấy (ảnh sơ đồ Công ty đã tải lên + bản đồ
             tương tác). Hai trang trả lời ngược nhau về cùng một câu hỏi — quy tắc 14. Nay chỉ
             còn một chỗ, và nó ở đây, cạnh danh sách công trình mà nó vẽ điểm.

          ⚠ Câu chờ-dữ-liệu của CR-29 KHÔNG mất: nó nói về hai thứ khác (PDF hai tỷ lệ, KMZ sau
            đăng nhập) và vẫn đúng, nên nó xuống làm chú thích dưới bản đồ. Gỡ nó đi là để người
            nghiệm thu tưởng CR-29 đã xong. */}
      <div className="mt-10">
        <BanDoHeThongCongTrinh
          catalog={catalog}
          anhSoDo={config?.['site.home.map-image.attachment-id']}
        />
        {/* <p className="mt-3 text-xs leading-relaxed text-surface-textSecondary">
          <strong className="font-semibold">CR-29 còn chờ:</strong> bản PDF hai tỷ lệ (1/50.000 và
          1/75.000) chờ Công ty gửi tệp; bản KMZ yêu cầu đăng nhập nên chờ chức năng đăng nhập trên
          cổng, và cách phục vụ (cho tải về hay nhúng viewer) còn đang chờ Công ty chốt (OI-07).
        </p> */}
      </div>
      <SectionNav duongDan={ROUTES.quanLyVanHanh.danhMucCongTrinh} />
    </PageShell>
  );
}

/** Nhãn loại công trình — `ConstructionType` phía backend là enum trong mã. */
const NHAN_LOAI: Record<string, string> = {
  TRAM_BOM: 'Trạm bơm',
  CONG: 'Cống',
  KENH_MUONG: 'Kênh mương',
  DE_DIEU: 'Đê điều',
  KHAC: 'Khác',
};

function DongCongTrinh({ row, stt }: { row: ConstructionRow; stt: number }) {
  // ⛔ `constructionDocUrl`, KHÔNG phải `fileUrl` — xem javadoc của nó: đường tệp của cổng không
  //    phục vụ loại `CONSTRUCTION`, nên `fileUrl` ở đây là một liên kết 404 câm chờ sẵn.
  const quyTrinh = constructionDocUrl(row.operatingProcedureFileId);
  const phuongAn = constructionDocUrl(row.protectionPlanFileId);
  const banDo = mapUrl(row.latitude, row.longitude);

  return (
    <tr className="align-top hover:bg-surface-bgLayout/60">
      <td className="px-4 py-3 text-surface-textSecondary">{stt}</td>
      <td className="px-4 py-3">
        <span className="font-semibold text-surface-textBase">{row.name}</span>
        <span className="mt-0.5 block text-[11px] text-surface-textSecondary">
          {row.code} · {NHAN_LOAI[row.constructionType] ?? row.constructionType}
        </span>
      </td>
      {/* CR-44: ghi theo địa giới hành chính cấp xã MỚI. Đó là ràng buộc nhập liệu — cổng
          hiện nguyên văn thứ trong CSDL, không "chuẩn hoá" hộ theo một bảng ánh xạ tự nghĩ. */}
      <td className="px-4 py-3 text-surface-textBase">
        <OTrong giaTri={row.location} />
      </td>
      <td className="px-4 py-3 whitespace-nowrap text-surface-textBase">
        {/* "Số máy × Lưu lượng 1 máy bơm". Thiếu bất kỳ vế nào thì backend trả null — không
            ghép một nửa, không điền số 0 (quy tắc 16). */}
        <OTrong giaTri={row.mainSpec} />
      </td>
      <td className="px-4 py-3">
        <TepQuyetDinh href={quyTrinh} nhan="Quy trình vận hành" />
      </td>
      <td className="px-4 py-3">
        <TepQuyetDinh href={phuongAn} nhan="Phương án bảo vệ" />
      </td>
      <td className="px-4 py-3">
        {banDo ? (
          <a
            href={banDo}
            target="_blank"
            rel="noopener noreferrer"
            className="whitespace-nowrap font-medium text-brand-primary hover:underline"
          >
            Xem bản đồ ↗
          </a>
        ) : (
          <Gach />
        )}
      </td>
    </tr>
  );
}

/** Liên kết tới tệp Quyết định phê duyệt. Chưa có tệp ⇒ dấu gạch, KHÔNG phải một nút chết. */
function TepQuyetDinh({ href, nhan }: { href: string | null; nhan: string }) {
  if (!href) return <Gach />;
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-1.5 whitespace-nowrap font-medium text-brand-primary hover:underline"
    >
      <span className="rounded bg-red-50 px-1 py-0.5 text-[10px] font-extrabold text-red-700">
        pdf
      </span>
      <span>{nhan}</span>
    </a>
  );
}

function Gach() {
  return (
    <span className="text-surface-textSecondary" aria-label="Chưa có thông tin">
      —
    </span>
  );
}

function OTrong({ giaTri }: { giaTri: string | null }) {
  return giaTri ? <>{giaTri}</> : <Gach />;
}
