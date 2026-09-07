import { Alert, Empty } from 'antd';
import L from 'leaflet';
import { mauMucCanhBao, statusColors } from 'design-tokens';
import { useEffect, useRef } from 'react';

import { CONSTRUCTION_STATUS, CONSTRUCTION_TYPE } from '@/components/business/statusVocabulary';
import { TRANG_THAI_TIN_HIEU, VAI_TRO_VI_TRI } from '@/features/hydro/hydroVocabulary';
import { type MapConfigView, type MapPointView, type StationMarkerView } from '@/shared/api-types';
import { formatDateTime } from '@/shared/format';

import 'leaflet/dist/leaflet.css';

/**
 * Bản đồ GIS tổng quan — T23.9 / CN-02.4, marker theo M2.10.
 *
 * <h3>⛔ Marker vẽ bằng `divIcon`, không dùng ảnh biểu tượng mặc định của Leaflet</h3>
 *
 * Ba lý do, và lý do thứ ba mới là lý do bắt buộc:
 *
 * <ol>
 *   <li>Biểu tượng mặc định của Leaflet nạp ảnh PNG bằng đường dẫn tương đối tính từ tệp
 *       CSS — với bản dựng có băm tên tệp thì đường đó trỏ vào hư không, và triệu chứng
 *       là marker biến mất trong khi bản đồ vẫn chạy. Đây là lỗi kinh điển của Leaflet
 *       trong bundler, và cách chữa phổ biến (vá `L.Icon.Default`) chỉ là né nó.
 *   <li>Màu marker phải theo **trạng thái**, mà ảnh PNG thì không đổi màu được — sẽ phải
 *       sinh sáu tệp ảnh cho sáu trạng thái, và chúng sẽ lệch khỏi bảng màu ngay lần đầu
 *       ai đó chỉnh một sắc độ.
 *   <li>{@code img-src} của CSP không cho phép ảnh ngoài; ảnh trong bản dựng thì được,
 *       nhưng vẽ bằng CSS thì <b>không cần đến `img-src` chút nào</b>.
 * </ol>
 *
 * <h3>⚠ Ảnh nền tile là nguồn ngoài — và CSP phải cho phép host đó</h3>
 *
 * URL tile đọc từ `settings` (đổi được không cần dựng lại ảnh), nhưng chỉ thị
 * {@code img-src} nằm ở nginx nên **không tự đi theo**. Đổi sang host khác mà quên mở CSP
 * thì trình duyệt chặn từng ô ảnh: bản đồ xám trơn, marker vẫn nổi lên trên, không lỗi
 * nào. `NginxSecurityHeadersTest` đối chiếu hai nơi đó ở CI.
 */
