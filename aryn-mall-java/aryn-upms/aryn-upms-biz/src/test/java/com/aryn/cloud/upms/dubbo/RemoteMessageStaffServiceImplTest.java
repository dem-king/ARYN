package com.aryn.cloud.upms.dubbo;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.StaffMessageAudienceRequest;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import com.aryn.cloud.upms.mapper.SysRoleMapper;
import com.aryn.cloud.upms.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteMessageStaffServiceImplTest {

	@Mock
	private SysUserMapper sysUserMapper;

	@Mock
	private SysRoleMapper sysRoleMapper;

	@BeforeAll
	static void initMybatisMetadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysRole.class);
	}

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void returnsStableCursorAndChecksCustomerServiceQualification() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		StaffMessageAudienceRequest request = new StaffMessageAudienceRequest();
		request.setTenantId("tenant-1");
		request.setLimit(1);
		when(sysUserMapper.selectMessageRecipients(request, 2))
			.thenReturn(List.of(recipient("10"), recipient("11")));
		when(sysUserMapper.countCustomerServiceStaff("tenant-1", "10")).thenReturn(1);

		RemoteMessageStaffServiceImpl service = new RemoteMessageStaffServiceImpl(sysUserMapper, sysRoleMapper);
		var page = service.queryRecipients(request);

		assertThat(page.getRecords()).extracting(StaffMessageRecipientVO::getId).containsExactly("10");
		assertThat(page.getNextCursor()).isEqualTo("10");
		assertThat(page.isHasMore()).isTrue();
		assertThat(service.isCustomerServiceStaff("tenant-1", "10")).isTrue();
	}

	@Test
	void rejectsCrossTenantQualificationCheck() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		assertThatThrownBy(() -> new RemoteMessageStaffServiceImpl(sysUserMapper, sysRoleMapper)
			.isCustomerServiceStaff("tenant-2", "10")).isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void queriesRecipientsByRoleCode() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		SysRole adminRole = new SysRole("role-1", "系统管理员", "ROLE_ADMIN");
		when(sysRoleMapper.selectList(any())).thenReturn(List.of(adminRole));
		StaffMessageAudienceRequest captured = new StaffMessageAudienceRequest();
		when(sysUserMapper.selectMessageRecipients(any(), org.mockito.ArgumentMatchers.eq(1001)))
			.thenAnswer(invocation -> {
				captured.setTenantId(invocation.getArgument(0, StaffMessageAudienceRequest.class).getTenantId());
				captured.setRoleIds(invocation.getArgument(0, StaffMessageAudienceRequest.class).getRoleIds());
				return List.of(recipient("10"));
			});

		RemoteMessageStaffServiceImpl service = new RemoteMessageStaffServiceImpl(sysUserMapper, sysRoleMapper);
		List<StaffMessageRecipientVO> recipients = service.queryRecipientsByRoleCode("tenant-1", "ROLE_ADMIN");

		assertThat(recipients).extracting(StaffMessageRecipientVO::getId).containsExactly("10");
		assertThat(captured.getTenantId()).isEqualTo("tenant-1");
		assertThat(captured.getRoleIds()).containsExactly("role-1");
	}

	@Test
	void returnsEmptyWhenRoleCodeMissing() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		when(sysRoleMapper.selectList(any())).thenReturn(List.of());

		RemoteMessageStaffServiceImpl service = new RemoteMessageStaffServiceImpl(sysUserMapper, sysRoleMapper);
		assertThat(service.queryRecipientsByRoleCode("tenant-1", "ROLE_ADMIN")).isEmpty();
	}

	private StaffMessageRecipientVO recipient(String id) {
		StaffMessageRecipientVO recipient = new StaffMessageRecipientVO();
		recipient.setId(id);
		return recipient;
	}

}
