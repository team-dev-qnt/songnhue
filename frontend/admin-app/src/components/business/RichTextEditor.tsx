import {
  AlignCenterOutlined,
  AlignLeftOutlined,
  AlignRightOutlined,
  BoldOutlined,
  CodeOutlined,
  DeleteOutlined,
  FileImageOutlined,
  ItalicOutlined,
  LinkOutlined,
  OrderedListOutlined,
  PlayCircleOutlined,
  StrikethroughOutlined,
  TableOutlined,
  UnderlineOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { TableKit } from '@tiptap/extension-table';
import { EditorContent, useEditor, useEditorState } from '@tiptap/react';
import { StarterKit } from '@tiptap/starter-kit';
import { App, Alert, Button, Divider, Input, Modal, Segmented, Space, Tooltip } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';

import { AlignClass, type AlignValue } from './AlignClass';
import { FigureImage, type ImageWidth } from './FigureImage';
import { VideoEmbed, toEmbedUrl } from './VideoEmbed';

import './richTextEditor.css';

/**
 * Trình soạn thảo nội dung — T20.1, CN-01.1.
 *
 * <h3>Vì sao TipTap chứ không phải CKEditor 5 hay TinyMCE</h3>
 *
 * `phase1-tracking.md` viết "CKEditor 5 hoặc TinyMCE, bản tự host". Đến thời điểm dựng
 * (8/2026) **cả hai đều đã chuyển sang GPL** — CKEditor 5 từ v44, TinyMCE từ v7. Dùng chúng
 * nghĩa là admin-app trở thành tác phẩm phái sinh của một thư viện GPL, và phải phát hành
 * theo GPL khi bàn giao. Đó là quyết định pháp lý của phía chủ đầu tư, không phải của người
 * viết mã, nên không thể chọn thay.
 *
 * TipTap là **MIT**, và nó còn giải một vấn đề mà hai lựa chọn kia không giải: ở đây ta khai
 * **chính xác** những nút nào tồn tại, nên bộ từ vựng HTML sinh ra khớp với danh sách cho
 * phép của `HtmlSanitizer`. Bộ soạn thảo trọn gói thì có hàng chục nút mà đa số tạo ra thẻ
 * backend sẽ gỡ — người dùng bấm được, lưu thành công, rồi mất nội dung.
 *
 * <h3>Ba chế độ, và vì sao có chế độ HTML</h3>
 *
 * CN-01.1 yêu cầu xem trước, và thực tế người soạn nội dung cơ quan thường dán từ Word rồi
 * cần sửa lại phần mã. Chế độ HTML là đường thoát cho những lần đó — có kiểm soát, vì nội
 * dung vẫn đi qua `HtmlSanitizer` lúc lưu.
 *
 * <h3>Ba đường chèn ảnh, và vì sao phải có cả ba</h3>
 *
 * 1. **Nút trên thanh công cụ** → mở thư viện media, chèn tại con trỏ.
 * 2. **Kéo-thả tệp từ máy** → chèn tại **chỗ thả**, không phải chỗ con trỏ đang đứng.
 * 3. **Dán ảnh chụp màn hình** → chèn tại con trỏ.
 *
 * Đường (1) một mình là không đủ, và không phải vì tiện: người soạn tin thường có sẵn ảnh
 * trong thư mục vừa chụp về, và bắt họ *tải lên thư viện → tìm lại → chèn* cho từng tấm là
 * ba thao tác cho một việc. Nhưng cái giá nếu **không** xử lý kéo-thả còn nặng hơn tiện lợi:
 * thả một tệp vào vùng soạn thảo mà không ai chặn thì trình duyệt **điều hướng cả tab sang
 * tệp đó** — và bài đang soạn dở biến mất.
 *
 * <h3>⛔ Tải lên vẫn đi qua backend, không dùng presigned URL</h3>
 *
 * Cho trình duyệt `PUT` thẳng vào MinIO thì bỏ qua **toàn bộ** chuỗi kiểm ở đường tải lên:
 * `FileValidator` (magic bytes — đuôi tệp nói dối được), `ImageSanitizer` (bóc EXIF, mà EXIF
 * ảnh chụp bằng điện thoại mang **toạ độ GPS** — đăng lên cổng công khai là công bố vị trí
 * công trình thuỷ lợi), `SvgSanitizer`, ClamAV, và hạn mức dung lượng đọc từ `settings`.
 * MinIO nhận đúng thứ trình duyệt gửi, và ta không có bộ bắt sự kiện nào để quét bù về sau.
 * Đổi lấy một chặng mạng nội bộ cho hệ 200 người dùng là không đáng.
 */

export interface RichTextEditorProps {
  value: string;
  onChange: (html: string) => void;
  /** Mở thư viện media và trả về ảnh được chọn. `null` = người dùng đóng hộp thoại. */
  onPickImage?: () => Promise<{ publicId: string; alt?: string } | null>;
  /**
   * Tải một tệp ảnh lên và trả về mã của nó. Thiếu hàm này thì kéo-thả và dán bị **tắt hẳn**
   * (và vẫn chặn trình duyệt điều hướng đi) chứ không im lặng nuốt tệp.
   */
  onUploadImage?: (file: File) => Promise<{ publicId: string }>;
  /** Số ảnh đang tải dở — nơi gọi dùng để khoá nút Lưu. Xem `FigureImage.TransientAttrs`. */
  onPendingUploadsChange?: (count: number) => void;
  disabled?: boolean;
  minHeight?: number;
}

type Mode = 'soan' | 'html' | 'xem-truoc';

/** Đường dẫn ảnh **ổn định vĩnh viễn** — xem tài liệu của `FigureImage`. */
function publicFileUrl(publicId: string): string {
  return `/api/v1/public/files/${publicId}`;
}

/** Ảnh mà cả trình duyệt lẫn `FileValidator` của backend đều nhận. */
const KIEU_ANH_NHAN = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];

