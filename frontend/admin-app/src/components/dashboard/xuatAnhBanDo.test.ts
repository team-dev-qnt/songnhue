import { describe, expect, it } from 'vitest';

import {
  chuGhiNguon,
  LoiXuatAnh,
  oCanTai,
  traiHinhHoc,
  TRAN_SO_O,
  type KhungChup,
} from './xuatAnhBanDo';

/**
 * **Xuất ảnh bản đồ — M2.13 vế 1 (T59.13).**
 *
 * ## ⛔ Cái bài này KHÔNG canh, và vì sao (luật 28)
 *
 * jsdom ⛔ có canvas 2D (kho ⛔ cài gói `canvas`), nên `getContext('2d')` trả `null` và **⛔ một nét
 * vẽ nào chạy được ở đây**. Đó chính là lý do phần **dễ sai nhất** — *phải tải ô nào, đặt nó ở
 * đâu*, và *hình học GeoJSON trải ra sao* — được bóc thành hàm thuần: chúng kiểm được, còn lượt
 * `drawImage` thì ⛔.
 *
 * ⇒ Thứ còn lại (ghép thật, `toBlob`, nhánh canvas bị nhiễm) phải đo bằng **trình duyệt thật** —
 * nợ T38.10 (Playwright). Cái xanh của bài này ⛔ được đọc thành *"nút xuất chạy"*.
 */
describe('oCanTai — danh sách ô ảnh nền và chỗ đặt', () => {
  const NEN: KhungChup = {
    rong: 300,
    cao: 200,
    zoom: 10,
    gocX: 100,
    gocY: 50,
    mauUrl: 'https://tile.example.org/{z}/{x}/{y}.png',
  };

  it('⭐⭐ Chỗ đặt trừ đi GỐC khung — thiếu phép trừ ấy thì ảnh nền lệch mà vẫn "trông có bản đồ"', () => {
    // Ô (0,0) phủ pixel CRS [0,256); mép trái khung ở pixel 100 ⇒ ô ấy phải bắt đầu ở **-100**.
    // Một bản quên trừ gốc sẽ đặt nó ở 0: bản đồ vẫn hiện, chỉ **trượt tới 100 px** — và lượt rà
    // bằng mắt ⛔ bắt được, vì tấm ảnh trông hoàn toàn bình thường.
    const o = oCanTai(NEN);
    expect(o).toHaveLength(2);
    expect(o.map((t) => [t.x, t.y])).toEqual([
      [-100, -50],
      [156, -50],
    ]);
  });

  it('⭐ URL cắm đủ {z}/{x}/{y}', () => {
    expect(oCanTai(NEN)[1].url).toBe('https://tile.example.org/10/1/0.png');
  });

  it('⭐⭐ Trục X CUỘN VÒNG, trục Y thì CẮT — hai trục, hai luật', () => {
    // Kinh độ quấn quanh quả đất: ở zoom 1 chỉ có 2 ô, nên ô `x = -1` **là** ô `x = 1`.
    // Vĩ độ thì ⛔: phía trên cực Bắc ⛔ có ô nào, và hỏi nguồn ô `y = -1` trả 404 ⇒ cả lượt xuất
    // rơi xuống nhánh "⛔ tải được ảnh nền" vì một ô ⛔ bao giờ tồn tại.
    const o = oCanTai({ ...NEN, zoom: 1, gocX: -10, gocY: -10, rong: 20, cao: 20 });
    expect(o.map((t) => t.url)).toEqual([
      'https://tile.example.org/1/1/0.png',
      'https://tile.example.org/1/0/0.png',
    ]);
    expect(o.map((t) => t.x)).toEqual([-246, 10]);
    expect(o.every((t) => !t.url.includes('/-1.png'))).toBe(true);
  });

  it('⭐ {s} cắm tên miền con theo đúng công thức của Leaflet', () => {
    const o = oCanTai({
      ...NEN,
      mauUrl: 'https://{s}.tile.example.org/{z}/{x}/{y}.png',
      tenMien: 'abc',
    });
    // `subdomains[abs(x + y) % 3]` ⇒ ô (0,0) → 'a', ô (1,0) → 'b'.
    expect(o.map((t) => t.url.slice(8, 9))).toEqual(['a', 'b']);
  });

  it('⛔⛔ Vượt trần số ô thì NÉM có tên, ⛔ lặng lẽ cắt bớt', () => {
    // Trần này là ràng buộc của **bên thứ ba** (chính sách dùng tile của OSM cấm tải hàng loạt),
    // ⛔ phải một mẹo giữ bộ nhớ. Cắt bớt cho "đỡ lỗi" là xuất ra một tấm ảnh THIẾU ô mà người
    // nhận ⛔ có cách nào biết là thiếu.
    const qua = () => oCanTai({ ...NEN, rong: 256 * 20, cao: 256 * 20, gocX: 0, gocY: 0 });
    expect(qua).toThrow(LoiXuatAnh);
    try {
      qua();
    } catch (loi) {
      expect((loi as LoiXuatAnh).lyDo).toBe('QUA_NHIEU_O');
      expect((loi as LoiXuatAnh).message).toContain(String(TRAN_SO_O));
    }
  });

  it('⚠ Vế chống tập rỗng — khung hợp lệ phải cho ÍT NHẤT một ô', () => {
    // Thiếu vế này thì mọi khẳng định trên vẫn xanh khi hàm trả `[]` với mọi đầu vào (luật 7).
    expect(oCanTai(NEN).length).toBeGreaterThan(0);
  });
});

