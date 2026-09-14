/**
 * Công cụ đo trên bản đồ — CN-02.4 / **M2.12**.
 *
 * ⛔⛔ Tách khỏi component ⛔ **không** phải cho gọn: hai công thức dưới đây là **số đo**, và một số
 * đo sai trên bản đồ trông y hệt một số đo đúng. Tách ra thì chúng có bài kiểm riêng
 * (`banDoDo.test.ts`) chạy trên những khoảng cách **đã biết đáp án**.
 *
 * ⚠ Và chúng ⛔ **không** dùng hình học phẳng: ở vĩ độ 21° (Hà Nội), một độ kinh tuyến ngắn hơn một
 * độ vĩ tuyến khoảng **7%**. Đo bằng Pythagore trên (lat, lng) cho ra sai số vài trăm mét trên một
 * tuyến kênh 10 km — đủ để một biên bản hiện trường sai.
 */

/** Bán kính Trái Đất trung bình theo WGS-84, mét. */
const BAN_KINH_M = 6_371_008.8;

export interface Diem {
  lat: number;
  lng: number;
}

const rad = (do_: number) => (do_ * Math.PI) / 180;

/**
 * Khoảng cách giữa hai điểm theo **haversine**, mét.
 *
 * ⚠ Haversine coi Trái Đất là hình cầu ⇒ sai số ≤ 0,5% so với ellipsoid WGS-84. Với một tuyến kênh
 * vài km thì đó là vài chục mét — chấp nhận được cho một công cụ đo nhanh trên bản đồ, và **phải
 * nói ra** thay vì để người dùng tưởng đây là số đo trắc địa.
 */
export function khoangCach(a: Diem, b: Diem): number {
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * BAN_KINH_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Tổng chiều dài một đường gấp khúc, mét. ⛔ Dưới 2 điểm ⇒ **0**, ⛔ không phải `NaN`. */
export function chieuDai(diem: Diem[]): number {
  let tong = 0;
  for (let i = 1; i < diem.length; i++) {
    tong += khoangCach(diem[i - 1], diem[i]);
  }
  return tong;
}

/**
 * Diện tích một đa giác trên mặt cầu, **mét vuông**.
 *
 * ⛔ Công thức lượng giác cầu (spherical excess), ⛔ **không** phải shoelace trên (lat, lng):
 * shoelace coi toạ độ là mặt phẳng, và ở vĩ độ 21° nó cho ra diện tích **lớn hơn thực tế ~7%** —
 * một sai số một chiều, tức nó ⛔ không tự triệt tiêu khi đo nhiều lần.
 *
 * ⛔ Dưới 3 điểm ⇒ **0**: một đoạn thẳng ⛔ không có diện tích, và trả `NaN` là để màn hình hiện
 * chữ "NaN m²".
 */
export function dienTich(diem: Diem[]): number {
  if (diem.length < 3) {
    return 0;
  }
  let tong = 0;
  for (let i = 0; i < diem.length; i++) {
    const p1 = diem[i];
    const p2 = diem[(i + 1) % diem.length];
    tong += rad(p2.lng - p1.lng) * (2 + Math.sin(rad(p1.lat)) + Math.sin(rad(p2.lat)));
  }
  return Math.abs((tong * BAN_KINH_M * BAN_KINH_M) / 2);
}

/**
 * Chuỗi hiển thị của một chiều dài.
 *
 * ⛔ Đổi đơn vị ở **một chỗ**: hai nơi cùng đổi m ↔ km là hai nơi sẽ chọn ngưỡng khác nhau, và
 * người dùng thấy `950 m` ở chỗ này, `0.95 km` ở chỗ kia cho cùng một phép đo.
 */
export function nhanChieuDai(met: number): string {
  return met < 1000 ? `${met.toFixed(0)} m` : `${(met / 1000).toFixed(2)} km`;
}

/** ⚠ Ngưỡng 10.000 m² = 1 ha — đơn vị ruộng đất người dùng thật sự dùng. */
export function nhanDienTich(metVuong: number): string {
  if (metVuong < 10_000) {
    return `${metVuong.toFixed(0)} m²`;
  }
  return `${(metVuong / 10_000).toFixed(2)} ha`;
}
