package com.songnhue.core.spi;

/**
 * Một tham số vừa đổi giá trị — phát sau khi giao dịch commit.
 *
 * <h2>Vì sao cần sự kiện, trong khi {@code SettingService} đã tự dọn bộ nhớ đệm của nó</h2>
 *
 * Bộ nhớ đệm của {@code SettingService} nằm ở tầng <i>từng khoá</i>. Cổng công khai thì không đọc
 * từng khoá — nó đọc <b>cả cụm</b> (tên site, logo, màu, footer, mạng xã hội…) và dựng sẵn một đối
 * tượng để trả về, nên module nghiệp vụ buộc phải có bộ nhớ đệm riêng ở tầng cụm.
 *
 * <p>Hai bộ nhớ đệm thì phải có đường nối, và đường nối đó <b>không được đi qua sự tự giác</b>: cùng
 * một dòng {@code settings} sửa được từ <i>hai</i> màn hình — cấu hình giao diện (CMS) và cấu hình hệ
 * thống (MOD-05). Nếu chỉ màn hình CMS chủ động dọn bộ nhớ đệm thì Quản trị viên hệ thống sửa tên
 * site ở màn hình kia, giao diện báo thành công, và cổng vẫn hiện tên cũ — không lỗi, không dấu vết.
 *
 * <p>Phát ở {@code SettingService.update} — nơi <b>duy nhất</b> ghi bảng {@code settings} — nên cả
 * hai đường sửa đều đi qua đây.
 *
 * <p>⚠ Người nghe phải dùng {@code @TransactionalEventListener(phase = AFTER_COMMIT)}. Dọn bộ nhớ đệm
 * <i>trước</i> khi commit thì lượt đọc kế tiếp nạp lại đúng giá trị cũ (giao dịch chưa nhìn thấy
 * được), rồi giao dịch rollback — bộ nhớ đệm vừa được làm mới bằng dữ liệu sai và không ai dọn nó
 * lần nữa.
 *
 * @param groupCode nhóm của tham số, để người nghe lọc mà không phải tra lại DB
 */
public record SettingChangedEvent(String key, String groupCode) {}
