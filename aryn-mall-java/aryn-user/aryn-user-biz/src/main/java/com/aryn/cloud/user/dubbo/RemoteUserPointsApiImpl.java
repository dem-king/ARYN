package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.user.api.entity.PointsRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.remote.IUserPointsApi;
import com.aryn.cloud.user.mapper.PointsRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 远程用户积分服务实现 (Dubbo RPC)
 * <p>
 * 使用 CAS 原子更新实现乐观锁防并发，确保积分扣减不会超扣。
 *
 * @author aryn
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteUserPointsApiImpl implements IUserPointsApi {

	private final UserInfoMapper userInfoMapper;

	private final PointsRecordMapper pointsRecordMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean deductPoints(String userId, Integer points, String bizId, String bizType) {
		if (points == null || points <= 0) {
			log.warn("积分扣减参数非法, userId={}, points={}", userId, points);
			return false;
		}

		// CAS 原子扣减：SQL 层面保证 point >= points 才会更新，防止并发超扣
		int affected = userInfoMapper.deductPointsCas(userId, points);
		if (affected == 0) {
			log.warn("积分扣减失败（余额不足或用户不存在）, userId={}, points={}", userId, points);
			return false;
		}

		// 查询扣减后余额，用于记录日志
		UserInfo userInfo = userInfoMapper.selectById(userId);
		int balanceAfter = userInfo.getPoint() != null ? userInfo.getPoint() : 0;

		// 记录积分变动日志
		insertPointsRecord(userId, "2", points, balanceAfter, bizType, "积分扣减，业务ID：" + bizId);

		log.info("积分扣减成功, userId={}, points={}, bizId={}, bizType={}, balanceAfter={}", userId, points, bizId,
				bizType, balanceAfter);
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean addPoints(String userId, Integer points, String bizId, String bizType) {
		if (points == null || points <= 0) {
			log.warn("积分增加参数非法, userId={}, points={}", userId, points);
			return false;
		}

		// CAS 原子增加
		int affected = userInfoMapper.addPointsCas(userId, points);
		if (affected == 0) {
			log.warn("积分增加失败（用户不存在）, userId={}, points={}", userId, points);
			return false;
		}

		// 查询增加后余额，用于记录日志
		UserInfo userInfo = userInfoMapper.selectById(userId);
		int balanceAfter = userInfo.getPoint() != null ? userInfo.getPoint() : 0;

		// 记录积分变动日志
		insertPointsRecord(userId, "1", points, balanceAfter, bizType, "积分增加，业务ID：" + bizId);

		log.info("积分增加成功, userId={}, points={}, bizId={}, bizType={}, balanceAfter={}", userId, points, bizId,
				bizType, balanceAfter);
		return true;
	}

	/**
	 * 插入积分变动记录
	 * @param userId 用户ID
	 * @param changeType 变动类型：1-获取；2-消耗
	 * @param changePoint 变动积分
	 * @param balanceAfter 变动后余额
	 * @param triggerScene 触发场景
	 * @param remark 备注
	 */
	private void insertPointsRecord(String userId, String changeType, Integer changePoint, int balanceAfter,
			String triggerScene, String remark) {
		PointsRecord record = new PointsRecord();
		record.setUserId(userId);
		record.setChangeType(changeType);
		record.setChangePoint(changePoint);
		record.setBalanceAfter(balanceAfter);
		record.setTriggerScene(triggerScene);
		record.setRemark(remark);
		pointsRecordMapper.insert(record);
	}

}