export function ConstructionMap({
  points,
  diemDo = [],
  config,
  height = 420,
}: {
  points: MapPointView[];
  /**
   * ⭐ **Lớp "Điểm đo thuỷ văn"** — T35.1. Mặc định rỗng để mọi nơi gọi cũ ⛔ không phải sửa.
   *
   * ⚠ Rỗng ở đây có **hai** nghĩa khác nhau mà component này ⛔ không phân biệt được: *chưa truyền
   * lớp* và *đã truyền, nhưng chưa điểm đo nào có toạ độ* (hôm nay là 19/19 — mục G8). Câu giải
   * thích thuộc về trang gọi, nơi biết mình vừa hỏi gì.
   */
  diemDo?: StationMarkerView[];
  config: MapConfigView | undefined;
  height?: number | string;
}) {
  const khungRef = useRef<HTMLDivElement>(null);
  const banDoRef = useRef<L.Map | null>(null);
  const lopMarkerRef = useRef<L.LayerGroup | null>(null);
  // ⛔ Lớp RIÊNG, ⛔ không trộn vào `lopMarkerRef`: hai lớp làm mới theo hai nhịp và theo hai lượt
  //    gọi API khác nhau. Dùng chung một layerGroup thì lượt vẽ lại của lớp này xoá mất lớp kia —
  //    triệu chứng là marker công trình biến mất mỗi lần số liệu thuỷ văn cập nhật.
  const lopDiemDoRef = useRef<L.LayerGroup | null>(null);

  // Dựng bản đồ một lần. `config` chỉ đọc ở lượt dựng đầu: đổi nguồn tile giữa chừng là
  // việc của người quản trị và có hiệu lực ở lượt tải trang sau — dựng lại cả bản đồ mỗi
  // lượt làm mới thì khung nhìn người dùng vừa kéo tới sẽ bị nhảy về chỗ cũ.
  useEffect(() => {
    if (!khungRef.current || !config || banDoRef.current) {
      return;
    }
    const banDo = L.map(khungRef.current, {
      center: [config.centerLat, config.centerLng],
      zoom: config.defaultZoom,
      // Bàn phím/chuột không dùng được ở chế độ màn hình lớn treo tường (CN-02.5 nói rõ
      // "không phụ thuộc thao tác chuột/bàn phím"), nhưng ở màn hình quản trị thì cần —
      // nên giữ mặc định và để wall mode tự khoá bằng CSS `pointer-events`.
      attributionControl: true,
    });
    L.tileLayer(config.tileUrl, {
      maxZoom: config.maxZoom,
      attribution: config.attribution,
    }).addTo(banDo);
    lopMarkerRef.current = L.layerGroup().addTo(banDo);
    lopDiemDoRef.current = L.layerGroup().addTo(banDo);
    banDoRef.current = banDo;

    // Leaflet đo kích thước lúc dựng; nằm trong thẻ co giãn thì lần đo đầu hay sai và
    // bản đồ hiện ra một mảng xám lệch. `invalidateSize` mỗi lần khung đổi bề rộng.
    const theoDoi = new ResizeObserver(() => banDo.invalidateSize());
    theoDoi.observe(khungRef.current);

    return () => {
      theoDoi.disconnect();
      banDo.remove();
      banDoRef.current = null;
      lopMarkerRef.current = null;
      lopDiemDoRef.current = null;
    };
  }, [config]);

  // Vẽ lại marker khi dữ liệu đổi.
  useEffect(() => {
    const lop = lopMarkerRef.current;
    const banDo = banDoRef.current;
    if (!lop || !banDo) {
      return;
    }
    lop.clearLayers();

    points.forEach((diem) => {
      // ⛔ Popup chưa có nút "Xem chi tiết" (M2.10 có yêu cầu): màn hình hồ sơ công trình
      // thuộc WS-21. Một nút dẫn tới route không tồn tại trông như chức năng hỏng, tệ hơn
      // hẳn so với việc chưa có nút.
      L.marker([diem.latitude, diem.longitude], { icon: bieuTuong(diem) })
        .addTo(lop)
        .bindPopup(noiDungPopup(diem));
    });

    // Khớp khung nhìn theo dữ liệu thật. Tâm mặc định trong `settings` chỉ để dùng khi
    // chưa công trình nào có toạ độ — bám vào nó khi đã có dữ liệu thì bản đồ sẽ trỏ sai
    // chỗ ngay lần đầu Công ty mở rộng địa bàn.
    //
    // ⚠ T35.1: khung nhìn tính CẢ hai lớp. Chỉ tính công trình thì một điểm đo nằm ngoài
    //    vùng công trình sẽ ở ngoài màn hình — và người dùng ⛔ không có cách nào biết nó
    //    tồn tại, vì marker duy nhất báo điều đó lại nằm ngoài khung.
    const toaDo: [number, number][] = [
      ...points.map((d): [number, number] => [d.latitude, d.longitude]),
      ...diemDo.map((d): [number, number] => [Number(d.latitude), Number(d.longitude)]),
    ];
    if (toaDo.length > 0) {
      banDo.fitBounds(L.latLngBounds(toaDo), { padding: [32, 32], maxZoom: 14 });
    }
  }, [points, diemDo]);

  // ⭐ Lớp điểm đo thuỷ văn — T35.1. useEffect RIÊNG: hai lớp đổi theo hai lượt gọi API khác nhau,
  //    gộp chung thì mỗi lượt làm mới số liệu thuỷ văn (2 phút) sẽ vẽ lại cả lớp công trình.
  useEffect(() => {
    const lop = lopDiemDoRef.current;
    if (!lop) {
      return;
    }
    lop.clearLayers();
    diemDo.forEach((d) => {
      L.marker([Number(d.latitude), Number(d.longitude)], { icon: bieuTuongDiemDo(d) })
        .addTo(lop)
        .bindPopup(popupDiemDo(d));
    });
  }, [diemDo]);

  if (!config) {
    return <Empty description="Chưa tải được cấu hình bản đồ" />;
  }

  return (
    <>
      {points.length === 0 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 8 }}
          message="Chưa công trình nào được số hoá toạ độ"
          description="Bản đồ chỉ hiện công trình đã có kinh độ/vĩ độ. Số hồ sơ còn thiếu vị trí nằm ở ô KPI 'Chưa số hoá toạ độ'."
        />
      )}
      <div ref={khungRef} style={{ width: '100%', height, borderRadius: 6, overflow: 'hidden' }} />
    </>
  );
}

