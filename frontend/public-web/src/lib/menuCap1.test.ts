import { describe, expect, it } from 'vitest';

import { menuCap1KeTiep, type MucDangMo, type SuKienMenuCap1 } from './menuCap1';

/**
 * **Menu con của thanh điều hướng — không bao giờ được có hai cái cùng mở.**
 *
 * <h2>Bài này canh cái gì, và KHÔNG canh cái gì (luật 28)</h2>
 *
 * Canh: <b>máy trạng thái</b> quyết định mục nào đang mở. Đó là chỗ sự cố 01/09 nằm sau khi hai
 * lớp CSS bị gỡ, và là chỗ ba cái bẫy đã đo được (chạm, so nhãn, đảo trạng thái) sống.
 *
 * <p><b>Không</b> canh: rằng {@code PortalNav.tsx} thật sự gọi hàm này, và rằng không ai thêm lại
 * {@code group-hover:visible} vào lớp CSS của menu con. Bài {@code thanhDieuHuongMotNguon.test.ts}
 * lo hai việc ấy — nó đọc văn bản tệp, vì kho này cố ý không dựng DOM
 * ({@code vitest.config.mts}), nên không bài nào ở đây dựng được một menu thật để bấm.
 *
 * <p>⛔ Hai bài cộng lại vẫn <b>không</b> chứng minh "menu chạy đúng trên trình duyệt". Chúng
 * chứng minh đúng một điều: <i>hai nguyên nhân của sự cố 01/09 chưa quay lại</i>.
 */

/** Chạy một chuỗi sự kiện từ trạng thái đóng — đọc ra như kịch bản người dùng thật. */
function chay(...cacSuKien: SuKienMenuCap1[]): MucDangMo {
  return cacSuKien.reduce<MucDangMo>((dangMo, e) => menuCap1KeTiep(dangMo, e), null);
}

const CHUOT = 'mouse';
const CHAM = 'touch';

