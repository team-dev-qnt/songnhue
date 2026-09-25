import type L from 'leaflet';

import { type ChamBanDo } from './constructionMapMarkers';

/**
 * **Xuất ảnh bản đồ ra tệp PNG — M2.13 vế 1 (T59.13).**
 *
 * ## ⛔⛔ Vì sao ⛔ dùng một thư viện chụp DOM — và đây là một phép ĐO, ⛔ phải một sở thích
 *
 * CSP của ảnh quản trị (`deploy/docker/admin-app.Dockerfile`) khai:
 *
 * - `img-src 'self' data: blob: https://tile.openstreetmap.org https://*.tile.openstreetmap.org`
 * - `connect-src 'self'`
 *
 * ⇒ Một lượt `fetch()`/`XMLHttpRequest` tới host tile **bị chặn**, trong khi `new Image()` đi qua
 * được. Mọi bộ chụp DOM phổ thông (`html2canvas`, `dom-to-image`) đọc ảnh chéo nguồn bằng
 * fetch/XHR hoặc qua một proxy — cả hai đường đều đâm vào `connect-src 'self'`. Nên ở đây thêm
 * một phụ thuộc ⛔ **mua được gì**: nó sẽ hỏng vì đúng lý do mà mã tự viết né được.
 *
 * ## ⛔⛔ Và vì sao ⛔ đặt `crossOrigin` lên lớp tile ĐANG HIỆN
 *
 * Cách ngắn nhất để canvas ⛔ bị nhiễm là khai `crossOrigin: 'anonymous'` ngay ở
 * `L.tileLayer(...)`. ⛔ Với một host tile ⛔ trả `Access-Control-Allow-Origin`, trình duyệt **bỏ
 * hẳn** ảnh ấy ⇒ bản đồ xám trơn. Mà `ops.map.tile-url` là một khoá `settings` **người vận hành
 * sửa được**. Đổi một tính năng phụ (xuất ảnh) lấy rủi ro làm hỏng tính năng chính (bản đồ) là
 * đúng thứ T59.10 đã trả giá. ⇒ Lượt xuất tự tải **bộ ảnh riêng** của nó; bản đồ trên màn hình
 * ⛔ đổi một dòng nào, và host ⛔ mở CORS thì chỉ **nút xuất** hỏng, kèm một câu nói rõ vì sao.
 *
 * Đo 25/09/2026 trên host đang dùng — kèm đối chứng để phép đo phân biệt được hai trạng thái:
 * `tile.openstreetmap.org` trả `access-control-allow-origin: *`; một host ảnh khác
 * (`www.google.com/favicon.ico`) trả **0** dòng header ấy.
 *
 * ## ⛔⛔ Dòng ghi nguồn phải NẰM TRONG ảnh
 *
 * Bản đồ trên màn hình có `attributionControl: true`, nhưng một tệp PNG thì rời khỏi màn hình và
 * đi vào báo cáo. Giấy phép ODbL của OpenStreetMap đòi ghi nguồn **đi cùng** bản trích. Một bản
 * xuất bỏ dòng ấy ⛔ phải thiếu thẩm mỹ — nó là một bản trích **⛔ đúng giấy phép**, và ⛔ ai phát
 * hiện cho tới lúc tấm ảnh đã nằm trong một văn bản gửi đi.
 *
 * ## ⚠ Phạm vi — thứ tấm ảnh này ⛔ có, và vì sao (luật 28)
 *
 * Có: ảnh nền, lớp GIS người vận hành nạp, đường/vùng của công cụ đo, chấm công trình, chấm điểm
 * đo, dòng ghi nguồn. **⛔ có**: popup (chúng chỉ mở từng cái một theo cú bấm) và khung chú giải
 * của Leaflet.
 *
 * ⚠ Ảnh ra ở **1×**, ⛔ phải 2× như {@code xuatSoDo}: nguồn tile chuẩn của OSM ⛔ có bản `@2x`, nên
 * 2× chỉ là phóng to một ảnh 1× — trông nét hơn mà ⛔ thêm một chi tiết nào, và tốn gấp bốn lượt
 * tải của một dịch vụ miễn phí.
 */

