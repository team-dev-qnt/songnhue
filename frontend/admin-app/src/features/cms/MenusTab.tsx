import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Switch,
  Tag,
  Tree,
  Typography,
} from 'antd';
import { type DataNode } from 'antd/es/tree';
import { useMemo, useState } from 'react';

import { ApiClientError } from '@/shared/apiClient';

import { cmsApi, cmsKeys } from './api';
import { buildTree, canDropInto } from './tree';
import { type MenuLinkType, type MenuNode, type MenuPosition } from './types';

/**
 * Menu cổng — T20.8, CN-01.5.
 *
 * <h3>Mục con phải cùng vị trí với mục cha, và luật đó thi hành ở CSDL</h3>
 *
 * Khoá ngoại ghép `(parent_id, position)` chặn việc một mục HEADER có cha nằm ở FOOTER. Giao
 * diện không cần kiểm lại — nhưng nó **phải không tạo ra được** thao tác đó, nên hai vị trí
 * hiển thị thành hai cây tách biệt chứ không phải một cây có cột "vị trí".
 */
export function MenusTab() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const [position, setPosition] = useState<MenuPosition>('HEADER');
  const [editing, setEditing] = useState<MenuNode | null>(null);
  const [creatingUnder, setCreatingUnder] = useState<MenuNode | null | undefined>(undefined);

  const menu = useQuery({
    queryKey: cmsKeys.menu(position),
    queryFn: () => cmsApi.menu(position),
  });
  const categories = useQuery({
    queryKey: cmsKeys.categories(),
    queryFn: () => cmsApi.categories(),
  });

  const items = useMemo(() => menu.data ?? [], [menu.data]);
  const invalidate = () => queryClient.invalidateQueries({ queryKey: cmsKeys.menu(position) });
  const baoLoi = (caught: unknown, fallback: string) =>
    message.error(caught instanceof ApiClientError ? caught.message : fallback);

  const save = useMutation({
    mutationFn: (body: Parameters<typeof cmsApi.createMenuItem>[1]) =>
      editing
        ? cmsApi.updateMenuItem(editing.publicId, body)
        : cmsApi.createMenuItem(position, body),
    onSuccess: async () => {
      message.success('Đã lưu mục menu');
      setEditing(null);
      setCreatingUnder(undefined);
      form.resetFields();
      await invalidate();
    },
    onError: (caught) => baoLoi(caught, 'Không lưu được mục menu'),
  });

  const remove = useMutation({
    mutationFn: (publicId: string) => cmsApi.deleteMenuItem(publicId),
    onSuccess: async () => {
      message.success('Đã xoá mục menu');
      await invalidate();
    },
    onError: (caught) => baoLoi(caught, 'Không xoá được mục menu'),
  });

  const reorder = useMutation({
    mutationFn: (publicIds: string[]) => cmsApi.reorderMenu(publicIds),
    onSuccess: invalidate,
    onError: (caught) => baoLoi(caught, 'Không đổi được thứ tự'),
  });

  const treeData: DataNode[] = useMemo(() => {
    const toNode = (item: ReturnType<typeof buildTree<MenuNode>>[number]): DataNode => ({
      key: item.value.publicId,
      title: (
        <Space>
          <span>{item.value.label}</span>
          <Tag>{MO_TA_LOAI[item.value.linkType]}</Tag>
          {!item.value.active && <Tag color="default">Đang tắt</Tag>}
          <Button
            type="link"
            size="small"
            onClick={(event) => {
              event.stopPropagation();
              setEditing(item.value);
              form.setFieldsValue({
                label: item.value.label,
                linkType: item.value.linkType,
                categoryId: item.value.categoryPublicId,
                url: item.value.url,
                openNewTab: item.value.openNewTab,
                active: item.value.active,
              });
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
            title="Xoá mục menu?"
            okText="Xoá"
            cancelText="Huỷ"
            onConfirm={() => remove.mutate(item.value.publicId)}
          >
            <Button type="link" size="small" danger onClick={(event) => event.stopPropagation()}>
              Xoá
            </Button>
          </Popconfirm>
        </Space>
      ),
      children: item.children.map(toNode),
    });
    return buildTree(items).map(toNode);
  }, [items, form, remove]);

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space wrap>
        <Segmented<MenuPosition>
          value={position}
          onChange={setPosition}
          options={[
            { label: 'Menu đầu trang', value: 'HEADER' },
            { label: 'Menu chân trang', value: 'FOOTER' },
          ]}
        />
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setCreatingUnder(null);
            form.resetFields();
          }}
        >
          Thêm mục gốc
        </Button>
      </Space>

      <Alert
        type="info"
        showIcon
        message="Menu đầu trang và chân trang là hai cây riêng"
        description="Một mục con luôn thuộc cùng vị trí với mục cha — ràng buộc này được cơ sở dữ liệu bảo đảm, không phải bằng nhắc nhở."
      />

      {menu.isLoading ? (
        <Typography.Text type="secondary">Đang tải…</Typography.Text>
      ) : (
        <Tree
          blockNode
          defaultExpandAll
          draggable
          treeData={treeData}
          allowDrop={({ dragNode, dropNode, dropPosition }) =>
            dropPosition !== 0 || canDropInto(items, String(dragNode.key), String(dropNode.key))
          }
          onDrop={({ dragNode, node, dropToGap }) => {
            const dragId = String(dragNode.key);
            if (!dropToGap && !canDropInto(items, dragId, String(node.key))) {
              message.warning('Không đưa một mục vào chính nhánh con của nó được');
              return;
            }
            // Backend nhận danh sách id theo thứ tự mới của cùng một cấp.
            const thuTuMoi = items.map((item) => item.publicId).filter((id) => id !== dragId);
            const viTri = thuTuMoi.indexOf(String(node.key));
            thuTuMoi.splice(viTri < 0 ? thuTuMoi.length : viTri + 1, 0, dragId);
            reorder.mutate(thuTuMoi);
          }}
        />
      )}

      <Modal
        open={editing !== null || creatingUnder !== undefined}
        title={editing ? `Sửa mục "${editing.label}"` : 'Thêm mục menu'}
        okText="Lưu"
        cancelText="Huỷ"
        confirmLoading={save.isPending}
        onCancel={() => {
          setEditing(null);
          setCreatingUnder(undefined);
          form.resetFields();
        }}
        onOk={async () => {
          const values = await form.validateFields();
          save.mutate({
            label: values.label,
            linkType: values.linkType as MenuLinkType,
            parentId: editing ? editing.parentPublicId : (creatingUnder?.publicId ?? null),
            categoryId: values.categoryId ?? null,
            articleId: null,
            url: values.url ?? null,
            openNewTab: Boolean(values.openNewTab),
            active: values.active !== false,
          });
        }}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" initialValues={{ linkType: 'CATEGORY', active: true }}>
          <Form.Item
            name="label"
            label="Nhãn hiển thị"
            rules={[{ required: true, message: 'Nhập nhãn' }]}
          >
            <Input autoFocus />
          </Form.Item>
          <Form.Item name="linkType" label="Loại liên kết">
            <Select
              options={(Object.keys(MO_TA_LOAI) as MenuLinkType[]).map((value) => ({
                value,
                label: MO_TA_LOAI[value],
              }))}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.linkType !== next.linkType}>
            {({ getFieldValue }) => {
              const loai = getFieldValue('linkType') as MenuLinkType;
              if (loai === 'CATEGORY') {
                return (
                  <Form.Item
                    name="categoryId"
                    label="Danh mục"
                    rules={[{ required: true, message: 'Chọn danh mục' }]}
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      loading={categories.isLoading}
                      options={(categories.data ?? []).map((c) => ({
                        value: c.publicId,
                        label: `${'  '.repeat(c.depth)}${c.name}`,
                      }))}
                    />
                  </Form.Item>
                );
              }
              if (loai === 'URL' || loai === 'EXTERNAL_DOC') {
                return (
                  <Form.Item
                    name="url"
                    label="Đường dẫn"
                    rules={[{ required: true, message: 'Nhập đường dẫn' }]}
                  >
                    <Input placeholder="https://… hoặc /bai-viet/…" />
                  </Form.Item>
                );
              }
              return (
                <Alert
                  type="info"
                  showIcon
                  message="Mục này chỉ để mở menu con, bấm vào không đi đâu cả"
                  style={{ marginBottom: 16 }}
                />
              );
            }}
          </Form.Item>
          <Form.Item name="openNewTab" label="Mở tab mới" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="active" label="Đang bật" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

const MO_TA_LOAI: Record<MenuLinkType, string> = {
  CATEGORY: 'Danh mục',
  ARTICLE: 'Bài viết',
  URL: 'Đường dẫn tự nhập',
  EXTERNAL_DOC: 'Hệ thống văn bản điều hành',
  NONE: 'Chỉ mở menu con',
};
