import L from 'leaflet';
import { alertLevelColors, mauMucCanhBao, neutralColors, statusColors } from 'design-tokens';

import { TRANG_THAI_TIN_HIEU, VAI_TRO_VI_TRI } from '@/features/hydro/hydroVocabulary';
import { type StationMarkerView } from '@/shared/api-types';
import { formatDateTime } from '@/shared/format';

/**
 * **Cách vẽ chấm điểm đo trên bản đồ điều hành** — tách khỏi `ConstructionMap.tsx` ở T28.45.
 *
 * ## Vì sao tách ra một tệp `.ts`
 *
 * Ba lý do, và cả ba đều đo được:
 *
 * 1. **⛔ Chưa bài kiểm nào chạy một dòng nào của chúng.** `operationsDashboard.test.tsx`
 *    `vi.mock('@/components/dashboard/ConstructionMap')` — nó thay cả component bằng một `<div/>`,
 *    nên bốn màu, viền nét đứt, nội dung popup và hàm chống XSS ⛔ chưa từng được chạy. Lý do mock
 *    là chính đáng (Leaflet đo bố cục thật, jsdom trả kích thước 0) — nhưng nó kéo theo cả phần
 *    ⛔ không cần DOM.
 * 2. ESLint `react-refresh/only-export-components` ở mức **lỗi** với `--max-warnings=0`, nên tệp
 *    `.tsx` xuất component ⛔ không được xuất thêm hàm thường. Không tách thì ⛔ không kiểm được.
 * 3. **Mười lượt** mã màu ghi cứng (bốn giá trị: xám phụ, xám nhạt, cam, trắng) nay đọc từ
 *    `design-tokens` ngay trong lượt chuyển — nợ T25.23 **giảm** thay vì đi theo.
 *    ⚠ Chính javadoc này từng liệt kê bốn mã ấy ra và làm phép đếm **tăng thêm 4**: bộ canh soi
 *    toàn tệp, ⛔ không phân biệt mã trong mã nguồn với mã trong chú thích. Kể chuyện bằng TÊN, ⛔
 *    đừng kể bằng giá trị.
 *
 * ## ⚠ Ba kênh thị giác, ba thông tin — ⛔ không chồng lên nhau
 *
 * - **Hình** (quả trám vs tròn) phân biệt *lớp*: điểm đo vs công trình. Người mù màu vẫn tách được.
 * - **Màu** mang *mức cảnh báo* nếu đang có, ngược lại mang *trạng thái tín hiệu*.
 * - **Viền nét đứt** mang *chất lượng nghi ngờ*.
 *
 * ⛔ Đừng dồn "nghi ngờ" vào màu: màu đã chở hai thứ rồi.
 */

/**
 * Trạng thái tín hiệu → khoá màu chung.
 *
 * <h3>⭐ T28.45 — `MAT_TIN_HIEU` TÁCH khỏi `CHUA_CO_DU_LIEU`</h3>
 *
 * Hai trạng thái này từng cùng về `unknown` (xám), trong khi backend **cố ý tách chúng**
 * (`StationDisplayStatus`): một trạm *mất tín hiệu* là trạm đang chết và cần người đi xem; một trạm
 * *chưa có dữ liệu* là trạm vừa khai, hoàn toàn bình thường. Cùng một sắc xám thì hai câu chuyện ấy
 * ⛔ không phân biệt được — và người trực nhìn tường 4K thì ⛔ không mở popup.
 *
 * ⚠ `warning` (hổ phách) trùng sắc với mức cảnh báo 1. Đó là **chấp nhận có ý thức**: màu cảnh báo
 * **thắng** màu tín hiệu (xem {@link bieuTuongDiemDo}), nên một chấm hổ phách chỉ có thể là *mất
 * tín hiệu* khi trạm ấy ⛔ không có cảnh báo nào đang mở. Hai nghĩa ⛔ không bao giờ xuất hiện cùng
 * lúc trên một chấm.
 *
 * ⛔ Bảng này ⛔ không khai mã hex — nó chỉ trỏ vào `statusColors`, để lớp GIS và badge trạng thái
 * trên bảng ⛔ không bao giờ lệch màu.
 */
export const MAU_TIN_HIEU: Record<StationMarkerView['trangThai'], keyof typeof statusColors> = {
  HOAT_DONG: 'normal',
  MAT_TIN_HIEU: 'warning',
  CHUA_CO_DU_LIEU: 'unknown',
  NGUNG: 'inactive',
};

