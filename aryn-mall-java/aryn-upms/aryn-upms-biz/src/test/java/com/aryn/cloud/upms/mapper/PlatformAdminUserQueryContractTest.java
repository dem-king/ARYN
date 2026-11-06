package com.aryn.cloud.upms.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户侧「查询员工账号」SQL 契约：平台管理员账号不属于任何租户，所有员工选择/搜索/
 * 统计入口都必须排除它；下游过滤所需的平台管理员 ID 查询必须按 platform_admin='1'。
 */
class PlatformAdminUserQueryContractTest {

	@Test
	void tenantFacingUserQueriesExcludePlatformAdmin() throws IOException {
		String xml = Files.readString(Path.of("src/main/resources/mapper/SysUserMapper.xml"));

		assertThat(xml).contains("id=\"adminPage\"", "id=\"selectMessageRecipients\"",
				"id=\"countCustomerServiceStaff\"", "id=\"searchNormalStaff\"", "id=\"countTenantStaff\"");
		assertThat(countOccurrences(xml, "platform_admin = '0'"))
			.as("员工账号列表、消息受众、客服资格、员工搜索、租户员工数都应排除平台管理员")
			.isGreaterThanOrEqualTo(5);
		assertThat(xml).contains("id=\"selectPlatformAdminIds\"", "platform_admin = '1'");
	}

	private static int countOccurrences(String text, String token) {
		int count = 0;
		int index = 0;
		while ((index = text.indexOf(token, index)) >= 0) {
			count++;
			index += token.length();
		}
		return count;
	}

}
