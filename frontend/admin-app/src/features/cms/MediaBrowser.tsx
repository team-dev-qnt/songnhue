import { FolderOutlined, InboxOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Empty,
  Image,
  Input,
  List,
  Progress,
  Space,
  Tree,
  Typography,
  Upload,
} from 'antd';
import { type DataNode } from 'antd/es/tree';
import { useMemo, useState } from 'react';

import { ApiClientError } from '@/shared/apiClient';
import { formatBytes, formatDateTime } from '@/shared/format';

import { cmsApi, cmsKeys } from './api';
import { buildTree } from './tree';
import { type FolderNode, type MediaFile } from './types';

/**
 * Duyệt thư viện media — phần dùng chung giữa màn hình quản lý (T20.7) và hộp chọn ảnh cho
 * bài viết.
 *
 * <h3>Vì sao tách ra khỏi cả hai</h3>
 *
 * Hai màn hình khác nhau ở phần *làm gì với tệp đã chọn*, chứ không khác ở phần duyệt. Để
 * mỗi bên tự viết cây thư mục và lưới ảnh thì lần đổi sau (thêm bộ lọc, đổi cách hiện dung
 * lượng) phải sửa hai chỗ — và chỗ thứ hai sẽ bị quên, vì nó nằm trong một hộp thoại ít ai
 * mở.
 */

export interface MediaBrowserProps {
  /** Chỉ nhận ảnh — dùng khi mở từ trình soạn thảo. */
  imagesOnly?: boolean;
  selectedId?: string | null;
  onSelect?: (file: MediaFile) => void;
  /** Ô thao tác thêm ở mỗi dòng — màn hình quản lý cắm nút xoá vào đây. */
  renderFileExtra?: (file: MediaFile) => React.ReactNode;
  height?: number;
}

/** Ảnh xem trước đi qua đường công khai ổn định, không phải presigned URL sống 10 phút. */
function previewUrl(publicId: string): string {
  return `/api/v1/public/files/${publicId}`;
}

