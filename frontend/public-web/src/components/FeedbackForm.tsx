'use client';

import { useId, useState } from 'react';

/**
 * Biểu mẫu gửi góp ý / đánh giá mức độ hài lòng — CN-01.6, chốt **D1**.
 *
 * <h2>⛔⛔ Đây ⛔ KHÔNG phải ô bình luận</h2>
 *
 * Chốt D1 (12/8/2026) **tắt bình luận công khai tự do**. Khác biệt ⛔ không nằm ở cái tên mà ở
 * hành vi: mục gửi lên vào trạng thái **Chờ duyệt** và ⛔ **không** hiện ra ngay. Câu thông báo
 * sau khi gửi vì thế phải nói thẳng điều đó — để người gửi ⛔ không tưởng mình vừa đăng một thứ
 * công khai rồi đi tìm nó suốt buổi chiều.
 *
 * <h2>⭐ Ẩn danh được — và đó là khác biệt so với biểu mẫu Liên hệ</h2>
 *
 * `/lien-he` **bắt buộc** ít nhất một cách liên hệ ngược (`ck_contacts_lien_lac`) vì Công ty phải
 * trả lời được. Ở đây thì ⛔ không ai chờ trả lời: đây là một phiếu khảo sát. Bắt buộc họ tên ở
 * đây là chép nhầm luật của một kênh khác.
 *
 * <h2>⛔ reCAPTCHA CHƯA chạy — nói ra thay vì để người đọc mã tự suy</h2>
 *
 * Backend có **chỗ cắm** (`InboundSubmissionGate`) và nó **mặc định TẮT**; khoá thuộc **G13** và
 * Công ty chưa cấp, nên phía giao diện chưa nạp script của Google và chưa gửi `recaptchaToken`.
 * Trong lúc chờ, chống lạm dụng dựa vào `RateLimitPolicy.PUBLIC`.
 *
 * ⛔ Và ⛔ ĐỪNG mở CSP cho `https://www.google.com` trước khi có khoá: một dòng `script-src` cho
 * một script ta **chưa nạp** là nới bề mặt tấn công lấy về đúng số không.
 *
 * <h2>Không tự khẳng định đã gửi thành công</h2>
 *
 * Trạng thái `xong` chỉ bật sau khi máy chủ trả 204.
 */
type TrangThai =
  { loai: 'nhap' } | { loai: 'dang-gui' } | { loai: 'xong' } | { loai: 'loi'; thongDiep: string };

/** Trần khớp `FeedbackService.DAI_TOI_DA_NOI_DUNG` — luật 14, một con số hai nơi nhớ. */
export const DAI_TOI_DA_NOI_DUNG = 2000;

const MUC_SAO = [1, 2, 3, 4, 5] as const;

