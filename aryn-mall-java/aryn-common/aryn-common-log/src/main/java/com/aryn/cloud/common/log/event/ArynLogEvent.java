package com.aryn.cloud.common.log.event;

import com.aryn.cloud.common.core.entity.SysLogBase;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class ArynLogEvent extends ApplicationEvent {

	@Getter
	private final SysLogBase sysLog;

	public ArynLogEvent(Object source, SysLogBase sysLog) {
		super(source);
		this.sysLog = sysLog;
	}

}
