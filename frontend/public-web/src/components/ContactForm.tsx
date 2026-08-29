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
 * <h2>⛔ reCAPTCHA CHƯA có — nói ra thay vì để người đọc mã tự suy</h2>
 *
 * CN-01.4 yêu cầu reCAPTCHA v3; khoá thuộc **G13** và Công ty chưa cấp. Trong lúc chờ, chống lạm
 * dụng dựa vào `RateLimitPolicy.PUBLIC` ở backend. Đừng đọc sự vắng mặt của captcha ở đây thành
 * "đã cân nhắc và không cần".
 *
 * <h2>Không tự khẳng định đã gửi thành công</h2>
 *
 * Trạng thái `xong` chỉ bật sau khi máy chủ trả 204. Bật lạc quan ngay lúc bấm là lặp lại đúng
 * lỗi mà cả khối này sinh ra để tránh — chỉ khác là tự lừa ở tầng giao diện thay vì tầng dữ liệu.
 */
type TrangThai =
  { loai: 'nhap' } | { loai: 'dang-gui' } | { loai: 'xong' } | { loai: 'loi'; thongDiep: string };

export function ContactForm() {
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
        <Truong id={`${id}-ten`} name="fullName" nhan="Họ và tên" batBuoc />
        <Truong id={`${id}-cd`} name="subject" nhan="Tiêu đề" batBuoc />
        <Truong id={`${id}-mail`} name="email" nhan="Email" kieu="email" />
        <Truong id={`${id}-dt`} name="phone" nhan="Số điện thoại" kieu="tel" />
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

      <p className="text-xs leading-relaxed text-surface-textSecondary">
        Cần ít nhất <b>một</b> cách liên hệ lại: email hoặc số điện thoại. Ý kiến được kiểm duyệt
        trước khi chuyển tới bộ phận xử lý.
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
