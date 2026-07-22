package com.aryn.cloud.common.log.event;

import com.aryn.cloud.common.core.entity.SysLogBase;
import com.aryn.cloud.common.core.entity.SysLoginLogBase;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.remote.RemoteSysLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

@Slf4j
@RequiredArgsConstructor
public class ArynLogEventListener {

	private final RemoteSysLogService remoteSysLogService;

	@Async("hxAsyncExecutor")
	@EventListener(ArynLogEvent.class)
	public void saveSysLog(ArynLogEvent event) {
		SysLogBase sysLog = event.getSysLog();
		log.info("调用远程接口之前: tenantId = {}", ArynTenantContextHolder.getTenantId());

		remoteSysLogService.saveLog(sysLog);
	}

	@Async("hxAsyncExecutor")
	@EventListener(ArynLoginLogEvent.class)
	public void saveSysLoginLog(ArynLoginLogEvent event) {
		SysLoginLogBase sysLoginLog = event.getLoginLog();
		remoteSysLogService.saveLoginLog(sysLoginLog);
	}

}
