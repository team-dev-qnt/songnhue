import type { Metadata } from 'next';
import { notFound } from 'next/navigation';

import { PageShell } from '@/components/PageShell';
import { BieuDoDienBienClient } from '@/components/charts/BieuDoDienBienClient';
import { getBieuDoCongTrinh, getSiteConfig } from '@/lib/api';
import { khoiVanHanhBat } from '@/lib/khoiVanHanh';
import { ROUTES, formatDateTime } from '@/lib/routes';

export const revalidate = 300;

interface Props {
  params: Promise<{ maCongTrinh: string }>;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { maCongTrinh } = await params;
  return {
    title: `Diễn biến mực nước ${decodeURIComponent(maCongTrinh)} - Thủy lợi Sông Nhuệ`,
    // ⛔ ⛔ Không hứa một con số nào ở đây: trang có thể mở ra khi công trình chưa có số đo, và
    //    một `description` khẳng định "12 mốc gần nhất" khi bảng rỗng là §10.54 ở tầng SEO.
    description: 'Diễn biến mực nước thượng lưu, hạ lưu và chênh lệch theo mốc đo 10 phút.',
  };
}

/**
 * Chi tiết một công trình — spec **§6.1.3**, mở từ nút `»»` của bảng §6.1.2.
 *
 * <p>Ba phần theo đúng đặc tả: <b>(a)</b> thông tin công trình · <b>(b)</b> biểu đồ diễn biến —
 * nội dung chính · <b>(c)</b> bảng số liệu chi tiết theo từng mốc.
 *
 * <p>⚠ §6.1.3 xếp (b) và phần khoảng thời gian của (c) vào nhóm <i>"yêu cầu đăng nhập"</i>. Yêu
 * cầu ấy đã bị <b>huỷ</b> — quyết định Q4 ngày 09/09/2026: dữ liệu thuỷ văn công khai toàn bộ,
 * ẩn/hiện đi qua công tắc quản trị. Nên trang này ⛔ không có tầng chặn nào.
 */
