import { describe, expect, it } from 'vitest';

import {
  dungPayloadNhapNhanh,
  hopLeLyTrinh,
  locTuDuongDan,
  khoiThongSoTheoLoai,
  locKhoiThongSo,
  trieuSangVnd,
  vndSangTrieu,
} from './constructionRules';

describe('khối thông số theo loại công trình (OPS-2009)', () => {
  it('mỗi loại ánh xạ đúng một khối', () => {
    expect(khoiThongSoTheoLoai('TRAM_BOM')).toBe('pump');
    expect(khoiThongSoTheoLoai('CONG')).toBe('sluice');
    expect(khoiThongSoTheoLoai('KENH_MUONG')).toBe('linear');
    expect(khoiThongSoTheoLoai('DE_DIEU')).toBe('linear');
  });

  it('loại "Khác" KHÔNG rơi vào khối nào — đưa nó về một khối bất kỳ là cho dữ liệu vào nhầm bảng', () => {
    expect(khoiThongSoTheoLoai('KHAC')).toBeNull();
    expect(khoiThongSoTheoLoai(undefined)).toBeNull();
  });

  // ⭐ Đây là bài bắt được lỗi thật: AntD giữ nguyên giá trị của ô đã bị ẩn trong form store.
  it('đổi loại sau khi đã nhập → khối cũ bị xoá khỏi payload', () => {
    const daNhapTramBom = { constructionType: 'TRAM_BOM' as const, pump: { pumpCount: 3 } };
    const doiSangCong = {
      ...daNhapTramBom,
      constructionType: 'CONG' as const,
      sluice: { gateCount: 2 },
    };

    const payload = locKhoiThongSo(doiSangCong);

    expect(payload.pump).toBeNull();
    expect(payload.sluice).toEqual({ gateCount: 2 });
    expect(payload.linear).toBeNull();
  });
});

describe('định dạng lý trình K<km>+<m> (OPS-2011)', () => {
  it('nhận các dạng đúng', () => {
    expect(hopLeLyTrinh('K0+390')).toBe(true);
    expect(hopLeLyTrinh('K18+100')).toBe(true);
    expect(hopLeLyTrinh('K129+696')).toBe(true);
    expect(hopLeLyTrinh('K6+0')).toBe(true);
  });

  it('để trống là hợp lệ — lý trình không bắt buộc', () => {
    expect(hopLeLyTrinh('')).toBe(true);
    expect(hopLeLyTrinh(null)).toBe(true);
    expect(hopLeLyTrinh(undefined)).toBe(true);
  });

  it('từ chối các dạng sai', () => {
    expect(hopLeLyTrinh('0+390')).toBe(false); // thiếu K
    expect(hopLeLyTrinh('K0-390')).toBe(false); // sai dấu
    expect(hopLeLyTrinh('K0')).toBe(false); // thiếu phần mét
    expect(hopLeLyTrinh('Km0+390')).toBe(false);
  });

  // ⭐ Phần mét phải < 1000. Không chặn thì K0+1390 và K1+390 cùng chỉ một vị trí mà là hai chuỗi
  //    khác nhau, và mọi phép sắp xếp theo lý trình về sau đều sai một cách im lặng.
  it('từ chối phần mét ≥ 1000 — cùng một vị trí không được có hai cách viết', () => {
    expect(hopLeLyTrinh('K0+1390')).toBe(false);
    expect(hopLeLyTrinh('K0+10000')).toBe(false);
  });
});