export function MediaBrowser({
  imagesOnly = false,
  selectedId = null,
  onSelect,
  renderFileExtra,
  height = 420,
}: MediaBrowserProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [folderId, setFolderId] = useState<string | null>(null);
  const [keyword, setKeyword] = useState('');
  /** Tiến trình từng tệp — khoá là `uid` của AntD Upload, không phải tên tệp (tên trùng được). */
  const [progress, setProgress] = useState<Record<string, number>>({});

  const folders = useQuery({
    queryKey: cmsKeys.folders(),
    queryFn: () => cmsApi.folders(),
  });

  // Thư mục đầu tiên được chọn sẵn: mở ra một khung trống kèm dòng "chọn thư mục" là bắt
  // người dùng làm một thao tác mà máy tự làm được.
  const activeFolder = folderId ?? folders.data?.[0]?.publicId ?? null;

  const files = useQuery({
    queryKey: cmsKeys.files(activeFolder),
    queryFn: () => (activeFolder ? cmsApi.files(activeFolder) : Promise.resolve([])),
    enabled: activeFolder !== null,
  });

  const upload = useMutation({
    mutationFn: ({ file }: { file: File; uid: string }) => {
      if (!activeFolder) {
        return Promise.reject(new Error('Chưa chọn thư mục'));
      }
      return cmsApi.uploadFile(activeFolder, file);
    },
    onSuccess: async (_data, variables) => {
      setProgress((prev) => ({ ...prev, [variables.uid]: 100 }));
      await queryClient.invalidateQueries({ queryKey: cmsKeys.files(activeFolder) });
    },
    onError: (caught: unknown, variables) => {
      // Giữ lại dòng tiến trình ở mức đang dở: xoá đi thì tệp hỏng biến mất khỏi danh sách
      // và người dùng không biết tệp nào không lên được.
      setProgress((prev) => ({ ...prev, [variables.uid]: -1 }));
      message.error(caught instanceof ApiClientError ? caught.message : 'Không tải được tệp lên');
    },
  });

  const treeData: DataNode[] = useMemo(() => {
    const toNode = (item: ReturnType<typeof buildTree<FolderNode>>[number]): DataNode => ({
      key: item.value.publicId,
      title: item.value.name,
      icon: <FolderOutlined />,
      children: item.children.map(toNode),
    });
    return buildTree(folders.data ?? []).map(toNode);
  }, [folders.data]);

  const hienThi = (files.data ?? [])
    .filter((file) => !imagesOnly || file.contentType.startsWith('image/'))
    .filter((file) => file.originalName.toLowerCase().includes(keyword.trim().toLowerCase()));

  return (
    <div style={{ display: 'flex', gap: 16, height, minHeight: 0 }}>
      <div
        style={{ width: 220, overflow: 'auto', borderRight: '1px solid #f0f0f0', paddingRight: 8 }}
      >
        {folders.isLoading ? (
          <Typography.Text type="secondary">Đang tải thư mục…</Typography.Text>
        ) : treeData.length === 0 ? (
          <Empty description="Chưa có thư mục" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Tree
            showIcon
            blockNode
            treeData={treeData}
            selectedKeys={activeFolder ? [activeFolder] : []}
            defaultExpandAll
            onSelect={(keys) => setFolderId(keys.length > 0 ? String(keys[0]) : null)}
          />
        )}
      </div>

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <Space style={{ marginBottom: 8 }} wrap>
          <Input.Search
            allowClear
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="Lọc theo tên tệp"
            style={{ width: 240 }}
          />
          <Upload
            multiple
            showUploadList={false}
            disabled={activeFolder === null}
            beforeUpload={(file) => {
              const uid = (file as unknown as { uid: string }).uid;
              setProgress((prev) => ({ ...prev, [uid]: 0 }));
              upload.mutate({ file, uid });
              // `false` = tự lo việc tải lên; AntD không được tự gọi `action`, vì mọi
              // request phải đi qua `apiClient` để mang token và vé CSRF.
              return false;
            }}
          >
            <Button icon={<InboxOutlined />} disabled={activeFolder === null}>
              Tải tệp lên
            </Button>
          </Upload>
          <Button
            icon={<ReloadOutlined />}
            onClick={() =>
              void queryClient.invalidateQueries({ queryKey: cmsKeys.files(activeFolder) })
            }
          />
        </Space>

        {Object.entries(progress).length > 0 && (
          <Space direction="vertical" size={2} style={{ marginBottom: 8 }}>
            {Object.entries(progress).map(([uid, percent]) => (
              <Progress
                key={uid}
                percent={percent < 0 ? 100 : percent}
                status={percent < 0 ? 'exception' : percent === 100 ? 'success' : 'active'}
                size="small"
                style={{ width: 260 }}
              />
            ))}
          </Space>
        )}

        <div style={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
          <List<MediaFile>
            grid={{ gutter: 12, xs: 2, sm: 3, md: 4, lg: 5 }}
            dataSource={hienThi}
            loading={files.isLoading}
            locale={{ emptyText: <Empty description="Thư mục này chưa có tệp nào" /> }}
            renderItem={(file) => {
              const laAnh = file.contentType.startsWith('image/');
              const dangChon = file.publicId === selectedId;
              return (
                <List.Item>
                  <div
                    role={onSelect ? 'button' : undefined}
                    tabIndex={onSelect ? 0 : undefined}
                    onClick={() => onSelect?.(file)}
                    onKeyDown={(event) => {
                      if (onSelect && (event.key === 'Enter' || event.key === ' ')) {
                        event.preventDefault();
                        onSelect(file);
                      }
                    }}
                    style={{
                      border: `2px solid ${dangChon ? '#1677ff' : '#f0f0f0'}`,
                      borderRadius: 6,
                      padding: 6,
                      cursor: onSelect ? 'pointer' : 'default',
                    }}
                  >
                    {laAnh ? (
                      <Image
                        src={previewUrl(file.publicId)}
                        alt={file.originalName}
                        height={90}
                        width="100%"
                        style={{ objectFit: 'cover' }}
                        preview={false}
                        fallback="data:image/gif;base64,R0lGODlhAQABAAAAACw="
                      />
                    ) : (
                      <div
                        style={{
                          height: 90,
                          display: 'grid',
                          placeItems: 'center',
                          background: '#fafafa',
                          fontSize: 12,
                          color: '#8c8c8c',
                        }}
                      >
                        {file.contentType.split('/').pop()}
                      </div>
                    )}
                    <Typography.Text
                      ellipsis
                      style={{ display: 'block', fontSize: 12, marginTop: 4 }}
                    >
                      {file.originalName}
                    </Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                      {formatBytes(file.sizeBytes)} · {formatDateTime(file.createdAt)}
                    </Typography.Text>
                    {renderFileExtra?.(file)}
                  </div>
                </List.Item>
              );
            }}
          />
        </div>
      </div>
    </div>
  );
}