/** Chấm tròn màu theo trạng thái, viền trắng để nổi trên mọi nền bản đồ. */
function bieuTuong(diem: MapPointView): L.DivIcon {
  const khoaMau = CONSTRUCTION_STATUS[diem.operationalStatus]?.color ?? 'unknown';
  const mau = statusColors[khoaMau];
  return L.divIcon({
    className: '',
    iconSize: [16, 16],
    iconAnchor: [8, 8],
    popupAnchor: [0, -8],
    html:
      `<span style="display:block;width:16px;height:16px;border-radius:50%;` +
      `background:${mau};border:2px solid #fff;box-shadow:0 0 0 1px rgba(0,0,0,.35)"></span>`,
  });
}

/**
 * Nội dung popup — M2.10: tên, mã, loại, Xí nghiệp, trạng thái.
 *
 * ⚠ Thoát HTML thủ công: dữ liệu là tên công trình do người dùng nhập, và Leaflet nhận
 * chuỗi HTML thô. Không thoát thì một cái tên chứa thẻ trở thành một lỗ XSS ở đúng chỗ ít
 * ai nghĩ tới — bản đồ.
 */
function noiDungPopup(diem: MapPointView): string {
  const loai = CONSTRUCTION_TYPE[diem.constructionType]?.label ?? diem.constructionType;
  const trangThai = CONSTRUCTION_STATUS[diem.operationalStatus];
  const mauTrangThai = statusColors[trangThai?.color ?? 'unknown'];

  return [
    `<strong>${thoat(diem.name)}</strong><br/>`,
    `<span style="color:#595959">Mã: ${thoat(diem.code)} · ${thoat(loai)}</span><br/>`,
    `<span style="color:#595959">Đơn vị: ${thoat(diem.orgUnitName ?? '—')}</span><br/>`,
    `<span style="color:${mauTrangThai};font-weight:600">`,
    `${thoat(trangThai?.label ?? diem.operationalStatus)}</span>`,
    // ⚠ Dòng "Số liệu thuỷ văn: chưa đấu nối (Phase 2)" đã bị GỠ ở T35.1 — từ 04/09/2026 nó là một
    //    lời nói dối: số liệu thuỷ văn nay có lớp riêng trên chính bản đồ này. §10.69 — một dòng
    //    chữ hứa sai khó thấy hơn hẳn một dòng chữ không có.
  ].join('');
}

/**
 * ⭐ Chấm **điểm đo thuỷ văn** — T35.1.
 *
 * <h3>Ba kênh thị giác, ba thông tin — ⛔ không chồng lên nhau</h3>
 *
 * <ul>
 *   <li><b>Hình</b> (vuông xoay 45° = quả trám) phân biệt <i>lớp</i>: điểm đo vs công trình (tròn).
 *       Người mù màu vẫn tách được hai lớp.
 *   <li><b>Màu</b> mang <i>mức cảnh báo</i> nếu đang có, ngược lại mang <i>trạng thái tín hiệu</i>.
 *   <li><b>Viền nét đứt</b> mang <i>chất lượng nghi ngờ</i>.
 * </ul>
 *
 * ⛔ Đừng dồn "nghi ngờ" vào màu: màu đã chở hai thứ rồi, và một kênh chở ba thông tin thì người
 * đọc ⛔ không tách ra được cái nào là cái nào.
 *
 * ⚠ Màu cảnh báo **thắng** màu trạng thái, có chủ đích: một trạm đang vượt ngưỡng thì việc nó còn
 * phát tín hiệu hay không là câu hỏi thứ hai.
 */
