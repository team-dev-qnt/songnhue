package com.songnhue.hydro.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.MucCauHinh;
import com.songnhue.core.spi.MucCauHinh.MucDo;
import com.songnhue.core.spi.MucCauHinh.TrangThai;
import com.songnhue.core.spi.NguonTinhTrangCauHinh;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.ApiSourceStatus;
import com.songnhue.hydro.infra.ApiSourceRepository;

/**
 * Tình trạng cấu hình của MOD-03 — T61.41.
 *
 * <p>⛔⛔ Nguồn đang HOẠT ĐỘNG mà thiếu mã số là mục CHẶN: quy tắc 18 — ⛔ có API lịch sử, mỗi lượt poll hỏng là số liệu
 * mất VĨNH VIỄN. Staging chạy 9 ngày với mã số dán nhầm ô và ⛔ màn hình nào kêu (T50.1).
 */
@Component
class TinhTrangCauHinhHydro implements NguonTinhTrangCauHinh {

    private final ApiSourceRepository sources;
    private final Environment env;

    TinhTrangCauHinhHydro(ApiSourceRepository sources, Environment env) {
        this.sources = sources;
        this.env = env;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MucCauHinh> mucCauHinh() {
        String moiTruong =
                env.getProperty("management.metrics.tags.environment", "local").strip();
        boolean trienKhai = "staging".equalsIgnoreCase(moiTruong) || "production".equalsIgnoreCase(moiTruong);
        List<MucCauHinh> ra = new ArrayList<>();

        List<String> thieuMaSo = sources.findByDeletedAtIsNullOrderByCodeAsc().stream()
                .filter(n -> n.getStatus() == ApiSourceStatus.HOAT_DONG)
                .filter(n -> !n.isCredentialDaCauHinh())
                .map(ApiSource::getCode)
                .toList();
        ra.add(new MucCauHinh(
                "HYDRO_MA_SO",
                "Thuỷ văn",
                "Mã số nguồn dữ liệu thuỷ văn đang hoạt động",
                thieuMaSo.isEmpty() ? TrangThai.DAT : TrangThai.THIEU,
                MucDo.CHAN,
                "Ứng dụng (poller)",
                "Giao diện — Thuỷ văn › Nguồn dữ liệu › Mã số",
                thieuMaSo.isEmpty()
                        ? "Mọi nguồn đang hoạt động đều có mã số (mã hoá AES-256-GCM)."
                        : "⛔ Thiếu mã số: " + String.join(", ", thieuMaSo)
                                + " — poller hỏng trước khi mở HTTP, số liệu mất VĨNH VIỄN (quy tắc 18)."));

        boolean mock = env.getProperty("app.hydro.api.mock", Boolean.class, false);
        boolean noiBo = env.getProperty("app.hydro.api.allow-internal-host", Boolean.class, false);
        ra.add(new MucCauHinh(
                "HYDRO_CONG_TAC",
                "Thuỷ văn",
                "Công tắc nới bảo mật của poller",
                mock || noiBo ? TrangThai.SAI : TrangThai.DAT,
                trienKhai ? MucDo.CHAN : MucDo.THONG_TIN,
                "Ứng dụng",
                ".env — HYDRO_API_MOCK · HYDRO_API_ALLOW_INTERNAL_HOST (bỏ trống = tắt)",
                mock || noiBo
                        ? "⛔ Đang bật " + (mock ? "HYDRO_API_MOCK " : "")
                                + (noiBo ? "HYDRO_API_ALLOW_INTERNAL_HOST" : "")
                                + " — số liệu giả / mở đường SSRF vào mạng nội bộ. ⛔ Bao giờ lên giao diện."
                        : "Cả hai đều tắt."));
        return ra;
    }
}
