import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Card, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { ApprovalActions } from '@/components/business/ApprovalActions';
import { ApiClientError } from '@/shared/apiClient';

import { LOAI_NGHI_LABEL, TRANG_THAI_DON_META, type DonNghiView } from './hrVocabulary';
import { nghiPhepApi } from './nghiPhepApi';
import { ngayVn } from './nghiPhepFormat';

const CO_TRANG = 20;

/**
 * Hộp chờ duyệt đơn nghỉ — CN-04.9 (WS-57).
 *
 * <h2>⛔⛔ *"Quản lý ĐƠN VỊ duyệt"* cần HAI cơ chế, và một trong hai ⛔ không nằm ở đây</h2>
 *
 * `hr:leave:approve` là một **mã quyền**; đặc tả nói một **quan hệ**. Vế còn lại là **bộ lọc phạm
 * vi tầng 3** trên `leave_requests.org_unit_id`: danh sách này chỉ chứa đơn trong phạm vi của người
 * đang đăng nhập, nên một trưởng Xí nghiệp 3 ⛔ **không nhìn thấy** đơn của Xí nghiệp 5 và ⛔ không
 * có gì để bấm. Bài `khongDuyetDuocDonNgoaiDonVi` canh đúng vế ấy — kể cả khi đoán đúng `publicId`.
 *
 * <h2>⛔ Nút do BACKEND quyết, ⛔ không do trạng thái</h2>
 *
 * `ApprovalActions` render từ `GET /{id}/hanh-dong`. Đặc biệt: người gọi gửi `APPROVE`, còn việc
 * đổi nó thành `ESCALATE` khi đơn đủ dài nằm ở **service** (chốt C2) — nếu ⛔ không thì hai khoá
 * `settings` thành núm điều khiển *trình duyệt* thay vì điều khiển *quy trình*.
 */
