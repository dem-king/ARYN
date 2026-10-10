package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.remote.RemoteOrderRefundService;
import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import com.aryn.cloud.promotion.api.entity.GroupBuyMember;
import com.aryn.cloud.promotion.api.entity.GroupBuyRecord;
import com.aryn.cloud.promotion.api.enums.GroupBuyActivityStatusEnum;
import com.aryn.cloud.promotion.api.enums.GroupBuyMemberStatusEnum;
import com.aryn.cloud.promotion.api.enums.GroupBuyRecordStatusEnum;
import com.aryn.cloud.promotion.api.vo.GroupBuyOrderContextVO;
import com.aryn.cloud.promotion.api.vo.GroupBuyRecordVO;
import com.aryn.cloud.promotion.mapper.GroupBuyActivityMapper;
import com.aryn.cloud.promotion.mapper.GroupBuyRecordMapper;
import com.aryn.cloud.promotion.service.IGroupBuyMemberService;
import com.aryn.cloud.promotion.service.IGroupBuyRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyRecordServiceImpl extends ServiceImpl<GroupBuyRecordMapper, GroupBuyRecord>
		implements IGroupBuyRecordService {

	private final GroupBuyActivityMapper activityMapper;

	private final IGroupBuyMemberService groupBuyMemberService;

	private final RedissonClient redissonClient;

	@DubboReference
	private final RemoteOrderRefundService remoteOrderRefundService;

	@DubboReference
	private final RemoteGoodsSkuService remoteGoodsSkuService;

	private static final long LOCK_WAIT_SECONDS = 5;

	private static final long LOCK_LEASE_SECONDS = 10;

	@Override
	public GroupBuyRecord openGroup(String activityId, String userId) {
		GroupBuyActivity activity = activityMapper.selectById(activityId);
		if (ObjectUtil.isNull(activity)) {
			throw new ArynBusinessException("拼团活动不存在");
		}
		if (!GroupBuyActivityStatusEnum.STATUS_1.getCode().equals(activity.getActivityStatus())) {
			throw new ArynBusinessException("拼团活动未开始或已结束");
		}
		LocalDateTime now = LocalDateTime.now();
		if (now.isBefore(activity.getStartedAt()) || now.isAfter(activity.getEndedAt())) {
			throw new ArynBusinessException("拼团活动不在有效期内");
		}
		if (activity.getLimitNum() > 0) {
			int count = groupBuyMemberService.countByActivityIdAndUserId(activityId, userId);
			if (count >= activity.getLimitNum()) {
				throw new ArynBusinessException("已达到限购数量");
			}
		}

		String lockKey = "group_buy:activity:" + activityId;
		RLock lock = redissonClient.getLock(lockKey);
		try {
			if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
				throw new ArynBusinessException("系统繁忙，请稍后重试");
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ArynBusinessException("操作被中断");
		}
		try {
			return doOpenGroup(activity, userId, now);
		}
		finally {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public GroupBuyRecord doOpenGroup(GroupBuyActivity activity, String userId, LocalDateTime now) {
		GroupBuyRecord record = new GroupBuyRecord();
		record.setActivityId(activity.getId());
		record.setSpuId(activity.getSpuId());
		record.setSkuId(activity.getSkuId());
		record.setGroupPrice(activity.getGroupPrice());
		record.setGroupNum(activity.getGroupNum());
		record.setCurrentNum(1);
		record.setLeaderUserId(userId);
		record.setGroupStatus(GroupBuyRecordStatusEnum.STATUS_0.getCode());
		record.setExpireAt(now.plusHours(
				ObjectUtil.isNotNull(activity.getGroupExpireHours()) ? activity.getGroupExpireHours() : 24));
		super.save(record);

		GroupBuyMember member = new GroupBuyMember();
		member.setRecordId(record.getId());
		member.setActivityId(activity.getId());
		member.setUserId(userId);
		member.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_0.getCode());
		member.setIsLeader("1");
		groupBuyMemberService.save(member);

		log.info("开团成功, recordId={}, activityId={}, userId={}", record.getId(), activity.getId(), userId);
		return record;
	}

	@Override
	public GroupBuyRecord joinGroup(String recordId, String userId) {
		GroupBuyRecord record = baseMapper.selectById(recordId);
		if (ObjectUtil.isNull(record)) {
			throw new ArynBusinessException("拼团记录不存在");
		}
		if (!GroupBuyRecordStatusEnum.STATUS_0.getCode().equals(record.getGroupStatus())) {
			throw new ArynBusinessException("该团已结束");
		}
		if (LocalDateTime.now().isAfter(record.getExpireAt())) {
			throw new ArynBusinessException("该团已过期");
		}
		if (record.getCurrentNum() >= record.getGroupNum()) {
			throw new ArynBusinessException("该团已满员");
		}

		long exists = groupBuyMemberService.count(Wrappers.<GroupBuyMember>lambdaQuery()
				.eq(GroupBuyMember::getRecordId, recordId)
				.eq(GroupBuyMember::getUserId, userId));
		if (exists > 0) {
			throw new ArynBusinessException("您已参与该团");
		}

		GroupBuyActivity activity = activityMapper.selectById(record.getActivityId());
		if (ObjectUtil.isNotNull(activity) && activity.getLimitNum() > 0) {
			int count = groupBuyMemberService.countByActivityIdAndUserId(record.getActivityId(), userId);
			if (count >= activity.getLimitNum()) {
				throw new ArynBusinessException("已达到限购数量");
			}
		}

		String lockKey = "group_buy:record:" + recordId;
		RLock lock = redissonClient.getLock(lockKey);
		try {
			if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
				throw new ArynBusinessException("系统繁忙，请稍后重试");
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ArynBusinessException("操作被中断");
		}
		try {
			return doJoinGroup(record, userId, activity);
		}
		finally {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public GroupBuyRecord doJoinGroup(GroupBuyRecord record, String userId, GroupBuyActivity activity) {
		GroupBuyRecord latestRecord = baseMapper.selectById(record.getId());
		if (!GroupBuyRecordStatusEnum.STATUS_0.getCode().equals(latestRecord.getGroupStatus())) {
			throw new ArynBusinessException("该团已结束");
		}
		if (latestRecord.getCurrentNum() >= latestRecord.getGroupNum()) {
			throw new ArynBusinessException("该团已满员");
		}

		latestRecord.setCurrentNum(latestRecord.getCurrentNum() + 1);

		GroupBuyMember member = new GroupBuyMember();
		member.setRecordId(record.getId());
		member.setActivityId(record.getActivityId());
		member.setUserId(userId);
		member.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_0.getCode());
		member.setIsLeader("0");
		groupBuyMemberService.save(member);

		// 成团统一在支付回调判定（付款人数满员才成团）：参团仅占坑，
		// 未付款的占坑成员订单取消后可重新下单，团超时则由超时任务失败回滚
		super.updateById(latestRecord);

		log.info("参团成功, recordId={}, userId={}, currentNum={}", record.getId(), userId, latestRecord.getCurrentNum());
		return latestRecord;
	}

	@Override
	public IPage<GroupBuyRecordVO> getPageByActivityId(Page page, String activityId, String userId) {
		IPage<GroupBuyRecord> recordPage = baseMapper.selectPageByActivityId(page, activityId);
		IPage<GroupBuyRecordVO> voPage = recordPage.convert(record -> {
			GroupBuyRecordVO vo = new GroupBuyRecordVO();
			vo.setId(record.getId());
			vo.setActivityId(record.getActivityId());
			vo.setSpuId(record.getSpuId());
			vo.setSkuId(record.getSkuId());
			vo.setGroupPrice(record.getGroupPrice());
			vo.setGroupNum(record.getGroupNum());
			vo.setCurrentNum(record.getCurrentNum());
			vo.setLeaderUserId(record.getLeaderUserId());
			vo.setGroupStatus(record.getGroupStatus());
			vo.setExpireAt(record.getExpireAt());
			vo.setSuccessAt(record.getSuccessAt());
			vo.setCreateTime(record.getCreateTime());
			if (userId != null) {
				GroupBuyMember myMember = groupBuyMemberService.getOne(Wrappers.<GroupBuyMember>lambdaQuery()
						.eq(GroupBuyMember::getRecordId, record.getId())
						.eq(GroupBuyMember::getUserId, userId));
				vo.setIsJoined(myMember != null);
				if (myMember != null) {
					vo.setMyMemberStatus(myMember.getMemberStatus());
					vo.setMyOrderId(myMember.getOrderId());
				}
			}
			else {
				vo.setIsJoined(false);
			}
			return vo;
		});
		return voPage;
	}

	@Override
	public GroupBuyOrderContextVO getOrderContext(String recordId, String userId) {
		GroupBuyRecord record = baseMapper.selectById(recordId);
		if (ObjectUtil.isNull(record) || !GroupBuyRecordStatusEnum.STATUS_0.getCode().equals(record.getGroupStatus())) {
			throw new ArynBusinessException("该团已结束，无法下单");
		}
		if (ObjectUtil.isNull(record.getExpireAt()) || LocalDateTime.now().isAfter(record.getExpireAt())) {
			throw new ArynBusinessException("该团已过期，无法下单");
		}
		GroupBuyActivity activity = activityMapper.selectById(record.getActivityId());
		if (ObjectUtil.isNull(activity)) {
			throw new ArynBusinessException("拼团活动不存在");
		}
		LocalDateTime now = LocalDateTime.now();
		if (!GroupBuyActivityStatusEnum.STATUS_1.getCode().equals(activity.getActivityStatus())
				|| now.isBefore(activity.getStartedAt()) || now.isAfter(activity.getEndedAt())) {
			throw new ArynBusinessException("拼团活动不在有效期内，无法下单");
		}
		GroupBuyMember member = groupBuyMemberService.getOne(Wrappers.<GroupBuyMember>lambdaQuery()
				.eq(GroupBuyMember::getRecordId, recordId)
				.eq(GroupBuyMember::getUserId, userId)
				.eq(GroupBuyMember::getMemberStatus, GroupBuyMemberStatusEnum.STATUS_0.getCode())
				.and(wrapper -> wrapper.isNull(GroupBuyMember::getOrderId).or().eq(GroupBuyMember::getOrderId, "")));
		if (ObjectUtil.isNull(member)) {
			throw new ArynBusinessException("未找到可用的拼团参团资格，请重新开团或参团");
		}
		GroupBuyOrderContextVO vo = new GroupBuyOrderContextVO();
		vo.setRecordId(record.getId());
		vo.setActivityId(record.getActivityId());
		vo.setActivityName(activity.getActivityName());
		vo.setSpuId(activity.getSpuId());
		vo.setSkuId(activity.getSkuId());
		vo.setGroupPrice(activity.getGroupPrice());
		vo.setOriginalPrice(activity.getOriginalPrice());
		vo.setExpireAt(record.getExpireAt());
		return vo;
	}

	@Override
	public boolean bindOrder(String recordId, String userId, String orderId) {
		// 乐观抢占：仅「待付款且未绑定订单」的成员可绑定，并发重复下单只有一笔成功
		boolean bound = groupBuyMemberService.update(Wrappers.<GroupBuyMember>lambdaUpdate()
				.eq(GroupBuyMember::getRecordId, recordId)
				.eq(GroupBuyMember::getUserId, userId)
				.eq(GroupBuyMember::getMemberStatus, GroupBuyMemberStatusEnum.STATUS_0.getCode())
				.and(wrapper -> wrapper.isNull(GroupBuyMember::getOrderId).or().eq(GroupBuyMember::getOrderId, ""))
				.set(GroupBuyMember::getOrderId, orderId));
		if (!bound) {
			log.warn("拼团成员绑定订单失败(无可用占坑), recordId={}, userId={}, orderId={}", recordId, userId, orderId);
		}
		return bound;
	}

	@Override
	public boolean releaseOrder(String orderId) {
		if (StrUtil.isBlank(orderId)) {
			return true;
		}
		// 已付款成员（成团中）不释放：取消仅发生在待付款订单上，状态机保证
		return groupBuyMemberService.update(Wrappers.<GroupBuyMember>lambdaUpdate()
				.eq(GroupBuyMember::getOrderId, orderId)
				.eq(GroupBuyMember::getMemberStatus, GroupBuyMemberStatusEnum.STATUS_0.getCode())
				.set(GroupBuyMember::getOrderId, null));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handlePaySuccess(String orderId) {
		GroupBuyMember member = groupBuyMemberService.getOne(Wrappers.<GroupBuyMember>lambdaQuery()
				.eq(GroupBuyMember::getOrderId, orderId));
		if (ObjectUtil.isNull(member)) {
			return;
		}
		if (GroupBuyMemberStatusEnum.STATUS_1.getCode().equals(member.getMemberStatus())) {
			log.info("拼团支付已处理, orderId={}, 幂等跳过", orderId);
			return;
		}
		GroupBuyRecord record = baseMapper.selectById(member.getRecordId());
		// 团已失败/已结束（关闭窗口）后到账的支付：立即退款，不能让用户为失效的团付款。
		// 超时任务只对「当时已付款」的成员退款，本兜底覆盖该边界。
		if (ObjectUtil.isNull(record)
				|| !GroupBuyRecordStatusEnum.STATUS_0.getCode().equals(record.getGroupStatus())) {
			member.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_2.getCode());
			groupBuyMemberService.updateById(member);
			if (refundPaidMember(member, record, "拼团已结束自动退款") && ObjectUtil.isNotNull(record)) {
				// 退款已发起，同步归还该成员下单占用的商品库存（退款链路不回滚库存）
				rollbackStock(record.getSkuId(), 1, record.getSpuId());
			}
			return;
		}
		member.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_1.getCode());
		groupBuyMemberService.updateById(member);

		long paidCount = groupBuyMemberService.count(Wrappers.<GroupBuyMember>lambdaQuery()
				.eq(GroupBuyMember::getRecordId, record.getId())
				.eq(GroupBuyMember::getMemberStatus, GroupBuyMemberStatusEnum.STATUS_1.getCode()));
		if (paidCount >= record.getGroupNum()) {
			record.setGroupStatus(GroupBuyRecordStatusEnum.STATUS_1.getCode());
			record.setSuccessAt(LocalDateTime.now());
			baseMapper.updateById(record);
			log.info("拼团全员付款成功, recordId={}", record.getId());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handleGroupExpire() {
		int pageNum = 1;
		int pageSize = 100;
		int totalProcessed = 0;
		while (true) {
			Page<GroupBuyRecord> page = new Page<>(pageNum, pageSize);
			IPage<GroupBuyRecord> expiredPage = baseMapper.selectPage(page,
					Wrappers.<GroupBuyRecord>lambdaQuery()
							.eq(GroupBuyRecord::getGroupStatus, GroupBuyRecordStatusEnum.STATUS_0.getCode())
							.le(GroupBuyRecord::getExpireAt, LocalDateTime.now()));
			List<GroupBuyRecord> expiredRecords = expiredPage.getRecords();
			if (expiredRecords.isEmpty()) {
				break;
			}
			for (GroupBuyRecord record : expiredRecords) {
				processExpiredRecord(record);
				totalProcessed++;
			}
			if (expiredRecords.size() < pageSize) {
				break;
			}
			pageNum++;
		}
		log.info("拼团超时处理完成, 共处理{}条过期拼团记录", totalProcessed);
	}

	private void processExpiredRecord(GroupBuyRecord record) {
		record.setGroupStatus(GroupBuyRecordStatusEnum.STATUS_2.getCode());
		baseMapper.updateById(record);

		// 未付款占坑：只关成员状态。商品库存由订单流占用与回滚，
		// 占坑阶段未下单不占库存，因此这里不回滚（历史实现在开团/参团预扣库存，
		// 与下单扣减叠加会双扣，已随本次改造移除）。
		List<GroupBuyMember> pendingMembers = groupBuyMemberService.list(
				Wrappers.<GroupBuyMember>lambdaQuery()
						.eq(GroupBuyMember::getRecordId, record.getId())
						.eq(GroupBuyMember::getMemberStatus, GroupBuyMemberStatusEnum.STATUS_0.getCode()));
		if (!pendingMembers.isEmpty()) {
			pendingMembers.forEach(m -> m.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_2.getCode()));
			groupBuyMemberService.updateBatchById(pendingMembers, 50);
		}

		// 已付款成员：整单退款；退款发起成功后归还其订单占用的商品库存
		// （订单取消链路不走——订单本身仍有效，退款由这里收尾）
		List<GroupBuyMember> paidMembers = groupBuyMemberService.list(
				Wrappers.<GroupBuyMember>lambdaQuery()
						.eq(GroupBuyMember::getRecordId, record.getId())
						.eq(GroupBuyMember::getMemberStatus, GroupBuyMemberStatusEnum.STATUS_1.getCode()));
		int refundTriggered = 0;
		for (GroupBuyMember m : paidMembers) {
			if (refundPaidMember(m, record, "拼团失败自动退款")) {
				refundTriggered++;
			}
		}
		if (refundTriggered > 0) {
			rollbackStock(record.getSkuId(), refundTriggered, record.getSpuId());
		}
	}

	/**
	 * 成团失败时对已付款成员发起整单退款。
	 *
	 * <p>真实退款在订单服务执行（创建退款单并调支付网关），退款成功后走既有退款回调链路，
	 * 由 GroupBuyRefundHandler 把成员置为已取消。单笔失败只记日志，不阻断其余成员退款；
	 * 订单侧按明细幂等，可人工重试。
	 *
	 * @return true=已发起退款（可据此回滚库存）
	 */
	private boolean refundPaidMember(GroupBuyMember member, GroupBuyRecord record, String reason) {
		if (StrUtil.isBlank(member.getOrderId())) {
			log.warn("拼团退款跳过: 成员未关联订单, recordId={}, memberId={}", record.getId(), member.getId());
			return false;
		}
		try {
			boolean refunded = remoteOrderRefundService.refundWholeOrder(member.getOrderId(), reason);
			log.info("拼团退款发起, orderId={}, recordId={}, reason={}, result={}", member.getOrderId(),
					record.getId(), reason, refunded);
			return refunded;
		}
		catch (Exception e) {
			log.error("拼团退款发起失败, orderId={}, recordId={}", member.getOrderId(), record.getId(), e);
			return false;
		}
	}

	@Override
	public void handleActivityExpire() {
		List<GroupBuyActivity> activities = activityMapper.selectList(
				Wrappers.<GroupBuyActivity>lambdaQuery()
						.eq(GroupBuyActivity::getActivityStatus, GroupBuyActivityStatusEnum.STATUS_1.getCode())
						.le(GroupBuyActivity::getEndedAt, LocalDateTime.now()));
		if (activities.isEmpty()) {
			return;
		}
		activities.forEach(activity -> {
			activity.setActivityStatus(GroupBuyActivityStatusEnum.STATUS_2.getCode());
			activityMapper.updateById(activity);
			log.info("拼团活动自动结束, activityId={}", activity.getId());
		});
		log.info("拼团活动状态流转完成, 共处理{}条", activities.size());
	}

	private void rollbackStock(String skuId, int quantity, String spuId) {
		try {
			GoodsSkuStockReqDTO dto = new GoodsSkuStockReqDTO();
			dto.setSkuId(skuId);
			dto.setStockNum(quantity);
			dto.setSpuId(spuId);
			remoteGoodsSkuService.rollbackStock(Collections.singletonList(dto));
			log.info("库存回滚成功, skuId={}, quantity={}", skuId, quantity);
		}
		catch (Exception e) {
			log.error("库存回滚异常, skuId={}, quantity={}", skuId, quantity, e);
		}
	}
}
