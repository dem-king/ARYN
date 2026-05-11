package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import com.aryn.cloud.promotion.api.entity.GroupBuyMember;
import com.aryn.cloud.promotion.api.entity.GroupBuyRecord;
import com.aryn.cloud.promotion.api.enums.GroupBuyActivityStatusEnum;
import com.aryn.cloud.promotion.api.enums.GroupBuyMemberStatusEnum;
import com.aryn.cloud.promotion.api.enums.GroupBuyRecordStatusEnum;
import com.aryn.cloud.promotion.api.vo.GroupBuyRecordVO;
import com.aryn.cloud.promotion.mapper.GroupBuyActivityMapper;
import com.aryn.cloud.promotion.mapper.GroupBuyRecordMapper;
import com.aryn.cloud.promotion.service.IGroupBuyMemberService;
import com.aryn.cloud.promotion.service.IGroupBuyRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.messaging.support.MessageBuilder;
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

	private final RocketMQTemplate rocketMQTemplate;

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
		reduceStock(activity.getSkuId(), 1, activity.getSpuId());

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

		if (ObjectUtil.isNotNull(activity)) {
			reduceStock(activity.getSkuId(), 1, activity.getSpuId());
		}

		latestRecord.setCurrentNum(latestRecord.getCurrentNum() + 1);

		GroupBuyMember member = new GroupBuyMember();
		member.setRecordId(record.getId());
		member.setActivityId(record.getActivityId());
		member.setUserId(userId);
		member.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_0.getCode());
		member.setIsLeader("0");
		groupBuyMemberService.save(member);

		if (latestRecord.getCurrentNum() >= latestRecord.getGroupNum()) {
			latestRecord.setGroupStatus(GroupBuyRecordStatusEnum.STATUS_1.getCode());
			latestRecord.setSuccessAt(LocalDateTime.now());
			log.info("拼团成功, recordId={}", record.getId());
		}
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
				long joined = groupBuyMemberService.count(Wrappers.<GroupBuyMember>lambdaQuery()
						.eq(GroupBuyMember::getRecordId, record.getId())
						.eq(GroupBuyMember::getUserId, userId));
				vo.setIsJoined(joined > 0);
			}
			else {
				vo.setIsJoined(false);
			}
			return vo;
		});
		return voPage;
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
		member.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_1.getCode());
		groupBuyMemberService.updateById(member);

		GroupBuyRecord record = baseMapper.selectById(member.getRecordId());
		if (ObjectUtil.isNotNull(record)
				&& GroupBuyRecordStatusEnum.STATUS_0.getCode().equals(record.getGroupStatus())) {
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

		List<GroupBuyMember> pendingMembers = groupBuyMemberService.list(
				Wrappers.<GroupBuyMember>lambdaQuery()
						.eq(GroupBuyMember::getRecordId, record.getId())
						.eq(GroupBuyMember::getMemberStatus, GroupBuyMemberStatusEnum.STATUS_0.getCode()));
		if (!pendingMembers.isEmpty()) {
			pendingMembers.forEach(m -> m.setMemberStatus(GroupBuyMemberStatusEnum.STATUS_2.getCode()));
			groupBuyMemberService.updateBatchById(pendingMembers, 50);
			rollbackStock(record.getSkuId(), pendingMembers.size(), record.getSpuId());
		}

		List<GroupBuyMember> paidMembers = groupBuyMemberService.list(
				Wrappers.<GroupBuyMember>lambdaQuery()
						.eq(GroupBuyMember::getRecordId, record.getId())
						.eq(GroupBuyMember::getMemberStatus, GroupBuyMemberStatusEnum.STATUS_1.getCode()));
		for (GroupBuyMember m : paidMembers) {
			sendRefundMessage(m, record);
		}
		if (!paidMembers.isEmpty()) {
			rollbackStock(record.getSkuId(), paidMembers.size(), record.getSpuId());
		}
	}

	private void sendRefundMessage(GroupBuyMember member, GroupBuyRecord record) {
		try {
			OrderRefundSuccessEvent event = new OrderRefundSuccessEvent();
			event.setOrderId(member.getOrderId());
			event.setUserId(member.getUserId());
			event.setRefundReason("拼团失败自动退款");
			rocketMQTemplate.syncSend(
					RocketMqConstants.ORDER_REFUND_SUCCESS_NOTIFY_TOPIC,
					MessageBuilder.withPayload(event).build(),
					RocketMqConstants.TIME_OUT);
			log.info("拼团失败退款消息已发送, orderId={}, recordId={}", member.getOrderId(), record.getId());
		}
		catch (Exception e) {
			log.error("拼团失败退款消息发送失败, orderId={}, recordId={}", member.getOrderId(), record.getId(), e);
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

	private void reduceStock(String skuId, int quantity, String spuId) {
		try {
			GoodsSkuStockReqDTO dto = new GoodsSkuStockReqDTO();
			dto.setSkuId(skuId);
			dto.setStockNum(quantity);
			dto.setSpuId(spuId);
			boolean success = remoteGoodsSkuService.reduceStock(Collections.singletonList(dto));
			if (!success) {
				throw new ArynBusinessException("库存不足");
			}
		}
		catch (ArynBusinessException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("库存扣减异常, skuId={}", skuId, e);
			throw new ArynBusinessException("库存服务异常，请稍后重试");
		}
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