export function DuyetNghiPhepPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [trang, setTrang] = useState(0);
  const [dangMo, setDangMo] = useState<string | null>(null);

  const hopChoDuyet = useQuery({
    queryKey: ['hr', 'nghi-phep', 'cho-duyet', trang],
    queryFn: () => nghiPhepApi.choDuyet(trang, CO_TRANG),
  });

  // ⚠ `useQuery` cho `allowedActions` phải gắn với MỘT dòng cụ thể, và hook ⛔ không gọi được trong
  //   `expandedRowRender`. Nên trang giữ đúng một id đang mở thay vì một hook mỗi dòng.
  const hanhDong = useQuery({
    queryKey: ['hr', 'nghi-phep', 'hanh-dong', dangMo],
    queryFn: () => nghiPhepApi.hanhDong(dangMo as string),
    enabled: dangMo !== null,
  });

  const thucHien = useMutation({
    mutationFn: (v: { publicId: string; action: string; reason?: string }) =>
      nghiPhepApi.thucHien(v.publicId, v.action, v.reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['hr', 'nghi-phep'] });
    },
    onError: (caught: unknown) =>
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không thực hiện được thao tác',
      ),
  });

  const columns: ColumnsType<DonNghiView> = [
    { title: 'Mã CBNV', dataIndex: 'employeeCode', width: 130 },
    { title: 'Họ tên', dataIndex: 'employeeName', width: 200, ellipsis: true },
    {
      title: 'Loại nghỉ',
      dataIndex: 'leaveType',
      width: 140,
      render: (loai: DonNghiView['leaveType']) => LOAI_NGHI_LABEL[loai],
    },
    { title: 'Từ ngày', dataIndex: 'fromDate', width: 120, render: (d: string) => ngayVn(d) },
    { title: 'Đến ngày', dataIndex: 'toDate', width: 120, render: (d: string) => ngayVn(d) },
    {
      title: 'Ngày công',
      dataIndex: 'workingDays',
      width: 110,
      align: 'right',
      render: (s: string) => <b>{s}</b>,
    },
    { title: 'Lý do', dataIndex: 'reason', width: 240, ellipsis: true },
    {
      title: 'Trạng thái',
      dataIndex: 'state',
      width: 150,
      render: (tt: DonNghiView['state'], don) => (
        <Space size={4} wrap>
          <Tag color={TRANG_THAI_DON_META[tt].color}>{TRANG_THAI_DON_META[tt].label}</Tag>
          {don.noHo ? <Tag>Nộp hộ</Tag> : null}
        </Space>
      ),
    },
    {
      /*
       * ⭐⭐ T80.7 — hộp chờ NÓI RA ai phải bấm.
       *
       * Danh sách cắt theo PHẠM VI (đơn vị), còn nút thì theo THẨM QUYỀN (trưởng/phó hoặc được uỷ
       * quyền). Hai tập ⛔ bằng nhau: một quản lý ⛔ giữ chức vụ vẫn thấy đơn của đơn vị mình — cố ý,
       * đó là danh sách việc của đơn vị. Trước lượt này họ phải mở từng dòng ra mới biết mình ⛔ có
       * nút, và ⛔ gì chỉ ra AI mới là người phải bấm.
       *
       * ⚠ `undefined` ⛔ phải `false` (xem `DonNghiView.toiDuyetDuoc`) — ô để TRỐNG, ⛔ vẽ một nhãn
       * phủ định cho một câu hỏi endpoint ⛔ trả lời.
       */
      title: 'Tôi duyệt được',
      dataIndex: 'toiDuyetDuoc',
      width: 140,
      render: (duoc: boolean | undefined) =>
        duoc === undefined ? null : duoc ? (
          <Tag color="success">Bấm được</Tag>
        ) : (
          <Tooltip title="Bạn thấy đơn này vì nó thuộc phạm vi đơn vị của bạn, nhưng người bấm nút phải là trưởng/phó đơn vị hoặc người đang được uỷ quyền.">
            <Tag color="default">Chỉ theo dõi</Tag>
          </Tooltip>
        ),
    },
  ];

  return (
    <Card title="Đơn nghỉ chờ duyệt">
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        title="Danh sách đã cắt theo đơn vị của bạn"
        description={
          <>
            Quyền duyệt một mình ⛔ không đủ để diễn đạt <i>“quản lý đơn vị mình duyệt”</i> — vế còn
            lại là <b>phạm vi dữ liệu</b>. Đơn của đơn vị khác ⛔ không xuất hiện ở đây, và cũng ⛔
            không thao tác được bằng đường nào khác.
          </>
        }
      />

      <Table
        rowKey="publicId"
        loading={hopChoDuyet.isLoading}
        dataSource={hopChoDuyet.data?.muc ?? []}
        columns={columns}
        locale={{ emptyText: 'Không có đơn nào chờ duyệt trong phạm vi của bạn' }}
        // 130+200+140+120+120+110+240+150 = 1210.
        scroll={{ x: 1210 }}
        expandable={{
          expandedRowKeys: dangMo ? [dangMo] : [],
          onExpand: (mo, don) => setDangMo(mo ? don.publicId : null),
          expandedRowRender: (don) => (
            <Space orientation="vertical" size="small" style={{ width: '100%' }}>
              <Typography.Text type="secondary">
                {don.reason ? `Lý do: ${don.reason}` : 'Người nộp không ghi lý do.'}
              </Typography.Text>
              <ApprovalActions
                actions={hanhDong.data}
                onAction={async (action, reason) => {
                  await thucHien.mutateAsync({ publicId: don.publicId, action, reason });
                }}
              />
            </Space>
          ),
        }}
        pagination={{
          current: trang + 1,
          pageSize: hopChoDuyet.data?.co ?? CO_TRANG,
          total: hopChoDuyet.data?.tong ?? 0,
          showSizeChanger: false,
          onChange: (p) => setTrang(p - 1),
        }}
      />
    </Card>
  );
}
