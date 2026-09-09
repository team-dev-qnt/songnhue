'use client';

import { useId, useState } from 'react';

/**
 * Biểu mẫu gửi liên hệ / phản ánh — CN-01.4.
 *
 * <h2>Vì sao biểu mẫu này được dựng ở lượt 29/08 mà trước đó thì không</h2>
 *
 * Chú thích cũ ở trang Liên hệ từ chối dựng nó, và lý do ấy đúng: *"một form gửi đi mà không ai
 * nhận tệ hơn hẳn không có form: người dân tin là đã gửi được"*. Điều kiện ấy nay đã đủ —
 * `V202608291043` dựng bảng `contacts`, `POST /api/v1/public/contacts` nhận, và màn hình quản
 * trị đọc được. Vòng khép kín có bài kiểm đi qua HTTP đứng sau (`ContactHttpTest`).
 *
 * <h2>⛔ reCAPTCHA CHƯA chạy — nói ra thay vì để người đọc mã tự suy</h2>
 *
 * CN-01.4 yêu cầu reCAPTCHA v3. Backend đã có **chỗ cắm** (T36.6) và nó **mặc định TẮT**; khoá
 * thuộc **G13** và Công ty chưa cấp, nên phía giao diện chưa nạp script của Google và chưa gửi
 * `recaptchaToken`. Trong lúc chờ, chống lạm dụng dựa vào `RateLimitPolicy.PUBLIC` ở backend.
 * Đừng đọc sự vắng mặt của captcha ở đây thành "đã cân nhắc và không cần".
 *
 * ⛔ Và ⛔ ĐỪNG mở CSP cho `https://www.google.com` trước khi có khoá: một dòng `script-src` cho
 * một script ta **chưa nạp** là nới bề mặt tấn công lấy về đúng số không.
 *
 * <h2>⭐ Trường nào hiện, trường nào bắt buộc — do CẤU HÌNH quyết định (T36.7)</h2>
 *
 * Ba khoá `site.contact.field.*` đọc từ `site-config`. ⚠ Giao diện **không tự suy**: khi Công ty
 * tắt ô Số điện thoại thì email trở thành bắt buộc, và luật ấy do **backend** suy ra
 * (`ContactFormPolicy`) rồi trang truyền xuống. Dựng lại phép suy ở đây là bản sao thứ hai của một
 * luật đang nằm ở backend, và bản sao ấy sẽ lệch.
 *
 * <h2>Không tự khẳng định đã gửi thành công</h2>
 *
 * Trạng thái `xong` chỉ bật sau khi máy chủ trả 204. Bật lạc quan ngay lúc bấm là lặp lại đúng
 * lỗi mà cả khối này sinh ra để tránh — chỉ khác là tự lừa ở tầng giao diện thay vì tầng dữ liệu.
 */
type TrangThai =
  { loai: 'nhap' } | { loai: 'dang-gui' } | { loai: 'xong' } | { loai: 'loi'; thongDiep: string };

export interface CauHinhBieuMau {
  /** Có hiện ô Số điện thoại ⛔ không — `site.contact.field.phone.enabled`. */
  hienDienThoai: boolean;
  /** ⚠ Đã tính cả vế suy ra "tắt điện thoại ⇒ email bắt buộc". */
  emailBatBuoc: boolean;
  dienThoaiBatBuoc: boolean;
  /**
   * Có hiện ô Họ và tên ⛔ không — `site.contact.field.full-name.enabled` (T28.49).
   *
   * ⛔ Tắt ô này ⛔ **không** làm liên hệ thành ẩn danh hoàn toàn: email vẫn bắt buộc, nên Công ty
   * vẫn trả lời được. Thứ mất đi là *danh tính tự khai* — điều kiện để một người dân dám phản ánh
   * việc họ ⛔ không muốn gắn tên mình vào.
   */
  hienHoTen: boolean;
  /** Có hiện ô Tiêu đề ⛔ không — `site.contact.field.subject.enabled` (T28.49). */
  hienTieuDe: boolean;
}

