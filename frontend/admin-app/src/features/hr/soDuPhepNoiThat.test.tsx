import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { SoDuPhepCard } from './SoDuPhepCard';
import { type SoDuPhepView } from './hrVocabulary';

const MOC: SoDuPhepView = {
  nam: 2026,
  duocHuong: '14.0',
  theoThamNien: '14.0',
  chuyenTuNamTruoc: '0.0',
  daDung: '3.0',
  dangChoDuyet: '2.0',
  conLai: '9.0',
  namTruocCoDuLieu: false,
};

/**
 * ⛔⛔ **Số 0 của ô *"chuyển từ năm trước"* mang HAI nghĩa** — quy tắc 16.
 *
 * `namTruocCoDuLieu = false` ⇒ 0 nghĩa là **CHƯA BIẾT** (hệ chưa vận hành năm ấy); `true` ⇒ 0
 * nghĩa là **đã dùng hết**. Hai câu khác hẳn nhau và dẫn tới hai hành động khác hẳn nhau: một cái
 * đòi Phòng Tổ chức nhập số phép tồn, cái kia ⛔ không đòi gì.
 *
 * <p>In một số 0 trần cho cả hai là để người lao động đọc một khẳng định mà hệ thống ⛔ không có cơ
 * sở nào để nói — và triệu chứng thì **im lặng tuyệt đối**: màn hình trông hoàn toàn bình thường.
 *
 * <p>⚠ Bài này canh **vế phân biệt** (luật 9): một màn hình luôn hiện lời nhắc, hoặc ⛔ không bao
 * giờ hiện, đều đi qua được một bài chỉ kiểm một trạng thái.
 */
// ⛔⛔ `testsupport/setup.ts` ⛔ KHÔNG đăng ký `cleanup` tự động, nên DOM của bài trước còn
//    nguyên khi bài sau chạy. Vế nguy hiểm ⛔ không phải bài này (nó đỏ ầm ĩ) mà là một khẳng định
//    KHẲNG ĐỊNH kiểu `getByText(...)` — nó có thể xanh nhờ một nút sót lại của bài TRƯỚC, tức xanh
//    vì lý do sai (luật 9). Sáu tệp khác trong kho đã tự gọi `cleanup`; đây là quy ước, ⛔ không
//    phải một mẹo.
afterEach(cleanup);

describe('SoDuPhepCard nói ra khác biệt giữa "chưa biết" và "đã dùng hết" — CN-04.9', () => {
  it('⭐ năm trước CHƯA có dữ liệu ⇒ màn hình phải NÓI RA, ⛔ không in một số 0 trần', () => {
    render(<SoDuPhepCard soDu={MOC} />);
    expect(screen.getByText(/chưa có dữ liệu nghỉ phép của năm 2025/i)).toBeInTheDocument();
  });

  it('⛔ năm trước ĐÃ có dữ liệu ⇒ ⛔ KHÔNG hiện lời nhắc ấy — vế phân biệt', () => {
    render(<SoDuPhepCard soDu={{ ...MOC, namTruocCoDuLieu: true, chuyenTuNamTruoc: '0.0' }} />);
    expect(screen.queryByText(/chưa có dữ liệu nghỉ phép của năm/i)).not.toBeInTheDocument();
  });

  it('⭐ số dư ÂM hiện nguyên giá trị âm — ⛔ không kẹp về 0', () => {
    // ⛔⛔ Một số âm là một sự thật cần ai đó xử lý (Công ty hạ tham số phép sau khi người ta đã
    //    nghỉ). Kẹp về 0 là giấu đúng trạng thái bất thường, và màn hình lại trông hoàn toàn bình
    //    thường — cùng hình dạng với "số 0 là một khẳng định".
    const { container } = render(<SoDuPhepCard soDu={{ ...MOC, conLai: '-2.0' }} />);
    expect(container.textContent).toContain('-2.0');
  });

  it('⛔ ⛔ Không tự cộng trừ: mọi con số hiện ra đúng bằng chuỗi backend gửi (quy tắc 2 · 3)', () => {
    // `NUMERIC(5,1)` đi qua `Number()` là mở đúng cửa sai số mà quy tắc 2 cấm. Giữ nguyên chuỗi
    // ⇒ phần thập phân `.0` phải còn nguyên trên màn hình.
    const { container } = render(<SoDuPhepCard soDu={{ ...MOC, duocHuong: '12.5' }} />);
    expect(container.textContent).toContain('12.5');
  });
});
