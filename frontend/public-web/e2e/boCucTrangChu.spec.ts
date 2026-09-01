import { expect, test, type Page } from '@playwright/test';

/**
 * Bố cục trang chủ — đo bằng HỘP THẬT, không đọc chuỗi class.
 *
 * Xem `playwright.config.ts` để biết vì sao bộ này tồn tại và nó KHÔNG chạy ở đâu.
 *
 * <h2>Nguyên tắc viết khẳng định ở đây</h2>
 *
 * Mỗi khẳng định phải **phân biệt được hai trạng thái** (luật 9). Khẳng định "trang có hiển
 * thị" thì xanh cả khi bố cục vỡ; khẳng định "hộp A không giao hộp B" thì chỉ xanh khi đúng
 * cái đã hỏng đã hết hỏng.
 */

/** Bốn bề rộng phải chạy đúng. Chọn theo thiết bị thật, không theo điểm ngắt của Tailwind. */
const BE_RONG = [
  { ten: 'desktop rộng', width: 1920, height: 1080 },
  { ten: 'laptop phổ thông', width: 1366, height: 768 },
  { ten: 'máy tính bảng dọc', width: 768, height: 1024 },
  { ten: 'điện thoại', width: 375, height: 812 },
] as const;

type Hop = { top: number; bottom: number; left: number; right: number; w: number; h: number };

async function hopCua(page: Page, selector: string): Promise<Hop | null> {
  return page.evaluate((sel) => {
    const el = document.querySelector(sel);
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { top: r.top, bottom: r.bottom, left: r.left, right: r.right, w: r.width, h: r.height };
  }, selector);
}

/**
 * Mép dưới THẬT SỰ ĐƯỢC VẼ của một khối — lấy `bottom` lớn nhất trong toàn bộ cây con.
 *
 * <h2>⚠⚠ Vì sao không dùng thẳng `hopCua(...).bottom` — lượt viết đầu của bài này đã sai đúng ở đây</h2>
 *
 * `getBoundingClientRect()` trả **hộp viền** của chính phần tử, và hộp ấy **không bao gồm phần
 * con tràn ra ngoài**. Mà tràn ra ngoài đúng là cơ chế của lỗi đang đo: `max-height` kẹp cái
 * hộp, hàng lưới vẫn cao hơn, con vẽ đè xuống khối dưới. So hai hộp viền với nhau thì chúng
 * **không bao giờ giao nhau** — lượt chạy đầu in ra `chồng lấn = -36.0px` ở CẢ BỐN bề rộng,
 * xanh trọn vẹn, trên đúng bản mã đã dựng ra ảnh chụp màn hình QuanTran gửi.
 *
 * <p>Đó là luật 9 nguyên văn: *một khẳng định không phân biệt được hai trạng thái thì không
 * khẳng định gì*. Thứ phải đo là **mực vẽ**, không phải hộp khai báo.
 *
 * <h2>⚠⚠ Và cũng không phải mọi con — con bị CẮT thì không được vẽ</h2>
 *
 * Lượt sửa đầu của hàm này lấy `bottom` lớn nhất trong cây con, và báo **22,9px chồng lấn**
 * trên một bản mã KHÔNG hề chồng. Thủ phạm là các thẻ `<article>` nằm trong vùng cuộn của cột
 * tin: chúng có `bottom` vượt xa khung, nhưng tổ tiên mang `overflow-y: auto` **cắt** chúng —
 * người dùng không nhìn thấy gì cả.
 *
 * <p>Nên phép đo phải kẹp `bottom` của mỗi con vào `bottom` của mọi tổ tiên có `overflow`
 * khác `visible`. Hai lượt sai liên tiếp của chính hàm này là ví dụ sống của luật 29: bài
 * kiểm chứng ngược do cùng một người viết mang cùng một giả định — lần đầu quá lỏng (so hộp
 * với hộp), lần sau quá chặt (đếm cả phần bị cắt). Thứ cứu được là **đi soi từng phần tử thò
 * ra và hỏi nó có thật sự được vẽ không**, chứ không phải chỉnh con số ngưỡng cho vừa.
 */
