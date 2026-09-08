package com.songnhue.core.api.attachment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.application.attachment.AttachmentService;
import com.songnhue.core.common.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Tệp đính kèm — {@code /api/v1/attachments/**} (pattern P3).
 *
 * <p>⭐ <b>Thu còn ĐÚNG MỘT endpoint ngày 08/09/2026</b> (T28.47) — xem bia mộ trong thân lớp. Bốn
 * endpoint kia có <b>0 nơi gọi từ giao diện</b>, và bề mặt ⛔ không ai dùng là bề mặt ⛔ không ai
 * canh: chính lớp này là nơi lỗ phân quyền A1 sống sót từ WS-6 tới 04/09/2026.
 *
 * <p>⚠ Vì sao {@code DELETE} <b>ở lại</b> trong khi nó cũng ⛔ không có nơi gọi: nó là chỗ duy nhất
 * mang bài kiểm hồi quy cho lỗ A1 ({@code AttachmentDeleteHttpTest}), và nay là một trong bốn cửa đi
 * qua chốt {@code AttachmentUsagePort} (T40.26). Gỡ nó là gỡ luôn phép đo chứng minh lỗ ấy đã đóng.
 */
@RestController
@RequestMapping("/api/v1/attachments")
@Tag(name = "00-core · Tệp đính kèm", description = "Xoá mềm tài liệu — đường chung")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /*
     * ⛔⛔ BIA MỘ — bốn endpoint đã GỠ ngày 08/09/2026 (T28.47).
     *
     *   POST   /api/v1/attachments                     (tải lên, `ops:document:upload`)
     *   GET    /api/v1/attachments/{publicId}          (thông tin,  `ops:document:view`)
     *   GET    /api/v1/attachments/{publicId}/download-url
     *   GET    /api/v1/attachments?ownerType&ownerId   (danh sách)
     *
     * ⚠ Lý do đo được, ⛔ không phải cảm tính: cả bốn có **0 nơi gọi từ giao diện**. Ba lượt `grep`
     * trúng chuỗi `/api/v1/attachments` trong `frontend/` đều là **chú thích** kể lại chuyện
     * `AttachmentPanel.tsx` bị xoá ngày 04/09. Màn hình thật đi đường riêng của từng module:
     * `/ops/constructions/{id}/documents…` và `/cms/media/…`.
     *
     * ⭐ Và javadoc cũ của lớp này tự bào chữa bằng một lý do ĐÚNG nhưng ⛔ KHÔNG áp cho nó: *"kiểm
     * magic bytes, mã hoá lại ảnh, quét virus phải giống hệt nhau ở mọi chỗ"*. Thứ bảo đảm điều ấy
     * là {@code AttachmentService}, ⛔ không phải endpoint này — mọi module đã gọi service. Endpoint
     * chỉ là một cửa thứ hai vào cùng một phòng, và là cửa ⛔ không ai canh.
     *
     * ⛔ Bề mặt ⛔ không ai dùng ⛔ không phải bề mặt vô hại: chính lớp này là nơi lỗ phân quyền A1
     * sống sót từ WS-6 tới 04/09/2026 — `DELETE` gác nhầm `ops:document:upload` trong khi hai đường
     * riêng gác `:delete`. Nó sống được vì tầng 1 (menu) và tầng 2 (nút) đều đúng, nên ⛔ không màn
     * hình nào lộ ra gì; chỉ tầng 3 sai, và người khai thác ⛔ không cần giao diện.
     *
     * ⬜ Cần lại một trong bốn cái ấy thì thêm CÙNG với màn hình gọi nó — quy tắc 15: một endpoint
     * ⛔ không ai gọi là một lỗi, ⛔ không phải một khoản để dành.
     */

    /**
     * ⭐⭐ Gác bằng {@code ops:document:delete} — <b>sửa 04/09/2026</b>, trước đó là
     * {@code ops:document:upload}.
     *
     * <h2>Đường CHUNG rộng hơn đường RIÊNG, và cái rộng hơn ấy là cái không ai canh</h2>
     *
     * <p>Đo trên ma trận seed: {@code TECHNICIAN} · {@code XN_MANAGER} · {@code XN_OPERATOR} đều có
     * {@code ops:document:upload}, và ⛔ <b>không</b> ai trong ba vai trò ấy có
     * {@code ops:document:delete} (chỉ SUPER_ADMIN + ADMIN). Hai controller khác gác đúng —
     * {@code ConstructionDocumentController} và {@code MaintenanceLogController} đều đòi
     * {@code :delete} — và giao diện cũng ẩn nút theo đúng mã ấy
     * ({@code ConstructionDocumentsPanel}). ⇒ ba vai trò <b>không thấy nút xoá ở đâu cả</b> mà vẫn
     * xoá được <b>bất kỳ</b> tệp nào trong hệ bằng một lượt {@code DELETE} thẳng vào đường chung.
     *
     * <p>⚠ Đây là hình dạng nguy hiểm nhất của một lỗ phân quyền: <b>tầng 1 (menu) và tầng 2 (nút)
     * đều đúng</b>, nên ⛔ không màn hình nào lộ ra điều gì. Chỉ tầng 3 sai, và tầng 3 là tầng duy
     * nhất thật sự chặn.
     *
     * <p>⛔ Cách sửa <b>sai</b> là nới {@code ConstructionDocumentController} xuống
     * {@code :upload} cho "nhất quán" — nó biến một lỗ thành ba lỗ. Đường riêng đang khai đúng ý
     * định của Công ty; đường chung phải đi theo nó.
     *
     * <p>📌 Nếu Công ty muốn người tải lên tự xoá được tệp mình vừa tải nhầm thì đó là một
     * <b>quyết định về ma trận quyền</b> — cấp {@code ops:document:delete} cho vai trò ấy bằng
     * migration, ⛔ không hạ chốt chặn của một endpoint. ⚠ Và chừng nào CN-05.2 (màn hình sửa ma
     * trận quyền) chưa có thì mọi thay đổi như vậy đều đòi một lượt deploy — nợ ấy đã ghi.
     */
    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm bản ghi — tệp trên kho vẫn giữ để nhật ký không trỏ vào khoảng không")
    @RequirePermission("ops:document:delete")
    public void delete(@PathVariable UUID publicId) {
        attachmentService.delete(publicId);
    }

    /*
     * ⛔ BIA MỘ — lớp lồng `AttachmentDtos` (hai record `AttachmentView` và `DownloadUrl`) gỡ ngày
     *    08/09/2026 cùng bốn endpoint ở trên. Nó chỉ phục vụ chúng, nên khi chúng đi thì nó còn
     *    **0 nơi dùng** — một cấu trúc dữ liệu ⛔ không ai đọc là một lỗi, ⛔ không phải một khoản
     *    để dành (quy tắc 15).
     *
     * ⚠ Bốn kiểu TypeScript soi gương nó (`ScanStatus` · `AttachmentStatus` · `AttachmentView` ·
     *   `DownloadUrl`) gỡ cùng lượt ở `admin-app/src/shared/api-types.ts`. Gỡ một phía là để lại
     *   đúng thứ T28.47 sinh ra để dọn.
     */
}
