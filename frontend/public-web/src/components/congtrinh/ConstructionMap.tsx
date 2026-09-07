'use client';

import 'leaflet/dist/leaflet.css';

import { useEffect, useRef } from 'react';

import { TILE_ATTRIBUTION, TILE_URL } from '@/lib/mapTiles';

export interface DiemCongTrinh {
  code: string;
  name: string;
  unitName: string;
  lat: number;
  lng: number;
}

/**
 * Bản đồ công trình — CN-02.4, dựng 29/08/2026.
 *
 * <h2>⚠ Leaflet nạp lúc CHẠY, không nằm trong gói đầu trang</h2>
 *
 * {@code import('leaflet')} nằm trong {@code useEffect} nên thư viện chỉ tải khi component này
 * thật sự được vẽ. Cùng với {@link HomeConstructionMap} — nó không vẽ component này khi chưa có
 * điểm nào — hệ quả là ngày G8 chưa về thì trang chủ <b>không tải một byte Leaflet nào</b>.
 * NFR-02 cho trang chủ có 3 giây; DOD1.17 vốn đã sát ngưỡng.
 *
 * <h2>⛔ Dấu vị trí vẽ bằng SVG, không dùng ảnh mặc định của Leaflet</h2>
 *
 * Bộ icon mặc định trỏ tới {@code marker-icon.png} trong gói, và đường dẫn ấy do bộ đóng gói
 * giải — với Next thì nó hay ra 404 lặng lẽ, để lại bản đồ có điểm mà không thấy dấu nào.
 * {@code divIcon} + SVG nội tuyến không có tệp nào để hỏng, và cũng không thêm một loại tài
 * nguyên nữa vào CSP.
 *
 * <h2>Bàn phím và trình đọc màn hình</h2>
 *
 * Bản đồ là một vùng đồ hoạ; danh sách công trình đọc được vẫn nằm ở
 * {@code /quan-ly-van-hanh/danh-muc-cong-trinh}. Vùng này khai {@code role="application"} kèm
 * nhãn, và {@code scrollWheelZoom} TẮT — cuộn trang bằng con lăn mà bị bản đồ nuốt mất là một
 * trong những phiền toái kinh điển của bản đồ nhúng.
 */
export default function ConstructionMap({ diem }: { diem: DiemCongTrinh[] }) {
  const oRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const o = oRef.current;
    if (!o || diem.length === 0) return;

    let huy = false;
    // Kiểu `unknown` rồi ép về sau: `import()` động không giữ được kiểu tĩnh ở đây, và khai
    // `any` sẽ bị ESLint chặn.
    let banDo: { remove: () => void } | null = null;

    void (async () => {
      const L = (await import('leaflet')).default;
      if (huy || !oRef.current) return;

      const map = L.map(oRef.current, {
        scrollWheelZoom: false,
        attributionControl: true,
      });
      banDo = map;

      L.tileLayer(TILE_URL, { attribution: TILE_ATTRIBUTION, maxZoom: 18 }).addTo(map);

      const icon = L.divIcon({
        className: '',
        html:
          '<span class="sn-marker" aria-hidden="true">' +
          '<svg viewBox="0 0 24 24" width="26" height="26" fill="currentColor">' +
          '<path d="M12 22s7-7.1 7-12a7 7 0 1 0-14 0c0 4.9 7 12 7 12z"/>' +
          '</svg></span>',
        iconSize: [26, 26],
        iconAnchor: [13, 26],
      });

      const nhom = L.featureGroup(
        diem.map((d) =>
          L.marker([d.lat, d.lng], { icon, title: d.name }).bindPopup(
            `<b>${d.name}</b><br>${d.unitName}`,
          ),
        ),
      ).addTo(map);

      // Khung nhìn suy ra từ chính dữ liệu — không có toạ độ trung tâm nào viết cứng, nên
      // thêm một công trình ở rìa tuyến là bản đồ tự nới ra.
      map.fitBounds(nhom.getBounds(), { padding: [32, 32], maxZoom: 14 });
    })();

    return () => {
      huy = true;
      banDo?.remove();
    };
  }, [diem]);

  return (
    <div
      ref={oRef}
      role="application"
      aria-label={`Bản đồ ${diem.length} công trình thủy lợi`}
      className="h-[340px] w-full overflow-hidden rounded-lg border border-surface-border"
    />
  );
}
