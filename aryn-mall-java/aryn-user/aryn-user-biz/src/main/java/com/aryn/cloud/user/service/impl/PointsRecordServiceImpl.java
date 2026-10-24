package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.PointsRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.enums.PointsSceneEnum;
import com.aryn.cloud.user.api.vo.PointsRecordVO;
import com.aryn.cloud.user.mapper.PointsRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import com.aryn.cloud.user.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
		recordPointsChange(userId, changeType, changePoint, triggerScene, remark, null);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void recordPointsChange(String userId, String changeType, Integer changePoint, String triggerScene,
			String remark, String bizNo) {
		if (!"1".equals(changeType) && !"2".equals(changeType)) {
			throw new ArynBusinessException("积分变动类型不合法");
		}
		if (changePoint == null || changePoint <= 0) {
			throw new ArynBusinessException("积分变动值必须大于0");
		}
		if (StringUtils.hasText(bizNo)) {
			long exists = this.count(Wrappers.<PointsRecord>lambdaQuery().eq(PointsRecord::getBizNo, bizNo));
			if (exists > 0) {
				// 同一业务单号已入账：MQ 重投或超时/取消并发时直接返回，避免重复加减
				return;
			}
		}

		UserInfo userInfo = userInfoMapper.selectById(userId);
		if (userInfo == null) {
			throw new ArynBusinessException("用户不存在");
		}
		Integer currentPoint = userInfoMapper.selectPointForUpdate(userId);
		if (currentPoint == null) {
			throw new ArynBusinessException("用户不存在");
		}

		// 退款/取消返还不是「新获得」：只恢复可用积分，不增加 total_point（累计获得），
		// 否则反复「下单-取消」即可刷高累计积分与积分线等级
		boolean refundScene = PointsSceneEnum.ORDER_REFUND.getCode().equals(triggerScene);
		int affectedRows;
		if ("1".equals(changeType)) {
			affectedRows = refundScene ? userInfoMapper.refundPoints(userId, changePoint)
					: userInfoMapper.acquirePoints(userId, changePoint);
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

		int balanceAfter = "1".equals(changeType) ? currentPoint + changePoint : currentPoint - changePoint;
		PointsRecord record = new PointsRecord();
		record.setUserId(userId);
		record.setChangeType(changeType);
		record.setChangePoint(changePoint);
		record.setBalanceAfter(balanceAfter);
		record.setTriggerScene(triggerScene);
		record.setRemark(remark);
		record.setBizNo(bizNo);
		this.save(record);

		if ("1".equals(changeType) && !refundScene) {
			memberLevelService.recalculateLevel(userId);
		}
	}

}
