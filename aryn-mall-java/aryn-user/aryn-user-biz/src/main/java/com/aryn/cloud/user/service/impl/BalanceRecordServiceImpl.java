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
import com.aryn.cloud.user.service.IMemberLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 余额变动记录
 *
 * @author 雨滴kian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceRecordServiceImpl extends ServiceImpl<BalanceRecordMapper, BalanceRecord>
		implements IBalanceRecordService {

	private final UserInfoMapper userInfoMapper;

	private final IMemberLevelService memberLevelService;

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
		UserInfo userInfo = userInfoMapper.selectById(userId);
		if (userInfo == null) {
			throw new ArynBusinessException("用户不存在");
		}

		BigDecimal currentBalance = userInfo.getBalance() != null ? userInfo.getBalance() : BigDecimal.ZERO;
		BigDecimal balanceAfter;

		if ("1".equals(changeType)) {
			// 充值
			balanceAfter = currentBalance.add(changeAmount);
		}
		else if ("2".equals(changeType)) {
			// 消费
			balanceAfter = currentBalance.subtract(changeAmount);
			if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
				throw new ArynBusinessException("余额不足");
			}
		}
		else {
			// 调整（可正可负）
			balanceAfter = currentBalance.add(changeAmount);
			if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
				throw new ArynBusinessException("调整后余额不能为负数");
			}
		}

		// 更新用户余额
		userInfo.setBalance(balanceAfter);
		userInfoMapper.updateById(userInfo);

		// 插入余额记录
		BalanceRecord record = new BalanceRecord();
		record.setUserId(userId);
		record.setChangeType(changeType);
		record.setChangeAmount(changeAmount);
		record.setBalanceAfter(balanceAfter);
		record.setTriggerScene(triggerScene);
		record.setRemark(remark);
		this.save(record);

		// 如果是充值，触发等级重算
		if ("1".equals(changeType)) {
			try {
				memberLevelService.recalculateLevel(userId);
			}
			catch (Exception e) {
				log.warn("等级重新计算失败, userId={}", userId, e);
			}
		}
	}

}
