import { CheckCircleOutlined, StopOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Card,
  Input,
  Modal,
  Segmented,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { type AlertEventRow } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { formatDateTime } from '@/shared/format';

import { LOAI_DIEU_KIEN_NGUONG, TRANG_THAI_CANH_BAO } from './hydroVocabulary';

/**
 * Lịch sử cảnh báo ngưỡng — T33.10 / T33.11.
 *
 * ⭐⭐ Hai cột chịu lực của bảng này là **"Đã báo động"** và **"Người đóng"**, và cả hai tồn tại
 * để chặn một cách đọc sai:
 *
 * - Một dòng `Đang xảy ra` mà **chưa** báo động nghĩa là điều kiện chưa giữ đủ số phút cấu
 *   hình — ⛔ **chưa ai nhận thông báo nào**. Không có cột ấy thì người trực đọc "đang xảy ra"
 *   thành "lãnh đạo đã biết".
 * - Một dòng `Đã kết thúc` với ô Người đóng **trống** nghĩa là máy tự đóng vì giá trị về dưới
 *   ngưỡng — ⛔ **không** phải "đã có người xử lý".
 *
 * ⬜ **T33.10 CHƯA đóng, và cố ý chưa gắn nút.** Yêu cầu là một nút *"Tạo bản ghi khắc phục"*
 * điền sẵn `alertEventPublicId` sang biểu mẫu MOD-02. Đo được: (1) ⛔ **không có tuyến
 * `/van-hanh/bao-tri`** — lịch sử bảo trì nằm trong trang chi tiết công trình; (2) dòng cảnh
 * báo hiện ⛔ **không mang** định danh công trình, vì nó gắn với *điểm đo*, và một điểm đo có
 * thể thuộc nhiều công trình; (3) biểu mẫu nhận ⛔ **chưa đọc** tham số `alertEventId` nào.
 *
 * ⇒ Gắn một nút dẫn tới tuyến không tồn tại là dựng đúng thứ T23.8 đã gọi tên: *"một liên kết
 * trỏ tới route không có thật trông như chức năng có mà hỏng, tệ hơn hẳn chức năng chưa có"*.
 * Nợ được ghi có số đo thay vì được che bằng một nút.
 *
 * ⛔ Và dù gắn nút thì cảnh báo vẫn ⛔ **không** tự sinh `maintenance_logs`: đó là quyết định
 * của con người. Tự sinh là đổ rác vào sổ gốc của cả MOD-02, và mỗi dòng rác còn kéo theo một
 * lượt tính lại trạng thái công trình. Vế đã dựng xong là **đường kiểm**: `OPS-2021` từ chối
 * một `alertEventId` không trỏ vào cảnh báo nào (T33.4).
 */
export function AlertHistoryPage() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [loc, setLoc] = useState<'tat-ca' | 'dang-mo' | 'da-dong'>('dang-mo');
  const [dangDong, setDangDong] = useState<{ row: AlertEventRow; baoDongGia: boolean } | null>(
    null,
  );
  const [ghiChu, setGhiChu] = useState('');

  const coXuLy = hasPermission('hyd:alert:handle');

  const dangMo = loc === 'tat-ca' ? undefined : loc === 'dang-mo';

  const query = useQuery({
    queryKey: ['hyd', 'alerts', loc],
    queryFn: () =>
      api.getPage<AlertEventRow>('/hyd/alerts', {
        size: 50,
        ...(dangMo === undefined ? {} : { dangMo }),
      }),
  });

  const dongMutation = useMutation({
    mutationFn: (v: { id: string; falseAlarm: boolean; note: string }) =>
      api.post(`/hyd/alerts/${v.id}/dong`, { falseAlarm: v.falseAlarm, note: v.note }),
    onSuccess: () => {
      message.success('Đã đóng cảnh báo');
      setDangDong(null);
      setGhiChu('');
      void queryClient.invalidateQueries({ queryKey: ['hyd', 'alerts'] });
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không đóng được cảnh báo'),
  });

  const columns: ColumnsType<AlertEventRow> = [
    {
      title: 'Điểm đo',
      width: 230,
      ellipsis: true,
      render: (_, r) => `${r.stationCode} — ${r.stationName}`,
    },
    {
      title: 'Mức',
      width: 150,
      ellipsis: true,
      render: (_, r) => <Tag>{r.alertLevelName}</Tag>,
    },
    {
      title: 'Trạng thái',
      width: 140,
      render: (_, r) => (
        <Tooltip title={TRANG_THAI_CANH_BAO[r.status].giaiThich}>
          <Tag color={TRANG_THAI_CANH_BAO[r.status].color}>
            {TRANG_THAI_CANH_BAO[r.status].label}
          </Tag>
        </Tooltip>
      ),
    },
    {
      // ⭐⭐ Cột chịu lực — xem javadoc lớp.
      title: 'Đã báo động',
      width: 130,
      render: (_, r) =>
        r.daXacNhan ? (
          <Tag color="red">Đã gửi</Tag>
        ) : (
          <Tooltip title="Điều kiện chưa giữ đủ số phút cấu hình — ⛔ chưa ai nhận thông báo nào">
            <Tag>Đang theo dõi</Tag>
          </Tooltip>
        ),
    },
    {
      title: 'Điều kiện',
      width: 160,
      render: (_, r) => LOAI_DIEU_KIEN_NGUONG[r.conditionType].label,
    },
    {
      title: 'Lý do',
      width: 300,
      ellipsis: true,
      render: (_, r) => <Typography.Text>{r.reason}</Typography.Text>,
    },
    {
      title: 'Đỉnh',
      width: 130,
      render: (_, r) => `${r.peakValue} ${r.unit}`,
    },
    {
      title: 'Bắt đầu',
      width: 170,
      render: (_, r) => formatDateTime(r.startedAt),
    },
    {
      title: 'Kết thúc',
      width: 170,
      render: (_, r) => (r.endedAt ? formatDateTime(r.endedAt) : '—'),
    },
    {
      // ⭐⭐ Cột chịu lực thứ hai — trống nghĩa là MÁY tự đóng, ⛔ không phải "đã có người xử lý".
      title: 'Người đóng',
      width: 130,
      render: (_, r) =>
        r.status === 'DANG_XAY_RA' ? (
          '—'
        ) : r.dongBoiNguoi ? (
          <Tag color="blue">Người trực</Tag>
        ) : (
          <Tooltip title="Giá trị tự về trong ngưỡng — ⛔ không có ai xử lý">
            <Tag>Tự hết</Tag>
          </Tooltip>
        ),
    },
    {
      title: '',
      width: 190,
      align: 'right',
      render: (_, r) => (
        <Space size={4}>
          {coXuLy && r.status === 'DANG_XAY_RA' && (
            <>
              <Tooltip title="Đã xử lý">
                <Button
                  type="text"
                  icon={<CheckCircleOutlined />}
                  onClick={() => setDangDong({ row: r, baoDongGia: false })}
                />
              </Tooltip>
              <Tooltip title="Báo động giả">
                <Button
                  type="text"
                  icon={<StopOutlined />}
                  onClick={() => setDangDong({ row: r, baoDongGia: true })}
                />
              </Tooltip>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="Lịch sử cảnh báo ngưỡng"
      extra={
        <Segmented
          value={loc}
          onChange={(v) => setLoc(v as typeof loc)}
          options={[
            { label: 'Đang mở', value: 'dang-mo' },
            { label: 'Đã đóng', value: 'da-dong' },
            { label: 'Tất cả', value: 'tat-ca' },
          ]}
        />
      }
    >
      <Table
        rowKey="id"
        size="small"
        loading={query.isLoading}
        dataSource={query.data?.items ?? []}
        columns={columns}
        pagination={false}
        scroll={{ x: 1900 }}
        locale={{
          emptyText:
            loc === 'dang-mo'
              ? 'Không có cảnh báo nào đang mở'
              : 'Chưa có cảnh báo nào trong khoảng này',
        }}
      />

      <Modal
        open={!!dangDong}
        title={dangDong?.baoDongGia ? 'Đánh dấu báo động giả' : 'Đóng cảnh báo — đã xử lý'}
        onCancel={() => {
          setDangDong(null);
          setGhiChu('');
        }}
        onOk={() =>
          dangDong &&
          dongMutation.mutate({
            id: dangDong.row.id,
            falseAlarm: dangDong.baoDongGia,
            note: ghiChu,
          })
        }
        confirmLoading={dongMutation.isPending}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary">
          {dangDong?.baoDongGia
            ? 'Dùng khi xem lại và kết luận cảnh báo này không phản ánh tình hình thật. Dòng vẫn nằm nguyên trong lịch sử.'
            : 'Dùng khi đã có người xử lý thực tế. ⛔ Không tự sinh bản ghi khắc phục — dùng nút riêng nếu cần ghi việc đã làm.'}
        </Typography.Paragraph>
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          value={ghiChu}
          onChange={(e) => setGhiChu(e.target.value)}
          placeholder="Ghi chú (không bắt buộc)"
        />
      </Modal>
    </Card>
  );
}

export default AlertHistoryPage;
