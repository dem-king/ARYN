package com.aryn.cloud.upms.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MessageStaffMapperContractTest {

	@Test
	void queryUsesTenantCursorRoleDepartmentAndPermissionBoundaries() throws IOException {
		String xml = Files.readString(Path.of("src/main/resources/mapper/SysUserMapper.xml"));
		assertThat(xml).contains("sys_user.tenant_id = #{query.tenantId}", "sys_user.id &gt; #{query.cursor}",
				"sys_user.status = '0'", "sys_user_role.tenant_id = #{query.tenantId}",
				"sys_user.dept_id IN", "query.roleIds", "message:service:agent", "ORDER BY sys_user.id ASC",
				"LIMIT #{fetchSize}");
	}

}
