import { PrinterOutlined, SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Empty, Input, Select, Space, Typography, theme } from 'antd';
import { Fragment, useEffect, useMemo, useState, type CSSProperties, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { api } from '@/shared/apiClient';
import { viTriKhop } from '@/shared/boDau';

import { ManHinhCuaBan } from './ManHinhCuaBan';
import { TraCuuMaLoi } from './TraCuuMaLoi';
import { docTaiLieu, type Block, type InlineNode } from './markdown';
import { catMuc, hopTuKhoa, hopVaiTro, type MucTaiLieu } from './mucTaiLieu';
import { tenVaiTro } from './vaiTro';
import nguonMarkdown from './huong-dan-su-dung.md?raw';

import './huongDan.css';

/** Giá trị đặc biệt của ô chọn vai trò — ⛔ phải một mã vai trò thật. */
const CUA_TOI = '__CUA_TOI__';

/**
 * **Hướng dẫn sử dụng** — trang tra cứu cho toàn bộ người dùng đã đăng nhập.
 *
 * <h2>⛔⛔ Tuyến này CỐ Ý ⛔ gác bằng một mã quyền nào</h2>
 *
 * Nó nằm cùng nhóm với `/hop-thu` và `/phien-dang-nhap`: chỉ đòi đăng nhập. Gác bằng một mã quyền
 * — kể cả một mã rộng — là chặn đúng nhóm người cần nó nhất: cán bộ vai trò `VIEWER` mở hệ thống
 * lần đầu, thấy menu ngắn hơn đồng nghiệp và ⛔ có chỗ nào tra xem vì sao. Câu trả lời cho việc ấy
 * nằm ở §4.2 của chính tài liệu này.
 *
 * <h2>⭐ Nội dung là MỘT tệp `.md` trong kho, ⛔ phải chữ gõ thẳng vào JSX</h2>
 *
 * `huong-dan-su-dung.md` là **nguồn duy nhất**. Chép nội dung ra JSX là dựng hai bản của cùng một
 * tài liệu, và bản bị quên luôn là bản ⛔ ai mở hằng ngày (luật 14 · luật 27). Sửa hướng dẫn = sửa
 * đúng một tệp markdown, đọc được diff trong PR, ⛔ đụng tới component nào.
 *
 * <h2>⛔⛔ Vì sao tệp `.md` nằm trong `src/` chứ ⛔ ở `docs/`</h2>
 *
 * Đo 20/09/2026, **hai** lý do độc lập, cả hai đều là lỗi chỉ hiện ra ngoài máy của người viết mã:
 *
 * 1. `ci.yml:563` dựng image frontend với `context: frontend`, và `admin-app.Dockerfile` chỉ
 *    `COPY . .` từ context ấy ⇒ **`docs/` ở gốc kho ⛔ có trong image**. Một lượt
 *    `import '../../../../docs/…md?raw'` chạy ngon ở máy, qua `npm run build` ở máy, rồi hỏng ở
 *    bước đóng gói image.
 * 2. `ci.yml:206` lọc job frontend bằng `^(frontend/|deploy/|.github/workflows/)` ⇒ sửa hướng dẫn
 *    ở `docs/` sẽ **bỏ qua toàn bộ bộ kiểm FE**, tức bộ canh markdown ⛔ chạy đúng lúc cần nhất.
 *
 * <h2>⚠ `?raw` ⇒ chữ THUẦN, ⛔ bao giờ là HTML</h2>
 *
 * Tài liệu đi qua {@link docTaiLieu} rồi dựng thành phần tử React. ⛔ Có một lời gọi
 * `dangerouslySetInnerHTML` nào ở đây, và ⛔ được thêm.
 */
export function HuongDanPage() {
  const { token } = theme.useToken();
  const { user, hasPermission } = useAuth();
  const [tuKhoa, setTuKhoa] = useState('');
  const [vaiTro, setVaiTro] = useState<string>(CUA_TOI);
  const [nguoiDungMuonLoc, setNguoiDungMuonLoc] = useState(true);

  // ⚠ Tệp markdown là hằng số lúc build ⇒ phân tích đúng MỘT lần cho cả vòng đời trang.
  const { dauTrang, nhom } = useMemo(() => catMuc(docTaiLieu(nguonMarkdown)), []);

  /**
   * ⛔⛔ **Danh mục vai trò chỉ tải khi tài khoản ĐƯỢC PHÉP đọc nó.**
   *
   * `GET /admin/users/roles/catalog` đòi `adm:role:view` — chỉ 2/12 vai trò có. Gọi vô điều kiện
   * là bắn 403 vào mặt 10/12 người dùng mỗi lần họ mở trang hướng dẫn, và nhật ký bảo mật đầy
   * những dòng từ chối ⛔ ai gây ra.
   */
  const coXemVaiTro = hasPermission('adm:role:view');
  const dsVaiTro = useQuery({
    queryKey: ['huong-dan', 'vai-tro'],
    queryFn: () => api.get<{ code: string; name: string }[]>('/admin/users/roles/catalog'),
    enabled: coXemVaiTro,
    staleTime: 10 * 60_000,
  });
  const quyenVaiTro = useQuery({
    queryKey: ['huong-dan', 'quyen-vai-tro', vaiTro],
    queryFn: () => api.get<string[]>(`/admin/users/roles/${vaiTro}/permissions`),
    enabled: coXemVaiTro && vaiTro !== CUA_TOI,
    staleTime: 10 * 60_000,
  });

  const laCuaToi = vaiTro === CUA_TOI;
  /**
   * ⭐⭐ Bộ quyền dùng để lọc — **luôn** từ một nguồn SỐNG, ⛔ bao giờ từ bảng chép cứng.
   *
   * *Vai trò của tôi* đọc thẳng `user.permissions` trong token: chính xác tuyệt đối, ⛔ tốn một
   * lượt gọi mạng, và dùng được cho **cả 12** vai trò. Đó là lý do nó là giá trị mặc định chứ ⛔
   * phải một lựa chọn phụ — ma trận phân quyền là thứ Công ty tự sửa (`RolesPage`), nên mọi bản
   * chép sẵn đều sai kể từ ngày họ chỉnh ô đầu tiên (xem `vaiTro.ts`).
   */
  const quyen = useMemo(
    () => new Set(laCuaToi ? (user?.permissions ?? []) : (quyenVaiTro.data ?? [])),
    [laCuaToi, user?.permissions, quyenVaiTro.data],
  );

  const dangCho = !laCuaToi && quyenVaiTro.isPending;

  /**
   * ⭐⭐ **Liên kết sâu phải LUÔN đáp xuống đúng chỗ** — kể cả khi bộ lọc đang giấu mục ấy.
   *
   * Nút `?` trên thanh tiêu đề gửi tới `/huong-dan#<neo>`. Mục đích có thể đang bị **thu gọn**
   * (mở lại một liên kết cũ, hoặc vừa đổi ô chọn vai trò), và cuộn tới một phần tử ⛔ tồn tại thì
   * **⛔ có gì xảy ra**: trang mở ở đầu tài liệu, người dùng kết luận nút hỏng.
   *
   * ⚠⚠ Trạng thái bộ lọc vì thế là **SUY RA**, ⛔ phải đặt trong một effect. Bản đầu gọi
   * `setBoLoc(false)` bên trong `useEffect` và luật `react-hooks/set-state-in-effect` đỏ ngay —
   * nó đúng: một lượt dựng đặt state là một lượt dựng thừa, và ở đây còn dễ thành vòng lặp vì
   * chính `hien` nằm trong danh sách phụ thuộc. Suy ra thì ⛔ có vòng nào cả.
   */
  const { hash } = useLocation();
  const neoDich = hash ? decodeURIComponent(hash.slice(1)) : '';
  const mucDich = useMemo(
    () => nhom.flatMap((n) => [n.muc, ...n.con]).find((m) => m.id === neoDich),
    [nhom, neoDich],
  );
  const dichBiAn = mucDich !== undefined && !hopVaiTro(mucDich, quyen);
  const boLoc = nguoiDungMuonLoc && !dichBiAn;

  const hien = useMemo(() => {
    const hopMuc = (m: MucTaiLieu) =>
      (!boLoc || dangCho || hopVaiTro(m, quyen)) && hopTuKhoa(m, tuKhoa);
    return nhom
      .map((n) => {
        const con = n.con.filter(hopMuc);
        // ⭐ Giữ cả phần lớn khi **bất kỳ** mục con nào còn lại — ⛔ thì một phần hiện ra với mỗi
        //   cái tiêu đề trống trơn, trông như trang dựng hỏng.
        return { ...n, con, hienNhom: hopMuc(n.muc) || con.length > 0 };
      })
      .filter((n) => n.hienNhom);
  }, [nhom, boLoc, dangCho, quyen, tuKhoa]);

  const tongMuc = nhom.reduce((s, n) => s + 1 + n.con.length, 0);
  const soHien = hien.reduce((s, n) => s + 1 + n.con.length, 0);
  const daAn = tongMuc - soHien;
  const tenBoLoc = laCuaToi ? `Vai trò của tôi${tenCacVaiTro(user?.roles)}` : tenVaiTro(vaiTro);

  /** ⚠ Chỉ cuộn, ⛔ đặt state: trình duyệt tìm neo ngay khi tải, lúc ấy React chưa dựng xong. */
  useEffect(() => {
    if (!neoDich) {
      return;
    }
    document.getElementById(neoDich)?.scrollIntoView({ block: 'start' });
  }, [neoDich, hien]);

  const bienMau = {
    '--sn-hd-chu': token.colorText,
    '--sn-hd-chu-mo': token.colorTextSecondary,
    '--sn-hd-nhan': token.colorPrimary,
    '--sn-hd-vien': token.colorBorderSecondary,
    '--sn-hd-nen-phu': token.colorFillQuaternary,
    '--sn-hd-nen-soc': token.colorFillAlter,
  } as CSSProperties;

  return (
    <Card
      title="Hướng dẫn sử dụng"
      extra={
        <Space className="sn-hd__thanh-cong-cu" wrap>
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="Tìm trong tài liệu, cả mã lỗi"
            aria-label="Tìm trong tài liệu"
            value={tuKhoa}
            onChange={(e) => setTuKhoa(e.target.value)}
            style={{ width: 260 }}
          />
          <Select
            value={vaiTro}
            onChange={setVaiTro}
            style={{ width: 230 }}
            aria-label="Lọc theo vai trò"
            options={[
              { value: CUA_TOI, label: 'Vai trò của tôi' },
              ...(coXemVaiTro
                ? (dsVaiTro.data ?? []).map((r) => ({
                    value: r.code,
                    label: r.name || tenVaiTro(r.code),
                  }))
                : []),
            ]}
          />
          <Button icon={<PrinterOutlined />} onClick={() => window.print()}>
            In
          </Button>
        </Space>
      }
    >
      <div className="sn-hd" style={bienMau}>
        <article className="sn-hd__noi-dung">
          <BangLoc
            tenBoLoc={tenBoLoc}
            daAn={daAn}
            boLoc={boLoc}
            setBoLoc={setNguoiDungMuonLoc}
            coXemVaiTro={coXemVaiTro}
            laCuaToi={laCuaToi}
            tuKhoa={tuKhoa}
          />

          {dauTrang.map((b, i) => (
            <Fragment key={i}>{dungKhoi(b, tuKhoa)}</Fragment>
          ))}

          <ManHinhCuaBan quyen={quyen} nhom={nhom} tenBoLoc={tenBoLoc} laVaiTroCuaToi={laCuaToi} />

          {hien.length === 0 && (
            <Empty description={`Không có mục nào khớp "${tuKhoa}"`} style={{ margin: '40px 0' }} />
          )}

          {hien.map((n) => (
            <Fragment key={n.muc.id}>
              {n.muc.blocks.map((b, i) => (
                <Fragment key={i}>{dungKhoi(b, tuKhoa)}</Fragment>
              ))}
              {n.con.map((c) => (
                <Fragment key={c.id}>
                  {c.blocks.map((b, i) => (
                    <Fragment key={i}>{dungKhoi(b, tuKhoa)}</Fragment>
                  ))}
                </Fragment>
              ))}
            </Fragment>
          ))}

          <TraCuuMaLoi tuKhoa={tuKhoa} />
        </article>

        <nav className="sn-hd__muc-luc" aria-label="Mục lục tài liệu">
          <div className="sn-hd__muc-luc-nhan">Nội dung</div>
          <ol>
            <li>
              <a href="#man-hinh-cua-ban">Màn hình của bạn</a>
            </li>
            {hien.map((n) => (
              <li key={n.muc.id}>
                {/*
                  ⛔ `<Link>` của react-router: đây là một neo TRONG CÙNG trang. `<Link to="#x">`
                  đi qua bộ định tuyến và đẩy một mục mới vào lịch sử trình duyệt, nên nút Quay lại
                  sẽ đi ngược từng đề mục một thay vì rời trang.
                */}
                <a href={`#${n.muc.id}`}>{n.muc.tieuDe}</a>
              </li>
            ))}
            <li>
              <a href="#tra-cuu-ma-loi">Tra cứu mã lỗi</a>
            </li>
          </ol>
        </nav>
      </div>
    </Card>
  );
}

/**
 * Băng trạng thái bộ lọc.
 *
 * <h3>⛔⛔ Băng này ⛔ được tắt đi, và nó phải nói ra CON SỐ</h3>
 *
 * Quyết định 20/09: lọc thì **thu gọn**, ⛔ ẩn lặng. Một người đọc trang đã lọc mà ⛔ biết mình
 * đang đọc bản rút gọn sẽ kết luận hệ thống **⛔ có** chức năng kia, rồi đi báo thiếu tính năng —
 * đúng hình dạng quy tắc 16 (*số 0 là một câu khẳng định*) ở tầng tài liệu.
 */
function BangLoc({
  tenBoLoc,
  daAn,
  boLoc,
  setBoLoc,
  coXemVaiTro,
  laCuaToi,
  tuKhoa,
}: {
  tenBoLoc: string;
  daAn: number;
  boLoc: boolean;
  setBoLoc: (v: boolean) => void;
  coXemVaiTro: boolean;
  laCuaToi: boolean;
  tuKhoa: string;
}) {
  if (!boLoc) {
    return (
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        title="Đang hiện TOÀN BỘ tài liệu, kể cả phần không thuộc vai trò của bạn."
        action={
          <Button size="small" onClick={() => setBoLoc(true)}>
            Lọc lại
          </Button>
        }
      />
    );
  }
  if (daAn === 0 && tuKhoa.trim() === '') {
    // ⛔ Băng "đang lọc mà ⛔ ẩn gì" — với người đủ quyền thì nó là tiếng ồn mỗi lần mở trang.
    return coXemVaiTro && laCuaToi ? null : null;
  }
  return (
    <Alert
      type="info"
      showIcon
      style={{ marginBottom: 16 }}
      title={
        <>
          Đang lọc theo <b>{tenBoLoc}</b>
          {tuKhoa.trim() !== '' && (
            <>
              {' '}
              và từ khoá <b>“{tuKhoa.trim()}”</b>
            </>
          )}{' '}
          — <b>{daAn}</b> mục khác đã thu gọn. Hệ thống <b>vẫn có</b> những chức năng đó, chỉ là vai
          trò này không mở được.
        </>
      }
      action={
        <Button size="small" onClick={() => setBoLoc(false)}>
          Hiện tất cả
        </Button>
      }
    />
  );
}

/** Tên các vai trò của chính người đang đăng nhập, để ô chọn nói rõ "của tôi" là những gì. */
function tenCacVaiTro(ma: readonly string[] | undefined): string {
  if (!ma || ma.length === 0) {
    return '';
  }
  return ` (${ma.map(tenVaiTro).join(', ')})`;
}

/**
 * Tô sáng MỌI lần khớp trong một chuỗi chữ thường.
 *
 * ⚠ Dùng `viTriKhop` (bỏ dấu) chứ ⛔ `indexOf` thẳng: ô tìm kiếm khớp ⛔ dấu, nên tô bằng phép so
 * có dấu sẽ ra kết quả *"tìm thấy mà ⛔ tô gì"* — người dùng đọc thành **hệ thống tìm sai**.
 */
function chuCoToSang(van: string, tuKhoa: string): ReactNode {
  const khop = viTriKhop(van, tuKhoa);
  if (khop.length === 0) {
    return van;
  }
  const goc = van.normalize('NFC');
  const ra: ReactNode[] = [];
  let truoc = 0;
  khop.forEach((k, i) => {
    if (k.tu > truoc) {
      ra.push(<Fragment key={`t${i}`}>{goc.slice(truoc, k.tu)}</Fragment>);
    }
    ra.push(<mark key={`m${i}`}>{goc.slice(k.tu, k.den)}</mark>);
    truoc = k.den;
  });
  if (truoc < goc.length) {
    ra.push(<Fragment key="cuoi">{goc.slice(truoc)}</Fragment>);
  }
  return <>{ra}</>;
}

/** Dựng một dãy inline thành phần tử React. Đệ quy — xem `InlineNode`. */
function dungInline(nodes: readonly InlineNode[], tuKhoa: string): ReactNode {
  return nodes.map((n, i) => {
    switch (n.kind) {
      case 'strong':
        return <strong key={i}>{dungInline(n.children, tuKhoa)}</strong>;
      case 'em':
        return <em key={i}>{dungInline(n.children, tuKhoa)}</em>;
      case 'code':
        return <code key={i}>{chuCoToSang(n.text, tuKhoa)}</code>;
      case 'link':
        // ⚠ Neo trong trang (`#…`) mở tại chỗ; liên kết ra ngoài mở tab mới kèm `noreferrer`.
        return n.href.startsWith('#') ? (
          <a key={i} href={n.href}>
            {dungInline(n.children, tuKhoa)}
          </a>
        ) : (
          <a key={i} href={n.href} target="_blank" rel="noopener noreferrer">
            {dungInline(n.children, tuKhoa)}
          </a>
        );
      case 'plain':
        return <Fragment key={i}>{chuCoToSang(n.text, tuKhoa)}</Fragment>;
    }
  });
}

function dungKhoi(b: Block, tuKhoa: string): ReactNode {
  switch (b.kind) {
    case 'heading': {
      // Tiêu đề cấp 5–6 ⛔ có trong tài liệu; kẹp về h4 để ⛔ sinh thẻ ngoài thang đã có kiểu.
      const The = `h${Math.min(b.level, 4)}` as 'h1' | 'h2' | 'h3' | 'h4';
      return <The id={b.id}>{dungInline(b.inline, tuKhoa)}</The>;
    }
    case 'paragraph':
      return <p>{dungInline(b.inline, tuKhoa)}</p>;
    case 'quote':
      return (
        <aside className="sn-hd__luu-y">
          {b.paragraphs.map((p, i) => (
            <p key={i}>{dungInline(p, tuKhoa)}</p>
          ))}
        </aside>
      );
    case 'list': {
      const The = b.ordered ? 'ol' : 'ul';
      return (
        <The>
          {b.items.map((it, i) => (
            <li key={i}>{dungInline(it, tuKhoa)}</li>
          ))}
        </The>
      );
    }
    case 'table':
      return (
        <div className="sn-hd__bang-boc">
          <table className="sn-hd__bang">
            <thead>
              <tr>
                {b.head.map((o, i) => (
                  <th key={i}>{dungInline(o, tuKhoa)}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {b.rows.map((hang, i) => (
                <tr key={i}>
                  {hang.map((o, j) => (
                    <td key={j}>{dungInline(o, tuKhoa)}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    case 'code':
      // Sơ đồ bằng chữ (mũi tên, khung) — ⛔ phải mã nguồn, nên ⛔ tô màu cú pháp.
      return <div className="sn-hd__so-do">{b.text}</div>;
    case 'rule':
      return <hr />;
    case 'directive':
      // Thẻ khai dữ liệu — `catMuc` đã bóc ra khỏi phần đọc được. Tới được đây nghĩa là một thẻ
      // nằm ngoài mọi mục (trước tiêu đề đầu tiên); nuốt nó, ⛔ in ra cho người dùng.
      return null;
    case 'unknown':
      /*
       * ⛔⛔ Hiện nguyên văn kèm lời gọi tên, ⛔ im lặng bỏ qua.
       *
       * `phamViMarkdown.test.ts` đỏ ở CI trước khi một dòng như thế này kịp lên production, nên
       * nhánh này về nguyên tắc ⛔ bao giờ chạy. Nó tồn tại cho trường hợp bộ canh bị ai đó tắt:
       * một dòng lạ hiện ra kèm chữ *"⛔ đọc được"* thì có người đi sửa, còn một dòng **biến mất**
       * thì tài liệu thiếu một câu mà ⛔ ai biết (quy tắc 16).
       */
      return (
        <p>
          <Typography.Text type="danger">
            [dòng {b.lineNumber} ⛔ đọc được] {b.line}
          </Typography.Text>
        </p>
      );
  }
}
