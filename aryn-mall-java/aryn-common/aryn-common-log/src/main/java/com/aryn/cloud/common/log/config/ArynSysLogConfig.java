package com.aryn.cloud.common.log.config;

import com.aryn.cloud.common.log.event.ArynLoginLogEvent;
import com.aryn.cloud.upms.api.remote.RemoteSysLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.event.EventListener;

@Slf4j
@AutoConfiguration
public class ArynSysLogConfig {

	@DubboReference(check = false)
	private RemoteSysLogService remoteSysLogService;

	@EventListener
	public void saveLoginLog(ArynLoginLogEvent event) {
		try {
			remoteSysLogService.saveLoginLog(event.getLoginLog());
		}
		catch (Exception exception) {
			log.error("Failed to save login log", exception);
		}
	}

}
