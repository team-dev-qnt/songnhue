import { Alert, App, Button, Empty, Space, theme, Typography } from 'antd';
import L from 'leaflet';
import { neutralColors, statusColors } from '@songnhue/design-tokens';
import { useEffect, useRef, useState } from 'react';

import { CONSTRUCTION_STATUS, CONSTRUCTION_TYPE } from '@/components/business/statusVocabulary';
import {
  type GisLayerView,
  type MapConfigView,
  type MapPointView,
  type StationMarkerView,
} from '@/shared/api-types';
import { ngayHomNay } from '@/shared/format';

import { chieuDai, dienTich, nhanChieuDai, nhanDienTich, type Diem } from './banDoDo';
import {
  bieuTuongCongTrinh,
  bieuTuongDiemDo,
  chamCongTrinh,
  chamDiemDo,
  coToaDo,
  popupDiemDo,
  thoat,
} from './constructionMapMarkers';
import { LoiXuatAnh, xuatAnhBanDo, type ChamVe } from './xuatAnhBanDo';

/**
 * Một lớp GIS đã tải xong nội dung, sẵn sàng vẽ.
 *
 * ⚠ `geojson` để kiểu `unknown` có chủ đích: nội dung do **người vận hành nạp**, và khai một kiểu
 * cụ thể ở đây là hứa một hình dạng mà ⛔ không ai kiểm được ở tầng này. `L.geoJSON` tự bỏ qua đối
 * tượng ⛔ không hợp lệ.
 */
