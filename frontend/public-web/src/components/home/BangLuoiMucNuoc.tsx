import type { CongTrinhLuoi, DongChiSo, LuoiMucNuoc, OLuoi } from '@/lib/api';

interface BangLuoiMucNuocProps {
  luoi: LuoiMucNuoc;
}

/**
 * Bảng lưới mực nước — **WS-44**, spec §5.2 (nhóm tuyến sông) và §6.1.2 (2–3 dòng/công trình).
 *
 * <h2>⛔⛔ Vì sao đây là `<table>` chứ ⛔ không phải `grid` bằng `div`</h2>
 *
 * Bảng cũ ({@link WaterLevelRows}) là một lưới CSS gồm các `div`, và §6.1.2 đòi **gộp ô**: một
 * công trình chiếm 2–3 dòng nhưng 3 cột đầu chỉ hiện **một lần**. `rowSpan` là thuộc tính của
 * `<td>`; trên `div` nó ⛔ **không tồn tại**, và mô phỏng bằng `grid-row: span` thì mọi ô phải tự
 * biết vị trí tuyệt đối của mình — một hàng thêm vào giữa làm lệch toàn bộ phần dưới, im lặng.
 *
 * ⇒ Đây là **viết lại**, ⛔ không phải sửa. Đo 09/09: `rowSpan|colSpan` xuất hiện **0 lần** trên
 * bảng dữ liệu trong toàn frontend.
 *
 * <h2>⛔ Ba trạng thái ô, ba cách hiện — ⛔ không gộp thành một dấu gạch</h2>
 *
 * <ul>
 *   <li><b>có số</b> — hiện số, 2 chữ số thập phân;
 *   <li><b>có số nhưng NGHI_NGO</b> — hiện số, nền vàng, dấu ⚠, tooltip mang lý do. ⛔ Đừng ẩn
 *       nó: ẩn một ô nghi ngờ biến nó thành ô trống, mà trống ⛔ không phân biệt được với
 *       "trạm ⛔ không gửi số" (quy tắc 16);
 *   <li><b>⛔ không có số</b> — ô <b>trống</b> + tooltip nói vì sao. ⛔ Cấm `0.00`: spec §6.2 ghi
 *       thẳng <i>"⛔ không được hiển thị 0.0 cho ô mất dữ liệu — hai trạng thái này khác nhau về
 *       nghiệp vụ"</i>.
 * </ul>
 *
 * <h2>⚠ Bề rộng — bài học WS-39</h2>
 *
 * Cổng tham chiếu của Công ty rộng **1336px** (`<table width=1336px>`, đo 09/09). Khung của ta
 * hẹp hơn, nên ⛔ **không chép số cột của họ**: số mốc do tầng gọi quyết định, và bảng cuộn ngang
 * trong <b>vùng cuộn của chính nó</b> — thân trang ⛔ không được cuộn ngang. Ba cột đầu dính lại
 * khi cuộn (§6.1.2 sticky column).
 */
