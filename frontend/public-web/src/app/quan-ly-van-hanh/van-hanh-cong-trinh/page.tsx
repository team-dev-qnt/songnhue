import type { Metadata } from 'next';

import { PageShell } from '@/components/PageShell';
import { KhoaDangNhap } from '@/components/realtime/KhoaDangNhap';
import { RealtimeFrame } from '@/components/realtime/RealtimeFrame';
import { getServerTime, getSiteConfig } from '@/lib/api';
import { ROUTES } from '@/lib/routes';
import { docSo } from '@/lib/settings';

export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Vận hành công trình - Thủy lợi Sông Nhuệ',
  description: 'Số liệu vận hành trạm bơm của từng Xí nghiệp trực thuộc tại ngày truy cập.',
  alternates: { canonical: ROUTES.quanLyVanHanh.vanHanhCongTrinh },
};

/**
 * Quản lý, vận hành &gt; **Vận hành công trình** — CR-15, CR-34, §5.3.
 *
 * <h2>Trạng thái thật: chưa có nguồn — trả lời OI-02</h2>
 *
 * Bảng {@code construction_operation_status} đã tồn tại từ WS-19, nhưng nó phục vụ CN-02.11:
 * <b>tình hình vận hành nhập tay</b>, mỗi bản ghi là một mã trạng thái kèm <i>một</i> giá trị
 * tham số. §5.3 lại đòi bốn trường cùng lúc — trạng thái vận hành, số máy đang chạy, lưu
 * lượng, thời điểm cập nhật — và đòi chúng <b>theo ngày truy cập</b>, tức là một luồng số liệu
 * đều đặn chứ không phải một sổ ghi chép sự kiện.
 *
 * <p>Ba thứ còn thiếu, không thứ nào sửa được ở tầng giao diện:
 *
 * <ol>
 *   <li>chưa có API nguồn nào cấp bốn trường ấy (câu hỏi OI-02 gửi Công ty);
 *   <li>chưa có dữ liệu công trình — danh mục tổng thể thuộc G8;
 *   <li>chưa có endpoint công khai cho nhật ký vận hành. Bảng ấy thuộc phạm vi đơn vị (lọc
 *       tầng 3): mở nó ra công khai là một quyết định về phạm vi công bố, không phải một dòng
 *       mã — và phải kèm bài kiểm cố tình hỏi dữ liệu của Xí nghiệp khác.
 * </ol>
 *
 * <p>Theo §7, trang vẫn dựng đủ khung và để trạng thái chờ dữ liệu.
 */
export default async function VanHanhCongTrinhPage() {
  const [config, serverTime] = await Promise.all([getSiteConfig(), getServerTime()]);
  const nhipLamMoi = docSo(config?.['site.home.realtime.refresh-seconds'], 300);

  return (
    <PageShell
      title="Vận hành công trình"
      description="Số liệu vận hành trạm bơm của từng Xí nghiệp trực thuộc tại ngày truy cập."
      breadcrumb={[{ label: 'Quản lý, vận hành' }, { label: 'Vận hành công trình' }]}
    >
      <section className="rounded-xl border border-surface-border bg-white p-5 shadow-xs">
        <h2 className="text-sm font-bold uppercase tracking-tight text-emerald-800">
          Số liệu tại ngày truy cập
        </h2>
        <div className="mt-4">
          <RealtimeFrame
            updatedAt={serverTime}
            refreshSeconds={nhipLamMoi}
            unavailable
            unavailableReason="Chưa có nguồn số liệu vận hành trạm bơm tự động, và danh mục công trình chưa được nhập."
          />
        </div>
      </section>

      <div className="mt-6">
        <KhoaDangNhap
          tieuDe="Theo dõi theo tuần và tháng"
          moTa="Số liệu vận hành theo tuần, tháng và hai biểu mẫu Báo cáo nhanh chống hạn / chống úng chỉ dành cho người dùng đã đăng nhập."
        />
      </div>
    </PageShell>
  );
}
