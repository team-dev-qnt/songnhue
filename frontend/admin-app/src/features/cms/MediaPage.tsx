import { FolderAddOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Form, Input, Modal, Space, Typography } from 'antd';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { ApiClientError } from '@/shared/apiClient';

import { cmsApi, cmsKeys } from './api';
import { MediaBrowser } from './MediaBrowser';
import { type KhoTep, type MediaFile } from './types';

/**
 * Thư viện media và **Kho tài liệu** — T20.7, CN-01.3, WS-40.
 *
 * <h3>Xoá tệp: hỏi trước xem có ai đang dùng nó không</h3>
 *
 * Backend có sẵn endpoint liệt kê bài viết đang tham chiếu một tệp. Không hỏi thì việc xoá
 * một ảnh sẽ làm **hỏng ảnh trong những bài đã xuất bản** — mà bài đó đã dựng sẵn trên cổng
 * và sẽ giữ ô ảnh vỡ suốt tới chu kỳ dựng lại kế tiếp. Người xoá thì không hề biết.
 *
 * <h3>⭐ Một trang, hai kho — ⛔ không chép thành tệp thứ hai</h3>
 *
 * Kho tài liệu khác thư viện media ở **đúng một thứ**: `owner_type`, tức phạm vi công bố. Cây
 * thư mục, đường tải lên, magic-bytes, hạn mức, quét virus, chốt chặn xoá đều dùng chung. Chép
 * màn hình ra thành hai tệp là dựng hai bản của cùng một luật, rồi lượt sửa sau chỉ chạm được
 * một bản.
 */
export function MediaPage({ kho = 'MEDIA' }: { kho?: KhoTep } = {}) {
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

  const laTaiLieu = kho === 'TAI_LIEU';

  return (
    <Card
      title={laTaiLieu ? 'Kho tài liệu' : 'Thư viện media'}
      extra={
        coQuyen && (
          <Button icon={<FolderAddOutlined />} onClick={() => setCreatingFolder(true)}>
            Thư mục mới
          </Button>
        )
      }
    >
      {/* ⭐ Một dòng CHỮ THẬT, không phải tooltip. Ranh giới công bố là thứ người vận hành phải
          biết TRƯỚC khi tải tệp lên, và một ghi chú chỉ hiện khi rê chuột thì không ai đọc.
          ⚠ Nói ra cả điều lâu nay đúng mà chưa ai viết: ảnh trong thư viện media công khai NGAY
            khi tải lên, kể cả khi chưa bài nào dùng. Đó là hành vi CỐ Ý, có ba bài kiểm đóng
            đinh — đợt WS-40 không đụng tới, chỉ tài liệu mới siết. */}
      <Typography.Paragraph type="secondary" style={{ fontSize: 13 }}>
        {laTaiLieu ? (
          <>
            Tài liệu ở đây <strong>chưa công khai</strong>. Chúng chỉ tải về được từ cổng khi đã
            đính vào một bài viết <strong>đã xuất bản</strong> — bài còn nháp hoặc đã gỡ thì đường
            tải trả về &ldquo;không tìm thấy&rdquo;. Nhận PDF, DOC/DOCX, XLS/XLSX, ZIP.
          </>
        ) : (
          <>
            Ảnh và video ở đây <strong>công khai ngay khi tải lên</strong>, kể cả khi chưa bài nào
            dùng — ai biết đường dẫn đều xem được. Tài liệu (PDF, DOCX, XLSX…) đi{' '}
            <strong>Kho tài liệu</strong> riêng, có kiểm trạng thái xuất bản.
          </>
        )}
      </Typography.Paragraph>

      <MediaBrowser
        kho={kho}
        loai={laTaiLieu ? 'document' : undefined}
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

/**
 * Kho tài liệu — cùng màn hình, khác kho (WS-40).
 *
 * ⚠ Tồn tại vì `lazyPage` của `router.tsx` render component **không truyền prop nào**. Một
 * component bọc ba dòng ở đây rẻ hơn hẳn việc nới `lazyPage` thành nhận props: nới nó là mở
 * cho mọi tuyến truyền trạng thái qua bảng định tuyến, đúng thứ khó lần ngược nhất khi đọc mã.
 *
 * ⛔ Không chép `MediaPage` ra tệp thứ hai — xem javadoc của nó.
 */
export function KhoTaiLieuPage() {
  return <MediaPage kho="TAI_LIEU" />;
}
