import { useQuery } from '@tanstack/react-query';
import { Alert, Drawer, Empty, Skeleton, Table, Tag, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';

import { type OperationStatusRow } from '@/shared/api-types';
import { api } from '@/shared/apiClient';
import { formatDateTime } from '@/shared/format';

interface Props {
  publicId: string | null;
  constructionName?: string | null;
  open: boolean;
  onClose: () => void;
}

/**
 * Lịch sử tình hình vận hành của một công trình — CN-02.11 (chốt G4).
 *
 * <h2>Vì sao màn hình này phải có</h2>
 *
 * <p>Tới 31/08/2026, `construction_operation_status` **chỉ có đường ghi**: nút "Nhập nhanh" gọi
 * `POST /ops/operation-statuses/batch`, và không màn hình quản trị nào gọi
 * `GET /ops/operation-statuses`. Endpoint đọc đã tồn tại từ WS-19 và javadoc của nó nói rõ vì sao:
 * *"lấp một quyền chết — `ops:operation-status:view` đã cấp cho 6 vai trò mà không endpoint nào đòi
 * nó"*. Nửa BE được lấp; **nửa giao diện thì chưa** — nên quyền ấy vẫn chết, chỉ chết ở tầng khác.
 *
 * <p>Từ 31/08 nó không còn là chuyện nội bộ: bản ghi mới nhất của mỗi công trình **lên thẳng cổng
 * công khai** (khối "Vận hành công trình", 6 cột). Nhập nhầm một mã là cổng nói sai ngay, và người
 * nhập không có màn hình nào để **thấy** mình vừa nhập gì.
 *
 * <h2>⚠ Chỉ ĐỌC — và đó là quyết định, không phải thiếu sót</h2>
 *
 * <p>Bảng này **append có lịch sử** (chốt G4): không có `PUT`, không có `DELETE`. Sửa một bản ghi
 * đã ghi nhận nghĩa là viết lại lịch sử vận hành của một công trình thuỷ lợi — cách đúng là ghi đè
 * bằng một bản ghi mới có `effective_at` mới hơn, đúng như trực ban làm trên sổ giấy. Mắt xích 4
 * của `ConstructionStatusService` đọc **bản ghi mới nhất**, nên bản ghi mới lập tức thắng.
 *
 * <p>⛔ Đừng thêm nút "Sửa" ở đây mà không đổi cả thiết kế bảng — hash chain của nhật ký kiểm toán
 * đang ký tên vào lịch sử này (quy tắc 18).
 */
export function OperationStatusHistoryDrawer({ publicId, constructionName, open, onClose }: Props) {
  const query = useQuery({
    queryKey: ['ops', 'operation-statuses', publicId],
    queryFn: () =>
      api.getPage<OperationStatusRow>('/ops/operation-statuses', {
        constructionPublicId: publicId as string,
        size: 50,
        sort: 'effectiveAt,desc',
      }),
    enabled: !!publicId && open,
  });

  const columns: ColumnsType<OperationStatusRow> = [
    {
      title: 'Thời điểm hiệu lực',
      dataIndex: 'effectiveAt',
      width: 170,
      render: (val: string) => formatDateTime(val),
    },
    {
      title: 'Mã tình hình vận hành',
      key: 'code',
      width: 220,
      render: (_, row) => (
        // Màu đến từ danh mục mã trong CSDL (Công ty tự đặt — CRUD, G4), không phải hằng số trong
        // mã. AntD `Tag` nhận mã màu tự do; giá trị lạ chỉ làm thẻ mất màu, không vỡ bố cục.
        <>
          <Tag color={row.colorHex || undefined}>{row.operationCode}</Tag>
          <Typography.Text type="secondary">{row.operationName}</Typography.Text>
        </>
      ),
    },
    {
      title: 'Giá trị tham số',
      key: 'param',
      width: 140,
      // ⛔ Không quy `null` về `0` — quy tắc 16: số 0 là một câu khẳng định, và "điều tiết 0,00 m"
      //    khác hẳn "mã này không có tham số".
      render: (_, row) =>
        row.parameterValue === null ? (
          <Typography.Text type="secondary">—</Typography.Text>
        ) : (
          <>
            {row.parameterValue}
            {row.parameterUnit ? (
              <Typography.Text type="secondary"> {row.parameterUnit}</Typography.Text>
            ) : null}
          </>
        ),
    },
    {
      title: 'Ghi chú',
      dataIndex: 'note',
      render: (val: string | null) => val || <Typography.Text type="secondary">—</Typography.Text>,
    },
  ];

  return (
    <Drawer
      title={
        constructionName
          ? `Lịch sử tình hình vận hành — ${constructionName}`
          : 'Lịch sử tình hình vận hành'
      }
      placement="right"
      width={800}
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Bản ghi mới nhất là thứ hiển thị trên cổng công khai"
        description="Bảng này ghi nhận theo thời điểm hiệu lực và không sửa/xoá được — muốn đính chính thì nhập một bản ghi mới với thời điểm hiệu lực mới hơn (nút “Nhập nhanh” ở danh sách công trình)."
      />

      {query.isLoading ? (
        <Skeleton active />
      ) : (query.data?.items.length ?? 0) === 0 ? (
        <Empty description="Công trình này chưa được ghi nhận tình hình vận hành lần nào." />
      ) : (
        <Table<OperationStatusRow>
          rowKey="publicId"
          size="small"
          pagination={false}
          columns={columns}
          dataSource={query.data?.items ?? []}
        />
      )}
    </Drawer>
  );
}