/**
 * Thoát HTML thủ công.
 *
 * ⚠ Leaflet `bindPopup()` nhận **chuỗi HTML thô**, và dữ liệu ở đây là tên công trình / tên điểm đo
 * do người dùng nhập. ⛔ Không thoát thì một cái tên chứa thẻ trở thành lỗ XSS ở đúng chỗ ít ai nghĩ
 * tới — bản đồ.
 *
 * ⚠ Dấu nháy đơn ⛔ **không** được thoát, và đó là an toàn *có điều kiện*: mọi thuộc tính trong các
 * template dưới đây dùng nháy KÉP. Đổi một chỗ sang nháy đơn là mở lại lỗ — `constructionMapMarkers
 * .test.ts` có bài canh đúng điều đó.
 */
export function thoat(gia: string): string {
  return gia
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/** Chấm điểm đo — quả trám 15px. Màu cảnh báo **thắng** màu trạng thái, có chủ đích. */
export function bieuTuongDiemDo(d: StationMarkerView): L.DivIcon {
  const mau = d.khoaMauCanhBao
    ? mauMucCanhBao(d.khoaMauCanhBao)
    : statusColors[MAU_TIN_HIEU[d.trangThai]];
  const vien = d.nghiNgo ? 'dashed' : 'solid';
  return L.divIcon({
    className: '',
    iconSize: [15, 15],
    iconAnchor: [7.5, 7.5],
    popupAnchor: [0, -8],
    html:
      `<span style="display:block;width:15px;height:15px;transform:rotate(45deg);` +
      `background:${mau};border:2px ${vien} ${neutralColors.bgContainer};` +
      `box-shadow:0 0 0 1px rgba(0,0,0,.35)"></span>`,
  });
}

/**
 * Popup điểm đo — và ⛔ chấm mất tín hiệu **vẫn hiện giá trị cuối kèm lý do**.
 *
 * Một trạm mất tín hiệu là đúng thứ bản đồ sinh ra để chỉ ra. Ẩn số cuối của nó đi thì người trực
 * ⛔ không biết nó dừng ở mức nào — mà đó chính là thông tin cần khi đi kiểm tra hiện trường.
 *
 * ⚠⚠ Hai con số trong popup đến từ **hai truy vấn khác nhau** ở backend
 * (`StationMapRepository`): cờ `nghiNgo` lấy theo `last_seen_at` (*"trạm còn phát không"*), còn
 * `giaTri` lấy theo `valid_measured_at` (*"số hợp lệ gần nhất"*). Đó là **chủ ý**, ⛔ không phải
 * lỗi — một trạm chỉ gửi số nghi ngờ thì vẫn đang phát. Hệ quả cần biết: với điểm đo có hai loại
 * chỉ số, viền nét đứt có thể đến từ chỉ số A trong khi số hiện ra là của chỉ số B. Dòng
 * *"Bản ghi gần nhất bị đánh dấu nghi ngờ"* nói ra điều đó bằng chữ, vì cái viền ⛔ không nói được.
 */
export function popupDiemDo(d: StationMarkerView): string {
  const tt = TRANG_THAI_TIN_HIEU[d.trangThai];
  const vaiTro = VAI_TRO_VI_TRI[d.positionRole] ?? d.positionRole;
  const phu = neutralColors.textSecondary;
  const nhat = statusColors.unknown;
  const dong = [
    `<strong>${thoat(d.name)}</strong><br/>`,
    `<span style="color:${phu}">Mã: ${thoat(d.code)} · ${thoat(vaiTro)}</span><br/>`,
  ];

  if (d.giaTri !== null) {
    dong.push(
      `<span style="font-size:15px;font-weight:700">${thoat(d.giaTri)} ${thoat(d.donVi ?? '')}</span>`,
      `<span style="color:${nhat}"> · ${thoat(d.tenChiSo ?? '')}</span><br/>`,
      `<span style="color:${nhat};font-size:11px">Lúc ${thoat(formatDateTime(d.mocDo) || '—')}</span><br/>`,
    );
  } else {
    // ⛔ Quy tắc 16 ở tầng bản đồ: ô rỗng phải nói được VÌ SAO nó rỗng.
    dong.push(`<span style="color:${nhat}">Chưa có số đo hợp lệ nào</span><br/>`);
  }

  dong.push(
    `<span style="color:${statusColors[MAU_TIN_HIEU[d.trangThai]]};font-weight:600">`,
    `${thoat(tt?.label ?? d.trangThai)}</span>`,
  );
  if (d.trangThai === 'MAT_TIN_HIEU') {
    dong.push(`<span style="color:${nhat}"> — dữ liệu chưa cập nhật</span>`);
  }
  if (d.nghiNgo) {
    dong.push(
      `<br/><span style="color:${alertLevelColors['alert-level-2']}">` +
        `⚠ Bản ghi gần nhất bị đánh dấu nghi ngờ</span>`,
    );
  }
  if (d.tenMucCanhBao) {
    dong.push(
      `<br/><span style="color:${mauMucCanhBao(d.khoaMauCanhBao)};font-weight:600">`,
      `⚠ ${thoat(d.tenMucCanhBao)}</span>`,
    );
  }
  return dong.join('');
}