/** Một ô ảnh nền cần tải, kèm chỗ đặt nó trên canvas. */
export interface OTile {
  url: string;
  x: number;
  y: number;
}

/**
 * Mọi thứ cần để biết *phải tải những ô nào và đặt chúng ở đâu* — ⛔ phụ thuộc Leaflet lẫn DOM,
 * nên phần dễ sai nhất của lượt xuất kiểm được bằng một bài kiểm thuần.
 */
export interface KhungChup {
  /** Bề rộng khung bản đồ, pixel CSS. */
  rong: number;
  /** Chiều cao khung bản đồ, pixel CSS. */
  cao: number;
  zoom: number;
  /** Toạ độ pixel (hệ CRS, ở mức zoom này) của góc TRÊN–TRÁI khung — `map.getPixelBounds().min`. */
  gocX: number;
  gocY: number;
  /** Mẫu URL tile, ví dụ `https://tile.openstreetmap.org/{z}/{x}/{y}.png`. */
  mauUrl: string;
  /** Danh sách tên miền con cho chỗ cắm `{s}`. Mặc định của Leaflet là `abc`. */
  tenMien?: string;
  /** Cạnh một ô tile. 256 với hầu hết nguồn. */
  canhO?: number;
}

/**
 * Trần số ô một lượt xuất được phép tải.
 *
 * ⚠ ⛔ phải một con số phòng thân cho bộ nhớ — khung 4K cần chừng 127 ô và canvas chịu được. Nó là
 * một **ràng buộc của bên thứ ba**: chính sách dùng tile của OSM cấm tải hàng loạt. Một nút bấm
 * tay lấy vài chục ô là dùng bình thường; một lượt xuất vô tình quét hàng nghìn ô thì ⛔ phải.
 */
export const TRAN_SO_O = 150;

/** Lý do một lượt xuất ⛔ đi tới tệp — mỗi lý do dẫn tới một câu khác nhau trên màn hình. */
export type LyDoHongXuat =
  'QUA_NHIEU_O' | 'ANH_NEN_KHONG_TAI_DUOC' | 'CANVAS_BI_NHIEM' | 'TRINH_DUYET_KHONG_HO_TRO';

/**
 * ⚠ Mang **mã lý do** chứ ⛔ chỉ một câu chữ: nơi gọi phải phân biệt được *host tile ⛔ cho tải
 * chéo* với *khung quá rộng*, vì hai thứ ấy người dùng xử lý theo hai cách khác hẳn nhau.
 */
export class LoiXuatAnh extends Error {
  constructor(
    readonly lyDo: LyDoHongXuat,
    message: string,
  ) {
    super(message);
    this.name = 'LoiXuatAnh';
  }
}

/**
 * Danh sách ô ảnh nền phủ kín khung, kèm vị trí đặt.
 *
 * ⚠ **Cuộn vòng theo trục X, CẮT theo trục Y.** Kinh độ quấn quanh quả đất nên ô `x = -1` ở mức
 * zoom 3 chính là ô `x = 7`; vĩ độ thì ⛔ — phía trên cực Bắc ⛔ có ô nào, và hỏi nguồn một ô như
 * thế trả về 404 rồi hạ cả lượt xuất xuống nhánh *"⛔ tải được ảnh nền"*. Hai trục, hai luật.
 *
 * @throws LoiXuatAnh khi khung đòi quá {@link TRAN_SO_O} ô.
 */
