/**
 * **Bóc một cặp toạ độ ra khỏi thứ người dùng DÁN VÀO** — WS-46 / T46.2.
 *
 * ## ⭐ Vì sao cần tệp này
 *
 * Bản chụp G8 của Công ty ⛔ không có cột toạ độ, nên **0/19 điểm đo và 0/11 công trình** có vị
 * trí — lớp GIS rỗng hoàn toàn. Đường vào dữ liệu ấy trên thực tế **⛔ không phải** gõ tay hai số
 * thập phân sáu chữ số: người ta mở Google Maps, bấm chuột phải, *Sao chép toạ độ*, rồi dán.
 *
 * Thứ nằm trong clipboard khi ấy là `21.048201, 105.782500` — một **chuỗi một dòng**. Dán nó vào
 * một `InputNumber` cho ra `21` (nó cắt ở dấu phẩy) hoặc rỗng. Nên trước bản này, con đường tự
 * nhiên nhất để nhập toạ độ vừa **im lặng** vừa **sai**: ⛔ không báo lỗi, chỉ mất phần thập phân
 * và cả kinh độ — và một điểm sai vài chục km trông y hệt một điểm đúng cho tới khi ai đó mở bản đồ.
 *
 * ## ⛔ Ba định dạng nhận, và ⛔ không nhận gì ngoài ba cái đó
 *
 * 1. **Cặp thập phân** — `21.0482, 105.7825` · `21.0482;105.7825` · `21.0482 105.7825`.
 * 2. **Độ-phút-giây** — `21°02'53.5"N 105°46'57.0"E`. Đây là thứ Google Maps hiện ở thanh tìm kiếm
 *    (khác với thứ nó *sao chép*), nên người dùng gõ tay lại rất hay ra dạng này.
 * 3. **Liên kết Google Maps** — `.../@21.0482,105.7825,17z` hoặc `?q=21.0482,105.7825`. Người ta
 *    hay gửi nhau cả cái link chứ ⛔ không gửi cặp số.
 *
 * ⛔ **Cố ý ⛔ KHÔNG đoán** khi chuỗi ⛔ không khớp cái nào: trả `null` để ô nhập nói *"⛔ không đọc
 * được"*. Một bộ bóc "cố hiểu" là một bộ bóc sẽ có ngày hiểu `K72+000` thành một toạ độ.
 *
 * ## ⚠ Thứ tự vĩ độ trước, và vì sao ⛔ không tự đảo
 *
 * Cả ba định dạng đều là **vĩ độ trước** — đó là quy ước của Google Maps, của GPS dân dụng, và của
 * `ST_MakePoint(longitude, latitude)` ở CSDL (chỗ ⛔ **duy nhất** đảo thứ tự, và nó đã được viết
 * đúng một lần trong cột sinh `geom`).
 *
 * ⛔ Bộ bóc này **⛔ không tự sửa** một cặp bị đảo. Lý do: với Việt Nam, kinh độ (~105) **luôn** vượt
 * biên vĩ độ (±90), nên cặp đảo bị ràng buộc CSDL `ck_*_lat_range` **ném ra** — một lỗi ồn ào, đúng
 * chỗ. Tự đảo hộ thì cặp ấy được **nhận** và điểm rơi xuống Ấn Độ Dương trong im lặng. Ồn ào thắng
 * im lặng (quy tắc 16).
 */

/** Một cặp toạ độ đã bóc được. Vĩ độ trước — xem javadoc tệp. */
export interface ToaDoDaBoc {
  viDo: number;
  kinhDo: number;
}

/**
 * Khung bao địa lý của **vùng Công ty quản lý** — Hà Nội và Hà Nam, lưu vực sông Nhuệ.
 *
 * ⚠ Đây là ngưỡng **CẢNH BÁO**, ⛔ không phải ngưỡng **CHẶN**, và khác biệt ấy là cố ý: một điểm đo
 * mới ngoài khung vẫn có thể đúng (Công ty mở rộng phạm vi), còn một cặp bị đảo hay dán nhầm thì
 * gần như luôn rơi ra ngoài. Chặn cứng là biến một lời nhắc hữu ích thành một bức tường; ⛔ không
 * nói gì là để một điểm sai đi thẳng lên bản đồ điều hành.
 */
export const KHUNG_SONG_NHUE = {
  viDoMin: 20.1,
  viDoMax: 21.5,
  kinhDoMin: 105.0,
  kinhDoMax: 106.5,
} as const;

const BIEN_VI_DO = 90;
const BIEN_KINH_DO = 180;