describe('quy đổi VNĐ ↔ triệu (quy tắc 2 — cấm float cho tiền)', () => {
  it('số nguyên triệu', () => {
    expect(trieuSangVnd(12)).toBe('12000000');
    expect(trieuSangVnd(0)).toBe('0');
    expect(trieuSangVnd(1500)).toBe('1500000000');
  });

  // ⭐⭐ Bài quan trọng nhất của nhóm này, và nó đứng trên một giá trị ĐÃ ĐO chứ không phải một
  //     giá trị nghe có vẻ đúng: `12.34 * 1e6` tình cờ ra chẵn, còn `1.001 * 1e6` thì không.
  it('phần thập phân KHÔNG được dính sai số dấu phẩy động', () => {
    // Chứng minh tại chỗ vì sao không nhân trực tiếp — nếu JS sửa được điều này thì bài kiểm đỏ và
    // ta biết mà xem lại, thay vì giữ mãi một lời cảnh báo không còn đúng.
    expect(1.001 * 1_000_000).not.toBe(1_001_000);
    expect(1.001 * 1_000_000).toBeCloseTo(1_000_999.9999999999, 6);

    expect(trieuSangVnd(1.001)).toBe('1001000');
    expect(trieuSangVnd(12.34)).toBe('12340000');
    expect(trieuSangVnd(0.1)).toBe('100000');
    expect(trieuSangVnd(0.000001)).toBe('1');
  });

  it('bỏ trống trả null, không trả 0 — số 0 là một câu khẳng định', () => {
    expect(trieuSangVnd(undefined)).toBeNull();
    expect(trieuSangVnd(null)).toBeNull();
    expect(trieuSangVnd('')).toBeNull();
    expect(trieuSangVnd('abc')).toBeNull();
  });

  it('đi và về không mất giá trị', () => {
    expect(vndSangTrieu(trieuSangVnd(12.34))).toBeCloseTo(12.34, 6);
    expect(vndSangTrieu('1500000000')).toBe(1500);
    expect(vndSangTrieu(undefined)).toBeUndefined();
  });
});

describe('payload nhập nhanh tình hình vận hành (CN-02.11)', () => {
  const LUC = '2026-08-23T01:00:00.000Z';

  it('CHỈ gửi dòng đã chọn mã — bản trước gửi toàn bộ danh sách công trình mỗi lượt Lưu', () => {
    const payload = dungPayloadNhapNhanh(
      {
        'ct-1': { code: 'ĐK' },
        'ct-2': {}, // người dùng không đụng tới
        'ct-3': { note: 'gõ nhầm vào ô ghi chú rồi thôi' },
      },
      LUC,
    );

    expect(payload).toHaveLength(1);
    expect(payload[0].constructionPublicId).toBe('ct-1');
  });

  it('khoá gửi lên là publicId, không phải khoá nội bộ', () => {
    const payload = dungPayloadNhapNhanh({ '9f1c-uuid': { code: 'MT' } }, LUC);
    expect(payload[0]).toHaveProperty('constructionPublicId', '9f1c-uuid');
    expect(payload[0]).not.toHaveProperty('constructionId');
  });

  it('giá trị kèm theo giữ dạng chuỗi và bị trim; rỗng thì không gửi', () => {
    const payload = dungPayloadNhapNhanh({ a: { code: 'MT', value: ' 1.70 ', note: '  ' } }, LUC);
    expect(payload[0].parameterValue).toBe('1.70');
    expect(payload[0].note).toBeUndefined();
  });

  it('mọi dòng dùng chung một thời điểm hiệu lực', () => {
    const payload = dungPayloadNhapNhanh({ a: { code: 'MT' }, b: { code: 'ĐK' } }, LUC);
    expect(payload.map((p) => p.effectiveAt)).toEqual([LUC, LUC]);
  });
});

describe('bộ lọc đọc từ đường dẫn (nợ #71)', () => {
  // ⭐ Dashboard điều hướng sang `?status=SU_CO`; trang danh sách trước đây KHÔNG đọc query string
  //    nên mở ra danh sách không lọc. Liên kết vẫn chạy, chỉ là nó không làm gì.
  it('đọc đủ các tham số dashboard gửi sang', () => {
    expect(locTuDuongDan(new URLSearchParams('status=SU_CO'))).toEqual({ status: 'SU_CO' });
    expect(locTuDuongDan(new URLSearchParams('type=CONG'))).toEqual({ type: 'CONG' });
    expect(locTuDuongDan(new URLSearchParams('level=XI_NGHIEP'))).toEqual({ level: 'XI_NGHIEP' });
  });

  it('gộp nhiều tham số cùng lúc', () => {
    expect(locTuDuongDan(new URLSearchParams('status=SU_CO&type=CONG&river=Sông Nhuệ'))).toEqual({
      status: 'SU_CO',
      type: 'CONG',
      river: 'Sông Nhuệ',
    });
  });

  it('đường dẫn trống → không lọc gì', () => {
    expect(locTuDuongDan(new URLSearchParams(''))).toEqual({});
  });

  it('withoutLocation chỉ bật khi đúng chữ "true"', () => {
    expect(locTuDuongDan(new URLSearchParams('withoutLocation=true')).withoutLocation).toBe(true);
    expect(
      locTuDuongDan(new URLSearchParams('withoutLocation=false')).withoutLocation,
    ).toBeUndefined();
  });
});