export function BangLuoiMucNuoc({ luoi }: BangLuoiMucNuocProps) {
  // ⛔ Backend ép "hoặc CÓ nhóm, hoặc CÓ lý do trống" ở hàm dựng — nên nhánh này luôn có câu chữ
  //    thật để hiện, ⛔ không phải một chuỗi dự phòng bịa ra ở đây (§10.54).
  if (luoi.tuyenSong.length === 0) {
    return (
      <p className="px-3.5 py-6 text-center text-[13px] text-surface-textSecondary">
        {luoi.lyDoTrong}
      </p>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px] border-collapse text-[12px]">
        <caption className="sr-only">
          Bảng mực nước theo tuyến sông — mỗi công trình một cặp thượng lưu và hạ lưu
        </caption>

        <thead>
          {/* Tiêu đề 2 tầng của §6.1.2: tầng trên gom các mốc dưới một nhãn chung. */}
          <tr className="bg-surface-bgLayout">
            <th
              scope="col"
              rowSpan={2}
              className="sticky left-0 z-20 bg-surface-bgLayout px-3 py-2 text-left font-semibold text-surface-textBase"
            >
              Tuyến sông
            </th>
            <th
              scope="col"
              rowSpan={2}
              className="px-3 py-2 text-left font-semibold text-surface-textBase"
            >
              Công trình
            </th>
            <th
              scope="col"
              rowSpan={2}
              className="px-3 py-2 text-left font-semibold text-surface-textBase"
            >
              Chỉ tiêu
            </th>
            <th
              scope="col"
              colSpan={luoi.moc.length}
              className="border-l border-surface-border px-3 py-2 text-center font-semibold text-surface-textBase"
            >
              Mực nước ({luoi.meta.donVi})
            </th>
          </tr>
          <tr className="bg-surface-bgLayout">
            {luoi.moc.map((m) => (
              <th
                scope="col"
                key={m}
                className="whitespace-nowrap border-l border-surface-border px-3 py-1.5 text-right text-[11px] font-medium text-surface-textSecondary"
              >
                {gioPhut(m)}
              </th>
            ))}
          </tr>
        </thead>

        <tbody className="divide-y divide-surface-border">
          {luoi.tuyenSong.map((nhom) =>
            nhom.congTrinh.map((ct, viTriCt) =>
              ct.dong.map((dong, viTriDong) => (
                <tr key={`${nhom.tenTuyen}-${ct.maCongTrinh}-${dong.chiTieu}`}>
                  {/* Gộp ô cột Tuyến sông: chỉ hiện ở dòng ĐẦU TIÊN của công trình ĐẦU TIÊN. */}
                  {viTriCt === 0 && viTriDong === 0 && (
                    <th
                      scope="rowgroup"
                      rowSpan={demDong(nhom.congTrinh)}
                      className="sticky left-0 z-10 border-r border-surface-border bg-white px-3 py-2 text-left align-top font-semibold text-surface-textBase"
                    >
                      {nhom.tenTuyen}
                    </th>
                  )}

                  {/* Gộp ô cột Công trình: chỉ hiện ở dòng đầu của chính công trình ấy. */}
                  {viTriDong === 0 && (
                    <th
                      scope="rowgroup"
                      rowSpan={ct.dong.length}
                      className="px-3 py-2 text-left align-top font-normal text-surface-textBase"
                    >
                      <span className="font-semibold">{ct.tenCongTrinh}</span>
                      {/* ⛔ Lý trình NULL là G8 chưa về — ⛔ không hiện chuỗi rỗng có dấu ngoặc. */}
                      {ct.lyTrinh && (
                        <span className="mt-0.5 block text-[11px] text-surface-textSecondary">
                          {ct.lyTrinh}
                        </span>
                      )}
                    </th>
                  )}

                  <td
                    className={`px-3 py-2 text-left ${
                      dong.loai === 'TINH'
                        ? 'bg-surface-bgLayout/60 italic text-surface-textSecondary'
                        : 'text-surface-textBase'
                    }`}
                  >
                    {dong.chiTieu}
                  </td>

                  {dong.o.map((o, i) => (
                    <O key={luoi.moc[i]} o={o} tinh={dong.loai === 'TINH'} />
                  ))}
                </tr>
              )),
            ),
          )}
        </tbody>
      </table>
    </div>
  );
}

/**
 * Một ô số liệu.
 *
 * ⚠ `title` mang lý do — đó là tooltip mà §6.1.2 đòi cho ô nghi ngờ và ô thiếu dữ liệu. Nó ⛔ không
 * thay được một nhãn nhìn thấy được, nên ô nghi ngờ **còn có** dấu ⚠ và nền vàng: một tooltip chỉ
 * hiện khi người ta đã nghi ngờ đủ để rê chuột vào.
 */
function O({ o, tinh }: { o: OLuoi; tinh: boolean }) {
  const nen = tinh ? 'bg-surface-bgLayout/60 italic' : '';

  if (o.giaTri === null) {
    return (
      <td
        title={o.lyDo ?? undefined}
        className={`border-l border-surface-border px-3 py-2 text-right text-surface-textSecondary ${nen}`}
      >
        {/* ⛔ Ô TRỐNG, ⛔ không phải `0.00` và ⛔ không phải `—`: cả hai đều là một câu khẳng định. */}
        <span aria-label={o.lyDo ?? 'Không có dữ liệu'} />
      </td>
    );
  }

  const nghiNgo = o.chatLuong === 'NGHI_NGO';
  return (
    <td
      title={nghiNgo ? (o.lyDo ?? 'Số liệu nghi ngờ, chờ kiểm tra') : undefined}
      className={`whitespace-nowrap border-l border-surface-border px-3 py-2 text-right tabular-nums ${nen} ${
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
    </td>
  );
}

/** Tổng số dòng của một nhóm tuyến sông — chính là `rowSpan` của ô Tuyến sông. */
function demDong(congTrinh: CongTrinhLuoi[]): number {
  return congTrinh.reduce((tong: number, ct: CongTrinhLuoi) => tong + ct.dong.length, 0);
}

/**
 * Nhãn cột: `HH:mm` giờ Việt Nam.
 *
 * ⛔ Backend gửi mốc UTC (quy tắc 1). Đổi múi giờ **tường minh** bằng `timeZone` chứ ⛔ không dựa
 * vào múi giờ của trình duyệt: cổng này phục vụ người ở Việt Nam, và một người mở nó từ nước ngoài
 * ⛔ không được thấy một trục thời gian lệch mấy tiếng so với biểu của Công ty.
 */
function gioPhut(moc: string): string {
  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(moc));
}

/** ⚠ Dùng ở `BangLuoiMucNuoc` — giữ ở đây để bài kiểm gọi được mà ⛔ không phải dựng cả bảng. */
export const _test = { demDong, gioPhut };

export type { DongChiSo };
