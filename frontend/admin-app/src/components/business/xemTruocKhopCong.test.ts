import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * **Xem trước phải hiển thị giống cổng** — T41.17.
 *
 * ## Đây là §10.26 ở dạng thuần khiết
 *
 * *"Màn hình xem trước trong admin-app vẫn đúng, vì nó dùng CSS của trình soạn thảo. Biên tập viên
 * định dạng kỹ, xem trước thấy đẹp, xuất bản, và không bao giờ mở lại trang công khai để đối
 * chiếu."*
 *
 * Trước bản này, khung soạn thảo ⛔ không khai **một cỡ tiêu đề nào**: `h2`/`h3`/`h4` hiện đúng bằng
 * cỡ đoạn văn. Người soạn ⛔ không nhìn thấy cấu trúc mình đang dựng, còn trên cổng ba cấp ấy khác
 * nhau rõ rệt — hai màn hình nói hai chuyện về cùng một bài.
 *
 * ## Bài này so GIÁ TRỊ ĐÃ GIẢI, ⛔ không so sự có mặt
 *
 * `richTextEditorCss.test.ts` hỏi *"có quy tắc không"*. Câu ấy vẫn xanh khi hai bên cùng khai
 * `font-size` mà một bên `1.5rem`, bên kia `1.1rem` — tức đúng lúc lệch. Nên ở đây phép so là
 * **bằng nhau từng chuỗi**.
 *
 * ## ⚠ Phạm vi tự khai (luật 28)
 *
 * Chỉ so những cặp trong {@link CAP_PHAI_KHOP}. Hai tệp CỐ Ý khác nhau ở nhiều chỗ khác (khung
 * soạn thảo có viền, tay nắm kéo, lớp phủ ô đang chọn — cổng ⛔ không có gì trong số đó), nên một
 * phép so toàn bộ sẽ đỏ vì lý do sai. Cặp nào chưa có trong danh sách thì bài này ⛔ không thấy.
 */

const ADMIN = 'admin-app/src/components/business/richTextEditor.css';
const CONG = 'public-web/src/app/article-content.css';

/** ⛔ Không dùng `import.meta.url`: Vitest chạy trong jsdom, Vite đổi nó thành URL `http://`. */
function docTuGocKho(duongDanTuongDoi: string): string {
  let hienTai = process.cwd();
  for (let sau = 0; sau < 6; sau += 1) {
    const ungVien = join(hienTai, 'frontend', duongDanTuongDoi);
    if (existsSync(ungVien)) {
      return readFileSync(ungVien, 'utf8');
    }
    const gan = join(hienTai, duongDanTuongDoi);
    if (existsSync(gan)) {
      return readFileSync(gan, 'utf8');
    }
    const cha = dirname(hienTai);
    if (cha === hienTai) break;
    hienTai = cha;
  }
  throw new Error(`Không tìm thấy ${duongDanTuongDoi} tính từ ${resolve(process.cwd())}`);
}

interface QuyTac {
  boChon: string[];
  than: string;
}

