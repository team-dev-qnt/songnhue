import { useQuery } from '@tanstack/react-query';
import { TreeSelect } from 'antd';
import { useMemo } from 'react';

import { type OrgUnitNode } from '@/shared/api-types';
import { api } from '@/shared/apiClient';

/**
 * Ô chọn đơn vị theo cây tổ chức.
 *
 * `org_units` là **một bảng dùng chung** cho Xí nghiệp (MOD-02) và phòng ban (MOD-04 HRM)
 * — CLAUDE.md quy tắc 7 — nên đúng component này dùng lại được ở cả hai nhóm module, và
 * cũng là lý do không tách thành "chọn xí nghiệp" với "chọn phòng ban".
 *
 * Cây tải một lượt rồi giữ lại lâu: sơ đồ tổ chức của một công ty thay đổi vài lần một
 * năm, tải lại mỗi lần mở ô chọn là phí.
 */
export function OrgUnitTreeSelect({
  value,
  onChange,
  placeholder = 'Chọn đơn vị',
  disabled,
  allowClear = true,
  /** Chỉ cho chọn đúng những loại này — VD chỉ Xí nghiệp khi gán công trình. */
  onlyTypes,
}: {
  value?: string;
  onChange?: (value: string | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
  allowClear?: boolean;
  onlyTypes?: readonly OrgUnitNode['unitType'][];
}) {
  const { data, isLoading } = useQuery({
    // ⚠ `/selectable` chứ không phải `/tree`: đường `/tree` đứng sau `adm:org-unit:view`, quyền mà
    // TECHNICIAN — vai trò DUY NHẤT tạo được công trình — không có. Component này có mặt trong biểu
    // mẫu của nhiều module, nên gate nó bằng quyền quản trị là khoá đúng người cần dùng nó.
    queryKey: ['org-units', 'selectable'],
    queryFn: () => api.get<OrgUnitNode[]>('/org-units/selectable'),
    staleTime: 10 * 60 * 1000,
  });

  const treeData = useMemo(
    () => (data ?? []).map((node) => toTreeNode(node, onlyTypes)),
    [data, onlyTypes],
  );

  return (
    <TreeSelect
      value={value}
      onChange={onChange}
      treeData={treeData}
      loading={isLoading}
      placeholder={placeholder}
      disabled={disabled}
      allowClear={allowClear}
      showSearch
      treeNodeFilterProp="title"
      treeDefaultExpandAll
      style={{ width: '100%' }}
    />
  );
}

interface TreeNode {
  value: string;
  title: string;
  selectable: boolean;
  disabled: boolean;
  children?: TreeNode[];
}

function toTreeNode(node: OrgUnitNode, onlyTypes?: readonly OrgUnitNode['unitType'][]): TreeNode {
  const selectable = !onlyTypes || onlyTypes.includes(node.unitType);
  return {
    value: node.publicId,
    title: node.shortName ? `${node.name} (${node.shortName})` : node.name,
    // Nút cha không chọn được vẫn phải hiện ra: bỏ nó đi là con của nó mất luôn đường
    // hiển thị, dù chính con mới là thứ cần chọn.
    selectable,
    disabled: !node.active,
    children:
      node.children.length > 0 ? node.children.map((c) => toTreeNode(c, onlyTypes)) : undefined,
  };
}
