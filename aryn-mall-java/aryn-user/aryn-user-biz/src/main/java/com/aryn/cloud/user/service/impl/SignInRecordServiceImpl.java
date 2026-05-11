package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.SignInConfig;
import com.aryn.cloud.user.api.entity.SignInRecord;
import com.aryn.cloud.user.api.vo.SignInRecordVO;
import com.aryn.cloud.user.api.vo.SignInResultVO;
import com.aryn.cloud.user.mapper.SignInConfigMapper;
import com.aryn.cloud.user.mapper.SignInRecordMapper;
import com.aryn.cloud.user.service.IPointsRecordService;
import com.aryn.cloud.user.service.ISignInRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 签到记录
 *
 * @author 雨滴kian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignInRecordServiceImpl extends ServiceImpl<SignInRecordMapper, SignInRecord>
		implements ISignInRecordService {

	private final SignInConfigMapper signInConfigMapper;

	private final IPointsRecordService pointsRecordService;

	@Override
	public IPage<SignInRecordVO> getPage(Page page, String nickname, String beginDate, String endDate) {
		return baseMapper.selectRecordPage(page, nickname, beginDate, endDate);
	}

	@Override
	public IPage<SignInRecordVO> getUserPage(Page page, String userId) {
		return baseMapper.selectUserRecordPage(page, userId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SignInResultVO signIn(String userId) {
		LocalDate today = LocalDate.now();

		// 查询今日是否已签到
		long todayCount = this.count(Wrappers.<SignInRecord>lambdaQuery()
				.eq(SignInRecord::getUserId, userId)
				.eq(SignInRecord::getSignDate, today));
		if (todayCount > 0) {
			throw new ArynBusinessException("今日已签到");
		}

		// 计算连续签到天数
		int consecutiveDay = 1;
		LocalDate yesterday = today.minusDays(1);
		SignInRecord yesterdayRecord = this.getOne(Wrappers.<SignInRecord>lambdaQuery()
				.eq(SignInRecord::getUserId, userId)
				.eq(SignInRecord::getSignDate, yesterday));
		if (yesterdayRecord != null) {
			consecutiveDay = yesterdayRecord.getConsecutiveDay() + 1;
		}

		// 查询签到配置，匹配连续天数获取奖励积分
		int rewardPoint = 0;
		List<SignInConfig> configs = signInConfigMapper
				.selectList(Wrappers.<SignInConfig>lambdaQuery().eq(SignInConfig::getStatus, "0")
						.le(SignInConfig::getConsecutiveDay, consecutiveDay).orderByDesc(SignInConfig::getConsecutiveDay));
		if (!configs.isEmpty()) {
			rewardPoint = configs.get(0).getRewardPoint();
		}

		// 插入签到记录
		SignInRecord record = new SignInRecord();
		record.setUserId(userId);
		record.setSignDate(today);
		record.setConsecutiveDay(consecutiveDay);
		record.setRewardPoint(rewardPoint);
		this.save(record);

		// 发放积分
		if (rewardPoint > 0) {
			pointsRecordService.recordPointsChange(userId, "1", rewardPoint, "SIGN_IN", "签到奖励");
		}

		// 返回签到结果
		SignInResultVO result = new SignInResultVO();
		result.setConsecutiveDay(consecutiveDay);
		result.setRewardPoint(rewardPoint);
		return result;
	}

}