async function mepDuoiThucTe(page: Page, selector: string): Promise<number | null> {
  return page.evaluate((sel) => {
    const goc = document.querySelector(sel);
    if (!goc) return null;

    /** `bottom` sau khi bị mọi tổ tiên cắt — `null` nếu bị cắt mất hoàn toàn. */
    const duoiSauKhiCat = (el: Element): number => {
      let duoi = el.getBoundingClientRect().bottom;
      let cha = el.parentElement;
      while (cha && cha !== goc.parentElement) {
        const kieu = getComputedStyle(cha);
        if (kieu.overflowY !== 'visible' || kieu.overflowX !== 'visible') {
          duoi = Math.min(duoi, cha.getBoundingClientRect().bottom);
        }
        cha = cha.parentElement;
      }
      return duoi;
    };

    let duoi = goc.getBoundingClientRect().bottom;
    for (const con of goc.querySelectorAll('*')) {
      const r = con.getBoundingClientRect();
      // Bỏ qua phần tử không chiếm chỗ (ẩn, thước đo) — chúng trả hộp 0×0 ở gốc toạ độ.
      if (r.width === 0 && r.height === 0) continue;
      const d = duoiSauKhiCat(con);
      if (d > duoi) duoi = d;
    }
    return duoi;
  }, selector);
}

const NHOM1 = 'section[aria-label="Ảnh hoạt động và tin tức"]';
const CHUYEN_MUC = 'section[aria-label="Tin theo chuyên mục"]';
/** Nhóm 5 — khối "Video giới thiệu": khung video bên trái, slider ảnh bên phải. */
const KHUNG_VIDEO = '[data-khung-video]';
const KHUNG_ANH_NHOM5 = 'section[aria-label="Ảnh thư viện của Công ty"] [data-khung-anh]';

