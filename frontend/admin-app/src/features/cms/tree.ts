/**
 * Dựng cây từ danh sách phẳng — dùng chung cho danh mục nội dung, thư mục media và menu.
 *
 * Backend trả danh sách đã sắp theo materialized path, nên cha luôn đứng trước con và một
 * lượt duyệt là đủ. Đây là cùng một bài toán mà `OrgUnitTreeSelect` đã giải cho cơ cấu tổ
 * chức, nhưng ba loại nút ở đây có hình dạng khác nhau — nên nhận vào **hàm đọc khoá** thay
 * vì ép ba kiểu về một.
 */

export interface TreeNodeLike {
  publicId: string;
  parentPublicId: string | null;
}

export interface TreeItem<T> {
  key: string;
  value: T;
  children: TreeItem<T>[];
}

/**
 * @returns danh sách nút gốc, mỗi nút mang con của nó
 *
 * ⚠ Nút có cha **không tìm thấy** được nâng lên cấp gốc chứ không bị bỏ. Mất một danh mục
 * khỏi cây là thứ không ai phát hiện (nó chỉ biến mất); thừa một nút ở sai cấp thì thấy
 * ngay. Tình huống này có thật: người dùng lọc cây hoặc một nhánh cha đang bị ẩn.
 */
export function buildTree<T extends TreeNodeLike>(items: readonly T[]): TreeItem<T>[] {
  const byId = new Map<string, TreeItem<T>>();
  for (const item of items) {
    byId.set(item.publicId, { key: item.publicId, value: item, children: [] });
  }

  const roots: TreeItem<T>[] = [];
  for (const item of items) {
    const node = byId.get(item.publicId);
    if (!node) {
      continue;
    }
    const parent = item.parentPublicId ? byId.get(item.parentPublicId) : undefined;
    if (parent) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }
  return roots;
}

/**
 * Tập id của một nút và toàn bộ con cháu.
 *
 * Dùng để **chặn kéo một nút vào chính nhánh con của nó** — thao tác đó cắt cả nhánh ra
 * khỏi cây và không có thông báo lỗi nào ở giao diện. Backend cũng chặn, nhưng để người
 * dùng thả xuống rồi mới báo lỗi là bắt họ làm lại từ đầu.
 */
export function subtreeIds<T extends TreeNodeLike>(
  items: readonly T[],
  rootId: string,
): Set<string> {
  const conCua = new Map<string, string[]>();
  for (const item of items) {
    if (item.parentPublicId) {
      const list = conCua.get(item.parentPublicId) ?? [];
      list.push(item.publicId);
      conCua.set(item.parentPublicId, list);
    }
  }

  const result = new Set<string>([rootId]);
  const stack = [rootId];
  while (stack.length > 0) {
    const current = stack.pop();
    if (current === undefined) {
      break;
    }
    for (const child of conCua.get(current) ?? []) {
      if (!result.has(child)) {
        result.add(child);
        stack.push(child);
      }
    }
  }
  return result;
}

/** Có được phép thả `dragId` vào làm con của `dropId` không. */
export function canDropInto<T extends TreeNodeLike>(
  items: readonly T[],
  dragId: string,
  dropId: string | null,
): boolean {
  if (dropId === null) {
    // Thả ra vùng trống = đưa lên cấp gốc, luôn hợp lệ.
    return true;
  }
  if (dragId === dropId) {
    return false;
  }
  return !subtreeIds(items, dragId).has(dropId);
}
