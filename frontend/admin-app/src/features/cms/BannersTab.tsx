import { DownOutlined, PlusOutlined, UpOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Alert,
  Button,
  DatePicker,
  Form,
  Image,
  Input,
  List,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Tag,
  Typography,
  Upload,
} from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { ApiClientError } from '@/shared/apiClient';
import { formatDateTime, toApiInstant } from '@/shared/format';

import { cmsApi, cmsKeys } from './api';
import { type BannerView } from './types';

/**
 * ⚠ Một câu duy nhất, dùng cho mọi nút bị khoá — một nút xám ⛔ không giải thích được là thứ
 * người dùng báo lại thành *"hệ thống lỗi"*, và người tiếp nhận ⛔ không dựng lại được.
 */
const LY_DO_THIEU_QUYEN = 'Bạn không có quyền cms:banner:manage để sửa banner';

/**
 * Banner trang chủ — T20.8, CN-01.4.
 *
 * <h3>Đổi thứ tự bằng nút lên/xuống, không bằng kéo thả</h3>
 *
 * Danh sách banner ngắn (thường 3–6 tấm) và mỗi dòng có một ảnh cao. Kéo thả trên danh sách
 * dòng cao thì phải cuộn trong lúc giữ chuột — thao tác khó trên chuột rời và gần như không
 * làm được trên máy tính bảng. Hai nút mũi tên vừa rõ ràng vừa dùng được bằng bàn phím.
 *
 * <h3>`visibleNow` do backend tính</h3>
 *
 * Banner có `active` cộng khoảng lịch chiếu. Ghép ba thứ đó ở FE thì màn hình quản trị và
 * cổng công khai sẽ có ngày trả lời khác nhau về việc "banner này có đang hiện không" — nhất
 * là quanh mốc nửa đêm, khi múi giờ vào cuộc.
 */