test.describe('Trang chủ — bố cục đo trên trình duyệt thật', () => {
  test('⛔ TIỀN ĐỀ: trang phải có đủ nội dung, nếu không mọi phép đo dưới đây đo tập rỗng', async ({
    page,
  }) => {
    // Luật 7. Cột tin ít bài thì không đủ cao để tràn, và phép đo chồng lấn sẽ XANH vì
    // không có gì để chồng — đúng hình dạng "phép kiểm chạy qua tập rỗng vẫn xanh trọn vẹn".
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto('/', { waitUntil: 'networkidle' });

    const soBai = await page.locator('a[href*="/bai-viet/"]').count();
    expect(
      soBai,
      'trang chủ phải có bài viết thật — chạy `make seed-portal` trước',
    ).toBeGreaterThanOrEqual(8);

    expect(await hopCua(page, NHOM1), 'không tìm thấy Nhóm 1').not.toBeNull();
    expect(
      await hopCua(page, CHUYEN_MUC),
      'không tìm thấy khối Tin theo chuyên mục',
    ).not.toBeNull();
  });

  for (const bp of BE_RONG) {
    test.describe(`${bp.ten} (${bp.width}×${bp.height})`, () => {
      test.beforeEach(async ({ page }) => {
        await page.setViewportSize({ width: bp.width, height: bp.height });
        await page.goto('/', { waitUntil: 'networkidle' });
      });

      test('⭐ Nhóm 1 KHÔNG chồng lên khối Tin theo chuyên mục', async ({ page }) => {
        // ⚠ `mepDuoiThucTe`, KHÔNG phải `hopCua(...).bottom` — xem javadoc của hàm ấy: hộp
        //   viền không chứa phần con tràn ra, nên so hộp với hộp là xanh vĩnh viễn.
        const duoiNhom1 = await mepDuoiThucTe(page, NHOM1);
        const sau = await hopCua(page, CHUYEN_MUC);
        expect(duoiNhom1, 'không tìm thấy Nhóm 1').not.toBeNull();
        expect(sau, 'không tìm thấy khối Tin theo chuyên mục').not.toBeNull();

        const hopNhom1 = await hopCua(page, NHOM1);
        const chongLan = duoiNhom1! - sau!.top;
        // In số đo ra để lượt kiểm chứng ngược có con số đối chiếu, không chỉ có xanh/đỏ.
        console.log(
          `    [${bp.ten}] hộp Nhóm 1 đáy = ${hopNhom1!.bottom.toFixed(1)}px · mực VẼ thật = ` +
            `${duoiNhom1!.toFixed(1)}px · đỉnh Chuyên mục = ${sau!.top.toFixed(1)}px · ` +
            `chồng lấn = ${chongLan.toFixed(1)}px`,
        );
        expect(
          chongLan,
          `Nhóm 1 vẽ tràn xuống và đè lên khối dưới ${chongLan.toFixed(1)}px`,
        ).toBeLessThanOrEqual(0.5);
      });

      test('⭐ khung ảnh slider đúng tỉ lệ 16:9', async ({ page }) => {
        const khung = await hopCua(page, `${NHOM1} [data-khung-anh]`);
        expect(khung, 'không tìm thấy khung ảnh slider').not.toBeNull();

        const tiLe = khung!.w / khung!.h;
        console.log(
          `    [${bp.ten}] khung ảnh = ${khung!.w.toFixed(1)}×${khung!.h.toFixed(1)} ⇒ tỉ lệ ${tiLe.toFixed(3)}`,
        );
        expect(tiLe, `tỉ lệ ${tiLe.toFixed(3)} lệch khỏi 16/9`).toBeCloseTo(16 / 9, 1);
      });

      test('⭐⭐ Nhóm 5: khung video và khung ảnh TRÙNG KHÍT', async ({ page }) => {
        // Yêu cầu QuanTran 01/09 đợt hai: *"fix cứng chiều rộng của phần slider so với video
        // giới thiệu"*. Bản trước để hai cột 7/12 và 5/12 rồi đặt CÙNG `aspect-[16/9]` cho cả
        // hai, kèm chú thích *"hai khối cùng tỉ lệ thì mép trên thẳng hàng"* — đúng mép TRÊN,
        // và chính vì cùng tỉ lệ mà mép DƯỚI **buộc phải** lệch: hai bề rộng khác nhau nhân
        // cùng một tỉ lệ thì ra hai chiều cao khác nhau. Đo được 114,3px ở ≥1280px.
        const video = await hopCua(page, KHUNG_VIDEO);
        const anh = await hopCua(page, KHUNG_ANH_NHOM5);
        if (!video || !anh) {
          // Video chưa cấu hình (`site.home.video-id` rỗng) hoặc thư viện chưa có ảnh ⇒ một
          // trong hai khung không được dựng. Nói thẳng là chưa đo được, KHÔNG xanh im lặng:
          // một phép đo chạy qua tập rỗng vẫn xanh trọn vẹn (luật 7).
          throw new Error(
            `Nhóm 5 chưa đủ hai khung để đo (video=${video !== null}, ảnh=${anh !== null}) — ` +
              'đặt `site.home.video-id` và `site.home.photos-folder` rồi chạy lại',
          );
        }

        const lechRong = Math.abs(video.w - anh.w);
        const lechCao = Math.abs(video.h - anh.h);
        console.log(
          `    [${bp.ten}] video ${video.w.toFixed(1)}×${video.h.toFixed(1)} · ` +
            `ảnh ${anh.w.toFixed(1)}×${anh.h.toFixed(1)} ⇒ ` +
            `lệch rộng ${lechRong.toFixed(1)}px · lệch cao ${lechCao.toFixed(1)}px`,
        );

        // ⚠ Biên ±3px chứ không ±0: khung video nằm trong một thẻ có `border` 1px mỗi bên, nên
        //   hộp trong của nó luôn hẹp hơn ô lưới đúng 2px — và 2px ấy kéo theo ~1,1px chiều cao
        //   qua tỉ lệ 16/9. Đòi bằng nhau tuyệt đối là đòi bỏ viền.
        expect(
          lechRong,
          'hai khung khác bề rộng ⇒ hai cột không cùng số ô lưới',
        ).toBeLessThanOrEqual(3);
        expect(
          lechCao,
          `đáy ảnh lệch đáy video ${lechCao.toFixed(1)}px — đúng lỗi QuanTran báo`,
        ).toBeLessThanOrEqual(3);
      });

      test('⭐ trang không cuộn ngang', async ({ page }) => {
        const { scrollWidth, clientWidth } = await page.evaluate(() => ({
          scrollWidth: document.documentElement.scrollWidth,
          clientWidth: document.documentElement.clientWidth,
        }));
        console.log(`    [${bp.ten}] scrollWidth=${scrollWidth} clientWidth=${clientWidth}`);
        expect(scrollWidth, 'trang tràn ngang').toBeLessThanOrEqual(clientWidth + 1);
      });

      test('⭐ ô tìm kiếm mở ra nằm TRÊN CHÍNH HÀNG MENU, không phải một hàng riêng', async ({
        page,
      }) => {
        const nut = page.getByRole('button', { name: /Tìm kiếm trên cổng thông tin/ });
        await expect(nut).toBeVisible();

        const hangTruoc = await hopCua(page, '[data-hang-nav]');
        await nut.click();

        const oNhap = page.locator('input[name="q"]');
        await expect(oNhap).toBeVisible();

        const hopO = await hopCua(page, 'input[name="q"]');
        const hang = await hopCua(page, '[data-hang-nav]');
        console.log(
          `    [${bp.ten}] hàng menu ${hang!.top.toFixed(1)}→${hang!.bottom.toFixed(1)} · ` +
            `ô nhập ${hopO!.top.toFixed(1)}→${hopO!.bottom.toFixed(1)} · ` +
            `hàng cao ${hangTruoc!.h.toFixed(1)}→${hang!.h.toFixed(1)}px · ô rộng ${hopO!.w.toFixed(1)}px`,
        );

        // ⚠⚠ So với HÀNG MENU (`[data-hang-nav]`), không với `<nav>`. Lượt viết đầu so với
        //    `<nav>` và XANH trên bản đang đặt ô tìm kiếm ở một hàng RIÊNG bên dưới — vì hàng
        //    riêng ấy cũng nằm trong `<nav>`. Một khẳng định đúng với cả hai cách dựng thì
        //    không phân biệt được cách nào đang chạy (luật 9).
        expect(
          hopO!.top,
          'ô nhập nằm DƯỚI hàng menu ⇒ vẫn là một hàng riêng, không phải mở trên thanh nav',
        ).toBeLessThan(hangTruoc!.bottom);
        expect(hopO!.bottom, 'ô nhập tràn xuống dưới hàng menu').toBeLessThanOrEqual(
          hang!.bottom + 1,
        );
        expect(hopO!.w, 'ô nhập quá hẹp để gõ').toBeGreaterThan(100);
      });

      test('⭐⭐ ô tìm kiếm KHÔNG che mất điều hướng khi còn chỗ', async ({ page }) => {
        // Yêu cầu QuanTran 01/09 lượt hai. Bản lượt một ẩn menu ở MỌI bề rộng, kể cả khi còn
        // 280px trống — đó là "thẻ dàn trải che mất navigation".
        const menuTruoc = await page.evaluate(() => {
          const ul = document.querySelector('[data-hang-nav] ul');
          return ul ? getComputedStyle(ul).display !== 'none' : false;
        });

        await page.getByRole('button', { name: /Tìm kiếm trên cổng thông tin/ }).click();
        await page.waitForTimeout(150);

        const sau = await page.evaluate(() => {
          const hang = document.querySelector('[data-hang-nav]') as HTMLElement;
          const ul = hang.querySelector('ul');
          const form = hang.querySelector('form[role="search"]');
          return {
            menuHien: ul ? getComputedStyle(ul).display !== 'none' : false,
            formRong: form ? Math.round(form.getBoundingClientRect().width) : 0,
            menuRong: ul ? Math.round(ul.getBoundingClientRect().width) : 0,
            hangRong: Math.round(hang.getBoundingClientRect().width),
          };
        });
        console.log(
          `    [${bp.ten}] menu trước=${menuTruoc} sau=${sau.menuHien} · ` +
            `menu ${sau.menuRong}px + form ${sau.formRong}px trong hàng ${sau.hangRong}px`,
        );

        if (menuTruoc) {
          // Thanh ngang đang hiện ⇒ mở tìm kiếm KHÔNG được làm nó biến mất, và ô nhập chỉ
          // được lấy phần CÒN LẠI: hai khối cộng lại phải nằm gọn trong hàng.
          expect(sau.menuHien, 'menu bị ẩn dù thanh ngang đang hiện ⇒ che mất điều hướng').toBe(
            true,
          );
          expect(
            sau.menuRong + sau.formRong,
            'menu + ô nhập vượt quá bề rộng hàng ⇒ thanh bị tràn',
          ).toBeLessThanOrEqual(sau.hangRong + 1);
        } else {
          // Ngăn kéo (mobile) — QuanTran yêu cầu GIỮ hành vi hoán đổi ở đây.
          expect(sau.menuHien).toBe(false);
        }
      });

      test('⭐ ô nhập không còn vòng focus hình CHỮ NHẬT — dấu hiệu nằm trên pill bo tròn', async ({
        page,
      }) => {
        await page.getByRole('button', { name: /Tìm kiếm trên cổng thông tin/ }).click();
        const o = page.locator('input[name="q"]');
        await expect(o).toBeFocused();

        const k = await page.evaluate(() => {
          const el = document.querySelector('input[name="q"]') as HTMLElement;
          const pill = el.parentElement as HTMLElement;
          const ko = getComputedStyle(el);
          const kp = getComputedStyle(pill);
          return {
            outlineO: `${ko.outlineStyle}/${ko.outlineWidth}`,
            banKinhPill: kp.borderRadius,
            boxShadowPill: kp.boxShadow,
          };
        });
        console.log(
          `    [${bp.ten}] outline ô nhập=${k.outlineO} · pill bo ${k.banKinhPill} · ring=${k.boxShadowPill !== 'none'}`,
        );

        // `globals.css` có luật toàn cục `:focus-visible { outline: 2px solid … }`. Nó vẽ một
        // hình CHỮ NHẬT quanh `<input>` — nằm lọt trong pill bo tròn. Đây là thứ phải hết.
        expect(k.outlineO, 'vòng focus chữ nhật vẫn còn trên <input>').toMatch(/none|\/0px/);
        // ⛔ Nhưng KHÔNG được mất dấu hiệu focus: nó phải chuyển lên pill.
        expect(
          k.boxShadowPill,
          'gỡ vòng focus mà không thay bằng gì ⇒ mất lối định vị bàn phím',
        ).not.toBe('none');
      });
    });
  }

  test('⭐ danh sách tin cột phải cuộn TRONG lòng nó, không đẩy hàng cao lên', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto('/', { waitUntil: 'networkidle' });

    const do_ = await page.evaluate(() => {
      const el = document.querySelector('[data-cuon-tin]');
      if (!el) return null;
      return { scrollHeight: el.scrollHeight, clientHeight: el.clientHeight };
    });
    expect(do_, 'không tìm thấy vùng cuộn của cột tin').not.toBeNull();
    console.log(`    cột tin: scrollHeight=${do_!.scrollHeight} clientHeight=${do_!.clientHeight}`);
    expect(
      do_!.scrollHeight,
      'vùng cuộn không hề bị chặn ⇒ `overflow-y-auto` vẫn là trang trí',
    ).toBeGreaterThan(do_!.clientHeight);
  });
});