function bieuTuongDiemDo(d: StationMarkerView): L.DivIcon {
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
      `background:${mau};border:2px ${vien} #fff;box-shadow:0 0 0 1px rgba(0,0,0,.35)"></span>`,
  });
}

/**
 * ⭐ Popup điểm đo — và ⛔ chấm XÁM vẫn hiện giá trị cuối kèm lý do, đúng yêu cầu T35.1.
 *
 * Một trạm mất tín hiệu là đúng thứ bản đồ sinh ra để chỉ ra. Ẩn số cuối của nó đi thì người trực
 * ⛔ không biết nó dừng ở mức nào — mà đó chính là thông tin cần khi đi kiểm tra hiện trường.
 */
function popupDiemDo(d: StationMarkerView): string {
  const tt = TRANG_THAI_TIN_HIEU[d.trangThai];
  const vaiTro = VAI_TRO_VI_TRI[d.positionRole] ?? d.positionRole;
  const dong = [
    `<strong>${thoat(d.name)}</strong><br/>`,
    `<span style="color:#595959">Mã: ${thoat(d.code)} · ${thoat(vaiTro)}</span><br/>`,
  ];

  if (d.giaTri !== null) {
    dong.push(
      `<span style="font-size:15px;font-weight:700">${thoat(d.giaTri)} ${thoat(d.donVi ?? '')}</span>`,
      `<span style="color:#8c8c8c"> · ${thoat(d.tenChiSo ?? '')}</span><br/>`,
      `<span style="color:#8c8c8c;font-size:11px">Lúc ${thoat(formatDateTime(d.mocDo) || '—')}</span><br/>`,
    );
  } else {
    // ⛔ Quy tắc 16 ở tầng bản đồ: ô rỗng phải nói được VÌ SAO nó rỗng.
    dong.push(`<span style="color:#8c8c8c">Chưa có số đo hợp lệ nào</span><br/>`);
  }

  dong.push(
    `<span style="color:${statusColors[MAU_TIN_HIEU[d.trangThai]]};font-weight:600">`,
    `${thoat(tt?.label ?? d.trangThai)}</span>`,
  );
  if (d.trangThai === 'MAT_TIN_HIEU') {
    dong.push(`<span style="color:#8c8c8c"> — dữ liệu chưa cập nhật</span>`);
  }
  if (d.nghiNgo) {
    dong.push(`<br/><span style="color:#fa8c16">⚠ Bản ghi gần nhất bị đánh dấu nghi ngờ</span>`);
  }
  if (d.tenMucCanhBao) {
    dong.push(
      `<br/><span style="color:${mauMucCanhBao(d.khoaMauCanhBao)};font-weight:600">`,
      `⚠ ${thoat(d.tenMucCanhBao)}</span>`,
    );
  }
  return dong.join('');
}

/**
 * Trạng thái tín hiệu → khoá màu chung.
 *
 * ⛔ Bảng này ⛔ không khai mã hex — nó chỉ trỏ vào `statusColors`, để lớp GIS và badge trạng thái
 * trên bảng ⛔ không bao giờ lệch màu (nợ T25.23: 29 mã màu ghi cứng đã lọt vào admin-app).
 */
const MAU_TIN_HIEU: Record<StationMarkerView['trangThai'], keyof typeof statusColors> = {
  HOAT_DONG: 'normal',
  MAT_TIN_HIEU: 'unknown',
  CHUA_CO_DU_LIEU: 'unknown',
  NGUNG: 'inactive',
};

function thoat(gia: string): string {
  return gia
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
