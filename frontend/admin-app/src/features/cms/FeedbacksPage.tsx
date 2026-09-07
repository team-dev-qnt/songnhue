import { DeleteOutlined, LikeOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Badge,
  Button,
  Card,
  Descriptions,
  Empty,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { ApprovalActions } from '@/components/business/ApprovalActions';
import { ApiClientError } from '@/shared/apiClient';

import { cmsApi, cmsKeys } from './api';
import { type FeedbackStatus, type FeedbackView } from './types';

/**
 * Kiểm duyệt góp ý / đánh giá từ cổng — CN-01.6, chốt **D1**.
 *
 * <h3>⭐⭐ Màn hình này là NỬA "GHI" của một vòng khép kín</h3>
 *
 * Nửa "đọc" là trang `/gop-y` của cổng công khai. Dựng màn hình này mà ⛔ không dựng nơi công bố
 * thì nút Duyệt ⛔ không quyết định điều gì — đúng luật 27, và triệu chứng của nó im lặng hoàn
 * toàn: *màn hình báo lưu thành công, cổng ⛔ không đổi gì*.
 *
 * <h3>⛔ Nội dung hiển thị bằng TEXT, tuyệt đối ⛔ không dựng thành HTML</h3>
 *
 * `content` do người lạ trên Internet nhập, và ở đây nguy cơ **cao hơn** hộp thư liên hệ: sau khi
 * duyệt nó còn đi tiếp ra cổng công khai, tức nạn nhân của một XSS lưu trữ là *mọi người đọc
 * cổng*, ⛔ không chỉ người quản trị. React escape mặc định và điều đó phải được giữ.
 *
 * <h3>⛔ Nút chuyển trạng thái do BACKEND trả về, ⛔ không dựng ở đây</h3>
 *
 * `allowedActions` đã lọc theo `workflow_transitions` **và** theo quyền người đang đăng nhập, và
 * nó mang cờ `requiresReason`. Một bảng `if` ở giao diện là bản sao thứ hai của một luật đang nằm
 * trong CSDL — bản sao ấy lệch ngay lần đầu Công ty thêm một bước (conventions.md §3).
 *
 * <h3>⛔⛔ Điểm trung bình ⛔ KHÔNG BAO GIỜ đứng một mình</h3>
 *
 * Nó tính trên **hai lớp lọc** (chỉ mục đã duyệt, và trong đó chỉ mục có chấm điểm). Một ô
 * `Statistic` hiện mỗi "4,2" ⛔ không phân biệt được *"4,2 trên 5 phiếu"* với *"4,2 trên 500"*, và
 * cũng ⛔ không cho ai thấy phần đã bị lọc ra (luật 9). Vì thế khối thống kê luôn hiện **mẫu số**
 * cạnh nó, và bốn con số trạng thái ở ngay bên.
 */
const NHAN_TRANG_THAI: Record<FeedbackStatus, { nhan: string; mau: string }> = {
  CHO_DUYET: { nhan: 'Chờ duyệt', mau: 'gold' },
  DA_DUYET: { nhan: 'Đã duyệt', mau: 'green' },
  TU_CHOI: { nhan: 'Từ chối', mau: 'red' },
  AN: { nhan: 'Đã ẩn', mau: 'default' },
};

const CO_TRANG = 20;

const QUYEN_GHI = 'cms:feedback:manage';
const LY_DO_THIEU_QUYEN = `Bạn không có quyền ${QUYEN_GHI}`;

/** UTC+7 ở mọi chỗ hiển thị thời gian — CLAUDE.md quy tắc 1. */
function gio(t: string) {
  return new Date(t).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
}

export function FeedbacksPage() {
  // ⭐ Mặc định mở ở "Chờ duyệt": đây là màn hình để LÀM VIỆC, và việc cần làm là những mục
  //    chưa ai xem. Mở ở "Tất cả" thì việc cần làm nằm lẫn giữa những mục đã xong.
  const [loc, datLoc] = useState<FeedbackStatus | undefined>('CHO_DUYET');
  const [trang, datTrang] = useState(0);
  const { hasPermission } = useAuth();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();

  const coQuyenGhi = hasPermission(QUYEN_GHI);

  const danhSach = useQuery({
    queryKey: cmsKeys.feedbacks(loc, trang),
    queryFn: () => cmsApi.listFeedbacks(loc, trang, CO_TRANG),
  });

  const tongHop = useQuery({
    queryKey: cmsKeys.feedbackSummary(),
    queryFn: () => cmsApi.feedbackSummary(),
  });

  const lamMoi = () => {
    void queryClient.invalidateQueries({ queryKey: ['cms', 'feedbacks'] });
  };

  const bao = (caught: unknown, macDinh: string) =>
    message.error(caught instanceof ApiClientError ? caught.message : macDinh);

  const chuyenTrangThai = useMutation({
    mutationFn: (v: { publicId: string; action: string; reason?: string }) =>
      cmsApi.feedbackTransition(v.publicId, v.action, v.reason),
    onSuccess: lamMoi,
    onError: (caught: unknown) => bao(caught, 'Không chuyển được trạng thái'),
  });

  const xoa = useMutation({
    mutationFn: (publicId: string) => cmsApi.deleteFeedback(publicId),
    onSuccess: () => {
      lamMoi();
      message.success('Đã xoá góp ý');
    },
    onError: (caught: unknown) => bao(caught, 'Không xoá được góp ý'),
  });

  const cot = [
    {
      title: 'Người gửi',
      dataIndex: 'fullName',
      key: 'fullName',
      width: 200,
      render: (ten: string | null, r: FeedbackView) => (
        <Space direction="vertical" size={0}>
          {/* ⛔ Ẩn danh là HỢP LỆ ở kênh này — nói thẳng, ⛔ không bịa một cái tên (quy tắc 16). */}
          {ten ? (
            <Typography.Text strong>{ten}</Typography.Text>
          ) : (
            <Typography.Text type="secondary">Ẩn danh</Typography.Text>
          )}
          {r.email ? <Typography.Text type="secondary">{r.email}</Typography.Text> : null}
        </Space>
      ),
    },
    {
      title: 'Điểm',
      dataIndex: 'rating',
      key: 'rating',
      width: 110,
      // ⛔ `null` = chưa chấm, ⛔ KHÔNG phải 0 sao. Hiện "0 ★" ở đây là bịa ra một mức hài lòng
      //    chưa ai phát biểu (quy tắc 16) — và nó sẽ được đọc thành "rất không hài lòng".
      render: (diem: number | null) =>
        diem === null ? (
          <Typography.Text type="secondary">Chưa chấm</Typography.Text>
        ) : (
          <span title={`${diem} trên 5 sao`}>{'★'.repeat(diem)}</span>
        ),
    },
    {
      title: 'Nội dung',
      dataIndex: 'content',
      key: 'content',
      // ⛔ TEXT thường — React escape. Xem javadoc màn hình.
      render: (nd: string) => (
        <Typography.Text ellipsis={{ tooltip: nd }} style={{ maxWidth: 420 }}>
          {nd}
        </Typography.Text>
      ),
    },
    {
      title: 'Gửi lúc',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (t: string) => gio(t),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (tt: FeedbackStatus) => (
        <Tag color={NHAN_TRANG_THAI[tt].mau}>{NHAN_TRANG_THAI[tt].nhan}</Tag>
      ),
    },
    {
      title: '',
      key: 'thaoTac',
      width: 60,
      render: (_: unknown, r: FeedbackView) => (
        <Button
          type="text"
          danger
          size="small"
          icon={<DeleteOutlined />}
          disabled={!coQuyenGhi}
          title={coQuyenGhi ? undefined : LY_DO_THIEU_QUYEN}
          onClick={() =>
            modal.confirm({
              title: 'Xoá góp ý này?',
              content:
                'Xoá mềm — bản ghi vẫn nằm trong cơ sở dữ liệu và trong nhật ký. ' +
                'Muốn gỡ khỏi cổng mà vẫn giữ trên màn hình này thì dùng bước "Ẩn khỏi cổng".',
              okText: 'Xoá',
              okButtonProps: { danger: true },
              cancelText: 'Huỷ',
              onOk: () => xoa.mutateAsync(r.publicId),
            })
          }
        />
      ),
    },
  ];

  const t = tongHop.data;

  return (
    <Card
      title={
        <Space>
          <LikeOutlined />
          <span>Góp ý &amp; đánh giá</span>
          {t && t.choDuyet > 0 ? <Badge count={t.choDuyet} /> : null}
        </Space>
      }
      extra={
        <Select<FeedbackStatus | 'ALL'>
          value={loc ?? 'ALL'}
          style={{ width: 160 }}
          onChange={(v) => {
            datLoc(v === 'ALL' ? undefined : v);
            datTrang(0);
          }}
          options={[
            { value: 'ALL', label: 'Tất cả' },
            ...(Object.keys(NHAN_TRANG_THAI) as FeedbackStatus[]).map((k) => ({
              value: k,
              label: NHAN_TRANG_THAI[k].nhan,
            })),
          ]}
        />
      }
    >
      {t ? (
        <Space size="large" wrap style={{ marginBottom: 16 }}>
          <Statistic
            title="Điểm hài lòng trung bình"
            // ⛔⛔ `null`/vắng mặt = CHƯA AI CHẤM. Một dấu "—" là câu trả lời đúng; một số 0 là
            //    một khẳng định về sự hài lòng mà ⛔ chưa ai phát biểu (quy tắc 16).
            value={
              t.diemTrungBinh === null || t.diemTrungBinh === undefined
                ? '—'
                : String(t.diemTrungBinh)
            }
            suffix={t.diemTrungBinh === null || t.diemTrungBinh === undefined ? '' : '/ 5'}
          />
          {/* ⛔⛔ Mẫu số đi KÈM, ⛔ không phải một dòng chú thích nhỏ ở đâu đó: xem javadoc màn
              hình. Con số trên tính trên các mục ĐÃ DUYỆT có chấm điểm — hai lớp lọc. */}
          <Statistic title="Số phiếu có chấm điểm (đã duyệt)" value={t.soCoDiem} />
          <Statistic title="Chờ duyệt" value={t.choDuyet} />
          <Statistic title="Đã duyệt" value={t.daDuyet} />
          <Statistic title="Từ chối" value={t.tuChoi} />
          <Statistic title="Đã ẩn" value={t.an} />
        </Space>
      ) : null}

      <Table<FeedbackView>
        rowKey="publicId"
        loading={danhSach.isPending}
        dataSource={danhSach.data?.items ?? []}
        columns={cot}
        locale={{
          emptyText: (
            <Empty
              description={
                loc === 'CHO_DUYET'
                  ? 'Không còn góp ý nào chờ duyệt'
                  : 'Chưa có góp ý nào gửi từ cổng thông tin'
              }
            />
          ),
        }}
        // Bề ngang tối thiểu: hẹp hơn thì CUỘN NGANG, không bóp chữ.
        scroll={{ x: 1100 }}
        pagination={{
          current: trang + 1,
          pageSize: CO_TRANG,
          total: danhSach.data?.meta.totalElements ?? 0,
          onChange: (p) => datTrang(p - 1),
          showSizeChanger: false,
        }}
        expandable={{
          expandedRowRender: (r) => (
            <ChiTietGopY
              gopY={r}
              coQuyenGhi={coQuyenGhi}
              onChuyenTrangThai={async (action, reason) => {
                await chuyenTrangThai.mutateAsync({ publicId: r.publicId, action, reason });
              }}
            />
          ),
        }}
      />
    </Card>
  );
}

/**
 * Khối chi tiết của một góp ý.
 *
 * ⚠ Tách thành component riêng ⛔ không phải để gọn: `useQuery` cho `allowedActions` **phải** gắn
 * với một dòng cụ thể, và hook ⛔ không gọi được trong `expandedRowRender` của bảng.
 */
function ChiTietGopY({
  gopY,
  coQuyenGhi,
  onChuyenTrangThai,
}: {
  gopY: FeedbackView;
  coQuyenGhi: boolean;
  onChuyenTrangThai: (action: string, reason?: string) => Promise<void>;
}) {
  const hanhDong = useQuery({
    queryKey: ['cms', 'feedback', gopY.publicId, 'actions'],
    queryFn: () => cmsApi.feedbackActions(gopY.publicId),
  });

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Descriptions column={1} size="small" bordered>
        <Descriptions.Item label="Nội dung">
          {/* Xuống dòng giữ nguyên; nội dung vẫn là TEXT — React escape. */}
          <Typography.Paragraph style={{ whiteSpace: 'pre-line', marginBottom: 0 }}>
            {gopY.content}
          </Typography.Paragraph>
        </Descriptions.Item>
        {gopY.moderationNote ? (
          <Descriptions.Item label="Lý do bước gần nhất">
            {/* ⛔⛔ Chỉ hiện ở ĐÂY. Trường này ⛔ KHÔNG có trong record công khai của cổng — nó là
                chỗ cán bộ viết VỀ người gửi. */}
            <Typography.Text>{gopY.moderationNote}</Typography.Text>
          </Descriptions.Item>
        ) : null}
      </Descriptions>

      {coQuyenGhi ? (
        <ApprovalActions actions={hanhDong.data} onAction={onChuyenTrangThai} />
      ) : (
        <Typography.Text type="secondary">{LY_DO_THIEU_QUYEN}</Typography.Text>
      )}
    </Space>
  );
}
