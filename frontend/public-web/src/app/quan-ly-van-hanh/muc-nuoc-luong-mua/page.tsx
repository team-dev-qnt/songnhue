import type { Metadata } from 'next';
import { notFound } from 'next/navigation';

import { PageShell } from '@/components/PageShell';
import { SectionNav } from '@/components/SectionNav';
import { ColumnHeaderRow } from '@/components/home/ColumnHeaderRow';
import { WaterLevelRows } from '@/components/home/WaterLevelRows';
import { RealtimeFrame } from '@/components/realtime/RealtimeFrame';
import { COT_MUC_NUOC } from '@/lib/homeDataColumns';
import { getServerTime, getSiteConfig, getWaterLevels } from '@/lib/api';
import { khoiVanHanhBat } from '@/lib/khoiVanHanh';
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
 * <h2>⭐⭐ 04/09/2026 — ĐÃ CÓ NGUỒN DỮ LIỆU THẬT (T35.7)</h2>
 *
 * Trả lời <b>OI-01</b>: MOD-03 đã dựng, poller {@code songnhue.bhh40.net} đang chạy, và bảng dưới
 * đây đọc {@code hydro_latest} qua {@code GET /api/v1/public/hydro/muc-nuoc}.
 *
 * <p>⚠ Nhưng <b>ba giới hạn của nguồn vẫn còn nguyên</b>, và trang này phải nói ra chúng — chúng
 * ⛔ không biến mất chỉ vì đường dữ liệu đã thông:
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
 *
 * <h2>⭐ 04/09: trang này TẮT ĐƯỢC từ màn hình quản trị</h2>
 *
 * Cùng một công tắc với khối Nhóm 2 trên trang chủ — xem {@code lib/khoiVanHanh.ts}. Đây là trang
 * chịu ảnh hưởng rõ nhất: chừng nào MOD-03 chưa cấp số liệu, Công ty tắt công tắc là ẩn được cả
 * lối vào lẫn trang, thay vì để một khung "chưa có dữ liệu" đứng trên cổng.
 *
 * <p>⚠ {@code config} dùng lại lượt gọi đã có sẵn ngay dưới — ⛔ không gọi lượt thứ hai chỉ để
 * hỏi một cờ.
 */
export default async function MucNuocLuongMuaPage() {
  const [config, serverTime, mucNuoc] = await Promise.all([
    getSiteConfig(),
    getServerTime(),
    getWaterLevels(),
  ]);

  if (!khoiVanHanhBat(config)) {
    notFound();
  }

  const nhipLamMoi = docSo(config?.['site.home.realtime.refresh-seconds'], 300);

  return (
    <PageShell
      title="Mực nước, lượng mưa"
      description="Số liệu tại giờ truy cập của các điểm đo đang hoạt động. Theo dõi theo tuần và tháng yêu cầu đăng nhập."
      breadcrumb={[{ label: 'Quản lý, vận hành' }, { label: 'Mực nước, lượng mưa' }]}
    >
      <section className="rounded-xl border border-surface-border bg-white p-5 shadow-xs">
        <h2 className="text-sm font-bold tracking-tight text-brand-primary">
          Số liệu tại giờ truy cập
        </h2>
        {/* ⚠ Trang này dùng LẠI đúng hai component của trang chủ — một bộ cột, một cách hiển thị ô
            rỗng. Dựng bảng thứ hai ở đây là mở đường cho hai con số khác nhau về cùng một mực
            nước, và chúng sẽ lệch nhau đúng vào ngày có sự cố. */}
        <div className="mt-4">
          <RealtimeFrame
            updatedAt={serverTime}
            refreshSeconds={nhipLamMoi}
            unavailable={mucNuoc === null}
            unavailableReason="Chưa lấy được số liệu mực nước. Số liệu sẽ hiện lại khi kết nối tới nguồn được khôi phục."
          >
            {mucNuoc !== null && mucNuoc.length > 0 ? (
              <div className="overflow-hidden rounded-lg border border-surface-border">
                <ColumnHeaderRow
                  cot={COT_MUC_NUOC}
                  luoi="grid-cols-[1.1fr_1.7fr_0.9fr_1fr_1fr_0.95fr_1.1fr_0.9fr]"
                  beRongToiThieu="min-w-[920px]"
                />
                <WaterLevelRows
                  rows={mucNuoc}
                  luoi="grid-cols-[1.1fr_1.7fr_0.9fr_1fr_1fr_0.95fr_1.1fr_0.9fr]"
                  beRongToiThieu="min-w-[920px]"
                />
              </div>
            ) : (
              <p className="px-3.5 py-6 text-center text-[13px] text-surface-textSecondary">
                Chưa điểm đo nào đang hoạt động để công bố số liệu.
              </p>
            )}
          </RealtimeFrame>
        </div>
      </section>

      <div className="mt-6">
        <KhoaDangNhap
          tieuDe="Theo dõi theo tuần và tháng"
          moTa="Bảng và biểu đồ diễn biến mực nước theo tuần, tháng chỉ dành cho người dùng đã đăng nhập."
        />
      </div>
      <SectionNav duongDan={ROUTES.quanLyVanHanh.mucNuocLuongMua} />
    </PageShell>
  );
}
