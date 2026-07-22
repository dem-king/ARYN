package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.dto.UserMessageAudienceRequest;
import com.aryn.cloud.user.api.vo.UserMessageAudiencePageVO;

/** 消息域会员受众查询契约。 */
public interface RemoteMessageAudienceService {

	UserMessageAudiencePageVO queryRecipients(UserMessageAudienceRequest request);

}
