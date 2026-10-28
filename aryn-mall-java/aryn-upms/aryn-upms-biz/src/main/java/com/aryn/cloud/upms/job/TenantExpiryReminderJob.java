package com.aryn.cloud.upms.job;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.api.dto.notice.SystemNoticeSendDTO;
import com.aryn.cloud.message.api.remote.RemoteSystemNoticeService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.service.ISysTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 租户授权到期提醒。
 *
 * <p>在到期前 30/7/1 天向租户全体工作人员发送站内信。任务每天执行一次，
 * 用 Redis 按「租户 + 阶段」去重，多实例部署时也只会由一个实例发送。
 * 到期后的会话回收由 {@link TenantExpiryJob} 负责。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantExpiryReminderJob {

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private static final int MAX_REMIND_DAYS = 30;

	private final ISysTenantService sysTenantService;

	private final StringRedisTemplate redisTemplate;

	@DubboReference(check = false)
	private final RemoteSystemNoticeService remoteSystemNoticeService;

	@Scheduled(cron = "0 30 9 * * ?")
	public void remindExpiringTenants() {
		LocalDateTime now = LocalDateTime.now();
		List<SysTenant> tenants;
		try {
			tenants = sysTenantService.list(Wrappers.<SysTenant>lambdaQuery()
				.eq(SysTenant::getStatus, CommonConstants.NORMAL_STATUS)
				.isNotNull(SysTenant::getAuthEndTime)
				.ge(SysTenant::getAuthEndTime, now)
				.le(SysTenant::getAuthEndTime, now.plusDays(MAX_REMIND_DAYS)));
		}
		catch (Exception e) {
			log.error("扫描临期租户失败", e);
			return;
		}
		for (SysTenant tenant : tenants) {
			if (CommonConstants.PLATFORM_TENANT_ID.equals(tenant.getId())) {
				continue;
			}
			long remainingDays = ChronoUnit.DAYS.between(LocalDate.now(), tenant.getAuthEndTime().toLocalDate());
			int stage = resolveStage(remainingDays);
			if (stage == 0) {
				continue;
			}
			String dedupKey = CacheConstants.TENANT_EXPIRY_REMIND_KEY + tenant.getId() + ":" + stage;
			Boolean acquired = redisTemplate.opsForValue()
				.setIfAbsent(dedupKey, "1", Duration.ofDays(dedupTtlDays(stage)));
			if (!Boolean.TRUE.equals(acquired)) {
				continue;
			}
			try {
				sendReminder(tenant, remainingDays);
			}
			catch (Exception e) {
				// 发送失败释放去重标记，次日任务可重试
				redisTemplate.delete(dedupKey);
				log.warn("租户到期提醒发送失败 tenantId={}", tenant.getId(), e);
			}
		}
	}

	private int resolveStage(long remainingDays) {
		if (remainingDays < 0 || remainingDays > MAX_REMIND_DAYS) {
			return 0;
		}
		if (remainingDays <= 1) {
			return 1;
		}
		return remainingDays <= 7 ? 7 : 30;
	}

	private long dedupTtlDays(int stage) {
		return switch (stage) {
			case 1 -> 1;
			case 7 -> 6;
			default -> 23;
		};
	}

	private void sendReminder(SysTenant tenant, long remainingDays) {
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		ArynTenantContextHolder.setTenantId(tenant.getId());
		try {
			String authEndTime = tenant.getAuthEndTime().format(DATE_TIME_FORMATTER);
			String remaining = remainingDays <= 0 ? "今天" : remainingDays + " 天";
			SystemNoticeSendDTO request = new SystemNoticeSendDTO();
			request.setTitle("租户授权即将到期");
			request.setSummary("授权将于 " + authEndTime + " 到期");
			request.setContent(StrUtil.format(
					"您的租户「{}」授权将于 {} 到期（剩余 {}），到期后将无法登录管理端。请及时联系平台续期。",
					tenant.getName(), authEndTime, remaining));
			request.setCategory("system");
			request.setExpireTime(tenant.getAuthEndTime().plusDays(7));
			remoteSystemNoticeService.sendToAllStaff(request);
			log.info("租户到期提醒已发送 tenantId={} stageDays={}", tenant.getId(), remainingDays);
		}
		finally {
			if (StrUtil.isBlank(previousTenantId)) {
				ArynTenantContextHolder.removeTenantId();
			}
			else {
				ArynTenantContextHolder.setTenantId(previousTenantId);
			}
		}
	}

}
