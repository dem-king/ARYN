package com.aryn.cloud.message.dubbo;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.notice.NoticeSaveRequest;
import com.aryn.cloud.message.api.dto.notice.SystemNoticeSendDTO;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.enums.NoticeJumpType;
import com.aryn.cloud.message.api.remote.RemoteSystemNoticeService;
import com.aryn.cloud.message.api.vo.notice.NoticeVO;
import com.aryn.cloud.message.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 系统通知 Dubbo 远程实现。
 *
 * <p>以「平台通知」身份创建并立即发布一条面向全体工作人员的站内信，
 * 分发任务由消息域异步执行。
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSystemNoticeServiceImpl implements RemoteSystemNoticeService {

	private static final String SENDER_ID = "SYSTEM";

	private static final String SENDER_NAME = "平台通知";

	private final NoticeService noticeService;

	@Override
	public void sendToAllStaff(SystemNoticeSendDTO request) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (StrUtil.isBlank(tenantId)) {
			throw new ArynBusinessException(403, "缺少租户上下文");
		}
		if (request == null || StrUtil.isBlank(request.getTitle()) || StrUtil.isBlank(request.getContent())) {
			throw new ArynBusinessException("通知标题和内容不能为空");
		}
		NoticeSaveRequest saveRequest = new NoticeSaveRequest();
		saveRequest.setTitle(request.getTitle());
		saveRequest.setSummary(request.getSummary());
		saveRequest.setContent(request.getContent());
		saveRequest.setCategory(StrUtil.blankToDefault(request.getCategory(), "system"));
		saveRequest.setPriority("NORMAL");
		saveRequest.setTargetTypes(Set.of(MessageIdentityType.SYS_USER));
		saveRequest.setExpireTime(request.getExpireTime());
		saveRequest.setJumpType(NoticeJumpType.NONE);
		NoticeVO draft = noticeService.createDraft(tenantId, SENDER_ID, SENDER_NAME, saveRequest);
		noticeService.publish(tenantId, SENDER_ID, draft.getId());
	}

}
