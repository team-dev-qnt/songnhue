#!/usr/bin/env node
// =============================================================================
// Máy chủ GIẢ cho lượt tự kiểm kịch bản tải — ⛔ phụ thuộc, ⛔ chạm máy chủ thật.
//
// Vì sao tệp này tồn tại (T63.21):
//
//   `tools/tai-thu/README.md` §3 tự khai — từ 14/09/2026 — rằng vế `tim_kiem_rong`
//   **chưa thử chiều ĐỎ**: máy chủ giả của lượt ấy ⛔ trả trang rỗng lần nào, nên
//   ngưỡng `tim_kiem_rong: rate==0` đi qua một **tập rỗng** và xanh trọn vẹn. Luật 7
//   nói thẳng: một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai.
//
//   Máy chủ giả trước đó sống trong `/tmp` rồi biến mất cùng phiên làm việc. Một lượt
//   kiểm chứng ⛔ chạy lại được thì lượt sau phải tin vào một dòng ghi chép — mà dự án
//   này đã mười lần đo ra rằng **một dòng sổ cũng là dữ liệu chưa kiểm**.
//
// Bốn chế độ, mỗi chế độ dựng ĐÚNG MỘT trạng thái mà kịch bản phải phân biệt được:
//
//   binh-thuong      mọi lượt tìm kiếm có kết quả            ⇒ k6 phải thoát 0
//   rong-duoi-tai    mốc CÓ kết quả, lượt dưới tải thì RỖNG  ⇒ `tim_kiem_rong` ĐỎ
//   moc-rong         ⛔ từ khoá nào có kết quả, kể cả lúc mốc ⇒ `thieu_moc_tim_kiem` ĐỎ
//   chan-429         mọi lượt trả 429                        ⇒ `bi_chan_429` ĐỎ
//
// Máy giả này CŨNG là đích của lượt tự kiểm bộ đo LCP (T63.22): nó trả đầu
// `x-nextjs-cache` và nhận `POST /api/revalidate` y như `public-web` thật, nên bài đo có thể
// chứng minh nó phân biệt được **lượt ẤM** với **lượt NGUỘI** mà ⛔ cần dựng cả stack.
//
// Chạy:  CHE_DO=rong-duoi-tai CONG=18099 node tools/tai-thu/tu-kiem/may-chu-gia.js
// =============================================================================
'use strict';

const http = require('node:http');

const CHE_DO = process.env.CHE_DO || 'binh-thuong';
const CONG = Number(process.env.CONG || 18099);
const HOP_LE = ['binh-thuong', 'rong-duoi-tai', 'moc-rong', 'chan-429'];
if (!HOP_LE.includes(CHE_DO)) {
  console.error(`CHE_DO='${CHE_DO}' ⛔ hợp lệ. Chọn một trong: ${HOP_LE.join(' · ')}`);
  process.exit(2);
}

/** Câu mà `tim-kiem/page.tsx` in ra khi ⛔ có kết quả — kịch bản k6 dò đúng chuỗi này. */
const CHU_RONG = 'Không tìm thấy bài viết';

/**
 * ⚠ Kịch bản khẳng định `trang chủ có nội dung (> 20 KB)` — cố ý, vì một trang LỖI cũng
 * trả nhanh và một P95 đo trên trang lỗi là một P95 nói dối. Máy chủ giả vì vậy phải trả
 * một thân THẬT SỰ lớn hơn 20 KB, ⛔ phải một chuỗi ngắn.
 */
const THAN_TRANG_CHU = (() => {
  const khoi = '<article><h2>Thông báo vận hành</h2><p>' + 'x'.repeat(400) + '</p></article>\n';
  // ⚠ `<h1>` cỡ lớn là ứng viên LCP rõ ràng — ⛔ có phần tử nào vẽ ra thì trình duyệt ⛔ phát
  //   một `largest-contentful-paint` nào, và bộ đo sẽ đọc `null`. Đó là trạng thái mà bài tự kiểm
  //   dựng riêng bằng `about:blank`, ⛔ phải trạng thái của một trang thật.
  const tieuDe = '<h1 style="font-size:64px;margin:0">Công ty Thuỷ lợi Sông Nhuệ (giả)</h1>';
  return `<!doctype html><html lang="vi"><head><title>Cổng giả</title></head><body>\n${tieuDe}\n${khoi.repeat(60)}</body></html>`;
})();

function tra(res, ma, than, dauThem) {
  res.writeHead(ma, { 'Content-Type': 'text/html; charset=utf-8', ...(dauThem || {}) });
  res.end(than);
}

/**
 * Trạng thái đệm ISR — bật lên sau một lượt `POST /api/revalidate`, tắt ngay ở lượt dựng kế tiếp.
 * Đó đúng vòng đời mà `revalidatePath('/')` của Next tạo ra, và là thứ DUY NHẤT chứng minh một lượt
 * đo là *ISR NGUỘI* chứ ⛔ phải một lượt đọc lại bản đã dựng sẵn (`DOD4.5`).
 */
let demNguoi = false;

const may = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://noi-bo');

  if (CHE_DO === 'chan-429') {
    return tra(res, 429, '<html><body>Quá nhiều yêu cầu</body></html>');
  }

  if (url.pathname === '/tim-kiem') {
    const laMoc = url.searchParams.get('moc') === '1';
    // `moc-rong`: rỗng ở MỌI lượt ⇒ `setup()` loại hết từ khoá ⇒ vế tìm kiếm ⛔ được đo.
    // `rong-duoi-tai`: mốc có kết quả, lượt dưới tải rỗng ⇒ đúng triệu chứng 429 phía SSR
    //                  mà `lib/api.ts` biến thành `null` trong im lặng.
    const rong = CHE_DO === 'moc-rong' || (CHE_DO === 'rong-duoi-tai' && !laMoc);
    const than = rong
      ? `<html><body><p>${CHU_RONG} nào khớp từ khoá.</p></body></html>`
      : '<html><body><ul><li>Bài viết khớp từ khoá</li><li>Bài viết khác</li></ul></body></html>';
    return tra(res, 200, than);
  }

  if (url.pathname === '/api/revalidate') {
    if (req.method !== 'POST') return tra(res, 405, '');
    // Mô phỏng đúng `route.ts` thật: thiếu bí mật thì 401, ⛔ phải 200-rỗng.
    if (!req.headers['x-revalidate-secret']) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      return res.end('{"error":"Không được phép"}');
    }
    demNguoi = true;
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end('{"revalidated":true,"path":"/"}');
  }

  if (url.pathname === '/') {
    if (demNguoi) {
      demNguoi = false;
      // Lượt dựng lại tốn thời gian thật — ⛔ có độ trễ thì hai trạng thái ⛔ phân biệt được bằng
      // bất kỳ số đo nào, và bài tự kiểm sẽ xanh vì lý do sai (luật 9).
      return setTimeout(() => tra(res, 200, THAN_TRANG_CHU, { 'x-nextjs-cache': 'MISS' }), 250);
    }
    return tra(res, 200, THAN_TRANG_CHU, { 'x-nextjs-cache': 'HIT' });
  }

  // Mọi đường dẫn còn lại của kịch bản (mực nước…) trả 200 với thân ngắn.
  return tra(res, 200, '<html><body><h1>Trang giả</h1></body></html>');
});

may.listen(CONG, () => {
  console.log(`Máy chủ giả: chế độ '${CHE_DO}' — http://127.0.0.1:${CONG}`);
});
