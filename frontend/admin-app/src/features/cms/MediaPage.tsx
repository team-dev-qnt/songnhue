import { FolderAddOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Form, Input, Modal, Space, Typography } from 'antd';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { ApiClientError } from '@/shared/apiClient';

import { cmsApi, cmsKeys } from './api';
import { MediaBrowser } from './MediaBrowser';
import { type MediaFile } from './types';

/**
 * Thư viện media — T20.7, CN-01.3.
 *
 * <h3>Xoá tệp: hỏi trước xem có ai đang dùng nó không</h3>
 *
 * Backend có sẵn endpoint liệt kê bài viết đang tham chiếu một tệp. Không hỏi thì việc xoá
 * một ảnh sẽ làm **hỏng ảnh trong những bài đã xuất bản** — mà bài đó đã dựng sẵn trên cổng
 * và sẽ giữ ô ảnh vỡ suốt tới chu kỳ dựng lại kế tiếp. Người xoá thì không hề biết.
 */
export function MediaPage() {
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const coQuyen = hasPermission('cms:media:manage');

  const [form] = Form.useForm<{ name: string }>();
  const [creatingFolder, setCreatingFolder] = useState(false);

  const folders = useQuery({ queryKey: cmsKeys.folders(), queryFn: () => cmsApi.folders() });

  const createFolder = useMutation({
    mutationFn: (name: string) => cmsApi.createFolder({ name }),
    onSuccess: async () => {
      message.success('Đã tạo thư mục');
      setCreatingFolder(false);
      form.resetFields();
      await queryClient.invalidateQueries({ queryKey: cmsKeys.folders() });
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không tạo được thư mục'),
  });

  const remove = useMutation({
    mutationFn: (publicId: string) => cmsApi.deleteFile(publicId),
    onSuccess: async () => {
      message.success('Đã xoá tệp');
      await queryClient.invalidateQueries({ queryKey: ['cms', 'files'] });
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được tệp'),
  });

  /** Hỏi backend xem tệp đang được dùng ở đâu, rồi mới hỏi người dùng. */
  const xacNhanXoa = async (file: MediaFile) => {
    let dangDung: string[] = [];
    try {
      dangDung = await cmsApi.fileUsages(file.publicId);
    } catch {
      // Không tra được thì vẫn cho xoá, nhưng nói rõ là chưa kiểm tra được — im lặng ở đây
      // sẽ khiến người dùng tưởng "không có dòng nào" nghĩa là "không ai dùng".
      dangDung = ['(không kiểm tra được danh sách bài đang dùng tệp này)'];
    }

    modal.confirm({
      title: `Xoá tệp "${file.originalName}"?`,
      width: 520,
      okText: 'Xoá',
      okButtonProps: { danger: true },
      cancelText: 'Huỷ',
      content:
        dangDung.length === 0 ? (
          <Typography.Text>Không bài viết nào đang dùng tệp này.</Typography.Text>
        ) : (
          <Space direction="vertical">
            <Typography.Text strong type="danger">
              {dangDung.length} bài viết đang dùng tệp này:
            </Typography.Text>
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {dangDung.slice(0, 10).map((title) => (
                <li key={title}>{title}</li>
              ))}
            </ul>
            {dangDung.length > 10 && (
              <Typography.Text type="secondary">
                …và {dangDung.length - 10} bài khác
              </Typography.Text>
            )}
            <Typography.Text type="warning">
              Xoá xong, ảnh trong những bài đó sẽ vỡ — kể cả bài đã xuất bản.
            </Typography.Text>
          </Space>
        ),
      onOk: () => remove.mutate(file.publicId),
    });
  };

  return (
    <Card
      title="Thư viện media"
      extra={
        coQuyen && (
          <Button icon={<FolderAddOutlined />} onClick={() => setCreatingFolder(true)}>
            Thư mục mới
          </Button>
        )
      }
    >
      <MediaBrowser
        height={560}
        renderFileExtra={
          coQuyen
            ? (file) => (
                <Button
                  type="link"
                  size="small"
                  danger
                  style={{ padding: 0 }}
                  onClick={() => void xacNhanXoa(file)}
                >
                  Xoá
                </Button>
              )
            : undefined
        }
      />

      <Modal
        open={creatingFolder}
        title="Thư mục mới"
        okText="Tạo"
        cancelText="Huỷ"
        confirmLoading={createFolder.isPending}
        onCancel={() => {
          setCreatingFolder(false);
          form.resetFields();
        }}
        onOk={async () => {
          const values = await form.validateFields();
          createFolder.mutate(values.name);
        }}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="Tên thư mục"
            rules={[{ required: true, message: 'Nhập tên thư mục' }]}
            extra={`Đang có ${folders.data?.length ?? 0} thư mục. Cây media sâu tối đa 3 cấp.`}
          >
            <Input autoFocus />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
