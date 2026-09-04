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
import { useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { ApprovalActions } from '@/components/business/ApprovalActions';
import { RichTextEditor } from '@/components/business/RichTextEditor';
import { StatusBadge } from '@/components/business/StatusBadge';
import { ApiClientError } from '@/shared/apiClient';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';
import { useChanRoiTrang } from '@/shared/useChanRoiTrang';
import { formatInteger, toApiInstant } from '@/shared/format';

import { ArticleDocumentsPanel } from './ArticleDocumentsPanel';
import { ARTICLE_STATUS, visibilityHint } from './articleStatus';
import { cmsApi, cmsKeys } from './api';
import { useMediaPicker } from './MediaPickerModal';
import { SeoInput } from './SeoField';
import { suggestSlug } from './seo';
import { type ArticleDetail, type ArticleDocumentView, type ArticleSaveRequest } from './types';
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
  const queryClient = useQueryClient();
  const laBaiMoi = publicId === undefined || publicId === 'moi';

  /**
   * ⭐⭐ **Mốc nạp lại tường minh** — bản vá cho lỗi mất dữ liệu ở luồng Phục hồi phiên bản (T41.9).
   *
   * <h3>Lỗi đã đo được</h3>
   *
   * `key` cũ là `article.data.publicId` — một **hằng số suốt vòng đời màn hình**. Nên sau khi
   * Phục hồi một phiên bản cũ, `ArticleForm` **không remount**, mà `useState(data?.content)` và
   * `initialValues` của AntD chỉ đổ dữ liệu vào **lượt mount đầu**. Đo bằng test dựng thật:
   * dữ liệu query đổi = `true`, DOM giữ nội dung CŨ = `true`, nội dung mới xuất hiện = `false`.
   *
   * Backend `restoreInto` ghi đè **9 trường**, nên màn hình đứng yên hoàn toàn — và cú bấm **Lưu**
   * kế tiếp gửi lại `content`/`documents`/`coverId` CŨ **đè lên bản vừa phục hồi**. Mất dữ liệu,
   * im lặng, không lỗi nào.
   *
   * <h3>Vì sao là một số đếm tường minh, không phải một trường dữ liệu</h3>
   *
   * ⛔ **Không** gắn `key` vào `version`/`updatedAt`: `queryClient` bật `refetchOnWindowFocus`, nên
   * gắn key vào dữ liệu là biến **mỗi lượt alt-tab quay lại** thành một lượt dựng lại biểu mẫu —
   * xoá sạch ngăn hoàn tác của TipTap và mọi ô đang gõ dở. (Và `ArticleDetail` hôm nay không có
   * mốc nào dùng được: không `updatedAt`, không `versionNo`.)
   *
   * ⛔ **Không** `location.reload()`: `apiClient` cố ý giữ access token **chỉ trong RAM**, nên F5
   * là mất phiên đăng nhập.
   *
   * ⛔ **Không** liệt kê tay 14 ô cho `form.setFieldsValue`: quên một ô là dựng lại đúng lỗi cũ ở
   * quy mô nhỏ hơn — và chỗ quên sẽ là ô ít dùng nhất, tức chỗ lâu bị phát hiện nhất.
   *
   * Mốc tường minh thì biểu mẫu **chỉ** dựng lại khi chính màn hình quyết định nạp lại.
   */
  const [mocNap, setMocNap] = useState(0);

  const article = useQuery({
    queryKey: cmsKeys.article(publicId ?? ''),
    queryFn: () => cmsApi.getArticle(publicId as string),
    enabled: !laBaiMoi,
  });

  /**
   * Nạp lại biểu mẫu từ một `ArticleDetail` **máy chủ vừa trả về**.
   *
   * ⚠ Cả `restoreVersion` và `transition` đều trả về `ArticleDetail` đầy đủ, nên không tốn thêm
   * một lượt mạng nào — `setQueryData` rồi tăng mốc là đủ. Bản trước `restore.mutate` **vứt bỏ**
   * giá trị ấy và chỉ gọi `invalidateQueries`.
   */
  const napLaiTuMayChu = (detail: ArticleDetail) => {
    queryClient.setQueryData(cmsKeys.article(detail.publicId), detail);
    setMocNap((truoc) => truoc + 1);
  };

  if (laBaiMoi) {
    return <ArticleForm key="moi" onNapLai={napLaiTuMayChu} />;
  }
  if (article.isLoading || !article.data) {
    return <Card loading title="Đang tải bài viết…" />;
  }
  return (
    <ArticleForm
      key={`${article.data.publicId}:${mocNap}`}
      article={article.data}
      onNapLai={napLaiTuMayChu}
    />
  );
}

