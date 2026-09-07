package com.songnhue.operations.api.dto;

import com.songnhue.operations.domain.OperationalStatus;

/**
 * Phần thân chung của {@link OperationStatusCodeCreateRequest} và
 * {@link OperationStatusCodeUpdateRequest}.
 *
 * <p>Hai DTO đó giống nhau từng trường, chỉ khác giá trị mặc định của {@code active}. Trước đây tầng
 * service chép nguyên khối tám lệnh {@code set} hai lần; thêm một trường vào danh mục thì phải nhớ
 * sửa cả hai chỗ, và chỗ quên sẽ im lặng bỏ rơi đúng trường mới thêm. Rút ra một giao diện đọc chung
 * để chỉ còn <b>một</b> khối gán.
 *
 * <p>Cố ý <b>không</b> gộp hai DTO làm một: ràng buộc kiểm tra của chúng có quyền tách nhau về sau
 * (ví dụ cấm đổi {@code code} khi sửa), và gộp lại thì mất chỗ để ghi ràng buộc đó.
 */
public interface OperationStatusCodeFields {

    String getName();

    boolean isHasParameter();

    String getParameterUnit();

    String getColorHex();

    OperationalStatus getMappedStatus();

    Integer getSortOrder();

    boolean isActive();
}
