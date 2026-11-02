package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.promotion.api.entity.SeckillActivity;
import com.aryn.cloud.promotion.api.entity.SeckillGoods;
import com.aryn.cloud.promotion.api.entity.SeckillGoodsTemplate;
import com.aryn.cloud.promotion.api.entity.SeckillOrder;
import com.aryn.cloud.promotion.api.entity.SeckillSession;
import com.aryn.cloud.promotion.api.entity.SeckillSessionTemplate;
import com.aryn.cloud.promotion.api.entity.SeckillSubscribe;
import com.aryn.cloud.promotion.api.enums.SeckillOrderStatusEnum;
import com.aryn.cloud.promotion.api.vo.SeckillReminderConfigVO;
import com.aryn.cloud.promotion.config.SeckillReminderProperties;
import com.aryn.cloud.promotion.mapper.SeckillActivityMapper;
import com.aryn.cloud.promotion.mapper.SeckillOrderMapper;
import com.aryn.cloud.promotion.service.ISeckillGoodsService;
import com.aryn.cloud.promotion.service.ISeckillGoodsTemplateService;
import com.aryn.cloud.promotion.service.ISeckillScheduleService;
import com.aryn.cloud.promotion.service.ISeckillSessionService;
import com.aryn.cloud.promotion.service.ISeckillSessionTemplateService;
import com.aryn.cloud.promotion.service.ISeckillSubscribeService;
import com.aryn.cloud.promotion.service.SeckillStockManager;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillScheduleServiceImpl implements ISeckillScheduleService {

	private static final DateTimeFormatter TIME_TEXT_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");

	private static final DateTimeFormatter HOUR_MINUTE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	/** 场次结束后 55 分钟内允许顺延：Redis 库存 key 的 TTL 是结束 + 1 小时 */
	private static final long CARRYOVER_WINDOW_MINUTES = 55L;

	private final SeckillActivityMapper seckillActivityMapper;

	private final SeckillOrderMapper seckillOrderMapper;

	private final ISeckillSessionService seckillSessionService;

	private final ISeckillGoodsService seckillGoodsService;

	private final ISeckillSessionTemplateService seckillSessionTemplateService;

	private final ISeckillGoodsTemplateService seckillGoodsTemplateService;

	private final ISeckillSubscribeService seckillSubscribeService;

	private final SeckillStockManager seckillStockManager;

	private final SeckillReminderProperties reminderProperties;

	private final RocketMQTemplate rocketMQTemplate;

	private final ObjectMapper objectMapper;

	@Override
	public int generateSessions(int days) {
		return generateSessionsInternal(null, days);
	}

	@Override
	public int generateForActivity(String activityId, int days) {
		return generateSessionsInternal(activityId, days);
	}

	@Override
	public int rebuildFutureSessions(String activityId) {
		LocalDateTime now = LocalDateTime.now();
		List<SeckillSession> futureSessions = seckillSessionService.list(
				Wrappers.<SeckillSession>lambdaQuery()
						.eq(SeckillSession::getActivityId, activityId)
						.isNotNull(SeckillSession::getTemplateId)
						.eq(SeckillSession::getStatus, 0)
						.gt(SeckillSession::getStartTime, now));
		for (SeckillSession session : futureSessions) {
			boolean hasOrders = seckillOrderMapper.selectCount(Wrappers.<SeckillOrder>lambdaQuery()
					.eq(SeckillOrder::getSessionId, session.getId())) > 0;
			boolean hasSubscriptions = seckillSubscribeService.count(Wrappers.<SeckillSubscribe>lambdaQuery()
					.eq(SeckillSubscribe::getSessionId, session.getId())
					.in(SeckillSubscribe::getStatus, SeckillSubscribe.STATUS_PENDING,
							SeckillSubscribe.STATUS_SENT)) > 0;
			// 已有订单或提醒订阅的场次不能换 ID，保留旧配置
			if (hasOrders || hasSubscriptions) {
				continue;
			}
			List<SeckillGoods> goodsList = seckillGoodsService.listBySessionId(session.getId());
			for (SeckillGoods goods : goodsList) {
				seckillStockManager.removeStock(activityId, session.getId(), goods.getSkuId());
			}
			seckillGoodsService.remove(Wrappers.<SeckillGoods>lambdaQuery()
					.eq(SeckillGoods::getSessionId, session.getId()));
			seckillSessionService.removeById(session.getId());
		}
		return generateSessionsInternal(activityId, 3);
	}

	/**
	 * 按「活动 × 日期 × 模板」生成场次实例，唯一键 template_id + biz_date 保证幂等。
	 */
	private int generateSessionsInternal(String activityId, int days) {
		LocalDateTime now = LocalDateTime.now();
		LocalDate today = now.toLocalDate();
		LocalDate lastDate = today.plusDays(Math.max(1, days) - 1L);
		List<SeckillActivity> activities = seckillActivityMapper.selectList(
				Wrappers.<SeckillActivity>lambdaQuery()
						.eq(SeckillActivity::getScheduleType, 1)
						.ne(SeckillActivity::getStatus, 3)
						.gt(SeckillActivity::getEndTime, now)
						.eq(StringUtils.hasText(activityId), SeckillActivity::getId, activityId));
		if (activities.isEmpty()) {
			return 0;
		}
		int created = 0;
		for (SeckillActivity activity : activities) {
			List<SeckillSessionTemplate> templates = seckillSessionTemplateService
					.listByActivityId(activity.getId()).stream()
					.filter(template -> template.getStatus() == null || template.getStatus() == 0)
					.toList();
			if (templates.isEmpty()) {
				continue;
			}
			Map<String, List<SeckillGoodsTemplate>> goodsByTemplate = seckillGoodsTemplateService
					.listByTemplateIds(templates.stream().map(SeckillSessionTemplate::getId).toList())
					.stream()
					.collect(Collectors.groupingBy(SeckillGoodsTemplate::getTemplateId));
			LocalDate startDate = today.isAfter(activity.getStartTime().toLocalDate())
					? today : activity.getStartTime().toLocalDate();
			LocalDate endDate = lastDate.isBefore(activity.getEndTime().toLocalDate())
					? lastDate : activity.getEndTime().toLocalDate();
			for (LocalDate bizDate = startDate; !bizDate.isAfter(endDate); bizDate = bizDate.plusDays(1)) {
				int weekday = bizDate.getDayOfWeek().getValue();
				for (SeckillSessionTemplate template : templates) {
					if (!weekdayMatches(template.getWeekdays(), weekday)) {
						continue;
					}
					LocalDateTime startTime = bizDate.atTime(template.getStartTime());
					LocalDateTime endTime = bizDate.atTime(template.getEndTime());
					// 任务停摆后恢复时，不再补建已经结束的场次
					if (!endTime.isAfter(now)) {
						continue;
					}
					boolean exists = seckillSessionService.count(Wrappers.<SeckillSession>lambdaQuery()
							.eq(SeckillSession::getTemplateId, template.getId())
							.eq(SeckillSession::getBizDate, bizDate)) > 0;
					if (exists) {
						continue;
					}
					SeckillSession session = new SeckillSession();
					session.setTemplateId(template.getId());
					session.setBizDate(bizDate);
					session.setActivityId(activity.getId());
					session.setSessionName(template.getSessionName());
					session.setStartTime(startTime);
					session.setEndTime(endTime);
					session.setStatus(0);
					session.setCarryoverDone(0);
					try {
						seckillSessionService.save(session);
					}
					catch (DuplicateKeyException exception) {
						log.debug("秒杀场次已由其它实例生成, templateId={}, bizDate={}", template.getId(), bizDate);
						continue;
					}
					List<SeckillGoodsTemplate> goodsTemplates = goodsByTemplate
							.getOrDefault(template.getId(), List.of());
					if (!goodsTemplates.isEmpty()) {
						List<SeckillGoods> goodsList = goodsTemplates.stream().map(goodsTemplate -> {
							SeckillGoods goods = new SeckillGoods();
							goods.setActivityId(activity.getId());
							goods.setSessionId(session.getId());
							goods.setSpuId(goodsTemplate.getSpuId());
							goods.setSkuId(goodsTemplate.getSkuId());
							goods.setSeckillPrice(goodsTemplate.getSeckillPrice());
							goods.setSeckillStock(goodsTemplate.getSeckillStock());
							goods.setLimitPerUser(goodsTemplate.getLimitPerUser() == null
									? 1 : goodsTemplate.getLimitPerUser());
							goods.setSoldCount(0);
							return goods;
						}).toList();
						seckillGoodsService.saveBatch(goodsList);
						Duration ttl = ttlFor(endTime, LocalDateTime.now());
						for (SeckillGoods goods : goodsList) {
							seckillStockManager.initStock(activity.getId(), session.getId(), goods.getSkuId(),
									goods.getSeckillStock(), goods.getLimitPerUser(), ttl);
						}
					}
					created++;
					log.info("秒杀场次生成, activityId={}, templateId={}, bizDate={}, sessionId={}",
							activity.getId(), template.getId(), bizDate, session.getId());
				}
			}
		}
		return created;
	}

	@Override
	public int carryoverEndedSessions() {
		LocalDateTime now = LocalDateTime.now();
		List<SeckillSession> endedSessions = seckillSessionService.list(
				Wrappers.<SeckillSession>lambdaQuery()
						.isNotNull(SeckillSession::getTemplateId)
						.eq(SeckillSession::getCarryoverDone, 0)
						.le(SeckillSession::getEndTime, now)
						.gt(SeckillSession::getEndTime, now.minusMinutes(CARRYOVER_WINDOW_MINUTES))
						.orderByAsc(SeckillSession::getEndTime));
		int carried = 0;
		for (SeckillSession ended : endedSessions) {
			// 先抢占标记再处理：失败最多漏顺延，不会重复加库存
			boolean claimed = seckillSessionService.update(Wrappers.<SeckillSession>lambdaUpdate()
					.eq(SeckillSession::getId, ended.getId())
					.eq(SeckillSession::getCarryoverDone, 0)
					.set(SeckillSession::getCarryoverDone, 1));
			if (!claimed) {
				continue;
			}
			List<SeckillGoods> endedGoods = seckillGoodsService.listBySessionId(ended.getId());
			if (endedGoods.isEmpty()) {
				continue;
			}
			SeckillSession next = findNextSession(ended, now);
			if (next == null) {
				continue;
			}
			Map<String, SeckillGoods> nextBySku = seckillGoodsService.listBySessionId(next.getId()).stream()
					.collect(Collectors.toMap(SeckillGoods::getSkuId, goods -> goods, (left, right) -> left));
			if (nextBySku.isEmpty()) {
				continue;
			}
			for (SeckillGoods goods : endedGoods) {
				SeckillGoods target = nextBySku.get(goods.getSkuId());
				if (target == null) {
					continue;
				}
				int remaining = resolveRemaining(ended, goods);
				if (remaining <= 0) {
					continue;
				}
				seckillGoodsService.update(Wrappers.<SeckillGoods>lambdaUpdate()
						.eq(SeckillGoods::getId, target.getId())
						.setSql("seckill_stock = seckill_stock + " + remaining));
				seckillStockManager.carryoverStock(ended.getActivityId(), next.getId(),
						goods.getSkuId(), remaining);
				carried++;
				log.info("秒杀未售库存顺延, activityId={}, fromSessionId={}, toSessionId={}, skuId={}, quantity={}",
						ended.getActivityId(), ended.getId(), next.getId(), goods.getSkuId(), remaining);
			}
		}
		return carried;
	}

	@Override
	public int sendDueReminders() {
		if (!reminderProperties.isEnabled() || !StringUtils.hasText(reminderProperties.getTemplateId())) {
			return 0;
		}
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime until = now.plusMinutes(Math.max(1, reminderProperties.getOffsetMinutes()));
		List<SeckillSession> sessions = seckillSessionService.list(Wrappers.<SeckillSession>lambdaQuery()
				.gt(SeckillSession::getStartTime, now)
				.le(SeckillSession::getStartTime, until)
				.ne(SeckillSession::getStatus, 2)
				.orderByAsc(SeckillSession::getStartTime));
		int sent = 0;
		for (SeckillSession session : sessions) {
			SeckillActivity activity = seckillActivityMapper.selectById(session.getActivityId());
			// 活动暂停/删除后不再发送既有的提醒订阅
			if (activity == null || (activity.getStatus() != null && activity.getStatus() == 3)) {
				continue;
			}
			for (SeckillSubscribe subscribe : seckillSubscribeService.listPendingBySession(session.getId())) {
				if (!seckillSubscribeService.claim(subscribe.getId())) {
					continue;
				}
				try {
					publishReminder(session, subscribe);
					sent++;
				}
				catch (Exception exception) {
					seckillSubscribeService.markFailed(subscribe.getId(), exception.getMessage());
					log.error("秒杀开抢提醒投递失败, sessionId={}, mallUserId={}",
							session.getId(), subscribe.getMallUserId(), exception);
				}
			}
		}
		return sent;
	}

	@Override
	public SeckillReminderConfigVO getReminderConfig() {
		SeckillReminderConfigVO config = new SeckillReminderConfigVO();
		boolean enabled = reminderProperties.isEnabled()
				&& StringUtils.hasText(reminderProperties.getTemplateId());
		config.setEnabled(enabled);
		config.setTemplateId(reminderProperties.getTemplateId());
		config.setOffsetMinutes(Math.max(1, reminderProperties.getOffsetMinutes()));
		return config;
	}

	@Override
	public void subscribe(String sessionId, String mallUserId) {
		SeckillReminderConfigVO config = getReminderConfig();
		if (!Boolean.TRUE.equals(config.getEnabled())) {
			throw new ArynBusinessException("开抢提醒暂未开放");
		}
		SeckillSession session = seckillSessionService.getById(sessionId);
		if (session == null) {
			throw new ArynBusinessException("秒杀场次不存在");
		}
		SeckillActivity activity = seckillActivityMapper.selectById(session.getActivityId());
		if (activity == null || (activity.getStatus() != null && activity.getStatus() == 3)) {
			throw new ArynBusinessException("活动已暂停，无法设置提醒");
		}
		LocalDateTime now = LocalDateTime.now();
		if (!now.isBefore(session.getStartTime())) {
			throw new ArynBusinessException("场次已开始，无法设置提醒");
		}
		if (!session.getEndTime().isAfter(now)) {
			throw new ArynBusinessException("场次已结束，无法设置提醒");
		}
		boolean subscribed = seckillSubscribeService.subscribe(session.getActivityId(), sessionId,
				mallUserId, config.getTemplateId());
		if (!subscribed) {
			throw new ArynBusinessException("设置提醒失败，请重试");
		}
	}

	@Override
	public void unsubscribe(String sessionId, String mallUserId) {
		if (!seckillSubscribeService.unsubscribe(sessionId, mallUserId)) {
			throw new ArynBusinessException("提醒已发送，无法取消");
		}
	}

	@Override
	public boolean isSubscribed(String sessionId, String mallUserId) {
		return seckillSubscribeService.isSubscribed(sessionId, mallUserId);
	}

	@Override
	public Set<String> subscribedSessionIds(List<String> sessionIds, String mallUserId) {
		return seckillSubscribeService.subscribedSessionIds(sessionIds, mallUserId);
	}

	/**
	 * 当日下一场：同活动、开始时间不早于本场结束、且尚未结束的最近一场。
	 */
	private SeckillSession findNextSession(SeckillSession ended, LocalDateTime now) {
		LocalDateTime dayEnd = ended.getEndTime().toLocalDate().plusDays(1).atStartOfDay();
		return seckillSessionService.getOne(Wrappers.<SeckillSession>lambdaQuery()
				.eq(SeckillSession::getActivityId, ended.getActivityId())
				.ge(SeckillSession::getStartTime, ended.getEndTime())
				.lt(SeckillSession::getStartTime, dayEnd)
				.gt(SeckillSession::getEndTime, now)
				.ne(SeckillSession::getId, ended.getId())
				.orderByAsc(SeckillSession::getStartTime)
				.last("LIMIT 1"), false);
	}

	/**
	 * 剩余库存：优先 Redis（实时扣减）；key 缺失时用 DB 基线 - 已支付 - 未支付预扣。
	 */
	private int resolveRemaining(SeckillSession session, SeckillGoods goods) {
		Integer redisRemaining = seckillStockManager.getRemainingStock(
				session.getActivityId(), session.getId(), goods.getSkuId());
		if (redisRemaining != null) {
			return Math.max(0, redisRemaining);
		}
		int baseline = goods.getSeckillStock() == null ? 0 : goods.getSeckillStock();
		int sold = goods.getSoldCount() == null ? 0 : goods.getSoldCount();
		int unpaid = unpaidQuantity(session.getId(), goods.getSkuId());
		return Math.max(0, baseline - sold - unpaid);
	}

	private int unpaidQuantity(String sessionId, String skuId) {
		return seckillOrderMapper.selectList(Wrappers.<SeckillOrder>lambdaQuery()
				.eq(SeckillOrder::getSessionId, sessionId)
				.eq(SeckillOrder::getSkuId, skuId)
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.UNPAID.getCode()))
				.stream()
				.mapToInt(order -> order.getQuantity() == null ? 0 : order.getQuantity())
				.sum();
	}

	private void publishReminder(SeckillSession session, SeckillSubscribe subscribe) {
		MessageSendCommand command = new MessageSendCommand();
		command.setEventId("seckill-remind:" + subscribe.getId());
		command.setTenantId(StringUtils.hasText(subscribe.getTenantId())
				? subscribe.getTenantId() : ArynTenantContextHolder.getTenantId());
		command.setRecipientType(MessageIdentityType.MALL_USER.name());
		command.setRecipientId(subscribe.getMallUserId());
		command.setChannels(List.of("WECHAT_SUBSCRIBE"));
		command.setCategory("PROMOTION");
		command.setTitle("秒杀开抢提醒");
		String startText = session.getStartTime().format(HOUR_MINUTE_FORMATTER);
		String summary = session.getSessionName() + " " + startText + " 开抢";
		command.setSummary(summary.length() > 500 ? summary.substring(0, 500) : summary);
		command.setContent("您订阅的秒杀场次即将开始，点击进入限时秒杀会场。");
		command.setTimeText(session.getStartTime().format(TIME_TEXT_FORMATTER));
		command.setPriority("HIGH");
		command.setBizType("SECKILL_SESSION_START");
		command.setBizId(session.getId());
		command.setJumpPayload(reminderJumpPayload());
		rocketMQTemplate.syncSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC,
				new GenericMessage<>(command), RocketMqConstants.TIME_OUT);
	}

	private String reminderJumpPayload() {
		try {
			return objectMapper.writeValueAsString(Map.of("route", "/pages/promotion/seckill"));
		}
		catch (JsonProcessingException exception) {
			return "{\"route\":\"/pages/promotion/seckill\"}";
		}
	}

	private boolean weekdayMatches(String weekdays, int weekday) {
		if (!StringUtils.hasText(weekdays)) {
			return true;
		}
		for (String part : weekdays.split(",")) {
			try {
				if (Integer.parseInt(part.trim()) == weekday) {
					return true;
				}
			}
			catch (NumberFormatException ignored) {
				// 脏配置按不匹配处理
			}
		}
		return false;
	}

	private Duration ttlFor(LocalDateTime sessionEnd, LocalDateTime now) {
		Duration ttl = Duration.between(now, sessionEnd).plusHours(1);
		return ttl.isNegative() ? Duration.ofHours(1) : ttl;
	}
}
