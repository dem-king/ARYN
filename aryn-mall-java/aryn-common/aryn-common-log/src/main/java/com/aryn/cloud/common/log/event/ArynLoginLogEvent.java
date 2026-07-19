package com.aryn.cloud.common.log.event;

import com.aryn.cloud.common.core.entity.SysLoginLogBase;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ArynLoginLogEvent extends ApplicationEvent {

	private final SysLoginLogBase loginLog;

	public ArynLoginLogEvent(Object source, SysLoginLogBase loginLog) {
		super(source);
		this.loginLog = loginLog;
	}

}
