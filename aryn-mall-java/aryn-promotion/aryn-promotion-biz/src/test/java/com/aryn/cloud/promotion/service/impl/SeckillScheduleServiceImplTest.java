package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.SeckillActivity;
import com.aryn.cloud.promotion.api.entity.SeckillGoods;
import com.aryn.cloud.promotion.api.entity.SeckillGoodsTemplate;
import com.aryn.cloud.promotion.api.entity.SeckillSession;
import com.aryn.cloud.promotion.api.entity.SeckillSessionTemplate;
import com.aryn.cloud.promotion.api.entity.SeckillSubscribe;
import com.aryn.cloud.promotion.config.SeckillReminderProperties;
import com.aryn.cloud.promotion.mapper.SeckillActivityMapper;
import com.aryn.cloud.promotion.mapper.SeckillOrderMapper;
import com.aryn.cloud.promotion.service.ISeckillGoodsService;
import com.aryn.cloud.promotion.service.ISeckillGoodsTemplateService;
import com.aryn.cloud.promotion.service.ISeckillSessionService;
import com.aryn.cloud.promotion.service.ISeckillSessionTemplateService;
import com.aryn.cloud.promotion.service.ISeckillSubscribeService;
import com.aryn.cloud.promotion.service.SeckillStockManager;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillScheduleServiceImplTest {

	private SeckillActivityMapper activityMapper;

	private SeckillOrderMapper orderMapper;

	private ISeckillSessionService sessionService;

	private ISeckillGoodsService goodsService;

	private ISeckillSessionTemplateService sessionTemplateService;

	private ISeckillGoodsTemplateService goodsTemplateService;

	private ISeckillSubscribeService subscribeService;

	private SeckillStockManager stockManager;

	private SeckillReminderProperties reminderProperties;

	private RocketMQTemplate rocketMQTemplate;

	private SeckillScheduleServiceImpl service;

	@BeforeAll
	static void initTableInfo() {
		// 纯 Mockito 单测不启动 MyBatis，lambda 条件需要先登记实体元数据
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
		TableInfoHelper.initTableInfo(assistant, SeckillActivity.class);
		TableInfoHelper.initTableInfo(assistant, SeckillSession.class);
		TableInfoHelper.initTableInfo(assistant, SeckillGoods.class);
		TableInfoHelper.initTableInfo(assistant, SeckillGoodsTemplate.class);
		TableInfoHelper.initTableInfo(assistant, SeckillSessionTemplate.class);
		TableInfoHelper.initTableInfo(assistant, SeckillSubscribe.class);
		TableInfoHelper.initTableInfo(assistant, com.aryn.cloud.promotion.api.entity.SeckillOrder.class);
	}

	@BeforeEach
	void setUp() {
		activityMapper = mock(SeckillActivityMapper.class);
		orderMapper = mock(SeckillOrderMapper.class);
		sessionService = mock(ISeckillSessionService.class);
		goodsService = mock(ISeckillGoodsService.class);
		sessionTemplateService = mock(ISeckillSessionTemplateService.class);
		goodsTemplateService = mock(ISeckillGoodsTemplateService.class);
		subscribeService = mock(ISeckillSubscribeService.class);
		stockManager = mock(SeckillStockManager.class);
		rocketMQTemplate = mock(RocketMQTemplate.class);
		reminderProperties = new SeckillReminderProperties();
		reminderProperties.setEnabled(true);
		reminderProperties.setTemplateId("tmpl-seckill");
		reminderProperties.setOffsetMinutes(10);
		service = new SeckillScheduleServiceImpl(activityMapper, orderMapper, sessionService, goodsService,
				sessionTemplateService, goodsTemplateService, subscribeService, stockManager,
				reminderProperties, rocketMQTemplate, new ObjectMapper());
		when(activityMapper.selectById("act-1"))
			.thenReturn(new SeckillActivity().setId("act-1").setStatus(0));
	}

	@Test
	void generateSessionsCreatesInstanceAndInitializesStock() {
		SeckillActivity activity = new SeckillActivity()
			.setId("act-1")
			.setScheduleType(1)
			.setStatus(0)
			.setStartTime(LocalDateTime.now().minusDays(1))
			.setEndTime(LocalDateTime.now().plusDays(10));
		when(activityMapper.selectList(any())).thenReturn(List.of(activity));
		SeckillSessionTemplate template = new SeckillSessionTemplate()
			.setId("tpl-1")
			.setActivityId("act-1")
			.setSessionName("10:00 场")
			.setStartTime(LocalTime.of(0, 0))
			.setEndTime(LocalTime.of(23, 59, 59))
			.setWeekdays("1,2,3,4,5,6,7")
			.setStatus(0);
		when(sessionTemplateService.listByActivityId("act-1")).thenReturn(List.of(template));
		SeckillGoodsTemplate goodsTemplate = new SeckillGoodsTemplate()
			.setId("gt-1")
			.setTemplateId("tpl-1")
			.setSpuId("spu-1")
			.setSkuId("sku-1")
			.setSeckillPrice(new BigDecimal("9.90"))
			.setSeckillStock(20)
			.setLimitPerUser(2);
		when(goodsTemplateService.listByTemplateIds(List.of("tpl-1"))).thenReturn(List.of(goodsTemplate));
		when(sessionService.count(any())).thenReturn(0L);
		when(sessionService.save(any())).thenAnswer(invocation -> {
			SeckillSession session = invocation.getArgument(0);
			session.setId("session-1");
			return true;
		});
		when(goodsService.saveBatch(any())).thenReturn(true);

		int created = service.generateSessions(3);

		assertThat(created).isEqualTo(3);
		verify(sessionService, org.mockito.Mockito.times(3)).save(any());
		verify(goodsService, org.mockito.Mockito.times(3)).saveBatch(any());
		verify(stockManager, org.mockito.Mockito.times(3)).initStock(
			eq("act-1"), eq("session-1"), eq("sku-1"), eq(20), eq(2), any());
	}

	@Test
	void generateSessionsSkipsExistingTemplateDate() {
		SeckillActivity activity = new SeckillActivity()
			.setId("act-1")
			.setScheduleType(1)
			.setStatus(0)
			.setStartTime(LocalDateTime.now().minusDays(1))
			.setEndTime(LocalDateTime.now().plusDays(10));
		when(activityMapper.selectList(any())).thenReturn(List.of(activity));
		when(sessionTemplateService.listByActivityId("act-1")).thenReturn(List.of(
			new SeckillSessionTemplate().setId("tpl-1").setSessionName("10:00 场")
				.setStartTime(LocalTime.of(0, 0)).setEndTime(LocalTime.of(23, 59, 59))
				.setWeekdays("1,2,3,4,5,6,7").setStatus(0)));
		when(goodsTemplateService.listByTemplateIds(any())).thenReturn(List.of());
		when(sessionService.count(any())).thenReturn(1L);

		int created = service.generateSessions(3);

		org.assertj.core.api.Assertions.assertThat(created).isZero();
		verify(sessionService, never()).save(any());
	}

	@Test
	void carryoverMovesUnsoldStockToNextSession() {
		LocalDateTime now = LocalDateTime.now();
		SeckillSession ended = new SeckillSession()
			.setId("s-ended")
			.setActivityId("act-1")
			.setTemplateId("tpl-1")
			.setStartTime(now.minusHours(2))
			.setEndTime(now.minusMinutes(5))
			.setCarryoverDone(0);
		SeckillSession next = new SeckillSession()
			.setId("s-next")
			.setActivityId("act-1")
			.setStartTime(now.plusMinutes(30))
			.setEndTime(now.plusHours(2))
			.setStatus(0);
		when(sessionService.list(any(Wrapper.class))).thenReturn(List.of(ended));
		when(sessionService.update(any())).thenReturn(true);
		SeckillGoods endedGoods = new SeckillGoods()
			.setId("g-ended").setSkuId("sku-1").setSeckillStock(20).setSoldCount(15);
		SeckillGoods nextGoods = new SeckillGoods()
			.setId("g-next").setSkuId("sku-1").setSeckillStock(20).setSoldCount(0);
		when(goodsService.listBySessionId("s-ended")).thenReturn(List.of(endedGoods));
		when(goodsService.listBySessionId("s-next")).thenReturn(List.of(nextGoods));
		when(sessionService.getOne(any(), eq(false))).thenReturn(next);
		when(stockManager.getRemainingStock("act-1", "s-ended", "sku-1")).thenReturn(5);

		int carried = service.carryoverEndedSessions();

		org.assertj.core.api.Assertions.assertThat(carried).isEqualTo(1);
		verify(goodsService).update(any());
		verify(stockManager).carryoverStock(eq("act-1"), eq("s-next"), eq("sku-1"), eq(5));
	}

	@Test
	void sendDueRemindersPublishesForPendingSubscription() {
		LocalDateTime now = LocalDateTime.now();
		SeckillSession session = new SeckillSession()
			.setId("s-1")
			.setActivityId("act-1")
			.setSessionName("10:00 场")
			.setStartTime(now.plusMinutes(5))
			.setEndTime(now.plusHours(2))
			.setStatus(0);
		SeckillSubscribe subscribe = new SeckillSubscribe()
			.setId("sub-1")
			.setTenantId("tenant-1")
			.setSessionId("s-1")
			.setMallUserId("user-1")
			.setStatus(SeckillSubscribe.STATUS_PENDING);
		when(sessionService.list(any(Wrapper.class))).thenReturn(List.of(session));
		when(subscribeService.listPendingBySession("s-1")).thenReturn(List.of(subscribe));
		when(subscribeService.claim("sub-1")).thenReturn(true);

		int sent = service.sendDueReminders();

		org.assertj.core.api.Assertions.assertThat(sent).isEqualTo(1);
		verify(rocketMQTemplate).syncSend(eq(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC),
			any(Message.class), anyLong());
		verify(subscribeService, never()).markFailed(anyString(), anyString());
	}

	@Test
	void subscribeRejectsAfterSessionStarted() {
		SeckillSession session = new SeckillSession()
			.setId("s-1")
			.setActivityId("act-1")
			.setStartTime(LocalDateTime.now().minusMinutes(1))
			.setEndTime(LocalDateTime.now().plusHours(1));
		when(sessionService.getById("s-1")).thenReturn(session);

		assertThatThrownBy(() -> service.subscribe("s-1", "user-1"))
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "场次已开始，无法设置提醒");
	}

	@Test
	void reminderConfigDisabledWhenTemplateMissing() {
		reminderProperties.setTemplateId("");

		var config = service.getReminderConfig();

		org.assertj.core.api.Assertions.assertThat(config.getEnabled()).isFalse();
	}
}