export function BannersTab() {
  const { hasPermission } = useAuth();

  /**
   * ⭐ **T27.28 — lệch tầng 1 ↔ tầng 3**, vá 04/09/2026.
   *
   * Tuyến `/noi-dung/giao-dien` gác bằng `cms:layout:manage`, còn **cả bảy** endpoint ghi của
   * `BannerController` đòi `cms:banner:manage`. Tab này có **0** lời gọi `hasPermission`, nên
   * ⛔ không có gì đứng giữa hai mã quyền ấy.
   *
   * ⚠ **Hôm nay vô hại, và đó chính là lý do phải vá bây giờ**: đo trên ma trận seed 04/09, cả
   * hai mã thuộc **đúng một vai trò** (CONTENT_MANAGER), nên ⛔ chưa ai gặp. Ngày Công ty tách
   * chúng — việc mà CN-05.2 sinh ra để làm — người vào được trang Giao diện sẽ thấy đủ nút Thêm ·
   * Sửa · Xoá · đổi thứ tự, bấm cái nào cũng **403**, và ⛔ không màn hình nào giải thích được.
   *
   * ⛔ Đây là loại nợ ⛔ không có triệu chứng cho tới đúng ngày nó đắt nhất.
   */
  const coQuyenGhi = hasPermission('cms:banner:manage');
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const [editing, setEditing] = useState<BannerView | null>(null);

  const banners = useQuery({ queryKey: cmsKeys.banners(), queryFn: () => cmsApi.banners() });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: cmsKeys.banners() });
  const baoLoi = (caught: unknown, fallback: string) =>
    message.error(caught instanceof ApiClientError ? caught.message : fallback);

  const create = useMutation({
    mutationFn: ({ title, file }: { title: string; file: File }) =>
      cmsApi.createBanner(title, file),
    onSuccess: async () => {
      message.success('Đã thêm banner');
      await invalidate();
    },
    onError: (caught) => baoLoi(caught, 'Không thêm được banner'),
  });

  const update = useMutation({
    mutationFn: ({
      publicId,
      body,
    }: {
      publicId: string;
      body: Parameters<typeof cmsApi.updateBanner>[1];
    }) => cmsApi.updateBanner(publicId, body),
    onSuccess: async () => {
      message.success('Đã cập nhật banner');
      setEditing(null);
      await invalidate();
    },
    onError: (caught) => baoLoi(caught, 'Không cập nhật được banner'),
  });

  const reorder = useMutation({
    mutationFn: (publicIds: string[]) => cmsApi.reorderBanners(publicIds),
    onSuccess: invalidate,
    onError: (caught) => baoLoi(caught, 'Không đổi được thứ tự'),
  });

  const remove = useMutation({
    mutationFn: (publicId: string) => cmsApi.deleteBanner(publicId),
    onSuccess: async () => {
      message.success('Đã xoá banner');
      await invalidate();
    },
    onError: (caught) => baoLoi(caught, 'Không xoá được banner'),
  });

  const list = banners.data ?? [];

  const doiCho = (index: number, huong: -1 | 1) => {
    const next = [...list];
    const target = index + huong;
    if (target < 0 || target >= next.length) {
      return;
    }
    [next[index], next[target]] = [next[target], next[index]];
    reorder.mutate(next.map((b) => b.publicId));
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space>
        <Upload
          showUploadList={false}
          accept="image/png,image/jpeg,image/webp"
          beforeUpload={(file) => {
            create.mutate({ title: file.name.replace(/\.[^.]+$/, ''), file });
            return false;
          }}
        >
          <Button
            type="primary"
            icon={<PlusOutlined />}
            loading={create.isPending}
            disabled={!coQuyenGhi}
            title={coQuyenGhi ? undefined : LY_DO_THIEU_QUYEN}
          >
            Thêm banner
          </Button>
        </Upload>
        <Typography.Text type="secondary">
          Chọn ảnh trước, đặt tiêu đề và liên kết sau
        </Typography.Text>
      </Space>

      <List<BannerView>
        loading={banners.isLoading}
        dataSource={list}
        locale={{ emptyText: 'Chưa có banner nào — trang chủ sẽ không hiện khối ảnh lớn' }}
        renderItem={(banner, index) => (
          <List.Item
            actions={[
              <Button
                key="len"
                type="text"
                icon={<UpOutlined />}
                disabled={index === 0 || reorder.isPending || !coQuyenGhi}
                onClick={() => doiCho(index, -1)}
                aria-label="Đưa lên trên"
              />,
              <Button
                key="xuong"
                type="text"
                icon={<DownOutlined />}
                disabled={index === list.length - 1 || reorder.isPending || !coQuyenGhi}
                onClick={() => doiCho(index, 1)}
                aria-label="Đưa xuống dưới"
              />,
              <Button
                key="sua"
                type="link"
                disabled={!coQuyenGhi}
                title={coQuyenGhi ? undefined : LY_DO_THIEU_QUYEN}
                onClick={() => {
                  setEditing(banner);
                  form.setFieldsValue({
                    title: banner.title,
                    description: banner.description ?? '',
                    linkUrl: banner.linkUrl ?? '',
                    openNewTab: banner.openNewTab,
                    active: banner.active,
                    khoang:
                      banner.startAt || banner.endAt
                        ? [
                            banner.startAt ? dayjs(banner.startAt) : null,
                            banner.endAt ? dayjs(banner.endAt) : null,
                          ]
                        : null,
                  });
                }}
              >
                Sửa
              </Button>,
              <Popconfirm
                key="xoa"
                title="Xoá banner?"
                okText="Xoá"
                cancelText="Huỷ"
                disabled={!coQuyenGhi}
                onConfirm={() => remove.mutate(banner.publicId)}
              >
                <Button
                  type="link"
                  danger
                  disabled={!coQuyenGhi}
                  title={coQuyenGhi ? undefined : LY_DO_THIEU_QUYEN}
                >
                  Xoá
                </Button>
              </Popconfirm>,
            ]}
          >
            <List.Item.Meta
              avatar={
                <Image
                  src={`/api/v1/public/files/${banner.imageAttachmentPublicId}`}
                  alt={banner.title}
                  width={160}
                  height={70}
                  style={{ objectFit: 'cover' }}
                />
              }
              title={
                <Space>
                  {banner.title}
                  {banner.visibleNow ? (
                    <Tag color="green">Đang hiện</Tag>
                  ) : (
                    <Tag color="default">{banner.active ? 'Ngoài khoảng lịch' : 'Đang tắt'}</Tag>
                  )}
                </Space>
              }
              description={
                <Space direction="vertical" size={0}>
                  {banner.description && <span>{banner.description}</span>}
                  {banner.linkUrl && (
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      → {banner.linkUrl}
                      {banner.openNewTab && ' (tab mới)'}
                    </Typography.Text>
                  )}
                  {(banner.startAt || banner.endAt) && (
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {formatDateTime(banner.startAt) || '—'} →{' '}
                      {formatDateTime(banner.endAt) || '—'}
                    </Typography.Text>
                  )}
                </Space>
              }
            />
          </List.Item>
        )}
      />

      <Modal
        open={editing !== null}
        title={`Sửa banner "${editing?.title ?? ''}"`}
        okText="Lưu"
        cancelText="Huỷ"
        confirmLoading={update.isPending}
        onCancel={() => setEditing(null)}
        onOk={async () => {
          const values = await form.validateFields();
          const khoang = values.khoang as [dayjs.Dayjs | null, dayjs.Dayjs | null] | null;
          update.mutate({
            publicId: editing?.publicId as string,
            body: {
              title: values.title,
              description: values.description || undefined,
              linkUrl: values.linkUrl || undefined,
              openNewTab: Boolean(values.openNewTab),
              active: Boolean(values.active),
              startAt: toApiInstant(khoang?.[0]) ?? null,
              endAt: toApiInstant(khoang?.[1]) ?? null,
            },
          });
        }}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="Để trống khoảng lịch = hiện liên tục khi đang bật"
        />
        <Form form={form} layout="vertical">
          <Form.Item
            name="title"
            label="Tiêu đề"
            rules={[{ required: true, message: 'Nhập tiêu đề' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Mô tả ngắn">
            <Input.TextArea autoSize={{ minRows: 2, maxRows: 3 }} />
          </Form.Item>
          <Form.Item name="linkUrl" label="Liên kết khi bấm vào">
            <Input placeholder="/bai-viet/… hoặc https://…" />
          </Form.Item>
          <Form.Item name="openNewTab" label="Mở tab mới" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="active" label="Đang bật" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="khoang" label="Khoảng lịch chiếu">
            <DatePicker.RangePicker showTime format="DD/MM/YYYY HH:mm" style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
