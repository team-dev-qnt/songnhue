package com.songnhue.core.spi;

import java.util.List;

/**
 * Module nào có cấu hình riêng thì khai một bean — T61.41. Màn hình gom mọi bean, nên phạm vi do Spring ĐO chứ ⛔ do
 * một danh sách gõ tay (luật 28).
 */
public interface NguonTinhTrangCauHinh {

    List<MucCauHinh> mucCauHinh();
}
