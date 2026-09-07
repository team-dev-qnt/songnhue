import type { Metadata } from 'next';

import { FeedbackForm } from '@/components/FeedbackForm';
import { EmptyBlock } from '@/components/home/EmptyBlock';
import { PageShell } from '@/components/PageShell';
import { getFeedbacks, getSiteConfig } from '@/lib/api';
import { ROUTES } from '@/lib/routes';
import { docBool } from '@/lib/settings';

export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Góp ý & đánh giá - Thủy lợi Sông Nhuệ',
  description:
    'Gửi góp ý và đánh giá mức độ hài lòng về cổng thông tin điện tử của Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ. Mọi góp ý được kiểm duyệt trước khi hiển thị.',
  alternates: { canonical: ROUTES.gopY },
};

/**
 * **Góp ý & đánh giá** — CN-01.6, chốt **D1** (kiểm duyệt 100%).
 *
 * <h2>⭐⭐ Trang này là NỬA "ĐỌC" của vòng kiểm duyệt</h2>
 *
 * Màn hình kiểm duyệt ở trang quản trị chỉ có nghĩa nếu *đã duyệt* khác *chưa duyệt* ở một chỗ
 * ai đó nhìn thấy được. Dựng nút Duyệt mà ⛔ không dựng nơi công bố thì nút ấy ⛔ không quyết
 * định điều gì — đúng luật 27, và lượt 28/8 tìm ra sáu lần trong một buổi cùng một triệu chứng:
 * *màn hình báo lưu thành công, cổng ⛔ không đổi gì*.
 *
 * <h2>⚠ Trang này ⛔ KHÔNG nằm trong menu — và đó là quyết định, ⛔ không phải sót</h2>
 *
 * Cây danh mục + menu nhận qua §3 văn bản nghiệm thu (G14) ⛔ không có mục "Góp ý". Menu là **dữ
 * liệu có CRUD** của khách (quy tắc 16); chèn một mục bằng migration là ta tự quyết hộ họ bố cục
 * cổng. Lối vào hôm nay: liên kết ở `/lien-he` và `sitemap.xml`. Chỗ đặt trong menu là câu hỏi
 * gửi Công ty.
 *
 * <h2>⛔ Nội dung dựng bằng TEXT, tuyệt đối ⛔ không dựng thành HTML</h2>
 *
 * Chữ ở đây do người lạ trên Internet gõ, và sau khi duyệt nó hiện cho **mọi người đọc cổng** —
 * xa hơn hẳn `contacts`, nơi nạn nhân của một XSS lưu trữ chỉ là người quản trị. React escape
 * mặc định và điều đó phải được giữ: ⛔ không `dangerouslySetInnerHTML` ở bất kỳ đâu trong tệp
 * này.
 *
 * <h2>⚠ Ba trạng thái rỗng KHÁC NHAU, và trang phải nói đúng cái nào</h2>
 *
 * <ol>
 *   <li>`null` — backend ⛔ không trả lời (`apiGet` nuốt lỗi). Cổng ⛔ không được trắng trang.
 *   <li>`[]` + công tắc công bố **TẮT** — Công ty cố ý ⛔ không công bố.
 *   <li>`[]` + công tắc **BẬT** — chưa có góp ý nào được duyệt.
 * </ol>
 *
 * ⛔ Gộp ba thứ này vào một câu "Chưa có góp ý nào" là bịa ra một sự thật ở hai trong ba trường
 * hợp (luật 9 + quy tắc 16).
 */
export default async function GopYPage() {
  const [config, feedbacks] = await Promise.all([getSiteConfig(), getFeedbacks()]);

  // ⛔ Mặc định `true` khi khoá vắng: đây là trạng thái seed của `V202609071068`, và một khoá
  //    chưa đọc được ⛔ không được lặng lẽ tắt một kênh Công ty đang mở.
  const nhanGopY = docBool(config?.['site.feedback.enabled'], true);
  const congBo = docBool(config?.['site.feedback.public-list.enabled'], true);

  const mucDaDuyet = feedbacks ?? [];

  return (
    <PageShell
      title="Góp ý & đánh giá"
      description="Ý kiến của bạn về cổng thông tin điện tử và dịch vụ của Công ty"
      breadcrumb={[{ label: 'Góp ý & đánh giá' }]}
    >
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <section className="rounded-xl border border-surface-border bg-white p-5 shadow-xs sm:p-6">
          <h2 className="border-b border-surface-border pb-3 text-sm font-bold tracking-tight text-brand-primary">
            Gửi góp ý
          </h2>
          <div className="mt-4">
            {nhanGopY ? (
              <FeedbackForm />
            ) : (
              <EmptyBlock>
                Công ty đang tạm ngừng tiếp nhận góp ý qua cổng. Bạn vẫn gửi được phản ánh, kiến
                nghị ở trang Liên hệ.
              </EmptyBlock>
            )}
          </div>
        </section>

        <section className="rounded-xl border border-surface-border bg-white p-5 shadow-xs sm:p-6">
          <h2 className="border-b border-surface-border pb-3 text-sm font-bold tracking-tight text-brand-primary">
            Góp ý đã được duyệt
          </h2>
          <div className="mt-4">
            {feedbacks === null ? (
              <EmptyBlock>Chưa tải được danh sách góp ý. Vui lòng thử lại sau ít phút.</EmptyBlock>
            ) : !congBo ? (
              <EmptyBlock>
                Công ty hiện không công bố góp ý trên cổng. Ý kiến bạn gửi vẫn được tiếp nhận và xử
                lý.
              </EmptyBlock>
            ) : mucDaDuyet.length === 0 ? (
              <EmptyBlock>
                Chưa có góp ý nào được duyệt. Mọi ý kiến gửi lên đều được Công ty xem xét trước khi
                hiển thị.
              </EmptyBlock>
            ) : (
              <ul className="flex flex-col gap-4">
                {mucDaDuyet.map((m, i) => (
                  <li
                    key={`${m.createdAt}-${i}`}
                    className="border-b border-surface-border/60 pb-4 last:border-b-0 last:pb-0"
                  >
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-semibold text-surface-textBase">
                        {/* ⛔ Ẩn danh là hợp lệ — nói thẳng, ⛔ không bịa một cái tên (quy tắc 16). */}
                        {m.fullName ?? 'Người dùng cổng'}
                      </span>
                      {m.rating !== null ? (
                        <span
                          className="text-xs text-brand-primary"
                          aria-label={`${m.rating} trên 5 sao`}
                        >
                          <span aria-hidden="true">{'★'.repeat(m.rating)}</span>
                        </span>
                      ) : null}
                      <span className="ml-auto text-[11px] text-surface-textSecondary">
                        {ngayVn(m.createdAt)}
                      </span>
                    </div>
                    {/* ⛔⛔ TEXT, ⛔ không HTML — xem javadoc trang. `whitespace-pre-line` giữ
                        xuống dòng người gửi gõ mà ⛔ không diễn giải một thẻ nào. */}
                    <p className="mt-1.5 whitespace-pre-line text-sm leading-relaxed text-surface-textBase">
                      {m.content}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>
      </div>
    </PageShell>
  );
}

/** UTC+7 ở mọi chỗ hiển thị thời gian — CLAUDE.md quy tắc 1. */
function ngayVn(iso: string): string {
  return new Date(iso).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
}