export function oCanTai(k: KhungChup): OTile[] {
  const canh = k.canhO ?? 256;
  const tenMien = k.tenMien ?? 'abc';
  const soOMotChieu = 2 ** k.zoom;

  const tuX = Math.floor(k.gocX / canh);
  const denX = Math.floor((k.gocX + k.rong - 1) / canh);
  const tuY = Math.floor(k.gocY / canh);
  const denY = Math.floor((k.gocY + k.cao - 1) / canh);

  const soO = (denX - tuX + 1) * (denY - tuY + 1);
  if (soO > TRAN_SO_O) {
    throw new LoiXuatAnh(
      'QUA_NHIEU_O',
      `Khung bản đồ cần ${soO} ô ảnh nền, vượt trần ${TRAN_SO_O}. Thu nhỏ cửa sổ hoặc giảm mức phóng rồi xuất lại.`,
    );
  }

  const o: OTile[] = [];
  for (let ty = tuY; ty <= denY; ty += 1) {
    if (ty < 0 || ty >= soOMotChieu) {
      continue;
    }
    for (let tx = tuX; tx <= denX; tx += 1) {
      const xVong = ((tx % soOMotChieu) + soOMotChieu) % soOMotChieu;
      o.push({
        url: k.mauUrl
          .replace('{s}', tenMien[Math.abs(xVong + ty) % tenMien.length])
          .replace('{z}', String(k.zoom))
          .replace('{x}', String(xVong))
          .replace('{y}', String(ty))
          .replace('{r}', ''),
        x: tx * canh - k.gocX,
        y: ty * canh - k.gocY,
      });
    }
  }
  return o;
}

/** Một nét vẽ đã trải phẳng khỏi GeoJSON. `toaDo` giữ thứ tự **kinh độ trước** của RFC 7946. */
export interface Net {
  loai: 'diem' | 'duong' | 'vung';
  toaDo: [number, number][];
}

/**
 * Trải một GeoJSON bất kỳ thành danh sách nét vẽ.
 *
 * ⛔⛔ **Giữ nguyên thứ tự kinh-độ-trước của RFC 7946 §3.1.1** — đúng cái bẫy T59.14 vừa trả giá.
 * Phép hoán vị sang thứ tự vĩ-độ-trước mà Leaflet đòi nằm ở **đúng một chỗ** (nơi gọi
 * `latLngToContainerPoint`), vì đảo hai lần là ⛔ đảo, và một điểm Hà Nội (105, 21) hoá (21, 105)
 * rơi xuống Ấn Độ Dương mà ⛔ một dòng lỗi nào.
 *
 * ⚠ Nhận `unknown` có chủ đích: nội dung do **người vận hành nạp**, cùng lý lẽ với `LopGisVe`.
 * Thứ ⛔ nhận ra được thì bỏ qua — y như `L.geoJSON`, để tấm ảnh ⛔ bao giờ **nhiều hơn** bản đồ.
 */
export function traiHinhHoc(geojson: unknown): Net[] {
  const ra: Net[] = [];
  di(geojson);
  return ra;

  function di(nut: unknown): void {
    if (!nut || typeof nut !== 'object') {
      return;
    }
    const o = nut as Record<string, unknown>;
    switch (o.type) {
      case 'FeatureCollection':
        (Array.isArray(o.features) ? o.features : []).forEach(di);
        return;
      case 'Feature':
        di(o.geometry);
        return;
      case 'GeometryCollection':
        (Array.isArray(o.geometries) ? o.geometries : []).forEach(di);
        return;
      case 'Point':
        them('diem', [o.coordinates]);
        return;
      case 'MultiPoint':
        them('diem', o.coordinates);
        return;
      case 'LineString':
        them('duong', o.coordinates);
        return;
      case 'MultiLineString':
        (Array.isArray(o.coordinates) ? o.coordinates : []).forEach((d) => them('duong', d));
        return;
      case 'Polygon':
        // ⚠ Mọi vòng đều vẽ, kể cả vòng LỖ: một hồ nằm trong vùng quy hoạch là một đường viền
        //   thật trên bản đồ. Canvas ⛔ khoét lỗ được bằng một nét, nhưng vẽ đủ viền vẫn nói đúng
        //   hình dạng — còn bỏ vòng trong đi thì tấm ảnh khẳng định một vùng LIỀN mà thực tế ⛔.
        (Array.isArray(o.coordinates) ? o.coordinates : []).forEach((v) => them('vung', v));
        return;
      case 'MultiPolygon':
        (Array.isArray(o.coordinates) ? o.coordinates : []).forEach((dt) =>
          (Array.isArray(dt) ? dt : []).forEach((v) => them('vung', v)),
        );
        return;
      default:
    }
  }

  function them(loai: Net['loai'], tho: unknown): void {
    const toaDo = (Array.isArray(tho) ? tho : [])
      .filter(
        (c): c is number[] =>
          Array.isArray(c) &&
          c.length >= 2 &&
          Number.isFinite(Number(c[0])) &&
          Number.isFinite(Number(c[1])),
      )
      // ⛔ Cắt bỏ thành phần thứ ba (cao độ) — KML mang nó, bản đồ phẳng ⛔ dùng tới.
      .map((c): [number, number] => [Number(c[0]), Number(c[1])]);
    if (toaDo.length > 0) {
      ra.push({ loai, toaDo });
    }
  }
}

