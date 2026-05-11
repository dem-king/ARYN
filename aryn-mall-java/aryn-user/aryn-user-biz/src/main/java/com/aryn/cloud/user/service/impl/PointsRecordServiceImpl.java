package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.PointsRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.PointsRecordVO;
import com.aryn.cloud.user.mapper.PointsRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import com.aryn.cloud.user.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 积分记录
 *
 * @author 雨滴kian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord>
		implements IPointsRecordService {

	private final UserInfoMapper userInfoMapper;

	private final IMemberLevelService memberLevelService;

	@Override
	public IPage<PointsRecordVO> getPage(Page page, String nickname, String changeType, String beginTime,
			String endTime) {
		return baseMapper.selectRecordPage(page, nickname, changeType, beginTime, endTime);
	}

	@Override
	public IPage<PointsRecordVO> getUserPage(Page page, String userId) {
		return baseMapper.selectUserRecordPage(page, userId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void recordPointsChange(String userId, String changeType, Integer changePoint, String triggerScene,
			String remark) {
		UserInfo userInfo = userInfoMapper.selectById(userId);
		if (userInfo == null) {
			throw new ArynBusinessException("用户不存在");
		}

		int currentPoint = userInfo.getPoint() != null ? userInfo.getPoint() : 0;
		int balanceAfter;

		if ("1".equals(changeType)) {
			// 获取积分
			balanceAfter = currentPoint + changePoint;
		}
		else {
			// 消耗积分
			balanceAfter = currentPoint - changePoint;
			if (balanceAfter < 0) {
				throw new ArynBusinessException("积分余额不足");
			}
		}

		// 更新用户积分
		userInfo.setPoint(balanceAfter);
		userInfoMapper.updateById(userInfo);

		// 插入积分记录
		PointsRecord record = new PointsRecord();
		record.setUserId(userId);
		record.setChangeType(changeType);
		record.setChangePoint(changePoint);
		record.setBalanceAfter(balanceAfter);
		record.setTriggerScene(triggerScene);
		record.setRemark(remark);
		this.save(record);

		// 异步触发等级重新计算
		try {
			memberLevelService.recalculateLevel(userId);
		}
		catch (Exception e) {
			log.warn("等级重新计算失败, userId={}", userId, e);
		}
	}

}
