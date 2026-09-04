package com.songnhue.core.spi;

import java.util.UUID;

/**
 * Một công trình, nhìn từ ngoài module {@code operations} — T28.19.
 *
 * <p>Cố ý <b>mỏng</b>: đúng những trường mà một module khác cần để (a) hiện tên cho người dùng chọn,
 * (b) lưu khoá liên kết, (c) biết đơn vị quản lý để tìm người nhận cảnh báo. ⛔ Không mang thông số
 * kỹ thuật, trạng thái vận hành hay toạ độ — thêm trường vào đây là kéo một mảnh lược đồ của
 * {@code operations} ra ngoài ranh giới của nó, và ngày lược đồ ấy đổi thì hai module cùng đổ.
 *
 * @param id khoá nội bộ — cần vì {@code station_constructions.construction_id} lưu khoá đó để join
 *     nhanh trong cùng một CSDL. ⛔ Không bao giờ ra tới API
 * @param publicId định danh ổn định, dùng ở API và khi đối chiếu giữa hai module
 * @param orgUnitId đơn vị quản lý công trình — <b>{@code NOT NULL} ở lược đồ</b>, nên ⛔ không có
 *     nhánh "chưa gán". Đây là mắt xích mà {@code RecipientResolver} đi tiếp để tìm trưởng/phó đơn
 *     vị nhận cảnh báo ngưỡng (chốt G11)
 * @param lifecycleState {@code DANG_SU_DUNG} · {@code BAO_TRI} · {@code NGUNG_MUA_VU} ·
 *     {@code DA_THANH_LY} — trả ra chuỗi, ⛔ không trả enum của {@code operations}: một enum đi qua
 *     ranh giới module là cùng thứ ràng buộc mà khoá ngoại xuyên module tạo ra, chỉ khó thấy hơn
 */
public record ConstructionRef(
        Long id, UUID publicId, String code, String name, Long orgUnitId, String lifecycleState) {}
