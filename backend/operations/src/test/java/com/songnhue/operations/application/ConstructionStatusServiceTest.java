package com.songnhue.operations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.spi.HydroAlertPort;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionOperationStatus;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.OperationStatusCode;
import com.songnhue.operations.domain.OperationalStatus;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;
import com.songnhue.operations.infra.ConstructionRepository;
import com.songnhue.operations.infra.MaintenanceLogRepository;

@ExtendWith(MockitoExtension.class)
class ConstructionStatusServiceTest {

    @Mock
    private ConstructionRepository constructions;

    @Mock
    private MaintenanceLogRepository maintenanceLogs;

    @Mock
    private ConstructionOperationStatusRepository operationStatuses;

    @Mock
    private HydroAlertPort hydroAlertPort;

    @InjectMocks
    private ConstructionStatusService service;

    private Construction construction;

    @BeforeEach
    void setUp() {
        construction = new Construction();
        // Giả lập entity đã được cấp ID để tránh bypass nhánh ID = null
        org.springframework.test.util.ReflectionTestUtils.setField(construction, "id", 100L);
        construction.setLifecycleState(LifecycleState.DANG_HOAT_DONG);
    }

    @Test
    void tinhKhiThanhLyTraVeThanhLyBatChapSMC() {
        construction.setLifecycleState(LifecycleState.DA_THANH_LY);
        // Ngay cả khi có sự cố đang mở
        lenient().when(maintenanceLogs.demBanGhiDangMo(100L, true)).thenReturn(1L);

        OperationalStatus result = service.recompute(construction);

        assertThat(result).isEqualTo(OperationalStatus.DA_THANH_LY);
    }

    @Test
    void tinhKhiCoSuCoDangMoTraVeSuCo() {
        when(maintenanceLogs.demBanGhiDangMo(100L, true)).thenReturn(1L);

        OperationalStatus result = service.recompute(construction);

        assertThat(result).isEqualTo(OperationalStatus.SU_CO);
    }

    @Test
    void tinhKhiCoBaoTriDangMoTraVeBaoTri() {
        when(maintenanceLogs.demBanGhiDangMo(100L, true)).thenReturn(0L);
        when(maintenanceLogs.demBanGhiDangMo(100L, false)).thenReturn(1L);

        OperationalStatus result = service.recompute(construction);

        assertThat(result).isEqualTo(OperationalStatus.BAO_TRI);
    }

    @Test
    void tinhKhiCoCanhBaoNguongTraVeCanhBao() {
        when(maintenanceLogs.demBanGhiDangMo(eq(100L), anyBoolean())).thenReturn(0L);
        when(hydroAlertPort.hasActiveAlert(100L)).thenReturn(true);

        OperationalStatus result = service.recompute(construction);

        assertThat(result).isEqualTo(OperationalStatus.CANH_BAO);
    }

    @Test
    void tinhKhiCoMaTinhHinhMapTheoMa() {
        when(maintenanceLogs.demBanGhiDangMo(eq(100L), anyBoolean())).thenReturn(0L);
        when(hydroAlertPort.hasActiveAlert(100L)).thenReturn(false);

        OperationStatusCode code = new OperationStatusCode();
        code.setMappedStatus(OperationalStatus.NGUNG_MUA_VU);

        ConstructionOperationStatus cos = new ConstructionOperationStatus();
        cos.setOperationCode(code);

        when(operationStatuses.banGhiMoiNhat(100L)).thenReturn(Optional.of(cos));

        OperationalStatus result = service.recompute(construction);

        assertThat(result).isEqualTo(OperationalStatus.NGUNG_MUA_VU);
    }

    @Test
    void tinhKhongCoGiMacDinhBinhThuong() {
        when(maintenanceLogs.demBanGhiDangMo(eq(100L), anyBoolean())).thenReturn(0L);
        when(hydroAlertPort.hasActiveAlert(100L)).thenReturn(false);
        when(operationStatuses.banGhiMoiNhat(100L)).thenReturn(Optional.empty());

        OperationalStatus result = service.recompute(construction);

        assertThat(result).isEqualTo(OperationalStatus.BINH_THUONG);
    }
}
