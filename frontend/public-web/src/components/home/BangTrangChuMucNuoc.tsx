import { alertLevelColors } from 'design-tokens';

import type { CongTrinhLuoi, LuoiMucNuoc, OLuoi } from '@/lib/api';

interface BangTrangChuMucNuocProps {
  luoi: LuoiMucNuoc;
}

/**
 * Khối "Mực nước, lượng mưa" của **trang chủ** — spec §5.2. **T44.8**.
 *
 * <h2>⛔⛔ Vì sao mỗi dòng là một CÔNG TRÌNH, ⛔ không phải một điểm đo</h2>
 *
 * §5.2 khai hai cột **riêng biệt**: *Mực nước thượng lưu (m)* và *Mực nước hạ lưu (m)*. Nếu mỗi
 * dòng là một điểm đo thì **một trong hai cột ấy luôn rỗng ở mọi dòng** — đúng thứ bảng cũ đang
 * làm, và nó khiến một bảng đủ dữ liệu trông như một bảng hỏng một nửa.
 *
 * Bảng của Công ty (*"Biểu tổng hợp"*, đo 09/09/2026) cũng xếp TL/HL **cạnh nhau dưới một tên
 * cống**. Đây là hình dạng người dùng đang quen đọc.
 *
 * <h2>⚠ Một đánh đổi có tên: lý do ô rỗng hẹp hơn bảng cũ</h2>
 *
 * `PublicHydroService` phân biệt được ba lý do một ô rỗng (*chưa gửi số nào* · *mất tín hiệu* ·
 * *chưa có nguồn lượng mưa*) vì nó đọc `hydro_latest` và suy `StationDisplayStatus`. Bảng này đọc
 * lưới theo **mốc**, nên nó chỉ nói được *"⛔ không có số tại mốc này"*.
 *
 * <p>Câu ấy **đúng** — với cửa sổ một mốc 10 phút, một trạm im lặng ba ngày và một trạm vừa lỡ một
 * khung cho ra cùng một sự thật: mốc này ⛔ không có số. Nhưng nó **kém thông tin hơn** bản cũ, và
 * đó là một khoản nợ có tên (T44.9), ⛔ không phải một chỗ quên.
 *
 * <p>⛔ Đổi lại: cổng nay có **một** nguồn sự thật cho mực nước. Hai bảng đọc hai endpoint là chỗ
 * hai con số về cùng một mực nước lệch nhau, và chúng sẽ lệch đúng vào ngày có sự cố.
 */
export function BangTrangChuMucNuoc({ luoi }: BangTrangChuMucNuocProps) {
  if (luoi.tuyenSong.length === 0) {
    return (
      <p className="px-3.5 py-6 text-center text-[13px] text-surface-textSecondary">
        {luoi.lyDoTrong}
      </p>
    );
  }

  const thoiDiem = luoi.moc[0];

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[680px] border-collapse text-[12px]">
        <caption className="sr-only">
          Mực nước và lượng mưa theo tuyến sông tại mốc đo gần nhất
        </caption>
        <thead>
          <tr className="bg-surface-bgLayout text-left">
            <th scope="col" className="px-3 py-2 font-semibold text-surface-textBase">
              Tuyến sông
            </th>
            <th scope="col" className="px-3 py-2 font-semibold text-surface-textBase">
              Công trình
            </th>
            <th scope="col" className="px-3 py-2 font-semibold text-surface-textBase">
              Lý trình
            </th>
            <th scope="col" className="px-3 py-2 text-right font-semibold text-surface-textBase">
              Thượng lưu ({luoi.meta.donVi})
            </th>
            <th scope="col" className="px-3 py-2 text-right font-semibold text-surface-textBase">
              Hạ lưu ({luoi.meta.donVi})
            </th>
            <th scope="col" className="px-3 py-2 text-right font-semibold text-surface-textBase">
              Chênh lệch ({luoi.meta.donVi})
            </th>
            <th scope="col" className="px-3 py-2 text-right font-semibold text-surface-textBase">
              MN sông / Bể hút ({luoi.meta.donVi})
            </th>
            <th scope="col" className="px-3 py-2 text-right font-semibold text-surface-textBase">
              Lượng mưa (mm)
            </th>
            <th scope="col" className="px-3 py-2 font-semibold text-surface-textBase">
              Thời điểm đo
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-surface-border">
          {luoi.tuyenSong.map((nhom) =>
            nhom.congTrinh.map((ct, i) => (
              <tr key={`${nhom.tenTuyen}-${ct.maCongTrinh}`}>
                {/* Gộp ô cột Tuyến sông — §5.2 "Gộp ô theo nhóm tuyến". */}
                {i === 0 && (
                  <th
                    scope="rowgroup"
                    rowSpan={nhom.congTrinh.length}
                    className="border-r border-surface-border px-3 py-2 text-left align-top font-semibold text-surface-textBase"
                  >
                    {nhom.tenTuyen}
                  </th>
                )}
                <td className="px-3 py-2 font-medium text-surface-textBase">{ct.tenCongTrinh}</td>
                <td className="px-3 py-2 text-surface-textSecondary">{ct.lyTrinh}</td>
                <OTrangChu o={oCua(ct, 'Thượng lưu')} />
                <OTrangChu o={oCua(ct, 'Hạ lưu')} />
                <OTrangChu o={oCua(ct, 'Chênh lệch')} nhat />
                {/* ⚠ 5/14 công trình ⛔ KHÔNG có cặp thượng/hạ lưu — trạm thuỷ văn đo `MN sông`,
                    trạm bơm đo `MN Bể hút`. Thiếu cột này thì năm dòng ấy trống trơn. */}
                <OTrangChu o={oCua(ct, 'MN sông') ?? oCua(ct, 'MN Bể hút')} />
                {/* ⛔ Cột lượng mưa TRỐNG kèm lý do của BACKEND — ⛔ không `?? 0`, ⛔ không `—`
                    trần: "0 mm" là một khẳng định về thời tiết và nó sai mỗi ngày trời mưa
                    (§10.54, mục G3-a). */}
                <td
                  title={luoi.meta.lyDoLuongMua ?? undefined}
                  className="px-3 py-2 text-right text-surface-textSecondary"
                >
                  <span aria-label={luoi.meta.lyDoLuongMua ?? 'Chưa có nguồn lượng mưa'} />
                </td>
                <td className="whitespace-nowrap px-3 py-2 text-surface-textSecondary">
                  {thoiDiem ? gioNgay(thoiDiem) : null}
                </td>
              </tr>
            )),
          )}
        </tbody>
      </table>
    </div>
  );
}

