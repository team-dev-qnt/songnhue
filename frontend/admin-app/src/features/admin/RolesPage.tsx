import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  Col,
  Empty,
  List,
  Modal,
  Row,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useMemo, useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { type PermissionSummary, type RoleSummary } from '@/shared/api-types';
import { api, ApiClientError } from '@/shared/apiClient';

/**
 * Vai trò & phân quyền — **sửa được** từ T27.31 (CN-05.2).
 *
 * ## ⛔ Vì sao màn hình này bị khoá "chỉ xem" suốt Phase 0–1, và vì sao lý do ấy sai
 *
 * Bản trước ghi: *"mở cho sửa lúc này là để một thao tác nhấp chuột phá vỡ thứ mà cả một bộ kiểm
 * thử đang canh"*, và hiện câu ấy ra màn hình cho người dùng đọc. Đo lại 08/09/2026: `RbacMatrixTest`
 * ⛔ **không** đối chiếu từng dòng ma trận. Phép khẳng định duy nhất chạm tới số lượng là một **SÀN**
 * (`role_permissions >= 300`), đặt ở đó để chặn kiểu hỏng *seed không chạy*. Sửa ma trận từ đây ⛔
 * không làm bài nào đỏ.
 *
 * ⇒ Một quyết định thiết kế đứng suốt hai phase trên một **lời mô tả sai về một bài kiểm** — và lời
 * mô tả ấy được chép ra ba nơi (javadoc bài kiểm, javadoc tệp này, sổ tracking), nên nó đọc như đã
 * được xác nhận ba lần.
 *
 * ## Ba mảnh nằm ngủ từ Phase 0, mỗi mảnh viết sẵn cho đúng màn hình này
 *
 * - `adm:role:manage` — trước T27.31 có **đúng 1** lượt xuất hiện trong **mã nguồn**: dòng *miễn
 *   kiểm* của `RbacMatrixTest`. ⚠ Phép đếm đầu ghi *"toàn kho"* và sai — `git grep` cho 3 lượt ở 2
 *   tệp; `rg` mặc định bỏ qua `.claude/`.
 * - `roles.is_system` — một cột ⛔ không ai đọc; bảo đảm "⛔ không sửa được" chỉ nằm trong một dòng
 *   chú thích SQL.
 * - `AuthorityLoader.invalidateAll()` — **0 nơi gọi**, javadoc ghi thẳng *"Gọi khi sửa quyền của một
 *   vai trò"*.
 *
 * ## ⚠ Ba điều màn hình này cố ý ⛔ KHÔNG tự quyết
 *
 * 1. **⛔ Không tự loại quyền khỏi danh sách** dù người dùng ⛔ không có `adm:role:manage` — cả danh
 *    mục vẫn hiện ra, chỉ ⛔ không tick được. Ẩn đi thì người dùng ⛔ không phân biệt được *"⛔ không
 *    có quyền ấy"* với *"quyền ấy ⛔ không tồn tại"*.
 * 2. **⛔ Không gửi PATCH thêm/bớt** — gửi cả tập, vì hai người sửa cùng lúc ⛔ không được ra một kết
 *    quả lai mà ⛔ không ai chọn.
 * 3. **⛔ Không tự bỏ tick hộ** khi người dùng sắp tự khoá mình — backend từ chối bằng `ADM-2016` và
 *    nói ra lý do. Sửa hộ trong im lặng là làm người dùng tin họ đã lưu một thứ khác thứ đã lưu.
 */
