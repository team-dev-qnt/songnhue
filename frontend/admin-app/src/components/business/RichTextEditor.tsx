import {
  AlignCenterOutlined,
  AlignLeftOutlined,
  AlignRightOutlined,
  BoldOutlined,
  CodeOutlined,
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
import { EditorContent, useEditor } from '@tiptap/react';
import { StarterKit } from '@tiptap/starter-kit';
import { App, Button, Divider, Input, Modal, Segmented, Space, Tooltip } from 'antd';
import { useEffect, useState } from 'react';

import { AlignClass } from './AlignClass';
import { FigureImage } from './FigureImage';
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
 * backend sẽ gỡ — người dùng bấm được, lưu thành công, rồi mất nội dung. `EditorVocabularyTest`
 * canh đúng ranh giới đó, và nó bắt được một lỗi thật ngay lượt chạy đầu (`<s>` bị gỡ).
 *
 * <h3>Ba chế độ, và vì sao có chế độ HTML</h3>
 *
 * CN-01.1 yêu cầu xem trước, và thực tế người soạn nội dung cơ quan thường dán từ Word rồi
 * cần sửa lại phần mã. Chế độ HTML là đường thoát cho những lần đó — có kiểm soát, vì nội
 * dung vẫn đi qua `HtmlSanitizer` lúc lưu.
 */

export interface RichTextEditorProps {
  value: string;
  onChange: (html: string) => void;
  /** Mở thư viện media và trả về ảnh được chọn. `null` = người dùng đóng hộp thoại. */
  onPickImage?: () => Promise<{ publicId: string; alt?: string } | null>;
  disabled?: boolean;
  minHeight?: number;
}

type Mode = 'soan' | 'html' | 'xem-truoc';

/** Đường dẫn ảnh **ổn định vĩnh viễn** — xem tài liệu của `FigureImage`. */
function publicFileUrl(publicId: string): string {
  return `/api/v1/public/files/${publicId}`;
}

export function RichTextEditor({
  value,
  onChange,
  onPickImage,
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
  });

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

  if (!editor) {
    return null;
  }

  const chenAnh = async () => {
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
      })
      .run();
  };

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
            <ToolbarButton
              title="Căn trái"
              icon={<AlignLeftOutlined />}
              onClick={() => editor.chain().focus().setAlign('left').run()}
            />
            <ToolbarButton
              title="Căn giữa"
              icon={<AlignCenterOutlined />}
              onClick={() => editor.chain().focus().setAlign('center').run()}
            />
            <ToolbarButton
              title="Căn phải"
              icon={<AlignRightOutlined />}
              onClick={() => editor.chain().focus().setAlign('right').run()}
            />
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
                onClick={() => void chenAnh()}
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
