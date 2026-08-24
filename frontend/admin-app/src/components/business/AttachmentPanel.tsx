import { DeleteOutlined, DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, List, Popconfirm, Space, Typography, Upload } from 'antd';

import { type AttachmentView, type DownloadUrl } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { formatBytes, formatDate } from '@/shared/format';

import { StatusBadge } from './StatusBadge';
import { SCAN_STATUS } from './statusVocabulary';

/**
 * Khung tệp đính kèm dùng chung cho mọi entity (pattern P3 của WS-6).
 *
 * Bảng `attachments` là **đa hình**: một bảng giữ tệp cho công trình, bài viết, hồ sơ
 * nhân sự… nên đúng component này dùng lại được ở mọi module, chỉ đổi `ownerType`.
 *
 * <h3>Hai điều cố ý</h3>
 *
 * - **Tải xuống đi qua liên kết ký sẵn có hạn ngắn**, xin ngay lúc bấm chứ không dựng
 *   sẵn trong danh sách: dựng sẵn thì mỗi lần mở màn hình là sinh ra n liên kết sống,
 *   và chúng bỏ qua phân quyền theo thiết kế.
 * - **`downloadable` do backend tính**, FE không tự suy từ `scanStatus`. Điều kiện tải
 *   được gồm cả hiệu lực theo ngày (`validFrom`/`validUntil`) lẫn kết quả quét mã độc;
 *   suy lại ở FE là sớm muộn lệch với backend.
 */
export function AttachmentPanel({
  ownerType,
  ownerId,
  canUpload = false,
  canDelete = false,
}: {
  ownerType: string;
  ownerId: number;
  canUpload?: boolean;
  canDelete?: boolean;
}) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const queryKey = ['attachments', ownerType, ownerId];

  const { data, isLoading } = useQuery({
    queryKey,
    queryFn: () => api.get<AttachmentView[]>('/attachments', { ownerType, ownerId }),
  });

  const remove = useMutation({
    mutationFn: (publicId: string) => api.delete<void>(`/attachments/${publicId}`),
    onSuccess: async () => {
      message.success('Đã xóa tệp');
      await queryClient.invalidateQueries({ queryKey });
    },
    onError: (error: unknown) => {
      message.error(error instanceof ApiClientError ? error.message : 'Không xóa được tệp');
    },
  });

  const download = async (item: AttachmentView) => {
    try {
      const { url } = await api.get<DownloadUrl>(`/attachments/${item.publicId}/download-url`);
      window.open(url, '_blank', 'noopener');
    } catch (error) {
      message.error(
        error instanceof ApiClientError ? error.message : 'Không lấy được liên kết tải',
      );
    }
  };

  return (
    <>
      {canUpload && (
        <Upload
          multiple
          showUploadList={false}
          customRequest={({ file, onSuccess, onError }) => {
            // `ownerType`/`ownerId` đi ở query string chứ không trộn vào multipart:
            // backend khai chúng là `@RequestParam`, và để lẫn trong body multipart thì
            // việc nó bind được hay không phụ thuộc cấu hình parser — không đáng đánh cược.
            const form = new FormData();
            form.append('file', file as Blob);
            const query = new URLSearchParams({ ownerType, ownerId: String(ownerId) });

            api
              .upload<AttachmentView>(`/attachments?${query.toString()}`, form)
              .then(async (uploaded) => {
                onSuccess?.(uploaded);
                message.success('Đã tải tệp lên');
                await queryClient.invalidateQueries({ queryKey });
              })
              .catch((error: unknown) => {
                onError?.(error as Error);
                message.error(
                  error instanceof ApiClientError ? error.message : 'Tải tệp lên thất bại',
                );
              });
          }}
        >
          <Button icon={<UploadOutlined />} style={{ marginBottom: 12 }}>
            Tải tệp lên
          </Button>
        </Upload>
      )}

      <List<AttachmentView>
        loading={isLoading}
        dataSource={data ?? []}
        locale={{ emptyText: 'Chưa có tệp đính kèm' }}
        renderItem={(item) => (
          <List.Item
            actions={[
              <Button
                key="tai"
                type="link"
                icon={<DownloadOutlined />}
                disabled={!item.downloadable}
                onClick={() => void download(item)}
              >
                Tải
              </Button>,
              ...(canDelete
                ? [
                    <Popconfirm
                      key="xoa"
                      title="Xóa tệp này?"
                      okText="Xóa"
                      cancelText="Hủy"
                      onConfirm={() => remove.mutate(item.publicId)}
                    >
                      <Button type="link" danger icon={<DeleteOutlined />}>
                        Xóa
                      </Button>
                    </Popconfirm>,
                  ]
                : []),
            ]}
          >
            <List.Item.Meta
              title={item.originalName}
              description={
                <Space size="small" wrap>
                  <Typography.Text type="secondary">{formatBytes(item.sizeBytes)}</Typography.Text>
                  <StatusBadge value={item.scanStatus} vocabulary={SCAN_STATUS} />
                  {item.validUntil && (
                    <Typography.Text type="secondary">
                      Hiệu lực đến {formatDate(item.validUntil)}
                    </Typography.Text>
                  )}
                  {item.fileVersion > 1 && (
                    <Typography.Text type="secondary">Bản {item.fileVersion}</Typography.Text>
                  )}
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </>
  );
}
