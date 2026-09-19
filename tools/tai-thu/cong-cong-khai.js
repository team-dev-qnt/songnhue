// =============================================================================
// NFR-02 — Cổng công khai: ≥ 200 người dùng đồng thời, trang chủ < 3s (T37.2 · T61.6)
//
//   Chạy:  docker run --rm -i -e BASE_URL=https://staging.songnhue.com \
//            grafana/k6:0.57.0 run - < tools/tai-thu/cong-cong-khai.js
//
// ⛔⛔ ĐỌC TRƯỚC KHI TIN MỘT CON SỐ XANH — tools/tai-thu/README.md §2
//
//   Mọi xô hạn mức mà cổng công khai chạm tới đều khoá theo IP (`PUBLIC` 300/phút ở RateLimitFilter ·
//   nginx `limit_req`/`limit_conn` — từ WS-72 hai chốt nginx trả 429, ⛔ còn 503). Bắn từ MỘT máy là
//   200 "người" chung MỘT IP ⇒ đo được là tốc độ trả 429, ⛔ phải hiệu năng. Kịch bản này vì vậy:
//     · đếm 429 thành chỉ số RIÊNG (`bi_chan_429`) và ĐỎ khi có bất kỳ lượt nào — một P95 nhanh
//       nhờ trả 429 sớm là một P95 nói dối;
//     · ⛔ nới hạn mức ở môi trường đo (tiền lệ T60.9: nới là tắt một cơ chế bảo mật thật).
//
//   ⚠ Trang TÌM KIẾM là chỗ hạn mức cắn NGƯỜI DÙNG THẬT chứ ⛔ chỉ cắn bài đo: mỗi từ khoá khác
//     nhau là một lượt `public-web` gọi backend từ IP CỦA CONTAINER — mọi khách chung một xô
//     PUBLIC 300/phút — và `lib/api.ts` biến 429 thành `null`. Trước T61.17 (WS-72) trang in
//     "Không tìm thấy…" trong im lặng; nay nó in khối "Chưa tra cứu được" mang dấu hiệu
//     `data-tra-cuu="khong-tra-loi"` ⇒ chỉ số `tim_kiem_khong_tra_loi`. `tim_kiem_rong` vẫn giữ:
//     từ khoá CÓ kết quả lúc không tải mà dưới tải lại "Không tìm thấy" là một khuyết tật KHÁC.
//
// ⭐ Chiều ĐỎ của kịch bản này đã được thử trên máy chủ giả — `tu-kiem/tu-kiem-tim-kiem-rong.sh`
//    (T63.21). Trước 18/09/2026 nó CHƯA từng được thử, nên `tim_kiem_rong: rate==0` là một ngưỡng
//    ⛔ ai biết có bắt được gì ⛔ (luật 1).
// =============================================================================
import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/+$/, '');
if (!BASE_URL) {
  fail('Thiếu BASE_URL (VD https://staging.songnhue.com) — ⛔ có giá trị mặc định: bắn nhầm production là sự cố.');
}
const DINH_VU = Number(__ENV.SO_NGUOI || 200);

/**
 * Hình dạng phiên của một người xem thật. Ba núm dưới đây CHỈ để lượt tự kiểm dựng lại được
 * một phiên trong vài chục giây; đổi chúng là đổi chính thứ đang đo, nên `setup()` in ra một
 * dòng cảnh báo khi giá trị ⛔ còn mặc định — một lượt chạy như vậy ⛔ phải số đo nghiệm thu.
 */
const TI_LE_TIM_KIEM = Number(__ENV.TI_LE_TIM_KIEM || 0.33);
const NGHI_MIN = Number(__ENV.NGHI_MIN || 2);
const NGHI_KHOANG = Number(__ENV.NGHI_KHOANG || 4);
const LA_TU_KIEM = TI_LE_TIM_KIEM !== 0.33 || NGHI_MIN !== 2 || NGHI_KHOANG !== 4;

