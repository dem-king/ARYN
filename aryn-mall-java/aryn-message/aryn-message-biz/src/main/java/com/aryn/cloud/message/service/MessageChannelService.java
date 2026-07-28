package com.aryn.cloud.message.service;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.entity.MessageChannelTask;
import com.aryn.cloud.message.mapper.MessageChannelTaskMapper;
import com.aryn.cloud.message.service.impl.WechatSubscribeChannelService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 在站内通知事实提交后建立外部通道任务。 */
@Service
@RequiredArgsConstructor
public class MessageChannelService {

	private static final String WECHAT_SUBSCRIBE = "WECHAT_SUBSCRIBE";

	private final MessageChannelTaskMapper taskMapper;
	private final WechatSubscribeChannelService wechatSubscribeChannelService;
	private final ObjectMapper objectMapper;

	public void createAndDispatch(MessageSendCommand command, String messageId) {
		Set<String> channels = command.getChannels();
		if (channels == null || !channels.contains(WECHAT_SUBSCRIBE)) {
			return;
		}
		MessageChannelTask task = buildTask(command, messageId);
		if (taskMapper.insertIgnore(task) == 1) {
			wechatSubscribeChannelService.dispatch(task);
		}
	}

	private MessageChannelTask buildTask(MessageSendCommand command, String messageId) {
		LocalDateTime now = LocalDateTime.now();
		MessageChannelTask task = new MessageChannelTask();
		task.setId(IdWorker.getIdStr());
		task.setMessageId(messageId);
		task.setRecipientType(command.getRecipientType());
		task.setRecipientId(command.getRecipientId());
		task.setChannel(WECHAT_SUBSCRIBE);
		task.setTemplateCode(command.getTemplateCode());
		task.setTemplateParams(templateParams(command));
		task.setSourceType(command.getBizType());
		task.setSourceKey(command.getEventId());
		task.setStatus("PENDING");
		task.setRetryCount(0);
		task.setNextRetryTime(now);
		task.setTenantId(command.getTenantId());
		task.setCreateBy("SYSTEM");
		task.setCreateTime(now);
		task.setDelFlag(CommonConstants.NO);
		return task;
	}

	private String templateParams(MessageSendCommand command) {
		if (!StringUtils.hasText(command.getMiniAppId()) || !StringUtils.hasText(command.getTemplateCode())) {
			throw new IllegalArgumentException("微信订阅通道缺少小程序或模板配置");
		}
		Map<String, String> data = new LinkedHashMap<>();
		data.put("thing1", command.getTitle());
		data.put("character_string2", command.getBizId());
		data.put("thing3", StringUtils.hasText(command.getSummary()) ? command.getSummary() : command.getContent());
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("appId", command.getMiniAppId());
		payload.put("page", "pages/tasks/detail?id=" + command.getBizId());
		payload.put("data", data);
		try {
			return objectMapper.writeValueAsString(payload);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("微信订阅模板参数序列化失败", exception);
		}
	}
}