export interface LopGisVe {
  view: GisLayerView;
  geojson: unknown;
}

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
  wall = false,
  lopGis = [],
  coCongCuDo = false,
  coXuatAnh = false,
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
  wall?: boolean;
  /**
   * Lớp bản đồ do người vận hành nạp — CN-02.4 / M2.9.
   *
   * ⛔ Nhận qua prop chứ ⛔ không tự gọi API: component này đã nhận `points`/`diemDo` theo cùng
   * cách, và để nó tự nạp thì mỗi nơi đặt bản đồ lại kéo theo một lượt gọi ⛔ không ai thấy.
   */
  lopGis?: LopGisVe[];
  /**
   * Bật công cụ đo khoảng cách / diện tích — M2.12.
   *
   * ⛔ Mặc định **TẮT**, và wall mode phải để nguyên: CN-02.5 nói rõ màn hình treo tường *"⛔ không
   * phụ thuộc thao tác chuột/bàn phím"*. Một công cụ cần bấm chuột trên màn hình ⛔ không ai chạm
   * vào là một nút ⛔ không dùng được, và tệ hơn — nó bắt được cú click ⛔ không chủ đích.
   */
  coCongCuDo?: boolean;
  /**
   * Bật nút **Xuất ảnh** — M2.13 vế 1 / T59.13.
   *
   * ⛔ Mặc định **TẮT**, cùng lý lẽ với `coCongCuDo`: wall mode ⛔ được có nút nào (CN-02.5), và
   * một nút mặc-định-bật sẽ tự mọc ra ở mọi nơi đặt bản đồ về sau mà ⛔ ai quyết định.
   */
  coXuatAnh?: boolean;
}) {
  const khungRef = useRef<HTMLDivElement>(null);
  const banDoRef = useRef<L.Map | null>(null);
  const lopMarkerRef = useRef<L.LayerGroup | null>(null);
  // ⛔ Lớp RIÊNG, ⛔ không trộn vào `lopMarkerRef`: hai lớp làm mới theo hai nhịp và theo hai lượt
  //    gọi API khác nhau. Dùng chung một layerGroup thì lượt vẽ lại của lớp này xoá mất lớp kia —
  //    triệu chứng là marker công trình biến mất mỗi lần số liệu thuỷ văn cập nhật.
  const lopDiemDoRef = useRef<L.LayerGroup | null>(null);
  // ⛔ Lớp GIS thêm TRƯỚC hai lớp kia nên nó nằm DƯỚI: một vùng ranh giới tô màu vẽ đè lên marker
  //    công trình là che mất đúng thứ bản đồ sinh ra để hiện.
  const lopGisRef = useRef<L.LayerGroup | null>(null);
  const lopDoRef = useRef<L.LayerGroup | null>(null);
  const [diemDo_Do, setDiemDoDo] = useState<Diem[]>([]);
  const [dangDo, setDangDo] = useState(false);
  const [dangXuat, setDangXuat] = useState(false);
  const { message } = App.useApp();
  const { token } = theme.useToken();

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
    lopGisRef.current = L.layerGroup().addTo(banDo);
    lopMarkerRef.current = L.layerGroup().addTo(banDo);
    lopDiemDoRef.current = L.layerGroup().addTo(banDo);
    lopDoRef.current = L.layerGroup().addTo(banDo);
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
      lopGisRef.current = null;
      lopDoRef.current = null;
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
      L.marker([diem.latitude, diem.longitude], { icon: bieuTuongCongTrinh(diem) })
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
      ...points
        .filter((d) => Number.isFinite(d.latitude) && Number.isFinite(d.longitude))
        .map((d): [number, number] => [d.latitude, d.longitude]),
      ...diemDo
        .filter(coToaDo)
        .map((d): [number, number] => [Number(d.latitude), Number(d.longitude)]),
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
    diemDo.filter(coToaDo).forEach((d) => {
      L.marker([Number(d.latitude), Number(d.longitude)], { icon: bieuTuongDiemDo(d) })
        .addTo(lop)
        .bindPopup(popupDiemDo(d));
    });
  }, [diemDo]);

  // ⭐ Lớp GIS do người vận hành nạp — M2.9. useEffect RIÊNG, cùng lý lẽ với lớp điểm đo.
  useEffect(() => {
    const lop = lopGisRef.current;
    if (!lop) {
      return;
    }
    lop.clearLayers();
    lopGis.forEach((l) => {
      // ⛔ `opacity` là phần trăm NGUYÊN 0–100 ở cả API lẫn CSDL; Leaflet nhận 0–1. Phép chia nằm
      //    ở ĐÚNG một chỗ — hai nơi cùng đổi đơn vị là chỗ để `0.8` và `80` lẫn vào nhau.
      const mo = l.view.opacity / 100;
      L.geoJSON(l.geojson as never, {
        style: () => ({ color: l.view.color, weight: 2, opacity: mo, fillOpacity: mo * 0.5 }),
        pointToLayer: (_f, latlng) =>
          L.circleMarker(latlng, { radius: 5, color: l.view.color, opacity: mo, fillOpacity: mo }),
      })
        .bindTooltip(l.view.name)
        .addTo(lop);
    });
  }, [lopGis]);

  // ⭐ Công cụ đo — M2.12. Vẽ lại đường/đa giác mỗi lần danh sách điểm đổi.
  useEffect(() => {
    const lop = lopDoRef.current;
    if (!lop) {
      return;
    }
    lop.clearLayers();
    if (diemDo_Do.length === 0) {
      return;
    }
    const toaDo = diemDo_Do.map((d): [number, number] => [d.lat, d.lng]);
    diemDo_Do.forEach((d) => L.circleMarker([d.lat, d.lng], { radius: 4 }).addTo(lop));
    if (diemDo_Do.length >= 3) {
      L.polygon(toaDo, { dashArray: '4 4' }).addTo(lop);
    } else if (diemDo_Do.length === 2) {
      L.polyline(toaDo, { dashArray: '4 4' }).addTo(lop);
    }
  }, [diemDo_Do]);

  // Bắt/ngắt lượt bấm khi bật/tắt công cụ đo.
  useEffect(() => {
    const banDo = banDoRef.current;
    if (!banDo || !dangDo) {
      return;
    }
    const bat = (e: L.LeafletMouseEvent) =>
      setDiemDoDo((truoc) => [...truoc, { lat: e.latlng.lat, lng: e.latlng.lng }]);
    banDo.on('click', bat);
    return () => {
      banDo.off('click', bat);
    };
  }, [dangDo]);

  /**
   * ⭐ M2.13 vế 1 — T59.13. Lượt xuất đọc **cùng dữ liệu nguồn** mà bản đồ đang vẽ (`points`,
   * `diemDo`, `lopGis`, `diemDo_Do`) và chiếu qua **cùng một** `latLngToContainerPoint`. ⛔ Đọc
   * ngược từ DOM của Leaflet: chỗ đặt các pane có phép biến hình CSS riêng, và một tấm ảnh lệch
   * vài chục pixel trông *gần đúng* — đúng lớp lỗi ⛔ ai phát hiện.
   */
  const xuatAnh = async () => {
    const banDo = banDoRef.current;
    if (!banDo || !config) {
      return;
    }
    setDangXuat(true);
    try {
      const chams: ChamVe[] = [
        ...points
          .filter((d) => Number.isFinite(d.latitude) && Number.isFinite(d.longitude))
          .map((d) => ({ lat: d.latitude, lng: d.longitude, cham: chamCongTrinh(d) })),
        ...diemDo.filter(coToaDo).map((d) => ({
          lat: Number(d.latitude),
          lng: Number(d.longitude),
          cham: chamDiemDo(d),
        })),
      ];
      await xuatAnhBanDo({
        banDo,
        cauHinh: config,
        chams,
        // ⛔ Cùng phép chia `/100` với lượt vẽ ở trên — `opacity` là phần trăm NGUYÊN ở API/CSDL.
        lopGis: lopGis.map((l) => ({
          geojson: l.geojson,
          mau: l.view.color,
          mo: l.view.opacity / 100,
        })),
        diemDo: diemDo_Do,
        // ⛔ ⛔ `new Date().toISOString()`: nó cho ngày **UTC**, nên một lượt xuất lúc 03:00 giờ VN
        //   đặt tên tệp theo NGÀY HÔM TRƯỚC. Đúng lớp lỗi T63.18, và luật ESLint chỉ bắt `dayjs()`
        //   trần nên đường này đi lọt — `ngayHomNay()` là lối đã chuẩn hoá.
        tenTep: `ban-do-cong-trinh-${ngayHomNay()}.png`,
        mauNen: token.colorBgContainer,
        mauVien: neutralColors.bgContainer,
        mauChu: token.colorText,
      });
    } catch (loi) {
      // ⛔ ⛔ Nuốt lỗi rồi im: một nút bấm xong ⛔ có gì xảy ra là thứ người dùng ⛔ báo lại được.
      message.error(
        loi instanceof LoiXuatAnh ? loi.message : 'Không xuất được ảnh bản đồ. Thử lại sau.',
        8,
      );
    } finally {
      setDangXuat(false);
    }
  };

  if (!config) {
    return <Empty description="Chưa tải được cấu hình bản đồ" />;
  }

  const banDoHeight =
    typeof height === 'number' && points.length === 0 ? Math.max(260, height - 76) : height;

  return (
    <>
      {wall && (
        <style>{`
          .leaflet-dark-tiles .leaflet-tile-pane {
            filter: brightness(0.6) invert(1) contrast(3) hue-rotate(200deg) saturate(0.3) brightness(0.7);
          }
        `}</style>
      )}
      {points.length === 0 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 8 }}
          title="Chưa công trình nào được số hoá toạ độ"
          description="Bản đồ chỉ hiện công trình đã có kinh độ/vĩ độ. Số hồ sơ còn thiếu vị trí nằm ở ô KPI 'Chưa số hoá toạ độ'."
        />
      )}
      {/*
        ⭐ Thanh công cụ đo — M2.12. ⛔ Chỉ hiện khi `coCongCuDo`: wall mode phải để nguyên vì
           CN-02.5 nói màn hình treo tường *"⛔ không phụ thuộc thao tác chuột/bàn phím"*.
        ⚠ Số đo hiện NGAY trên thanh, ⛔ không trong popup: người đo cần thấy con số **trong lúc**
          bấm thêm điểm, ⛔ không phải sau khi bấm xong.
      */}
      {(coCongCuDo || coXuatAnh) && (
        <Space wrap style={{ marginBottom: 8 }}>
          {coCongCuDo && (
            <Button
              size="small"
              type={dangDo ? 'primary' : 'default'}
              onClick={() => {
                setDangDo((truoc) => !truoc);
                if (dangDo) {
                  setDiemDoDo([]);
                }
              }}
            >
              {dangDo ? 'Tắt công cụ đo' : 'Đo khoảng cách / diện tích'}
            </Button>
          )}
          {/*
            ⭐ M2.13 vế 1 — T59.13. Ảnh chụp ĐÚNG khung nhìn hiện tại: *"theo khu vực"* của đặc tả
              chính là vùng người dùng vừa kéo/phóng tới, ⛔ phải một ô chọn Xí nghiệp thứ hai —
              thêm một bộ lọc riêng cho lượt xuất là dựng hai khái niệm "khu vực" cạnh nhau.
          */}
          {coXuatAnh && (
            <Button size="small" loading={dangXuat} onClick={() => void xuatAnh()}>
              Xuất ảnh bản đồ (PNG)
            </Button>
          )}
          {coCongCuDo && dangDo && (
            <>
              <Button
                size="small"
                disabled={diemDo_Do.length === 0}
                onClick={() => setDiemDoDo([])}
              >
                Xoá điểm
              </Button>
              <Typography.Text>
                {diemDo_Do.length} điểm · {nhanChieuDai(chieuDai(diemDo_Do))}
                {diemDo_Do.length >= 3 ? ` · ${nhanDienTich(dienTich(diemDo_Do))}` : ''}
              </Typography.Text>
              <Typography.Text type="secondary">
                Bấm lên bản đồ để thêm điểm. Số đo theo công thức cầu — sai số dưới 0,5%, dùng để
                ước lượng, không thay số liệu trắc địa.
              </Typography.Text>
            </>
          )}
        </Space>
      )}
      <div
        ref={khungRef}
        className={wall ? 'leaflet-dark-tiles' : undefined}
        style={{ width: '100%', height: banDoHeight, borderRadius: 6, overflow: 'hidden' }}
      />
    </>
  );
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
    `<span style="color:${neutralColors.textSecondary}">Mã: ${thoat(diem.code)} · ${thoat(loai)}</span><br/>`,
    `<span style="color:${neutralColors.textSecondary}">Đơn vị: ${thoat(diem.orgUnitName ?? '—')}</span><br/>`,
    `<span style="color:${mauTrangThai};font-weight:600">`,
    `${thoat(trangThai?.label ?? diem.operationalStatus)}</span>`,
    // ⚠ Dòng "Số liệu thuỷ văn: chưa đấu nối (Phase 2)" đã bị GỠ ở T35.1 — từ 04/09/2026 nó là một
    //    lời nói dối: số liệu thuỷ văn nay có lớp riêng trên chính bản đồ này. §10.69 — một dòng
    //    chữ hứa sai khó thấy hơn hẳn một dòng chữ không có.
  ].join('');
}
