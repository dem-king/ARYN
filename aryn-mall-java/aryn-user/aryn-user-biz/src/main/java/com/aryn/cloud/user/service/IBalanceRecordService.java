package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.BalanceRecord;
import com.aryn.cloud.user.api.vo.BalanceRecordVO;

import java.math.BigDecimal;

/**
 * 余额变动记录
 *
 * @author 雨滴kian
 */
public interface IBalanceRecordService extends IService<BalanceRecord> {

	IPage<BalanceRecordVO> getPage(Page page, String nickname, String changeType, String beginTime, String endTime);

	IPage<BalanceRecordVO> getUserPage(Page page, String userId);

	/**
	 * 记录余额变动
	 * @param userId 用户ID
	 * @param changeType 变动类型：1-充值；2-消费；3-调整
	 * @param changeAmount 变动金额（正数）
	 * @param triggerScene 触发场景
	 * @param remark 备注
	 */
	void recordBalanceChange(String userId, String changeType, BigDecimal changeAmount, String triggerScene,
			String remark);

}
