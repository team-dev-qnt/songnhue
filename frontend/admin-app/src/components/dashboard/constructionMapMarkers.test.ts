import { statusColors } from 'design-tokens';
import { describe, expect, it } from 'vitest';

import { type StationMarkerView } from '@/shared/api-types';

import { bieuTuongDiemDo, MAU_TIN_HIEU, popupDiemDo, thoat } from './constructionMapMarkers';

/**
 * **Lần ĐẦU TIÊN mã vẽ marker được chạy** — T28.45.
 *
 * ## Vì sao nó chưa từng chạy
 *
 * `operationsDashboard.test.tsx` mở đầu bằng
 * `vi.mock('@/components/dashboard/ConstructionMap', () => ({ ConstructionMap: () => <div/> }))`.
 * Lý do chính đáng — Leaflet đo bố cục thật, jsdom trả kích thước 0 — nhưng nó thay **cả** component,
 * nên bốn màu, viền nét đứt, nội dung popup và hàm chống XSS đi theo. Bài kiểm ấy có sẵn dữ liệu
 * `StationMarkerView` đầy đủ (kể cả `nghiNgo: true` và `khoaMauCanhBao: 'alert-level-3'`) mà ⛔ không
 * một dòng nào của chúng được chạy.
 *
 * ⇒ Tách phần **không cần DOM** ra một module `.ts` là cách duy nhất chạm tới nó, và bài này là chỗ
 * chạm.
 *
 * ## ⛔ Cái nó KHÔNG canh (luật 28)
 *
 * Bài này ⛔ không chứng minh chấm vẽ ra đúng chỗ trên bản đồ, ⛔ không chứng minh Leaflet gắn popup
 * vào đúng marker. Đó là phần cần DOM thật và vẫn ⛔ chưa ai đi qua — nợ T38.10 (Playwright).
 */

const CO_BAN: StationMarkerView = {
  publicId: '11111111-0000-0000-0000-000000000000',
  code: 'F01559',
  name: 'TV Hà Nội',
  lat: 21.02,
  lng: 105.85,
  positionRole: 'THUONG_LUU',
  trangThai: 'HOAT_DONG',
  giaTri: '2.450',
  donVi: 'm',
  tenChiSo: 'Mực nước',
  mocDo: '2026-09-08T01:00:00Z',
  nghiNgo: false,
  khoaMauCanhBao: null,
  tenMucCanhBao: null,
} as unknown as StationMarkerView;

const diem = (p: Partial<StationMarkerView>): StationMarkerView => ({ ...CO_BAN, ...p });

describe('⭐⭐ MAU_TIN_HIEU — bốn trạng thái, và hai cái từng trùng màu', () => {
  it('mọi trạng thái tra ra một khoá màu CÓ THẬT', () => {
    const khoa = Object.values(MAU_TIN_HIEU);
    expect(khoa).toHaveLength(4);
    expect(khoa.filter((k) => statusColors[k] === undefined)).toEqual([]);
  });

  it('⭐⭐ `MAT_TIN_HIEU` KHÁC MÀU `CHUA_CO_DU_LIEU` — backend cố ý tách hai trạng thái', () => {
    // Trước 08/09 cả hai cùng về `unknown`. Một trạm ĐANG CHẾT và một trạm VỪA KHAI trông y hệt
    // nhau trên tường 4K — nơi người trực ⛔ không mở popup.
    expect(statusColors[MAU_TIN_HIEU.MAT_TIN_HIEU]).not.toEqual(
      statusColors[MAU_TIN_HIEU.CHUA_CO_DU_LIEU],
    );
  });

  it('bốn trạng thái cho ra ít nhất BA màu phân biệt được', () => {
    // ⚠ Khẳng định về SỐ LƯỢNG, ⛔ không chia sẻ giả định nào với phép so ở bài trên (luật 29).
    const mau = new Set(Object.values(MAU_TIN_HIEU).map((k) => statusColors[k]));
    expect(mau.size).toBeGreaterThanOrEqual(3);
  });
});

