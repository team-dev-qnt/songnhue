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
  /**
   * **T74.11** — làm mờ đơn vị NGOÀI phạm vi ghi của người đăng nhập.
   *
   * ⛔⛔ Chỉ bật ở biểu mẫu mà backend **thật sự** chặn, tức đường ghi đi qua
   * `ScopeGuard.requireWritableOrgUnit`. Đo 23/09: đúng **4** đường — công trình
   * (`ConstructionService`) · hồ sơ CBNV (`EmployeeService`) · điểm đo (`StationService`) · uỷ
   * quyền duyệt phép (`UyQuyenDuyetPhepService`).
   *
   * Bật ở chỗ khác là **bày ra một điều cấm ⛔ hề tồn tại**: sáu ô chọn còn lại ghi vào bảng ⛔
   * thuộc `ScopedEntity` (`User` · `Contact` · `ConstructionCluster` · `OrgUnit`) hoặc ghi một cột
   * *dữ liệu* (`MaintenanceFormModal` → `performerOrgUnitId`, *đơn vị thực hiện*, tách hẳn khỏi cột
   * phạm vi). Làm mờ ở đó thì người dùng đi xin quyền cho một thứ họ vốn đã làm được.
   */
  chiTrongPhamVi = false,
}: {
  value?: string;
  onChange?: (value: string | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
  allowClear?: boolean;
  onlyTypes?: readonly OrgUnitNode['unitType'][];
  chiTrongPhamVi?: boolean;
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
    () => (data ?? []).map((node) => toTreeNode(node, onlyTypes, chiTrongPhamVi)),
    [data, onlyTypes, chiTrongPhamVi],
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
      showSearch={{ treeNodeFilterProp: 'title' }}
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

function toTreeNode(
  node: OrgUnitNode,
  onlyTypes?: readonly OrgUnitNode['unitType'][],
  chiTrongPhamVi = false,
): TreeNode {
  const hopLoai = !onlyTypes || onlyTypes.includes(node.unitType);
  // T74.11 — `trongPhamVi` là cờ MỚI, nên một phản hồi cũ còn trong đệm ⛔ có nó. `=== false`
  // chứ ⛔ `!node.trongPhamVi`: `undefined` phải đọc là *chưa biết* và **⛔ làm mờ**, vì làm mờ
  // theo mặc định là khoá người dùng ra khỏi chính đơn vị của họ (quy tắc 16 — số 0 là một
  // khẳng định, và ở đây *chưa có dữ liệu* ⛔ được nói thành *anh ⛔ có quyền*).
  const ngoaiPhamVi = chiTrongPhamVi && node.trongPhamVi === false;
  return {
    value: node.publicId,
    title: node.shortName ? `${node.name} (${node.shortName})` : node.name,
    // Nút cha không chọn được vẫn phải hiện ra: bỏ nó đi là con của nó mất luôn đường
    // hiển thị, dù chính con mới là thứ cần chọn. ⇒ Ngoài phạm vi thì MỜ, ⛔ phải BIẾN MẤT:
    // một Xí nghiệp ngoài phạm vi vẫn có thể là cha của Tổ đội mà người dùng được ghi vào.
    selectable: hopLoai && !ngoaiPhamVi,
    disabled: !node.active || ngoaiPhamVi,
    children:
      node.children.length > 0
        ? node.children.map((c) => toTreeNode(c, onlyTypes, chiTrongPhamVi))
        : undefined,
  };
}