/**
 * Bỏ thẻ HTML khỏi dòng ghi nguồn.
 *
 * ⚠ `ops.map.tile-attribution` là một khoá `settings` **sửa được từ giao diện**, và Leaflet nhận
 * HTML ở ô ấy (`&copy; <a href=…>`). Canvas thì chỉ vẽ được chữ ⇒ phải bóc thẻ. Giá trị seed hôm
 * nay là chữ thuần, nhưng một khoá người ta sửa được ⛔ được phép giả định là chữ thuần.
 */
export function chuGhiNguon(html: string): string {
  return html
    .replace(/<[^>]*>/g, '')
    .replace(/&copy;/gi, '©')
    .replace(/&amp;/gi, '&')
    .replace(/&nbsp;/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Vẽ một chấm marker lên canvas theo ĐÚNG bản mô tả mà `divIcon` trên màn hình đang dùng. */
export function veCham(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  cham: ChamBanDo,
  mauVien: string,
): void {
  const r = cham.canh / 2;
  ctx.save();
  ctx.fillStyle = cham.mau;
  ctx.strokeStyle = mauVien;
  ctx.lineWidth = 2;
  ctx.setLineDash(cham.vien === 'dashed' ? [3, 2] : []);
  ctx.beginPath();
  if (cham.hinh === 'tron') {
    ctx.arc(x, y, r, 0, Math.PI * 2);
  } else {
    // Quả trám = hình vuông xoay 45°. ⛔ Vẽ thẳng bốn đỉnh thay vì `ctx.rotate`: xoay hệ trục rồi
    // quên trả lại là lỗi kinh điển, và ở đây bốn đỉnh viết ra còn ngắn hơn.
    ctx.moveTo(x, y - r);
    ctx.lineTo(x + r, y);
    ctx.lineTo(x, y + r);
    ctx.lineTo(x - r, y);
    ctx.closePath();
  }
  ctx.fill();
  ctx.stroke();
  ctx.restore();
}

/** Một chấm cần vẽ: vị trí địa lý + bản mô tả hình dạng dùng chung với lớp marker trên màn hình. */
export interface ChamVe {
  lat: number;
  lng: number;
  cham: ChamBanDo;
}

/** Một lớp GIS cần vẽ, với đúng bộ màu mà Leaflet đang dùng cho nó. */
export interface LopVe {
  geojson: unknown;
  mau: string;
  mo: number;
}

export interface YeuCauXuat {
  banDo: L.Map;
  cauHinh: { tileUrl: string; attribution: string };
  chams: ChamVe[];
  lopGis: LopVe[];
  /** Điểm của công cụ đo, theo thứ tự người dùng bấm. */
  diemDo: { lat: number; lng: number }[];
  tenTep: string;
  mauNen: string;
  mauVien: string;
  mauChu: string;
}

/**
 * Ghép và tải về tấm ảnh.
 *
 * @throws LoiXuatAnh — mọi nhánh hỏng đều **có tên**, vì một nút xuất im lặng nhả ra tệp hỏng tệ
 *     hơn hẳn một nút xuất báo rằng nó ⛔ làm được (quy tắc 16).
 */
export async function xuatAnhBanDo(y: YeuCauXuat): Promise<void> {
  const kich = y.banDo.getSize();
  const goc = y.banDo.getPixelBounds().min;
  if (!goc) {
    throw new LoiXuatAnh(
      'TRINH_DUYET_KHONG_HO_TRO',
      'Bản đồ chưa dựng xong, thử lại sau giây lát.',
    );
  }

  const CAO_GHI_NGUON = 18;
  const canvas = document.createElement('canvas');
  canvas.width = kich.x;
  canvas.height = kich.y;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new LoiXuatAnh(
      'TRINH_DUYET_KHONG_HO_TRO',
      'Trình duyệt này ⛔ dựng được canvas 2D nên ⛔ ghép được ảnh bản đồ.',
    );
  }

  ctx.fillStyle = y.mauNen;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // --- 1. Ảnh nền ---------------------------------------------------------
  const o = oCanTai({
    rong: kich.x,
    cao: kich.y,
    zoom: y.banDo.getZoom(),
    gocX: goc.x,
    gocY: goc.y,
    mauUrl: y.cauHinh.tileUrl,
  });
  let anh: HTMLImageElement[];
  try {
    anh = await Promise.all(o.map((t) => taiAnhChoCanvas(t.url)));
  } catch {
    // ⛔ ⛔ Chép lý do gốc của trình duyệt vào đây: sự kiện `error` của `<img>` cố ý ⛔ nói vì sao
    //   (chính nó là một kênh rò rỉ thông tin chéo nguồn). Nên câu này phải nêu **nguyên nhân khả
    //   dĩ** kèm cách kiểm, ⛔ đoán chắc một nguyên nhân — luật 37.
    throw new LoiXuatAnh(
      'ANH_NEN_KHONG_TAI_DUOC',
      'Không tải được ảnh nền bản đồ để ghép. Thường là do nguồn ảnh nền ⛔ cho phép tải chéo ' +
        '(thiếu header CORS), hoặc mạng gián đoạn. Bản đồ trên màn hình vẫn dùng bình thường — ' +
        'có thể chụp màn hình thay cho lượt xuất này.',
    );
  }
  o.forEach((t, i) => ctx.drawImage(anh[i], t.x, t.y));

  // --- 2. Lớp GIS người vận hành nạp --------------------------------------
  y.lopGis.forEach((lop) => {
    traiHinhHoc(lop.geojson).forEach((net) => {
      // ⛔⛔ ĐÂY là chỗ DUY NHẤT đảo thứ tự: GeoJSON cho [lng, lat], Leaflet đòi [lat, lng].
      const diem = net.toaDo.map((c) => y.banDo.latLngToContainerPoint([c[1], c[0]]));
      ctx.save();
      ctx.globalAlpha = lop.mo;
      ctx.strokeStyle = lop.mau;
      ctx.fillStyle = lop.mau;
      ctx.lineWidth = 2;
      if (net.loai === 'diem') {
        diem.forEach((p) => {
          ctx.beginPath();
          ctx.arc(p.x, p.y, 5, 0, Math.PI * 2);
          ctx.fill();
        });
      } else {
        ctx.beginPath();
        diem.forEach((p, i) => (i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
        if (net.loai === 'vung') {
          ctx.closePath();
          ctx.globalAlpha = lop.mo * 0.5;
          ctx.fill();
          ctx.globalAlpha = lop.mo;
        }
        ctx.stroke();
      }
      ctx.restore();
    });
  });

  // --- 3. Công cụ đo ------------------------------------------------------
  if (y.diemDo.length > 0) {
    const diem = y.diemDo.map((d) => y.banDo.latLngToContainerPoint([d.lat, d.lng]));
    ctx.save();
    ctx.strokeStyle = y.mauChu;
    ctx.lineWidth = 2;
    ctx.setLineDash([4, 4]);
    if (diem.length >= 2) {
      ctx.beginPath();
      diem.forEach((p, i) => (i === 0 ? ctx.moveTo(p.x, p.y) : ctx.lineTo(p.x, p.y)));
      if (diem.length >= 3) {
        ctx.closePath();
      }
      ctx.stroke();
    }
    ctx.setLineDash([]);
    diem.forEach((p) => {
      ctx.beginPath();
      ctx.arc(p.x, p.y, 4, 0, Math.PI * 2);
      ctx.stroke();
    });
    ctx.restore();
  }

  // --- 4. Chấm công trình và điểm đo --------------------------------------
  y.chams.forEach((c) => {
    const p = y.banDo.latLngToContainerPoint([c.lat, c.lng]);
    veCham(ctx, p.x, p.y, c.cham, y.mauVien);
  });

  // --- 5. Dòng ghi nguồn — bắt buộc, xem javadoc đầu tệp -------------------
  const ghi = chuGhiNguon(y.cauHinh.attribution);
  if (ghi) {
    ctx.save();
    ctx.font = '11px sans-serif';
    const rong = ctx.measureText(ghi).width + 10;
    ctx.globalAlpha = 0.75;
    ctx.fillStyle = y.mauNen;
    ctx.fillRect(canvas.width - rong, canvas.height - CAO_GHI_NGUON, rong, CAO_GHI_NGUON);
    ctx.globalAlpha = 1;
    ctx.fillStyle = y.mauChu;
    ctx.textBaseline = 'middle';
    ctx.fillText(ghi, canvas.width - rong + 5, canvas.height - CAO_GHI_NGUON / 2);
    ctx.restore();
  }

  // --- 6. Ra tệp ----------------------------------------------------------
  const blob = await new Promise<Blob | null>((giaiQuyet, tuChoi) => {
    try {
      canvas.toBlob(giaiQuyet, 'image/png');
    } catch (loi) {
      tuChoi(loi);
    }
  }).catch(() => {
    // `toBlob` ném `SecurityError` khi canvas đã **bị nhiễm** — tức một ô ảnh nền nào đó đã vẽ
    // được nhưng ⛔ mang header CORS. Nhánh này KHÁC nhánh 'ANH_NEN_KHONG_TAI_DUOC' ở trên: ở đó
    // ảnh ⛔ tải được, ở đây ảnh tải được mà ⛔ đọc ngược ra byte được.
    throw new LoiXuatAnh(
      'CANVAS_BI_NHIEM',
      'Nguồn ảnh nền ⛔ cho phép đọc lại nội dung (thiếu header CORS) nên ⛔ ghép được tệp ảnh. ' +
        'Bản đồ trên màn hình vẫn dùng bình thường — có thể chụp màn hình thay cho lượt xuất này.',
    );
  });
  if (!blob) {
    throw new LoiXuatAnh('TRINH_DUYET_KHONG_HO_TRO', 'Trình duyệt ⛔ tạo được tệp PNG từ bản đồ.');
  }
  taiVe(blob, y.tenTep);
}

/**
 * Tải một ảnh ở chế độ đọc lại được.
 *
 * ⚠ `crossOrigin` phải đặt **TRƯỚC** `src`: đặt sau thì lượt tải đã khởi động mà ⛔ mang thuộc
 * tính, và canvas vẫn nhiễm — một lỗi im lặng hoàn toàn, vì ảnh vẫn hiện ra đúng.
 */
function taiAnhChoCanvas(url: string): Promise<HTMLImageElement> {
  return new Promise((giaiQuyet, tuChoi) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => giaiQuyet(img);
    img.onerror = () => tuChoi(new Error(url));
    img.src = url;
  });
}

function taiVe(blob: Blob, tenTep: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = tenTep;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
