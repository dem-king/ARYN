package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.mapper.SysMaterialMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteMaterialAccessServiceImplTest {

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void atomicallyReservesOwnedUnboundMaterialsAndSupportsRetry() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		SysMaterialMapper mapper = mock(SysMaterialMapper.class);
		when(mapper.reserveDeliveryMaterial(anyString(), anyString(), anyString(), anyString(), any()))
			.thenReturn(1);
		when(mapper.selectReservedDeliveryMaterials("tenant-1", "staff-1", "reservation-1"))
			.thenReturn(List.of(material("m1"), material("m2")));
		RemoteMaterialAccessServiceImpl service = new RemoteMaterialAccessServiceImpl(mapper);

		var result = service.reserveForDelivery("tenant-1", "staff-1", List.of("m1", "m2"), "reservation-1");

		assertThat(result).extracting(com.aryn.cloud.upms.api.vo.MaterialAccessVO::getMaterialId)
			.containsExactly("m1", "m2");
	}

	@Test
	void rejectsCrossStaffOrAlreadyBoundMaterial() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		SysMaterialMapper mapper = mock(SysMaterialMapper.class);
		when(mapper.reserveDeliveryMaterial(anyString(), anyString(), anyString(), anyString(), any()))
			.thenReturn(0);

		assertThatThrownBy(() -> new RemoteMaterialAccessServiceImpl(mapper)
			.reserveForDelivery("tenant-1", "staff-1", List.of("m1"), "reservation-1"))
			.isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void temporaryAccessDoesNotExposeObjectKey() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		SysMaterialMapper mapper = mock(SysMaterialMapper.class);
		SysMaterial material = material("m1");
		material.setObjectKey("tenant-1/private/evidence.png");
		material.setUrl("/upms/file/local/tenant-1/evidence.png");
		when(mapper.selectByTenantAndId("tenant-1", "m1")).thenReturn(material);

		var access = new RemoteMaterialAccessServiceImpl(mapper).getTemporaryAccess("tenant-1", "m1");

		assertThat(access.getAccessUrl()).isEqualTo(material.getUrl());
		assertThat(access.getExpiresAt()).isAfter(LocalDateTime.now());
		assertThat(com.aryn.cloud.upms.api.vo.MaterialAccessVO.class.getDeclaredFields())
			.extracting(java.lang.reflect.Field::getName).doesNotContain("objectKey", "accessKey", "secret");
	}

	private SysMaterial material(String id) {
		SysMaterial material = new SysMaterial();
		material.setId(id);
		material.setTenantId("tenant-1");
		material.setCreateBy("staff-1");
		return material;
	}

	private static String anyString() {
		return org.mockito.ArgumentMatchers.anyString();
	}

}
