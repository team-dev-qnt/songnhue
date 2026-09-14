import { DeleteOutlined, DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { type MaintenanceAttachment } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { formatBytes, formatDateTime } from '@/shared/format';

/**
 * Biên bản nghiệm thu, ảnh trước / sau của MỘT bản ghi sửa chữa — CN-02.2, T61.19.
 *
 * ⛔ Trước 14/09/2026 năm endpoint của khối này (dựng từ T18.6) có **0 nơi gọi** và **0 bài kiểm HTTP**
 * — `EndpointCoNoiGoiTest` lộ ra. Nghiệm thu hỏi *"ảnh trước/sau của lần sửa này ở đâu"* là ⛔ có câu
 * trả lời trên giao diện.
 *
 * ⚠ Tệp gắn vào **bản ghi**, ⛔ vào công trình (xem javadoc `MaintenanceAttachmentService`): ảnh "sau
 * khi sửa" mất ngữ cảnh lần sửa nào nếu nằm chung tab tài liệu công trình.
 */

/**
 * Nhãn loại tệp — gửi NGUYÊN CHỮ làm `docType` và backend lưu nguyên chữ vào `purpose`; cùng nhãn thì
 * tải lại thành phiên bản kế tiếp. Ba nhãn lấy từ javadoc `MaintenanceAttachmentService.upload`.
 */
const LOAI_TEP_SUA_CHUA = ['Biên bản nghiệm thu', 'Ảnh trước', 'Ảnh sau'] as const;

/** Khớp `DINH_DANG_NHAN` phía backend — chặn sớm ở hộp chọn tệp, backend vẫn là chốt thật. */
const NHAN_DINH_DANG = '.pdf,.doc,.docx,.jpg,.jpeg,.png,.webp';

export function MaintenanceAttachmentsPanel({ logId }: { logId: string }) {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [loai, setLoai] = useState<string>(LOAI_TEP_SUA_CHUA[0]);
  const [dangTai, setDangTai] = useState(false);

  const goc = `/ops/maintenance-logs/${logId}/attachments`;
  const queryKey = ['ops', 'maintenance-logs', 'attachments', logId];

  const { data, isLoading } = useQuery({
    queryKey,
    queryFn: () => api.get<MaintenanceAttachment[]>(goc),
  });

  const xoa = useMutation({
    mutationFn: (tepId: string) => api.delete<void>(`${goc}/${tepId}`),
    onSuccess: () => {
      message.success('Đã xoá tệp');
      void queryClient.invalidateQueries({ queryKey });
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được tệp'),
  });

  const taiVe = async (tep: MaintenanceAttachment) => {
    try {
      // Đường dẫn có hạn cấp riêng từng lượt bấm — ⛔ nhúng sẵn N đường còn sống vào bảng.
      const { url } = await api.get<{ url: string }>(`${goc}/${tep.id}/download-url`);
      window.open(url, '_blank', 'noopener');
    } catch (caught: unknown) {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không tải được tệp');
    }
  };

  const columns: ColumnsType<MaintenanceAttachment> = [
    {
      title: 'Tên tệp',
      dataIndex: 'originalName',
      render: (ten: string, row) => (
        <Space size={4} wrap>
          <span>{ten}</span>
          {row.fileVersion > 1 && <Tag color="blue">Phiên bản {row.fileVersion}</Tag>}
        </Space>
      ),
    },
    { title: 'Loại', dataIndex: 'purpose', width: 170 },
    {
      title: 'Dung lượng',
      dataIndex: 'sizeBytes',
      width: 100,
      align: 'right',
      render: (b: number) => formatBytes(b),
    },
    {
      title: 'Tải lên lúc',
      dataIndex: 'createdAt',
      width: 150,
      render: (luc: string) => formatDateTime(luc),
    },
    {
      title: 'Thao tác',
      key: 'thaoTac',
      width: 130,
      align: 'center',
      render: (_, row) => (
        <Space>
          {row.downloadable ? (
            <Tooltip title="Tải về">
              <Button
                type="text"
                aria-label={`Tải về ${row.originalName}`}
                icon={<DownloadOutlined />}
                onClick={() => void taiVe(row)}
              />
            </Tooltip>
          ) : (
            // ⛔ Ẩn nút thay vì để bấm rồi nhận SYS-0009 — nhưng NÓI RA vì sao ⛔ có nút.
            <Tag>Đang quét virus</Tag>
          )}
          {hasPermission('ops:document:delete') && (
            <Popconfirm
              title="Xoá tệp này?"
              description="Tệp được xoá mềm, vẫn truy vết được trong nhật ký."
              onConfirm={() => xoa.mutate(row.id)}
            >
              <Button
                type="text"
                danger
                aria-label={`Xoá ${row.originalName}`}
                icon={<DeleteOutlined />}
              />
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="small" style={{ width: '100%' }}>
      <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Text strong>Biên bản, ảnh trước / sau</Typography.Text>
        {hasPermission('ops:document:upload') && (
          <Space wrap>
            <Select
              aria-label="Loại tệp"
              size="small"
              style={{ width: 180 }}
              value={loai}
              onChange={setLoai}
              options={LOAI_TEP_SUA_CHUA.map((l) => ({ value: l, label: l }))}
            />
            <Upload
              showUploadList={false}
              accept={NHAN_DINH_DANG}
              customRequest={({ file, onSuccess, onError }) => {
                // `docType` ở query, tệp ở multipart — đúng chữ ký `@RequestParam` + `@RequestPart`.
                const query = new URLSearchParams({ docType: loai });
                const than = new FormData();
                than.append('file', file as File);
                setDangTai(true);
                api
                  .upload<MaintenanceAttachment>(`${goc}?${query.toString()}`, than)
                  .then((ket) => {
                    message.success('Đã tải tệp lên');
                    void queryClient.invalidateQueries({ queryKey });
                    onSuccess?.(ket);
                  })
                  .catch((caught: unknown) => {
                    message.error(
                      caught instanceof ApiClientError ? caught.message : 'Không tải được tệp lên',
                    );
                    onError?.(caught as Error);
                  })
                  .finally(() => setDangTai(false));
              }}
            >
              <Button size="small" icon={<UploadOutlined />} loading={dangTai}>
                Tải tệp
              </Button>
            </Upload>
          </Space>
        )}
      </Space>
      <Table<MaintenanceAttachment>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={data ?? []}
        loading={isLoading}
        pagination={false}
        scroll={{ x: 640 }}
        locale={{ emptyText: 'Chưa có biên bản hay ảnh nào cho bản ghi này' }}
      />
    </Space>
  );
}
