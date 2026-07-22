package com.aryn.cloud.user.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MessageAudienceMapperContractTest {

	@Test
	void queryUsesTenantCursorLevelAndTagBoundaries() throws IOException {
		String xml = Files.readString(Path.of("src/main/resources/mapper/UserInfoMapper.xml"));
		assertThat(xml).contains("user_info.tenant_id = #{query.tenantId}", "user_info.id &gt; #{query.cursor}",
				"user_info.member_level_id = #{query.memberLevelId}", "user_tag_rel.tenant_id = #{query.tenantId}",
				"user_tag_rel.tag_id = #{query.memberTagId}", "ORDER BY user_info.id ASC", "LIMIT #{fetchSize}");
	}

}
