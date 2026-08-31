import { ArrowLeftOutlined, HistoryOutlined, PictureOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Image,
  Input,
  Row,
  Select,
  Space,
  Typography,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { ApprovalActions } from '@/components/business/ApprovalActions';
import { RichTextEditor } from '@/components/business/RichTextEditor';
import { StatusBadge } from '@/components/business/StatusBadge';
import { ApiClientError } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';
import { formatInteger, toApiInstant } from '@/shared/format';

import { ARTICLE_STATUS, visibilityHint } from './articleStatus';
import { cmsApi, cmsKeys } from './api';
import { useMediaPicker } from './MediaPickerModal';
import { SeoInput } from './SeoField';
import { suggestSlug } from './seo';
import { type ArticleDetail, type ArticleSaveRequest } from './types';
import { VersionHistoryDrawer } from './VersionHistoryDrawer';

/**
 * Soạn và duyệt một bài viết — T20.3, T20.4.
 *
 * <h3>Nút duyệt render từ API, không suy từ trạng thái</h3>
 *
 * `allowedActions` do backend trả về đã lọc theo `workflow_transitions` **và** theo quyền
 * của người đang đăng nhập. Màn hình này cố ý **không biết** quy trình duyệt gồm những bước
 * nào — khách thêm một bước là bảng dữ liệu đổi, giao diện đi theo, không ai phải sửa mã.
 *
 * <h3>Slug: gợi ý khi tạo, đứng yên khi bài đã lên cổng</h3>
 *
 * Bài đã xuất bản có địa chỉ công khai đang được chia sẻ và được công cụ tìm kiếm ghi nhận.
 * Sửa một lỗi chính tả trong tiêu đề mà slug đổi theo là **đổi địa chỉ của một trang đang
 * sống** — mọi liên kết cũ chết. Backend đã chốt luật này (`slugKhiSua`); ở đây chỉ ngừng
 * gợi ý để giao diện không nói ngược lại.
 *
 * <h3>Hai lớp: vỏ ngoài nạp dữ liệu, lớp trong dựng biểu mẫu</h3>
 *
 * ⚠⚠ Tách hai lớp là **bắt buộc**, không phải cho gọn. Bản đầu dùng một component và một
 * `useEffect` để đổ dữ liệu vào biểu mẫu khi API trả về — ESLint chặn đúng chỗ đó
 * (`react-hooks/set-state-in-effect`), và lý do sâu hơn cái tên của luật: màn hình vẽ một
 * lượt với biểu mẫu trống rồi vẽ lại với dữ liệu. Người dùng thấy các ô nhấp nháy, và nếu
 * họ kịp gõ vào khoảng giữa thì cú gõ đó bị effect ghi đè mất.
 *
 * Dựng biểu mẫu **sau khi đã có dữ liệu**, truyền qua `initialValues`, thì không có khoảng
 * giữa nào để mất gì cả. `key` bảo đảm chuyển sang bài khác là dựng lại từ đầu.
 */
export function ArticleEditorPage() {
  const { publicId } = useParams<{ publicId: string }>();
  const laBaiMoi = publicId === undefined || publicId === 'moi';

  const article = useQuery({
    queryKey: cmsKeys.article(publicId ?? ''),
    queryFn: () => cmsApi.getArticle(publicId as string),
    enabled: !laBaiMoi,
  });

  if (laBaiMoi) {
    return <ArticleForm key="moi" />;
  }
  if (article.isLoading || !article.data) {
    return <Card loading title="Đang tải bài viết…" />;
  }
  return <ArticleForm key={article.data.publicId} article={article.data} />;
}

function ArticleForm({ article: data }: { article?: ArticleDetail }) {
  const laBaiMoi = data === undefined;
  const publicId = data?.publicId;
  const navigate = useNavigate();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { chonAnh, picker } = useMediaPicker();

  const [form] = Form.useForm<FormValues>();
  const [content, setContent] = useState(data?.content ?? '');
  const [coverId, setCoverId] = useState<string | null>(data?.coverAttachmentPublicId ?? null);
  const [historyOpen, setHistoryOpen] = useState(false);
  /** Bài đã có slug thì thôi gợi ý; bài mới thì gợi ý cho tới khi người dùng tự sửa. */
  const [slugDaSuaTay, setSlugDaSuaTay] = useState(!laBaiMoi);
  /** Ảnh kéo-thả đang tải dở — xem `khoaLuu` bên dưới. */
  const [anhDangTai, setAnhDangTai] = useState(0);

  const categories = useQuery({
    queryKey: cmsKeys.categories(),
    queryFn: () => cmsApi.categories(),
  });

  const folders = useQuery({
    queryKey: cmsKeys.folders(),
    queryFn: () => cmsApi.folders(),
  });

  /**
   * Ảnh kéo thẳng vào bài đi vào thư mục media nào.
   *
   * Lấy thư mục gốc đầu tiên — cùng quy tắc mà `MediaBrowser` đã dùng để chọn thư mục mặc
   * định, nên hai màn hình không nói ngược nhau. Chưa có thư mục nào thì `onUploadImage`
   * **không được truyền xuống**, và trình soạn thảo nói thẳng là chưa bật đường tải ảnh thay
   * vì nuốt tệp im lặng.
   */
  const thuMucAnh = folders.data?.[0]?.publicId ?? null;

  const initialValues: FormValues = {
    title: data?.title ?? '',
    slug: data?.slug ?? '',
    summary: data?.summary ?? '',
    source: data?.source ?? '',
    publishedAt: data?.publishedAt ? dayjs(data.publishedAt) : null,
    metaTitle: data?.metaTitle ?? '',
    metaDescription: data?.metaDescription ?? '',
    metaKeywords: data?.metaKeywords ?? '',
    categoryPublicIds: data?.categoryPublicIds ?? [],
  };

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['cms', 'articles'] });
    if (publicId) {
      await queryClient.invalidateQueries({ queryKey: cmsKeys.article(publicId) });
    }
  };

  const save = useMutation({
    mutationFn: (body: ArticleSaveRequest) =>
      laBaiMoi ? cmsApi.createArticle(body) : cmsApi.updateArticle(publicId as string, body),
    onSuccess: async (saved) => {
      message.success('Đã lưu bài viết');
      await invalidate();
      if (laBaiMoi) {
        navigate(`/noi-dung/bai-viet/${saved.publicId}`, { replace: true });
      }
    },
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError) {
        // Lỗi theo trường đưa thẳng vào ô tương ứng — thông báo nổi ở góc màn hình thì người
        // dùng phải tự đi tìm ô nào sai trong một biểu mẫu mười hai trường.
        if (datLoiTheoTruong(form, caught)) return;
        message.error(caught.message);
        return;
      }
      message.error('Không lưu được bài viết');
    },
  });

  const transition = useMutation({
    mutationFn: ({ action, reason }: { action: string; reason?: string }) =>
      cmsApi.transition(publicId as string, action, reason),
    onSuccess: invalidate,
    onError: (caught: unknown) =>
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không đổi được trạng thái',
      ),
  });

  const submit = async () => {
    const values = await form.validateFields();
    save.mutate({
      title: values.title,
      slug: values.slug || undefined,
      summary: values.summary || undefined,
      content,
      coverAttachmentPublicId: coverId,
      source: values.source || undefined,
      publishedAt: toApiInstant(values.publishedAt) ?? null,
      metaTitle: values.metaTitle || undefined,
      metaDescription: values.metaDescription || undefined,
      metaKeywords: values.metaKeywords || undefined,
      categoryPublicIds: values.categoryPublicIds,
    });
  };

  // Bài đang chờ duyệt bị khoá chỉnh sửa (CN-01.1). Backend là chốt chặn; khoá ở đây chỉ để
  // người dùng không gõ xong cả bài rồi mới bị từ chối.
  const khoaSua = data?.status === 'CHO_DUYET';

  /**
   * ⚠ Khoá Lưu khi còn ảnh đang tải, và đây **không** phải chuyện tiện dụng.
   *
   * Ảnh chưa tải xong thì `src` của nó là `blob:` — địa chỉ chỉ sống trong tab đang mở. Lưu
   * lúc đó thì `HtmlSanitizer` gỡ thuộc tính `src` (giao thức không nằm trong danh sách cho
   * phép) và bài viết còn lại một thẻ ảnh rỗng: người soạn thấy "Đã lưu", mở lại thì đúng
   * tấm ảnh vừa kéo vào đã biến mất, không lỗi nào.
   */
  const khoaLuu = khoaSua || anhDangTai > 0;

  return (
    <>
      <Card
        title={
          <Space>
            <Button
              icon={<ArrowLeftOutlined />}
              type="text"
              onClick={() => navigate('/noi-dung/bai-viet')}
            />
            {laBaiMoi ? 'Viết bài mới' : 'Sửa bài viết'}
            {data && <StatusBadge value={data.status} vocabulary={ARTICLE_STATUS} />}
          </Space>
        }
        extra={
          <Space>
            {!laBaiMoi && (
              <Button icon={<HistoryOutlined />} onClick={() => setHistoryOpen(true)}>
                Lịch sử phiên bản
              </Button>
            )}
            <Button
              type="primary"
              loading={save.isPending}
              disabled={khoaLuu}
              title={anhDangTai > 0 ? `Đang tải ${anhDangTai} ảnh lên` : undefined}
              onClick={() => void submit()}
            >
              Lưu
            </Button>
          </Space>
        }
      >
        {data && (
          <Alert
            style={{ marginBottom: 16 }}
            type={data.publiclyVisible ? 'success' : 'info'}
            showIcon
            message={visibilityHint(data.status, data.publiclyVisible)}
            description={
              <Space split="·" wrap>
                <span>{formatInteger(data.viewCount)} lượt xem</span>
                {data.reviewNote && <span>Ghi chú duyệt: {data.reviewNote}</span>}
              </Space>
            }
          />
        )}

        {khoaSua && (
          <Alert
            style={{ marginBottom: 16 }}
            type="warning"
            showIcon
            message="Bài đang chờ duyệt nên tạm khoá chỉnh sửa"
            description="Người duyệt cần đọc đúng nội dung đã được gửi. Muốn sửa thì yêu cầu trả bài về trước."
          />
        )}

        <Form<FormValues>
          form={form}
          layout="vertical"
          disabled={khoaSua}
          initialValues={initialValues}
        >
          <Row gutter={24}>
            <Col xs={24} lg={16}>
              <Form.Item
                name="title"
                label="Tiêu đề"
                rules={[{ required: true, message: 'Nhập tiêu đề bài viết' }]}
              >
                <Input
                  placeholder="Tiêu đề hiển thị trên cổng"
                  onChange={(event) => {
                    if (!slugDaSuaTay) {
                      form.setFieldValue('slug', suggestSlug(event.target.value));
                    }
                  }}
                />
              </Form.Item>

              <Form.Item
                name="slug"
                label="Đường dẫn"
                extra={
                  data?.publiclyVisible
                    ? '⚠ Bài đang hiển thị công khai — đổi đường dẫn làm mọi liên kết đã chia sẻ bị hỏng'
                    : 'Tự sinh từ tiêu đề; sửa được, hệ thống tự thêm hậu tố nếu trùng'
                }
              >
                <Input addonBefore="/bai-viet/" onChange={() => setSlugDaSuaTay(true)} />
              </Form.Item>

              <Form.Item label="Nội dung" required>
                <RichTextEditor
                  value={content}
                  onChange={setContent}
                  disabled={khoaSua}
                  onPendingUploadsChange={setAnhDangTai}
                  onPickImage={async () => {
                    const file = await chonAnh();
                    return file ? { publicId: file.publicId, alt: file.originalName } : null;
                  }}
                  onUploadImage={
                    thuMucAnh
                      ? async (file) => {
                          const uploaded = await cmsApi.uploadFile(thuMucAnh, file);
                          // Thư viện media vừa có thêm tệp — làm mới để hộp chọn ảnh nhìn
                          // thấy nó ngay, thay vì người dùng phải bấm nút tải lại.
                          await queryClient.invalidateQueries({
                            queryKey: cmsKeys.files(thuMucAnh),
                          });
                          return { publicId: uploaded.publicId };
                        }
                      : undefined
                  }
                />
              </Form.Item>

              <Form.Item name="summary" label="Tóm tắt">
                <SeoInputBridge field="summary" form={form} name="summary" textarea />
              </Form.Item>
            </Col>

            <Col xs={24} lg={8}>
              <Form.Item
                name="categoryPublicIds"
                label="Danh mục"
                rules={[{ required: true, message: 'Chọn ít nhất một danh mục' }]}
              >
                <Select
                  mode="multiple"
                  showSearch
                  optionFilterProp="label"
                  placeholder="Một bài thuộc được nhiều danh mục"
                  loading={categories.isLoading}
                  options={(categories.data ?? []).map((c) => ({
                    value: c.publicId,
                    label: `${'  '.repeat(c.depth)}${c.name}`,
                  }))}
                />
              </Form.Item>

              <Form.Item
                label="Ảnh đại diện"
                extra="Dùng cho danh sách bài và khi chia sẻ lên mạng xã hội"
              >
                <Space direction="vertical" style={{ width: '100%' }}>
                  {coverId ? (
                    <Image
                      src={`/api/v1/public/files/${coverId}`}
                      alt="Ảnh đại diện"
                      style={{ maxHeight: 160, objectFit: 'cover' }}
                    />
                  ) : (
                    <Typography.Text type="secondary">Chưa chọn ảnh</Typography.Text>
                  )}
                  <Space>
                    <Button
                      icon={<PictureOutlined />}
                      disabled={khoaSua}
                      onClick={async () => {
                        const file = await chonAnh();
                        if (file) {
                          setCoverId(file.publicId);
                        }
                      }}
                    >
                      Chọn ảnh
                    </Button>
                    {coverId && (
                      <Button
                        type="text"
                        danger
                        disabled={khoaSua}
                        onClick={() => setCoverId(null)}
                      >
                        Bỏ ảnh
                      </Button>
                    )}
                  </Space>
                </Space>
              </Form.Item>

              <Form.Item
                name="publishedAt"
                label="Ngày đăng"
                extra="Để trống = đăng ngay khi duyệt. Đặt thời điểm tương lai = hẹn giờ đăng"
              >
                <DatePicker showTime format="DD/MM/YYYY HH:mm" style={{ width: '100%' }} />
              </Form.Item>

              <Form.Item name="source" label="Nguồn tin">
                <Input placeholder="VD: Cổng TTĐT Bộ NN&PTNT" />
              </Form.Item>

              <Card
                size="small"
                title="Tối ưu tìm kiếm (SEO)"
                styles={{ body: { paddingTop: 12 } }}
              >
                <Form.Item name="metaTitle" label="Tiêu đề SEO" style={{ marginBottom: 12 }}>
                  <SeoInputBridge field="metaTitle" form={form} name="metaTitle" />
                </Form.Item>
                <Form.Item name="metaDescription" label="Mô tả SEO" style={{ marginBottom: 12 }}>
                  <SeoInputBridge
                    field="metaDescription"
                    form={form}
                    name="metaDescription"
                    textarea
                  />
                </Form.Item>
                <Form.Item name="metaKeywords" label="Từ khoá" style={{ marginBottom: 0 }}>
                  <Input placeholder="Cách nhau bằng dấu phẩy" />
                </Form.Item>
              </Card>
            </Col>
          </Row>
        </Form>

        {data && data.allowedActions.length > 0 && (
          <>
            <Typography.Title level={5} style={{ marginTop: 24 }}>
              Quy trình duyệt
            </Typography.Title>
            <ApprovalActions
              actions={data.allowedActions}
              disabled={transition.isPending}
              onAction={async (action, reason) => {
                await transition.mutateAsync({ action, reason });
              }}
            />
          </>
        )}
      </Card>

      {picker}

      {!laBaiMoi && (
        <VersionHistoryDrawer
          articleId={publicId as string}
          open={historyOpen}
          onClose={() => setHistoryOpen(false)}
          onRestored={invalidate}
        />
      )}
    </>
  );
}

interface FormValues {
  title: string;
  slug: string;
  summary: string;
  source: string;
  publishedAt: Dayjs | null;
  metaTitle: string;
  metaDescription: string;
  metaKeywords: string;
  categoryPublicIds: string[];
}

/**
 * Nối `SeoInput` vào `Form.Item` của AntD.
 *
 * `Form.Item` truyền `value`/`onChange` xuống con của nó, nhưng `SeoInput` cần đọc lại giá
 * trị để đếm — nên nhận thêm `form` và tự đọc. Bọc lại ở đây để mỗi chỗ dùng không phải lặp
 * bốn dòng nối dây.
 */
function SeoInputBridge({
  field,
  form,
  name,
  textarea,
  value,
  onChange,
}: {
  field: Parameters<typeof SeoInput>[0]['field'];
  form: ReturnType<typeof Form.useForm<FormValues>>[0];
  name: keyof FormValues & string;
  textarea?: boolean;
  value?: string;
  onChange?: (value: string) => void;
}) {
  return (
    <SeoInput
      field={field}
      textarea={textarea}
      value={value ?? (form.getFieldValue(name) as string | undefined)}
      onChange={(next) => onChange?.(next)}
    />
  );
}