export const options = {
  scenarios: {
    cong_cong_khai: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.LEN || '2m', target: DINH_VU },
        { duration: __ENV.GIU || '10m', target: DINH_VU },
        { duration: __ENV.HA || '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // NFR-02 — "trang chủ < 3s". P95 chứ ⛔ trung bình: trung bình giấu đuôi chậm.
    'http_req_duration{trang:trang-chu}': ['p(95)<3000'],
    // ⛔ Bất kỳ 429 nào cũng làm lượt đo MẤT GIÁ TRỊ — xem đầu tệp.
    bi_chan_429: ['rate==0'],
    tim_kiem_rong: ['rate==0'],
    // T61.17 (WS-72): backend ⛔ trả lời lúc dựng trang tìm kiếm (429 · 5xx) — xem đầu tệp.
    tim_kiem_khong_tra_loi: ['rate==0'],
    // ⛔⛔ Vế CHỐNG TẬP RỖNG (luật 7). `tim_kiem_rong` chỉ nhận mẫu khi `setup()` tìm được ít
    //     nhất một từ khoá CÓ kết quả; ⛔ có từ khoá nào thì nó đi qua một tập rỗng và XANH —
    //     xanh trong đúng tình huống *"vế tìm kiếm ⛔ được đo"*. Chỉ số này nhận mẫu ở MỌI lượt
    //     lặp, nên trạng thái ấy thành một dòng ĐỎ gọi đúng tên nó.
    thieu_moc_tim_kiem: ['rate==0'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const biChan429 = new Rate('bi_chan_429');
const timKiemRong = new Rate('tim_kiem_rong');
const timKiemKhongTraLoi = new Rate('tim_kiem_khong_tra_loi');
const thieuMocTimKiem = new Rate('thieu_moc_tim_kiem');
const kichThuocTrangChu = new Trend('trang_chu_byte');
const loiMayChu = new Counter('loi_5xx');

/** Ứng viên từ khoá — lấy ngẫu nhiên để phá đệm như người dùng thật. Ghi đè bằng TU_KHOA="a,b,c". */
const UNG_VIEN = (__ENV.TU_KHOA || 'sông,trạm bơm,cống,vận hành,thông báo,kế hoạch,nhuệ,thuỷ lợi')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);
const CHU_RONG = 'Không tìm thấy bài viết';
/**
 * Dấu hiệu CẤU TRÚC của khối "Chưa tra cứu được" — `THUOC_TINH_KHONG_TRA_LOI` ở
 * `frontend/public-web/src/lib/traCuu.ts`. `KichBanTaiThuTest` đối chiếu hai nơi (quy tắc 14).
 */
const DAU_KHONG_TRA_LOI = 'data-tra-cuu="khong-tra-loi"';

function goi(duong, trang) {
  const r = http.get(`${BASE_URL}${duong}`, { tags: { trang }, redirects: 0 });
  biChan429.add(r.status === 429);
  if (r.status >= 500) loiMayChu.add(1, { trang });
  return r;
}

/** Khoảng nghỉ giữa hai trang của cùng một người xem. */
function nghi() {
  sleep(NGHI_MIN + Math.random() * NGHI_KHOANG);
}

/**
 * ⭐ Đo MỐC trước khi có tải: từ khoá nào THẬT SỰ trả kết quả trên môi trường này.
 *
 * ⛔ Đoán "từ khoá này chắc có trong nội dung" là để `tim_kiem_rong` đỏ oan trên một staging ít bài.
 * Chỉ từ khoá có kết quả lúc KHÔNG tải mới được dùng — khi ấy "Không tìm thấy" dưới tải là triệu chứng
 * của 429 phía SSR, ⛔ phải của dữ liệu. Mốc đo ở đây, một lượt mỗi từ, ⛔ chạm hạn mức.
 */
export function setup() {
  if (LA_TU_KIEM) {
    console.warn(
      `⚠ Hình dạng phiên ⛔ còn mặc định (TI_LE_TIM_KIEM=${TI_LE_TIM_KIEM} · NGHI=${NGHI_MIN}+${NGHI_KHOANG}s) ` +
        '⇒ lượt này là TỰ KIỂM, ⛔ phải một số đo nghiệm thu. ⛔ ghi nó vào docs/nghiem-thu-nfr.md.',
    );
  }
  const coKetQua = UNG_VIEN.filter((q) => {
    const r = http.get(`${BASE_URL}/tim-kiem?q=${encodeURIComponent(q)}&moc=1`, { tags: { trang: 'moc' } });
    const than = r.body || '';
    // ⚠ Lượt mốc tự nó gặp 429 thì thân ⛔ có CHU_RONG — ⛔ được đếm nó là "có kết quả".
    return r.status === 200 && !than.includes(CHU_RONG) && !than.includes(DAU_KHONG_TRA_LOI);
  });
  console.log(`Mốc tìm kiếm: ${coKetQua.length}/${UNG_VIEN.length} từ khoá có kết quả — [${coKetQua.join(', ')}]`);
  if (coKetQua.length === 0) {
    console.warn(
      '⚠⚠ 0 từ khoá có kết quả ⇒ vế tìm kiếm ⛔ đo được gì trong lượt này. `thieu_moc_tim_kiem` sẽ ĐỎ — ' +
        'đó là CÂU TRẢ LỜI ĐÚNG, ⛔ phải một trục trặc của bộ đo: hãy truyền TU_KHOA có mặt trong nội dung thật.',
    );
  }
  return { tuKhoa: coKetQua };
}

export default function (moc) {
  // Đặt ở ĐẦU vòng lặp: chỉ số này phải có mẫu kể cả khi nhánh tìm kiếm bên dưới ⛔ chạy lần nào.
  thieuMocTimKiem.add(moc.tuKhoa.length === 0);

  const chu = goi('/', 'trang-chu');
  kichThuocTrangChu.add(chu.body ? chu.body.length : 0);
  check(chu, {
    'trang chủ 200': (r) => r.status === 200,
    // ⚠ Trang lỗi cũng trả nhanh — đòi thân đủ lớn để ⛔ đo tốc độ của một trang hỏng.
    'trang chủ có nội dung (> 20 KB)': (r) => (r.body ? r.body.length : 0) > 20_000,
  });
  nghi();

  const mucNuoc = goi('/quan-ly-van-hanh/muc-nuoc-luong-mua', 'muc-nuoc');
  check(mucNuoc, { 'mực nước 200': (r) => r.status === 200 });
  nghi();

  // Một phần ba lượt có tìm kiếm — con số ƯỚC LƯỢNG, ⛔ đo từ người dùng thật.
  if (moc.tuKhoa.length > 0 && Math.random() < TI_LE_TIM_KIEM) {
    const q = moc.tuKhoa[Math.floor(Math.random() * moc.tuKhoa.length)];
    const tim = goi(`/tim-kiem?q=${encodeURIComponent(q)}`, 'tim-kiem');
    check(tim, { 'tìm kiếm 200': (r) => r.status === 200 });
    // Từ khoá này CÓ kết quả lúc không tải (setup). Dưới tải:
    //   · "Chưa tra cứu được" ⇒ backend ⛔ trả lời lúc dựng trang (429 phía SSR) — T61.17;
    //   · "Không tìm thấy"    ⇒ backend TRẢ LỜI rỗng cho một từ khoá vốn có kết quả — khuyết tật khác.
    const thanTim = tim.body || '';
    timKiemKhongTraLoi.add(tim.status === 200 && thanTim.includes(DAU_KHONG_TRA_LOI));
    timKiemRong.add(tim.status === 200 && thanTim.includes(CHU_RONG));
    nghi();
  }
}
