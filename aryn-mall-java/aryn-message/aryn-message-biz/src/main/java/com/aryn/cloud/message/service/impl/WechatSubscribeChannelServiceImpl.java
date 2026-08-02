package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.config.WechatSubscribeProperties;
import com.aryn.cloud.message.service.WechatSubscribeChannelService;
import com.aryn.cloud.upms.api.remote.RemoteWechatBindingService;
import com.aryn.cloud.user.api.dto.WechatSubscribeMsgDTO;
import com.aryn.cloud.user.api.remote.RemoteWechatSubscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 微信小程序订阅消息通道实现。
 * 通过 Dubbo 调用 aryn-user-biz 发送订阅消息，通过 Dubbo 调用 aryn-upms-biz 查询员工 openid。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatSubscribeChannelServiceImpl implements WechatSubscribeChannelService {

	private final WechatSubscribeProperties properties;

	@DubboReference
	private final RemoteWechatSubscribeService wechatSubscribeService;

	@DubboReference
	private final RemoteWechatBindingService wechatBindingService;

	@Override
	public void send(MessageSendCommand command) {
		if (!StringUtils.hasText(properties.getAppId()) || properties.getTemplateMap() == null) {
			log.debug("微信订阅消息未配置 app-id 或 template-map，跳过");
			return;
		}
		String templateId = properties.getTemplateMap().get(command.getBizType());
		if (!StringUtils.hasText(templateId)) {
			log.debug("bizType={} 未配置微信订阅模板，跳过", command.getBizType());
			return;
		}
		String openid = wechatBindingService.getOpenid(command.getTenantId(), command.getRecipientId());
		if (!StringUtils.hasText(openid)) {
			log.debug("员工 {} 未绑定微信 openid，跳过订阅消息", command.getRecipientId());
			return;
		}
		WechatSubscribeMsgDTO dto = new WechatSubscribeMsgDTO();
		dto.setAppId(properties.getAppId());
		dto.setOpenid(openid);
		dto.setTemplateId(templateId);
		dto.setPage(parsePage(command.getJumpPayload()));
		dto.setData(buildTemplateData(command));
		wechatSubscribeService.sendSubscribeMessage(dto);
	}

	private String parsePage(String jumpPayload) {
		if (!StringUtils.hasText(jumpPayload)) {
			return null;
		}
		if (jumpPayload.startsWith("/")) {
			return jumpPayload;
		}
		return jumpPayload;
	}

	private List<Map<String, String>> buildTemplateData(MessageSendCommand command) {
		List<Map<String, String>> data = new ArrayList<>();
		data.add(Map.of("key", "thing1", "value", truncate(command.getTitle(), 20)));
		if (StringUtils.hasText(command.getSummary())) {
			data.add(Map.of("key", "thing2", "value", truncate(command.getSummary(), 20)));
		}
		data.add(Map.of("key", "time3", "value", java.time.LocalDateTime.now()
				.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"))));
		return data;
	}

	private String truncate(String text, int max) {
		if (text == null) {
			return "";
		}
		return text.length() > max ? text.substring(0, max) : text;
	}
}