function locTepAnh(data: DataTransfer | null): File[] {
  if (!data) {
    return [];
  }
  return Array.from(data.files).filter((file) => KIEU_ANH_NHAN.includes(file.type));
}

export function RichTextEditor({
  value,
  onChange,
  onPickImage,
  onUploadImage,
  onPendingUploadsChange,
  disabled = false,
  minHeight = 360,
}: RichTextEditorProps) {
  const { message } = App.useApp();
  const [mode, setMode] = useState<Mode>('soan');
  const [linkOpen, setLinkOpen] = useState(false);
  const [linkUrl, setLinkUrl] = useState('');
  const [linkNewTab, setLinkNewTab] = useState(true);
  const [videoOpen, setVideoOpen] = useState(false);
  const [videoUrl, setVideoUrl] = useState('');
  const [dangTai, setDangTai] = useState(0);

  /**
   * ⚠⚠ Bộ xử lý kéo-thả được TipTap giữ **từ lượt dựng đầu tiên** và không nhận lại theo mỗi
   * lượt vẽ. Mà ở lượt đầu tiên, `editor` còn là `undefined` — nên đóng gói thẳng hàm xử lý
   * vào `editorProps` là khoá cứng một bản đóng gói mà trong đó *chưa có trình soạn thảo nào
   * tồn tại*: thả ảnh vào bài sẽ **không có gì xảy ra**, không lỗi, không thông báo.
   *
   * Đi qua ref thì lượt thả nào cũng gọi đúng bản mới nhất. Cùng lý do với `uploadRef`.
   */
  const xuLyTep = useRef<(files: File[], viTri: number) => void>(() => {});
  const uploadRef = useRef(onUploadImage);
  useEffect(() => {
    uploadRef.current = onUploadImage;
  }, [onUploadImage]);

  useEffect(() => {
    onPendingUploadsChange?.(dangTai);
  }, [dangTai, onPendingUploadsChange]);

  /** Địa chỉ `blob:` đã cấp — thu hồi hết khi rời màn hình, nếu không là rò bộ nhớ. */
  const blobDaCap = useRef(new Set<string>());
  useEffect(() => {
    const set = blobDaCap.current;
    return () => set.forEach((url) => URL.revokeObjectURL(url));
  }, []);

  const editor = useEditor({
    editable: !disabled,
    extensions: [
      StarterKit.configure({
        // Chỉ h2–h4: h1 dành cho tiêu đề bài, do trang hiển thị đặt. Cho người soạn tạo h1
        // giữa bài là tạo ra hai tiêu đề cấp một trên cùng một trang — công cụ tìm kiếm
        // hiểu sai cấu trúc, và trình đọc màn hình cũng vậy.
        heading: { levels: [2, 3, 4] },
        link: {
          openOnClick: false,
          // `HtmlSanitizer` chỉ nhận http/https/mailto/tel. Khai lại ở đây để người dùng
          // biết ngay lúc dán, thay vì mất liên kết lúc lưu.
          protocols: ['http', 'https', 'mailto', 'tel'],
        },
      }),
      TableKit.configure({ table: { resizable: true } }),
      AlignClass,
      FigureImage,
      VideoEmbed,
    ],
    content: value,
    onUpdate: ({ editor: current }) => onChange(current.getHTML()),
    editorProps: {
      /**
       * Thả tệp ảnh từ máy vào bài.
       *
       * ⚠ Trả `true` là **bắt buộc kể cả khi ta không chèn được gì** (chưa cấu hình đường tải
       * lên): trả `false` nghĩa là "để trình duyệt lo", và trình duyệt lo bằng cách **mở tệp
       * đó thay cho cả trang** — bài đang soạn dở mất trắng.
       */
      handleDrop: (view, event, _slice, moved) => {
        // `moved` = đang kéo một nút có sẵn trong bài sang chỗ khác. Việc của ProseMirror.
        if (moved) {
          return false;
        }
        const tepAnh = locTepAnh(event.dataTransfer);
        if (tepAnh.length === 0) {
          return false;
        }
        event.preventDefault();
        // Vị trí **chỗ thả**, không phải chỗ con trỏ đang đứng. Người dùng ngắm vào giữa hai
        // đoạn văn rồi thả thì ảnh phải nằm đúng đó.
        const toado = view.posAtCoords({ left: event.clientX, top: event.clientY });
        xuLyTep.current(tepAnh, toado?.pos ?? view.state.selection.from);
        return true;
      },

      /**
       * Dán ảnh — ảnh chụp màn hình là đường dùng nhiều nhất.
       *
       * ⚠ Chỉ nhận khi khay nhớ tạm **không kèm HTML**. Dán từ Word thì khay chứa cả `text/html`
       * lẫn ảnh; cướp lấy phần ảnh sẽ làm mất toàn bộ chữ mà người dùng vừa dán.
       */
      handlePaste: (view, event) => {
        const data = event.clipboardData;
        if (!data || data.types.includes('text/html')) {
          return false;
        }
        const tepAnh = locTepAnh(data);
        if (tepAnh.length === 0) {
          return false;
        }
        event.preventDefault();
        xuLyTep.current(tepAnh, view.state.selection.from);
        return true;
      },
    },
  });

  /**
   * Chèn ô giữ chỗ ngay lập tức, rồi tải lên nền và thay ảnh thật vào.
   *
   * <h3>Vì sao giữ chỗ trước chứ không chờ tải xong</h3>
   *
   * Ảnh hồ sơ công trình thường vài MB. Chờ tải xong mới chèn nghĩa là người dùng thả ảnh
   * xuống và **không có gì xảy ra** trong vài giây — họ sẽ thả lần nữa, và có hai tấm.
   */
  const nhanTepAnh = useCallback(
    async (files: File[], viTri: number) => {
      if (!editor) {
        return;
      }
      const tai = uploadRef.current;
      if (!tai) {
        message.warning('Màn hình này chưa bật đường tải ảnh lên — dùng nút chèn ảnh từ thư viện');
        return;
      }

      const cho = files.map((file) => {
        const blobUrl = URL.createObjectURL(file);
        blobDaCap.current.add(blobUrl);
        return { file, blobUrl, uploadId: crypto.randomUUID() };
      });

      // ⚠ Chèn **một lượt** cho cả nhóm, không phải mỗi tấm một lượt cộng dồn vị trí. Chèn một
      // nút khối vào giữa đoạn văn làm đoạn đó tách đôi, nên vị trí sau lượt chèn không phải là
      // `vị trí cũ + 1` — tự cộng tay thì thả năm tấm ra thứ tự lộn xộn.
      editor.commands.insertContentAt(
        viTri,
        cho.map(({ blobUrl, uploadId, file }) => ({
          type: 'figureImage',
          attrs: {
            src: blobUrl,
            alt: file.name,
            caption: null,
            width: null,
            uploadId,
            uploading: true,
          },
        })),
      );
      setDangTai((truoc) => truoc + cho.length);

      // Tuần tự chứ không song song: năm lượt tải cùng lúc từ một người soạn không nhanh hơn
      // bao nhiêu, nhưng làm hạn mức theo IP của backend nhìn như một lượt tấn công.
      for (const { file, blobUrl, uploadId } of cho) {
        try {
          const { publicId } = await tai(file);
          editor.commands.resolveFigureUpload(uploadId, { src: publicFileUrl(publicId) });
        } catch (caught) {
          editor.commands.dropFigureUpload(uploadId);
          message.error(
            `Không tải được ảnh "${file.name}"${caught instanceof Error ? `: ${caught.message}` : ''}`,
          );
        } finally {
          setDangTai((truoc) => truoc - 1);
          URL.revokeObjectURL(blobUrl);
          blobDaCap.current.delete(blobUrl);
        }
      }
    },
    [editor, message],
  );

  useEffect(() => {
    xuLyTep.current = (files, viTri) => void nhanTepAnh(files, viTri);
  }, [nhanTepAnh]);

  // Nội dung đến từ bên ngoài (nạp bài, phục hồi phiên bản cũ) — đồng bộ vào trình soạn thảo.
  // ⚠ Điều kiện so sánh là bắt buộc: thiếu nó thì mỗi lượt gõ sẽ nạp lại nội dung và con trỏ
  // nhảy về đầu bài sau mỗi ký tự.
  useEffect(() => {
    if (editor && value !== editor.getHTML()) {
      editor.commands.setContent(value, { emitUpdate: false });
    }
  }, [editor, value]);

  useEffect(() => {
    editor?.setEditable(!disabled);
  }, [editor, disabled]);

  /**
   * Trạng thái của ảnh đang được chọn.
   *
   * `useEditorState` chứ không phải đọc thẳng `editor.isActive(...)` trong thân hàm: bản đọc
   * thẳng chỉ đúng ở lượt vẽ tình cờ xảy ra sau khi vùng chọn đổi, nên thanh công cụ ảnh sẽ
   * lúc hiện lúc không.
   */
  const anhDangChon = useEditorState({
    editor,
    selector: ({ editor: current }) => {
      if (!current?.isActive('figureImage')) {
        return null;
      }
      const attrs = current.getAttributes('figureImage');
      return {
        alt: (attrs.alt as string | null) ?? '',
        caption: (attrs.caption as string | null) ?? '',
        width: (attrs.width as ImageWidth | null) ?? null,
        align: (attrs.align as AlignValue | null) ?? null,
        uploading: Boolean(attrs.uploading),
      };
    },
  });

  if (!editor) {
    return null;
  }

  const chenAnhTuThuVien = async () => {
    if (!onPickImage) {
      return;
    }
    const picked = await onPickImage();
    if (!picked) {
      return;
    }
    editor
      .chain()
      .focus()
      .insertFigureImage({
        src: publicFileUrl(picked.publicId),
        alt: picked.alt ?? null,
        caption: null,
        width: null,
      })
      .run();
  };

  /**
   * Sửa một thuộc tính của ảnh đang chọn.
   *
   * ⚠ Cố ý **không** gọi `.focus()`: ô nhập chú thích nằm ngoài vùng soạn thảo, kéo con trỏ
   * về vùng soạn sau mỗi ký tự thì người dùng gõ được đúng một chữ cái rồi mất tiêu điểm.
   */
  const suaAnh = (attrs: Record<string, unknown>) =>
    editor.commands.updateAttributes('figureImage', attrs);

  const apDungLienKet = () => {
    const url = linkUrl.trim();
    if (url.length === 0) {
      editor.chain().focus().unsetLink().run();
    } else {
      editor
        .chain()
        .focus()
        .extendMarkRange('link')
        // `rel` bắt buộc khi mở tab mới: thiếu nó thì trang đích điều khiển được tab gốc
        // qua `window.opener` và đổi nó thành một trang đăng nhập giả.
        .setLink({
          href: url,
          target: linkNewTab ? '_blank' : null,
          rel: linkNewTab ? 'noopener' : null,
        })
        .run();
    }
    setLinkOpen(false);
    setLinkUrl('');
  };

  const chenVideo = () => {
    if (!editor.chain().focus().insertVideo(videoUrl).run()) {
      message.error(
        'Chỉ nhận đường dẫn YouTube hoặc Vimeo dạng https — đường khác sẽ bị gỡ khi lưu',
      );
      return;
    }
    setVideoOpen(false);
    setVideoUrl('');
  };

  return (
    <div className="sn-editor">
      <Space wrap size={4} className="sn-editor__toolbar">
        <Segmented<Mode>
          size="small"
          value={mode}
          onChange={setMode}
          options={[
            { label: 'Soạn thảo', value: 'soan' },
            { label: 'HTML', value: 'html' },
            { label: 'Xem trước', value: 'xem-truoc' },
          ]}
        />

        {mode === 'soan' && (
          <>
            <Divider type="vertical" />
            <ToolbarButton
              title="Đậm"
              icon={<BoldOutlined />}
              active={editor.isActive('bold')}
              onClick={() => editor.chain().focus().toggleBold().run()}
            />
            <ToolbarButton
              title="Nghiêng"
              icon={<ItalicOutlined />}
              active={editor.isActive('italic')}
              onClick={() => editor.chain().focus().toggleItalic().run()}
            />
            <ToolbarButton
              title="Gạch chân"
              icon={<UnderlineOutlined />}
              active={editor.isActive('underline')}
              onClick={() => editor.chain().focus().toggleUnderline().run()}
            />
            <ToolbarButton
              title="Gạch ngang"
              icon={<StrikethroughOutlined />}
              active={editor.isActive('strike')}
              onClick={() => editor.chain().focus().toggleStrike().run()}
            />
            <Divider type="vertical" />
            {([2, 3, 4] as const).map((level) => (
              <ToolbarButton
                key={level}
                title={`Tiêu đề cấp ${level}`}
                label={`H${level}`}
                active={editor.isActive('heading', { level })}
                onClick={() => editor.chain().focus().toggleHeading({ level }).run()}
              />
            ))}
            <Divider type="vertical" />
            <ToolbarButton
              title="Gạch đầu dòng"
              icon={<UnorderedListOutlined />}
              active={editor.isActive('bulletList')}
              onClick={() => editor.chain().focus().toggleBulletList().run()}
            />
            <ToolbarButton
              title="Danh sách đánh số"
              icon={<OrderedListOutlined />}
              active={editor.isActive('orderedList')}
              onClick={() => editor.chain().focus().toggleOrderedList().run()}
            />
            <ToolbarButton
              title="Khối mã"
              icon={<CodeOutlined />}
              active={editor.isActive('codeBlock')}
              onClick={() => editor.chain().focus().toggleCodeBlock().run()}
            />
            <Divider type="vertical" />
            {ALIGN_BUTTONS.map(({ value, title, icon }) => (
              <ToolbarButton
                key={value}
                title={title}
                icon={icon}
                active={anhDangChon?.align === value}
                onClick={() => {
                  if (!editor.chain().focus().setAlign(value).run()) {
                    message.warning('Đặt con trỏ vào đoạn văn, tiêu đề hoặc ảnh trước khi căn lề');
                  }
                }}
              />
            ))}
            <Divider type="vertical" />
            <ToolbarButton
              title="Liên kết"
              icon={<LinkOutlined />}
              active={editor.isActive('link')}
              onClick={() => {
                setLinkUrl((editor.getAttributes('link').href as string) ?? '');
                setLinkOpen(true);
              }}
            />
            {onPickImage && (
              <ToolbarButton
                title="Chèn ảnh từ thư viện"
                icon={<FileImageOutlined />}
                onClick={() => void chenAnhTuThuVien()}
              />
            )}
            <ToolbarButton
              title="Nhúng video YouTube/Vimeo"
              icon={<PlayCircleOutlined />}
              onClick={() => setVideoOpen(true)}
            />
            <ToolbarButton
              title="Chèn bảng 3×3"
              icon={<TableOutlined />}
              onClick={() =>
                editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
              }
            />
          </>
        )}
      </Space>

      {mode === 'soan' && anhDangChon && (
        <div className="sn-editor__imagebar">
          <Space wrap size={8} align="start">
            <Space.Compact>
              {WIDTH_BUTTONS.map(({ value, label, title }) => (
                <Tooltip key={value ?? 'auto'} title={title}>
                  <Button
                    size="small"
                    type={anhDangChon.width === value ? 'primary' : 'default'}
                    onClick={() => suaAnh({ width: value })}
                  >
                    {label}
                  </Button>
                </Tooltip>
              ))}
            </Space.Compact>

            <Input
              size="small"
              style={{ width: 260 }}
              placeholder="Chú thích hiện dưới ảnh"
              value={anhDangChon.caption}
              onChange={(event) => suaAnh({ caption: event.target.value })}
              allowClear
            />
            <Input
              size="small"
              style={{ width: 260 }}
              placeholder="Mô tả ảnh cho người khiếm thị (alt)"
              value={anhDangChon.alt}
              onChange={(event) => suaAnh({ alt: event.target.value })}
              allowClear
            />
            <Tooltip title="Xoá ảnh khỏi bài">
              <Button
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={() => editor.chain().focus().deleteSelection().run()}
              />
            </Tooltip>
          </Space>
        </div>
      )}

      {dangTai > 0 && (
        <Alert
          type="info"
          showIcon
          banner
          message={`Đang tải ${dangTai} ảnh lên — chưa lưu được bài cho tới khi xong`}
        />
      )}

      {mode === 'soan' && (
        <EditorContent editor={editor} className="sn-editor__body" style={{ minHeight }} />
      )}

      {mode === 'html' && (
        <Input.TextArea
          value={value}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
          style={{ minHeight, fontFamily: 'monospace', fontSize: 13 }}
          spellCheck={false}
        />
      )}

      {mode === 'xem-truoc' && (
        <div className="sn-editor__body sn-editor__preview" style={{ minHeight }}>
          {/* eslint-disable-next-line react/no-danger -- nội dung của chính người đang soạn,
              và HtmlSanitizer (BE) lọc lại lúc GHI. Xem trước mà không dựng HTML thì nó không
              còn là xem trước. */}
          <div dangerouslySetInnerHTML={{ __html: value }} />
        </div>
      )}

      <Modal
        open={linkOpen}
        title="Liên kết"
        okText="Áp dụng"
        cancelText="Huỷ"
        onCancel={() => setLinkOpen(false)}
        onOk={apDungLienKet}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Input
            value={linkUrl}
            onChange={(event) => setLinkUrl(event.target.value)}
            placeholder="https://… hoặc /bai-viet/ten-bai cho liên kết nội bộ"
            autoFocus
          />
          <Button
            type={linkNewTab ? 'primary' : 'default'}
            size="small"
            onClick={() => setLinkNewTab((prev) => !prev)}
          >
            {linkNewTab ? '✓ Mở tab mới' : 'Mở trong tab hiện tại'}
          </Button>
          <span style={{ color: '#8c8c8c', fontSize: 12 }}>
            Để trống rồi bấm Áp dụng để bỏ liên kết.
          </span>
        </Space>
      </Modal>

      <Modal
        open={videoOpen}
        title="Nhúng video"
        okText="Chèn"
        cancelText="Huỷ"
        okButtonProps={{ disabled: toEmbedUrl(videoUrl) === null }}
        onCancel={() => setVideoOpen(false)}
        onOk={chenVideo}
      >
        <Input
          value={videoUrl}
          onChange={(event) => setVideoUrl(event.target.value)}
          placeholder="https://www.youtube.com/watch?v=… hoặc https://vimeo.com/…"
          autoFocus
        />
        <p style={{ color: '#8c8c8c', fontSize: 12, marginTop: 8 }}>
          Chỉ nhận YouTube và Vimeo. Đường dẫn được đổi sang dạng nhúng không đặt cookie theo dõi.
        </p>
      </Modal>
    </div>
  );
}