describe('thoat — chống XSS trên bản đồ', () => {
  it('⭐⭐ tên chứa thẻ trở thành chữ, ⛔ không thành mã', () => {
    expect(thoat('<img src=x onerror=alert(1)>')).toBe('&lt;img src=x onerror=alert(1)&gt;');
  });

  it('thoát đủ bốn ký tự, và ⛔ không thoát nhầm dấu nháy đơn', () => {
    expect(thoat('a&b')).toBe('a&amp;b');
    expect(thoat('"x"')).toBe('&quot;x&quot;');
    // Nháy đơn cố ý ⛔ KHÔNG thoát — an toàn *có điều kiện*, xem bài kế tiếp.
    expect(thoat("cống Nhật Tựu's")).toContain("'");
  });

  it('⭐⭐ điều kiện của phép an toàn ấy: MỌI thuộc tính trong popup dùng nháy KÉP', () => {
    // Đây là bài canh cho một giả định, ⛔ không phải cho một hành vi. Đổi một `style="…"` sang
    // `style='…'` là biến việc ⛔ không thoát nháy đơn thành một lỗ XSS — và ⛔ không gì khác thấy.
    const html = popupDiemDo(diem({ name: 'x', trangThai: 'MAT_TIN_HIEU', nghiNgo: true }));
    expect(html).not.toMatch(/<[a-z]+[^>]*=\s*'/i);
    expect(html).toMatch(/style="/);
  });

  it('⭐ tên độc đi qua popup vẫn bị vô hiệu', () => {
    const html = popupDiemDo(diem({ name: '<script>alert(1)</script>' }));
    expect(html).not.toContain('<script>');
    expect(html).toContain('&lt;script&gt;');
  });
});

describe('bieuTuongDiemDo — ba kênh thị giác', () => {
  it('⭐ màu CẢNH BÁO thắng màu trạng thái', () => {
    const mat = bieuTuongDiemDo(diem({ trangThai: 'MAT_TIN_HIEU' })).options.html as string;
    const canhBao = bieuTuongDiemDo(
      diem({ trangThai: 'MAT_TIN_HIEU', khoaMauCanhBao: 'alert-level-3' }),
    ).options.html as string;

    expect(mat).toContain(statusColors[MAU_TIN_HIEU.MAT_TIN_HIEU]);
    expect(
      canhBao,
      'Một trạm đang vượt ngưỡng thì việc nó còn phát tín hiệu hay không là câu hỏi THỨ HAI',
    ).not.toContain(`background:${statusColors[MAU_TIN_HIEU.MAT_TIN_HIEU]};`);
  });

  it('⭐ `nghiNgo` đi bằng VIỀN NÉT ĐỨT, ⛔ không đi bằng màu', () => {
    const sach = bieuTuongDiemDo(diem({ nghiNgo: false })).options.html as string;
    const ngo = bieuTuongDiemDo(diem({ nghiNgo: true })).options.html as string;

    expect(sach).toContain('solid');
    expect(ngo).toContain('dashed');
    // Màu ⛔ không được đổi theo: một kênh chở ba thông tin thì ⛔ không tách ra được cái nào là cái nào.
    expect(ngo.replace('dashed', 'solid')).toEqual(sach);
  });

  it('⛔ ⛔ không mã màu ghi cứng nào lọt vào HTML của chấm', () => {
    // Mọi màu phải đến từ `design-tokens`. Đây là vế đo được của nợ T25.23 ở đúng tệp này.
    const html = bieuTuongDiemDo(diem({})).options.html as string;
    const hex = html.match(/#[0-9a-fA-F]{3,6}/g) ?? [];
    const hopLe = new Set(Object.values(statusColors).map((c) => c.toLowerCase()));
    expect(hex.filter((m) => !hopLe.has(m.toLowerCase()) && m.toLowerCase() !== '#ffffff')).toEqual(
      [],
    );
  });
});

describe('popupDiemDo — ô rỗng phải nói được VÌ SAO (quy tắc 16)', () => {
  it('⭐⭐ ⛔ KHÔNG có số đo hợp lệ ⇒ nói ra, ⛔ không hiện số 0', () => {
    const html = popupDiemDo(diem({ giaTri: null, donVi: null, tenChiSo: null }));
    expect(html).toContain('Chưa có số đo hợp lệ nào');
    expect(html).not.toMatch(/>0\s/);
  });

  it('⭐⭐ trạm MẤT TÍN HIỆU vẫn hiện số cuối, kèm lý do', () => {
    // Ẩn số cuối đi thì người trực ⛔ không biết nó dừng ở mức nào — mà đó chính là thông tin cần
    // khi đi kiểm tra hiện trường.
    const html = popupDiemDo(diem({ trangThai: 'MAT_TIN_HIEU' }));
    expect(html).toContain('2.450');
    expect(html).toContain('dữ liệu chưa cập nhật');
  });

  it('⭐ cờ nghi ngờ nói bằng CHỮ — cái viền ⛔ không nói được nó thuộc chỉ số nào', () => {
    expect(popupDiemDo(diem({ nghiNgo: true }))).toContain('nghi ngờ');
    expect(popupDiemDo(diem({ nghiNgo: false }))).not.toContain('nghi ngờ');
  });

  it('tên mức cảnh báo hiện ra khi có', () => {
    const html = popupDiemDo(
      diem({ khoaMauCanhBao: 'alert-level-3', tenMucCanhBao: 'Báo động II' }),
    );
    expect(html).toContain('Báo động II');
  });
});
