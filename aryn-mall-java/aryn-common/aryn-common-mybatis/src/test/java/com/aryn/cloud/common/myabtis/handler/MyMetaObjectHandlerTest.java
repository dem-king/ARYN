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
	void updatePinsTenantIdToCurrentTenantSoItCannotBeReassigned() {
		ArynTenantContextHolder.setTenantId("tenant-a");
		TenantProbeEntity entity = new TenantProbeEntity();
		entity.setTenantId("tenant-b");

		handler.updateFill(SystemMetaObject.forObject(entity));

		// 必须在填充阶段改写为当前租户：MyBatis-Plus 先生成 SET 子句再执行填充，
		// 置空会让已生成的 tenant_id SET 绑定 NULL，触发非空约束。
		assertThat(entity.getTenantId()).isEqualTo("tenant-a");
	}

	@Test
	void updateKeepsExplicitTenantWhenNoContextExists() {
		TenantProbeEntity entity = new TenantProbeEntity();
		entity.setTenantId("job-tenant");

		handler.updateFill(SystemMetaObject.forObject(entity));

		assertThat(entity.getTenantId()).isEqualTo("job-tenant");
	}

}
