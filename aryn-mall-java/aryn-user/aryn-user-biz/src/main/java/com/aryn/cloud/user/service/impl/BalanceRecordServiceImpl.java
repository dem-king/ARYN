package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.BalanceRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.BalanceRecordVO;
import com.aryn.cloud.user.mapper.BalanceRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IBalanceRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 余额变动记录
 *
 * @author 雨滴kian
 */
@Service
@RequiredArgsConstructor
public class BalanceRecordServiceImpl extends ServiceImpl<BalanceRecordMapper, BalanceRecord>
		implements IBalanceRecordService {

	private final UserInfoMapper userInfoMapper;

	@Override
	public IPage<BalanceRecordVO> getPage(Page page, String nickname, String changeType, String beginTime,
			String endTime) {
		return baseMapper.selectRecordPage(page, nickname, changeType, beginTime, endTime);
	}

	@Override
	public IPage<BalanceRecordVO> getUserPage(Page page, String userId) {
		return baseMapper.selectUserRecordPage(page, userId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void recordBalanceChange(String userId, String changeType, BigDecimal changeAmount, String triggerScene,
			String remark) {
		if (!"1".equals(changeType) && !"2".equals(changeType) && !"3".equals(changeType)) {
			throw new ArynBusinessException("余额变动类型不合法");
		}
		if (changeAmount == null) {
			throw new ArynBusinessException("余额变动值不能为空");
		}
		if (("1".equals(changeType) || "2".equals(changeType))
				&& changeAmount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ArynBusinessException("余额变动值必须大于0");
		}
		if ("3".equals(changeType) && changeAmount.compareTo(BigDecimal.ZERO) == 0) {
			throw new ArynBusinessException("余额调整值不能为0");
		}

		UserInfo userInfo = userInfoMapper.selectById(userId);
		if (userInfo == null) {
			throw new ArynBusinessException("用户不存在");
		}

		BigDecimal delta = "2".equals(changeType) ? changeAmount.negate() : changeAmount;
		if (userInfoMapper.changeBalance(userId, delta) == 0) {
			throw new ArynBusinessException("2".equals(changeType) ? "余额不足" : "调整后余额不能为负数");
		}

		UserInfo updatedUser = userInfoMapper.selectById(userId);
		if (updatedUser == null) {
			throw new ArynBusinessException("用户不存在");
		}

		BalanceRecord record = new BalanceRecord();
		record.setUserId(userId);
		record.setChangeType(changeType);
		record.setChangeAmount(changeAmount);
		record.setBalanceAfter(updatedUser.getBalance());
		record.setTriggerScene(triggerScene);
		record.setRemark(remark);
		this.save(record);
	}

}
