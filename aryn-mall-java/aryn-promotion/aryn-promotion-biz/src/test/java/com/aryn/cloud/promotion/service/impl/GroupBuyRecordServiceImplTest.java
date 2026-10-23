package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.remote.RemoteOrderRefundService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import com.aryn.cloud.promotion.api.entity.GroupBuyMember;
import com.aryn.cloud.promotion.api.entity.GroupBuyRecord;
import com.aryn.cloud.promotion.api.enums.GroupBuyMemberStatusEnum;
import com.aryn.cloud.promotion.api.enums.GroupBuyRecordStatusEnum;
import com.aryn.cloud.promotion.api.vo.GroupBuyMemberVO;
import com.aryn.cloud.promotion.api.vo.GroupBuyOrderContextVO;
import com.aryn.cloud.promotion.api.vo.GroupBuyRecordVO;
import com.aryn.cloud.promotion.mapper.GroupBuyActivityMapper;
import com.aryn.cloud.promotion.mapper.GroupBuyRecordMapper;
import com.aryn.cloud.promotion.service.IGroupBuyMemberService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

	@Mock
	private RemoteMallUserService remoteMallUserService;

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
				remoteOrderRefundService, remoteGoodsSkuService, remoteMallUserService);
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

	private GroupBuyRecord groupRecord(String recordId, String leaderUserId) {
		GroupBuyRecord record = activeRecord();
		record.setId(recordId);
		record.setLeaderUserId(leaderUserId);
		return record;
	}

	private GroupBuyMember member(String recordId, String userId, String isLeader, String orderId,
			LocalDateTime createTime) {
		GroupBuyMember member = new GroupBuyMember();
		member.setId("member-" + userId);
		member.setRecordId(recordId);
		member.setActivityId("act-1");
		member.setUserId(userId);
		member.setIsLeader(isLeader);
		member.setOrderId(orderId);
		member.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_0.getCode());
		member.setCreateTime(createTime);
		return member;
	}

	private UserInfoVO user(String userId, String nickname, String avatarUrl) {
		UserInfoVO userInfo = new UserInfoVO();
		userInfo.setId(userId);
		userInfo.setNickname(nickname);
		userInfo.setAvatarUrl(avatarUrl);
		return userInfo;
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
	 *
	 * <p>改造后「我的参团状态」复用本页成员批量查询结果，不再逐条 getOne。
	 */
	@Test
	void recordPageExposesCurrentUserMemberState() {
		when(recordMapper.selectPageByActivityId(any(), any())).thenReturn(recordPage(activeRecord()));
		GroupBuyMember mine = pendingMember();
		mine.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_0.getCode());
		mine.setOrderId("order-1");
		when(groupBuyMemberService.getByRecordIds(any())).thenReturn(List.of(mine));

		IPage<GroupBuyRecordVO> page = service.getPageByActivityId(new Page<>(1, 20), "act-1", "user-1");

		GroupBuyRecordVO vo = page.getRecords().get(0);
		assertThat(vo.getIsJoined()).isTrue();
		assertThat(vo.getMyMemberStatus()).isEqualTo(GroupBuyMemberStatusEnum.STATUS_0.getCode());
		assertThat(vo.getMyOrderId()).isEqualTo("order-1");
	}

	@Test
	void recordPageMarksNotJoinedWhenUserHasNoMemberRow() {
		when(recordMapper.selectPageByActivityId(any(), any())).thenReturn(recordPage(activeRecord()));
		when(groupBuyMemberService.getByRecordIds(any())).thenReturn(List.of());

		IPage<GroupBuyRecordVO> page = service.getPageByActivityId(new Page<>(1, 20), "act-1", "user-1");

		GroupBuyRecordVO vo = page.getRecords().get(0);
		assertThat(vo.getIsJoined()).isFalse();
		assertThat(vo.getMyMemberStatus()).isNull();
		assertThat(vo.getMyOrderId()).isNull();
	}

	/**
	 * 列表页昵称/头像装配：本页成员只批量查 1 次、用户资料只远程调 1 次（无 N+1），
	 * 团长昵称头像回填、成员列表团长置首且按 createTime 稳定排序。
	 */
	@Test
	void recordPageBatchAssemblesLeaderAndMemberNicknameAvatar() {
		LocalDateTime now = LocalDateTime.now();
		GroupBuyRecord record1 = activeRecord();
		GroupBuyRecord record2 = groupRecord("record-2", "user-3");
		when(recordMapper.selectPageByActivityId(any(), any())).thenReturn(recordPage(record1, record2));
		// 故意把非团长成员排在前面，验证装配后仍按「团长置首 + createTime 升序」输出
		when(groupBuyMemberService.getByRecordIds(any())).thenReturn(List.of(
				member("record-1", "user-2", "0", "order-9", now.minusMinutes(30)),
				member("record-1", "user-1", "1", null, now.minusHours(1)),
				member("record-2", "user-3", "1", null, now.minusHours(2))));
		when(remoteMallUserService.getUserByIds(any())).thenReturn(List.of(user("user-1", "团长甲", "https://cdn/u1.png"),
				user("user-2", "成员乙", "https://cdn/u2.png"), user("user-3", "团长丙", "https://cdn/u3.png")));

		IPage<GroupBuyRecordVO> page = service.getPageByActivityId(new Page<>(1, 20), "act-1", "user-2");

		assertThat(page.getRecords()).hasSize(2);
		GroupBuyRecordVO vo1 = page.getRecords().get(0);
		assertThat(vo1.getLeaderNickname()).isEqualTo("团长甲");
		assertThat(vo1.getLeaderAvatar()).isEqualTo("https://cdn/u1.png");
		assertThat(vo1.getMemberList()).extracting(GroupBuyMemberVO::getUserId).containsExactly("user-1", "user-2");
		assertThat(vo1.getMemberList()).extracting(GroupBuyMemberVO::getIsLeader).containsExactly("1", "0");
		assertThat(vo1.getMemberList()).extracting(GroupBuyMemberVO::getNickname).containsExactly("团长甲", "成员乙");
		assertThat(vo1.getMemberList()).extracting(GroupBuyMemberVO::getAvatarUrl)
			.containsExactly("https://cdn/u1.png", "https://cdn/u2.png");
		assertThat(vo1.getIsJoined()).isTrue();
		assertThat(vo1.getMyMemberStatus()).isEqualTo(GroupBuyMemberStatusEnum.STATUS_0.getCode());
		assertThat(vo1.getMyOrderId()).isEqualTo("order-9");

		GroupBuyRecordVO vo2 = page.getRecords().get(1);
		assertThat(vo2.getLeaderNickname()).isEqualTo("团长丙");
		assertThat(vo2.getMemberList()).hasSize(1);
		assertThat(vo2.getIsJoined()).isFalse();

		// 无 N+1：本页成员批量查询 1 次 + 用户资料远程批量调用 1 次（userId 去重后只传 3 个）
		verify(groupBuyMemberService, times(1)).getByRecordIds(any());
		verify(groupBuyMemberService, never()).getOne(any());
		verify(remoteMallUserService, times(1)).getUserByIds(org.mockito.ArgumentMatchers
			.argThat(ids -> ids != null && ids.size() == 3 && ids.containsAll(List.of("user-1", "user-2", "user-3"))));
	}

	/**
	 * 远程用户服务超时/异常时必须降级不阻断：头像留空、成员昵称回落「拼友」，
	 * isJoined/myMemberStatus/myOrderId 等原有字段语义仍正常。
	 */
	@Test
	void recordPageDegradesGracefullyWhenRemoteUserServiceFails() {
		LocalDateTime now = LocalDateTime.now();
		when(recordMapper.selectPageByActivityId(any(), any())).thenReturn(recordPage(activeRecord()));
		when(groupBuyMemberService.getByRecordIds(any())).thenReturn(List.of(
				member("record-1", "user-1", "1", null, now.minusHours(1)),
				member("record-1", "user-2", "0", "order-9", now.minusMinutes(30))));
		when(remoteMallUserService.getUserByIds(any())).thenThrow(new RuntimeException("dubbo timeout"));

		IPage<GroupBuyRecordVO> page = service.getPageByActivityId(new Page<>(1, 20), "act-1", "user-2");

		GroupBuyRecordVO vo = page.getRecords().get(0);
		assertThat(vo.getLeaderNickname()).isNull();
		assertThat(vo.getLeaderAvatar()).isNull();
		assertThat(vo.getMemberList()).hasSize(2);
		assertThat(vo.getMemberList()).allSatisfy(member -> {
			assertThat(member.getNickname()).isEqualTo("拼友");
			assertThat(member.getAvatarUrl()).isNull();
		});
		assertThat(vo.getMemberList().get(0).getIsLeader()).isEqualTo("1");
		assertThat(vo.getIsJoined()).isTrue();
		assertThat(vo.getMyMemberStatus()).isEqualTo(GroupBuyMemberStatusEnum.STATUS_0.getCode());
		assertThat(vo.getMyOrderId()).isEqualTo("order-9");
		assertThat(vo.getCurrentNum()).isEqualTo(1);
		verify(remoteMallUserService, times(1)).getUserByIds(any());
	}

	/**
	 * 远程批量结果缺少某个用户（用户被删除/跨租户）时逐人兜底：
	 * 该成员昵称「拼友」、头像空；未登录也不影响成员列表下发。
	 */
	@Test
	void recordPageFallsBackForUsersMissingFromRemoteResult() {
		LocalDateTime now = LocalDateTime.now();
		when(recordMapper.selectPageByActivityId(any(), any())).thenReturn(recordPage(activeRecord()));
		when(groupBuyMemberService.getByRecordIds(any())).thenReturn(List.of(
				member("record-1", "user-1", "1", null, now.minusHours(1)),
				member("record-1", "user-2", "0", null, now.minusMinutes(30))));
		when(remoteMallUserService.getUserByIds(any()))
			.thenReturn(List.of(user("user-1", "团长甲", "https://cdn/u1.png")));

		IPage<GroupBuyRecordVO> page = service.getPageByActivityId(new Page<>(1, 20), "act-1", null);

		GroupBuyRecordVO vo = page.getRecords().get(0);
		assertThat(vo.getLeaderNickname()).isEqualTo("团长甲");
		assertThat(vo.getLeaderAvatar()).isEqualTo("https://cdn/u1.png");
		assertThat(vo.getMemberList()).hasSize(2);
		GroupBuyMemberVO leader = vo.getMemberList().get(0);
		assertThat(leader.getUserId()).isEqualTo("user-1");
		assertThat(leader.getNickname()).isEqualTo("团长甲");
		GroupBuyMemberVO missing = vo.getMemberList().get(1);
		assertThat(missing.getUserId()).isEqualTo("user-2");
		assertThat(missing.getNickname()).isEqualTo("拼友");
		assertThat(missing.getAvatarUrl()).isNull();
		assertThat(missing.getIsLeader()).isEqualTo("0");
		assertThat(vo.getIsJoined()).isFalse();
		assertThat(vo.getMyMemberStatus()).isNull();
		verify(remoteMallUserService, times(1)).getUserByIds(any());
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

	private com.baomidou.mybatisplus.core.metadata.IPage<GroupBuyRecord> recordPage(GroupBuyRecord... records) {
		com.baomidou.mybatisplus.extension.plugins.pagination.Page<GroupBuyRecord> page
				= new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
		page.setRecords(List.of(records));
		page.setTotal(records.length);
		return page;
	}
}
