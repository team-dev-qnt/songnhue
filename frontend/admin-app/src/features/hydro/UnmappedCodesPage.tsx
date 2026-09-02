import { PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useNavigate } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { type UnmappedCodeRow } from '@/shared/api-types';
import { api } from '@/shared/apiClient';
import { EMPTY_MARK, formatDateTimeWithSeconds, formatInteger } from '@/shared/format';

/**
 * Mã lạ từ nguồn — T31.13.
 *
 * <h3>Màn hình này chỉ LIỆT KÊ, và đó là toàn bộ thiết kế</h3>
 *
 * Nguồn trả **28 mã**, hệ thống khai **19**. Chín mã còn lại ta không biết là trạm nào, ở đâu,
 * thuộc công trình nào — đó là **G8, thuộc Công ty**.
 *
 * - ⛔ **Không có nút nào tự tạo điểm đo từ mã lạ** (quy tắc parse 5). Bản suy đoán trước đó dò
 *   danh tính theo giá trị đo đã **sai 1/4 mã**, và một điểm đo gán nhầm mã là toàn bộ lịch sử
 *   của trạm này đi vào biểu đồ của trạm khác — vẽ vẫn đẹp.
 * - ⭐ Nút *Khai thành điểm đo* chỉ mở biểu mẫu điểm đo với ô mã **điền sẵn**. Người khai vẫn
 *   phải gõ tên, vai trò, nguồn — tức vẫn phải **biết** mã ấy là gì.
 *
 * <h3>⚠⚠ Cột giá trị là số NGUYÊN VĂN NGUỒN, chưa quy đổi</h3>
 *
 * Chưa biết mã này là loại chỉ số gì thì cũng chưa biết quy đổi về đâu. Nguồn trả mực nước bằng
 * **cm** còn hệ thống lưu bằng **m**, nên một ô hiện `213` mà không kèm đơn vị sẽ được đọc thành
 * *213 mét*. Đơn vị **luôn** đi cạnh con số ở cột ấy.
 */
export function UnmappedCodesPage() {
  const { hasPermission } = useAuth();
  const navigate = useNavigate();
  const coQuanLy = hasPermission('hyd:station:manage');

  const query = useQuery({
    queryKey: ['hyd', 'ma-la'],
    queryFn: () => api.get<UnmappedCodeRow[]>('/hyd/ma-la'),
  });

  const rows = query.data ?? [];
  const chuaKhai = rows.filter((r) => !r.daKhaiThanhDiemDo);
  const daKhaiConLichSu = rows.filter((r) => r.daKhaiThanhDiemDo);

  const columns: ColumnsType<UnmappedCodeRow> = [
    {
      title: 'Mã nguồn',
      dataIndex: 'apiCode',
      width: 140,
      render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    },
    { title: 'Nguồn', dataIndex: 'nguonCode', width: 110 },
    {
      title: 'Bản ghi đã tích',
      dataIndex: 'soBanGhi',
      width: 140,
      align: 'right',
      render: (v: number) => formatInteger(v),
    },
    {
      title: 'Lần đầu',
      dataIndex: 'lanDau',
      width: 180,
      render: (v: string | null) => (v ? formatDateTimeWithSeconds(v) : EMPTY_MARK),
    },
    {
      title: 'Gần nhất',
      dataIndex: 'lanGanNhat',
      width: 180,
      render: (v: string | null) => (v ? formatDateTimeWithSeconds(v) : EMPTY_MARK),
    },
    {
      // ⚠ Tiêu đề nói thẳng "nguyên văn nguồn": một cột số không có nhãn cảnh báo sẽ được đọc
      //    bằng đơn vị của hệ thống (m), trong khi nguồn trả cm — sai 100 lần.
      title: 'Giá trị gần nhất (nguyên văn nguồn)',
      dataIndex: 'giaTriGanNhat',
      width: 230,
      align: 'right',
      render: (v: string | null, r) =>
        v == null ? (
          EMPTY_MARK
        ) : (
          <span>
            {v} <Tag>{r.donViNguon ?? '?'}</Tag>
          </span>
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'daKhaiThanhDiemDo',
      width: 220,
      render: (daKhai: boolean, r) =>
        daKhai ? (
          <Tooltip title="Số đo MỚI của mã này từ nay đi thẳng vào bảng số liệu. Số bản ghi lịch sử ở cột bên trái vẫn nằm lại đây cho tới khi có job chuyển.">
            <Tag color="blue">Đã khai — {r.maDiemDo}</Tag>
          </Tooltip>
        ) : (
          <Tag color="orange">Chưa khai</Tag>
        ),
    },
    {
      title: '',
      width: 190,
      align: 'right',
      render: (_, r) =>
        coQuanLy && !r.daKhaiThanhDiemDo ? (
          <Button
            type="link"
            icon={<PlusOutlined />}
            onClick={() => navigate(`/thuy-van/diem-do?apiCode=${encodeURIComponent(r.apiCode)}`)}
          >
            Khai thành điểm đo
          </Button>
        ) : null,
    },
  ];

  return (
    <Card title="Mã lạ từ nguồn">
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message={`${chuaKhai.length} mã nguồn đang gửi số liệu về mà chưa có điểm đo nào nhận`}
        description={
          <>
            Số đo của chúng <b>vẫn được giữ lại</b> — nguồn không có API lịch sử, bỏ hai tháng là
            mất hai tháng ngay cả sau khi Công ty khai báo. Nhưng chúng{' '}
            <b>không lên biểu đồ, không lên bản đồ, không so ngưỡng</b> cho tới khi có người khai.
            <br />⛔ Hệ thống <b>không tự tạo điểm đo</b> từ mã lạ: ta không biết tên, vị trí hay
            công trình của chúng — đó là mục <b>G8</b>, thuộc Công ty. Một bản suy đoán trước đây dò
            danh tính theo giá trị đo đã <b>sai 1/4 mã</b>.
          </>
        }
      />

      {daKhaiConLichSu.length > 0 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={`${daKhaiConLichSu.length} mã đã được khai nhưng lịch sử cũ vẫn nằm ở đây`}
          description="Số đo mới của chúng đã vào bảng số liệu. Phần lịch sử tích trước lúc khai chưa được chuyển sang — biểu đồ của những trạm ấy sẽ bắt đầu từ ngày khai, không phải từ ngày đầu tiên có số đo."
        />
      )}

      <Table
        rowKey="apiCode"
        loading={query.isLoading}
        dataSource={rows}
        columns={columns}
        pagination={false}
        // 140+110+140+180+180+230+220+190 = 1390.
        scroll={{ x: 1390 }}
        size="small"
      />

      <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
        <Space direction="vertical" size={2}>
          <span>
            Danh sách này <b>teo dần</b> theo tiến độ khai báo — rỗng nghĩa là mọi mã nguồn phát đều
            đã có điểm đo nhận.
          </span>
          <span>
            Bấm <b>Khai thành điểm đo</b> chỉ mở biểu mẫu với ô mã điền sẵn; các ô còn lại vẫn phải
            gõ tay vì chỉ Công ty biết mã ấy là trạm nào.
          </span>
        </Space>
      </Typography.Paragraph>
    </Card>
  );
}

export default UnmappedCodesPage;
