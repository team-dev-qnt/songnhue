import { Alert, Empty } from 'antd';
import L from 'leaflet';
import { statusColors } from 'design-tokens';
import { useEffect, useRef } from 'react';

import { CONSTRUCTION_STATUS, CONSTRUCTION_TYPE } from '@/components/business/statusVocabulary';
import { type MapConfigView, type MapPointView } from '@/shared/api-types';

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
  config,
  height = 420,
}: {
  points: MapPointView[];
  config: MapConfigView | undefined;
  height?: number | string;
}) {
  const khungRef = useRef<HTMLDivElement>(null);
  const banDoRef = useRef<L.Map | null>(null);
  const lopMarkerRef = useRef<L.LayerGroup | null>(null);

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
    if (points.length > 0) {
      banDo.fitBounds(L.latLngBounds(points.map((d) => [d.latitude, d.longitude])), {
        padding: [32, 32],
        maxZoom: 14,
      });
    }
  }, [points]);

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
    `${thoat(trangThai?.label ?? diem.operationalStatus)}</span><br/>`,
    `<span style="color:#8c8c8c;font-size:11px">Số liệu thuỷ văn: chưa đấu nối (Phase 2)</span>`,
  ].join('');
}

function thoat(gia: string): string {
  return gia
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