const ALIGN_BUTTONS: { value: AlignValue; title: string; icon: React.ReactNode }[] = [
  { value: 'left', title: 'Căn trái', icon: <AlignLeftOutlined /> },
  { value: 'center', title: 'Căn giữa', icon: <AlignCenterOutlined /> },
  { value: 'right', title: 'Căn phải', icon: <AlignRightOutlined /> },
];

/**
 * Bề ngang ảnh.
 *
 * "Tự nhiên" (`null`) là mặc định và **không** phát ra class nào — nội dung cũ soạn trước khi
 * có tính năng này giữ nguyên cách hiển thị của nó.
 */
const WIDTH_BUTTONS: { value: ImageWidth | null; label: string; title: string }[] = [
  { value: null, label: 'Tự nhiên', title: 'Giữ kích thước gốc của ảnh' },
  { value: 'sn-w-full', label: '100%', title: 'Chiếm trọn bề ngang khung bài' },
  { value: 'sn-w-1-2', label: '1/2', title: 'Một nửa bề ngang — căn lề mới thấy rõ tác dụng' },
  { value: 'sn-w-1-3', label: '1/3', title: 'Một phần ba bề ngang' },
];

function ToolbarButton({
  title,
  icon,
  label,
  active,
  onClick,
}: {
  title: string;
  icon?: React.ReactNode;
  label?: string;
  active?: boolean;
  onClick: () => void;
}) {
  return (
    <Tooltip title={title}>
      <Button
        size="small"
        type={active ? 'primary' : 'text'}
        icon={icon}
        onClick={onClick}
        aria-label={title}
        aria-pressed={active}
      >
        {label}
      </Button>
    </Tooltip>
  );
}
