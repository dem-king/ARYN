package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.user.api.entity.PointsRecord;
import com.aryn.cloud.user.api.enums.PointsSceneEnum;
import com.aryn.cloud.user.mapper.PointsRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IPointsExpireService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分过期清零。
 *
 * <p>发放类流水按「expire_time + remain_point」记账：FIFO 消耗时先扣最早到期批次，
 * 到期批次剩余量由本服务清零并同步扣减 user_info.point（写 POINTS_EXPIRE 负流水，
 * bizNo=POINTS_EXPIRE:{日期}:{用户}，按天幂等）。扣减额取 min(Σ到期剩余, 当前余额)，
 * 防御历史账实漂移把余额扣成负数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsExpireServiceImpl implements IPointsExpireService {

	private final PointsRecordMapper pointsRecordMapper;

	private final UserInfoMapper userInfoMapper;

	@Override
	public List<String> listExpiringUserIds() {
		return pointsRecordMapper.selectExpiringUserIds(LocalDateTime.now());
	}

	@Override
	public int expireTenant() {
		List<String> userIds = listExpiringUserIds();
		int expired = 0;
		for (String userId : userIds) {
			try {
				if (expireUser(userId)) {
					expired++;
				}
			}
			catch (Exception e) {
				// 单用户失败不阻断整租户：批次剩余量未清零时下次扫描会重试
				log.error("积分过期清零失败 userId={}", userId, e);
			}
		}
		return expired;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean expireUser(String userId) {
		LocalDateTime now = LocalDateTime.now();
		List<PointsRecord> expiredRows = pointsRecordMapper.selectExpiredIncomeRows(userId, now);
		if (expiredRows == null || expiredRows.isEmpty()) {
			return false;
		}
		int sum = expiredRows.stream()
			.mapToInt(row -> row.getRemainPoint() == null ? 0 : row.getRemainPoint())
			.sum();
		Integer currentPoint = userInfoMapper.selectPointForUpdate(userId);
		// 批次剩余量先清零：即使余额侧无可扣（账实漂移/用户已删），也终止对该批次的重复扫描
		pointsRecordMapper.zeroExpiredIncomeRows(userId, now);
		if (currentPoint == null || sum <= 0) {
			return false;
		}
		int expired = Math.min(sum, Math.max(currentPoint, 0));
		if (expired <= 0) {
			return false;
		}
		userInfoMapper.expirePoints(userId, expired);
		PointsRecord record = new PointsRecord().setUserId(userId)
			.setChangeType("2")
			.setChangePoint(expired)
			.setBalanceAfter(Math.max(currentPoint - expired, 0))
			.setTriggerScene(PointsSceneEnum.POINTS_EXPIRE.getCode())
			.setRemark("积分到期自动清零")
			.setBizNo("POINTS_EXPIRE:" + now.toLocalDate() + ":" + userId)
			.setCreateTime(now);
		try {
			pointsRecordMapper.insert(record);
		}
		catch (DuplicateKeyException e) {
			// 同日重复调度：批次剩余量已清零，流水幂等返回
			log.warn("积分过期流水已存在，跳过 userId={} date={}", userId, now.toLocalDate());
			return false;
		}
		return true;
	}

}
