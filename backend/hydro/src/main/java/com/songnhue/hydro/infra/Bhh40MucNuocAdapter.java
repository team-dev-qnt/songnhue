package com.songnhue.hydro.infra;

import org.springframework.stereotype.Component;

import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.TelemetryReading;

/**
 * Nguồn <b>mực nước</b> của {@code songnhue.bhh40.net} — {@code GET /api/getmucnuoc.aspx?key=<mã số>;}.
 *
 * <h2>⛔⛔ Đường dẫn đổi ngày 26/09/2026, và lượt đổi ấy ⛔ đứng một mình được</h2>
 *
 * <p>Tới 26/09/2026 đường dẫn là {@code api/getmn.aspx}. Đo trên nguồn thật hôm ấy: đường cũ trả
 * <b>HTTP 200 kèm thân {@code not.working}</b>, đường mới trả <b>200 kèm 28 bản ghi</b>. Tức endpoint
 * cũ ⛔ biến mất — nó <i>trả lời</i>, và trả lời bằng đúng chuỗi mà hệ đọc thành "mã số sai". Một
 * nguồn ⛔ phân biệt được <i>khoá hỏng</i> với <i>endpoint đã nghỉ hưu</i> sẽ gửi người vận hành đi
 * đổi mã số (T42.17 đã ghi đúng cái bẫy ấy).
 *
 * <p>⛔⛔ Và đổi hằng số này <b>một mình là gây ra một sự cố mới</b>: {@code DiaChiNguon.chuanHoaGoc}
 * cắt phần trùng theo ĐOẠN, nên một {@code base_url} đang nhúng sẵn {@code /api/getmn.aspx} vốn
 * <i>đang được cứu</i> (k=2) sẽ thôi khớp ⇒ URL nối ra {@code /api/getmn.aspx/api/getmucnuoc.aspx}
 * ⇒ <b>404</b>, đúng hình dạng T52.0 (3576 lượt hỏng, 0 byte). Phép cắt {@code base_url} nằm ở
 * {@code V202609261099} và hai thứ phải đi cùng một PR.
 */
@Component
public class Bhh40MucNuocAdapter extends Bhh40Adapter {

    /** Loại chỉ số nguồn này giao — khớp {@code measurement_types.code}. */
    static final String MA_LOAI_CHI_SO = "MUC_NUOC";

    /** ⚠ Đường dẫn tương đối — {@code DiaChiNguon} lo dấu {@code /} và phần trùng đoạn. */
    static final String DUONG_DAN = "api/getmucnuoc.aspx";

    public Bhh40MucNuocAdapter(HydroApiProperties properties) {
        super(properties);
    }

    @Override
    public AdapterType kieu() {
        return AdapterType.BHH40;
    }

    @Override
    public String maLoaiChiSo() {
        return MA_LOAI_CHI_SO;
    }

    @Override
    protected String duongDan() {
        return DUONG_DAN;
    }

    @Override
    protected String donViNguon() {
        return TelemetryReading.DON_VI_CM;
    }
}