function tachQuyTac(css: string): QuyTac[] {
  const khongChuThich = css.replace(/\/\*[\s\S]*?\*\//g, '');
  const phang = khongChuThich.replace(/@media[^{]*\{([\s\S]*?)\n\}/g, '$1');
  const ra: QuyTac[] = [];
  const mau = /([^{}]+)\{([^{}]*)\}/g;
  let khop: RegExpExecArray | null;
  while ((khop = mau.exec(phang)) !== null) {
    ra.push({
      boChon: khop[1]
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
      than: khop[2],
    });
  }
  return ra;
}

const QT_ADMIN = tachQuyTac(docTuGocKho(ADMIN));
const QT_CONG = tachQuyTac(docTuGocKho(CONG));

/** Giá trị ĐÃ GIẢI của một thuộc tính, lấy từ quy tắc **cuối cùng** khớp bộ chọn (CSS thắng sau). */
function giaTri(quyTac: QuyTac[], boChon: string, thuocTinh: string): string | undefined {
  let ket: string | undefined;
  for (const qt of quyTac) {
    if (!qt.boChon.includes(boChon)) continue;
    const khop = new RegExp(`(?:^|[;{\\s])${thuocTinh}\\s*:\\s*([^;]+)`).exec(qt.than);
    if (khop) ket = khop[1].trim();
  }
  return ket;
}

/** Thẻ → những thuộc tính mà hai bên phải khai **cùng một giá trị**. */
const CAP_PHAI_KHOP: { the: string; thuocTinh: string[] }[] = [
  {
    the: 'h2',
    thuocTinh: ['font-size', 'font-weight', 'margin-top', 'margin-bottom', 'line-height'],
  },
  {
    the: 'h3',
    thuocTinh: ['font-size', 'font-weight', 'margin-top', 'margin-bottom', 'line-height'],
  },
  { the: 'h4', thuocTinh: ['font-size', 'font-weight', 'margin-top', 'margin-bottom'] },
  { the: 'p', thuocTinh: ['margin-top', 'margin-bottom'] },
  { the: 'li', thuocTinh: ['margin-top', 'margin-bottom'] },
];

describe('Xem trước khớp cổng — T41.17', () => {
  it('⚠ đọc được CẢ HAI tệp — bài chạy qua tập rỗng thì xanh mà ⛔ không so gì (luật 7)', () => {
    expect(QT_ADMIN.length).toBeGreaterThan(20);
    expect(QT_CONG.length).toBeGreaterThan(20);
    expect(QT_CONG.some((qt) => qt.boChon.includes('.sn-article'))).toBe(true);
    expect(QT_ADMIN.some((qt) => qt.boChon.includes('.sn-editor__body'))).toBe(true);
  });

  it('⭐⭐ mọi cặp chữ nghĩa khai CÙNG một giá trị ở hai tệp', () => {
    const lech: string[] = [];
    for (const { the, thuocTinh } of CAP_PHAI_KHOP) {
      for (const tt of thuocTinh) {
        const cong = giaTri(QT_CONG, `.sn-article ${the}`, tt);
        const admin = giaTri(QT_ADMIN, `.sn-editor__body ${the}`, tt);
        if (cong !== admin) {
          lech.push(`${the} { ${tt} }: cổng \`${cong ?? '—'}\` ≠ soạn thảo \`${admin ?? '—'}\``);
        }
      }
    }
    expect(
      lech,
      'Xem trước sẽ hiển thị khác trang công khai. Sửa `richTextEditor.css` cho khớp ' +
        '`article-content.css` — hoặc, nếu lệch là CÓ CHỦ ĐÍCH, gỡ cặp ấy khỏi `CAP_PHAI_KHOP` ' +
        'kèm một câu nói rõ vì sao.',
    ).toEqual([]);
  });

  it('⭐ phép so phân biệt được hai trạng thái — nếu không nó ⛔ không khẳng định gì (luật 9)', () => {
    // Bài trên chỉ có nghĩa nếu `giaTri` thật sự đọc ra giá trị, và trả `undefined` khi không có.
    expect(giaTri(QT_CONG, '.sn-article h2', 'font-size')).toMatch(/^[\d.]+rem$/);
    expect(giaTri(QT_ADMIN, '.sn-editor__body h2', 'font-size')).toMatch(/^[\d.]+rem$/);
    expect(giaTri(QT_CONG, '.sn-article h2', 'thuoc-tinh-khong-ton-tai')).toBeUndefined();
    expect(giaTri(QT_ADMIN, '.bo-chon-khong-ton-tai', 'font-size')).toBeUndefined();
  });

  it('⭐⭐ căn đều hai bên lề: Xem trước CÓ, khung đang gõ KHÔNG', () => {
    // ⚠ Cổng khai `justify` trên **KHUNG** `.sn-article`, ⛔ không trên `.sn-article p`. Bản đầu của
    //   bài kiểm này đoán là `p` — dựa vào một chú thích trong chính tệp ấy nói về độ ưu tiên của
    //   `.sn-article p` — và ĐỎ ngay lượt chạy đầu. Chú thích ấy mô tả một phương án đã CÂN NHẮC
    //   RỒI BỎ, không mô tả mã đang chạy. Đó đúng là lý do bài này đọc cả hai tệp thay vì tin lời
    //   người viết: một chú thích là dữ liệu chưa kiểm (§10.69).
    expect(giaTri(QT_CONG, '.sn-article', 'text-align')).toBe('justify');
    expect(giaTri(QT_ADMIN, '.sn-editor__preview', 'text-align')).toBe('justify');

    // ⛔ Nhưng KHÔNG ở khung đang soạn: mỗi ký tự gõ vào làm giãn lại cả dòng, con trỏ nhảy, và
    //    người soạn mất dấu chỗ mình đang đứng. Đây là vế phân biệt — thiếu nó thì khẳng định
    //    trên vẫn xanh khi ai đó tiện tay đặt `justify` cho cả `.sn-editor__body`.
    expect(giaTri(QT_ADMIN, '.sn-editor__body', 'text-align')).toBeUndefined();
  });

  it('tiêu đề ⛔ không căn đều ở cả hai phía — một dòng ngắn bị giãn trông như chữ vỡ', () => {
    for (const the of ['h2', 'h3', 'h4']) {
      expect(giaTri(QT_CONG, `.sn-article ${the}`, 'text-align'), the).toBe('left');
      expect(giaTri(QT_ADMIN, `.sn-editor__body ${the}`, 'text-align'), the).toBe('left');
    }
  });
});
