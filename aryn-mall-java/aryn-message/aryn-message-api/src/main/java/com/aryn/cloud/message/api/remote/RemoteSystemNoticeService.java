package com.aryn.cloud.message.api.remote;

import com.aryn.cloud.message.api.dto.notice.SystemNoticeSendDTO;

/**
 * 系统通知 Dubbo 远程服务。
 *
 * <p>供平台侧内部任务向租户工作人员推送站内信；租户由调用上下文决定，
 * 不属于对外接口，禁止直接暴露为 Controller。
 */
public interface RemoteSystemNoticeService {

	/**
	 * 向当前租户全体工作人员发送站内信。
	 */
	void sendToAllStaff(SystemNoticeSendDTO request);

}
