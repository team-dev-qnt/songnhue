import type { Metadata } from 'next';

import { PageShell } from '@/components/PageShell';
import { RealtimeFrame } from '@/components/realtime/RealtimeFrame';
import { getServerTime, getSiteConfig } from '@/lib/api';
import { ROUTES } from '@/lib/routes';
import { docSo } from '@/lib/settings';
import { KhoaDangNhap } from '@/components/realtime/KhoaDangNhap';

export const revalidate = 300;

export const metadata: Metadata = {
  title: 'Mực nước, lượng mưa - Thủy lợi Sông Nhuệ',
  description:
    'Mực nước và lượng mưa tại 10 cống trên trục chính hệ thống thủy lợi Sông Nhuệ, cập nhật theo giờ truy cập.',
  alternates: { canonical: ROUTES.quanLyVanHanh.mucNuocLuongMua },
};

/**
 * Quản lý, vận hành &gt; **Mực nước, lượng mưa** — CR-13, CR-14, CR-33, §5.2.
 *
 * <h2>Trạng thái thật: chưa có nguồn dữ liệu — trả lời OI-01</h2>
 *
 * Module MOD-03 (Quản lý dữ liệu thủy văn) <b>chưa được dựng</b>: thư mục {@code hydro/} của
 * backend mới chỉ có khai báo gói và một service rỗng. Nguồn {@code songnhue.bhh40.net} đã
 * khảo sát xong nhưng chưa có poller nào chạy, và ba giới hạn của nguồn đã đo được:
 *
 * <ul>
 *   <li><b>không có API lượng mưa</b> — nguồn chỉ có {@code getmn.aspx} (mực nước);
 *   <li><b>không có API lịch sử</b> — tham số ngày bị bỏ qua, nên dữ liệu quá khứ chỉ có nếu
 *       chính hệ này đã ghi lại từ trước;
 *   <li>API phủ <b>19 điểm đo</b>, ít hơn biểu tổng hợp giấy Công ty đang dùng.
 * </ul>
 *
 * <p>Điều đó có nghĩa: cột "lượng mưa" của §5.2 <b>không tự động hoá được</b> bằng nguồn hiện
 * tại, và bảng theo TUẦN / THÁNG của CR-14 chỉ có dữ liệu kể từ ngày poller chạy lần đầu. Hai
 * điểm này cần Công ty biết trước khi nghiệm thu, nên chúng nói ra ở đây thay vì nằm trong một
 * ghi chú kỹ thuật.
 *
 * <h2>Phần "sau đăng nhập" của CR-14 — chưa dựng, và chưa giả vờ là đã dựng</h2>
 *
 * CR-14 và §6 đòi số liệu theo tuần/tháng chỉ xem được sau khi đăng nhập, và §2 nói rõ *"phân
 * quyền phải xử lý ở tầng route/API, không chỉ ẩn/hiện ở giao diện"*. Cổng công khai hiện
 * <b>không có tầng xác thực nào</b> — thêm nó là CR-08, một quyết định kiến trúc riêng.
 *
 * <p>⛔ Nên trang này KHÔNG dựng một nút "Đăng nhập" dẫn tới hư không, và cũng không dựng sẵn
 * bảng tuần/tháng rồi ẩn bằng CSS. Ẩn ở giao diện là đúng thứ §2 cấm, và nó tạo ra <i>ảo giác
 * đã phân quyền</i> — loại sai nguy hiểm hơn hẳn một ô nói thẳng là chưa có.
 */
export default async function MucNuocLuongMuaPage() {
  const [config, serverTime] = await Promise.all([getSiteConfig(), getServerTime()]);
  const nhipLamMoi = docSo(config?.['site.home.realtime.refresh-seconds'], 300);

  return (
    <PageShell
      title="Mực nước, lượng mưa"
      description="Số liệu tại giờ truy cập của 10 cống trên trục chính. Theo dõi theo tuần và tháng yêu cầu đăng nhập."
      breadcrumb={[{ label: 'Quản lý, vận hành' }, { label: 'Mực nước, lượng mưa' }]}
    >
      <section className="rounded-xl border border-surface-border bg-white p-5 shadow-xs">
        <h2 className="text-sm font-bold tracking-tight text-brand-primary">
          Số liệu tại giờ truy cập
        </h2>
        <div className="mt-4">
          <RealtimeFrame
            updatedAt={serverTime}
            refreshSeconds={nhipLamMoi}
            unavailable
            unavailableReason="Mô-đun Quản lý dữ liệu thủy văn (MOD-03) chưa được đấu nối. Danh sách 10 cống trên trục chính cũng đang chờ Công ty chốt."
          />
        </div>
      </section>

      <div className="mt-6">
        <KhoaDangNhap
          tieuDe="Theo dõi theo tuần và tháng"
          moTa="Bảng và biểu đồ diễn biến mực nước theo tuần, tháng chỉ dành cho người dùng đã đăng nhập."
        />
      </div>
    </PageShell>
  );
}