describe('traiHinhHoc — trải GeoJSON thành nét vẽ', () => {
  it('⛔⛔⛔ GIỮ thứ tự kinh-độ-trước của RFC 7946 — đảo ở đây là lỗi IM LẶNG', () => {
    // Đây là đúng cái bẫy T59.14: KML và GeoJSON đều ghi kinh độ trước, còn Leaflet đòi vĩ độ
    // trước. Người sửa mã rất dễ "sửa cho đúng" ngay tại đây, và một điểm Hà Nội (105,21) hoá
    // (21,105) rơi xuống Ấn Độ Dương — ⛔ một dòng lỗi nào, chỉ là một chấm ở chỗ khác.
    const net = traiHinhHoc({ type: 'Point', coordinates: [105.78, 21.04] });
    expect(net).toEqual([{ loai: 'diem', toaDo: [[105.78, 21.04]] }]);
  });

  it('⭐ Bóc được qua FeatureCollection → Feature → geometry', () => {
    const net = traiHinhHoc({
      type: 'FeatureCollection',
      features: [
        {
          type: 'Feature',
          properties: { name: 'Kênh' },
          geometry: {
            type: 'LineString',
            coordinates: [
              [105.7, 21.0],
              [105.8, 21.1],
            ],
          },
        },
      ],
    });
    expect(net).toEqual([
      {
        loai: 'duong',
        toaDo: [
          [105.7, 21.0],
          [105.8, 21.1],
        ],
      },
    ]);
  });

  it('⭐ Cao độ bị cắt — KML mang nó, bản đồ phẳng ⛔ dùng tới', () => {
    const net = traiHinhHoc({ type: 'Point', coordinates: [105.78, 21.04, 12.5] });
    expect(net[0].toaDo[0]).toHaveLength(2);
  });

  it('⛔⛔ Vòng LỖ của Polygon vẫn được vẽ — bỏ nó là khẳng định một vùng LIỀN mà thực tế ⛔', () => {
    const net = traiHinhHoc({
      type: 'Polygon',
      coordinates: [
        [
          [0, 0],
          [2, 0],
          [2, 2],
          [0, 2],
        ],
        [
          [0.5, 0.5],
          [1, 0.5],
          [1, 1],
        ],
      ],
    });
    expect(net).toHaveLength(2);
    expect(net.every((n) => n.loai === 'vung')).toBe(true);
  });

  it('⭐ MultiGeometry / GeometryCollection trải phẳng', () => {
    const net = traiHinhHoc({
      type: 'GeometryCollection',
      geometries: [
        { type: 'Point', coordinates: [1, 2] },
        {
          type: 'MultiLineString',
          coordinates: [
            [
              [3, 4],
              [5, 6],
            ],
          ],
        },
      ],
    });
    expect(net.map((n) => n.loai)).toEqual(['diem', 'duong']);
  });

  it('⛔ Rác thì BỎ QUA, ⛔ ném — tấm ảnh ⛔ bao giờ được nhiều hơn bản đồ', () => {
    // Nội dung lớp GIS do **người vận hành nạp**; `L.geoJSON` trên màn hình cũng bỏ qua thứ nó
    // ⛔ hiểu. Nếu ở đây ném thì một lớp hỏng làm CẢ lượt xuất chết — cùng hình dạng T59.10.
    expect(traiHinhHoc(null)).toEqual([]);
    expect(traiHinhHoc({ type: 'KhongPhaiGeoJson' })).toEqual([]);
    expect(traiHinhHoc({ type: 'LineString', coordinates: [['a', 'b'], [1]] })).toEqual([]);
  });

  it('⚠ Vế chống tập rỗng — một GeoJSON hợp lệ phải cho ÍT NHẤT một nét', () => {
    expect(traiHinhHoc({ type: 'Point', coordinates: [1, 2] }).length).toBeGreaterThan(0);
  });
});

describe('chuGhiNguon — dòng ghi nguồn burn vào ảnh', () => {
  it('⛔⛔ Bóc thẻ HTML — `ops.map.tile-attribution` là khoá `settings` SỬA ĐƯỢC', () => {
    // Leaflet nhận HTML ở ô ấy, canvas thì chỉ vẽ được chữ. Giá trị seed hôm nay là chữ thuần,
    // nhưng một khoá người ta sửa được ⛔ được phép giả định là chữ thuần — và một tấm ảnh in ra
    // nguyên văn `<a href=…>` là một tấm ảnh hỏng đi vào báo cáo.
    expect(
      chuGhiNguon('&copy; <a href="https://osm.org/copyright">OpenStreetMap</a> contributors'),
    ).toBe('© OpenStreetMap contributors');
  });

  it('⭐ Chữ thuần đi qua nguyên vẹn — đó là giá trị seed đang chạy', () => {
    expect(chuGhiNguon('© OpenStreetMap contributors')).toBe('© OpenStreetMap contributors');
  });

  it('⚠ Rỗng vẫn là rỗng — nơi gọi dựa vào đó để ⛔ vẽ dải nền trống', () => {
    expect(chuGhiNguon('   ')).toBe('');
  });
});