/**
 * Ô ở mốc gần nhất của một chỉ tiêu.
 *
 * ⛔ Trả `null` khi công trình ⛔ không có chỉ tiêu ấy — ví dụ một trạm thuỷ văn chỉ đo *MN sông*
 * thì ⛔ không có thượng lưu. Đó khác với *"có chỉ tiêu mà mốc này ⛔ không có số"*, nên hai tình
 * huống hiện hai kiểu: ô ⛔ không áp dụng để **trống hẳn**, ô thiếu số mang **tooltip lý do**.
 */
function oCua(ct: CongTrinhLuoi, chiTieu: string): OLuoi | null {
  return ct.dong.find((d) => d.chiTieu === chiTieu)?.o[0] ?? null;
}

function OTrangChu({ o, nhat = false }: { o: OLuoi | null; nhat?: boolean }) {
  const nen = nhat ? 'bg-surface-bgLayout/60 italic text-surface-textSecondary' : '';

  if (o === null) {
    // ⛔ Chỉ tiêu ⛔ không tồn tại ở công trình này — ⛔ không phải một số bị thiếu, nên ⛔ không
    //    tooltip. Một tooltip "không có dữ liệu" ở đây sẽ nói rằng đáng lẽ phải có.
    return <td className={`px-3 py-2 text-right ${nen}`} />;
  }
  if (o.giaTri === null) {
    return (
      <td
        title={o.lyDo ?? undefined}
        className={`px-3 py-2 text-right text-surface-textSecondary ${nen}`}
      >
        <span aria-label={o.lyDo ?? 'Không có dữ liệu'} />
      </td>
    );
  }

  const nghiNgo = o.chatLuong === 'NGHI_NGO';
  const mau =
    !nghiNgo && o.khoaMauCanhBao
      ? (alertLevelColors as Record<string, string>)[o.khoaMauCanhBao]
      : undefined;

  return (
    <td
      title={
        nghiNgo ? (o.lyDo ?? 'Số liệu nghi ngờ, chờ kiểm tra') : (o.tenMucCanhBao ?? undefined)
      }
      style={mau ? { backgroundColor: mau } : undefined}
      className={`whitespace-nowrap px-3 py-2 text-right tabular-nums ${nen} ${
        nghiNgo ? 'bg-amber-50 font-medium text-amber-900' : 'text-surface-textBase'
      }`}
    >
      {nghiNgo && (
        <span aria-hidden="true" className="mr-1">
          ⚠
        </span>
      )}
      {o.giaTri}
      {nghiNgo && <span className="sr-only"> — số liệu nghi ngờ</span>}
      {!nghiNgo && o.tenMucCanhBao && <span className="sr-only"> — {o.tenMucCanhBao}</span>}
    </td>
  );
}

/** `HH:mm dd/MM/yyyy` giờ Việt Nam — định dạng §5.2 đòi. */
function gioNgay(moc: string): string {
  const d = new Date(moc);
  const gio = new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(d);
  const ngay = new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(d);
  return `${gio} ${ngay}`;
}

export const _testTrangChu = { oCua, gioNgay };