export default async function ChiTietCongTrinhPage({ params }: Props) {
  const { maCongTrinh } = await params;
  const [config, bieuDo] = await Promise.all([
    getSiteConfig(),
    getBieuDoCongTrinh(decodeURIComponent(maCongTrinh)),
  ]);

  // ⛔ Cùng một công tắc với bảng — tắt khối Vận hành là ẩn CẢ lối vào lẫn trang, ⛔ không để một
  //    trang con sống sót sau khi trang cha đã biến mất.
  if (!khoiVanHanhBat(config)) {
    notFound();
  }

  const ct = bieuDo?.congTrinh ?? null;
  const dong = ct?.dong ?? [];

  return (
    <PageShell
      title={ct ? `Diễn biến mực nước — ${ct.tenCongTrinh}` : 'Diễn biến mực nước'}
      description="Thượng lưu, hạ lưu và chênh lệch theo mốc đo 10 phút trong ngày."
      breadcrumb={[
        { label: 'Quản lý, vận hành' },
        { label: 'Mực nước, lượng mưa', href: ROUTES.quanLyVanHanh.mucNuocLuongMua },
        { label: ct?.tenCongTrinh ?? 'Chi tiết' },
      ]}
    >
      {bieuDo === null ? (
        <p className="rounded-xl border border-surface-border bg-white p-6 text-center text-[13px] text-surface-textSecondary">
          Chưa lấy được số liệu. Trang sẽ hiện lại khi kết nối tới nguồn được khôi phục.
        </p>
      ) : (
        <>
          {/* (a) Thông tin công trình — §6.1.3 */}
          <section className="rounded-xl border border-surface-border bg-white p-5 shadow-xs">
            <h2 className="text-sm font-bold tracking-tight text-brand-primary">
              Thông tin công trình
            </h2>
            <dl className="mt-3 grid gap-x-6 gap-y-2 text-[13px] sm:grid-cols-2">
              <Muc nhan="Mã công trình" giaTri={ct?.maCongTrinh} />
              <Muc nhan="Tên công trình" giaTri={ct?.tenCongTrinh} />
              <Muc nhan="Lý trình" giaTri={ct?.lyTrinh} />
              <Muc
                nhan="Số liệu cập nhật lúc"
                giaTri={bieuDo.meta.lanLayCuoi ? formatDateTime(bieuDo.meta.lanLayCuoi) : null}
              />
            </dl>
            {/* ⛔ Ngưỡng báo động hiện ra ở đây khi CÓ — bảng `alert_levels` đang rỗng (chờ G9-a),
                nên khối này biến mất thay vì hiện một danh sách trống (quy tắc 16). */}
            {bieuDo.nguong.length > 0 && (
              <p className="mt-3 text-[12px] text-surface-textSecondary">
                Ngưỡng báo động:{' '}
                {bieuDo.nguong.map((n) => `${n.chiTieu} ${n.tenMuc} ${n.giaTri}`).join(' · ')}
              </p>
            )}
          </section>

          {/* (b) Biểu đồ — nội dung chính của màn hình, §7.1 */}
          <section className="mt-6 rounded-xl border border-surface-border bg-white p-5 shadow-xs">
            <h2 className="text-sm font-bold tracking-tight text-brand-primary">
              Biểu đồ diễn biến
            </h2>
            <div className="mt-3">
              <BieuDoDienBienClient bieuDo={bieuDo} />
            </div>
          </section>

          {/* (c) Bảng số liệu chi tiết — mỗi dòng một mốc, §6.1.3 */}
          {ct && (
            <section className="mt-6 rounded-xl border border-surface-border bg-white p-5 shadow-xs">
              <h2 className="text-sm font-bold tracking-tight text-brand-primary">
                Số liệu chi tiết
              </h2>
              <div className="mt-3 max-h-[420px] overflow-auto rounded-lg border border-surface-border">
                <table className="w-full border-collapse text-[12px]">
                  <thead className="sticky top-0 bg-surface-bgLayout">
                    <tr>
                      <th
                        scope="col"
                        className="px-3 py-2 text-left font-semibold text-surface-textBase"
                      >
                        Thời điểm
                      </th>
                      {dong.map((d) => (
                        <th
                          scope="col"
                          key={d.chiTieu}
                          className="px-3 py-2 text-right font-semibold text-surface-textBase"
                        >
                          {d.chiTieu} ({bieuDo.meta.donVi})
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-surface-border">
                    {bieuDo.moc.map((m, i) => (
                      <tr key={m}>
                        <td className="whitespace-nowrap px-3 py-1.5 text-surface-textSecondary">
                          {formatDateTime(m)}
                        </td>
                        {dong.map((d) => {
                          const o = d.o[i];
                          return (
                            <td
                              key={d.chiTieu}
                              title={o?.lyDo ?? o?.tenMucCanhBao ?? undefined}
                              className={`px-3 py-1.5 text-right tabular-nums ${
                                o?.chatLuong === 'NGHI_NGO'
                                  ? 'bg-amber-50 font-medium text-amber-900'
                                  : 'text-surface-textBase'
                              }`}
                            >
                              {/* ⛔ Ô thiếu số để TRỐNG — ⛔ không `0.00`, ⛔ không dấu gạch. */}
                              {o?.chatLuong === 'NGHI_NGO' && (
                                <span aria-hidden="true" className="mr-1">
                                  ⚠
                                </span>
                              )}
                              {o?.giaTri ?? <span aria-label={o?.lyDo ?? 'Không có dữ liệu'} />}
                            </td>
                          );
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </>
      )}
    </PageShell>
  );
}

/** ⛔ Ô thiếu giá trị để TRỐNG chứ ⛔ không hiện dấu gạch — hai trạng thái khác nhau (quy tắc 16). */
function Muc({ nhan, giaTri }: { nhan: string; giaTri?: string | null }) {
  return (
    <div>
      <dt className="text-surface-textSecondary">{nhan}</dt>
      <dd className="font-medium text-surface-textBase">{giaTri}</dd>
    </div>
  );
}
