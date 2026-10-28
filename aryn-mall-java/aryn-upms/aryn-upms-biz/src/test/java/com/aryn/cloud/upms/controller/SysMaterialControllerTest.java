package com.aryn.cloud.upms.controller;

import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.service.ISysMaterialService;
import com.aryn.cloud.upms.support.PrivateFileUrlSigner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysMaterialControllerTest {

	private final ISysMaterialService materialService = mock(ISysMaterialService.class);

	private final SysMaterialController controller = new SysMaterialController(materialService,
			mock(PrivateFileUrlSigner.class));

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

}
