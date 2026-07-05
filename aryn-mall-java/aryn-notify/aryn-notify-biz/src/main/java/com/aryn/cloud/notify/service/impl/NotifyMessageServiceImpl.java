
package com.aryn.cloud.notify.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.notify.api.dto.NotifySendDTO;
import com.aryn.cloud.notify.api.entity.NotifyMessage;
import com.aryn.cloud.notify.api.entity.NotifyTemplate;
import com.aryn.cloud.notify.api.enums.NotifyReadStatusEnum;
import com.aryn.cloud.notify.api.enums.NotifyTypeEnum;
import com.aryn.cloud.notify.api.vo.NotifyMessageVO;
import com.aryn.cloud.notify.api.vo.NotifyUnreadCountVO;
import com.aryn.cloud.notify.mapper.NotifyMessageMapper;
import com.aryn.cloud.notify.mapper.NotifyTemplateMapper;
import com.aryn.cloud.notify.service.INotifyMessageService;
import com.aryn.cloud.notify.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息记录 Service 实现
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyMessageServiceImpl implements INotifyMessageService {

	private final NotifyMessageMapper notifyMessageMapper;

	private final NotifyTemplateMapper notifyTemplateMapper;

	private final WebSocketSessionManager sessionManager;

	private final StringRedisTemplate stringRedisTemplate;

	/** Redis 未读计数 key：notify:unread:{userId}:{notifyType}，notifyType=0 表示总未读 */
	private static final String UNREAD_KEY = "notify:unread:%s:%s";

	@Override
	@Async("hxAsyncExecutor")
	public void sendMessage(NotifySendDTO dto) {
		try {
			// 1. 使用模板渲染
			if (StrUtil.isNotBlank(dto.getTemplateCode())) {
				NotifyTemplate template = notifyTemplateMapper.selectByCode(dto.getTemplateCode());
				if (template == null) {
					log.warn("消息模板不存在: {}", dto.getTemplateCode());
					return;
				}
				Map<String, String> params = dto.getParams() == null ? new HashMap<>() : dto.getParams();
				dto.setTitle(renderTemplate(template.getTitle(), params));
				dto.setContent(renderTemplate(template.getContent(), params));
				dto.setNotifyType(template.getNotifyType());
				dto.setJumpType(template.getJumpType());
				if (StrUtil.isNotBlank(template.getJumpUrl())) {
					dto.setJumpUrl(renderTemplate(template.getJumpUrl(), params));
				}
			}

			if (dto.getJumpType() == null) {
				dto.setJumpType(0);
			}

			// 2. 持久化
			NotifyMessage message = new NotifyMessage();
			message.setUserId(dto.getUserId());
			message.setNotifyType(dto.getNotifyType());
			message.setTitle(dto.getTitle());
			message.setContent(dto.getContent());
			message.setBizType(dto.getBizType());
			message.setBizId(dto.getBizId());
			message.setJumpType(dto.getJumpType());
			message.setJumpUrl(dto.getJumpUrl());
			message.setReadStatus(NotifyReadStatusEnum.UNREAD.getCode());
			notifyMessageMapper.insert(message);

			// 3. Redis 未读计数 +1
			String typeKey = String.format(UNREAD_KEY, dto.getUserId(), dto.getNotifyType());
			String totalKey = String.format(UNREAD_KEY, dto.getUserId(), 0);
			stringRedisTemplate.opsForValue().increment(typeKey);
			stringRedisTemplate.opsForValue().increment(totalKey);

			// 4. WebSocket 实时推送
			if (sessionManager.isOnline(dto.getUserId())) {
				NotifyMessageVO vo = new NotifyMessageVO();
				BeanUtils.copyProperties(message, vo);
				sessionManager.sendToUser(dto.getUserId(), vo);
			}
		}
		catch (Exception e) {
			log.error("发送站内信失败: dto={}", dto, e);
		}
	}

	@Override
	public IPage<NotifyMessageVO> pageUserMessages(Page<NotifyMessage> page, String userId, Integer notifyType) {
		LambdaQueryWrapper<NotifyMessage> wrapper = new LambdaQueryWrapper<NotifyMessage>()
			.eq(NotifyMessage::getUserId, userId)
			.eq(notifyType != null, NotifyMessage::getNotifyType, notifyType)
			.orderByDesc(NotifyMessage::getCreateTime);
		IPage<NotifyMessage> result = notifyMessageMapper.selectPage(page, wrapper);
		// 转换为 VO
		Page<NotifyMessageVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
		List<NotifyMessageVO> voList = result.getRecords().stream().map(m -> {
			NotifyMessageVO vo = new NotifyMessageVO();
			BeanUtils.copyProperties(m, vo);
			return vo;
		}).toList();
		voPage.setRecords(voList);
		return voPage;
	}

	@Override
	public IPage<NotifyMessage> pageAdminMessages(Page<NotifyMessage> page, NotifyMessage notifyMessage) {
		LambdaQueryWrapper<NotifyMessage> wrapper = new LambdaQueryWrapper<NotifyMessage>()
			.eq(StrUtil.isNotBlank(notifyMessage.getUserId()), NotifyMessage::getUserId, notifyMessage.getUserId())
			.eq(notifyMessage.getNotifyType() != null, NotifyMessage::getNotifyType, notifyMessage.getNotifyType())
			.eq(StrUtil.isNotBlank(notifyMessage.getReadStatus()), NotifyMessage::getReadStatus,
					notifyMessage.getReadStatus())
			.like(StrUtil.isNotBlank(notifyMessage.getTitle()), NotifyMessage::getTitle, notifyMessage.getTitle())
			.orderByDesc(NotifyMessage::getCreateTime);
		return notifyMessageMapper.selectPage(page, wrapper);
	}

	@Override
	public NotifyUnreadCountVO getUnreadCount(String userId) {
		NotifyUnreadCountVO vo = new NotifyUnreadCountVO();
		int total = 0;
		for (NotifyTypeEnum type : NotifyTypeEnum.values()) {
			String key = String.format(UNREAD_KEY, userId, type.getCode());
			String count = stringRedisTemplate.opsForValue().get(key);
			int c = count != null ? Integer.parseInt(count) : 0;
			vo.put(type.getCode(), c);
			total += c;
		}
		vo.setTotal(total);
		return vo;
	}

	@Override
	public void markAsRead(String userId, String messageId) {
		NotifyMessage message = notifyMessageMapper.selectById(messageId);
		if (message == null || !message.getUserId().equals(userId)) {
			throw new ArynBusinessException("消息不存在或无权操作");
		}
		if (NotifyReadStatusEnum.UNREAD.getCode().equals(message.getReadStatus())) {
			message.setReadStatus(NotifyReadStatusEnum.READ.getCode());
			message.setReadTime(LocalDateTime.now());
			notifyMessageMapper.updateById(message);
			// Redis 未读计数 -1
			stringRedisTemplate.opsForValue().decrement(String.format(UNREAD_KEY, userId, message.getNotifyType()));
			stringRedisTemplate.opsForValue().decrement(String.format(UNREAD_KEY, userId, 0));
		}
	}

	@Override
	public void markAllAsRead(String userId, Integer notifyType) {
		// 批量更新已读
		notifyMessageMapper.update(null, new LambdaUpdateWrapper<NotifyMessage>()
			.eq(NotifyMessage::getUserId, userId)
			.eq(NotifyMessage::getReadStatus, NotifyReadStatusEnum.UNREAD.getCode())
			.eq(notifyType != null, NotifyMessage::getNotifyType, notifyType)
			.set(NotifyMessage::getReadStatus, NotifyReadStatusEnum.READ.getCode())
			.set(NotifyMessage::getReadTime, LocalDateTime.now()));
		// Redis 清零
		if (notifyType != null) {
			stringRedisTemplate.delete(String.format(UNREAD_KEY, userId, notifyType));
		}
		else {
			for (NotifyTypeEnum type : NotifyTypeEnum.values()) {
				stringRedisTemplate.delete(String.format(UNREAD_KEY, userId, type.getCode()));
			}
			stringRedisTemplate.delete(String.format(UNREAD_KEY, userId, 0));
		}
	}

	@Override
	public void deleteMessage(String userId, String messageId) {
		NotifyMessage message = notifyMessageMapper.selectById(messageId);
		if (message == null || !message.getUserId().equals(userId)) {
			throw new ArynBusinessException("消息不存在或无权操作");
		}
		// 如果是未读消息，删除时同步减未读计数
		if (NotifyReadStatusEnum.UNREAD.getCode().equals(message.getReadStatus())) {
			stringRedisTemplate.opsForValue().decrement(String.format(UNREAD_KEY, userId, message.getNotifyType()));
			stringRedisTemplate.opsForValue().decrement(String.format(UNREAD_KEY, userId, 0));
		}
		notifyMessageMapper.deleteById(messageId);
	}

	/**
	 * 模板变量渲染：${var} → params.get(var)
	 */
	private String renderTemplate(String template, Map<String, String> params) {
		if (StrUtil.isBlank(template) || CollUtil.isEmpty(params)) {
			return template;
		}
		String result = template;
		for (Map.Entry<String, String> entry : params.entrySet()) {
			result = result.replace("${" + entry.getKey() + "}",
					entry.getValue() == null ? "" : entry.getValue());
		}
		return result;
	}

}
