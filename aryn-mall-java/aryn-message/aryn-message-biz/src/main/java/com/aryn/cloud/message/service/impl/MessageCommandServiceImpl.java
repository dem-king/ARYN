package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.entity.MessageNotice;
import com.aryn.cloud.message.api.entity.MessageRecipient;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.enums.NoticeJumpType;
import com.aryn.cloud.message.api.enums.NoticeStatus;
import com.aryn.cloud.message.mapper.MessageNoticeMapper;
import com.aryn.cloud.message.mapper.MessageRecipientMapper;
import com.aryn.cloud.message.service.MessageCommandService;
import com.aryn.cloud.message.service.MessagePushService;
import com.aryn.cloud.message.service.WechatSubscribeChannelService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 来源唯一键和收件人唯一键双重幂等的业务通知落库实现。 */
@Service
@RequiredArgsConstructor
public class MessageCommandServiceImpl implements MessageCommandService {

	private static final String CHANNEL_IN_APP = "IN_APP";
	private static final String CHANNEL_WECHAT_SUBSCRIBE = "WECHAT_SUBSCRIBE";

	private final MessageNoticeMapper noticeMapper;
	private final MessageRecipientMapper recipientMapper;
	private final MessagePushService pushService;
	private final WechatSubscribeChannelService wechatSubscribeChannelService;
	private final ObjectMapper objectMapper;
	private final Validator validator;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void consume(MessageSendCommand command) {
		validate(command);
		List<String> channels = command.getChannels() != null && !command.getChannels().isEmpty()
				? command.getChannels() : List.of(CHANNEL_IN_APP);
		MessageIdentityType recipientType = parseRecipientType(command.getRecipientType());
		boolean needInApp = channels.contains(CHANNEL_IN_APP);
		boolean needWechat = channels.contains(CHANNEL_WECHAT_SUBSCRIBE);
		MessageNotice notice = null;
		if (needInApp) {
			notice = noticeMapper.selectBySource(command.getTenantId(), command.getBizType(),
					command.getEventId());
			if (notice == null) {
				notice = notice(command, recipientType);
				if (noticeMapper.insertIgnoreSource(notice) == 0) {
					notice = noticeMapper.selectBySource(command.getTenantId(), command.getBizType(),
							command.getEventId());
					if (notice == null) {
						throw new ArynBusinessException("业务通知幂等记录读取失败");
					}
				}
			}
			MessageRecipient recipient = recipient(command, notice.getId(), recipientType);
			if (recipientMapper.insertIgnoreBatch(List.of(recipient)) > 0) {
				String messageId = notice.getId();
				runAfterCommit(() -> pushService.pushNotice(command.getTenantId(), recipientType.name(),
						command.getRecipientId(), messageId));
			}
		}
		if (needWechat) {
			MessageSendCommand wechatCommand = command;
			runAfterCommit(() -> {
				try {
					wechatSubscribeChannelService.send(wechatCommand);
				}
				catch (Exception e) {
					org.slf4j.LoggerFactory.getLogger(getClass()).warn("微信订阅消息发送失败: {}", e.getMessage());
				}
			});
		}
	}

	private MessageNotice notice(MessageSendCommand command, MessageIdentityType recipientType) {
		LocalDateTime now = LocalDateTime.now();
		MessageNotice notice = new MessageNotice();
		notice.setId(IdWorker.getIdStr());
		notice.setTenantId(command.getTenantId());
		notice.setTitle(command.getTitle().trim());
		notice.setSummary(command.getSummary());
		notice.setContent(command.getContent());
		notice.setCategory(command.getCategory());
		notice.setPriority(StringUtils.hasText(command.getPriority()) ? command.getPriority() : "NORMAL");
		notice.setSourceType(command.getBizType());
		notice.setSourceKey(command.getEventId());
		notice.setTargetTypes(recipientType.name());
		notice.setAudienceSnapshot(audienceSnapshot(command, recipientType));
		notice.setSenderType(MessageIdentityType.SYSTEM.name());
		notice.setSenderId("SYSTEM");
		notice.setSenderName("系统");
		notice.setStatus(NoticeStatus.PUBLISHED.name());
		notice.setPublishTime(now);
		notice.setCardPayload(command.getCardPayload());
		notice.setJumpType(StringUtils.hasText(command.getJumpPayload()) ? NoticeJumpType.BIZ_DETAIL.name()
				: NoticeJumpType.NONE.name());
		notice.setJumpPayload(command.getJumpPayload());
		notice.setCreateBy("SYSTEM");
		notice.setCreateTime(now);
		notice.setDelFlag(CommonConstants.NO);
		return notice;
	}

	private MessageRecipient recipient(MessageSendCommand command, String noticeId,
			MessageIdentityType recipientType) {
		LocalDateTime now = LocalDateTime.now();
		MessageRecipient recipient = new MessageRecipient();
		recipient.setId(IdWorker.getIdStr());
		recipient.setTenantId(command.getTenantId());
		recipient.setMessageId(noticeId);
		recipient.setRecipientType(recipientType.name());
		recipient.setRecipientId(command.getRecipientId());
		recipient.setRecipientName(command.getRecipientName());
		recipient.setReadStatus(CommonConstants.NO);
		recipient.setReceivedTime(now);
		recipient.setInboxStatus("VISIBLE");
		recipient.setCreateBy("SYSTEM");
		recipient.setCreateTime(now);
		recipient.setDelFlag(CommonConstants.NO);
		return recipient;
	}

	private String audienceSnapshot(MessageSendCommand command, MessageIdentityType recipientType) {
		try {
			return recipientType == MessageIdentityType.MALL_USER
					? objectMapper.writeValueAsString(java.util.Map.of("mallUserIds", List.of(command.getRecipientId())))
					: objectMapper.writeValueAsString(java.util.Map.of("staffUserIds", List.of(command.getRecipientId())));
		}
		catch (JsonProcessingException exception) {
			throw new ArynBusinessException("业务通知受众快照序列化失败");
		}
	}

	private void validate(MessageSendCommand command) {
		if (command == null) {
			throw new ArynBusinessException("业务通知命令不能为空");
		}
		Set<ConstraintViolation<MessageSendCommand>> violations = validator.validate(command);
		if (!violations.isEmpty()) {
			throw new ArynBusinessException("业务通知命令字段不完整: " + violations.iterator().next().getPropertyPath());
		}
		validateJson(command.getCardPayload(), "业务卡片");
		validateJson(command.getJumpPayload(), "跳转参数");
	}

	private void validateJson(String json, String fieldName) {
		if (!StringUtils.hasText(json)) {
			return;
		}
		try {
			if (!objectMapper.readTree(json).isObject()) {
				throw new ArynBusinessException(fieldName + "必须是 JSON 对象");
			}
		}
		catch (JsonProcessingException exception) {
			throw new ArynBusinessException(fieldName + "不是合法 JSON");
		}
	}

	private MessageIdentityType parseRecipientType(String value) {
		try {
			MessageIdentityType type = MessageIdentityType.valueOf(value);
			if (type == MessageIdentityType.SYSTEM) {
				throw new IllegalArgumentException();
			}
			return type;
		}
		catch (IllegalArgumentException exception) {
			throw new ArynBusinessException("业务通知收件人类型不合法");
		}
	}

	private void runAfterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}

}
