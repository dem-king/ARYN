package com.aryn.cloud.common.log.event;

import com.aryn.cloud.common.core.entity.SysLogBase;
import com.aryn.cloud.common.core.entity.SysLoginLogBase;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.remote.RemoteSysLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志异步落库监听器。
 *
 * <p>2026-11-04 注册为 Bean（此前未注册，导致 @SysLog 不产生 sys_log 记录）；
 * 异步执行内所有异常只记录日志，不影响业务主流程。
 */
@Slf4j
@Component
public class ArynLogEventListener {

	@DubboReference(check = false)
	private RemoteSysLogService remoteSysLogService;

	@Async("hxAsyncExecutor")
	@EventListener(ArynLogEvent.class)
	public void saveSysLog(ArynLogEvent event) {
		SysLogBase sysLog = event.getSysLog();
		try {
			remoteSysLogService.saveLog(sysLog);
		}
		catch (Exception ex) {
			log.error("操作日志落库失败 title={} uri={}", sysLog.getTitle(), sysLog.getRequestUri(), ex);
		}
	}

	@Async("hxAsyncExecutor")
	@EventListener(ArynLoginLogEvent.class)
	public void saveSysLoginLog(ArynLoginLogEvent event) {
		SysLoginLogBase sysLoginLog = event.getLoginLog();
		try {
			remoteSysLogService.saveLoginLog(sysLoginLog);
		}
		catch (Exception ex) {
			log.error("登录日志落库失败", ex);
		}
	}

}
