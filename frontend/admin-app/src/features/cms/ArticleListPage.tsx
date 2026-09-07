import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Input, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { StatusBadge } from '@/components/business/StatusBadge';
import { useAuth } from '@/app/auth/useAuth';
import { ApiClientError } from '@/shared/apiClient';
import { formatDateTime, formatInteger } from '@/shared/format';

import { ARTICLE_STATUS, ARTICLE_STATUS_ORDER } from './articleStatus';
import { cmsApi, cmsKeys, type ArticleFilter } from './api';
import { type ArticleSummary } from './types';

/**
 * Danh sách bài viết — T20.2, CN-01.1.
 *
 * <h3>Phân trang và lọc chạy ở máy chủ</h3>
 *
 * Kéo hết bài về rồi lọc trong trình duyệt thì chạy tốt đúng tới lúc Công ty có vài nghìn
 * bài — và đó là lúc không ai còn nhớ vì sao trang chậm. `PageUtils` của backend đã chốt sẵn
 * danh sách cột được sắp xếp, nên FE chỉ việc gửi lên.
 */
export function ArticleListPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();

  const [filter, setFilter] = useState<ArticleFilter>({
    page: 0,
    size: 20,
    sort: 'createdAt,desc',
  });
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<React.Key[]>([]);

  const categories = useQuery({
    queryKey: cmsKeys.categories(),
    queryFn: () => cmsApi.categories(),
  });

  const articles = useQuery({
    queryKey: cmsKeys.articles(filter),
    queryFn: () => cmsApi.searchArticles(filter),
    // Giữ trang cũ trong lúc tải trang mới: bảng nhấp nháy về rỗng rồi đầy lại làm mắt mất
    // dấu dòng đang đọc, và người dùng tưởng dữ liệu vừa biến mất.
    placeholderData: keepPreviousData,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['cms', 'articles'] });

  const remove = useMutation({
    mutationFn: (publicId: string) => cmsApi.deleteArticle(publicId),
    onSuccess: async () => {
      message.success('Đã xoá bài viết');
      setSelected([]);
      await invalidate();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được bài viết'),
  });

  /**
   * Thao tác hàng loạt.
   *
   * ⚠ Chạy **tuần tự** chứ không `Promise.all`. Mỗi lượt xoá là một giao dịch riêng ở
   * backend; bắn song song hai chục lượt vào một hệ 200 người dùng chỉ để tiết kiệm vài
   * giây của một người là đánh đổi sai. Quan trọng hơn: hỏng giữa chừng thì ở đây biết
   * chính xác đã xong tới đâu.
   */
  const xoaHangLoat = async () => {
    let xong = 0;
    for (const key of selected) {
      try {
        await cmsApi.deleteArticle(String(key));
        xong += 1;
      } catch (caught) {
        message.error(
          `Dừng ở bài thứ ${xong + 1}: ${caught instanceof ApiClientError ? caught.message : 'lỗi không xác định'}`,
        );
        break;
      }
    }
    if (xong > 0) {
      message.success(`Đã xoá ${xong}/${selected.length} bài`);
    }
    setSelected([]);
    await invalidate();
  };

  const columns: ColumnsType<ArticleSummary> = [
    {
      title: 'Tiêu đề',
      dataIndex: 'title',
      sorter: true,
      render: (title: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Link onClick={() => navigate(`/noi-dung/bai-viet/${row.publicId}`)}>
            {title}
          </Typography.Link>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            /{row.slug}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Danh mục',
      dataIndex: 'categoryNames',
      width: 220,
      render: (names: string[]) => (
        <Space size={[0, 4]} wrap>
          {names.map((name) => (
            <Tag key={name}>{name}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (value: string) => <StatusBadge value={value} vocabulary={ARTICLE_STATUS} />,
    },
    {
      title: 'Ngày đăng',
      dataIndex: 'publishedAt',
      width: 170,
      sorter: true,
      render: (value: string | null) => formatDateTime(value),
    },
    {
      title: 'Lượt xem',
      dataIndex: 'viewCount',
      width: 110,
      align: 'right',
      render: (value: number) => formatInteger(value),
    },
    {
      title: '',
      key: 'thao-tac',
      width: 120,
      render: (_value, row) =>
        hasPermission('cms:article:delete') && (
          <Popconfirm
            title="Xoá bài viết?"
            description="Bài bị ẩn khỏi mọi danh sách. Dữ liệu vẫn còn trong cơ sở dữ liệu."
            okText="Xoá"
            cancelText="Huỷ"
            onConfirm={() => remove.mutate(row.publicId)}
          >
            <Button type="link" danger>
              Xoá
            </Button>
          </Popconfirm>
        ),
    },
  ];

  return (
    <Card
      title="Bài viết"
      extra={
        hasPermission('cms:article:create') && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/noi-dung/bai-viet/moi')}
          >
            Viết bài mới
          </Button>
        )
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          allowClear
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          onPressEnter={() => setFilter((prev) => ({ ...prev, q: keyword || undefined, page: 0 }))}
          prefix={<SearchOutlined />}
          placeholder="Tìm theo tiêu đề hoặc nội dung"
          style={{ width: 280 }}
        />
        <Select
          allowClear
          placeholder="Mọi trạng thái"
          style={{ width: 170 }}
          value={filter.status}
          onChange={(status) => setFilter((prev) => ({ ...prev, status, page: 0 }))}
          options={ARTICLE_STATUS_ORDER.map((value) => ({
            value,
            label: ARTICLE_STATUS[value]?.label ?? value,
          }))}
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="Mọi danh mục"
          style={{ width: 220 }}
          value={filter.categoryId}
          loading={categories.isLoading}
          onChange={(categoryId) => setFilter((prev) => ({ ...prev, categoryId, page: 0 }))}
          options={(categories.data ?? []).map((c) => ({
            value: c.publicId,
            // Thụt lề theo cấp để cây danh mục đọc được trong một ô chọn phẳng.
            label: `${'  '.repeat(c.depth)}${c.name}`,
          }))}
        />
        {selected.length > 0 && hasPermission('cms:article:delete') && (
          <Popconfirm
            title={`Xoá ${selected.length} bài đã chọn?`}
            okText="Xoá"
            cancelText="Huỷ"
            onConfirm={() => void xoaHangLoat()}
          >
            <Button danger>Xoá {selected.length} bài</Button>
          </Popconfirm>
        )}
      </Space>

      <Table<ArticleSummary>
        rowKey="publicId"
        columns={columns}
        dataSource={articles.data?.items ?? []}
        loading={articles.isLoading}
        rowSelection={
          hasPermission('cms:article:delete')
            ? { selectedRowKeys: selected, onChange: setSelected }
            : undefined
        }
        locale={{ emptyText: 'Chưa có bài viết nào khớp bộ lọc' }}
        // Bề ngang tối thiểu: hẹp hơn thì CUỘN NGANG, không bóp chữ.
        // Vì sao cần — xem chú thích cột "Địa chỉ" ở `features/hydro/ApiSourcesPage.tsx`.
        scroll={{ x: 1200 }}
        pagination={{
          // ⚠ `meta.page` đếm từ 1, tham số `page` gửi lên đếm từ 0 — quy đổi ở đúng chỗ này.
          current: articles.data?.meta.page ?? 1,
          pageSize: articles.data?.meta.size ?? 20,
          total: articles.data?.meta.totalElements ?? 0,
          showSizeChanger: true,
          pageSizeOptions: ['20', '50', '100'],
          showTotal: (total) => `${formatInteger(total)} bài`,
        }}
        onChange={(pagination, _filters, sorter) => {
          const single = Array.isArray(sorter) ? sorter[0] : sorter;
          const field = single?.field ? String(single.field) : undefined;
          setFilter((prev) => ({
            ...prev,
            page: (pagination.current ?? 1) - 1,
            size: pagination.pageSize ?? 20,
            sort: field ? `${field},${single?.order === 'ascend' ? 'asc' : 'desc'}` : prev.sort,
          }));
        }}
      />
    </Card>
  );
}