export function FeedbackForm() {
  const id = useId();
  const [tt, datTt] = useState<TrangThai>({ loai: 'nhap' });
  const [sao, datSao] = useState<number | null>(null);

  async function gui(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const fd = new FormData(form);
    datTt({ loai: 'dang-gui' });

    try {
      const res = await fetch('/api/v1/public/feedbacks', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fullName: String(fd.get('fullName') ?? ''),
          email: String(fd.get('email') ?? ''),
          // ⛔ `null` chứ ⛔ không phải 0: quy tắc 16 — "0 sao" là một khẳng định về sự hài lòng,
          //    "chưa chấm" thì ⛔ không nói gì, và backend phân biệt hai thứ ấy ở mẫu số của
          //    điểm trung bình.
          rating: sao,
          content: String(fd.get('content') ?? ''),
        }),
      });

      if (res.status === 204) {
        form.reset();
        datSao(null);
        datTt({ loai: 'xong' });
        return;
      }
      // ⚠ 429 có thông điệp riêng: "thử lại sau" khác hẳn "bạn nhập sai", và gộp hai cái vào một
      //   câu chung khiến người dùng sửa mãi một biểu mẫu vốn ⛔ không sai gì.
      datTt({
        loai: 'loi',
        thongDiep:
          res.status === 429
            ? 'Bạn đã gửi quá nhiều lần trong thời gian ngắn. Vui lòng thử lại sau ít phút.'
            : 'Chưa gửi được. Nội dung góp ý không được để trống và không quá 2.000 ký tự.',
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
        <p className="font-bold text-brand-primary">Đã nhận được góp ý của bạn.</p>
        {/* ⛔⛔ Câu này PHẢI nói ra việc kiểm duyệt. Chốt D1 làm mục gửi lên KHÔNG hiện ngay;
            một câu "Cảm ơn bạn đã góp ý" trơn để người gửi đi tìm bài của mình và kết luận là
            trang hỏng. */}
        <p className="mt-1.5 text-surface-textSecondary">
          Góp ý sẽ được Công ty xem xét trước khi hiển thị công khai, nên bạn chưa thấy nó ngay bên
          dưới.
        </p>
        <button
          type="button"
          onClick={() => datTt({ loai: 'nhap' })}
          className="mt-3 text-xs font-semibold text-brand-primary hover:underline"
        >
          Gửi góp ý khác
        </button>
      </div>
    );
  }

  const dangGui = tt.loai === 'dang-gui';

  return (
    <form onSubmit={gui} className="flex flex-col gap-3">
      <fieldset className="flex flex-col gap-1.5">
        <legend className="text-xs font-semibold text-surface-textSecondary">
          Mức độ hài lòng (không bắt buộc)
        </legend>
        <div className="flex items-center gap-1.5">
          {MUC_SAO.map((n) => (
            <button
              key={n}
              type="button"
              aria-pressed={sao === n}
              aria-label={`${n} trên 5 sao`}
              // ⭐ Bấm lại chính mức đang chọn là BỎ chọn — nếu không thì một cú bấm nhầm khoá
              //   người dùng vào một điểm số họ ⛔ không định cho, và ⛔ không có đường lùi.
              onClick={() => datSao(sao === n ? null : n)}
              className={`h-11 w-11 rounded-lg border text-lg transition-colors ${
                sao !== null && n <= sao
                  ? 'border-brand-primary bg-brand-primaryLight text-brand-primary'
                  : 'border-surface-border text-surface-textSecondary hover:border-brand-primary'
              }`}
            >
              <span aria-hidden="true">★</span>
            </button>
          ))}
          {sao !== null ? (
            <button
              type="button"
              onClick={() => datSao(null)}
              className="ml-2 text-xs font-semibold text-surface-textSecondary hover:underline"
            >
              Bỏ chấm điểm
            </button>
          ) : null}
        </div>
      </fieldset>

      {/* ⭐ Hai ô này KHÔNG bắt buộc — khác hẳn `/lien-he`. Xem javadoc component. */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Truong id={`${id}-ten`} name="fullName" nhan="Họ và tên (không bắt buộc)" />
        <Truong id={`${id}-mail`} name="email" nhan="Email (không bắt buộc)" kieu="email" />
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor={`${id}-nd`} className="text-xs font-semibold text-surface-textSecondary">
          Nội dung góp ý <span aria-hidden="true">*</span>
        </label>
        <textarea
          id={`${id}-nd`}
          name="content"
          required
          rows={5}
          maxLength={DAI_TOI_DA_NOI_DUNG}
          className="rounded-lg border border-surface-border px-3.5 py-2.5 text-sm text-surface-textBase outline-none focus:border-brand-primary"
        />
      </div>

      <p className="text-xs leading-relaxed text-surface-textSecondary">
        Góp ý được kiểm duyệt trước khi hiển thị công khai. Họ tên và email là tuỳ chọn; email
        <b> không</b> được công bố.
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
        {dangGui ? 'Đang gửi…' : 'Gửi góp ý'}
      </button>
    </form>
  );
}

function Truong({
  id,
  name,
  nhan,
  kieu = 'text',
}: {
  id: string;
  name: string;
  nhan: string;
  kieu?: string;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-xs font-semibold text-surface-textSecondary">
        {nhan}
      </label>
      <input
        id={id}
        name={name}
        type={kieu}
        maxLength={255}
        className="h-11 rounded-lg border border-surface-border px-3.5 text-sm text-surface-textBase outline-none focus:border-brand-primary"
      />
    </div>
  );
}
