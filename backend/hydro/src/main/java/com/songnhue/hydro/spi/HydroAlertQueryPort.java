package com.songnhue.hydro.spi;

/**
 * Cổng đọc <b>số cảnh báo ngưỡng đang xảy ra</b> — T35.6.
 *
 * <h2>⛔ Vì sao KHÔNG gộp vào {@code core.spi.HydroAlertPort}</h2>
 *
 * <p>Hai cổng trả lời hai câu hỏi khác nhau cho hai người gọi khác nhau:
 *
 * <ul>
 *   <li>{@code core.spi.HydroAlertPort#hasActiveAlert(Long)} — <i>"công trình NÀY có cảnh báo
 *       không"</i>. Nó là <b>mắt xích 3</b> của {@code ConstructionStatusService.tinh()}, kết quả đi
 *       thẳng vào cột {@code constructions.operational_status}. Nó nằm ở {@code core.spi} vì
 *       {@code operations} phải gọi được nó <b>từ trước khi</b> có cạnh Maven
 *       {@code operations → hydro}.
 *   <li>Cổng này — <i>"toàn hệ đang có bao nhiêu cảnh báo"</i>, một con số cho ô KPI. ⛔ Không ghi
 *       xuống cột nào.
 * </ul>
 *
 * <p>⛔ Gộp chúng là buộc một cổng phải mang cả hai ngữ nghĩa, và người sửa sau sẽ đổi vế lọc của
 * cái này mà không biết mình vừa đổi giá trị được ghi xuống CSDL của cái kia.
 *
 * <h2>⭐ Nhưng ĐỊNH NGHĨA "đang xảy ra" thì phải là MỘT</h2>
 *
 * <p>Cả hai đi qua đúng một vị từ SQL dùng chung
 * ({@code AlertEventQueryRepository#DIEU_KIEN_DANG_CANH_BAO}):
 * {@code status = 'DANG_XAY_RA' AND confirmed_at IS NOT NULL}.
 *
 * <p>⚠ {@code confirmed_at IS NOT NULL} là vế chịu lực: một dòng vừa vượt ngưỡng nhưng chưa giữ đủ
 * {@code delay_minutes} là điều kiện <i>đang được theo dõi</i> — chưa ai nhận thông báo nào về nó.
 * Nếu ô KPI đếm cả những dòng ấy còn trạng thái công trình thì không, hai màn hình cạnh nhau sẽ nói
 * hai con số và người trực sẽ tin con số lớn hơn. Đó là lý do vị từ nằm ở <b>một hằng số</b>, ⛔
 * không chép làm hai câu.
 */
public interface HydroAlertQueryPort {

    /**
     * Đếm sự kiện cảnh báo <b>đang xảy ra và đã xác nhận</b> trên toàn hệ.
     *
     * <p>⛔ Không lọc phạm vi đơn vị — xem lý do ở {@link HydroLatestQueryPort}.
     *
     * <p>⚠ Trả {@code 0} là một câu <b>khẳng định</b>: "đã đếm, và không có cảnh báo nào đang mở".
     * Khác hẳn ô KPI trước WS-35 vốn trả {@code null} kèm lý do vì <i>chưa đấu nối nguồn</i>. Ô này
     * chỉ được phép trả 0 sau khi cổng thật sự đọc được CSDL (quy tắc 16).
     */
    long demCanhBaoDangXayRa();
}
