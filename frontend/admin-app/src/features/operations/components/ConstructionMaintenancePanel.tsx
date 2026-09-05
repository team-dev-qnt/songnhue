import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Empty, Space, Statistic, Tag, Timeline, Typography } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { ApprovalActions } from '@/components/business/ApprovalActions';
import { StatusBadge } from '@/components/business/StatusBadge';
import { MAINTENANCE_STATUS, MAINTENANCE_TYPE } from '@/components/business/statusVocabulary';
import {
  type MaintenanceCostSummary,
  type MaintenanceDetail,
  type MaintenanceRow,
} from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { formatInvestment } from '@/shared/format';

import { MaintenanceFormModal } from './MaintenanceFormModal';

/**
 * Lịch sử sửa chữa / bảo trì / khắc phục sự cố của một công trình — CN-02.2, T21.5.
 *
 * ⛔ Chỗ này trước đây là một câu chữ: *"Lịch sử sửa chữa sẽ được tích hợp trong phiên bản sau."* —
 * và nó vẫn được đánh dấu ✅ trong bản ghi tiến độ ngày 22/8. Một tab mở ra, hiện một dòng chữ, và
 * không ai đọc bản ghi tiến độ để biết rằng nó chưa làm.
 *
 * ⚠ Nút chuyển trạng thái render từ `allowedActions` của API, **không** từ một bảng `if` phía giao
 * diện. Quy tắc 4: quyền của từng bước chuyển nằm ở `workflow_transitions.required_permission`;
 * giao diện đoán lại tập nút là dựng một bản sao thứ hai của luật, và hai bản sao sẽ lệch nhau.
 */
export function ConstructionMaintenancePanel({
  constructionPublicId,
}: {
  constructionPublicId: string;
}) {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [dangChon, setDangChon] = useState<string | null>(null);

  const queryKey = ['ops', 'maintenance-logs', constructionPublicId];

  const { data: trang, isLoading } = useQuery({
    queryKey,
    queryFn: () =>
      api.getPage<MaintenanceRow>('/ops/maintenance-logs', {
        constructionId: constructionPublicId,
        size: 50,
        sort: 'startedOn,desc',
      }),
  });

  // Tổng chi phí kỳ tính ở BE (quy tắc 3) — giao diện không tự cộng cột `cost`. Cộng ở FE thì con
  // số chỉ đúng trên trang đang mở, và sẽ lệch ngay khi có phân trang.
  const { data: chiPhi } = useQuery({
    queryKey: ['ops', 'maintenance-logs', 'cost-summary', constructionPublicId],
    queryFn: () =>
      api.get<MaintenanceCostSummary>('/ops/maintenance-logs/cost-summary', {
        constructionId: constructionPublicId,
      }),
  });

  const { data: chiTiet } = useQuery({
    queryKey: ['ops', 'maintenance-logs', 'detail', dangChon],
    queryFn: () => api.get<MaintenanceDetail>(`/ops/maintenance-logs/${dangChon}`),
    enabled: !!dangChon,
  });

  const bamNut = useMutation({
    mutationFn: (input: { publicId: string; action: string }) =>
      api.post<MaintenanceDetail>(`/ops/maintenance-logs/${input.publicId}/actions`, {
        action: input.action,
        // Ngày hoàn thành để backend tự quyết: chuyển sang "Đã xử lý" mà thiếu ngày thì OPS-2004,
        // và người dùng đang đứng ở màn hình lịch sử chứ không phải biểu mẫu nhập.
        completedOn: dayjs().format('YYYY-MM-DD'),
      }),
    onSuccess: () => {
      message.success('Đã chuyển trạng thái bản ghi');
      void queryClient.invalidateQueries({ queryKey });
      void queryClient.invalidateQueries({ queryKey: ['ops', 'maintenance-logs', 'detail'] });
      // Trạng thái công trình là giá trị DẪN XUẤT từ chính những bản ghi này — đóng một sự cố là
      // cờ đỏ tắt. Không làm mới hồ sơ công trình thì màn hình hiện trạng thái cũ cho tới lượt F5.
      void queryClient.invalidateQueries({ queryKey: ['ops', 'constructions'] });
    },
    onError: (caught: unknown) =>
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không chuyển được trạng thái',
      ),
  });

  const banGhi = trang?.items ?? [];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Space size="large" align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
        <Space size="large">
          <Statistic
            title="Tổng chi phí đã ghi nhận"
            value={chiPhi ? (formatInvestment(Number(chiPhi.total)) ?? '—') : '—'}
            valueStyle={{ fontSize: 20 }}
          />
          <Statistic
            title="Số bản ghi"
            value={trang?.meta.totalElements ?? 0}
            valueStyle={{ fontSize: 20 }}
          />
        </Space>

        {(hasPermission('ops:maintenance:create') ||
          hasPermission('ops:maintenance:report-incident')) && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setFormOpen(true)}>
            Ghi nhận công việc
          </Button>
        )}
      </Space>

      {isLoading ? null : banGhi.length === 0 ? (
        <Empty description="Công trình này chưa có bản ghi sửa chữa nào" />
      ) : (
        <Timeline
          mode="left"
          items={banGhi.map((row) => ({
            color:
              row.workType === 'KHAC_PHUC_SU_CO'
                ? row.status === 'DA_XU_LY'
                  ? 'green'
                  : 'red'
                : 'blue',
            label: row.startedOn ? dayjs(row.startedOn).format('DD/MM/YYYY') : '—',
            children: (
              <Card
                size="small"
                onClick={() => setDangChon(row.id)}
                style={{ cursor: 'pointer' }}
                title={
                  <Space wrap>
                    <Typography.Text strong>{row.code}</Typography.Text>
                    <StatusBadge value={row.workType} vocabulary={MAINTENANCE_TYPE} />
                    <StatusBadge value={row.status} vocabulary={MAINTENANCE_STATUS} />
                    {row.severity && <Tag color="volcano">Mức {row.severity}</Tag>}
                  </Space>
                }
                extra={row.cost ? formatInvestment(Number(row.cost)) : null}
              >
                <Space direction="vertical" size={2} style={{ width: '100%' }}>
                  <Typography.Text>{row.content}</Typography.Text>
                  <Typography.Text type="secondary">
                    {row.performer}
                    {row.performerIsInternal ? ' (nội bộ)' : ' (thuê ngoài)'}
                    {row.completedOn &&
                      ` · hoàn thành ${dayjs(row.completedOn).format('DD/MM/YYYY')}`}
                  </Typography.Text>

                  {dangChon === row.id && chiTiet && (
                    <div style={{ marginTop: 8 }} onClick={(e) => e.stopPropagation()}>
                      <ApprovalActions
                        actions={chiTiet.actions}
                        disabled={bamNut.isPending}
                        onAction={async (action) => {
                          await bamNut.mutateAsync({ publicId: row.id, action });
                        }}
                      />
                    </div>
                  )}
                </Space>
              </Card>
            ),
          }))}
        />
      )}

      <MaintenanceFormModal
        constructionPublicId={constructionPublicId}
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          void queryClient.invalidateQueries({ queryKey });
          void queryClient.invalidateQueries({ queryKey: ['ops', 'constructions'] });
        }}
      />
    </Space>
  );
}
