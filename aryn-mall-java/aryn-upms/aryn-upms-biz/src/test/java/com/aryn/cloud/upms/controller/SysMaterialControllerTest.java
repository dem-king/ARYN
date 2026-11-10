package com.aryn.cloud.upms.controller;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.service.ISysMaterialGroupService;
import com.aryn.cloud.upms.service.ISysMaterialService;
import com.aryn.cloud.upms.support.PrivateFileUrlSigner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysMaterialControllerTest {

	private static final String TENANT_ID = "tenant-a";

	private final ISysMaterialService materialService = mock(ISysMaterialService.class);

	private final ISysMaterialGroupService materialGroupService = mock(ISysMaterialGroupService.class);

	private final SysMaterialController controller = new SysMaterialController(materialService,
			mock(PrivateFileUrlSigner.class), materialGroupService);

	@BeforeEach
	void setUp() {
		ArynTenantContextHolder.setTenantId(TENANT_ID);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void editKeepsPrivateEvidenceUrlAndBizTag() {
		SysMaterial existing = new SysMaterial();
		existing.setId("material-1");
		existing.setBizTag("delivery-evidence");
		existing.setUrl("/boot/file/local/tenant-a/private/photo.jpg");
		when(materialService.getById("material-1")).thenReturn(existing);

		SysMaterial request = new SysMaterial();
		request.setId("material-1");
		request.setName("新名称");
		request.setUrl("/boot/file/local/tenant-a/private/photo.jpg?expires=1&signature=expired");
		request.setBizTag(null);

		controller.edit(request);

		assertThat(request.getUrl()).isEqualTo(existing.getUrl());
		assertThat(request.getBizTag()).isEqualTo("delivery-evidence");
		verify(materialService).updateById(request);
	}

	@Test
	void addResolvesGroupIdBeforeSaving() {
		// 新增素材与上传接口同一口径：空/'-1'/未知分组都落到默认分组
		when(materialGroupService.resolveGroupIdForSave(TENANT_ID, "-1")).thenReturn("default-group");
		SysMaterial request = new SysMaterial();
		request.setName("海报");
		request.setGroupId("-1");

		controller.add(request);

		assertThat(request.getGroupId()).isEqualTo("default-group");
		verify(materialService).save(request);
	}

	@Test
	void editResolvesOnlyWhenGroupIdProvided() {
		when(materialGroupService.resolveGroupIdForSave(eq(TENANT_ID), anyString())).thenReturn("default-group");

		SysMaterial withGroup = new SysMaterial();
		withGroup.setId("material-1");
		withGroup.setGroupId("group-missing");
		controller.edit(withGroup);
		assertThat(withGroup.getGroupId()).isEqualTo("default-group");

		// 只改名称（不带 groupId）时不能顺手把素材挪到默认分组
		SysMaterial nameOnly = new SysMaterial();
		nameOnly.setId("material-1");
		nameOnly.setName("新名称");
		controller.edit(nameOnly);
		assertThat(nameOnly.getGroupId()).isNull();
		verify(materialGroupService, never()).resolveGroupIdForSave(any(), isNull());
		verify(materialService, never()).save(any());
	}

}