/**
 * ⚠ Mặc định khớp giá trị seed của migration — luật 14, một luật hai nơi nhớ.
 *
 * ⛔ `emailBatBuoc` đổi `false` → **`true`** ngày 08/09/2026 cùng lượt `V202609081071` đặt lại hàng
 * seed. Để lệch là dựng đúng cái bẫy luật 3: một môi trường thiếu hàng settings sẽ lặng lẽ quay về
 * chính sách CŨ, và biểu mẫu thôi đánh dấu Email là bắt buộc trong khi backend vẫn từ chối.
 */
export const CAU_HINH_MAC_DINH: CauHinhBieuMau = {
  hienDienThoai: true,
  emailBatBuoc: true,
  dienThoaiBatBuoc: false,
  hienHoTen: true,
  hienTieuDe: true,
};

export function ContactForm({ cauHinh = CAU_HINH_MAC_DINH }: { cauHinh?: CauHinhBieuMau }) {
  const id = useId();
  const [tt, datTt] = useState<TrangThai>({ loai: 'nhap' });

  async function gui(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const fd = new FormData(form);
    datTt({ loai: 'dang-gui' });

    try {
      const res = await fetch('/api/v1/public/contacts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fullName: String(fd.get('fullName') ?? ''),
          email: String(fd.get('email') ?? ''),
          phone: String(fd.get('phone') ?? ''),
          subject: String(fd.get('subject') ?? ''),
          content: String(fd.get('content') ?? ''),
        }),
      });

      if (res.status === 204) {
        form.reset();
        datTt({ loai: 'xong' });
        return;
      }
      // ⚠ 429 có thông điệp riêng: "thử lại sau" khác hẳn "bạn nhập sai", và gộp hai cái vào một
      //   câu chung khiến người dùng sửa mãi một biểu mẫu vốn không sai gì.
      datTt({
        loai: 'loi',
        thongDiep:
          res.status === 429
            ? 'Bạn đã gửi quá nhiều lần trong thời gian ngắn. Vui lòng thử lại sau ít phút.'
            : 'Chưa gửi được. Kiểm tra lại họ tên, tiêu đề, nội dung và ít nhất một cách liên hệ (email hoặc số điện thoại).',
      });
    } catch {
      datTt({ loai: 'loi', thongDiep: 'Không kết nối được tới máy chủ. Vui lòng thử lại.' });
    }
  }

  if (tt.loai === 'xong') {
    return (
      <div
        role="status"
        className="rounded-lg border border-brand-primary/30 bg-brand-primaryLight/40 p-5 text-sm leading-relaxed text-surface-textBase"
      >
        <p className="font-bold text-brand-primary">Đã nhận được ý kiến của bạn.</p>
        <p className="mt-1.5 text-surface-textSecondary">
          Công ty sẽ xem xét và liên hệ lại qua thông tin bạn để lại. Trường hợp khẩn cấp về phòng
          chống thiên tai, vui lòng gọi trực tiếp số trực ban 24/7 ở trên.
        </p>
        <button
          type="button"
          onClick={() => datTt({ loai: 'nhap' })}
          className="mt-3 text-xs font-semibold text-brand-primary hover:underline"
        >
          Gửi ý kiến khác
        </button>
      </div>
    );
  }

  const dangGui = tt.loai === 'dang-gui';

  return (
    <form onSubmit={gui} className="flex flex-col gap-3">
      {/* Bốn ô một hàng từ `lg`: từ 29/08 biểu mẫu này chiếm TRỌN bề rộng ở cả trang chủ lẫn
          trang Liên hệ, và bốn ô xếp 2×2 trên một khung rộng 1200px để lại một khoảng trống
          bằng nửa màn hình. Dưới `lg` vẫn 2 cột, dưới `sm` vẫn 1 — ô nhập không bao giờ hẹp
          hơn ngưỡng bấm được bằng ngón tay. */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {/* ⛔ Ô TẮT thì BIẾN MẤT hẳn, ⛔ không phải "hiện mà ⛔ không bắt buộc" — T28.49. Một ô
            trống ⛔ không bắt buộc vẫn là một câu hỏi đặt ra cho người dân, và mục đích của lượt
            tắt này là ⛔ KHÔNG hỏi. Backend cũng thôi kiểm nó (`ContactFormPolicy.hienHoTen()`),
            nên hai phía nói cùng một câu. */}
        {cauHinh.hienHoTen ? (
          <Truong id={`${id}-ten`} name="fullName" nhan="Họ và tên" batBuoc />
        ) : null}
        {cauHinh.hienTieuDe ? (
          <Truong id={`${id}-cd`} name="subject" nhan="Tiêu đề" batBuoc />
        ) : null}
        <Truong
          id={`${id}-mail`}
          name="email"
          nhan="Email"
          kieu="email"
          batBuoc={cauHinh.emailBatBuoc}
        />
        {/* ⛔ Ô đã tắt thì ⛔ KHÔNG dựng input ẩn: một trường `disabled`/`hidden` vẫn nằm trong
            `FormData` ở vài trình duyệt, và người đọc mã sau sẽ tưởng nó còn gửi gì đó. */}
        {cauHinh.hienDienThoai ? (
          <Truong
            id={`${id}-dt`}
            name="phone"
            nhan="Số điện thoại"
            kieu="tel"
            batBuoc={cauHinh.dienThoaiBatBuoc}
          />
        ) : null}
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor={`${id}-nd`} className="text-xs font-semibold text-surface-textSecondary">
          Nội dung <span aria-hidden="true">*</span>
        </label>
        <textarea
          id={`${id}-nd`}
          name="content"
          required
          rows={5}
          maxLength={5000}
          className="rounded-lg border border-surface-border px-3.5 py-2.5 text-sm text-surface-textBase outline-none focus:border-brand-primary"
        />
      </div>

      {/* ⛔ Câu này phải NÓI ĐÚNG cấu hình đang chạy. Giữ nguyên "email hoặc số điện thoại" khi ô
          điện thoại đã tắt là một dòng chữ NÓI DỐI — §10.69: một chú thích sai khó thấy hơn hẳn
          một chú thích không ai đọc. */}
      <p className="text-xs leading-relaxed text-surface-textSecondary">
        {cauHinh.hienDienThoai
          ? 'Cần ít nhất một cách liên hệ lại: email hoặc số điện thoại.'
          : 'Vui lòng để lại email để Công ty liên hệ lại.'}{' '}
        Ý kiến được kiểm duyệt trước khi chuyển tới bộ phận xử lý.
      </p>

      {tt.loai === 'loi' ? (
        <p
          role="alert"
          className="rounded-lg bg-red-50 px-3.5 py-2.5 text-xs font-semibold text-red-700"
        >
          {tt.thongDiep}
        </p>
      ) : null}

      <button
        type="submit"
        disabled={dangGui}
        className="min-h-11 self-start rounded-lg bg-brand-primary px-5 py-3 text-sm font-bold text-white transition-colors hover:bg-brand-primaryHover disabled:opacity-60"
      >
        {dangGui ? 'Đang gửi…' : 'Gửi ý kiến'}
      </button>
    </form>
  );
}

function Truong({
  id,
  name,
  nhan,
  kieu = 'text',
  batBuoc = false,
}: {
  id: string;
  name: string;
  nhan: string;
  kieu?: string;
  batBuoc?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-xs font-semibold text-surface-textSecondary">
        {nhan}
        {batBuoc ? <span aria-hidden="true"> *</span> : null}
      </label>
      <input
        id={id}
        name={name}
        type={kieu}
        required={batBuoc}
        maxLength={255}
        className="h-11 rounded-lg border border-surface-border px-3.5 text-sm text-surface-textBase outline-none focus:border-brand-primary"
      />
    </div>
  );
}
