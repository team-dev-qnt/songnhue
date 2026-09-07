package com.songnhue.core.spi;

import java.util.UUID;

/**
 * Một tệp đính kèm vừa bị <b>xoá mềm</b> — mọi module đang trỏ tới nó phải gỡ tham chiếu.
 * <b>T28.34</b>.
 *
 * <h2>⛔⛔ Vì sao {@code ON DELETE SET NULL} của CSDL ⛔ KHÔNG cứu được</h2>
 *
 * <p>Năm cột trong hai module khai {@code REFERENCES attachments (public_id) ON DELETE SET NULL}:
 *
 * <ul>
 *   <li>{@code constructions.operating_procedure_attachment_public_id} ·
 *       {@code constructions.protection_plan_attachment_public_id} ({@code operations});
 *   <li>{@code categories.cover_attachment_public_id} · {@code articles.cover_attachment_public_id}
 *       · {@code menu_items.logo_attachment_public_id} ({@code content}).
 * </ul>
 *
 * <p>Cả năm đọc như một bảo đảm hoàn chỉnh, và cả năm <b>chưa từng bắn một lần nào</b> — vì
 * {@code AttachmentService.delete} <b>xoá mềm</b> ({@code deleted_at = now()}), ⛔ không
 * {@code DELETE FROM attachments}. Với CSDL thì ⛔ không có gì bị xoá, nên ⛔ không có gì để
 * {@code SET NULL}. Quy tắc 9 của dự án (soft delete cho mọi entity nghiệp vụ) và luật ràng buộc
 * của CSDL <b>loại trừ nhau</b>, và ⛔ không ai để ý vì mỗi vế nhìn riêng đều đúng.
 *
 * <h2>Triệu chứng đo được: một liên kết tải về trả 404 CÂM trên cổng công khai</h2>
 *
 * <p>{@code readForPublic} lọc {@code deleted_at IS NULL} ⇒ trả {@code Optional.empty()} ⇒
 * {@code SYS-0004} ⇒ <b>404</b>. Nhưng cột ở {@code constructions} <b>vẫn giữ UUID</b>, nên cổng
 * <b>vẫn dựng liên kết</b>. Người dân bấm "Quy trình vận hành" và nhận một trang lỗi; quản trị viên
 * mở màn hình công trình thì thấy tài liệu <i>vẫn được khai</i>. ⛔ Không dòng log nào, ⛔ không
 * cảnh báo nào.
 *
 * <h2>⭐ Vì sao SỰ KIỆN, ⛔ không phải một cổng SPI mới</h2>
 *
 * <p>Đây là quan hệ <b>một-phát nhiều-nghe</b>, và số người nghe còn tăng: mỗi module thêm một cột
 * trỏ tệp là thêm một người nghe. Một cổng SPI thì {@code core} phải biết tên từng module đang trỏ
 * tới nó — tức đảo đúng chiều phụ thuộc mà quy tắc 6 dựng lên. Sự kiện thì {@code core} chỉ nói
 * <i>"tệp này không còn"</i> và ⛔ không cần biết ai quan tâm.
 *
 * <h2>⚠ Người nghe chạy TRONG giao dịch của lượt xoá, ⛔ không {@code AFTER_COMMIT}</h2>
 *
 * <p>Cố ý khác {@link SettingChangedEvent} (nơi {@code AFTER_COMMIT} là bắt buộc, vì việc cần làm là
 * <i>dọn bộ nhớ đệm</i> — làm sớm thì nạp lại giá trị cũ). Ở đây việc cần làm là <b>ghi dữ liệu</b>,
 * và nó phải <b>nguyên tử</b> với lượt xoá: đúng thứ mà {@code ON DELETE SET NULL} lẽ ra đã làm.
 * Tách ra một giao dịch sau là mở một khe hở mà trong đó cổng công khai trỏ vào một tệp đã chết —
 * ⛔ chính là khuyết tật đang được sửa, thu nhỏ lại chứ ⛔ không biến mất.
 *
 * <p>⇒ Người nghe ném thì <b>lượt xoá rollback</b>. Đó là hành vi mong muốn: thà từ chối xoá còn hơn
 * để lại một tham chiếu treo mà ⛔ không ai biết.
 *
 * @param publicId khoá công khai của tệp — ⛔ không phải khoá bigint nội bộ, vì các cột trỏ tới nó
 *     đều là {@code UUID} (module khác ⛔ không import entity của {@code core}, quy tắc 6)
 * @param ownerType để người nghe lọc sớm; ⛔ <b>không</b> được dùng làm điều kiện DUY NHẤT — một tệp
 *     tải lên với {@code ownerType} này vẫn có thể được module khác trỏ tới
 */
public record AttachmentDeletedEvent(UUID publicId, String ownerType) {}