describe('menuCap1KeTiep — một nguồn sự thật cho menu con cấp 1', () => {
  it('⭐ KỊCH BẢN QUANTRAN BÁO: bấm mục A rồi rê chuột sang mục B → chỉ MỘT mục mở', () => {
    // Đây là nguyên văn lời báo: "click 1 item, dropdown không disappear, hover item khác thì
    // 2 dropdown cùng hiển thị chồng lên nhau". Với một biến `string | null`, trạng thái "hai
    // mục cùng mở" không biểu diễn được — nên phép khẳng định là: sau chuỗi ấy, mục đang mở là
    // B và CHỈ B.
    const sau = chay(
      { loai: 'bam', nhan: 'Giới thiệu' },
      { loai: 'contro-ra', nhan: 'Giới thiệu', loaiContro: CHUOT },
      { loai: 'contro-vao', nhan: 'Quản lý, vận hành', loaiContro: CHUOT },
    );
    expect(sau).toBe('Quản lý, vận hành');
  });

  it('⭐ và nếu con trỏ KHÔNG kịp rời mục A (đi tắt qua khe giữa hai mục) vẫn chỉ một mục mở', () => {
    // Trường hợp xấu nhất: trình duyệt nuốt mất `pointerleave` của A. Kiểu dữ liệu vẫn cứu —
    // `contro-vao` của B GHI ĐÈ, không cộng thêm.
    const sau = chay(
      { loai: 'bam', nhan: 'Giới thiệu' },
      { loai: 'contro-vao', nhan: 'Quản lý, vận hành', loaiContro: CHUOT },
    );
    expect(sau).toBe('Quản lý, vận hành');
  });

  describe('bẫy 1 — máy tính bảng: chạm KHÔNG được mở rồi tự đóng', () => {
    it('chạm vào mục NONE mở menu con (pointerenter của cảm ứng bị bỏ qua)', () => {
      // Trên cảm ứng, một cú chạm bắn `pointerenter` rồi `click`. Nếu `contro-vao` cũng mở thì
      // `bam` ngay sau đó đảo lại thành đóng — nút bấm không phản hồi, đúng lỗi bản 28/08 sửa.
      const sau = chay(
        { loai: 'contro-vao', nhan: 'Giới thiệu', loaiContro: CHAM },
        { loai: 'bam', nhan: 'Giới thiệu' },
      );
      expect(sau).toBe('Giới thiệu');
    });

    it('chạm lần thứ hai đóng lại — cảm ứng không có đường nào khác', () => {
      const sau = chay(
        { loai: 'contro-vao', nhan: 'Giới thiệu', loaiContro: CHAM },
        { loai: 'bam', nhan: 'Giới thiệu' },
        { loai: 'bam', nhan: 'Giới thiệu' },
      );
      expect(sau).toBeNull();
    });

    it('con trỏ cảm ứng rời mục KHÔNG đóng menu — nếu không, menu tắt ngay khi nhấc ngón tay', () => {
      const sau = chay(
        { loai: 'contro-vao', nhan: 'Giới thiệu', loaiContro: CHAM },
        { loai: 'bam', nhan: 'Giới thiệu' },
        { loai: 'contro-ra', nhan: 'Giới thiệu', loaiContro: CHAM },
      );
      expect(sau).toBe('Giới thiệu');
    });
  });

  describe('bẫy 2 — rời mục A không được đóng menu của mục B', () => {
    it('con trỏ rời A trong khi B đang mở → B vẫn mở', () => {
      // Bản trước truyền `datMoCap1(null)` trần cho `onMouseLeave`, nên bất kỳ mục nào bị rời
      // cũng xoá trạng thái chung. Đo được: rê ngang qua thanh làm menu đang mở tắt oan.
      expect(
        menuCap1KeTiep('Tin tức', { loai: 'contro-ra', nhan: 'Giới thiệu', loaiContro: CHUOT }),
      ).toBe('Tin tức');
    });

    it('focus rời A trong khi B đang mở → B vẫn mở', () => {
      expect(menuCap1KeTiep('Tin tức', { loai: 'roi-focus', nhan: 'Giới thiệu' })).toBe('Tin tức');
    });

    it('nhưng rời ĐÚNG mục đang mở thì phải đóng — nếu không, menu không có đường tắt', () => {
      expect(
        menuCap1KeTiep('Tin tức', { loai: 'contro-ra', nhan: 'Tin tức', loaiContro: CHUOT }),
      ).toBeNull();
      expect(menuCap1KeTiep('Tin tức', { loai: 'roi-focus', nhan: 'Tin tức' })).toBeNull();
    });
  });

  describe('bẫy 3 — bàn phím phải mở được, kể cả khi :focus-visible không dùng được', () => {
    it('focus bàn phím mở menu con', () => {
      expect(menuCap1KeTiep(null, { loai: 'focus-ban-phim', nhan: 'Giới thiệu' })).toBe(
        'Giới thiệu',
      );
    });

    it('ArrowDown mở menu con — đường không phụ thuộc vị từ :focus-visible', () => {
      // `laFocusBanPhim` rơi về `false` khi trình duyệt không hiểu `:focus-visible`. Bài này
      // chứng minh vẫn còn MỘT lối vào bàn phím trong trường hợp đó.
      expect(menuCap1KeTiep(null, { loai: 'mui-ten-xuong', nhan: 'Giới thiệu' })).toBe(
        'Giới thiệu',
      );
    });

    it('ArrowDown trên mục ĐANG mở giữ nguyên, không đảo thành đóng', () => {
      expect(menuCap1KeTiep('Giới thiệu', { loai: 'mui-ten-xuong', nhan: 'Giới thiệu' })).toBe(
        'Giới thiệu',
      );
    });
  });

  it('điều hướng / Esc / bấm ra ngoài đóng hết', () => {
    expect(menuCap1KeTiep('Giới thiệu', { loai: 'dong-het' })).toBeNull();
    expect(menuCap1KeTiep(null, { loai: 'dong-het' })).toBeNull();
  });

  /**
   * ⭐ Khẳng định về **SỐ LƯỢNG**, không chia sẻ giả định nào với các bài ở trên (luật 29).
   *
   * §10.62 đã cho thấy một bài kiểm chứng ngược sai được theo đúng cách thứ nó kiểm đang sai.
   * Bài dưới đây không hỏi "trạng thái sau chuỗi này là gì" mà hỏi <b>"có bao nhiêu mục mở"</b>
   * — đúng câu QuanTran hỏi, và là câu mà mọi bài ở trên đều <i>giả định</i> câu trả lời.
   */
  it('duyệt CẠN mọi chuỗi 3 sự kiện trên 2 mục — không chuỗi nào cho ra quá một mục mở', () => {
    const NHAN = ['Giới thiệu', 'Quản lý, vận hành'];
    const bang: SuKienMenuCap1[] = NHAN.flatMap((nhan) => [
      { loai: 'contro-vao', nhan, loaiContro: CHUOT },
      { loai: 'contro-vao', nhan, loaiContro: CHAM },
      { loai: 'contro-ra', nhan, loaiContro: CHUOT },
      { loai: 'contro-ra', nhan, loaiContro: CHAM },
      { loai: 'bam', nhan },
      { loai: 'focus-ban-phim', nhan },
      { loai: 'roi-focus', nhan },
      { loai: 'mui-ten-xuong', nhan },
    ]);
    bang.push({ loai: 'dong-het' });

    let soChuoi = 0;
    for (const a of bang)
      for (const b of bang)
        for (const c of bang) {
          soChuoi += 1;
          const ketQua = chay(a, b, c);
          // Kiểu `string | null` chỉ giữ được MỘT nhãn — đó chính là bảo đảm. Khẳng định ở đây
          // là kết quả luôn nằm trong tập hợp lệ, không bao giờ là một cấu trúc gộp.
          expect(ketQua === null || NHAN.includes(ketQua)).toBe(true);
        }

    // ⛔ Chống xanh trên tập rỗng (luật 7): 17³ = 4913. Không có dòng này thì một `bang` rỗng
    //    vẫn cho bài kiểm xanh trọn vẹn mà không duyệt chuỗi nào.
    expect(bang).toHaveLength(17);
    expect(soChuoi).toBe(4913);
  });
});
