package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.core.entity.SysLogBase;
import com.aryn.cloud.common.core.entity.SysLoginLogBase;
import com.aryn.cloud.upms.api.entity.SysLog;
import com.aryn.cloud.upms.api.entity.SysLoginLog;
import com.aryn.cloud.upms.api.remote.RemoteSysLogService;
import com.aryn.cloud.upms.service.ISysLogService;
import com.aryn.cloud.upms.service.ISysLoginLogService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSysLogServiceImpl implements RemoteSysLogService {

	private final ISysLogService sysLogService;

	private final ISysLoginLogService sysLoginLogService;

	@Override
	public Boolean saveLog(SysLogBase sysLogBase) {
		SysLog sysLog = new SysLog();
		sysLog.setId(sysLogBase.getId());
		sysLog.setIpAddr(sysLogBase.getIpAddr());
		sysLog.setTitle(sysLogBase.getTitle());
		sysLog.setRequestMethod(sysLogBase.getRequestMethod());
		sysLog.setRequestUri(sysLogBase.getRequestUri());
		sysLog.setRequestParams(sysLogBase.getRequestParams());
		sysLog.setRequestTime(sysLogBase.getRequestTime());
		sysLog.setLocation(sysLogBase.getLocation());
		sysLog.setMethod(sysLogBase.getMethod());
		sysLog.setUserName(sysLogBase.getUserName());
		sysLog.setStatus(sysLogBase.getStatus());
		sysLog.setExMsg(sysLogBase.getExMsg());
		sysLog.setCreateBy(sysLogBase.getCreateBy());
		sysLog.setCreateTime(sysLogBase.getCreateTime());
		sysLog.setDelFlag(sysLogBase.getDelFlag());
		sysLog.setTenantId(sysLogBase.getTenantId());
		return sysLogService.save(sysLog);
	}

	@Override
	public Boolean saveLoginLog(SysLoginLogBase sysLoginLogBase) {
		SysLoginLog sysLoginLog = new SysLoginLog();
		sysLoginLog.setId(sysLoginLogBase.getId());
		sysLoginLog.setIpAddr(sysLoginLogBase.getIpAddr());
		sysLoginLog.setLocation(sysLoginLogBase.getLocation());
		sysLoginLog.setUserName(sysLoginLogBase.getUserName());
		sysLoginLog.setStatus(sysLoginLogBase.getStatus());
		sysLoginLog.setMsg(sysLoginLogBase.getMsg());
		sysLoginLog.setBrowser(sysLoginLogBase.getBrowser());
		sysLoginLog.setOs(sysLoginLogBase.getOs());
		sysLoginLog.setCreateBy(sysLoginLogBase.getCreateBy());
		sysLoginLog.setCreateTime(sysLoginLogBase.getCreateTime());
		sysLoginLog.setDelFlag(sysLoginLogBase.getDelFlag());
		sysLoginLog.setTenantId(sysLoginLogBase.getTenantId());
		return sysLoginLogService.save(sysLoginLog);
	}

}
