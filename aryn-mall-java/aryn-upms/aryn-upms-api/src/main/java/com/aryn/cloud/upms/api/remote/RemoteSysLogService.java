package com.aryn.cloud.upms.api.remote;

import com.aryn.cloud.common.core.entity.SysLogBase;
import com.aryn.cloud.common.core.entity.SysLoginLogBase;

public interface RemoteSysLogService {

	Boolean saveLog(SysLogBase sysLog);

	Boolean saveLoginLog(SysLoginLogBase sysLoginLog);

}
