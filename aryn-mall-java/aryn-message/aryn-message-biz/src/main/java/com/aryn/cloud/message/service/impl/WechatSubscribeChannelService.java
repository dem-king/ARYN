package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.message.api.entity.MessageChannelTask;
import com.aryn.cloud.message.mapper.MessageChannelTaskMapper;
import com.aryn.cloud.upms.api.remote.RemoteStaffWechatBindingService;
import com.aryn.cloud.user.api.dto.MiniAppSubscribeResult;
import com.aryn.cloud.user.api.remote.RemoteMiniAppGateway;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 微信小程序订阅消息通道执行器。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatSubscribeChannelService {

	private static final int MAX_RETRY_COUNT = 6;
	private static final List<Integer> RETRY_MINUTES = List.of(1, 5, 15, 30, 60, 120);

	private final MessageChannelTaskMapper taskMapper;
	@DubboReference
	private final RemoteStaffWechatBindingService bindingService;
	@DubboReference
	private final RemoteMiniAppGateway miniAppGateway;
	private final ObjectMapper objectMapper;

	public void dispatch(MessageChannelTask task) {
		LocalDateTime attemptTime = LocalDateTime.now();
		if (task == null || taskMapper.markSending(task.getTenantId(), task.getId(), attemptTime) != 1) {
			return;
		}
		try {
			ControlledTemplate template = parseTemplate(task.getTemplateParams());
			if (!StringUtils.hasText(template.appId()) || !StringUtils.hasText(task.getTemplateCode())
					|| !StringUtils.hasText(template.page())) {
				markTerminal(task, "微信订阅模板配置不完整", attemptTime);
				return;
			}
			String openId = bindingService.getBoundOpenId(task.getTenantId(), task.getRecipientId(), template.appId());
			if (!StringUtils.hasText(openId)) {
				markTerminal(task, "员工未绑定配送微信", attemptTime);
				return;
			}
			MiniAppSubscribeResult result = miniAppGateway.sendSubscribeMessage(template.appId(), openId,
				task.getTemplateCode(), template.data(), template.page());
			handleResult(task, result, attemptTime);
		}
		catch (IllegalArgumentException exception) {
			markTerminal(task, exception.getMessage(), attemptTime);
		}
		catch (RuntimeException exception) {
			log.warn("微信订阅消息发送异常 tenantId={}, taskId={}", task.getTenantId(), task.getId(), exception);
			markRetry(task, "微信订阅消息发送异常", attemptTime);
		}
	}

	private ControlledTemplate parseTemplate(String templateParams) {
		try {
			Map<String, Object> root = objectMapper.readValue(templateParams, new TypeReference<>() { });
			String appId = value(root.get("appId"));
			String page = value(root.get("page"));
			Map<String, String> data = objectMapper.convertValue(root.get("data"), new TypeReference<>() { });
			return new ControlledTemplate(appId, page, data == null ? Map.of() : data);
		}
		catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new IllegalArgumentException("微信订阅模板参数无效", exception);
		}
	}

	private void handleResult(MessageChannelTask task, MiniAppSubscribeResult result, LocalDateTime attemptTime) {
		if (result != null && "SUCCESS".equals(result.getStatus())) {
			taskMapper.markSuccess(task.getTenantId(), task.getId(), attemptTime);
			return;
		}
		String error = result == null ? "微信订阅消息未返回结果" : errorOf(result);
		if (result != null && "TERMINAL".equals(result.getStatus())) {
			markTerminal(task, error, attemptTime);
			return;
		}
		markRetry(task, error, attemptTime);
	}

	private void markTerminal(MessageChannelTask task, String error, LocalDateTime attemptTime) {
		taskMapper.markTerminal(task.getTenantId(), task.getId(), error, attemptTime);
	}

	private void markRetry(MessageChannelTask task, String error, LocalDateTime attemptTime) {
		int retryCount = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
		if (retryCount > MAX_RETRY_COUNT) {
			markTerminal(task, "重试次数已耗尽:" + error, attemptTime);
			return;
		}
		int delayMinutes = RETRY_MINUTES.get(Math.min(retryCount - 1, RETRY_MINUTES.size() - 1));
		taskMapper.markRetry(task.getTenantId(), task.getId(), retryCount,
			attemptTime.plusMinutes(delayMinutes), error, attemptTime);
	}

	private String errorOf(MiniAppSubscribeResult result) {
		if (StringUtils.hasText(result.getErrorCode())) {
			return result.getErrorCode() + ":" + value(result.getErrorMessage());
		}
		return StringUtils.hasText(result.getErrorMessage()) ? result.getErrorMessage() : "微信订阅消息发送失败";
	}

	private String value(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private record ControlledTemplate(String appId, String page, Map<String, String> data) {
	}
}
