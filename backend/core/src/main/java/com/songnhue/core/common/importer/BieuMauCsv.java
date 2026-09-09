package com.songnhue.core.common.importer;

import java.util.List;

import com.songnhue.core.common.export.BangCsv;

/**
 * Sinh tệp mẫu CSV từ một danh mục {@link CotMau} — <b>một nguồn cho cả bộ đọc và tệp mẫu</b>.
 *
 * <h2>⭐⭐ Tệp mẫu là <i>tiêu đề + một dòng MÔ TẢ</i>, ⛔ KHÔNG phải dòng ví dụ hợp lệ</h2>
 *
 * <p>Dòng mô tả làm hai việc cùng lúc:
 *
 * <ol>
 *   <li><b>Dạy cách điền</b> ngay trong tệp — người lập tệp ⛔ không phải mở thêm tài liệu nào.
 *   <li><b>Là lưới an toàn</b>: tải mẫu về rồi nhập thẳng lại thì lượt chạy khô báo lỗi ở đúng dòng
 *       2 và ⛔ <b>không bản ghi rác nào</b> được tạo.
 * </ol>
 *
 * <p>Một tệp mẫu gồm các dòng ví dụ <i>hợp lệ</i> làm ngược lại: nó im lặng tạo ra đúng số bản ghi
 * ví dụ ấy, và ⛔ không có gì nói ra điều đó — người dùng thấy <i>"nhập thành công 2 hồ sơ"</i> và
 * tin rằng đó là hồ sơ của mình.
 *
 * <h2>⚠ BOM UTF-8 là bắt buộc</h2>
 *
 * <p>Excel bản Windows mở CSV không BOM thành tiếng Việt vỡ dấu, và người dùng sẽ "sửa lỗi phông"
 * bằng cách lưu lại ở một bảng mã khác — lúc ấy tệp gửi lên ⛔ không đọc được nữa, và nguyên nhân
 * nằm cách triệu chứng rất xa.
 */
public final class BieuMauCsv {

    private BieuMauCsv() {}

    public static byte[] dung(List<CotMau> cot) {
        if (cot == null || cot.isEmpty()) {
            // ⛔ Một tệp mẫu RỖNG tải về vẫn mở được trong Excel và trông như một tệp hợp lệ chưa
            //    điền. Ném ở đây thì lỗi hiện ra lúc gọi, ⛔ không hiện ra ở bàn người dùng.
            throw new IllegalArgumentException("Danh mục cột rỗng — tệp mẫu sẽ là một tệp trắng ⛔ không ai dùng được");
        }
        BangCsv b = new BangCsv();
        b.dong(cot.stream().map(CotMau::ten).toArray(Object[]::new));
        b.dong(cot.stream().map(CotMau::moTa).toArray(Object[]::new));
        return b.byteUtf8Bom();
    }
}
