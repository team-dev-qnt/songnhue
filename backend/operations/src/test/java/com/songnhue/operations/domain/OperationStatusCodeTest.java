package com.songnhue.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OperationStatusCodeTest {

    @Test
    void testGettersAndSetters() {
        OperationStatusCode code = new OperationStatusCode();
        code.setCode("CODE1");
        code.setName("Name 1");
        code.setHasParameter(true);
        code.setParameterUnit("Unit");
        code.setColorHex("#FFFFFF");
        code.setMappedStatus(OperationalStatus.SU_CO);
        code.setSortOrder(1);
        code.setActive(true);

        assertThat(code.getCode()).isEqualTo("CODE1");
        assertThat(code.getName()).isEqualTo("Name 1");
        assertThat(code.isHasParameter()).isTrue();
        assertThat(code.getParameterUnit()).isEqualTo("Unit");
        assertThat(code.getColorHex()).isEqualTo("#FFFFFF");
        assertThat(code.getMappedStatus()).isEqualTo(OperationalStatus.SU_CO);
        assertThat(code.getSortOrder()).isEqualTo(1);
        assertThat(code.isActive()).isTrue();
    }
}
