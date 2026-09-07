package com.songnhue.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MaintenanceLogTest {

    @Test
    void testGettersAndSetters() {
        MaintenanceLog log = new MaintenanceLog(
                "C", 1L, 2L, MaintenanceType.BAO_TRI_DINH_KY, "MOI", LocalDate.of(2023, 1, 1), "Content", 3L);
        log.setCompletedOn(LocalDate.of(2023, 1, 10));
        log.setCost(new BigDecimal("1000"));
        log.datDonViThucHien(2L, "Nha thau A");
        log.setAlertEventPublicId(UUID.randomUUID());

        assertThat(log.getConstructionId()).isEqualTo(1L);
        assertThat(log.getWorkType()).isEqualTo(MaintenanceType.BAO_TRI_DINH_KY);
        assertThat(log.getStartedOn()).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(log.getCompletedOn()).isEqualTo(LocalDate.of(2023, 1, 10));
        assertThat(log.getCost()).isEqualTo(new BigDecimal("1000"));
        assertThat(log.getContent()).isEqualTo("Content");
        assertThat(log.getPerformerOrgUnitId()).isEqualTo(2L);
        assertThat(log.getPerformerName()).isEqualTo("Nha thau A");
        assertThat(log.getAssigneeUserId()).isEqualTo(3L);
        assertThat(log.getAlertEventPublicId()).isNotNull();
    }
}
