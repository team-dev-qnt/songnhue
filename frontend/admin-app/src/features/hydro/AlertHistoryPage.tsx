import { CheckCircleOutlined, StopOutlined, ToolOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
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
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { type AlertEventRow } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { formatDateTime } from '@/shared/format';

import { TaoBanGhiKhacPhucModal } from './TaoBanGhiKhacPhucModal';
import { LOAI_DIEU_KIEN_NGUONG, TRANG_THAI_CANH_BAO } from './hydroVocabulary';

const NHAN_TAO_KHAC_PHUC = 'Tạo bản ghi khắc phục';

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
 * ✅ **T33.10 đóng 24/09/2026** — nút *"Tạo bản ghi khắc phục"* ở cột thao tác. Ba trở ngại mà
 * bản ghi cũ ở đây kê ra đều **vẫn đúng**; cái đổi là cách đi vòng qua chúng:
 *
 * <ol>
 *   <li>*"⛔ có tuyến `/van-hanh/bao-tri`"* ⇒ **⛔ điều hướng đi đâu cả** — mở thẳng
 *       {@code MaintenanceFormModal} tại chỗ. Lý do T23.8 (*"một liên kết trỏ tới route ⛔ có
 *       thật trông như chức năng có mà hỏng"*) vì thế ⛔ còn áp dụng: ⛔ có liên kết nào.
 *   <li>*"dòng cảnh báo ⛔ mang định danh công trình"* ⇒ {@code TaoBanGhiKhacPhucModal} tra
 *       {@code GET /hyd/stations/&#123;publicId&#125;} (đã trả kèm {@code constructions[]}) rồi
 *       **bắt người dùng CHỌN** khi có nhiều hơn một — ⛔ đoán hộ, vì gắn sự cố vào sai hồ sơ là
 *       sai ở đúng nơi Công ty dùng để quyết toán sửa chữa.
 *   <li>*"biểu mẫu ⛔ đọc tham số `alertEventId`"* ⇒ hai prop mới
 *       ({@code loaiMacDinh} · {@code alertEventId}), và {@code dungPayloadSuaChua} nay **gửi**
 *       trường ấy ở đường TẠO. Trước lượt này cả cơ chế — cột, entity, DTO, {@code OPS-2021},
 *       {@code HydroAlertPort} — đứng đủ mà ⛔ một đường nào của người dùng ghi nổi vào đó.
 * </ol>
 *
 * ⛔ Và cảnh báo vẫn ⛔ **tự sinh** `maintenance_logs`: đó là quyết định của con người. Tự sinh là
 * đổ rác vào sổ gốc của cả MOD-02, và mỗi dòng rác còn kéo theo một lượt tính lại trạng thái công
 * trình. Nút chỉ **điền sẵn**; người trực vẫn bấm Lưu, và vẫn phải tự khai Mức độ — mức cảnh báo
 * nói về **mực nước**, ⛔ nói về mức độ hư hỏng công trình.
 */
export function AlertHistoryPage() {
  const { message } = App.useApp();
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [loc, setLoc] = useState<'tat-ca' | 'dang-mo' | 'da-dong'>('dang-mo');
  const [dangDong, setDangDong] = useState<{ row: AlertEventRow; baoDongGia: boolean } | null>(
    null,
  );
  const [ghiChu, setGhiChu] = useState('');
  const [dangTaoKhacPhuc, setDangTaoKhacPhuc] = useState<AlertEventRow | null>(null);

  const coXuLy = hasPermission('hyd:alert:handle');
  // ⛔ `hyd:alert:handle`: nút này TẠO một bản ghi của MOD-02, nên quyền phải là quyền của việc
  //   nó làm. Bày ra cho người ⛔ có quyền ấy là dựng một lựa chọn chắc chắn trả 403.
  const coGhiSuCo = hasPermission('ops:maintenance:report-incident');

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
          {/*
            ⭐⭐ T33.10 — ⛔ gắn với `status`: bản ghi khắc phục thường được ghi SAU khi cảnh báo
            đã kết thúc (người ta đi xử lý xong mới ngồi ghi), nên khoá nút ở dòng "Đã đóng" là
            khoá đúng lúc nó hay được dùng nhất.
          */}
          {coGhiSuCo && (
            <Tooltip title={NHAN_TAO_KHAC_PHUC}>
              <Button
                type="text"
                aria-label={NHAN_TAO_KHAC_PHUC}
                icon={<ToolOutlined />}
                onClick={() => setDangTaoKhacPhuc(r)}
              />
            </Tooltip>
          )}
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
        destroyOnHidden
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

      {/*
        ⚠ Dựng theo ĐIỀU KIỆN, ⛔ truyền `open={!!…}` — bên trong nó tra `GET /hyd/stations/{id}`
        theo `canhBao.stationId`, và một hộp thoại luôn-tồn-tại sẽ giữ `useQuery` của cảnh báo
        MỞ TRƯỚC ĐÓ (T51.12 ở dạng truy vấn: dữ liệu cũ hiện dưới tên mới).
      */}
      {dangTaoKhacPhuc && (
        <TaoBanGhiKhacPhucModal
          key={dangTaoKhacPhuc.id}
          canhBao={dangTaoKhacPhuc}
          onClose={() => setDangTaoKhacPhuc(null)}
          onSaved={() => setDangTaoKhacPhuc(null)}
        />
      )}
    </Card>
  );
}

export default AlertHistoryPage;
