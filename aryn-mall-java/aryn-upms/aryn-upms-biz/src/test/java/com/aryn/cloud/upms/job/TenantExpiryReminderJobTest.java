package com.aryn.cloud.upms.job;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.api.dto.notice.SystemNoticeSendDTO;
import com.aryn.cloud.message.api.remote.RemoteSystemNoticeService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.service.ISysTenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 租户到期提醒测试：按 30/7/1 天阶段发送，平台租户和超出窗口的租户跳过，
 * 同一租户同一阶段只发送一次。
 */
class TenantExpiryReminderJobTest {

	private ISysTenantService sysTenantService;

	private StringRedisTemplate redisTemplate;

	private ValueOperations<String, String> valueOperations;

	private RemoteSystemNoticeService remoteSystemNoticeService;

	private TenantExpiryReminderJob job;

	@BeforeEach
	void setUp() {
		sysTenantService = mock(ISysTenantService.class);
		redisTemplate = mock(StringRedisTemplate.class);
		valueOperations = mock(ValueOperations.class);
		remoteSystemNoticeService = mock(RemoteSystemNoticeService.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
		job = new TenantExpiryReminderJob(sysTenantService, redisTemplate, remoteSystemNoticeService);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void sendsReminderForEachStageAndSkipsPlatformTenant() {
		LocalDateTime now = LocalDateTime.now();
		when(sysTenantService.list(any(Wrapper.class))).thenReturn(List.of(
				tenant("t-7", now.plusDays(5)),
				tenant("t-30", now.plusDays(20)),
				tenant(CommonConstants.PLATFORM_TENANT_ID, now.plusDays(5)),
				tenant("t-far", now.plusDays(40))));

		job.remindExpiringTenants();

		ArgumentCaptor<SystemNoticeSendDTO> notices = ArgumentCaptor.forClass(SystemNoticeSendDTO.class);
		verify(remoteSystemNoticeService, times(2)).sendToAllStaff(notices.capture());
		assertThat(notices.getAllValues()).extracting(SystemNoticeSendDTO::getTitle)
			.containsOnly("租户授权即将到期");
		assertThat(notices.getAllValues()).extracting(SystemNoticeSendDTO::getContent)
			.allSatisfy(content -> assertThat(content).contains("请及时联系平台续期"));
		verify(valueOperations).setIfAbsent(eq(CacheConstants.TENANT_EXPIRY_REMIND_KEY + "t-7:7"), eq("1"),
				any(Duration.class));
		verify(valueOperations).setIfAbsent(eq(CacheConstants.TENANT_EXPIRY_REMIND_KEY + "t-30:30"), eq("1"),
				any(Duration.class));
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void skipsTenantAlreadyRemindedForStage() {
		when(sysTenantService.list(any(Wrapper.class)))
			.thenReturn(List.of(tenant("t-7", LocalDateTime.now().plusDays(5))));
		when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

		job.remindExpiringTenants();

		verify(remoteSystemNoticeService, never()).sendToAllStaff(any());
	}

	@Test
	void releasesDedupKeyWhenSendFails() {
		when(sysTenantService.list(any(Wrapper.class)))
			.thenReturn(List.of(tenant("t-7", LocalDateTime.now().plusDays(5))));
		org.mockito.Mockito.doThrow(new RuntimeException("message service down"))
			.when(remoteSystemNoticeService)
			.sendToAllStaff(any());

		job.remindExpiringTenants();

		verify(redisTemplate).delete(CacheConstants.TENANT_EXPIRY_REMIND_KEY + "t-7:7");
	}

	private static SysTenant tenant(String id, LocalDateTime authEndTime) {
		SysTenant tenant = new SysTenant();
		tenant.setId(id);
		tenant.setName("测试租户-" + id);
		tenant.setAuthEndTime(authEndTime);
		return tenant;
	}

}
