import type { Metadata } from 'next';
import { notFound } from 'next/navigation';

import { PageShell } from '@/components/PageShell';
import { SectionNav } from '@/components/SectionNav';
import { ColumnHeaderRow } from '@/components/home/ColumnHeaderRow';
import { WaterLevelRows } from '@/components/home/WaterLevelRows';
import { BangLuoiMucNuoc } from '@/components/home/BangLuoiMucNuoc';
import { RealtimeFrame } from '@/components/realtime/RealtimeFrame';
import { BE_RONG_TOI_THIEU_MUC_NUOC, COT_MUC_NUOC, LUOI_MUC_NUOC } from '@/lib/homeDataColumns';
import { getServerTime, getSiteConfig, getWaterLevelGrid, getWaterLevels } from '@/lib/api';
import { khoiVanHanhBat } from '@/lib/khoiVanHanh';
import { ROUTES, formatDateTime } from '@/lib/routes';
import { docSo } from '@/lib/settings';

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
  const [config, serverTime, mucNuoc, luoi] = await Promise.all([
    getSiteConfig(),
    getServerTime(),
    getWaterLevels(),
    getWaterLevelGrid('PHUT', 12),
  ]);

  if (!khoiVanHanhBat(config)) {
    notFound();
  }

  const nhipLamMoi = docSo(config?.['site.home.realtime.refresh-seconds'], 300);

  return (
    <PageShell
      title="Mực nước, lượng mưa"
      description="Số liệu tại giờ truy cập và diễn biến 12 mốc đo gần nhất của các điểm đo đang hoạt động."
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
                  luoi={LUOI_MUC_NUOC}
                  beRongToiThieu={BE_RONG_TOI_THIEU_MUC_NUOC}
                />
                <WaterLevelRows
                  rows={mucNuoc}
                  luoi={LUOI_MUC_NUOC}
                  beRongToiThieu={BE_RONG_TOI_THIEU_MUC_NUOC}
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

      {/* ⭐ WS-44 — bảng lưới §6.1.2: nhóm theo tuyến sông, mỗi công trình một cặp thượng/hạ lưu
          kèm dòng Chênh lệch tự tính.

          ⛔ Ô `KhoaDangNhap` từng đứng ở đây đã được GỠ: quyết định Q4 ngày 09/09/2026 huỷ CR-08 và
             công bố dữ liệu thuỷ văn công khai toàn bộ. Giữ lại một ô chữ nói về một quyết định đã
             bị huỷ là để cổng tự mô tả sai chính nó. Việc ẩn/hiện nay đi qua công tắc quản trị
             (`khoiVanHanh`), ⛔ không qua một tầng xác thực. */}
      <section className="mt-6 rounded-xl border border-surface-border bg-white p-5 shadow-xs">
        <h2 className="text-sm font-bold tracking-tight text-brand-primary">
          Diễn biến 12 mốc đo gần nhất
        </h2>
        <p className="mt-1 text-[12px] text-surface-textSecondary">
          Biểu tổng hợp theo tuyến sông — mỗi công trình một cặp thượng lưu và hạ lưu.
        </p>
        <div className="mt-4 overflow-hidden rounded-lg border border-surface-border">
          {luoi === null ? (
            <p className="px-3.5 py-6 text-center text-[13px] text-surface-textSecondary">
              Chưa lấy được số liệu diễn biến. Bảng sẽ hiện lại khi kết nối tới nguồn được khôi
              phục.
            </p>
          ) : (
            <BangLuoiMucNuoc luoi={luoi} />
          )}
        </div>
        {/* ⛔ Mốc lấy từ `meta.lanLayCuoi` của BACKEND, ⛔ không phải đồng hồ máy khách: nguồn chết
            ba ngày thì `new Date()` vẫn nhảy số mới mỗi lượt F5 (khuyết tật T43.9). */}
        {luoi?.meta.lanLayCuoi && (
          <p className="mt-3 text-[11px] text-surface-textSecondary">
            Số liệu cập nhật lúc {formatDateTime(luoi.meta.lanLayCuoi)}
            {luoi.meta.trangThaiNguon === 'DEGRADED' &&
              ' — nguồn đang chậm, đây là số liệu gần nhất còn hợp lệ'}
          </p>
        )}
      </section>
      <SectionNav duongDan={ROUTES.quanLyVanHanh.mucNuocLuongMua} />
    </PageShell>
  );
}
