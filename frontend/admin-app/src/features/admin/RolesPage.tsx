import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Col, Empty, List, Row, Spin, Table, Tag, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useMemo, useState } from 'react';

import { type RoleSummary } from '@/shared/api-types';
import { api } from '@/shared/apiClient';

/**
 * Vai trò & phân quyền — **chỉ xem** ở Phase 0.
 *
 * Ma trận 12 vai trò × 88 quyền (334 dòng phân quyền) được nạp bằng migration ở WS-2,
 * dịch thẳng từ ma trận RBAC trong `function-spec.md` §6, và có bài kiểm ở CI đối chiếu
 * lại từng dòng. Mở cho sửa trên giao diện lúc này là để một thao tác nhấp chuột phá vỡ
 * thứ mà cả một bộ kiểm thử đang canh — trong khi việc thật sự hay làm là **gán vai trò
 * cho người**, và việc đó đã có ở màn hình Tài khoản.
 *
 * Sửa được ma trận là hạng mục của Phase 1 khi nghiệp vụ đã ổn định.
 */
export function RolesPage() {
  const [selected, setSelected] = useState<string | null>(null);

  const roles = useQuery({
    queryKey: ['admin', 'roles', 'catalog'],
    queryFn: () => api.get<RoleSummary[]>('/admin/users/roles/catalog'),
  });

  const permissions = useQuery({
    queryKey: ['admin', 'roles', selected, 'permissions'],
    queryFn: () => api.get<string[]>(`/admin/users/roles/${selected}/permissions`),
    enabled: selected !== null,
  });

  const grouped = useMemo(() => groupByModule(permissions.data ?? []), [permissions.data]);

  const columns: ColumnsType<RoleSummary> = [
    { title: 'Mã', dataIndex: 'code', width: 170, render: (code: string) => <Tag>{code}</Tag> },
    { title: 'Tên vai trò', dataIndex: 'name' },
    { title: 'Số quyền', dataIndex: 'permissionCount', width: 100, align: 'right' },
  ];

  return (
    <Row gutter={16}>
      <Col xs={24} lg={13}>
        <Card title="Vai trò">
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="Ma trận phân quyền là dữ liệu nền, chỉ xem"
            description="Gán vai trò cho người dùng ở màn hình Tài khoản. Thay đổi ma trận vai trò × quyền được thực hiện bằng migration để giữ nguyên dấu vết và bài kiểm đối chiếu."
          />
          <Table<RoleSummary>
            columns={columns}
            dataSource={roles.data ?? []}
            rowKey="code"
            loading={roles.isLoading}
            pagination={false}
            onRow={(row) => ({
              onClick: () => setSelected(row.code),
              style: { cursor: 'pointer' },
            })}
            rowClassName={(row) => (row.code === selected ? 'ant-table-row-selected' : '')}
          />
        </Card>
      </Col>

      <Col xs={24} lg={11}>
        <Card title={selected ? `Quyền của ${selected}` : 'Quyền'}>
          {!selected && <Empty description="Chọn một vai trò để xem danh sách quyền" />}
          {selected && permissions.isLoading && <Spin />}
          {selected && !permissions.isLoading && (
            <List
              dataSource={Object.entries(grouped)}
              renderItem={([module, codes]) => (
                <List.Item>
                  <List.Item.Meta
                    title={
                      <Typography.Text strong>{MODULE_LABELS[module] ?? module}</Typography.Text>
                    }
                    description={
                      <>
                        {codes.map((code) => (
                          <Tag key={code} style={{ marginBottom: 4 }}>
                            {code}
                          </Tag>
                        ))}
                      </>
                    }
                  />
                </List.Item>
              )}
            />
          )}
        </Card>
      </Col>
    </Row>
  );
}

const MODULE_LABELS: Record<string, string> = {
  adm: 'MOD-05 · Quản trị',
  cms: 'MOD-01 · Cổng thông tin',
  ops: 'MOD-02 · Vận hành công trình',
  hyd: 'MOD-03 · Thủy văn',
  hr: 'MOD-04 · Nhân sự',
};

/** Mã quyền có dạng `module:resource:action` — nhóm theo phần đầu cho dễ đọc. */
function groupByModule(codes: readonly string[]): Record<string, string[]> {
  return codes.reduce<Record<string, string[]>>((acc, code) => {
    const module = code.split(':')[0] ?? 'khac';
    (acc[module] ??= []).push(code);
    return acc;
  }, {});
}