/** `21.0482, 105.7825` — phân tách bằng dấu phẩy, chấm phẩy hoặc khoảng trắng. */
const CAP_THAP_PHAN = /^\s*(-?\d{1,3}(?:\.\d+)?)\s*[,;\s]\s*(-?\d{1,3}(?:\.\d+)?)\s*$/;

/** `21°02'53.5"N 105°46'57.0"E` — chấp cả `'`/`’` và `"`/`”`, hoa hay thường. */
const DMS = /(\d{1,3})\s*°\s*(\d{1,2})\s*['’]\s*(\d{1,2}(?:\.\d+)?)\s*["”]?\s*([NSEWnsew])/g;

/** `@21.0482,105.7825,17z` hoặc `q=21.0482,105.7825` trong một URL Google Maps. */
const TRONG_URL = /[@?&](?:q=|ll=)?(-?\d{1,3}\.\d+),\s*(-?\d{1,3}\.\d+)/;

/**
 * Cặp toạ độ có nằm trong vùng Công ty quản lý không — dùng để **nhắc**, ⛔ không để chặn.
 */
export function trongVungSongNhue(t: ToaDoDaBoc): boolean {
  return (
    t.viDo >= KHUNG_SONG_NHUE.viDoMin &&
    t.viDo <= KHUNG_SONG_NHUE.viDoMax &&
    t.kinhDo >= KHUNG_SONG_NHUE.kinhDoMin &&
    t.kinhDo <= KHUNG_SONG_NHUE.kinhDoMax
  );
}

function hopLe(viDo: number, kinhDo: number): ToaDoDaBoc | null {
  if (!Number.isFinite(viDo) || !Number.isFinite(kinhDo)) return null;
  if (Math.abs(viDo) > BIEN_VI_DO || Math.abs(kinhDo) > BIEN_KINH_DO) return null;
  // ⛔ Làm tròn 6 chữ số — khớp `NUMERIC(9,6)` của cả `stations` lẫn `constructions`. Để nguyên
  //    15 chữ số thì backend nhận rồi CSDL tự cắt, và giá trị hiện lại trên màn hình ⛔ KHÁC giá
  //    trị vừa gõ — thứ trông như hệ thống "tự sửa" số của người dùng.
  return { viDo: Number(viDo.toFixed(6)), kinhDo: Number(kinhDo.toFixed(6)) };
}

/** Một cụm DMS → độ thập phân. `S`/`W` cho ra số âm. */
function tuDms(do_: string, phut: string, giay: string, huong: string): number {
  const value = Number(do_) + Number(phut) / 60 + Number(giay) / 3600;
  return /[SWsw]/.test(huong) ? -value : value;
}

/**
 * Bóc cặp toạ độ ra khỏi một chuỗi bất kỳ.
 *
 * @returns `null` khi ⛔ không khớp định dạng nào — ⛔ **không** đoán, xem javadoc tệp.
 */
export function bocToaDo(chuoi: string | null | undefined): ToaDoDaBoc | null {
  if (!chuoi) return null;
  const s = chuoi.trim();
  if (!s) return null;

  // 1. Cặp thập phân trần — thử TRƯỚC, vì nó là thứ Google Maps đưa vào clipboard.
  const cap = CAP_THAP_PHAN.exec(s);
  if (cap) return hopLe(Number(cap[1]), Number(cap[2]));

  // 2. Độ-phút-giây. ⚠ `matchAll` trên một regex có cờ `g` — đọc ĐÚNG hai cụm, ⛔ không phải "ít
  //    nhất hai": ba cụm nghĩa là chuỗi có thứ khác lẫn vào và ta ⛔ không biết cụm nào là toạ độ.
  const cum = [...s.matchAll(DMS)];
  if (cum.length === 2) {
    const a = tuDms(cum[0][1], cum[0][2], cum[0][3], cum[0][4]);
    const b = tuDms(cum[1][1], cum[1][2], cum[1][3], cum[1][4]);
    // ⚠ Hướng quyết định cột, ⛔ không phải thứ tự xuất hiện: `105°…E 21°…N` là hợp lệ và
    //   ⛔ không hiếm — người ta chép từ một bảng có cột kinh độ đứng trước.
    const dauLaVi = /[NSns]/.test(cum[0][4]);
    return dauLaVi ? hopLe(a, b) : hopLe(b, a);
  }

  // 3. Liên kết Google Maps.
  const url = TRONG_URL.exec(s);
  if (url) return hopLe(Number(url[1]), Number(url[2]));

  return null;
}
