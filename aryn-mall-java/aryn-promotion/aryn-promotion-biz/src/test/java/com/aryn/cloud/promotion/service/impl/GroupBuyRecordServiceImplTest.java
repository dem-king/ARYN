package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.remote.RemoteOrderRefundService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import com.aryn.cloud.promotion.api.entity.GroupBuyMember;
import com.aryn.cloud.promotion.api.entity.GroupBuyRecord;
import com.aryn.cloud.promotion.api.enums.GroupBuyMemberStatusEnum;
import com.aryn.cloud.promotion.api.enums.GroupBuyRecordStatusEnum;
import com.aryn.cloud.promotion.api.vo.GroupBuyOrderContextVO;
import com.aryn.cloud.promotion.mapper.GroupBuyActivityMapper;
import com.aryn.cloud.promotion.mapper.GroupBuyRecordMapper;
import com.aryn.cloud.promotion.service.IGroupBuyMemberService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 拼团下单链路与成团结算语义测试。
 *
 * <p>覆盖三处此前的断裂点：下单取价上下文校验、订单绑定（成团判定前提）、
 * 支付成功按付款人数成团、团结束后的迟到支付必须退款。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupBuyRecordServiceImplTest {

	@Mock
	private GroupBuyActivityMapper activityMapper;

	@Mock
	private IGroupBuyMemberService groupBuyMemberService;

	@Mock
	private GroupBuyRecordMapper recordMapper;

	@Mock
	private RemoteGoodsSkuService remoteGoodsSkuService;

	@Mock
	private RedissonClient redissonClient;

	@Mock
	private RemoteOrderRefundService remoteOrderRefundService;

	private GroupBuyRecordServiceImpl service;

	/**
	 * Lambda 条件构造（LambdaQueryWrapper/LambdaUpdateWrapper）需要实体的 TableInfo 缓存，
	 * 否则报 "can not find lambda cache for this entity"。
	 */
	@BeforeAll
	static void initMybatisMetadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GroupBuyMember.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GroupBuyRecord.class);
	}

	@BeforeEach
	void setUp() {
		service = new GroupBuyRecordServiceImpl(activityMapper, groupBuyMemberService, redissonClient,
				remoteOrderRefundService, remoteGoodsSkuService);
		org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", recordMapper);
	}

	private GroupBuyActivity activeActivity() {
		GroupBuyActivity activity = new GroupBuyActivity();
		activity.setId("act-1");
		activity.setActivityName("团购测试");
		activity.setSkuId("sku-1");
		activity.setSpuId("spu-1");
		activity.setGroupPrice(new BigDecimal("9.90"));
		activity.setOriginalPrice(new BigDecimal("19.90"));
		activity.setGroupNum(2);
		activity.setActivityStatus("1");
		activity.setStartedAt(LocalDateTime.now().minusHours(1));
		activity.setEndedAt(LocalDateTime.now().plusHours(1));
		return activity;
	}

	private GroupBuyRecord activeRecord() {
		GroupBuyRecord record = new GroupBuyRecord();
		record.setId("record-1");
		record.setActivityId("act-1");
		record.setSkuId("sku-1");
		record.setSpuId("spu-1");
		record.setGroupStatus(GroupBuyRecordStatusEnum.STATUS_0.getCode());
		record.setCurrentNum(1);
		record.setGroupNum(2);
		record.setLeaderUserId("user-1");
		record.setExpireAt(LocalDateTime.now().plusHours(2));
		return record;
	}

	private GroupBuyMember pendingMember() {
		GroupBuyMember member = new GroupBuyMember();
		member.setId("member-1");
		member.setRecordId("record-1");
		member.setActivityId("act-1");
		member.setUserId("user-1");
		member.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_0.getCode());
		return member;
	}

	@Test
	void getOrderContextReturnsGroupPriceForValidPendingMember() {
		when(recordMapper.selectById("record-1")).thenReturn(activeRecord());
		when(activityMapper.selectById("act-1")).thenReturn(activeActivity());
		when(groupBuyMemberService.getOne(any())).thenReturn(pendingMember());

		GroupBuyOrderContextVO context = service.getOrderContext("record-1", "user-1");

		assertThat(context.getRecordId()).isEqualTo("record-1");
		assertThat(context.getSkuId()).isEqualTo("sku-1");
		assertThat(context.getGroupPrice()).isEqualByComparingTo("9.90");
	}

	@Test
	void getOrderContextRejectsFinishedRecord() {
		GroupBuyRecord record = activeRecord();
		record.setGroupStatus(GroupBuyRecordStatusEnum.STATUS_2.getCode());
		when(recordMapper.selectById("record-1")).thenReturn(record);

		assertThatThrownBy(() -> service.getOrderContext("record-1", "user-1"))
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "该团已结束，无法下单");
	}

	@Test
	void getOrderContextRejectsExpiredRecord() {
		GroupBuyRecord record = activeRecord();
		record.setExpireAt(LocalDateTime.now().minusMinutes(1));
		when(recordMapper.selectById("record-1")).thenReturn(record);

		assertThatThrownBy(() -> service.getOrderContext("record-1", "user-1"))
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "该团已过期，无法下单");
	}

	@Test
	void getOrderContextRejectsMemberWithoutPendingSlot() {
		when(recordMapper.selectById("record-1")).thenReturn(activeRecord());
		when(activityMapper.selectById("act-1")).thenReturn(activeActivity());
		when(groupBuyMemberService.getOne(any())).thenReturn(null);

		assertThatThrownBy(() -> service.getOrderContext("record-1", "user-1"))
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "未找到可用的拼团参团资格，请重新开团或参团");
	}

	@Test
	void bindOrderUsesConditionalUpdateAndReportsFailure() {
		when(groupBuyMemberService.update(any())).thenReturn(true);

		assertThat(service.bindOrder("record-1", "user-1", "order-1")).isTrue();
		verify(groupBuyMemberService).update(any());
	}

	@Test
	void bindOrderReturnsFalseWhenSlotAlreadyTaken() {
		when(groupBuyMemberService.update(any())).thenReturn(false);

		assertThat(service.bindOrder("record-1", "user-1", "order-1")).isFalse();
	}

	@Test
	void releaseOrderClearsOrderLinkForPendingMember() {
		when(groupBuyMemberService.update(any())).thenReturn(true);

		assertThat(service.releaseOrder("order-1")).isTrue();
		verify(groupBuyMemberService).update(any());
	}

	@Test
	void releaseOrderIsIdempotentForBlankOrderId() {
		assertThat(service.releaseOrder("")).isTrue();
		assertThat(service.releaseOrder(null)).isTrue();
	}

	/**
	 * 记录列表要带出当前用户自己的参团状态与订单号：
	 * C 端据此区分「未参与 / 已占坑未下单 / 已下单待付款 / 已付款」，避免重复占坑。
	 */
	@Test
	void recordPageExposesCurrentUserMemberState() {
		when(recordMapper.selectPageByActivityId(any(), any())).thenReturn(recordPage(activeRecord()));
		GroupBuyMember mine = pendingMember();
		mine.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_0.getCode());
		mine.setOrderId("order-1");
		when(groupBuyMemberService.getOne(any())).thenReturn(mine);

		com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.promotion.api.vo.GroupBuyRecordVO> page
				= service.getPageByActivityId(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20),
						"act-1", "user-1");

		com.aryn.cloud.promotion.api.vo.GroupBuyRecordVO vo = page.getRecords().get(0);
		assertThat(vo.getIsJoined()).isTrue();
		assertThat(vo.getMyMemberStatus()).isEqualTo(GroupBuyMemberStatusEnum.STATUS_0.getCode());
		assertThat(vo.getMyOrderId()).isEqualTo("order-1");
	}

	@Test
	void recordPageMarksNotJoinedWhenUserHasNoMemberRow() {
		when(recordMapper.selectPageByActivityId(any(), any())).thenReturn(recordPage(activeRecord()));
		when(groupBuyMemberService.getOne(any())).thenReturn(null);

		com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.promotion.api.vo.GroupBuyRecordVO> page
				= service.getPageByActivityId(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20),
						"act-1", "user-1");

		com.aryn.cloud.promotion.api.vo.GroupBuyRecordVO vo = page.getRecords().get(0);
		assertThat(vo.getIsJoined()).isFalse();
		assertThat(vo.getMyMemberStatus()).isNull();
		assertThat(vo.getMyOrderId()).isNull();
	}

	/**
	 * 关键语义：参团占位不直接成团，必须等付款人数达成才成团。
	 */
	@Test
	void paySuccessMarksGroupSuccessOnlyWhenPaidCountReachesGroupNum() {
		GroupBuyMember member = pendingMember();
		when(groupBuyMemberService.getOne(any())).thenReturn(member);
		when(recordMapper.selectById("record-1")).thenReturn(activeRecord());
		when(groupBuyMemberService.count(any())).thenReturn(2L);

		service.handlePaySuccess("order-1");

		verify(groupBuyMemberService).updateById(member);
		assertThat(member.getMemberStatus()).isEqualTo(GroupBuyMemberStatusEnum.STATUS_1.getCode());
		verify(recordMapper).updateById(org.mockito.ArgumentMatchers.argThat(
				(GroupBuyRecord r) -> GroupBuyRecordStatusEnum.STATUS_1.getCode().equals(r.getGroupStatus())));
	}

	@Test
	void paySuccessKeepsGroupPendingWhenPaidCountBelowGroupNum() {
		GroupBuyMember member = pendingMember();
		when(groupBuyMemberService.getOne(any())).thenReturn(member);
		when(recordMapper.selectById("record-1")).thenReturn(activeRecord());
		when(groupBuyMemberService.count(any())).thenReturn(1L);

		service.handlePaySuccess("order-1");

		assertThat(member.getMemberStatus()).isEqualTo(GroupBuyMemberStatusEnum.STATUS_1.getCode());
		verify(recordMapper, org.mockito.Mockito.never())
				.updateById(org.mockito.ArgumentMatchers.<GroupBuyRecord>any());
	}

	/**
	 * 团已失败后支付到账（边界）：必须退款并回滚库存，不能让用户为失效的团付款。
	 */
	@Test
	void latePaymentAfterGroupClosedIsRefundedAndStockRolledBack() {
		GroupBuyMember member = pendingMember();
		member.setOrderId("order-1");
		when(groupBuyMemberService.getOne(any())).thenReturn(member);
		GroupBuyRecord failed = activeRecord();
		failed.setGroupStatus(GroupBuyRecordStatusEnum.STATUS_2.getCode());
		when(recordMapper.selectById("record-1")).thenReturn(failed);
		when(remoteOrderRefundService.refundWholeOrder("order-1", "拼团已结束自动退款")).thenReturn(true);

		service.handlePaySuccess("order-1");

		assertThat(member.getMemberStatus()).isEqualTo(GroupBuyMemberStatusEnum.STATUS_2.getCode());
		verify(remoteOrderRefundService).refundWholeOrder("order-1", "拼团已结束自动退款");
		verify(remoteGoodsSkuService).rollbackStock(any());
	}

	@Test
	void handleGroupExpireRefundsPaidMembersAndRollsBackTheirStock() {
		GroupBuyRecord expired = activeRecord();
		expired.setExpireAt(LocalDateTime.now().minusMinutes(1));
		when(recordMapper.selectPage(any(), any())).thenReturn(expiredPage(expired));
		GroupBuyMember paid = pendingMember();
		paid.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_1.getCode());
		paid.setOrderId("order-1");
		// processExpiredRecord 先查待付款占坑（无）、再查已付款成员（一条）——按调用顺序桩返回
		when(groupBuyMemberService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<GroupBuyMember>>any()))
				.thenReturn(java.util.List.of())
				.thenReturn(java.util.List.of(paid));
		when(remoteOrderRefundService.refundWholeOrder("order-1", "拼团失败自动退款")).thenReturn(true);

		service.handleGroupExpire();

		verify(remoteOrderRefundService).refundWholeOrder("order-1", "拼团失败自动退款");
		verify(remoteGoodsSkuService).rollbackStock(any());
	}

	private com.baomidou.mybatisplus.core.metadata.IPage<GroupBuyRecord> expiredPage(GroupBuyRecord record) {
		com.baomidou.mybatisplus.extension.plugins.pagination.Page<GroupBuyRecord> page
				= new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 100);
		page.setRecords(java.util.List.of(record));
		page.setTotal(1);
		return page;
	}

	private com.baomidou.mybatisplus.core.metadata.IPage<GroupBuyRecord> recordPage(GroupBuyRecord record) {
		com.baomidou.mybatisplus.extension.plugins.pagination.Page<GroupBuyRecord> page
				= new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
		page.setRecords(java.util.List.of(record));
		page.setTotal(1);
		return page;
	}
}
