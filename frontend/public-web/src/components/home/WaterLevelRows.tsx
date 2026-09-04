import type { WaterLevelRow } from '@/lib/api';
import { formatDateTime } from '@/lib/routes';

interface WaterLevelRowsProps {
  rows: WaterLevelRow[];
  /** Lớp `grid-cols-[…]` — phải TRÙNG với lớp truyền cho `ColumnHeaderRow`, nếu không cột lệch. */
  luoi: string;
  /** `min-w-[…]` — cũng phải trùng, vì hai khối cuộn ngang trong cùng một khung. */
  beRongToiThieu: string;
}

/**
 * Các dòng dữ liệu của bảng **"Mực nước, lượng mưa"** — CR-13 · CR-33 · CN-03.4, 8 cột. **T35.7**.
 *
 * <h2>⛔⛔ Đây là chỗ bài học nặng nhất của dự án nằm (§10.54)</h2>
 *
 * Bản đầu của khối này (trước 29/08) có **5 trạm quan trắc viết cứng**, kèm mực nước và một mức
 * "Cảnh báo BĐ I" gắn **tên cống có thật**, kèm chấm "live" nhấp nháy. Tất cả đều **bịa**, và
 * chúng đã lên staging — không ai nhìn ra đường dữ liệu đã chết, vì một trang đầy trông như một
 * trang đang chạy. Hàng tiêu đề cột sau đó được dựng với ranh giới ghi rõ: *"được dựng tên cột,
 * cấm dựng dòng"*.
 *
 * <p>Nay có endpoint thật đứng sau, và phần thêm vào là **đúng phần dòng**. ⛔ Không mảng
 * `DEFAULT_*`, ⛔ không `rows.length >= n ? rows : [...rows, ...BIA]`. `noFabricatedContent.test.ts`
 * quét toàn cây và phải **vẫn xanh** — nghĩa là mọi con số ở đây đến từ `props`, ⛔ không từ một
 * hằng số nào trong tệp này.
 *
 * <h2>⛔ Ô rỗng phải nói được VÌ SAO nó rỗng — quy tắc 16</h2>
 *
 * Ba tình huống cho ra một ô trống, và chúng cần ba câu khác nhau:
 *
 * <ul>
 *   <li><b>Chưa gửi số liệu nào</b> — điểm đo vừa khai, chưa tới lượt polling đầu tiên;
 *   <li><b>Mất tín hiệu</b> — trạm đã im lặng quá ngưỡng. ⚠ Backend cố ý ⛔ KHÔNG gửi số cuối ra
 *       cổng: một mực nước của mười ngày trước hiện ở cột "hiện tại" là thứ người ta ra quyết định
 *       dựa vào;
 *   <li><b>Lượng mưa</b> — ⛔ chưa có nguồn, vĩnh viễn cho tới khi mục <b>G3-a</b> được chốt.
 * </ul>
 *
 * ⛔ Một dấu `—` trần thì người đọc ⛔ không phân biệt được ba tình huống ấy, và cả ba đều trông
 * như "hệ thống hỏng".
 */
export function WaterLevelRows({ rows, luoi, beRongToiThieu }: WaterLevelRowsProps) {
  return (
    <div className="overflow-x-auto">
      <div className={`${beRongToiThieu} divide-y divide-surface-border`}>
        {rows.map((row) => (
          <div key={row.maDiemDo} className={`grid ${luoi} items-center`}>
            <div className="px-3.5 py-2.5 text-[12px] text-surface-textBase">
              {/* ⛔ "Chưa phân tuyến" đến TỪ BACKEND, không phải một chuỗi dự phòng ở đây: tuyến
                  sông là mục G8 và backend là nơi duy nhất biết vì sao nó trống. */}
              {row.tuyenSong}
            </div>

            <div className="px-3.5 py-2.5">
              <span className="text-[13px] font-semibold text-surface-textBase">
                {row.tenDiemDo}
              </span>
              <span className="mt-0.5 block text-[11px] text-surface-textSecondary">
                {row.maDiemDo}
              </span>
            </div>

            <div className="px-3.5 py-2.5 text-[12px] text-surface-textSecondary">
              {row.lyTrinh ?? <ORong lyDo="Lý trình chưa có (mục G8)" />}
            </div>

            <OSo giaTri={row.mucNuocThuongLuu} donVi={row.donVi} lyDo={row.lyDoTrong} />
            <OSo giaTri={row.mucNuocHaLuu} donVi={row.donVi} lyDo={row.lyDoTrong} />

            <div className="px-3.5 py-2.5 text-right text-[13px]">
              {/* ⛔⛔ LUÔN rỗng (G3-a). ⛔ Đừng `?? 0` — `0 mm` là một khẳng định về thời tiết. */}
              {row.luongMua === null ? (
                <ORong lyDo={row.lyDoLuongMua ?? 'Chưa có nguồn lượng mưa'} />
              ) : (
                `${row.luongMua} mm`
              )}
            </div>

            <div className="px-3.5 py-2.5 text-[12px] text-surface-textSecondary">
              {formatDateTime(row.thoiDiemDo) || <ORong lyDo="Chưa có lượt đo nào" />}
            </div>

            <div className="px-3.5 py-2.5 text-[12px]">
              {row.chatLuong === null ? (
                <ORong lyDo="Chưa có số đo để đánh giá chất lượng" />
              ) : (
                <span className="font-semibold text-status-normal">Hợp lệ</span>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/** Ô số liệu — số thật, hoặc ô rỗng KÈM LÝ DO. ⛔ Không có nhánh thứ ba. */
function OSo({
  giaTri,
  donVi,
  lyDo,
}: {
  giaTri: string | null;
  donVi: string | null;
  lyDo: string | null;
}) {
  return (
    <div className="px-3.5 py-2.5 text-right">
      {giaTri === null ? (
        <ORong lyDo={lyDo ?? 'Chưa có số đo'} />
      ) : (
        <span className="text-[14px] font-bold text-surface-textBase">
          {giaTri}
          <span className="ml-1 text-[11px] font-normal text-surface-textSecondary">
            {donVi ?? ''}
          </span>
        </span>
      )}
    </div>
  );
}

/**
 * ⛔ Ô rỗng **kèm lý do** — quy tắc 16 ở tầng hiển thị.
 *
 * ⚠ Lý do đi vào `title` (chuột) **và** vào DOM qua `sr-only`: bản in và trình đọc màn hình
 * ⛔ không có tooltip, và ở đó ô rỗng sẽ trở lại thành một dấu gạch vô nghĩa.
 */
function ORong({ lyDo }: { lyDo: string }) {
  return (
    <span className="cursor-help text-surface-textSecondary" title={lyDo}>
      <span aria-hidden="true">—</span>
      <span className="sr-only">{lyDo}</span>
    </span>
  );
}
