import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Alert,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Tree,
  Typography,
} from 'antd';
import { type DataNode } from 'antd/es/tree';
import { useMemo, useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { ApiClientError } from '@/shared/apiClient';

import { cmsApi, cmsKeys } from './api';
import { buildTree, canDropInto } from './tree';
import { type CategoryNode } from './types';

/**
 * Cây danh mục nội dung — T20.6, CN-01.2.
 *
 * <h3>Kéo thả, và chỗ phải chặn trước khi gửi lên</h3>
 *
 * Kéo một danh mục vào chính nhánh con của nó sẽ cắt cả nhánh ra khỏi cây. Backend chặn
 * việc đó, nhưng để người dùng kéo tới nơi, thả xuống, rồi mới nhận thông báo lỗi là bắt họ
 * làm lại từ đầu — và trên một cây ba cấp thì thao tác kéo không hề nhẹ. Chặn ngay lúc thả
 * bằng `canDropInto`.
 *
 * ⛔ Chặn ở đây **không thay thế** chốt chặn của backend: một lượt gọi API thẳng vẫn phải bị
 * từ chối. Đây chỉ là phép lịch sự với người dùng.
 */
export function CategoriesPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const coQuyen = hasPermission('cms:category:manage');

  const [form] = Form.useForm<{ name: string; slug?: string }>();
  const [editing, setEditing] = useState<CategoryNode | null>(null);
  const [creatingUnder, setCreatingUnder] = useState<CategoryNode | null | undefined>(undefined);

  const categories = useQuery({
    queryKey: cmsKeys.categories(),
    queryFn: () => cmsApi.categories(),
  });
  const items = useMemo(() => categories.data ?? [], [categories.data]);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: cmsKeys.categories() });

  const baoLoi = (caught: unknown, fallback: string) =>
    message.error(caught instanceof ApiClientError ? caught.message : fallback);

  const create = useMutation({
    mutationFn: (body: { name: string; slug?: string; parentId?: string | null }) =>
      cmsApi.createCategory(body),
    onSuccess: async () => {
      message.success('Đã thêm danh mục');
      setCreatingUnder(undefined);
      form.resetFields();
      await invalidate();
    },
    onError: (caught) => baoLoi(caught, 'Không thêm được danh mục'),
  });

  const rename = useMutation({
    mutationFn: ({ publicId, ...body }: { publicId: string; name: string; slug?: string }) =>
      cmsApi.renameCategory(publicId, body),
    onSuccess: async () => {
      message.success('Đã cập nhật danh mục');
      setEditing(null);
      form.resetFields();
      await invalidate();
    },
    onError: (caught) => baoLoi(caught, 'Không đổi được tên danh mục'),
  });

  const move = useMutation({
    mutationFn: ({ publicId, newParentId }: { publicId: string; newParentId: string | null }) =>
      cmsApi.moveCategory(publicId, newParentId),
    onSuccess: invalidate,
    onError: (caught) => baoLoi(caught, 'Không di chuyển được danh mục'),
  });

  const remove = useMutation({
    mutationFn: (publicId: string) => cmsApi.deleteCategory(publicId),
    onSuccess: async () => {
      message.success('Đã xoá danh mục');
      await invalidate();
    },
    // Danh mục còn bài viết thì backend trả mã lỗi riêng kèm câu giải thích — hiện nguyên văn.
    onError: (caught) => baoLoi(caught, 'Không xoá được danh mục'),
  });

  const treeData: DataNode[] = useMemo(() => {
    const toNode = (item: ReturnType<typeof buildTree<CategoryNode>>[number]): DataNode => ({
      key: item.value.publicId,
      title: (
        <Space>
          <span>{item.value.name}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            /{item.value.slug}
          </Typography.Text>
          {coQuyen && (
            <>
              <Button
                type="link"
                size="small"
                onClick={(event) => {
                  event.stopPropagation();
                  setEditing(item.value);
                  form.setFieldsValue({ name: item.value.name, slug: item.value.slug });
                }}
              >
                Sửa
              </Button>
              <Button
                type="link"
                size="small"
                onClick={(event) => {
                  event.stopPropagation();
                  setCreatingUnder(item.value);
                  form.resetFields();
                }}
              >
                Thêm con
              </Button>
              <Popconfirm
                title="Xoá danh mục?"
                description="Danh mục còn bài viết sẽ không xoá được — chuyển bài sang danh mục khác trước."
                okText="Xoá"
                cancelText="Huỷ"
                onConfirm={() => remove.mutate(item.value.publicId)}
              >
                <Button
                  type="link"
                  size="small"
                  danger
                  onClick={(event) => event.stopPropagation()}
                >
                  Xoá
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
      children: item.children.map(toNode),
    });
    return buildTree(items).map(toNode);
  }, [items, coQuyen, form, remove]);

  return (
    <Card
      title="Danh mục nội dung"
      extra={
        coQuyen && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setCreatingUnder(null);
              form.resetFields();
            }}
          >
            Thêm danh mục gốc
          </Button>
        )
      }
    >
      <Alert
        style={{ marginBottom: 16 }}
        type="info"
        showIcon
        message="Kéo thả để đổi cấp và thứ tự"
        description="Thứ tự ở đây quyết định thứ tự hiển thị trên cổng. Không kéo được một danh mục vào chính nhánh con của nó."
      />

      {categories.isLoading ? (
        <Typography.Text type="secondary">Đang tải…</Typography.Text>
      ) : (
        <Tree
          blockNode
          defaultExpandAll
          draggable={coQuyen}
          treeData={treeData}
          allowDrop={({ dragNode, dropNode, dropPosition }) =>
            // dropPosition 0 = thả vào trong nút; khác 0 = thả cạnh nút (đổi thứ tự).
            dropPosition !== 0 || canDropInto(items, String(dragNode.key), String(dropNode.key))
          }
          onDrop={({ dragNode, node, dropToGap }) => {
            const dragId = String(dragNode.key);
            const newParentId = dropToGap
              ? (items.find((c) => c.publicId === String(node.key))?.parentPublicId ?? null)
              : String(node.key);
            if (!canDropInto(items, dragId, newParentId)) {
              message.warning('Không đưa một danh mục vào chính nhánh con của nó được');
              return;
            }
            move.mutate({ publicId: dragId, newParentId });
          }}
        />
      )}

      <Modal
        open={editing !== null || creatingUnder !== undefined}
        title={
          editing
            ? `Sửa danh mục "${editing.name}"`
            : creatingUnder
              ? `Thêm danh mục con của "${creatingUnder.name}"`
              : 'Thêm danh mục gốc'
        }
        okText="Lưu"
        cancelText="Huỷ"
        confirmLoading={create.isPending || rename.isPending}
        onCancel={() => {
          setEditing(null);
          setCreatingUnder(undefined);
          form.resetFields();
        }}
        onOk={async () => {
          const values = await form.validateFields();
          if (editing) {
            rename.mutate({ publicId: editing.publicId, ...values });
          } else {
            create.mutate({ ...values, parentId: creatingUnder?.publicId ?? null });
          }
        }}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="Tên danh mục"
            rules={[{ required: true, message: 'Nhập tên' }]}
          >
            <Input autoFocus />
          </Form.Item>
          <Form.Item name="slug" label="Đường dẫn" extra="Bỏ trống để hệ thống tự sinh từ tên">
            <Input addonBefore="/danh-muc/" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
