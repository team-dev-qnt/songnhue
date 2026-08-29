import { MailOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Card, Descriptions, Empty, Select, Space, Table, Tag, Typography } from 'antd';
import { useState } from 'react';

import { cmsApi, cmsKeys } from './api';
import { type ContactStatus, type ContactView } from './types';

/**
 * Hộp thư tiếp nhận liên hệ / phản ánh từ cổng công khai — CN-01.4.
 *
 * <h3>Vì sao màn hình này ra đời CÙNG lượt với biểu mẫu</h3>
 *
 * Chú thích cũ ở trang Liên hệ của cổng từ chối dựng biểu mẫu với đúng một lý do: *"một form gửi
 * đi mà không ai nhận tệ hơn hẳn không có form: người dân tin là đã gửi được"*. Màn hình này
 * chính là nửa "có người nhận". Thiếu nó thì cả tính năng vẫn là thứ bị từ chối, chỉ khác là bây
 * giờ dữ liệu nằm trong một bảng không ai mở được.
 *
 * <h3>⛔ Nội dung hiển thị bằng TEXT, tuyệt đối không dựng thành HTML</h3>
 *
 * `subject` và `content` do người lạ trên Internet nhập. React escape mặc định, và điều đó phải
 * được giữ: một `dangerouslySetInnerHTML` đặt lên hai trường này là XSS lưu trữ nhắm thẳng vào
 * người có quyền quản trị — cùng hình dạng lỗi §10 đã trả giá ở `settings`, chỉ khác là nạn nhân
 * có quyền cao hơn.
 *
 * <h3>Phạm vi lượt này</h3>
 *
 * Đọc + đánh dấu đã đọc. ⛔ Chưa có: trả lời qua email, bốn trạng thái sau `DA_DOC`, phân loại,
 * chuyển phòng ban, ghi chú nội bộ, xuất Excel, nhắc SLA — phần còn lại của CN-01.4.
 */
const NHAN_TRANG_THAI: Record<ContactStatus, { nhan: string; mau: string }> = {
  MOI: { nhan: 'Mới', mau: 'red' },
  DA_DOC: { nhan: 'Đã đọc', mau: 'blue' },
  DANG_XU_LY: { nhan: 'Đang xử lý', mau: 'gold' },
  DA_PHAN_HOI: { nhan: 'Đã phản hồi', mau: 'green' },
  DONG: { nhan: 'Đóng', mau: 'default' },
  LUU_TRU: { nhan: 'Lưu trữ', mau: 'default' },
};

const CO_TRANG = 20;

export function ContactsPage() {
  const [loc, datLoc] = useState<ContactStatus | undefined>(undefined);
  const [trang, datTrang] = useState(0);
  const queryClient = useQueryClient();

  const danhSach = useQuery({
    queryKey: cmsKeys.contacts(loc, trang),
    queryFn: () => cmsApi.listContacts(loc, trang, CO_TRANG),
  });

  const danhDau = useMutation({
    mutationFn: (publicId: string) => cmsApi.markContactRead(publicId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['cms', 'contacts'] });
    },
  });

  const cot = [
    {
      title: 'Người gửi',
      dataIndex: 'fullName',
      key: 'fullName',
      render: (ten: string, r: ContactView) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{ten}</Typography.Text>
          {/* ⛔ Không ghép email và điện thoại bằng dấu gạch khi một bên rỗng — một dấu gạch
              trông như một giá trị. */}
          {r.email ? <Typography.Text type="secondary">{r.email}</Typography.Text> : null}
          {r.phone ? <Typography.Text type="secondary">{r.phone}</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: 'Tiêu đề',
      dataIndex: 'subject',
      key: 'subject',
      render: (cd: string) => <Typography.Text>{cd}</Typography.Text>,
    },
    {
      title: 'Nhận lúc',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (t: string) => new Date(t).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (tt: ContactStatus) => (
        <Tag color={NHAN_TRANG_THAI[tt].mau}>{NHAN_TRANG_THAI[tt].nhan}</Tag>
      ),
    },
  ];

  const soMoi = (danhSach.data?.items ?? []).filter((c) => c.status === 'MOI').length;

  return (
    <Card
      title={
        <Space>
          <MailOutlined />
          <span>Hộp thư liên hệ</span>
          {soMoi > 0 ? <Badge count={soMoi} /> : null}
        </Space>
      }
      extra={
        <Select<ContactStatus | 'ALL'>
          value={loc ?? 'ALL'}
          style={{ width: 160 }}
          onChange={(v) => {
            datLoc(v === 'ALL' ? undefined : v);
            datTrang(0);
          }}
          options={[
            { value: 'ALL', label: 'Tất cả' },
            ...(Object.keys(NHAN_TRANG_THAI) as ContactStatus[]).map((k) => ({
              value: k,
              label: NHAN_TRANG_THAI[k].nhan,
            })),
          ]}
        />
      }
    >
      <Table<ContactView>
        rowKey="publicId"
        loading={danhSach.isPending}
        dataSource={danhSach.data?.items ?? []}
        columns={cot}
        locale={{
          emptyText: <Empty description="Chưa có liên hệ nào gửi từ cổng thông tin" />,
        }}
        pagination={{
          current: trang + 1,
          pageSize: CO_TRANG,
          total: danhSach.data?.meta.totalElements ?? 0,
          onChange: (p) => datTrang(p - 1),
          showSizeChanger: false,
        }}
        expandable={{
          // Mở một dòng = đã đọc nó. Không dựng thêm một nút "đánh dấu đã đọc" riêng: hai
          // đường làm cùng một việc thì trạng thái lệch nhau tuỳ người dùng bấm cái nào.
          onExpand: (moRa, r) => {
            if (moRa && r.status === 'MOI') danhDau.mutate(r.publicId);
          },
          expandedRowRender: (r) => (
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Nội dung">
                {/* Xuống dòng giữ nguyên; nội dung vẫn là TEXT — React escape. */}
                <Typography.Paragraph style={{ whiteSpace: 'pre-line', marginBottom: 0 }}>
                  {r.content}
                </Typography.Paragraph>
              </Descriptions.Item>
              {r.readAt ? (
                <Descriptions.Item label="Đọc lần đầu lúc">
                  {new Date(r.readAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}
                </Descriptions.Item>
              ) : null}
            </Descriptions>
          ),
        }}
      />
    </Card>
  );
}

export default ContactsPage;