export function RolesPage() {
  const { message } = App.useApp();
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();

  const [selected, setSelected] = useState<string | null>(null);
  const [nhap, setNhap] = useState<Set<string> | null>(null);

  const suaDuoc = hasPermission('adm:role:manage');

  const roles = useQuery({
    queryKey: ['admin', 'roles', 'catalog'],
    queryFn: () => api.get<RoleSummary[]>('/admin/users/roles/catalog'),
  });

  const catalog = useQuery({
    queryKey: ['admin', 'permissions', 'catalog'],
    queryFn: () => api.get<PermissionSummary[]>('/admin/users/permissions/catalog'),
  });

  const permissions = useQuery({
    queryKey: ['admin', 'roles', selected, 'permissions'],
    queryFn: () => api.get<string[]>(`/admin/users/roles/${selected}/permissions`),
    enabled: selected !== null,
  });

  const vaiTroDangChon = roles.data?.find((r) => r.code === selected) ?? null;
  const khoa = vaiTroDangChon?.isSystem === true;

  const daChon = useMemo(
    () => nhap ?? new Set(permissions.data ?? []),
    [nhap, permissions.data],
  );

  const banGoc = useMemo(() => new Set(permissions.data ?? []), [permissions.data]);
  const coThayDoi = nhap !== null && !bangNhau(nhap, banGoc);

  const grouped = useMemo(() => groupByModule(catalog.data ?? []), [catalog.data]);

  /**
   * Đổi vai trò đang xem — và **xoá ô nháp trong cùng một nhịp**.
   *
   * ⛔ Bản đầu làm việc này bằng `useEffect(() => setNhap(null), [selected])`, và `eslint` từ chối
   * đúng chỗ: `react-hooks/set-state-in-effect`. Nó ⛔ không chỉ là chuyện hiệu năng — một effect
   * chạy SAU lượt render đầu tiên của vai trò mới, nên có đúng một khung hình mà màn hình hiện
   * **tick của vai trò cũ trên tên vai trò mới**. Đặt hai lệnh cạnh nhau ở đây thì trạng thái ấy
   * ⛔ không tồn tại.
   */
  function chonVaiTro(ma: string) {
    setSelected(ma);
    setNhap(null);
  }

  const luu = useMutation({
    mutationFn: (codes: string[]) =>
      api.put<void>(`/admin/users/roles/${selected}/permissions`, { permissionCodes: codes }),
    onSuccess: async () => {
      message.success('Đã lưu — quyền mới có hiệu lực ngay, không cần đăng nhập lại');
      setNhap(null);
      await queryClient.invalidateQueries({ queryKey: ['admin', 'roles'] });
    },
    onError: (caught: unknown) => {
      // ⛔ ADM-2016 khai `handling: 'caller'` ở `error-map.ts` — nó ⛔ không bắn toast toàn cục, vì
      //   một dòng toast trôi mất sau 3 giây ⛔ không phân biệt được với mọi lỗi nhập liệu khác.
      //   Đây là thao tác DUY NHẤT của màn hình mà hậu quả ⛔ không quay lui được từ giao diện.
      if (caught instanceof ApiClientError && caught.code === 'ADM-2016') {
        Modal.error({
          title: 'Thao tác này không gỡ lại được',
          content: caught.message,
          okText: 'Đã hiểu',
        });
        return;
      }
      message.error(caught instanceof ApiClientError ? caught.message : 'Không lưu được phân quyền');
    },
  });

  const columns: ColumnsType<RoleSummary> = [
    {
      title: 'Mã',
      dataIndex: 'code',
      width: 190,
      render: (code: string, row) => (
        <Space size={4}>
          <Tag>{code}</Tag>
          {row.isSystem && <Tag color="gold">hệ thống</Tag>}
        </Space>
      ),
    },
    { title: 'Tên vai trò', dataIndex: 'name' },
    { title: 'Số quyền', dataIndex: 'permissionCount', width: 100, align: 'right' },
  ];

  return (
    <Row gutter={16}>
      <Col xs={24} lg={11}>
        <Card title="Vai trò">
          {!suaDuoc && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="Bạn đang xem ma trận phân quyền"
              description="Sửa được ma trận cần quyền adm:role:manage. Việc gán vai trò cho từng người nằm ở màn hình Tài khoản."
            />
          )}
          <Table<RoleSummary>
            columns={columns}
            dataSource={roles.data ?? []}
            rowKey="code"
            loading={roles.isLoading}
            // Bề ngang tối thiểu: hẹp hơn thì CUỘN NGANG, không bóp chữ.
            // Vì sao cần — xem chú thích cột "Địa chỉ" ở `features/hydro/ApiSourcesPage.tsx`.
            scroll={{ x: 640 }}
            pagination={false}
            onRow={(row) => ({
              onClick: () => chonVaiTro(row.code),
              style: { cursor: 'pointer' },
            })}
            rowClassName={(row) => (row.code === selected ? 'ant-table-row-selected' : '')}
          />
        </Card>
      </Col>

      <Col xs={24} lg={13}>
        <Card
          title={selected ? `Quyền của ${selected}` : 'Quyền'}
          extra={
            selected &&
            suaDuoc &&
            !khoa && (
              <Space>
                <Button disabled={!coThayDoi} onClick={() => setNhap(null)}>
                  Hoàn tác
                </Button>
                <Button
                  type="primary"
                  disabled={!coThayDoi}
                  loading={luu.isPending}
                  onClick={() => luu.mutate([...daChon].sort())}
                >
                  Lưu
                </Button>
              </Space>
            )
          }
        >
          {!selected && <Empty description="Chọn một vai trò để xem danh sách quyền" />}
          {selected && khoa && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              message="Vai trò hệ thống — không sửa quyền được"
              description="Đây là lối thoát cuối cùng của hệ thống: nếu mọi vai trò khác đều bị gỡ mất quyền quản trị, tài khoản mang vai trò này vẫn vào được để gỡ lại."
            />
          )}
          {selected && (permissions.isLoading || catalog.isLoading) && <Spin />}
          {selected && !permissions.isLoading && !catalog.isLoading && (
            <List
              dataSource={Object.entries(grouped)}
              renderItem={([module, items]) => (
                <List.Item>
                  <List.Item.Meta
                    title={
                      <Typography.Text strong>{MODULE_LABELS[module] ?? module}</Typography.Text>
                    }
                    description={
                      <Space direction="vertical" size={2} style={{ width: '100%' }}>
                        {items.map((quyen) => (
                          <Checkbox
                            key={quyen.code}
                            checked={daChon.has(quyen.code)}
                            disabled={!suaDuoc || khoa}
                            onChange={(e) => {
                              const tiep = new Set(daChon);
                              if (e.target.checked) {
                                tiep.add(quyen.code);
                              } else {
                                tiep.delete(quyen.code);
                              }
                              setNhap(tiep);
                            }}
                          >
                            <Typography.Text>{quyen.name}</Typography.Text>{' '}
                            <Typography.Text type="secondary" code>
                              {quyen.code}
                            </Typography.Text>
                          </Checkbox>
                        ))}
                      </Space>
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

/**
 * Gom danh mục quyền theo module.
 *
 * ⚠ Nguồn là **cả danh mục**, ⛔ không phải quyền vai trò đang có: một ô đánh dấu chỉ dựng được từ
 * hiệu của hai tập, nên muốn *thêm* một quyền thì phải nhìn thấy quyền vai trò ấy **chưa** có.
 */
function groupByModule(items: readonly PermissionSummary[]): Record<string, PermissionSummary[]> {
  return items.reduce<Record<string, PermissionSummary[]>>((acc, quyen) => {
    (acc[quyen.module] ??= []).push(quyen);
    return acc;
  }, {});
}

/** Hai tập có cùng phần tử không — dùng để bật/tắt nút Lưu. */
function bangNhau(a: ReadonlySet<string>, b: ReadonlySet<string>): boolean {
  return a.size === b.size && [...a].every((x) => b.has(x));
}
