import type { Metadata } from 'next';
import { notFound } from 'next/navigation';

import { PageShell } from '@/components/PageShell';
import { SectionNav } from '@/components/SectionNav';
import { ColumnHeaderRow } from '@/components/home/ColumnHeaderRow';
import { OperationStatusRows } from '@/components/home/OperationStatusRows';
import { KhoaDangNhap } from '@/components/realtime/KhoaDangNhap';
import { RealtimeFrame } from '@/components/realtime/RealtimeFrame';
import { getOperationStatuses, getServerTime, getSiteConfig } from '@/lib/api';
import { COT_VAN_HANH } from '@/lib/homeDataColumns';
import { khoiVanHanhBat } from '@/lib/khoiVanHanh';
import { ROUTES } from '@/lib/routes';
import { docSo } from '@/lib/settings';

/** Lưới 6 cột — TRÙNG với khối trên trang chủ; hai nơi lệch nhau là hai bảng khác nhau. */
const LUOI = 'grid-cols-[1.7fr_1.2fr_1.3fr_1fr_1.1fr_1.1fr]';
const BE_RONG_TOI_THIEU = 'min-w-[760px]';

export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Vận hành công trình - Thủy lợi Sông Nhuệ',
  description: 'Số liệu vận hành trạm bơm của từng Xí nghiệp trực thuộc tại ngày truy cập.',
  alternates: { canonical: ROUTES.quanLyVanHanh.vanHanhCongTrinh },
};

/**
 * Quản lý, vận hành &gt; **Vận hành công trình** — CR-15, CR-34, §5.3.
 *
 * <h2>⭐ 31/08: đã đấu nối phần CN-02.11 — và chỉ phần ấy</h2>
 *
 * Bảng {@code construction_operation_status} có từ WS-19, phục vụ CN-02.11: <b>tình hình vận hành
 * nhập tay</b>, mỗi bản ghi là một mã trạng thái kèm <i>một</i> giá trị tham số. Nay nó ra tới cổng
 * qua {@code GET /api/v1/public/constructions/operation-statuses} — endpoint công khai từng là mục
 * "chưa có" duy nhất sửa được bằng mã. Mở nó là một quyết định về <b>phạm vi công bố</b> (bảng
 * thuộc phạm vi đơn vị, lọc tầng 3), chốt 31/08: công bố đúng sáu cột, {@code note} và người cập
 * nhật ở lại bên trong.
 *
 * <h2>⬜ Hai thứ còn thiếu — và §5.3 vẫn CHƯA được trả lời đủ</h2>
 *
 * <p>§5.3 đòi bốn trường cùng lúc — trạng thái vận hành, số máy đang chạy, lưu lượng, thời điểm
 * cập nhật — và đòi chúng <b>theo ngày truy cập</b>, tức một luồng số liệu đều đặn chứ không phải
 * một sổ ghi chép sự kiện. Bảng CN-02.11 không thay thế được nó.
 *
 * <ol>
 *   <li>⬜ chưa có API nguồn nào cấp bốn trường ấy — <b>OI-02</b> còn mở;
 *   <li>⬜ chưa có dữ liệu công trình — danh mục tổng thể thuộc <b>G8</b>, nên bảng dưới đây vẫn
 *       rỗng cho tới khi Công ty gửi.
 * </ol>
 *
 * <p>⛔ Rỗng thì nói thẳng là rỗng, không dựng sẵn lưới dấu gạch (§10.54).
 *
 * <h2>⭐ 04/09: trang này TẮT ĐƯỢC từ màn hình quản trị</h2>
 *
 * Cùng một công tắc với khối Nhóm 2 trên trang chủ — xem {@code lib/khoiVanHanh.ts}. Tắt ⇒ mục
 * menu biến mất khỏi thanh điều hướng, chân trang, dải mục và sidebar, <b>và</b> địa chỉ này trả
 * 404. Ẩn lối vào mà vẫn để trang mở là nửa vòng: ai có liên kết cũ, hoặc công cụ tìm kiếm đã lập
 * chỉ mục, vẫn dẫn người dùng vào một trang Công ty đã quyết định không công bố.
 *
 * <p>⚠ {@code config} dùng lại lượt gọi đã có sẵn ngay dưới — ⛔ không gọi {@code getSiteConfig()}
 * lượt thứ hai chỉ để hỏi một cờ; thêm một lượt gọi là thêm một điểm hỏng.
 */
export default async function VanHanhCongTrinhPage() {
  const [config, tinhHinhVanHanh, serverTime] = await Promise.all([
    getSiteConfig(),
    getOperationStatuses(),
    getServerTime(),
  ]);

  if (!khoiVanHanhBat(config)) {
    notFound();
  }

  const nhipLamMoi = docSo(config?.['site.home.realtime.refresh-seconds'], 300);
  const dong = tinhHinhVanHanh ?? [];

  return (
    <PageShell
      title="Vận hành công trình"
      description="Số liệu vận hành trạm bơm của từng Xí nghiệp trực thuộc tại ngày truy cập."
      breadcrumb={[{ label: 'Quản lý, vận hành' }, { label: 'Vận hành công trình' }]}
    >
      <section className="overflow-hidden rounded-xl border border-surface-border bg-white shadow-xs">
        <div className="p-5 pb-4">
          <h2 className="text-sm font-bold tracking-tight text-emerald-800">
            Tình hình vận hành hiện hành
          </h2>
          <p className="mt-1 text-xs text-surface-textSecondary">
            Mã tình hình vận hành do trực ban ghi nhận (CN-02.11). Số liệu tự động của trạm bơm — số
            máy đang chạy, lưu lượng — chưa có nguồn API.
          </p>
        </div>

        <ColumnHeaderRow cot={COT_VAN_HANH} luoi={LUOI} beRongToiThieu={BE_RONG_TOI_THIEU} />

        {dong.length > 0 ? (
          <OperationStatusRows rows={dong} luoi={LUOI} beRongToiThieu={BE_RONG_TOI_THIEU} />
        ) : null}

        <div className="p-5">
          <RealtimeFrame
            updatedAt={serverTime}
            refreshSeconds={nhipLamMoi}
            unavailable={dong.length === 0}
            unavailableReason="Chưa công trình nào được ghi nhận tình hình vận hành — danh mục công trình tổng thể thuộc G8. Số liệu tự động của trạm bơm (§5.3) vẫn chờ API nguồn — OI-02."
          />
        </div>
      </section>

      <div className="mt-6">
        <KhoaDangNhap
          tieuDe="Theo dõi theo tuần và tháng"
          moTa="Số liệu vận hành theo tuần, tháng và hai biểu mẫu Báo cáo nhanh chống hạn / chống úng chỉ dành cho người dùng đã đăng nhập."
        />
      </div>
      <SectionNav duongDan={ROUTES.quanLyVanHanh.vanHanhCongTrinh} />
    </PageShell>
  );
}
