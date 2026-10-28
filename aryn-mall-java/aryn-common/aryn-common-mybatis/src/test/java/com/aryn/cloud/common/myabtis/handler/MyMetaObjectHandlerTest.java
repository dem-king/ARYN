package com.aryn.cloud.common.myabtis.handler;

import com.aryn.cloud.common.core.api.entity.TenantProbeEntity;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MyMetaObjectHandlerTest {

	private final MyMetaObjectHandler handler = new MyMetaObjectHandler();

	@AfterEach
	void clearTenantContext() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void insertOverridesClientSuppliedTenantWithCurrentTenant() {
		ArynTenantContextHolder.setTenantId("tenant-a");
		TenantProbeEntity entity = new TenantProbeEntity();
		entity.setTenantId("tenant-b");

		handler.insertFill(SystemMetaObject.forObject(entity));

		assertThat(entity.getTenantId()).isEqualTo("tenant-a");
	}

	@Test
	void insertKeepsExplicitTenantWhenNoContextExists() {
		TenantProbeEntity entity = new TenantProbeEntity();
		entity.setTenantId("job-tenant");

		handler.insertFill(SystemMetaObject.forObject(entity));

		assertThat(entity.getTenantId()).isEqualTo("job-tenant");
	}

	@Test
	void updateClearsTenantIdSoItCannotBeReassigned() {
		ArynTenantContextHolder.setTenantId("tenant-a");
		TenantProbeEntity entity = new TenantProbeEntity();
		entity.setTenantId("tenant-b");

		handler.updateFill(SystemMetaObject.forObject(entity));

		assertThat(entity.getTenantId()).isNull();
	}

}
