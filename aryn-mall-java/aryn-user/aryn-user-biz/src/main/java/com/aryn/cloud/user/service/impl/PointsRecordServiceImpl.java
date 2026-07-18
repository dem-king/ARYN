package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 积分记录
 *
 * @author 雨滴kian
 */
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
		if (!"1".equals(changeType) && !"2".equals(changeType)) {
			throw new ArynBusinessException("积分变动类型不合法");
		}
		if (changePoint == null || changePoint <= 0) {
			throw new ArynBusinessException("积分变动值必须大于0");
		}

		UserInfo userInfo = userInfoMapper.selectById(userId);
		if (userInfo == null) {
			throw new ArynBusinessException("用户不存在");
		}

		int affectedRows;
		if ("1".equals(changeType)) {
			affectedRows = userInfoMapper.acquirePoints(userId, changePoint);
		}
		else {
			affectedRows = userInfoMapper.consumePoints(userId, changePoint);
			if (affectedRows == 0) {
				throw new ArynBusinessException("积分余额不足");
			}
		}
		if (affectedRows == 0) {
			throw new ArynBusinessException("积分变动失败");
		}

		UserInfo updatedUser = userInfoMapper.selectById(userId);
		if (updatedUser == null) {
			throw new ArynBusinessException("用户不存在");
		}

		PointsRecord record = new PointsRecord();
		record.setUserId(userId);
		record.setChangeType(changeType);
		record.setChangePoint(changePoint);
		record.setBalanceAfter(updatedUser.getPoint());
		record.setTriggerScene(triggerScene);
		record.setRemark(remark);
		this.save(record);

		if ("1".equals(changeType)) {
			memberLevelService.recalculateLevel(userId);
		}
	}

}
