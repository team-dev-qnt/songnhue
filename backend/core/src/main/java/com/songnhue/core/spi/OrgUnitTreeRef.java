package com.songnhue.core.spi;

import java.util.List;

/**
 * Một dòng của cây tổ chức, dạng <b>PHẲNG</b>, cho module nghiệp vụ dựng sơ đồ — CN-04.1.
 *
 * <h2>Vì sao phẳng chứ ⛔ không lồng sẵn</h2>
 *
 * <p>{@code core.application.org.OrgUnitNode} (bản lồng) nằm ngoài {@code core.spi}, nên module
 * nghiệp vụ ⛔ không import được — ArchUnit {@code ModuleBoundaryTest} chặn, và đúng. Trả một danh
 * sách phẳng kèm {@code parentId} cho phép bên gọi dựng cây bằng
 * {@code core.common.tree.TreeBuilder} — thứ <b>đã có</b> và đã được dùng ở hai nơi.
 *
 * <p>⭐ Nó còn cho bên gọi <b>gắn thêm dữ liệu của chính mình</b> vào từng nút (ở đây là quân số)
 * trước khi dựng cây. Một cây lồng sẵn thì phải duyệt lại một lần nữa để gắn.
 *
 * @param parentId {@code null} = nút gốc
 * @param active đơn vị đã tắt vẫn trả về — sơ đồ phải <b>hiện</b> nó kèm nhãn, ⛔ không được lặng
 *     lẽ bỏ đi: một đơn vị biến mất khỏi sơ đồ trong khi hồ sơ vẫn trỏ vào nó là đúng triệu chứng
 *     mà WS-56 phải dựng chốt chặn để tránh
 */
public record OrgUnitTreeRef(
        OrgUnitRef donVi, Long parentId, int sortOrder, boolean active, List<OrgUnitLeaderRef> lanhDao) {}