function ArticleForm({
  article: data,
  onNapLai,
}: {
  article?: ArticleDetail;
  /** Dựng lại biểu mẫu từ dữ liệu máy chủ vừa trả về — xem `napLaiTuMayChu`. */
  onNapLai: (detail: ArticleDetail) => void;
}) {
  const laBaiMoi = data === undefined;
  const publicId = data?.publicId;
  const navigate = useNavigate();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const { chonTep: chonAnh, picker } = useMediaPicker();
  // ⭐ Hộp chọn THỨ HAI, kho khác — WS-40. Hai lượt gọi hook giữ trạng thái riêng và render hai
  //   `<Modal>` riêng; gộp làm một là dựng lại đúng cái trạng thái "đang mở để làm gì" mà kiểu
  //   hàm-hứa sinh ra để loại bỏ.
  const { chonTep: chonTaiLieu, picker: pickerTaiLieu } = useMediaPicker({ kho: 'TAI_LIEU' });

  const [form] = Form.useForm<FormValues>();
  const [content, setContent] = useState(data?.content ?? '');
  const [coverId, setCoverId] = useState<string | null>(data?.coverAttachmentPublicId ?? null);
  const [documents, setDocuments] = useState<ArticleDocumentView[]>(data?.documents ?? []);
  const [historyOpen, setHistoryOpen] = useState(false);
  /** Bài đã có slug thì thôi gợi ý; bài mới thì gợi ý cho tới khi người dùng tự sửa. */
  const [slugDaSuaTay, setSlugDaSuaTay] = useState(!laBaiMoi);
  /** Ảnh kéo-thả đang tải dở — xem `khoaLuu` bên dưới. */
  const [anhDangTai, setAnhDangTai] = useState(0);

  /**
   * Có thay đổi chưa lưu không — T41.10.
   *
   * ⭐⭐ Đo bằng **hành động của người dùng**, ⛔ KHÔNG so `content` với `data.content`.
   *
   * `HtmlSanitizer` gọi `Jsoup.clean` với OutputSettings mặc định ⇒ **prettyPrint bật** ⇒ HTML
   * trong CSDL có xuống dòng và thụt lề giữa các thẻ khối, còn `editor.getHTML()` thì không. Một
   * phép so chuỗi vì thế **báo bẩn 100%** với mọi bài nhiều hơn một khối — ngay khi vừa mở, chưa
   * gõ gì. Và một cảnh báo luôn hiện là một cảnh báo người dùng học cách bấm qua mà không đọc.
   *
   * `RichTextEditor` chỉ gọi `onChange` khi người dùng thật sự sửa: lượt đồng bộ từ ngoài vào dùng
   * `setContent(value, { emitUpdate: false })`, nên nó **không** bắn `onUpdate`. Vậy một lượt
   * `onChange` là một tín hiệu sạch, không cần so gì.
   */
  const [coSuaChuaLuu, setCoSuaChuaLuu] = useState(false);

  /**
   * Bản chuẩn hoá của nội dung **lúc nạp** — do chính trình soạn thảo báo lên (`onNormalized`).
   *
   * ⚠ Không so với `data.content`: chuỗi trong CSDL đã qua `Jsoup.clean` với prettyPrint bật nên
   * mang thụt lề mà `editor.getHTML()` không có ⇒ so trực tiếp là bẩn ngay khi vừa mở bài.
   */
  const mocNoiDung = useRef<string | null>(null);

  const { choPhepRoi, hopThoaiRoiTrang } = useChanRoiTrang(
    coSuaChuaLuu,
    'Phần vừa sửa chưa được lưu và sẽ mất nếu rời khỏi trang này.',
  );

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

  /**
   * Mở Kho tài liệu, **thêm tệp vào danh sách đính kèm**, rồi trả chữ cho trình soạn thảo chèn.
   *
   * <h3>⛔⛔ Chèn liên kết PHẢI đồng thời tạo mối nối — đây là chỗ ràng buộc ấy được ép</h3>
   *
   * Đường công khai `/api/v1/public/article-documents/{id}` đòi tệp có mặt trong **bản chụp phiên
   * bản** của bài. Một liên kết chỉ nằm trong HTML là một liên kết **chết**: nó có tên, bấm vào
   * trả 404, và không lỗi nào ở phía quản trị. Đó đúng là §10.52.
   *
   * ⚠ Chốt **một** trong hai cách nối, không để cả hai: *chèn ⇒ tự thêm vào danh sách*. Hệ quả
   * chấp nhận có ý thức là tệp vừa chèn giữa bài **cũng** xuất hiện ở khối cuối bài — khối ấy là
   * mục lục tài liệu của bài, nên trùng lặp ở đây không phải lỗi. ⛔ Không có lựa chọn thứ ba
   * "chèn được mà không nối": đó là dựng sẵn một liên kết chết.
   */
  const chenTaiLieuVaoBai = async () => {
    const tep = await chonTaiLieu();
    if (!tep) {
      return null;
    }
    const daCo = documents.find((d) => d.publicId === tep.publicId);
    if (!daCo) {
      setDocuments((truoc) => [
        ...truoc,
        {
          publicId: tep.publicId,
          label: null,
          originalName: tep.originalName,
          contentType: tep.contentType,
          sizeBytes: tep.sizeBytes,
          // Hộp chọn chỉ bày tệp trong Kho tài liệu; trạng thái quét thật do backend trả lại ở
          // lượt lưu kế tiếp. Đặt `true` ở đây là nói dối, nên đặt `false` và để dòng mang thẻ
          // "đang quét" cho tới lúc có câu trả lời thật (quy tắc 16).
          downloadable: false,
        },
      ]);
    }
    return { publicId: tep.publicId, text: daCo?.label ?? tep.originalName };
  };

  const initialValues: FormValues = {
    title: data?.title ?? '',
    slug: data?.slug ?? '',
    summary: data?.summary ?? '',
    source: data?.source ?? '',
    publishedAt: data?.publishedAt ? dayjs(data.publishedAt) : null,
    metaTitle: data?.metaTitle ?? '',
    metaDescription: data?.metaDescription ?? '',
    metaKeywords: data?.metaKeywords ?? '',
    docNumber: data?.docNumber ?? '',
    // ⚠ `docIssuedDate` là NGÀY thuần (`YYYY-MM-DD`), không phải mốc thời gian — `dayjs` đọc nó
    //   ở múi giờ địa phương và trả đúng ngày ấy. Đừng đưa qua `toApiInstant`.
    docIssuedDate: data?.docIssuedDate ? dayjs(data.docIssuedDate) : null,
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
      // Mốc mới là thứ VỪA GỬI ĐI, không phải thứ máy chủ trả về: `HtmlSanitizer` có thể lọc bớt
      // và prettyPrint thêm thụt lề, nên lấy bản máy chủ làm mốc là bẩn ngay sau khi lưu.
      mocNoiDung.current = content;
      setCoSuaChuaLuu(false);
      await invalidate();
      if (laBaiMoi) {
        // ⛔ `choPhepRoi()` TRƯỚC `navigate`: React gộp `setState` rồi mới vẽ lại, nên hàm chặn mà
        //    router đang giữ vẫn là bản cũ (`coSuaChuaLuu === true`) và nó sẽ chặn đúng cú chuyển
        //    trang do chính ta thực hiện — người dùng lưu xong thì bị hỏi "rời trang không?".
        //    Một `ref` có hiệu lực ngay, không đợi lượt vẽ.
        choPhepRoi();
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
    // ⭐ Nạp lại biểu mẫu, không chỉ `invalidate`. Chuyển trạng thái đổi cả `status`,
    //   `allowedActions` **và** `publishedAt` (sau khi duyệt) — trước đây ba thứ ấy đổi trong
    //   dữ liệu mà biểu mẫu đứng yên, nên ô "Ngày đăng" không bao giờ hiện giờ đăng thật.
    onSuccess: async (detail) => {
      await invalidate();
      onNapLai(detail);
    },
    onError: (caught: unknown) =>
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không đổi được trạng thái',
      ),
  });

  /**
   * ⚠⚠ `validateFields()` **reject** khi có ô sai, và lời gọi ở nút Lưu là `void submit()` — không
   * gắn `catch` nào. Một promise bị từ chối mà không ai bắt là một **unhandledRejection**: trong
   * trình duyệt nó là một dòng đỏ ở console không ai đọc, còn trong vitest 4 nó làm **đỏ cả lượt
   * chạy** ở một tệp chẳng liên quan.
   *
   * Bắt ở đây, và **đưa tiêu điểm về ô sai đầu tiên**. ⛔ Không dùng prop `scrollToFirstError` của
   * `<Form>`: nó chỉ chạy trong `onInternalFinishFailed`, tức chỉ khi submit qua `form.submit()` —
   * màn hình này gọi `validateFields()` bằng tay nên prop ấy là một trường **không ai đọc**
   * (quy tắc 15).
   */
  const submit = async () => {
    let values: FormValues;
    try {
      values = await form.validateFields();
    } catch (loi) {
      const truong = (loi as { errorFields?: { name: (string | number)[] }[] }).errorFields?.[0];
      if (truong) {
        form.scrollToField(truong.name, { behavior: 'smooth', block: 'center' });
      }
      return;
    }
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
      docNumber: values.docNumber || undefined,
      // ⛔ `format('YYYY-MM-DD')` chứ KHÔNG `toISOString()`: chuyển sang UTC sẽ lùi ngày ký ban
      //    hành đúng một hôm với mọi văn bản ký trước 07:00 giờ Hà Nội. Ngày ban hành là một
      //    NGÀY trên tờ giấy, không có giờ để quy đổi.
      docIssuedDate: values.docIssuedDate ? values.docIssuedDate.format('YYYY-MM-DD') : null,
      categoryPublicIds: values.categoryPublicIds,
      // ⚠ Gửi cả khi rỗng, KHÔNG bỏ trường đi: mảng rỗng nghĩa là *gỡ hết tài liệu*, còn thiếu
      //   trường thì backend đọc `null` và cũng ghi rỗng — hai đường ra cùng kết quả hôm nay,
      //   nhưng chỉ một trong hai nói đúng ý người dùng. Gửi tường minh thì không phải đoán.
      documents: documents.map((d) => ({ publicId: d.publicId, label: d.label })),
    });
  };

  // Bài đang chờ duyệt bị khoá chỉnh sửa (CN-01.1). Backend là chốt chặn; khoá ở đây chỉ để
  // người dùng không gõ xong cả bài rồi mới bị từ chối.
  const khoaSua = data?.status === 'CHO_DUYET';

  /**
   * ⭐⭐ **T27.28 — lệch tầng 1 ↔ tầng 2**, vá 04/09/2026.
   *
   * Tuyến `/noi-dung/bai-viet/:publicId` gác bằng `cms:article:view` (tầng 1), còn nút Lưu gọi
   * `POST`/`PUT` đòi `cms:article:create`/`update` (tầng 3). Đo trên ma trận seed:
   *
   * | quyền | vai trò |
   * |---|---|
   * | `cms:article:view` | CONTENT_EDITOR · CONTENT_MANAGER · **EXECUTIVE** · **VIEWER** |
   * | `cms:article:create`/`update` | CONTENT_EDITOR · CONTENT_MANAGER |
   *
   * ⇒ **EXECUTIVE và VIEWER mở được trình soạn thảo**, gõ xong cả bài, bấm Lưu và nhận **403**.
   * Hai vai trò ấy là lãnh đạo và người xem — đúng những người ⛔ không có cách nào tự đoán rằng
   * mình chỉ được đọc, vì màn hình mở ra y hệt người soạn bài.
   *
   * ⛔ Cách sửa **sai** là siết tuyến lên `cms:article:create`: khi ấy lãnh đạo ⛔ không xem được
   * nội dung một bài nữa, mà xem là đúng thứ họ được phép. Cách đúng là **tầng 2 nói thật**: mở
   * để đọc, ⛔ không mở để ghi.
   *
   * ⚠ Khuôn đã có ngay cạnh — `ArticleListPage` gác từng nút bằng `hasPermission`. Lệch này sống
   * được vì trình soạn thảo có **0** lời gọi `hasPermission`.
   */
  const coQuyenGhi = hasPermission(laBaiMoi ? 'cms:article:create' : 'cms:article:update');

  /**
   * ⚠ Khoá Lưu khi còn ảnh đang tải, và đây **không** phải chuyện tiện dụng.
   *
   * Ảnh chưa tải xong thì `src` của nó là `blob:` — địa chỉ chỉ sống trong tab đang mở. Lưu
   * lúc đó thì `HtmlSanitizer` gỡ thuộc tính `src` (giao thức không nằm trong danh sách cho
   * phép) và bài viết còn lại một thẻ ảnh rỗng: người soạn thấy "Đã lưu", mở lại thì đúng
   * tấm ảnh vừa kéo vào đã biến mất, không lỗi nào.
   */
  const khoaLuu = khoaSua || anhDangTai > 0 || !coQuyenGhi;

  /**
   * Vì sao **không** chuyển trạng thái được lúc này — `null` = chuyển được. T41.11.
   *
   * <h3>⛔ Hai lỗ mà một luật khoá DÙNG CHUNG đóng lại</h3>
   *
   * Trước lượt này `ApprovalActions` chỉ khoá theo `transition.isPending`, trong khi nút Lưu ngay
   * phía trên đọc `khoaLuu`. Hai chỗ, hai luật, và cả hai lỗ đều im lặng:
   *
   * <ul>
   *   <li><b>Ảnh đang tải</b> — bấm "Gửi duyệt" lúc đó là gửi đi một bài có thẻ ảnh mang `blob:`,
   *       thứ `HtmlSanitizer` sẽ gỡ `src`. Người duyệt đọc một bài thiếu ảnh.
   *   <li><b>Còn sửa chưa lưu</b> — chuyển sang `CHO_DUYET` làm `khoaSua` bật ⇒ biểu mẫu
   *       `disabled` **và** nút Lưu khoá. Phần vừa gõ còn hiện trên màn hình mà **không còn đường
   *       nào lưu nó nữa**; backend cũng ném `CMS-2007` nếu cố. Mất công gõ, không cảnh báo.
   * </ul>
   *
   * ⚠ Trả **câu lý do** và hiện nó bằng CHỮ cạnh nút, ⛔ không chỉ ở `title`: máy tính bảng không
   * có hover, nên một nút xám kèm tooltip là một nút xám câm.
   */
  const lyDoKhongChuyenTrangThai: string | null = (() => {
    if (anhDangTai > 0) {
      return `Đang tải ${anhDangTai} ảnh lên — chờ xong rồi mới chuyển trạng thái được`;
    }
    if (coSuaChuaLuu) {
      return 'Còn thay đổi chưa lưu — bấm Lưu trước, vì chuyển sang Chờ duyệt sẽ khoá chỉnh sửa';
    }
    return null;
  })();

  return (
    <>
      <Card
        title={
          <Space>
            {/* ⚠ `aria-label` là BẮT BUỘC: nút chỉ có biểu tượng thì không có tên khả truy cập —
                trình đọc màn hình đọc "button", và `getByRole('button', { name })` không tìm thấy
                nó. Cùng khuôn `ToolbarButton` của trình soạn thảo. */}
            <Button
              icon={<ArrowLeftOutlined />}
              type="text"
              aria-label="Quay lại danh sách bài viết"
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
              // ⚠ `title` phải nói ĐÚNG lý do đang khoá — một nút xám ⛔ không giải thích được là
              //   thứ người dùng báo lại thành "hệ thống lỗi". Ba lý do, ba câu khác nhau.
              title={
                !coQuyenGhi
                  ? 'Bạn chỉ có quyền XEM bài viết — cần cms:article:create/update để lưu'
                  : anhDangTai > 0
                    ? `Đang tải ${anhDangTai} ảnh lên`
                    : undefined
              }
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
          onValuesChange={() => setCoSuaChuaLuu(true)}
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
                    : // ⚠ Câu cũ ở đây — "hệ thống tự thêm hậu tố nếu trùng" — là NÓI DỐI:
                      //   `requireUniqueSlug` của backend **ném** CMS-2001 chứ không thêm hậu tố.
                      //   Một dòng gợi ý sai còn tệ hơn không có: người dùng tin nó, đặt trùng, và
                      //   nhận một lỗi họ tưởng là lỗi hệ thống (§10.69 — tham số nói dối).
                      'Tự sinh từ tiêu đề; sửa được. Đường dẫn phải là DUY NHẤT — trùng thì bị từ chối'
                }
              >
                <Input addonBefore="/bai-viet/" onChange={() => setSlugDaSuaTay(true)} />
              </Form.Item>

              <Form.Item label="Nội dung" required>
                <RichTextEditor
                  value={content}
                  onChange={(html) => {
                    setContent(html);
                    // ⚠ TipTap bắn một lượt `onUpdate` khi nạp nội dung (đã đo). So với mốc chuẩn
                    //   hoá thay vì đếm lượt: lượt ấy mang đúng chuỗi của mốc nên không tính là sửa.
                    if (mocNoiDung.current !== null && html !== mocNoiDung.current) {
                      setCoSuaChuaLuu(true);
                    }
                  }}
                  onNormalized={(html) => {
                    mocNoiDung.current = html;
                  }}
                  disabled={khoaSua}
                  onPendingUploadsChange={setAnhDangTai}
                  onPickImage={async () => {
                    const file = await chonAnh();
                    return file ? { publicId: file.publicId, alt: file.originalName } : null;
                  }}
                  onPickDocument={chenTaiLieuVaoBai}
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

              {/* ⚠ Nằm NGOÀI `Form.Item`: danh sách này không phải một trường của biểu mẫu AntD,
                  nó là trạng thái riêng đi kèm lượt lưu. Nhét vào `Form` thì `validateFields`
                  phải biết cách so sánh một mảng đối tượng — và không đổi lại được gì. */}
              <ArticleDocumentsPanel
                documents={documents}
                onChange={setDocuments}
                disabled={khoaSua}
                onPick={() => void chenTaiLieuVaoBai()}
              />
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

              {/* ⭐ Hai ô này HIỆN LUÔN, không ẩn theo danh mục đang chọn.

                  Ẩn theo danh mục nghe hợp lý hơn, nhưng nó dựng ra một trạng thái thứ hai phải
                  nhớ đồng bộ với `site.home.documents-category` — đổi khoá ấy ở màn hình Cấu hình
                  giao diện là hai ô này biến mất khỏi đúng những bài cần chúng, và không có gì
                  báo (quy tắc 14). Hai ô trống thì không hại gì.

                  ⛔ Và KHÔNG điền mặc định: để trống ⇒ ô tương ứng trên cổng để trống. Suy ngày
                     ban hành từ ngày đăng là biến "chưa ai nhập" thành một câu khẳng định sai với
                     mọi văn bản được đăng lại sau ngày ký (quy tắc 16). */}
              <Card
                size="small"
                title="Thông tin văn bản (tuỳ chọn)"
                styles={{ body: { paddingBottom: 0 } }}
                style={{ marginBottom: 16 }}
              >
                <Form.Item
                  name="docNumber"
                  label="Số ký hiệu"
                  rules={[{ max: 100, message: 'Tối đa 100 ký tự' }]}
                  style={{ marginBottom: 12 }}
                >
                  <Input placeholder="VD: 43/2015/NĐ-CP" />
                </Form.Item>
                <Form.Item
                  name="docIssuedDate"
                  label="Ngày ban hành"
                  extra="Ngày ký trên văn bản — khác Ngày đăng ở trên. Để trống thì cột này trên cổng bỏ trống."
                  style={{ marginBottom: 12 }}
                >
                  <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
                </Form.Item>
              </Card>

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
            {lyDoKhongChuyenTrangThai && (
              <Typography.Paragraph type="warning" style={{ marginBottom: 8 }}>
                {lyDoKhongChuyenTrangThai}
              </Typography.Paragraph>
            )}
            <ApprovalActions
              actions={data.allowedActions}
              disabled={transition.isPending || lyDoKhongChuyenTrangThai !== null}
              onAction={async (action, reason) => {
                await transition.mutateAsync({ action, reason });
              }}
            />
          </>
        )}
      </Card>

      {picker}
      {pickerTaiLieu}
      {hopThoaiRoiTrang}

      {!laBaiMoi && (
        <VersionHistoryDrawer
          articleId={publicId as string}
          open={historyOpen}
          onClose={() => setHistoryOpen(false)}
          onRestored={async (detail) => {
            // ⭐ Hai việc, không phải một: `invalidate` làm mới DANH SÁCH bài và danh sách phiên
            //   bản; `onNapLai` dựng lại BIỂU MẪU. Bản trước chỉ có việc thứ nhất, nên dữ liệu
            //   đúng mà màn hình sai — xem `napLaiTuMayChu`.
            await invalidate();
            onNapLai(detail);
          }}
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
  docNumber: string;
  docIssuedDate: Dayjs | null;
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
