package com.aryn.cloud.common.myabtis.handler;

import com.aryn.cloud.common.core.api.dto.TenantProbeDTO;
import com.aryn.cloud.common.core.api.entity.TenantProbeEntity;
import com.aryn.cloud.common.core.config.TenantIdRequestModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIdRequestModuleTest {

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new TenantIdRequestModule());

	@Test
	void ignoresClientSuppliedTenantIdOnTenantEntity() throws Exception {
		TenantProbeEntity entity = objectMapper.readValue(
				"{\"id\":\"1\",\"name\":\"probe\",\"tenantId\":\"victim-tenant\"}", TenantProbeEntity.class);

		assertThat(entity.getTenantId()).isNull();
		assertThat(entity.getName()).isEqualTo("probe");
	}

	@Test
	void keepsTenantIdOnExplicitCrossTenantDTO() throws Exception {
		TenantProbeDTO dto = objectMapper.readValue("{\"tenantId\":\"target-tenant\"}", TenantProbeDTO.class);

		assertThat(dto.getTenantId()).isEqualTo("target-tenant");
	}

}
