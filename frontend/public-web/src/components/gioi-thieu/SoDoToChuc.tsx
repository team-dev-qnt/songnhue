import type { NutSoDo } from '@/lib/soDoToChuc';

/**
 * Sơ đồ tổ chức **hình cây** — CR-24 · CR-25.
 *
 * <h2>Hai chế độ, một khối markup</h2>
 *
 * <p>Dưới 768px là danh sách thụt lề; từ 768px là sơ đồ ngang có đường nối. Cùng một DOM, CSS đổi
 * bố cục (xem khối *"Sơ đồ tổ chức hình cây"* trong {@code globals.css}). Dựng hai khối markup rồi
 * ẩn bớt bằng {@code hidden md:block} là nhân đôi nội dung cho bộ đọc màn hình và cho SEO.
 *
 * <p>⛔ Ngữ nghĩa vẫn là {@code <ul>/<li>} lồng nhau ở **cả hai** chế độ, nên bộ đọc màn hình luôn
 * nghe đúng cây. Mọi đường kẻ là {@code ::before}/{@code ::after} — chúng ⛔ không tồn tại với
 * trình đọc, đúng như một đường kẻ nên thế.
 *
 * <p>⚠ Component này ⛔ <b>không</b> có bài kiểm render: vitest của `public-web` cố ý ⛔ không dựng
 * DOM. Vì vậy mọi suy luận đã được đẩy sang {@code lib/soDoToChuc.ts} — nơi có bài kiểm thật. Thứ
 * còn lại ở đây đúng nghĩa chỉ là trình bày.
 */
export function SoDoToChuc({ nut, nhan }: { nut: NutSoDo[]; nhan: string }) {
  if (nut.length === 0) {
    return null;
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-surface-border bg-white p-4 shadow-xs md:p-6">
      <ul className="sn-cay" aria-label={nhan}>
        {nut.map((n) => (
          <NhanhCay key={n.khoa} nut={n} goc />
        ))}
      </ul>
    </div>
  );
}

function NhanhCay({ nut, goc = false }: { nut: NutSoDo; goc?: boolean }) {
  return (
    <li>
      <div
        className={[
          // ⚠ Bề rộng khai ở ĐÚNG MỘT nhánh. Bản đầu để `md:w-full` ở phần dùng chung rồi thêm
          // `md:w-44` ở nhánh con ⇒ hai lớp cùng thuộc tính, thứ tự do bộ sinh CSS quyết định —
          // một khuyết tật im lặng, đổi theo phiên bản Tailwind chứ ⛔ không theo mã.
          'inline-block rounded-lg border px-3 py-2 align-top',
          goc
            ? 'border-brand-primary bg-brand-primaryLight md:mx-auto md:w-full md:max-w-md'
            : 'border-surface-border bg-surface-bgLayout/60 md:w-44',
        ].join(' ')}
      >
        <p
          className={`text-sm leading-snug ${
            goc ? 'font-bold text-brand-primary' : 'font-semibold text-surface-textBase'
          }`}
        >
          {nut.ten}
        </p>
        {nut.nhan ? (
          <p className="mt-0.5 text-[11px] font-medium text-surface-textSecondary">{nut.nhan}</p>
        ) : null}
        {nut.phu.length > 0 ? <BanLanhDao dong={nut.phu} /> : null}
      </div>
      {nut.con.length > 0 ? (
        <ul className="sn-cay-nhanh">
          {nut.con.map((con) => (
            <NhanhCay key={con.khoa} nut={con} />
          ))}
        </ul>
      ) : null}
    </li>
  );
}

/**
 * Ban lãnh đạo, hiển thị **bên trong** hộp gốc.
 *
 * <p>⛔⛔ Cố ý là một danh sách **PHẲNG**, ⛔ không phải một tầng cây. {@code org_unit_leaders} ⛔
 * không có cột cha–con và {@code title} là ô văn bản tự do, nên xếp *Chủ tịch* trên *Tổng Giám đốc*
 * là suy diễn từ chuỗi ký tự — một sơ đồ TRÔNG như dữ liệu trong khi nó là phỏng đoán. Thứ tự lấy
 * nguyên {@code sort_order} mà Công ty tự sắp ở màn hình quản trị. Bánh cóc giữ quyết định này nằm
 * ở {@code soDoToChuc.test.ts}.
 */
function BanLanhDao({ dong }: { dong: NutSoDo['phu'] }) {
  return (
    <div className="mt-2 border-t border-brand-primary/25 pt-2 text-left">
      {/* ⛔ KHÔNG `uppercase`: chữ hoa tiếng Việt chồng dấu khó đọc hơn chữ thường (CR-42, §10.61),
          và `noForcedUppercase.test.ts` bắt đúng lớp ấy — nó đỏ ngay lượt chạy đầu của tệp này.
          Cần nhấn mạnh thì dùng đậm + giãn chữ + màu, ⛔ đừng đổi chính con chữ. */}
      <p className="text-[11px] font-bold tracking-wide text-brand-primary">Ban lãnh đạo</p>
      <ul className="mt-1 space-y-0.5">
        {dong.map((d) => (
          <li key={`${d.ten}-${d.chucDanh}`} className="text-xs leading-snug">
            <span className="font-semibold text-surface-textBase">{d.ten}</span>
            <span className="text-surface-textSecondary"> — {d.chucDanh}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
